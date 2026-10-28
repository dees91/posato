# Politique de confidentialité de Posato

En vigueur à compter du 22 octobre 2026.

Posato n’envoie pas à son développeur vos ensembles de pause, votre liste de
sites, vos choix d’apps, vos sessions, vos programmations ni les données de
votre espace de travail. Il n’y a pas de compte Posato, pas d’outil d’analyse,
pas de publicité et pas de serveur exploité par Posato. Cette politique couvre
Posato pour Mac, iPhone, Linux et Android. La synchronisation facultative par
iCloud ou par dossier, le blocage sur chaque appareil et les requêtes de mise
à jour sur macOS sont décrits ci-dessous.

## Données que Posato conserve sur vos appareils

Pour fonctionner, Posato enregistre sur chaque appareil :

- vos ensembles de pause : leur nom, l’ensemble défini par défaut, les
  domaines de sites que contient chacun et les apps que vous avez choisies
  pour chacun sur cet appareil, enregistrées sur iPhone sous forme de
  sélections fournies par le système que Posato ne peut pas lire comme des
  noms d’apps ; sur Mac, Linux et Android, Posato enregistre de quelles apps
  il s’agit ;
- vos sessions : leur début et leur fin, l’ensemble de pause utilisé par
  chacune et si elles ont été arrêtées plus tôt ;
- vos programmations : leur nom, leurs ensembles de pause, les jours de la
  semaine et les heures où elles se répètent, si chacune est activée, et les
  pauses programmées que vous avez ignorées ou arrêtées plus tôt ;
- des réglages, par exemple si la synchronisation est activée, le dossier
  choisi pour la synchronisation par dossier et si l’assistant macOS est
  activé ;
- lorsque la synchronisation est activée, la date de la dernière
  synchronisation terminée par cet appareil.

Ces données restent dans le stockage privé de Posato sur l’appareil jusqu’à ce
que vous les modifiiez ou les supprimiez. Sur Mac, iPhone et Linux, ces
données, à l’exception des choix d’apps sur iPhone, peuvent aussi être
incluses dans les sauvegardes de vos appareils, comme Sauvegarde iCloud, Time
Machine ou une sauvegarde de votre dossier personnel Linux, selon vos réglages
de sauvegarde. Sur Android, Posato exclut ses données des sauvegardes Android
et des transferts d’appareil. Sur Mac, placer Posato dans la Corbeille ne
supprime pas ces données ; elles restent dans le dossier de données de
l’application Posato jusqu’à ce que vous les supprimiez. Sur Linux, supprimer
le paquet ne les supprime pas non plus ; elles restent dans le dossier de
Posato parmi les données d’applications de votre dossier personnel jusqu’à ce
que vous les supprimiez.

Lorsque la synchronisation est activée, par iCloud ou par un dossier, Posato
conserve aussi l’historique des modifications synchronisées dans son stockage
privé sur chaque appareil relié : chaque ensemble de pause créé, renommé ou
supprimé, chaque site ajouté à un ensemble ou retiré d’un ensemble, l’ensemble
défini par défaut et l’ensemble utilisé par chaque session et chaque
programmation, ainsi que la date de chaque modification, y compris le début
des sessions passées, leur fin prévue et si elles ont été arrêtées plus tôt.
Les entrées antérieures à Posato 1.3 enregistrent aussi les modifications du
nom du groupe d’apps partagé utilisé à l’époque. Modifier ou supprimer un site
ou un ensemble n’efface pas ses entrées précédentes de cet historique.
L’historique reste jusqu’à ce que **Supprimer l’espace de travail** soit
terminé sur cet appareil ou que les données de Posato en soient supprimées, et
il peut être inclus dans les sauvegardes de l’appareil comme les données
ci-dessus.

Posato ne collecte pas l’historique de navigation, les pages que vous
consultez, les pages bloquées, la fréquence à laquelle vous ouvrez des apps,
des scores d’utilisation ni aucun autre relevé d’activité.

## Synchronisation iCloud

La synchronisation iCloud est facultative et désactivée tant que vous ne
choisissez pas **Synchroniser avec iCloud**.

- Vos ensembles de pause (leur nom, leurs sites et l’ensemble défini par
  défaut), dans les modifications antérieures à Posato 1.3 le nom du groupe
  d’apps partagé, le début et la fin des sessions, ainsi que vos
  programmations, y compris les pauses programmées ignorées et arrêtées, sont
  chiffrés sur votre appareil avant d’être enregistrés dans la base de données
  CloudKit privée de votre propre compte iCloud. Le développeur de Posato ne
  peut ni les lire ni y accéder.
- Chaque modification, et l’enregistrement de chaque appareil, est stocké sous
  forme d’enregistrement chiffré distinct, et ces enregistrements
  s’accumulent dans votre base de données iCloud jusqu’à ce que vous
  choisissiez **Supprimer l’espace de travail** ; Posato ne les nettoie pas
  automatiquement.
- La clé de chiffrement est partagée entre vos appareils par le trousseau
  iCloud.
- Les choix d’apps ne sont jamais synchronisés ; chaque appareil garde les
  siens.
- Apple exploite iCloud et peut observer les informations techniques
  nécessaires pour le fournir, comme l’association à votre compte, les
  horaires, la taille et le nombre des enregistrements, selon sa propre
  politique de confidentialité.

**Supprimer l’espace de travail** supprime les enregistrements synchronisés de
Posato de votre base de données iCloud, son historique des modifications
synchronisées sur l’appareil où vous le supprimez, ainsi que la clé de
l’espace de travail du trousseau iCloud : vos autres appareils perdent donc
aussi l’accès à l’espace de travail ; leurs propres copies de vos ensembles de
pause, de vos sessions et de l’historique des modifications restent sur ces
appareils jusqu’à ce que vous y supprimiez l’espace de travail. Les ensembles
de pause et les sites enregistrés sur l’appareil où vous supprimez l’espace de
travail restent sur cet appareil. Apple peut conserver des sauvegardes pendant
un certain temps selon ses propres règles. Si toutes les copies de la clé sont
perdues, les données synchronisées ne peuvent pas être récupérées.

## Synchronisation par dossier

La synchronisation par dossier est facultative et désactivée tant que vous ne
choisissez pas de dossier dans Posato. C’est la seule synchronisation sur
Linux et Android ; sur Mac et iPhone, elle remplace la synchronisation iCloud
pour cet espace de travail.

- Posato écrit un dossier `Posato` dans le dossier que vous choisissez. Il
  contient un fichier avec des identifiants aléatoires de l’espace de travail
  et, pour chaque modification, un fichier distinct chiffré sur votre appareil
  avec le même chiffrement que la synchronisation iCloud : vos ensembles de
  pause (leur nom, leurs sites et l’ensemble défini par défaut), le début et
  la fin des sessions, ainsi que vos programmations, y compris les pauses
  programmées ignorées et arrêtées. Les choix d’apps ne sont jamais
  synchronisés ; chaque appareil garde les siens.
- Ces fichiers s’accumulent dans le dossier jusqu’à ce que vous choisissiez
  **Supprimer l’espace de travail**, qui en supprime le dossier de Posato.
  Posato ne les nettoie pas automatiquement.
- Posato n’envoie rien lui-même. Le service qui garde le dossier synchronisé,
  comme Dropbox, OneDrive, iCloud Drive ou Syncthing, copie les fichiers selon
  sa propre politique de confidentialité. Il ne peut pas lire leur contenu,
  mais il peut observer des informations techniques comme les noms des
  fichiers, qui sont aléatoires, leur taille, leur nombre et le moment où ils
  changent, et il peut conserver un certain temps des versions antérieures ou
  des fichiers supprimés.
- Avec la synchronisation par dossier, chaque appareil conserve la clé de
  l’espace de travail dans un fichier du stockage privé de Posato que seul
  votre compte utilisateur peut lire, et non dans le trousseau iCloud. Sur
  Android, le fichier est en outre scellé avec une clé qui ne quitte jamais
  l’Android Keystore de ce téléphone. Sur Mac, iPhone et Linux, le fichier de
  clé peut être inclus dans les sauvegardes de l’appareil comme les données
  ci-dessus.
- Pour ajouter un appareil, Posato affiche, sur un appareil de l’espace de
  travail, un code d’association à usage unique, également sous forme de code
  QR et de lien `posato://` qui ne contient que ce code. Il place la clé de
  l’espace de travail dans le dossier, chiffrée avec une clé dérivée du code.
  Il supprime ce fichier quand le nouvel appareil a rejoint l’espace, quand
  vous fermez le code ou, une fois le code expiré au bout de 10 minutes, au
  prochain démarrage de Posato ou au prochain affichage d’un nouveau code.
  Posato affiche le code QR mais n’utilise pas votre appareil photo. Si vous
  partagez le lien par un message, un e-mail ou le presse-papiers, il transite
  par l’app que vous choisissez. Toute personne qui dispose du code ou du lien
  et peut ouvrir le dossier, ou une copie de ce fichier dans son historique de
  fichiers, peut récupérer la clé de l’espace de travail, même après
  l’expiration du code : ne les partagez donc qu’avec vos propres appareils.

**Supprimer l’espace de travail** supprime le dossier de Posato du dossier que
vous avez choisi, si cet appareil peut encore y accéder, ainsi que le fichier
de clé de l’espace de travail et l’historique des modifications synchronisées
sur l’appareil où vous le supprimez. Les autres appareils gardent leurs
propres copies de vos ensembles de pause, de vos sessions, de l’historique des
modifications et du fichier de clé jusqu’à ce que vous y supprimiez l’espace
de travail. Le service de synchronisation peut conserver des versions
antérieures des fichiers selon sa propre politique. Si toutes les copies de la
clé sont perdues, les données synchronisées ne peuvent pas être récupérées.

## Temps d’écran sur iPhone

Sur iPhone, Posato utilise le framework Temps d’écran d’Apple pour bloquer les
sites et les apps que vous choisissez. Vos choix d’apps sont des sélections
système opaques qui restent sur l’appareil ; Posato ne reçoit de Temps d’écran
ni noms d’apps ni données d’utilisation. Pour qu’une programmation puisse
démarrer pendant que Posato est fermé, Posato conserve une copie de vos
programmations, ainsi que des sites et des choix d’apps de chaque ensemble de
pause, dans un stockage partagé uniquement avec sa propre extension Temps
d’écran sur le même iPhone. Cette copie ne quitte jamais l’appareil, est
exclue des sauvegardes de l’appareil et est supprimée quand les données de
Posato ou l’espace de travail sont supprimés.

## Blocage sur Mac

Sur Mac, l’assistant d’arrière-plan de Posato bloque les sites choisis en
faisant passer le trafic du navigateur par un proxy local sur votre Mac
pendant qu’une session est active. L’assistant n’examine chaque requête que le
temps de l’autoriser ou de la bloquer, et ne l’enregistre ni ne l’envoie. Pour
afficher sa page de pause dans l’onglet Safari ou Chrome en cours, Posato peut
utiliser l’autorisation Automatisation ; il lit alors uniquement l’adresse de
l’onglet en cours à ce moment-là et n’en garde aucune trace. Posato ne
déchiffre pas le trafic HTTPS.

Si vous choisissez de lancer des sessions sur un Mac sans mot de passe
administrateur, l’assistant conserve une trace de cette autorisation dans un
fichier système que seul le système peut lire. Cette trace contient le numéro
d’utilisateur et l’identifiant système de votre compte Mac, ainsi que
l’identifiant matériel de votre Mac. Elle ne quitte jamais votre Mac et est
supprimée quand vous désactivez l’option ou supprimez Posato de ce Mac.

## Blocage sur Linux

Sur Linux, Posato installe une seule fois, avec votre mot de passe, un service
de pause qui s’exécute avec les autorisations du système. Seuls votre compte
utilisateur et le système peuvent lui envoyer des requêtes. Pendant une pause,
le service :

- ajoute chaque site en pause et son nom `www` au fichier système
  `/etc/hosts`, que les autres comptes utilisateur de l’ordinateur peuvent
  lire, et les retire à la fin de la pause ;
- ferme les programmes en pause que vous avez choisis et qui s’exécutent sous
  votre compte utilisateur, en vérifiant de quel programme il s’agit pour
  chaque processus en cours, sans lire ni conserver ce que vous y faites.

Le service conserve votre nom d’utilisateur et votre numéro d’utilisateur dans un fichier de
configuration système, et la pause qu’il applique, avec ses sites et les
emplacements des programmes, dans un fichier système que seul le système peut
lire, afin qu’un redémarrage conserve la pause. Après la fin de la pause, ce
fichier ne conserve que l’identifiant de la session jusqu’à ce que Posato le
lise. Supprimer le paquet supprime le service, rétablit `/etc/hosts` et
supprime les deux fichiers. Rien de ce que traite le service ne quitte votre
ordinateur.

## Blocage sur Android

Sur Android, Posato a besoin de plusieurs autorisations, chacune accordée dans
les paramètres d’Android :

- **VPN.** Pendant une pause qui comprend des sites, Posato fait fonctionner
  un VPN local qui ne transporte que les résolutions de noms, c’est-à-dire les
  questions que pose votre téléphone pour trouver l’adresse d’un site. Il
  répond lui-même pour les noms en pause et transmet toutes les autres
  résolutions au serveur DNS du réseau auquel vous êtes connecté, ou à Google
  Public DNS quand ce réseau n’en propose aucun que Posato puisse utiliser.
  Aucun autre trafic n’entre dans le VPN, et Posato n’enregistre ni ne
  conserve les résolutions.
- **Accès aux données d’utilisation.** Pendant une pause qui comprend des
  apps, Posato vérifie environ deux fois par seconde quelle app est au premier
  plan, afin de pouvoir masquer une app en pause par son écran de blocage. Il
  ne garde que l’app au premier plan à ce moment-là, en mémoire, et
  n’enregistre pas l’utilisation des apps.
- **Superposition à d’autres applis**, pour afficher l’écran de blocage.
- **Notifications, alarmes exactes et exécution sans restriction de
  batterie**, pour que les pauses et les programmations commencent et se
  terminent à l’heure pendant que Posato est fermé. Une notification
  silencieuse s’affiche pendant que Posato les maintient en fonctionnement.
- **Accès à tous les fichiers**, pour que Posato puisse lire et écrire dans le
  dossier que vous choisissez pour la synchronisation par dossier. Posato ne
  lit et n’écrit que dans ce dossier.

Posato lit la liste des apps que vous pouvez ouvrir depuis le lanceur, afin
que vous puissiez choisir des apps à mettre en pause ; cette liste reste sur
le téléphone. La pause qu’il applique, avec ses sites et les noms de paquet
des apps, est conservée dans le stockage privé de Posato jusqu’à la fin de la
pause, afin qu’un redémarrage la conserve.

## Exportation et importation

Vous pouvez exporter vos ensembles de pause dans un fichier et l’importer sur
un autre appareil. Le fichier contient le nom et les sites de vos ensembles de
pause, l’ensemble défini par défaut et vos programmations. Il ne contient
jamais de choix d’apps, de sessions, d’historique, de noms d’appareils ni de
clés de synchronisation. Posato écrit le fichier uniquement là où vous
l’enregistrez ou le partagez, et n’envoie rien nulle part lui-même.

- Sans code, toute personne qui reçoit le fichier peut lire vos ensembles de
  pause et vos sites.
- Avec **Protéger par un code**, le fichier est chiffré avec une clé dérivée
  d’un code à neuf caractères que Posato affiche une seule fois et ne conserve
  pas. Le code arrête un lecteur occasionnel d’un fichier arrivé entre de
  mauvaises mains, mais une personne qui dispose du fichier et consacre une
  grande puissance de calcul à deviner le code pourrait l’ouvrir : n’envoyez
  donc le fichier et le code qu’à des personnes et par des moyens de confiance.
- Sur iPhone, un fichier partagé est une copie temporaire que Posato supprime
  à la fermeture de la feuille de partage, et un fichier importé est lu à
  partir d’une copie temporaire que Posato supprime après l’avoir lue.

Une importation modifie vos ensembles de pause et vos programmations comme des
modifications faites à la main ; si la synchronisation est activée, les
modifications parviennent à vos autres appareils.

## Notifications

Sur Mac et iPhone, Posato peut afficher des avis locaux quand une pause se
termine ou commence sur un autre appareil. Ils sont créés sur votre appareil
par le système de notifications de celui-ci ; Posato n’envoie rien à un
serveur pour les produire. Vous pouvez les désactiver dans À propos de Posato
ou dans les réglages de votre appareil.

## Mises à jour sur Mac

Posato pour iPhone, Linux et Android ne recherche pas lui-même de mises à
jour. Sur Mac, Posato demande votre accord avant de rechercher
automatiquement des mises à jour. Si vous acceptez, il effectue une recherche
une fois par jour pendant que l’app est ouverte. Vous pouvez désactiver la
recherche automatique ou choisir vous-même Rechercher les mises à jour. Chaque
mise à jour nécessite votre action pour être téléchargée et installée, et
l’installation attend qu’aucune session ne soit active.

Les informations de mise à jour et les téléchargements proviennent de GitHub
Releases par l’intermédiaire de Sparkle, une bibliothèque de mise à jour
incluse dans Posato. Les requêtes ne contiennent ni votre liste de sites, ni
vos choix d’apps, ni vos sessions, ni vos données iCloud, ni identifiant
d’installation, ni profil système. Posato compare les versions et la
configuration requise sur votre Mac.

GitHub et ses prestataires de diffusion peuvent recevoir votre adresse IP,
l’heure de la requête, la ressource demandée et des informations techniques
sur la connexion. Une adresse de téléchargement identifie la version que vous
demandez. Ils traitent ces informations conformément à la
[déclaration de confidentialité de GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
GitHub publie des nombres de téléchargements agrégés ; Posato n’ajoute aucune
analyse d’usage ni aucun identifiant de suivi. L’ouverture d’un lien de
version ou d’assistance dans votre navigateur est soumise aux règles de ce
site et aux réglages de votre navigateur.

Les préférences de mise à jour sont enregistrées sur ce Mac et ne sont pas
synchronisées par iCloud. Sparkle enregistre localement l’état des recherches
de mises à jour et les fichiers de mise à jour temporaires.

## Diagnostics

Posato ne collecte ni n’envoie de diagnostics, de rapports de plantage ou de
télémétrie. En dehors de la synchronisation que vous choisissez, des
résolutions que transmet le VPN Android et des requêtes de mise à jour
décrites dans Mises à jour sur Mac, Posato n’effectue aucune requête réseau
de lui-même.
Votre système d’exploitation ou votre boutique d’apps peut collecter des
diagnostics selon les réglages de votre appareil et ses propres règles ;
Posato n’y accède pas et n’y ajoute rien.

Si vous demandez de l’aide, ne partagez que ce que vous choisissez. Merci de
ne pas inclure de noms de sites, de noms d’apps, de détails sur vos appareils,
de captures d’écran ni de journaux dans les signalements publics.

## Enfants

Posato n’est pas conçu pour gérer l’appareil d’une autre personne et ne
collecte sciemment de données sur personne.

## Modifications

Les modifications de cette politique seront publiées à la même adresse avec
une nouvelle date d’entrée en vigueur.

## Contact

Questions sur la confidentialité : privacy@posato.app
