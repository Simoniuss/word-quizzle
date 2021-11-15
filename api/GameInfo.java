import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.util.Timer;

/*
** La classe GameInfo rappresenta le informazioni di un utente che ha lanciato una sfida,
** in modo da poter essere recuperate quando il server Word Quizzle riceverà una risposta
** dall'utente sfidato.
** @Attributi:
**		-key: è la SelectionKey associata all'utente che ha lanciao la sfida.
**		-timer: è la variabile Timer che rappresenta il tempo di attesa massimo dell'utente
**				che lancia la sfida. Dalla classe GameInfo il Timer può essere solo cancellato.
**/

public class GameInfo {
	private SelectionKey key;
	private Timer timer;

	public GameInfo(SelectionKey k,Timer t) {
		key=k;
		timer=t;
	}

	public SocketChannel getSocket() {
		return (SocketChannel)key.channel();
	}

	public SelectionKey getKey() {
		return key;
	}

	public void cancelTimer() {
		timer.cancel();
	} 
}