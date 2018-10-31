package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;

public interface ServerInterface extends Remote {
	boolean authenticate(String username, String password) throws RemoteException;
	boolean isOverloaded(int nbOperations) throws RemoteException;
	int calculate(List<String> operations) throws RemoteException;
}
