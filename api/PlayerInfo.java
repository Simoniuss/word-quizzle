import java.util.List;
import java.util.Collections;
import java.util.LinkedList;

/*
** La classe PlayerInfo rappresenta i dati degli utenti che verranno salvati all'interno
** del database json.
** @Attributi:
**		-password: rappresenta la password dell'utente.
**		-win: numero di partite vinte dall'utente.
**		-lose: numero di partite perse dall'utente.
**		-draw: numero di partite pareggiate dall'utente.
**		-score: punteggio totale dell'utente.
**		-friends: lista degli amici dell'utente.
*/

public class PlayerInfo {
	private String password;
	private int win;
	private int lose;
	private int draw;
	private int score;
	private List<String> friends;

	public PlayerInfo(String psw) {
		password=psw;
		win=0;
		lose=0;
		draw=0;
		score=0;
		friends=Collections.synchronizedList(new LinkedList<String>());
	}

	public String getPass() {
		return password;
	}

	public int getWin() {
		return win;
	}

	public int getLose() {
		return lose;
	}

	public int getDraw() {
		return draw;
	}

	public int getScore() {
		return score;
	}

	public List<String> getFriends() {
		return friends;
	}

	public void win() {
		win++;
	}

	public void lose() {
		lose++;
	}

	public void draw() {
		draw++;
	}

	public void addScore(int s) {
		score+=s;
	}

	public boolean addFriend(String f) {
		synchronized(friends) {
			if(friends.contains(f))
				return false;
			else {
			friends.add(f);
			return true;
			}
		}
	}
}