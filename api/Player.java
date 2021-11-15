import java.nio.channels.SelectionKey;

/*
** La classe Player rappresenta il giocatore all'interno del server di Word Quizzle.
** @Attributi:
**		-username: username dell'utente.
**		-info: oggetto PlayerInfo che contiene le informazioni dell'utente.
**		-state: contiene lo stato dell'utente rispetto al server.
**		-key: è la SelectionKey associata all'utente una volta connesso al server.
**		-udpPort: è la porta UDP associata all'utente quando si connette al server.
*/

public class Player {

	/*
	** State è la classe enum che contiene i possibili stati che l'utente può avere
	** all'interno del server. Gli stati sono quattro e il loro significato è il seguente:
	** -ONLINE: utente connesso al server.
	** -OFFLINE: utente non connesso al server.
	** -BUSY: utente connesso al server ma occupato in una sfida.
	** -UPDATE: utente non connesso al server con richiesta pendente di salvataggio nel database
	**			(ad esempio a seguito di un'amicizia stretta con l'utente offline).
	*/
	public enum State {ONLINE,OFFLINE,BUSY,UPDATE;}
	private String username;
	private PlayerInfo info;
	private State state;
	private SelectionKey key;
	private int udpPort;

	public Player(String usr, PlayerInfo pi) {
		username=usr;
		info=pi;
		state=State.OFFLINE;
	}
	public Player(String usr, String psw) {
		username=usr;
		info=new PlayerInfo(psw);
		state=State.OFFLINE;
	}

	public String getUser() {
		return username;
	}

	public PlayerInfo getInfo() {
		return info;
	}

	public State getState() {
		return state;
	}

	public void setState(State s) {
		state=s;
	}

	public int getPort() {
		return udpPort;
	}

	public void setPort(int port) {
		udpPort=port;
	}

	public SelectionKey getKey() {
		return key;
	}

	public void setKey(SelectionKey k) {
		key=k;
	}
}