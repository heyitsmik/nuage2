package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;

public interface ServiceInterface extends Remote {
	void signUpRepartiteur(String username, String password) throws RemoteException;
	void signUpServer(String hostAddress, int operationCapacity) throws RemoteException;
	boolean authenticate(String username, String password) throws RemoteException;
	Map<String, Integer> getServers() throws RemoteException;
}
