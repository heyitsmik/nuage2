package repartiteur;

import java.io.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
	private ExecutorService executorService = Executors.newFixedThreadPool(5000);

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
			// Collections.sort(serversConfigs, Comparator.comparingInt(ServerConfig ::getOperationCapacity).reversed());

			// Loader les stubs des serveurs
			for (ServerConfig serverConfig : serversConfigs) {
				ServerInterface serverStub = this.loadServerStub(serverConfig.getServerHostname(), serverConfig.getPort());
				this.servers.put(serverStub, serverConfig);
			}

			// MODE SÉCURISÉ
			// Le résultat des serveurs de calcul est considéré bon et valide.
			// Le répartiteur n'a donc besoin que d'une seule réponse de la part d'un serveur.
			if (secureMode) {
				List<CompletableFuture<Integer>> futureSubResults = new ArrayList<>();
	
				// Envoyer concurremment les tâches à chaque serveur
				while (!operations.isEmpty()) {
					for (Map.Entry<ServerInterface, ServerConfig> server : servers.entrySet()) {
						ServerInterface serverStub = server.getKey();
						ServerConfig serverConfig = server.getValue();
		
						// Transférer les opérations de la liste principale à la sub liste envoyée au serveur courant
						List<String> subOperations = new ArrayList<String>();
						int operationsSize = operations.size();
						
						for (int i = 0; i < serverConfig.getOperationCapacity() && i < operationsSize; i++) {
							subOperations.add(operations.remove(0));
						}

						futureSubResults.add(executeSubOperationsSecure(serverStub, subOperations));

						if (operations.isEmpty()) {
							break;
						}
					}
				}

				// Attendre la complétion de chaque tâche (avec l'appel "get" bloquant)
				for (CompletableFuture<Integer> futureSubResult : futureSubResults) {
					try {
						futureSubResult.get();
					} catch (InterruptedException | ExecutionException e) {
						System.out.println("Erreur: " + e.getMessage());
					}
				}

				// Combiner les résultats de chaque tâche
				int result = 0;
				for (CompletableFuture<Integer> futureSubResult : futureSubResults) {
					try {
						int subResult = futureSubResult.get();
						result += subResult;
						result %= 4000;
					} catch (InterruptedException | ExecutionException e) {
						System.out.println("Erreur: " + e.getMessage());
					}
				}
	
				System.out.println(result);
				executorService.shutdown();
			} else {
				// MODE NON-SÉCURISÉE
				// Le système ne fait pas confiance aux serveurs de calcul.
				// Le répartiteur considère alors une réponse comme étant valide que si
				// deux serveurs de calcul sont d'accord sur la même réponse à un même travail.
	
				// Une tâche peut être envoyée à plusieurs serveurs. Le nombre limite d'opérations correspond donc
				// à la capacité la plus faible trouvée parmi les serveurs.
				int maxOperations = serversConfigs.get(serversConfigs.size() - 1).getOperationCapacity();
				int result = 0;
	
				while (!operations.isEmpty()) {
					List<String> subOperations = new ArrayList<String>();
		
					for (int i = 0; i < operations.size() && i < maxOperations; i++) {
						subOperations.add(operations.remove(0));
					}
					
					result += this.executeSubOperations(subOperations);
					result %= 4000;
				}
	
				System.out.println(result);
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

			bufferedReader.close();
		} catch (IOException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	private CompletableFuture<Integer> executeSubOperationsSecure(ServerInterface server, List<String> subOperations) {
		CompletableFuture<Integer> futureSubResult = new CompletableFuture<>();
		executorService.submit(() -> {
			try {
				futureSubResult.complete(server.calculate(subOperations));
			} catch (RemoteException e) {
				System.out.println("Erreur: " + e.getMessage());
			}
		});
		return futureSubResult;
	}

	private int executeSubOperations(List<String> subOperations) {
		// Une tâche est envoyée à un serveur aléatoire.
		Random random = new Random();
		List<ServerInterface> keys = new ArrayList<>(this.servers.keySet());

		// Une tâche n'est jamais envoyée deux fois au même serveur.
		ServerInterface firstRandomServer = keys.remove( random.nextInt(keys.size()) );

		try {
			if (!keys.isEmpty()) {
				
				List<Integer> results = new ArrayList<>();
				int firstResult = firstRandomServer.calculate(subOperations);
				results.add(firstResult);
				
				ServerInterface otherRandomServer = keys.remove( random.nextInt(keys.size()) );
				int otherResult = otherRandomServer.calculate(subOperations);
				
				while (!results.contains(otherResult)) {
					results.add(otherResult);
	
					if (!keys.isEmpty()) {
						otherRandomServer = keys.remove( random.nextInt(keys.size()) );
						otherResult = otherRandomServer.calculate(subOperations);
					} else {
						// Tous les serveurs ont été utilisé.
						// Retourner le premier résultat par défaut si tous les résultats obtenus diffèrent.
						return firstResult;
					}
				}
	
				// Retourner un résultat à l'instant où il a été obtenu par deux serveurs différents
				results.add(otherResult);
				return otherResult;
			} else {
				// Aucune validation peut être faite s'il n'y a qu'un serveur.
				return firstRandomServer.calculate(subOperations);
			}
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
			return Integer.MAX_VALUE;
		}
	}

}
