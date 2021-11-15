import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Timer;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/*
** Challenge è il thread lanciato dal Word Quizzle server quando viene avviata una sfida
** tra due utenti. Tutta la sfida viene gestita da questo thread che prima di farla partire
** sceglie delle parole casuali da un dizionario e le traduce in inglese sfruttando le api
** del sito "mymemory". Succesivamente inizierà ad inviare le parole ai due sfidanti e confronterà
** le loro traduzioni con le traduzioni del sito assegnando dei punti. Alla fine della sfida il thread
** invia il risultato ai due utenti, aggiornando i loro punteggi. La sfida non può durare più di un tempo
** massimo che verrà controllato tramite in Timer.
** @Attributi:
**		-WORDS: è il numero di parole per la sfida.
**		-PATH: rappresenta l'url del sito al quale fare le richieste di traduzione.
**		-TIMEOUT: indica il tempo massimo della sfida.
**		-keyP1: è la SelectionKey del player 1.
**		-keyP2: è la SelectionKey del player 2.
**		-p1: è l'oggetto Player del player 1.
**		-p2: è l'oggetto Player del player 2.
**		-dict: è il nome del dizionario da consultare per scegliere WORDS parole dell sfida.
**		-dictLock: è l'oggetto che garantisce la mutua esclusione per l'accesso al dizionario.
**		-sel: è il Selector del thread per ricevere le traduzioni sia dal player 1 che dal player 2.
**		-translations: è la struttura nella quale vengono salvate le associazioni tra la parola da tradurre
**						e quella tradotta.
**		-timer: è la variabile che indica se è scattato il TIMEOUT di fine sfida.
*/

public class Challenge extends Thread {
	private static final int WORDS=5;
	private static final String PATH="https://api.mymemory.translated.net/get?langpair=it|en&q=";
	private static final int TIMEOUT=40000;
	private SelectionKey keyP1;
	private SelectionKey keyP2;
	private Player p1;
	private Player p2;
	private String dict;
	private Object dictLock;
	private Selector sel;
	private Map<String,String> translations;
	private boolean[] timer;

	public Challenge(SelectionKey sk1,SelectionKey sk2,Player pp1,Player pp2,String d,Object dL) {
		keyP1=sk1;
		keyP2=sk2;
		p1=pp1;
		p2=pp2;
		dict=d;
		dictLock=dL;
		translations=new HashMap<String,String>();
		timer=new boolean[1];
		timer[0]=false;
	}

	public void run() {
		try {
			// Le chiavi vengono "silenziate" nel selector del server principale e i canali
			// dei player vengono registrati al selettore del thread.
			keyP1.interestOps(0);
			keyP2.interestOps(0);
			sel=Selector.open();
			keyP1.channel().register(sel,SelectionKey.OP_READ);
			keyP2.channel().register(sel,SelectionKey.OP_READ);
			SocketChannel sc1=(SocketChannel)keyP1.channel();
			SocketChannel sc2=(SocketChannel)keyP2.channel();

			// Seleziono parole dal dizionario.
			synchronized(dictLock) {
				List<String> lines = Files.readAllLines(Paths.get(dict));
				for(int i=0;i<WORDS;i++) {
					// Genera un random tra 0 e lines (numero di linee max del file dizionario).
					int randLine=(int)(Math.random()*(lines.size()+1));
					translations.put(lines.get(randLine),null);
				}
			}

			// Traduzione delle parole con richieste al sito PATH.
			for(Map.Entry<String,String> e : translations.entrySet()) {
				URL url=new URL(PATH+e.getKey());
				URLConnection uc=url.openConnection();
				uc.connect();
				StringBuilder response=new StringBuilder();
				try(BufferedReader br=new BufferedReader(new InputStreamReader(uc.getInputStream()))) {
					String l=null;
					while((l=br.readLine())!=null)
						response.append(l);
				}
				JsonObject objResp=new Gson().fromJson(response.toString(),JsonObject.class);
				JsonObject trObj=objResp.getAsJsonObject("responseData");
				String tr=trObj.get("translatedText").getAsString();
				translations.put(e.getKey(),tr);
			}

			// Partenza timer sfida.
			Timer t=new Timer();
			t.schedule(new TimeExpired(timer),TIMEOUT);
			ArrayList<String> l=new ArrayList<String>(translations.keySet());
			int currP1=0;
			int currP2=0;
			int scoreP1=0;
			int scoreP2=0;
			ByteBuffer buf=ByteBuffer.allocate(500);

			// Invio prima parola ad entrambi i player.
			buf.clear();
			String first="TR "+l.get(currP1);
			buf.put(first.getBytes());
			buf.flip();
			while(buf.hasRemaining())
				sc1.write(buf);
			buf.clear();
			buf.put(first.getBytes());
			buf.flip();
			while(buf.hasRemaining())
				sc2.write(buf);

			// Lettura risposte fino alla scadenza del timer.
			while(!timer[0]) {

				// Sfida terminata.
				if(currP1==WORDS && currP2==WORDS) {
					t.cancel();
					timer[0]=true;
					checkWin(scoreP1,scoreP2);
				}

				// Continua la sfida.
				else {
					try {
						sel.select();
					}
					catch(IOException e) {
						System.out.println("Server Challenge: Select error");
					}
					Set<SelectionKey> selKeys=sel.selectedKeys();
					Iterator<SelectionKey> it=selKeys.iterator();
					while(it.hasNext()) {
						SelectionKey key=it.next();
						try {
							if(key.isReadable()) {
								SocketChannel client=(SocketChannel)key.channel();
								buf.clear();
								int r;
								r=client.read(buf);
								buf.flip();
								byte[] rcv=new byte[buf.remaining()];
								while(buf.hasRemaining())
									buf.get(rcv);

								// Ho ricevuto una risposta.
								if(r>0) {
									if(!timer[0]) {
										String[] splitMsg=new String(rcv).split(" ");
										// Errore di formato.
										if(splitMsg.length!=2 || !splitMsg[0].equals("TR")) {
											System.out.println("Server Challenge: formatError");
											buf.clear();
											buf.put("KO formatError".getBytes());
											buf.flip();
											while(buf.hasRemaining())
												client.write(buf);
										}

										// Verifico traduzione.
										else {

											//PLAYER 1.
											if(client.equals(keyP1.channel())) {
												// Risposta esatta P1.
												if(splitMsg[1].equalsIgnoreCase(translations.get(l.get(currP1)))) {
													currP1++;
													scoreP1++;
													if(currP1!=WORDS) {
														buf.clear();
														String next="TR "+l.get(currP1);
														buf.put(next.getBytes());
														buf.flip();
														while(buf.hasRemaining())
															client.write(buf);
													}
												}
												// Risposta sbagliata P1.
												else {
													currP1++;
													scoreP1--;
														if(currP1!=WORDS) {
														buf.clear();
														String next="TR "+l.get(currP1);
														buf.put(next.getBytes());
														buf.flip();
														while(buf.hasRemaining())
															client.write(buf);
													}
												}
											}

											// PLAYER 2.
											else {
												// Risposta esatta P2.
												if(splitMsg[1].equalsIgnoreCase(translations.get(l.get(currP2)))) {
													currP2++;
													scoreP2++;
													if(currP2!=WORDS) {
														buf.clear();
														String next="TR "+l.get(currP2);
														buf.put(next.getBytes());
														buf.flip();
														while(buf.hasRemaining())
															client.write(buf);
													}
												}
												// Risposta sbagliata P2.
												else {
													currP2++;
													scoreP2--;
													if(currP2!=WORDS) { 
														buf.clear();
														String next="TR "+l.get(currP2);
														buf.put(next.getBytes());
														buf.flip();
														while(buf.hasRemaining())
															client.write(buf);
													}
												}
											}
										}
									}
								}
								// Un giocatore si è disconnesso.
								if(r==-1) {
									System.out.println("Server Challenge: client disconnect");
									if(client.equals(keyP1.channel())) {
										currP1=WORDS;
										p1.setState(Player.State.OFFLINE);
									}
									else {
										currP2=WORDS;
										p2.setState(Player.State.OFFLINE);
									}
									key.cancel();
								}
							}
							it.remove();
						}
						catch(IOException e) {
							System.out.println("Server Challenge: IOException reading key");
						}
					}
				}
			}

			// Timer scaduto con uno dei due player che non hanno completato la sfida.
			if(timer[0] && (currP1!=WORDS || currP2!=WORDS)) {
				checkWin(scoreP1,scoreP2);
			}

			// Riattivo le chiavi dei player per il selettore principale nel server.
			try{sel.close();}catch(IOException e){}
			keyP1.interestOps(SelectionKey.OP_READ);
			keyP2.interestOps(SelectionKey.OP_READ);
		}
		catch(IOException e) {
			System.out.println("Server Challenge: IOException");
		}
		finally {
			// Riattivo le chiavi dei player per il selettore principale nel server e setto i player come ONLINE.
			keyP1.interestOps(keyP1.interestOps() | SelectionKey.OP_READ);
			keyP2.interestOps(keyP2.interestOps() | SelectionKey.OP_READ);
			if(p1.getState()==Player.State.BUSY) {
				p1.setState(Player.State.ONLINE);
			}
			if(p2.getState()==Player.State.BUSY) {
				p2.setState(Player.State.ONLINE);
			}
		}
	}

	/*
	** La funziona checkWin controlla chi tra i due giocatori ha vinto la sfida e lo comunica
	** ad entrambi. In caso di vittoria viene assegnato un bonus di 3 punti al vincitore.
	** @param s1: punteggio del player 1.
	** @param s2: punteggio del player 2.
	*/
	private void checkWin(int s1,int s2) {
		SocketChannel sc1=(SocketChannel)keyP1.channel();
		SocketChannel sc2=(SocketChannel)keyP2.channel();
		ByteBuffer buf=ByteBuffer.allocate(500);
		try {

			// Vittoria del player 1.
			if(s1>s2) {
				p1.getInfo().win();
				p1.getInfo().addScore(s1+3);
				p2.getInfo().lose();
				p2.getInfo().addScore(s2);
				// Risposta al vincitore.
				buf.clear();
				String resp1="OK END WIN "+p1.getUser()+":"+s1+" "+p2.getUser()+":"+s2;
				buf.put(resp1.getBytes());
				buf.flip();
				while(buf.hasRemaining())
					sc1.write(buf);
				System.out.println("Server Challenge: send result to "+p1.getUser());
				// Risposta al perdente.
				buf.clear();
				String resp2="OK END LOSE "+p2.getUser()+":"+s2+" "+p1.getUser()+":"+s1;
				buf.put(resp2.getBytes());
				buf.flip();
				while(buf.hasRemaining())
					sc2.write(buf);
				System.out.println("Server Challenge: send result to "+p2.getUser());
			}

			else {
				// Vittoria del player 2.
				if(s1<s2) {
					p1.getInfo().lose();
					p1.getInfo().addScore(s1);
					p2.getInfo().win();
					p2.getInfo().addScore(s2+3);
					// Risposta al perdente.
					buf.clear();
					String resp1="OK END LOSE "+p1.getUser()+":"+s1+" "+p2.getUser()+":"+s2;
					buf.put(resp1.getBytes());
					buf.flip();
					while(buf.hasRemaining())
						sc1.write(buf);
					System.out.println("Server Challenge: send result to "+p1.getUser());
					// Risposta al vincitore.
					buf.clear();
					String resp2="OK END WIN "+p2.getUser()+":"+s2+" "+p1.getUser()+":"+s1;
					buf.put(resp2.getBytes());
					buf.flip();
					while(buf.hasRemaining())
						sc2.write(buf);
					System.out.println("Server Challenge: send result to "+p2.getUser());
				}

				// Pareggio.
				else {
					p1.getInfo().draw();
					p1.getInfo().addScore(s1);
					p2.getInfo().draw();
					p2.getInfo().addScore(s2);
					// Risposta pareggio.
					buf.clear();
					String resp1="OK END DRAW "+s1+"-"+s2;
					buf.put(resp1.getBytes());
					buf.flip();
					while(buf.hasRemaining())
						sc1.write(buf);
					System.out.println("Server Challenge: send result to "+p1.getUser());
					// Risposta pareggio.
					buf.clear();
					String resp2="OK END DRAW "+s2+"-"+s1;
					buf.put(resp2.getBytes());
					buf.flip();
					while(buf.hasRemaining())
						sc2.write(buf);
					System.out.println("Server Challenge: send result to "+p2.getUser());
				}
			}
		}
		catch(IOException e) {
			System.out.println("Server Challenge: IOException checkWin");
		}
	}
}