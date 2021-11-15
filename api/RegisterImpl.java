import java.util.Map;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/*
** RegisterImpl è l'implementazione dell'interfaccio Register. Questa classe implementa
** la registrazione dei nuovi utenti al server Word Quizzle tramite RMI.
** @Attributi:
**		-playerMap: è il riferimento alla Map implementata all'interno del server principale.
**		-db: è il nome del database json nel quale salvare gli utenti registrati.
**		-lock: è l'oggetto utilizzato per garantire la mutua esclusione nell'accesso al database.
*/

public class RegisterImpl extends UnicastRemoteObject implements Register {
	private Map<String,Player> playerMap;
	private String db;
	private Object lock;

	public RegisterImpl(Map<String,Player> m,String filename,Object l) throws RemoteException {
		playerMap=m;
		db=filename;
		lock=l;
	}

	/*
	** E' il metodo che verrà richiamato dall'oggetto remoto per la registrazione di un nuovo utente.
	** @param usr: è il nome con il quale l'utente vuole registrarsi.
	** @param psw: è la password scelta dall'utente in fase di registrazione.
	** @return: il metodo ritorna un booleano che rappresenta l'andata a buon fine o meno della
	**			registrazione. Il ritorno è true se la registrazione va a buon fine, mentre è 
	**			false se la password è null, se il nome utente è già esistente oppure se viene sollevata
	**			un'eccezione.
	*/
	
	public synchronized boolean register(String usr,String psw) throws RemoteException {
		System.out.println("Start register "+usr+"...");
		if(psw==null) {
			System.out.println("Register "+usr+": password null");
			return false;
		}

		// Controllo nella Map locale.
		if(playerMap.containsKey(usr)) {
			System.out.println("Register "+usr+": username already exists");
			return false;
		}

		// Controllo nel database.
		else {
			synchronized(lock) {
				try(FileReader fr=new FileReader(db)) {
					JsonObject obj=new Gson().fromJson(fr,JsonObject.class);
					if(obj.get(usr)!=null) {
						System.out.println("Register "+usr+": username already exists");
						return false;
					}
					else {
						try(FileWriter fw=new FileWriter(db)) {
							Player p=new Player(usr,psw);
							obj.add(usr,new Gson().toJsonTree(p.getInfo()));
							new Gson().toJson(obj,fw);
							playerMap.put(usr,p);
							System.out.println(usr+" registered!");
							return true;
						}
						catch(IOException e) {
							System.out.println("Register "+usr+": IOException");
							return false;
						}
					}
				}
				catch(IOException e) {
					System.out.println("Register "+usr+": IOException");
					return false;
				}
			}
		}
	}
}