/*
** La classe MainWQS avvia il WQServer, settandone le impostazioni principali.
** @Attributi:
**		-PORT: porta sulla quale si mette in ascolto il server. Su PORT+1 si mette in
**				ascolto i registry.
**		-DICT: nome del file dizionario.
**		-DB: nome del databse json.
**		-TIMEOUT: tempo massimo di accettazione sfida.
*/

public class MainWQS {
	static final int PORT=2020;
	static final String DICT="Dictionary.txt";
	static final String DB="db.json";
	static final int TIMEOUT=15000;

	public static void main(String[] args) {

		WQServer server=new WQServer(DICT,DB,TIMEOUT);
		server.setup(PORT,PORT+1);
		server.start();
	}
}