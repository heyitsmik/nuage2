package service;

import java.rmi.AccessException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

import shared.*;

public class Service implements ServiceInterface {

    private List<ServerConfig> servers = new ArrayList<>();
    private String repartiteurUsername = "";
    private String repartiteurPassword = "";

    public static void main(String[] args) {
        Service service = new Service();
        service.run();
    }

    public Service() {
        super();
    }

    private void run() {
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
        }

        try {
            ServiceInterface stub = (ServiceInterface) UnicastRemoteObject.exportObject(this, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind("service", stub);
            System.out.println("Service ready.");
        } catch (ConnectException e) {
            System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
            System.err.println();
            System.err.println("Erreur: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erreur: " + e.getMessage());
        }
    }

    @Override
	public void signUpRepartiteur(String username, String password) throws RemoteException {
        this.repartiteurUsername = username;
        this.repartiteurPassword = password;
    }

    @Override
	public void signUpServer(String hostAddress, int operationCapacity, int maliciousRate, int port) throws RemoteException {
        this.servers.add(new ServerConfig(hostAddress, operationCapacity, maliciousRate, port));
    }

    @Override
    public boolean authenticate(String username, String password) throws RemoteException {
        return (this.repartiteurUsername == username && this.repartiteurPassword == password);
    }

    @Override
    public List<ServerConfig> getServers() throws RemoteException {
        return this.servers;
    }

}
