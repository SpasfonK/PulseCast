# Gauntlet Check 2 — Auto-audit Phase 2

## 1. Cycle de vie du service
- `onCreate()` construit l'`ExoPlayer`, l'`AudioAttributes` et la `MediaSession` une seule fois ; `onGetSession()` renvoie la session existante à tout contrôleur qui se connecte (app, Android Auto, Assistant...).
- `onDestroy()` : arrête le tracking de progression, sauvegarde une dernière fois la position, retire le listener, libère le player puis la session, annule le `serviceScope`.
- `onTaskRemoved()` n'est **pas** surchargé, volontairement : le comportement par défaut de `MediaSessionService` arrête déjà le service si la lecture n'est pas active au moment où l'app est retirée des tâches récentes, et le laisse tourner en Foreground Service sinon — exactement le comportement attendu d'un lecteur de podcasts. Réimplémenter cette logique à la main aurait dupliqué un comportement déjà correct et testé par la bibliothèque, avec un vrai risque d'introduire une divergence. `onDestroy()` couvre la sauvegarde finale dans les deux cas.

## 2. Permissions Android 13+ (notifications)
- `POST_NOTIFICATIONS` est déjà déclarée dans le manifeste (Phase 1).
- La demande **runtime** de cette permission ne peut être faite que depuis une `Activity` (`ActivityCompat.requestPermissions`) — un `Service` ne peut pas afficher de dialogue système. Elle sera donc ajoutée en Phase 4 avec `MainActivity`, pas ici.
- Sans cette permission accordée sur Android 13+, la lecture continue de fonctionner normalement (le Foreground Service démarre quand même) ; seule la notification visuelle est supprimée par le système — comportement standard de la plateforme, pas une limitation de notre code.

## 3. Tenue de la lecture écran verrouillé
- `AudioAttributes(contentType = SPEECH, usage = MEDIA)` + `handleAudioFocus = true` : ExoPlayer gère seul la perte de focus (pause sur appel entrant, ducking sur une invite de guidage GPS), sans `AudioFocusRequest` manuel.
- `setHandleAudioBecomingNoisy(true)` : la lecture se met en pause si l'utilisateur débranche son casque/déconnecte le Bluetooth.
- `setWakeMode(C.WAKE_MODE_NETWORK)` : maintien du CPU et du Wi-Fi le temps de la lecture, écran éteint.
- Les contrôles écran de verrouillage / Bluetooth / Android Auto n'ont nécessité aucun code dédié : ils découlent automatiquement du couple `MediaSession` + `ExoPlayer` correctement configuré, via le protocole standard MediaSession/MediaController.

## 4. Vitesse et sauts temporels
- Vitesse 0.8x-2.5x : `PlaybackParameters(speedDemandée, pitch = 1.0f)` — le pitch est explicitement figé à 1.0 indépendamment de la vitesse, ce qui est ce qui garantit la préservation de la hauteur de la voix.
- Les 5 sauts dédiés (-10s/-30s/+20s/+40s/+60s) sont exposés en `SessionCommand` personnalisées (`onConnect` les déclare, `onCustomCommand` les exécute) : c'est ce que l'UI Compose des Phases 3/4 appellera via `MediaController.sendCustomCommand()`.
- En complément, `setSeekBackIncrementMs(10s)` / `setSeekForwardIncrementMs(20s)` définissent les valeurs par défaut utilisées par les intégrations système génériques (Android Auto, Assistant, Bluetooth) qui ne connaissent pas nos commandes personnalisées.
- **Point de vigilance signalé** : l'API `MediaSession.ConnectionResult.AcceptedResultBuilder` est marquée `@UnstableApi` dans Media3 — j'ai vérifié sa signature exacte (constructeur à un seul argument `AcceptedResultBuilder(session)` pour la variante synchrone d'`onConnect`, par opposition à la variante asynchrone `onConnectAsync` qui utilise un constructeur à deux arguments) avant de l'utiliser, plutôt que de deviner. L'annotation `@OptIn(UnstableApi::class)` est posée sur `onConnect()` en conséquence.

## 5. Sauvegarde de la position
- Toutes les 5 secondes pendant la lecture active (`Player.Listener.onIsPlayingChanged` démarre/arrête un ticker coroutine).
- Sauvegarde immédiate à la mise en pause (on ne dépend pas du tick suivant, qui n'aura pas lieu).
- À `STATE_ENDED`, la position est explicitement forcée à `= durée` : garantit `isPlayed = true` côté DAO sans dépendre du timing du tick périodique, y compris si l'épisode se termine entre deux ticks.
- Toutes les écritures DB passent par `Dispatchers.IO`, jamais sur le thread principal.

## Limite connue, à traiter en Phase 4
- `MediaSession.Builder` n'a pas de `setSessionActivity(...)` pour l'instant (aucune `MainActivity` n'existe encore) : taper sur la notification ne fait donc rien pour l'instant. À ajouter dès que Phase 4 introduit l'écran principal.
