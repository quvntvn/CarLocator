# Migration vers Android 16 (API 36)

Branche : `chore/target-sdk-36` — obligation Play Store : toute nouvelle version
publiée après le **31 août 2026** doit cibler l'API 36.

## 1. Diff des versions

### Modifié

| Élément | Avant | Après |
|---|---|---|
| Android Gradle Plugin | `8.4.0` | **`8.13.2`** |
| `compileSdk` | `35` | **`36`** |
| `targetSdk` | `35` | **`36`** |
| `versionCode` | `13` | **`14`** |
| `versionName` | `1.2.3` | **`1.2.4`** |
| `androidx.glance:glance-appwidget` | `1.1.0` | **supprimée** (inutilisée) |
| `android.newDsl` (gradle.properties) | `false` | **supprimée** (inconnue d'AGP 8.13.2) |
| `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` | absente | **déclarée** |

### Volontairement inchangé

| Élément | Version | Raison |
|---|---|---|
| Gradle wrapper | `9.1.0` | Déjà compatible AGP 8.13.2 |
| Kotlin | `2.0.0` | Rien dans l'API 36 ne l'exige ; un bump forcerait un bump du Compose BOM |
| KSP | `2.0.0-1.0.21` | Aligné sur Kotlin 2.0.0 |
| `minSdk` | `24` | Hors périmètre |
| Room | `2.6.1` | Compile et tourne sans adaptation en compileSdk 36 |
| WorkManager | `2.9.0` | Idem — voir la réserve en §2.4 |
| Compose BOM | `2024.04.01` | Idem |
| maps-compose / play-services-maps | `4.4.1` / `19.0.0` | 4 versions majeures de retard, APIs cassantes, aucun gain API 36 |
| `android.r8.strictFullModeForKeepRules=false` et les 10 autres `android.*` | — | **Options vivantes d'AGP 8.13.2**, vérifiées une par une dans les jars ; les retirer changerait le comportement de R8 en release |

### Vérifications sur le binaire

```
$ aapt2 dump badging app-release-unsigned.apk
package: name='com.quvntvn.carlocator' versionCode='14' versionName='1.2.4'
         compileSdkVersion='36' compileSdkVersionCodename='16'
targetSdkVersion:'36'
```

- **Aucun `.so` dans l'APK** (`mergeNativeLibs NO-SOURCE`) → la contrainte
  d'alignement sur pages de 16 Ko est **sans objet** pour ce projet.
- Taille APK release : `1 626 364` → `1 502 109` octets (−121 Ko, retrait de glance).

## 2. Changements de comportement à valider

Chaque point = une chose à valider à la main, dans l'ordre de risque décroissant.

### 2.1 Démarrage du service de trajet en arrière-plan — **risque le plus élevé**
`CarBluetoothReceiver` et `MyCompanionDeviceService` appellent `startForegroundService()`
alors que l'app est en arrière-plan ; la permission d'exemption Companion, jusqu'ici
absente, est désormais déclarée — à vérifier que le flux marche toujours, et mieux.

### 2.2 Quotas d'exécution des jobs (Android 16)
`SafetyNetWorker` (périodique 15 min) est soumis à des quotas resserrés selon l'*app
standby bucket* : si l'app est peu ouverte, le filet de sécurité se déclenchera plus
rarement qu'avant — dégradation progressive, invisible en test court.

### 2.3 Orientation et redimensionnement forcés sur grand écran
En API 36, `screenOrientation` / `resizeableActivity` / `maxAspectRatio` sont ignorés
sur les écrans ≥ 600 dp ; l'app n'en déclarait aucun, mais l'activité devient
librement rotative et redimensionnable sur tablette et pliable.

### 2.4 Edge-to-edge
Aucun changement attendu : l'app était déjà en `targetSdk 35` donc déjà edge-to-edge
depuis Android 15, elle n'utilise pas `windowOptOutEdgeToEdgeEnforcement`, et
`MainScreen` applique déjà `statusBarsPadding()` en haut et `navigationBarsPadding()`
en bas — à confirmer visuellement uniquement.

### 2.5 `window.statusBarColor` (Theme.kt:56)
Déjà sans effet sur Android 15+, toujours actif sur Android ≤ 14 (le no-op dépend de
la version de l'OS, pas du targetSdk) — code laissé en place exprès, à ne retirer
qu'après validation visuelle sur un appareil Android 14.

### 2.6 Retour prédictif
Aucun impact : pas de `onBackPressed()` surchargé, pas de `OnBackPressedCallback`,
`ComponentActivity` gère nativement.

### 2.7 Notifications « Live Update »
Aucune régression attendue : `POST_PROMOTED_NOTIFICATIONS` était déjà déclarée et
`HyperIslandHelper` visait déjà l'API 36 par littéraux — compileSdk 36 ne fait que
rendre les constantes officiellement disponibles.

## 3. Checklist de test manuel

Signature requise pour installer : les APK release produits ici sont **non signés**
(aucun `signingConfig` dans Gradle) — passer par Android Studio pour un build signé.

### A. Poco F8 Ultra — HyperOS 3.1 / Android 16 (appareil principal)

| # | Écran / action | À observer | ✔ |
|---|---|---|---|
| A1 | Se connecter au Bluetooth de la voiture, **app fermée** | La notif de trajet apparaît ; le service démarre bien depuis l'arrière-plan (§2.1) | ☐ |
| A2 | Pendant le trajet, écran allumé | Pastille Hyper Island présente, vitesse rafraîchie ~2×/s | ☐ |
| A3 | Pendant le trajet, écran éteint puis rallumé | La pastille ne s'actualise pas sur l'AOD, reprend au réveil | ☐ |
| A4 | Couper le Bluetooth de la voiture | Notif « Garée », pastille « 📍 Garée » ~2 s, puis service arrêté | ☐ |
| A5 | Rouvrir l'app | Le marqueur est à la bonne position, date de stationnement correcte | ☐ |
| A6 | Bouton « Reprendre le trajet » | Le service redémarre, la notif revient | ☐ |
| A7 | Écran principal | Menu du haut sous la barre d'état, carte info au-dessus de la barre de navigation, rien de tronqué (§2.4) | ☐ |
| A8 | Redémarrer le téléphone, ne pas ouvrir l'app, puis se connecter à la voiture | Le suivi repart (BootCompletedReceiver + SafetyNet) | ☐ |
| A9 | Laisser l'app fermée 24–48 h puis se connecter | Le suivi repart quand même (§2.2 — dégradation possible des quotas) | ☐ |

### B. Tablette ou pliable Android 16 (ou émulateur ≥ 600 dp) — **§2.3**

| # | Écran / action | À observer | ✔ |
|---|---|---|---|
| B1 | Écran principal en **paysage** | Le menu du haut et la carte d'info du bas ne se chevauchent pas | ☐ |
| B2 | Rotation portrait ↔ paysage en continu | Pas de crash, position de caméra conservée | ☐ |
| B3 | **Garage** (dialogue) en paysage | La liste (hauteur figée à 200 dp) reste scrollable et les boutons atteignables | ☐ |
| B4 | **Réglages** (dialogue) en paysage | Contenu scrollable jusqu'en bas, aucun bouton hors écran | ☐ |
| B5 | **Tutoriel** en paysage | Idem | ☐ |
| B6 | Mode fenêtré / écran partagé | Pas de crash, mise en page dégradée acceptable | ☐ |
| B7 | Pliable : plier/déplier pendant un trajet | Le service survit, la notif reste | ☐ |

### C. Appareil Android 14 ou moins (régression) — **§2.5**

| # | Écran / action | À observer | ✔ |
|---|---|---|---|
| C1 | Écran principal | Barre d'état bien noire (et non transparente sur la carte) | ☐ |
| C2 | Cycle connexion / déconnexion Bluetooth complet | Comportement identique à la 1.2.3 | ☐ |
| C3 | Android 11 (API 30) si disponible | Pas de crash au démarrage du foreground service | ☐ |

### D. Permissions

| # | Écran / action | À observer | ✔ |
|---|---|---|---|
| D1 | Première installation | Dialogue de divulgation localisation, puis demandes fine → background | ☐ |
| D2 | Refuser la localisation en arrière-plan | L'app ne crashe pas, message clair | ☐ |
| D3 | Révoquer BLUETOOTH_CONNECT en cours d'usage | Pas de `SecurityException` | ☐ |

## 4. Point ouvert

`./gradlew lint` remonte **11 erreurs**, toutes **préexistantes** : nombre et contenu
identiques sur `main` avec AGP 8.4.0 / compileSdk 35, et inchangés après le passage à
l'API 36. Elles ne sont donc pas causées par cette migration et n'ont pas été traitées
ici (aucune ligne de base ni suppression lint n'a été ajoutée).

- 6 × `MissingPermission` — dont 2 signalant l'absence réelle de
  `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, requise par
  `startObservingDevicePresence()` (`BootCompletedReceiver:51`, `MainViewModel:434`)
- 2 × `NewApi` — `startObservingDevicePresence()` exige l'API 31 alors que `minSdk` = 24
- 3 × `MissingTranslation` — `location_disclosure_title`, `location_disclosure_body`,
  `continue_label` absentes de `values-fr`

À traiter dans un lot séparé.
