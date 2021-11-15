import java.util.TimerTask;

/*
** TimeExpired è il thread che viene eseguito alla scadenza del Timer avviato all'inizio
** della sfida tra due utenti. TimeExpired setta un flag di controllo a true.
** @Attributi:
**		-flag: è il flag di controllo, rappresentato come un array di una posizione.
*/

public class TimeExpired extends TimerTask {
	private boolean[] flag;

	public TimeExpired(boolean[] f) {
		flag=f;
	}

	public void run() {
		flag[0]=true;
	} 
}