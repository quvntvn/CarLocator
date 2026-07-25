# Migration vers Android 16 (API 36)

Branche : `chore/target-sdk-36` — obligation Play Store : toute nouvelle version
publiée après le **31 août 2026** doit cibler l'API 36.

> **État de la branche.** 12 commits vs `main` : **7** pour la migration + **5 postérieurs** —
> `86180c0` (garde `startObservingDevicePresence` en API 31/S), `40d80a1` (traductions FR),
> `9baa8f9` (`@RequiresApi(S)` sur les helpers), et 2 mises à jour de ce document.
>
> **Vérification d'intégrité** : `git diff main HEAD --stat` doit lister **8 fichiers** (le diff
> porte sur les *commits*). Ne **pas** utiliser `git diff main --stat`, qui inclut le *working tree*
> et peut afficher du bruit local non commité (ex. `.idea/deploymentTargetSelector.xml`).
>
> **Une seule permission ajoutée au manifest** : `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`.
> `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE` **n'a jamais été déclarée** (ni committée nulle part) →
> le chemin d'observation CDM (`startObservingDevicePresence` / `MyCompanionDeviceService`) reste
> **inerte** → **comportement runtime identique à la 1.2.3**. La permission ajoutée ne fait que
> légitimer un `startForegroundService()` déjà existant ; elle n'active aucun nouveau chemin.

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

### 2.1 Démarrage du service de trajet en arrière-plan — **aucun changement de comportement**
`CarBluetoothReceiver` appelle `startForegroundService()` alors que l'app est en arrière-plan ;
la permission d'exemption Companion `START_FOREGROUND_SERVICES_FROM_BACKGROUND`, jusqu'ici absente,
est désormais déclarée. Elle **légitime un appel déjà existant**, elle n'ouvre aucun nouveau chemin.
`MyCompanionDeviceService` appellerait aussi `startForegroundService()`, mais il **n'est jamais
invoqué** (observation CDM inerte — cf. l'encadré en tête). Le seul déclencheur runtime reste le
broadcast Bluetooth, exactement comme en 1.2.3 → **aucune validation en conduisant n'est requise
pour la 1.2.4.**

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

**La 1.2.4 est identique à la 1.2.3 côté logique applicative** (aucun `.kt` de flux modifié, CDM
inerte). La série A (conduite) ci-dessous n'est donc **pas un bloquant de publication** — ce sont
des smoke tests optionnels. Les seules vérifications réellement **nouvelles** tiennent au niveau
*config API 36* : grand écran (série B, §2.3) et barre d'état sur Android ≤ 14 (C1, §2.5).

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

## 4. État du lint

Trajectoire réelle : **11 → 8 → 6**, sans jamais recourir à une baseline, un `@Suppress` ou un
`lint.xml`. Les 11 de départ étaient toutes **préexistantes** (identiques sur `main` en AGP 8.4.0 /
compileSdk 35, inchangées par le passage à l'API 36).

| Étape | Commit | Effet |
|---|---|---|
| 11 → 8 | `40d80a1` | traductions FR → efface les 3 `MissingTranslation` |
| 11 → 8 | `86180c0` | garde `>= S` chez les appelants → corrige le **crash runtime** `NoSuchMethodError` sur Android 8–11, **mais ne réduit pas le lint** |
| 8 → 6 | `9baa8f9` | `@RequiresApi(S)` sur les helpers → efface les 2 `NewApi` |

### Pourquoi les 2 `NewApi` ont survécu à `86180c0` — la leçon réutilisable

Ce n'était **pas un faux positif**. Lint signalait un appel API 31 non protégé *à l'endroit où il se
trouve* — dans le helper privé `observeDevicePresence` —, ce qui était exact. Le garde posé chez les
appelants rendait le code sûr **à condition** que ces appelants soient les seuls : un invariant vrai
au moment du commit, mais **non vérifiable par l'outil** et silencieusement cassé par le prochain
appel ajouté.

> **Règle générale.** Quand un appel dépasse le `minSdk`, la contrainte se pose sur la **déclaration**
> (`@RequiresApi`), d'où elle se **propage aux appelants** — lint vérifie alors chaque garde, à chaque
> compilation. Un garde chez l'appelant seul est une garantie *supposée*, pas *vérifiée*.
>
> C'est exactement la classe d'erreur du garde `O` (26) au lieu de `S` (31) qui a survécu **13 versions
> publiées** : une hypothèse de version plausible, jamais confrontée à l'outil. Le symptôme (une erreur
> lint qu'on est tenté de qualifier de bruit) était le seul signal disponible.

### Les 6 restantes — aucune corrigée dans ce lot

| n | id | emplacements | statut |
|---|---|---|---|
| 4 | `MissingPermission` | `CarBluetoothReceiver:163`, `:164`, `GpsTracker:55`, `MainViewModel:186` | **préexistantes**. Appels BT/GPS sans check visible → `SecurityException` si la permission est révoquée après coup dans les réglages système. **Les seules pouvant crasher un vrai utilisateur** → reportées en 1.2.5 |
| 2 | `MissingPermission` | `BootCompletedReceiver:60`, `MainViewModel:436` | **absence assumée** de `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, jusqu'à la décision CDM (§5) |

## 5. Prérequis 1.2.5 — activation du chemin CDM

Le chemin d'observation CDM est **volontairement laissé inerte en 1.2.4**. L'activer (ajouter
`REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`) branche `MyCompanionDeviceService`, qui devient alors un
**second déclencheur** de `TripService` en parallèle de `CarBluetoothReceiver`. Deux corrections de
code doivent **précéder** cet ajout, sinon deux régressions apparaissent :

1. **Normaliser le MAC en majuscules** en tête de `TripService.onStartCommand`. CDM émet le MAC en
   **minuscules** (`MacAddress.toString()`), le broadcast BT en **majuscules** (`BluetoothDevice.address`).
   Sans normalisation : (a) le dédoublonnage `shouldProcessEvent` (comparaison sensible à la casse)
   échoue → double déclenchement ; (b) le lookup `getCarByMac` échoue → nom générique au lieu du nom
   de la voiture.
2. **Retirer `ACTION_STOP_AND_SAVE` du chemin CDM** (`onDeviceDisappeared`). La latence de détection
   d'absence CDM peut atteindre plusieurs minutes ; une sauvegarde déclenchée tardivement enregistre
   la position **courante** de l'utilisateur (loin de la voiture) et **écrase la vraie position de
   stationnement**. CDM ne doit servir qu'au `START` ; le broadcast BT garde seul la sauvegarde.

Ne **pas** filtrer via `isTripActive` : HyperOS tue le service agressivement, donc le flag peut être
`false` en plein trajet légitime → sauvegarde manquée.

Ordre : (1) normalisation MAC, (2) retrait `STOP_AND_SAVE` CDM, (3) **puis seulement** ajout de la
permission `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE` — c'est un **premier ajout**, pas une
réintroduction (elle n'a jamais été committée). Validation sur plusieurs trajets réels avant
publication. Branche dédiée `feat/cdm-presence`, après publication de la 1.2.4.

## 6. Ce qui se généralise

Transposable à un autre projet Kotlin/Android qui doit passer à l'API 36.

**Le bump AGP était le seul prérequis réel.** AGP 8.4.0 ne connaît pas compileSdk 36 ; une fois
l'AGP à jour (8.13.2), le passage à l'API 36 **n'a rien cassé** : aucun `.kt` modifié, aucune
nouvelle erreur lint (11 avant, 11 après, identiques). Kotlin, KSP, Room, WorkManager, Compose BOM
et les libs Maps sont restés à leur version — les mettre à jour « pendant qu'on y est » aurait
introduit des variables sans rapport avec l'objectif.

**L'ordre compte, parce qu'il isole la vraie variable :**

1. **AGP d'abord, seul.** Build vert avant de toucher au SDK. Si quelque chose casse ici, c'est
   l'outillage — pas la cible API.
2. **Puis `compileSdk` / `targetSdk` → 36, seuls.** Ce qui casse à cette étape est *causé par la
   nouvelle cible*, et rien d'autre. Dans ce projet : rien.
3. **Puis les correctifs conditionnels**, un commit par sujet, chacun justifiable
   indépendamment — et surtout **séparés de la migration**. Les correctifs `86180c0`, `40d80a1` et
   `9baa8f9` corrigent des bugs **préexistants** ; les mêler aux commits de migration aurait rendu
   impossible de répondre à « qu'est-ce que l'API 36 a changé ? ».

**Corollaires vérifiés sur ce projet :**

- **Compter avant et après.** Le même chiffre de lint des deux côtés (11 → 11) est la preuve la plus
  simple que la migration n'a rien introduit. Sans mesure initiale, cette affirmation n'est pas
  démontrable.
- **Ne pas nettoyer ce qu'on n'a pas vérifié.** Les 12 flags de `gradle.properties` ressemblaient à
  des résidus ; vérification faite dans les jars AGP, **11 sur 12 sont vivants**, dont un qui change
  le comportement de R8 en release. Un seul a été retiré.
- **Une permission déclarée ≠ un chemin actif.** `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`
  n'ayant jamais été déclarée, tout le chemin d'observation CDM est resté inerte pendant 13 versions
  sans qu'aucun log, toast ou état d'UI ne le révèle (exception avalée par un `catch(Exception)`
  muet). Vérifier qu'un chemin s'exécute, pas seulement qu'il est écrit.
- **Vérifier l'intégrité d'une branche avec `git diff main HEAD --stat`** (les *commits*), jamais
  `git diff main --stat` qui inclut le *working tree* et son bruit local.
