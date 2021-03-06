package serveur;

import java.net.InetAddress;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.Port;

import shared.*;

public class Server implements ServerInterface {
	
	private static int PORT = 5000;
	private static String serviceIP = "";
	private static float MALICIOUS_RATE = 0;
	private static int OPERATION_CAPACITY = 0;

	private boolean hasAuthenticatedDispatcher = false;

	private ServiceInterface serviceStub;

	public static void main(String[] args) {
		if (args.length > 0) {
			OPERATION_CAPACITY = Integer.valueOf(args[0]);

			if (args.length > 1) {
				MALICIOUS_RATE = Float.valueOf(args[1]);

				if (MALICIOUS_RATE >= 0 && MALICIOUS_RATE <= 1) {

					if (args.length > 2) {
						serviceIP = args[2];
						Server server = new Server();
						server.run();
					} else {
						System.out.println("Erreur: Aucune adresse IP pour le service des noms.");
					}
				} else {
					System.out.println("Erreur: La frequence de comportement malicieux doit etre entre 0 et 1.");
				}
			} else {
				System.out.println("Erreur: Aucun seuil de comportement malicieux.");
			}
		} else {
			System.out.println("Erreur: Aucun seuil d'operations maximal.");
		}
	}

	public Server() {
		super();
		serviceStub = loadServiceStub(serviceIP);

		try {
			serviceStub.signUpServer(OPERATION_CAPACITY, PORT);
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}

	private ServiceInterface loadServiceStub(String hostname) {
		ServiceInterface stub = null;
		try {
			Registry registry = LocateRegistry.getRegistry(hostname, PORT);
			stub = (ServiceInterface) registry.lookup("service");
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage() + "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
		return stub;
	}

	private void run() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		try {
			ServerInterface stub = (ServerInterface) UnicastRemoteObject.exportObject(this, PORT);
			Registry registry = LocateRegistry.createRegistry(PORT);
			registry.rebind("server", stub);
			System.out.println("Server ready.");
		} catch (ConnectException e) {
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}

	@Override
    public boolean authenticate(String username, String password) throws RemoteException {
        return this.serviceStub.authenticate(username, password);
	}

	@Override
	public int calculate(List<String> operations) throws ServerOverloadedException, RemoteException {
		// Vérifier si le serveur est surchargé.
		int nbOperations = operations.size();
		if (nbOperations > OPERATION_CAPACITY) {
			
			// Le taux de refus est de 100% si le nombre d'opérations excède 5x la capacité du serveur.
			if (nbOperations > 5 * OPERATION_CAPACITY) {
				throw new ServerOverloadedException("Erreur: Le serveur est surchargé. Redistribution des tâches.");
			} else {
				double refusalRate = (nbOperations - OPERATION_CAPACITY) / (4 * OPERATION_CAPACITY);
				double randomNumber = Math.random();

				if (randomNumber <= refusalRate) {
					throw new ServerOverloadedException("Erreur: Le serveur est surchargé. Redistribution des tâches.");
				}
			}
		}

		int result = 0;

		if (Math.random() > MALICIOUS_RATE) {
			for (String operation : operations) {
				String[] op = operation.split(" ");
				String name = op[0];
				int parameter = Integer.valueOf(op[1]);
	
				result += (name.equals("pell")) ? Operations.pell(parameter) : Operations.prime(parameter);
				result %= 4000;
			}
			return result;
		} else {
			return (int)(Math.random() * 1234);
		}
	}

}
