package repartiteur;

import java.io.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

import shared.*;

public class Repartiteur {

	private static String serviceHostName = "127.0.0.1";
	private static boolean secureMode = false;
	private static final String INPUT_DIRECTORY = "inputs/";
	private static List<String> operations = new ArrayList<>();

	private final String username = "username";
	private final String password = "password";

	private ServiceInterface serviceStub;
	private Map<ServerInterface, ServerConfig> serverStubs = new HashMap<>();

	public static void main(String[] args) {
		if (args.length > 0) {
			String operationsFileName = args[0];
			File operationsFile = new File(INPUT_DIRECTORY + operationsFileName);

			if (operationsFile.exists()) {
				readOperations(operationsFile);

				if (args.length > 1) {
					String secureModeArg = args[1];

					if (secureModeArg.equals("-s")) {
						secureMode = true;
					} else {
						System.out.println("Erreur: Argument pas reconnu.");
					}

					if (args.length > 2) {
						System.out.println("Erreur: Trop d'arguments.");
					}
				}

				Repartiteur repartiteur = new Repartiteur();
				repartiteur.run();
			} else {
				System.out.println("Erreur: Le fichier d'operation n'existe pas.");
			}
		} else {
			System.out.println("Erreur: Aucun fichier d'operations.");
		}
	}

	public Repartiteur() {
		super();

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		this.serviceStub = this.loadServiceStub(serviceHostName);
	}

	private void run() {
		try {
			this.serviceStub.signUpRepartiteur(this.username, this.password);
			List<ServerConfig> serversConfigs = this.serviceStub.getServers();
			for (ServerConfig serverConfig : serversConfigs) {
				ServerInterface serverStub = this.loadServerStub(serverConfig.getServerHostname(), serverConfig.getPort());
				this.serverStubs.put(serverStub, serverConfig);
			}
		}
		catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	private ServiceInterface loadServiceStub(String hostname) {
		ServiceInterface stub = null;
		try {
			Registry registry = LocateRegistry.getRegistry(hostname);
			stub = (ServiceInterface) registry.lookup("service");
			System.out.println("service");
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage() + "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
		return stub;
	}

	private ServerInterface loadServerStub(String hostname, int port) {
		ServerInterface stub = null;

		try {
			Registry registry = LocateRegistry.getRegistry(hostname, port);
			stub = (ServerInterface) registry.lookup("server");
			stub.calculate(operations);
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage() + "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}

		return stub;
	}

	static private void readOperations(File operationsFile) {
		try {
			BufferedReader bufferedReader = new BufferedReader(new FileReader(operationsFile));
	
			String operation;
			while ((operation = bufferedReader.readLine()) != null) {
				operations.add(operation);
			}
		} catch (Exception e) { // FileNotFoundException ou IOException
			System.out.println("Erreur: " + e.getMessage());
		}
	}

}
