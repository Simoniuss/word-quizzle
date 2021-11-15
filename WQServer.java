import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Timer;
import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.io.FileReader;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.NoSuchObjectException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/*
** WQServer è la classe che rappresenta il server principale di Word Quizzle. Si occupa della
** gestione delle connessioni in entrata da parte degli utenti e soddisfa le loro richieste. Inoltre
** gestisce un database json in cui vengono salvati i dati degli utenti. Il server utilizza un selector
** per le gestione delle connessioni in entrata e per l'ascolto di richieste da parte degli utenti. Il server
** è non bloccante e utilizza un threadpool solo per la gestione delle sfide, tutte le altre richieste
** vengono soddisfatte all'interno del server stesso. I messaggi scambiati tra utenti e server seguono
** un protocollo ben preciso. WQServer è in grado di operare anche in sessioni diverse, recuperando i dati salvati
** all'interno del database, qual'ora venisse interrotto.
** @Attributi:
**		-serverCh: ServerSocketChannel non bloccante registrato nel selector che accetta nuove connessioni.
**		-sel: Selector del server.
**		-dict: il nome del file dizionario nel quale sono contenute le parole.
**		-dictLock: oggetto utilizzato per garantire la mutua esclusione nell'utilizzo del dizionario.
**		-dbName: nome del file json utilizzato come database. Se esiste viene aperto, se non esiste viene creato.
**		-dbLock: oggetto utilizzato per garantire la mutua esclusione nell'accesso al database.
**		-playerMap: Map sincronizzata che contiene i riferimenti agli utenti nel server.
**		-playerLock: oggetto utilizzato per garantire la mutua esclusione con operazioni più complesse sulla playerMap.
**		-challengeTimeout: timeout per la validità della richiesta di sfida da parte di un utente.
**		-gameMap: Map sincronizzata che contiene le richieste di sfida attive associate all'utente.
**		-gamePool: threadpool al quale vengono delegate le sfide tra gli utenti.
**		-reg: oggetto remoto utilizzato per la registrazione degli utenti.
**		-closed: variabile che indica se il server è stato chiuso o no.
*/

public class WQServer {
	private ServerSocketChannel serverCh;
	private Selector sel;
	private String dict;
	private Object dictLock;
	private String dbName;
	private Object dbLock;
	private Map<String,Player> playerMap;
	private Object playerLock;
	private int challengeTimeout;
	private Map<String,GameInfo> gameMap;
	private ExecutorService gamePool;
	private Register reg;
	private boolean closed=false;

	public WQServer(String d,String json,int timeout) {
		dict=d;
		dictLock=new Object();
		dbName=json;
		dbLock=new Object();
		challengeTimeout=timeout;
	} 

	/*
	** La funzione log stampa a schermo il messaggio passato come parametro.
	** @param msg: messaggio stampato a schermo.
	*/
	private void log(String msg) {
		System.out.println(msg);
	}

	/*
	** La funzione setup inizializza il server in tutte le sue componenti, prima di poter essere
	** utilizzato. Inizializza il selettore, il ServerSocketChanell, il threadpool per la gestione
	** delle sfide, le strutture dati sugli utenti, il database, il registro remoto e lo shutdown hook
	** per la gestione della chiusura del server se viene interrotto.
	** @param port: porta alla quale si mette in ascolto il ServerSocketChannel.
	** @param portReg: porta alla quale si mette in ascolto il registro remoto.
	** @return: ritorna un booleano, true se il setup è avvenuto correttamente, altrimenti false.
	*/
	public boolean setup(int port,int portReg) {
		log("Server setup: START...");
		
		// Selector+ServerSocketChannel.
		try {
			sel=Selector.open();
			log("Server setup: Selector open!");
			serverCh=ServerSocketChannel.open();
			serverCh.socket().bind(new InetSocketAddress(port));
			serverCh.configureBlocking(false);
			serverCh.register(sel,SelectionKey.OP_ACCEPT);
			log("Server setup: ServerSocketChannel ready!");
		}
		catch(IllegalArgumentException e) {
			log("Server setup: not valid port "+port);
			return false;
		}
		catch(IOException e1) {
			log("Server setup: IOException");
			return false;
		}

		// ThreadPool.
		gamePool=Executors.newCachedThreadPool();
		log("Server setup: ThreadPool ready!");

		// playerMap+gameMap.
		playerMap=Collections.synchronizedMap(new HashMap<String,Player>());
		playerLock=new Object();
		log("Server setup: playerMap ready!");
		gameMap=Collections.synchronizedMap(new HashMap<String,GameInfo>());
		log("Server setup: gameMap ready!");

		// Setup database.
		if(new File(dict).exists())
			log("Server setup: dictionary ready!");
		else {
			log("Server setup: dictionary not found");
			return false;
		}
		if(new File(dbName).exists())
			log("Server setup: Json Database ready!");
		else {
			File db=new File(dbName);
			try(FileWriter fw=new FileWriter(db)) {
				db.createNewFile();
				log("Server setup: create Json Database...");
				fw.write("{}",0,2);
			}
			catch(IOException e) {
				log("Server setup: IOException");
				return false;
			}
			log("Server setup: Json Database ready!");
		}

		// Registry per la registrazione.
		try {
			reg=new RegisterImpl(playerMap,dbName,dbLock);
			LocateRegistry.createRegistry(portReg);
			Registry r=LocateRegistry.getRegistry(portReg);
			r.rebind(reg.SERVICE_NAME,reg);
			log("Server setup: Registry ready!");
		}
		catch(RemoteException e) {
			log("Server setup: RemoteException");
			return false;
		}

		// ShutdownHook
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				log("Server shutdown");
				close();
			}
		});
		log("Server setup: Shutdown Thread ready!");
		log("Server setup: COMPLETE!");
		return true;
	}

	/*
	** La funzione start gestisce le connessioni in entrata da parte degli utenti e le richieste
	** di operazioni. Registra i clienti al selettore con operazioni di lettura e seleziona le chiavi
	** pronte in lettura per svolgere le operazioni(delegate alla funzione readOp).
	*/
	public void start() {
		log("Server start: START...");
		try {
			while(true) {
				try {
					sel.selectNow();
				}
				catch(IOException e) {
					log("Server start: Select error");
					break;
				}
				Set<SelectionKey> selKeys=sel.selectedKeys();
				Iterator<SelectionKey> it=selKeys.iterator();
				while(it.hasNext()) {
					SelectionKey key=it.next();
					try {

						// Nuovo utente richiede di connettersi.
						if(key.isAcceptable()) {
							ServerSocketChannel s=(ServerSocketChannel) key.channel();
							SocketChannel client=s.accept();
							client.configureBlocking(false);
							client.register(sel,SelectionKey.OP_READ);
							log("Server: new client connection ("+client.getRemoteAddress().toString()+")");
						}

						// Un utente sta richiedendo un'operazione.
						if(key.isReadable()) {
							SocketChannel client=(SocketChannel)key.channel();
							ByteBuffer buf=ByteBuffer.allocate(500);
							int r;
							r=client.read(buf);
							buf.flip();
							byte[] rcv=new byte[buf.remaining()];
							while(buf.hasRemaining())
								buf.get(rcv);

							// Richiesta consistente.
							if(r>0) {
								log("Server: new message from "+client.getRemoteAddress().toString());
								readOp(new String(rcv),key);
							}

							// Utente disconnesso senza logout.
							if(r==-1) {
								log("Server: client disconnect without logout");
								synchronized(playerLock) {
									for(Map.Entry<String,Player> e : playerMap.entrySet()) {
										if(e.getValue().getKey()==key) {
											String logout="1 "+e.getKey();
											readOp(logout,key);
										}
									}
								}
								key.channel().close();
								key.cancel();
								log("Server: key removed!");
							}
						}
						it.remove();
					}
					catch(IOException e) {
						log("Server: IOException in read or accept key");
						try {
							key.channel().close();
						}
						catch(IOException e1) {}
						key.cancel();
					}	
				}
			}
		}
		finally {
			close();
		}
	}

	/*
	** La funzione readOp si occupa di leggere la richiesta ricevuta dall'utente e formula
	** una risposta eseguendo la richiesta.
	** @param msg: stringa della richiesta ricevuta dall'utente.
	** @param k: SelectionKey dell'utente che ha inviato la richiesta.
	*/
	private void readOp(String msg, SelectionKey k) {
		String[] splitMsg=msg.split(" ");
		SocketChannel client=(SocketChannel)k.channel();
		ByteBuffer buf=ByteBuffer.allocate(500);
		switch(splitMsg[0]) {

			/*************************************************************
			**															 *
			**							LOGIN							 *
			**															 *
			*************************************************************/
			case "0":
				log("Server Op: LOGIN...");

				// Errore nel messaggio ricevuto.
				if(splitMsg.length!=3) {
					try {
						log("Server Op: formatError");
						buf.put("KO formatError".getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}

				else {
					try {

						// Player già in lista.
						if(playerMap.containsKey(splitMsg[1])) {
							Player p=playerMap.get(splitMsg[1]);

							// Player già loggato.
							if(p.getState()==Player.State.ONLINE) {
								log("Server Op: "+splitMsg[1]+" already logged!");
								buf.put("KO Already Logged".getBytes());
								buf.flip();
								while(buf.hasRemaining())
									client.write(buf);
							}

							else {
								// Password corretta.
								if(p.getInfo().getPass().equals(splitMsg[2])) {
									p.setState(Player.State.ONLINE);
									p.setPort(((InetSocketAddress)((SocketChannel)k.channel()).getRemoteAddress()).getPort());
									p.setKey(k);
									buf.put("OK".getBytes());
									buf.flip();
									while(buf.hasRemaining())
										client.write(buf);
									log("Server Op: "+splitMsg[1]+" logged!");
								}

								// Password sbagliata.
								else {
									log("Server Op: "+splitMsg[1]+" password incorrect!");
									buf.put("KO password incorrect".getBytes());
									buf.flip();
									while(buf.hasRemaining())
										client.write(buf);
								}
							}
						}

						else {

							// Player non in lista, recupero database json.
							synchronized(dbLock) {
								try(FileReader fr=new FileReader(dbName)) {
									JsonObject obj=new Gson().fromJson(fr,JsonObject.class);

									// Player non esistente.
									if(obj.get(splitMsg[1])==null) {
										log("Server Op: "+splitMsg[1]+" not found!");
										buf.put("KO username incorrect!".getBytes());
										buf.flip();
										while(buf.hasRemaining())
											client.write(buf);
									}

									// Recupero player dal db.
									else {
										PlayerInfo pi=new Gson().fromJson(obj.get(splitMsg[1]),PlayerInfo.class);
										Player p=new Player(splitMsg[1],pi);

										// Password corretta.
										if(pi.getPass().equals(splitMsg[2])) {
											p.setState(Player.State.ONLINE);
											p.setPort(((InetSocketAddress)((SocketChannel)k.channel()).getRemoteAddress()).getPort());
											p.setKey(k);
											playerMap.put(splitMsg[1],p);
											buf.put("OK".getBytes());
											buf.flip();
											while(buf.hasRemaining())
												client.write(buf);
											log("Server Op: "+splitMsg[1]+" logged!");
										}

										// Password sbagliata.
										else {
											log("Server Op: "+splitMsg[1]+" password incorrect!");
											buf.put("KO password incorrect".getBytes());
											buf.flip();
											while(buf.hasRemaining())
												client.write(buf);
										}
									}
								}
								catch(IOException e) {
									log("Server Op: IOException");
									break;
								}
							}
						}
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
			break;

			/*************************************************************
			**															 *
			**							LOGOUT							 *
			**															 *
			*************************************************************/
			case "1":
				log("Server Op: LOGOUT...");

				// Errore nel messaggio ricevuto.
				if(splitMsg.length!=2) {
					try {
						log("Server Op: formatError");
						buf.put("KO formatError".getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}

				// Esegui logout.
				else {
					try {
						if(playerMap.containsKey(splitMsg[1])) {
							Player p=playerMap.get(splitMsg[1]);
							p.setState(Player.State.OFFLINE);
							synchronized(dbLock) {
								try(FileReader fr=new FileReader(dbName)) {
									JsonObject obj=new Gson().fromJson(fr,JsonObject.class);
									obj.remove(splitMsg[1]);
									obj.add(splitMsg[1],new Gson().toJsonTree(p.getInfo()));
									try(FileWriter fw=new FileWriter(dbName)) {
										new Gson().toJson(obj,fw);
									}
									catch(IOException e) {
										log("Server Op: IOException");
										break;
									}
								}
								catch(IOException e) {
									log("Server Op: IOException");
									break;
								}
							}
							buf.put("OK".getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							k.channel().close();
							k.cancel();
							log("Server Op: "+splitMsg[1]+" logout!");
						}
						else {
							buf.put("KO logout error".getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							log("Server Op: "+splitMsg[1]+" logout error");
						}
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
			break;

			/*************************************************************
			**															 *
			**							ADD FRIEND						 *
			**															 *
			*************************************************************/
			case "2":
				log("Server Op: ADD FRIEND...");

				// Errore nel messaggio ricevuto.
				if(splitMsg.length!=3) {
					try {
						log("Server Op: formatError");
						buf.put("KO formatError".getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}

				// L'utente cerca di aggiungere se stesso.
				if(splitMsg[1].equals(splitMsg[2])) {
					try {
						log("Server Op: samePerson");
						buf.put("KO Same Person".getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}

				else {
					try {

						// Recupero la lista di amici del player.
						if(playerMap.containsKey(splitMsg[1])) {
							Player p=playerMap.get(splitMsg[1]);
							List friends=p.getInfo().getFriends();

							// L'amico è già stato aggiunto.
							if(friends.contains(splitMsg[2])) {
								buf.put("KO Already Friends".getBytes());
								buf.flip();
								while(buf.hasRemaining())
									client.write(buf);
								log("Server Op: "+splitMsg[1]+" & "+splitMsg[2]+" already friends!");
							}

							// Cerco l'amico da aggiungere.
							else {
								// L'amico è nella playerMap.
								if(playerMap.containsKey(splitMsg[2])) {
									Player p2=playerMap.get(splitMsg[2]);
									p.getInfo().addFriend(splitMsg[2]);
									p2.getInfo().addFriend(splitMsg[1]);
									if(p2.getState()==Player.State.OFFLINE)
										p2.setState(Player.State.UPDATE);
									buf.put("OK".getBytes());
									buf.flip();
									while(buf.hasRemaining())
										client.write(buf);
									log("Server Op: "+splitMsg[1]+" & "+splitMsg[2]+" now friends!!");
								}

								// L'amico va recuperato dal db json
								else {
									synchronized(dbLock) {
										try(FileReader fr=new FileReader(dbName)) {
											JsonObject obj=new Gson().fromJson(fr,JsonObject.class);

											// L'amico da aggiungere esiste
											if(obj.has(splitMsg[2])) {
												PlayerInfo pi2=new Gson().fromJson(obj.get(splitMsg[2]),PlayerInfo.class);
												pi2.addFriend(splitMsg[1]);
												Player p2=new Player(splitMsg[2],pi2);
												p2.setState(Player.State.UPDATE);
												playerMap.put(splitMsg[2],p2);
												p.getInfo().addFriend(splitMsg[2]);
												buf.put("OK".getBytes());
												buf.flip();
												while(buf.hasRemaining())
													client.write(buf);
												log("Server Op: "+splitMsg[1]+" & "+splitMsg[2]+" now friends!!");
											}

											// L'amico da aggiungere non esiste
											else {
												buf.put("KO Friend Not Found".getBytes());
												buf.flip();
												while(buf.hasRemaining())
													client.write(buf);
												log("Server Op: "+splitMsg[2]+" not found");
											}
										}
										catch(IOException e) {
											log("Server Op: IOException");
											break;
										}
									}
								}
							}
						}
						else {
							buf.put("KO Add Friend Error".getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							log("Server Op: "+splitMsg[1]+" add friend error");
						}
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
			break;

			/*************************************************************
			**															 *
			**							FRIENDLIST						 *
			**															 *
			*************************************************************/
			case "3":
				log("Server Op: FRIEND LIST...");
				// Errore nel messaggio ricevuto.
				if(splitMsg.length!=2) {
					try {
						log("Server Op: formatError");
						buf.put("KO formatError".getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
				else {
					try {
						if(playerMap.containsKey(splitMsg[1])) {
							Player p=playerMap.get(splitMsg[1]);
							List friends=p.getInfo().getFriends();
							String jsonFriends=new Gson().toJson(friends);
							String resp="OK "+jsonFriends;
							buf.put(resp.getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							log("Server Op: friend list sent to "+splitMsg[1]);
						}
						else {
							buf.put("KO Friend List Error".getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							log("Server Op: "+splitMsg[1]+" friend list error");
						}
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
			break;

			/*************************************************************
			**															 *
			**							SCORE							 *
			**															 *
			*************************************************************/
			case "4":
				log("Server Op: SCORE...");
				// Errore nel messaggio ricevuto.
				if(splitMsg.length!=2) {
					try {
						log("Server Op: formatError");
						buf.put("KO formatError".getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
				else {
					try {
						if(playerMap.containsKey(splitMsg[1])) {
							Player p=playerMap.get(splitMsg[1]);
							int score=p.getInfo().getScore();
							String resp="OK "+score;
							buf.put(resp.getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							log("Server Op: score sent to "+splitMsg[1]);
						}
						else {
							buf.put("KO Score Error".getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							log("Server Op: "+splitMsg[1]+" Score error");
						}
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
			break;

			/*************************************************************
			**															 *
			**							RANKING							 *
			**															 *
			*************************************************************/
			case "5":
				log("Server Op: RANKING...");
				// Errore nel messaggio ricevuto.
				if(splitMsg.length!=2) {
					try {
						log("Server Op: formatError");
						buf.put("KO formatError".getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
				else {
					try {
						if(playerMap.containsKey(splitMsg[1])) {
							Player p=playerMap.get(splitMsg[1]);
							List<String> friends=p.getInfo().getFriends();
							Map<String,Integer> rank=new HashMap<String,Integer>();
							rank.put(splitMsg[1],p.getInfo().getScore());

							// Trovo i punteggi di ogni amico.
							for(String f : friends) {
								
								// Amico in playerMap.
								if(playerMap.containsKey(f))
									rank.put(f,playerMap.get(f).getInfo().getScore());

								// Amico da recuperare nel json db.
								else {
									synchronized(dbLock) {
										try(FileReader fr=new FileReader(dbName)) {
											JsonObject obj=new Gson().fromJson(fr,JsonObject.class);
											if(obj.has(f)) {
												PlayerInfo pi2=new Gson().fromJson(obj.get(f),PlayerInfo.class);
												Player p2=new Player(f,pi2);
												playerMap.put(f,p2);
												rank.put(f,pi2.getScore());
											}
										}
										catch(IOException e) {
											log("Server Op: IOException");
											break;
										}
									}
								}
							}

							// Salvo in una lista utente-punteggio la classifica degli amici e la ordino.
							List<Map.Entry<String,Integer>> sortedRank=new LinkedList<>(rank.entrySet());
							Collections.sort(sortedRank, (p1,p2) -> p2.getValue().compareTo(p1.getValue()));

							// Inserisco la lista in un oggetto json da inviare all'utente.
							JsonObject objRank=new JsonObject();
							for(Map.Entry<String,Integer> o : sortedRank)
								objRank.addProperty(o.getKey(),o.getValue().toString());
							String jsonRank=new Gson().toJson(objRank);
							String resp="OK "+jsonRank;
							buf.put(resp.getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							log("Server Op: ranking sent to "+splitMsg[1]);
						}
						else {
							buf.put("KO Ranking Error".getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							log("Server Op: "+splitMsg[1]+" ranking error");
						}
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
			break;

			/*************************************************************
			**															 *
			**							CHALLENGE						 *
			**															 *
			*************************************************************/
			case "6":
				log("Server Op: CHALLENGE...");
				// Errore nel messaggio ricevuto.
				if(splitMsg.length!=3) {
					try {
						log("Server Op: formatError");
						buf.put("KO formatError".getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}

				// L'utente sta cercando di sfidare se stesso.
				if(splitMsg[1].equals(splitMsg[2])) {
					try {
						log("Server Op: samePerson");
						buf.put("KO Same Person".getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}

				else {
					try {
						// Sfida già presente.
						if(gameMap.containsKey(splitMsg[1])) {
							buf.put("KO Challenge Error".getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							log("Server Op: "+splitMsg[1]+" challenge error");
						}
						else {

							// Ricerca player sfidato.
							if(playerMap.get(splitMsg[2])!=null) {
								Player p2=playerMap.get(splitMsg[2]);

								// Sfida player, inviando una richiesta UDP e facendo partire un timer di accettazione.
								if(p2.getState()==Player.State.ONLINE) {
									DatagramSocket dataSock=new DatagramSocket();
									InetAddress ia=InetAddress.getByName("localhost");
									String challReq="CHALLENGE "+splitMsg[1]+" "+splitMsg[2];
									byte[] dataBuf=new byte[500];
									dataBuf=challReq.getBytes();
									DatagramPacket dataPkt=new DatagramPacket(dataBuf,dataBuf.length,ia,p2.getPort());
									dataSock.send(dataPkt);
									// Set Timer
									Timer timer=new Timer();
									timer.schedule(new ChallengeTimer(splitMsg[1],gameMap,client,playerMap.get(splitMsg[1])),challengeTimeout);
									GameInfo gi=new GameInfo(k,timer);
									gameMap.put(splitMsg[1],gi);
									playerMap.get(splitMsg[1]).setState(Player.State.BUSY);
								}

								// Player occupato o offline.
								else {
									buf.put("KO Player Unavailable".getBytes());
									buf.flip();
									while(buf.hasRemaining())
										client.write(buf);
									log("Server Op: "+splitMsg[2]+" unavailable");
								}
							}

							// Player offline perchè non in playerMap.
							else {
								buf.put("KO Player Unavailable".getBytes());
								buf.flip();
								while(buf.hasRemaining())
									client.write(buf);
								log("Server Op: "+splitMsg[2]+" unavailable");
							}
						}
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
			break;

			/*************************************************************
			**															 *
			**						ACCEPT CHALLENGE					 *
			**															 *
			*************************************************************/
			case "7":
				log("Server Op: ACCEPT CHALLENGE...");
				// Errore nel messaggio ricevuto.
				if(splitMsg.length!=4) {
					try {
						log("Server Op: formatError");
						buf.put("KO formatError".getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
				else {
					try {
						// Sfida scaduta.
						if(!gameMap.containsKey(splitMsg[2])) {
							buf.put("KO Challenge Expired".getBytes());
							buf.flip();
							while(buf.hasRemaining())
								client.write(buf);
							log("Server Op: "+splitMsg[2]+" challenge expired");
						}
						else {

							// Sfida accettata, lancio thread sfida.
							if(Integer.parseInt(splitMsg[3])==1) {
								GameInfo gi=gameMap.get(splitMsg[2]);
								gi.cancelTimer();
								gameMap.remove(splitMsg[2]);
								playerMap.get(splitMsg[1]).setState(Player.State.BUSY);
								gamePool.submit(new Challenge(gi.getKey(),k,playerMap.get(splitMsg[2]),playerMap.get(splitMsg[1]),dict,dictLock));
							}

							// Sfida rifiutata.
							else {
								GameInfo gi=gameMap.get(splitMsg[2]);
								gi.cancelTimer();
								SocketChannel p1=gi.getSocket();
								// Risposta allo sfidante
								buf.put("KO Sfida Rifiutata".getBytes());
								buf.flip();
								while(buf.hasRemaining())
									p1.write(buf);
								log("Server Op: send challenge rejected to "+splitMsg[2]);
								// Risposta allo sfidato che ha rifiutato
								buf.clear();
								buf.put("OK Sfida Rifiutata".getBytes());
								buf.flip();
								while(buf.hasRemaining())
									client.write(buf);
								log("Server Op: send challenge rejected to "+splitMsg[1]);
								gameMap.remove(splitMsg[2]);
								playerMap.get(splitMsg[2]).setState(Player.State.ONLINE);
							}
						}
					}
					catch(IOException e) {
						log("Server Op: IOException");
						break;
					}
				}
			break;

			/*************************************************************
			**															 *
			**						COMMAND NOT FOUND					 *
			**															 *
			*************************************************************/
			default:
				log("Server Op: COMMAND NOT FOUND...");
				try {
					buf.put("KO CommandNotFound".getBytes());
					buf.flip();
					while(buf.hasRemaining())
						client.write(buf);
				}
				catch(IOException e) {
					log("Server Op: IOException");
					break;
				}
			break;
		}
	}

	/*
	** Funzione che gestisce la chiusura del server. Salva sul database tutti i player che
	** sono ancora online, occupati o da aggiornare. Chiude il selettore e cancella l'oggetto remoto
	** per la registrazione. Chiude il threadpool con le sfide.
	*/
	private void close() {
		if(!closed) {
			closed=true;	
			log("Server close: START...");

			try {
				UnicastRemoteObject.unexportObject(reg,true);
			}
			catch(NoSuchObjectException e) {
				log("Server close: NoSuchObjectException");
			}
			log("Server close: Registry closed!");

			gamePool.shutdownNow();
			log("Server close: GamePool closed!");

			if(!playerMap.isEmpty()) {
				synchronized(dbLock) {
					try(FileReader fr=new FileReader(dbName)) {
						JsonObject obj=new Gson().fromJson(fr,JsonObject.class);
						synchronized(playerLock) {
							for(Map.Entry<String,Player> e : playerMap.entrySet()) {
								if(e.getValue().getState()==Player.State.ONLINE || e.getValue().getState()==Player.State.BUSY || e.getValue().getState()==Player.State.UPDATE) {
									obj.remove(e.getKey());
									obj.add(e.getKey(),new Gson().toJsonTree(e.getValue().getInfo()));
									log("Server close: saving "+e.getKey()+"...");
								}
							}
						}
						try(FileWriter fw=new FileWriter(dbName)) {
							new Gson().toJson(obj,fw);
						}
						catch(IOException e1) {
							log("Server close: IOException");
						}
						log("Server close: saving db...");
					}
					catch(IOException e) {
						log("Server close: IOException");
					}
				}
			}
			
			try {
				sel.close();
			}
			catch(IOException e) {
				log("Server close: IOException");
			}
			log("Server close: Selector closed!");
			log("Server close: COMPLETE!");
		}
	}

}