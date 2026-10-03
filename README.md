# Secrétaire

Application Android qui lit à voix haute les notifications reçues.

## Fonctionnalités

- **Deux modes de lecture**
  - *Nom de l'appli seulement* : « Notification de WhatsApp »
  - *Contenu complet* : « WhatsApp. Marie : On se retrouve à 19 h ? »
- **Mode par défaut + réglage par appli** : chaque appli peut suivre le réglage
  global, ou être forcée en *Nom seul*, *Complet* ou *Muet*.
- **Toutes les applis sont lues**, sauf celles que vous passez en *Muet*.
- **Conditions**
  - lire seulement quand des écouteurs (filaires, USB, Bluetooth) sont connectés ;
  - lire seulement quand l'écran est éteint ;
  - respecter le mode silencieux et Ne pas déranger (le vibreur n'empêche pas la lecture) (en NPD prioritaire,
    seules les notifications autorisées par le système sont lues) ;
  - ignorer les notifications discrètes (affichées sans son) ;
  - plage horaire silencieuse (par ex. 22:00 → 07:00).
- **Voix** : vitesse, hauteur, bouton de test, accès au choix du moteur / de la voix.
- La musique est baissée pendant la lecture, puis remise à son volume.
- Les notifications permanentes (lecture média, téléchargements…), les résumés de
  groupe et les mises à jour identiques ne sont pas relus.

- **Journal** : les 50 dernières notifications reçues, avec « lue » ou la raison
  pour laquelle elles ont été ignorées.

- **Reste actif appli fermée** : une notification permanente discrète
  (« Secrétaire lit vos notifications ») empêche Android de mettre l'appli en pause.
  Vous pouvez la masquer (appui long → désactiver la catégorie « Secrétaire actif ») :
  le service continue de tourner.

## Installation

1. Récupérer l'APK : onglet **Actions** du dépôt → dernier build → artefact
   `secretaire-debug-apk`. Ou compiler : `./gradlew assembleDebug`
   (APK dans `app/build/outputs/apk/debug/`).
2. Installer l'APK sur le téléphone (autoriser les sources inconnues si demandé).
3. Ouvrir Secrétaire et appuyer sur **Autoriser l'accès** pour lui donner l'accès
   aux notifications.
4. Si l'appli le propose, désactiver l'optimisation de batterie, sinon Android
   peut l'endormir écran éteint.

> Sur certains téléphones (Xiaomi, Huawei, Oppo, OnePlus, Samsung…), il faut aussi
> autoriser le « démarrage automatique » / « activité en arrière-plan » de l'appli
> dans les réglages de batterie du constructeur.

> Sur Android 13+, pour une appli installée hors Play Store, l'accès aux
> notifications peut être grisé : ouvrez *Paramètres → Applis → Secrétaire*, menu ⋮,
> **Autoriser les paramètres restreints**, puis réessayez.

## Technique

Kotlin, Jetpack Compose (Material 3), Android 8.0+ (minSdk 26).

- `NotificationReaderService` : `NotificationListenerService` qui filtre et lit.
- `NotificationText` : construit la phrase (gère les conversations MessagingStyle,
  remplace les liens par « lien », tronque les textes trop longs).
- `Speaker` : synthèse vocale (`TextToSpeech`) avec file d'attente et atténuation audio.
- `Settings` : réglages (SharedPreferences).
- `ui/` : écran principal et écran des réglages par appli.
