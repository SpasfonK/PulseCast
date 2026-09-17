# PulseCast

Lecteur de podcasts Android natif (Kotlin, Jetpack Compose, Media3, Room),
sans publicité. Développement séquencé en 4 phases ("Gauntlet Loop").

## État actuel : Phase 4 — Écrans, mini-player et interface finale (projet complet)

Les 4 phases du protocole sont livrées :
- **Phase 1** : Gradle multi-module, CI GitHub Actions, couche Room (`PodcastEntity`/`EpisodeEntity`/DAOs), parseurs `RssParser` et `OpmlParser`. Détails : `PHASE1_NOTES.md`.
- **Phase 2** : `PlaybackService` (`MediaSessionService` + `ExoPlayer`), vitesse 0.8x-2.5x avec préservation du pitch, 5 sauts temporels dédiés, focus audio délégué à ExoPlayer, sauvegarde périodique de la position. Détails : `PHASE2_NOTES.md`.
- **Phase 3** : système de thèmes complet (`AppTheme`, `PulseCastTheme`, `ThemeSelector`, `PulseCastSurface`, `PaletteAccentExtractor`). Détails : `PHASE3_NOTES.md`.
- **Phase 4** : interface complète.
  - `MainActivity` + `PulseCastApp` : bord-à-bord, permission notifications (Android 13+), assemblage thème/navigation/lecteur.
  - `PodcastLibraryScreen` : abonnements + ajout d'un podcast par URL de flux RSS.
  - `EpisodeListScreen` : onglets Non-lus/Lus réactifs, dialogue de filtrage par mot-clé.
  - `FullPlayerScreen` : grand lecteur immersif (illustration, titre en texte défilant, scrubber, 5 sauts temporels, vitesse réglable).
  - `MiniPlayer` : barre persistante extensible vers le grand lecteur (`BottomSheetScaffold`).
  - `PlaybackViewModel` : pont `MediaController` ↔ Compose, extraction d'accent dynamique (Palette API) réellement branchée.

  Détails, limites assumées et l'avertissement important sur l'absence de compilation locale dans cet environnement : `PHASE4_NOTES.md` (Gauntlet Check 4).

## Compiler et essayer l'application

Le seul moyen de vérifier que l'ensemble compile réellement est le workflow GitHub Actions (voir plus haut) : aucune compilation locale n'a été possible pendant le développement de ce projet.

## Compiler sans Android Studio

1. Aller dans l'onglet **Actions** du dépôt GitHub.
2. Sélectionner le workflow **Build PulseCast APK**.
3. Cliquer sur **Run workflow**, choisir `debug` ou `release`.
4. Récupérer l'APK dans les artefacts du run une fois le job terminé.

## Limites connues et pistes d'évolution

Voir `PHASE4_NOTES.md` pour le détail complet. En résumé :
- Pas d'import OPML par UI (le parseur existe depuis la Phase 1, sans écran dédié).
- "Bottom sheet extensible" = bascule nette mini-lecteur/grand lecteur, pas un morphing en fondu continu pendant le geste.
- Pas de nouvelle tentative automatique si l'import d'un flux RSS échoue.
- Aucun build local n'a pu être vérifié dans l'environnement de développement (voir l'avertissement en tête de `PHASE4_NOTES.md`) : le premier build réel aura lieu via le workflow GitHub Actions.
