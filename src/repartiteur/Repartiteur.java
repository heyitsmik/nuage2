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
	private Map<ServerInterface, ServerConfig> servers = new HashMap<>();

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
			// Authentifier le répartiteur auprès du service des noms
			this.serviceStub.signUpRepartiteur(this.username, this.password);

			// Récupérer les configurations des serveurs actifs
			List<ServerConfig> serversConfigs = this.serviceStub.getServers();

			// Trier en ordre décroissant les serveurs selon leur capacité de calcul
			// On veut prioriser les serveurs les plus puissants
			Collections.sort(serversConfigs, Comparator.comparingInt(ServerConfig ::getOperationCapacity).reversed());

			// Loader les stubs des serveurs
			for (ServerConfig serverConfig : serversConfigs) {
				ServerInterface serverStub = this.loadServerStub(serverConfig.getServerHostname(), serverConfig.getPort());
				this.servers.put(serverStub, serverConfig);
			}

			int result = 0;

			// Envoyer séquentiellement les tâches à chaque serveur
			while (!operations.isEmpty()) {
				for (Map.Entry<ServerInterface, ServerConfig> server : servers.entrySet()) {
					ServerInterface serverStub = server.getKey();
					ServerConfig serverConfig = server.getValue();
	
					// Transférer les opérations de la liste principale à la sub liste envoyée au serveur courant
					List<String> subOperations = new ArrayList<String>();
					for (int i = 0; i < serverConfig.getOperationCapacity() && i < operations.size(); i++) {
						subOperations.add(operations.remove(0));
					}
	
					result += serverStub.calculate(subOperations);
					result %= 4000;
				}
			}

			System.out.println(result);
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
