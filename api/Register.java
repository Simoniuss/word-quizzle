import java.rmi.Remote;
import java.rmi.RemoteException;

/*
** Register rappresenta l'interfaccia dell'oggetto remoto RegisterImpl che gestirà
** le registrazioni degli utenti al server Word Quizzle tramite RMI.
*/

public interface Register extends Remote {
	String SERVICE_NAME="Register";
	public boolean register(String usr,String psw) throws RemoteException;
}