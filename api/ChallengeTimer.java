import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.Map;
import java.util.TimerTask;

/*
** La classe ChallengeTimer rappresenta il thread che viene eseguito alla scadenza del
** Timer lanciato dall'utente quando sfida un suo amico, nell'attesa che quest'ultimo
** accetti o rifiuti la richiesta di sfida. ChallengeTimer invia un timeout allo sfidante
** e reimposta il suo stato ad ONLINE.
** @Attributi:
**		-map: è il riferimento alla Map del server che contiene le richieste di sfida.
**		-sock: è il SocketChannel dell'utente sfidante.
**		-usr: è l'username dell'utente sfidante.
**		-player: è l'oggetto Player dell'utente sfidante.
*/

public class ChallengeTimer extends TimerTask {
	private Map<String,GameInfo> map;
	private SocketChannel sock;
	private String usr;
	private Player player;

	public ChallengeTimer(String u,Map<String,GameInfo> m,SocketChannel s,Player p) {
		usr=u;
		map=m;
		sock=s;
		player=p;
	}

	public void run() {
		try {
			if(map.containsKey(usr)) {
				map.remove(usr);
				player.setState(Player.State.ONLINE);
				ByteBuffer buf=ByteBuffer.allocate(500);
				buf.put("OK Timeout".getBytes());
				buf.flip();
				while(buf.hasRemaining())
					sock.write(buf);
				System.out.println("Server Op: "+usr+" challenge timeout");
			}
		}
		catch(IOException e) {
			System.out.println("Server Op: IOException timer");
		}
	} 
}