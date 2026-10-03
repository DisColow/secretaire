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
- **Voix** : choix du moteur de synthèse et de la voix propres à Secrétaire (sans
  changer la voix du reste du téléphone), vitesse, hauteur, bouton de test.
- La musique est baissée pendant la lecture, puis remise à son volume.
- Les notifications permanentes (lecture média, téléchargements…), les résumés de
  groupe et les mises à jour identiques ne sont pas relus.

- **Journal** : les 50 dernières notifications reçues, avec « lue » ou la raison
  pour laquelle elles ont été ignorées.

- **Notification permanente** « Secrétaire : lecture activée / en pause », avec un bouton
  **Mettre en pause / Reprendre**. Elle empêche aussi Android de mettre l'appli en pause.
- **Tuile des réglages rapides** « Secrétaire » pour activer / désactiver d'un geste
  (bouton « Ajouter la tuile » dans l'appli sur Android 13+, sinon via la modification
  des réglages rapides).

## Installation

1. Récupérer l'APK : onglet **Actions** du dépôt → dernier build → artefact
   `secretaire-apk`. Les mises à jour s'installent par-dessus la version
   existante (même clé de signature), sans perdre les réglages.
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

## Voix neuronale hors ligne (recommandé)

Pour une voix bien plus naturelle, gratuite et sans internet, installez un moteur
[sherpa-onnx](https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html)
(fichiers `arm64-v8a-fra-…`, par ex. `fr_FR-siwis-medium`, `fr_FR-miro-high`,
`fr_FR-tom-medium` ou `supertonic-3`), puis dans Secrétaire : **Voix → Moteur**
→ sherpa-onnx, et **Tester**.

## Signature

Les APK sont signés en CI avec une clé fixe, stockée dans les secrets GitHub du dépôt
(*Settings → Secrets and variables → Actions*) :

| Secret | Contenu |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | le fichier `.jks` encodé en base64 |
| `SIGNING_STORE_PASSWORD` | mot de passe du keystore |
| `SIGNING_KEY_ALIAS` | alias de la clé |
| `SIGNING_KEY_PASSWORD` | mot de passe de la clé |

Gardez une copie de la clé en lieu sûr : sans elle, impossible de publier une mise à
jour installable par-dessus l'appli existante.

Compilation locale signée : définir `SIGNING_KEYSTORE_PATH` (chemin du `.jks`) et les
trois autres variables, puis `./gradlew assembleRelease`.

## Technique

Kotlin, Jetpack Compose (Material 3), Android 8.0+ (minSdk 26).

- `NotificationReaderService` : `NotificationListenerService` qui filtre et lit.
- `NotificationText` : construit la phrase (gère les conversations MessagingStyle,
  remplace les liens par « lien », tronque les textes trop longs).
- `Speaker` : synthèse vocale (`TextToSpeech`) avec file d'attente et atténuation audio.
- `Settings` : réglages (SharedPreferences).
- `ui/` : écran principal et écran des réglages par appli.
