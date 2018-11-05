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

			// Loader les stubs des serveurs
			for (ServerConfig serverConfig : serversConfigs) {
				ServerInterface serverStub = this.loadServerStub(serverConfig.getServerHostname(), serverConfig.getPort());

				// Authentifier avec chacun des nouveaux stubs
				if (serverStub.authenticate(this.username, this.password)) {
					this.servers.put(serverStub, serverConfig);
				}
			}

			// MODE SÉCURISÉ
			// Le résultat des serveurs de calcul est considéré bon et valide.
			// Le répartiteur n'a donc besoin que d'une seule réponse de la part d'un serveur.
			if (secureMode) {
				List<CompletableFuture<Integer>> futureSubResults = new ArrayList<>();

				while (!operations.isEmpty()) {
					// Envoyer concurremment les tâches à chaque serveur
					boolean isDivisible = this.divideAndSendSubOperationsSecure(futureSubResults);

					if (!isDivisible) {
						System.out.println("Erreur: Aucun serveur est disponible.");
						executorService.shutdown();
						return;
					}
	
					// Attendre la complétion de chaque tâche (avec l'appel "get" bloquant)
					for (CompletableFuture<Integer> futureSubResult : futureSubResults) {
						try {
							futureSubResult.get();
						} catch (InterruptedException | ExecutionException e) {
							System.out.println("Erreur: " + e.getMessage());
						}
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

				List<CompletableFuture<Integer>> futureSubResults = new ArrayList<>();
	
				while (!operations.isEmpty()) {
					// Une tâche peut être envoyée à plusieurs serveurs. Le nombre limite d'opérations correspond donc
					// à la capacité la plus faible trouvée parmi les serveurs.
					Collections.sort(serversConfigs, Comparator.comparingInt(ServerConfig ::getOperationCapacity).reversed());
					int maxOperations = serversConfigs.get(serversConfigs.size() - 1).getOperationCapacity();

					// Envoyer concurremment les tâches à chaque serveur
					boolean isDivisible = this.divideAndSendSubOperationsNonSecure(futureSubResults, maxOperations);

					if (!isDivisible) {
						System.out.println("Erreur: Aucun serveur est disponible.");
						executorService.shutdown();
						return;
					}

					// Attendre la complétion de chaque tâche (avec l'appel "get" bloquant)
					for (CompletableFuture<Integer> futureSubResult : futureSubResults) {
						try {
							futureSubResult.get();
						} catch (InterruptedException | ExecutionException e) {
							System.out.println("Erreur: " + e.getMessage());
						}
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

	private boolean divideAndSendSubOperationsSecure(List<CompletableFuture<Integer>> futureSubResults) {
		while (!operations.isEmpty()) {
			// Retourner faux s'il n'est pas possible de distribuer le calcul (c-.à-d. aucun serveur de disponible).
			if (servers.isEmpty()) {
				return false;
			}

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

		return true;
	}

	private CompletableFuture<Integer> executeSubOperationsSecure(ServerInterface serverStub, List<String> subOperations) {
		CompletableFuture<Integer> futureSubResult = new CompletableFuture<>();

		executorService.submit(() -> {
			try {
				futureSubResult.complete(serverStub.calculate(subOperations));
			} catch (ServerOverloadedException e) {
				// Exception envoyée lorsqu'un serveur est surchargé ou lorsqu'il y a un problème avec le remoting de Java RMI.
				this.handleServerOverloadedException(e, subOperations);
				futureSubResult.complete(0);
			} catch (RemoteException e) {
				// Exception envoyée lorsqu'un serveur de calcul est tué au beau milieu de l'exécution d'une tâche.
				this.handleRemoteException(e, subOperations, serverStub);
				futureSubResult.complete(0);
			}
		});

		return futureSubResult;
	}

	private boolean divideAndSendSubOperationsNonSecure(List<CompletableFuture<Integer>> futureSubResults, int maxOperations) {
		while (!operations.isEmpty()) {
			// Retourner faux s'il n'est pas possible de distribuer le calcul (c-.à-d. aucun serveur de disponible).
			if (servers.isEmpty()) {
				return false;
			}

			List<String> subOperations = new ArrayList<String>();
		
			for (int i = 0; i < operations.size() && i < maxOperations; i++) {
				subOperations.add(operations.remove(0));
			}
					
			futureSubResults.add(executeSubOperationsNonSecure(subOperations));
		}

		return true;
	}

	private CompletableFuture<Integer> executeSubOperationsNonSecure(List<String> subOperations) {
		CompletableFuture<Integer> futureSubResult = new CompletableFuture<>();

		executorService.submit(() -> {
			futureSubResult.complete(this.calculateNonSecure(subOperations));
		});

		return futureSubResult;
	}

	private int calculateNonSecure(List<String> subOperations) {
		// Une tâche est envoyée à un serveur aléatoire.
		Random random = new Random();
		List<ServerInterface> keys = new ArrayList<>(this.servers.keySet());

		// Une tâche n'est jamais envoyée deux fois au même serveur.
		ServerInterface firstRandomServer = keys.remove( random.nextInt(keys.size()) );
		List<Integer> results = new ArrayList<>();
		int firstResult = 0;
		try {
			firstResult = firstRandomServer.calculate(subOperations);
		} catch (RemoteException e) {
			// Exception envoyée lorsqu'un serveur de calcul est tué au beau milieu de l'exécution d'une tâche.
			this.handleRemoteException(e, subOperations, firstRandomServer);
			return 0;
		}  catch (ServerOverloadedException e) {
			// Exception envoyée lorsqu'un serveur est surchargé ou lorsqu'il y a un problème avec le remoting de Java RMI.
			this.handleServerOverloadedException(e, subOperations);
			return 0;
		}
		results.add(firstResult);

		try {
			if (!keys.isEmpty()) {
				ServerInterface otherRandomServer = keys.remove( random.nextInt(keys.size()) );
				int otherResult = 0;
				try {
					otherResult = otherRandomServer.calculate(subOperations);
				} catch (RemoteException e) {
					// Exception envoyée lorsqu'un serveur de calcul est tué au beau milieu de l'exécution d'une tâche.
					this.handleRemoteException(e, subOperations, otherRandomServer);
					return 0;
				}
				
				while (!results.contains(otherResult)) {
					results.add(otherResult);
	
					if (!keys.isEmpty()) {
						otherRandomServer = keys.remove( random.nextInt(keys.size()) );
						try {
							otherResult = otherRandomServer.calculate(subOperations);
						} catch (RemoteException e) {
							// Exception envoyée lorsqu'un serveur de calcul est tué au beau milieu de l'exécution d'une tâche.
							this.handleRemoteException(e, subOperations, otherRandomServer);
							return 0;
						}
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
				return firstResult;
			}
		} catch (ServerOverloadedException e) {
			// Exception envoyée lorsqu'un serveur est surchargé ou lorsqu'il y a un problème avec le remoting de Java RMI.
			this.handleServerOverloadedException(e, subOperations);
			return 0;
		}
	}

	private void handleServerOverloadedException(ServerOverloadedException e, List<String> subOperations) {
		// Rajouter les opérations non-complétées pour qu'elles soient traitées à nouveau.
		for (String operation : subOperations) {
			operations.add(operation);
		}
		
		System.out.println("Erreur: " + e.getMessage());
	}

	private void handleRemoteException(RemoteException e, List<String> subOperations, ServerInterface serverStub) {
		// Rajouter les opérations non-complétées pour qu'elles soient traitées à nouveau.
		for (String operation : subOperations) {
			operations.add(operation);
		}

		// Retirer le serveur défectueux des serveurs disponibles.
		this.servers.remove(serverStub);

		System.out.println("Erreur: " + e.getMessage());
	}

}
