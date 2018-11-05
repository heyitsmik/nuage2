Pour exécuter les tests de performance, suivre les étapes suivantes en ordre:

1) Rouler le service de noms avec "./service.sh"
   - Retenir l'adresse IP affichée à l'écran.

2) Rouler le serveur avec "./serveur.sh [capacité] [taux de malice] [adresse IP du service des noms]"
   - Au préalable, se connecter sur un autre poste avec "ssh L4712-XX" (XX est le numéro de poste).

3) Rouler le répartiteur avec "./client.sh [nom du fichier d'opérations] [adresse IP du service des noms]"

Note: Le service des noms et les serveurs communiquent tous via le port 5000.