# PulseCast

Lecteur de podcasts Android natif (Kotlin, Jetpack Compose, Media3, Room),
sans publicité. Développement séquencé en 4 phases ("Gauntlet Loop"),
suivi d'itérations d'amélioration.

## État actuel : build fonctionnel confirmé, itération visuelle + pliables en cours

Les 4 phases du protocole sont livrées et l'APK a été confirmé
fonctionnel sur appareil réel :
- **Phase 1** : Gradle multi-module, CI GitHub Actions, couche Room (`PodcastEntity`/`EpisodeEntity`/DAOs), parseurs `RssParser` et `OpmlParser`. Détails : `PHASE1_NOTES.md`.
- **Phase 2** : `PlaybackService` (`MediaSessionService` + `ExoPlayer`), vitesse 0.8x-2.5x avec préservation du pitch, 5 sauts temporels dédiés, focus audio délégué à ExoPlayer, sauvegarde périodique de la position. Détails : `PHASE2_NOTES.md`.
- **Phase 3** : système de thèmes complet (`AppTheme`, `PulseCastTheme`, `ThemeSelector`, `PulseCastSurface`, `PaletteAccentExtractor`). Détails : `PHASE3_NOTES.md`.
- **Phase 4** : interface complète (bibliothèque, épisodes, mini-lecteur, grand lecteur). Détails : `PHASE4_NOTES.md`.
- **Itération actuelle** : améliorations visuelles (fond flouté Verre Dépoli enfin rendu, illustration encadrée par le thème actif, transitions en fondu) et support des pliables book-style (Honor Magic V2 : mode tabletop dans le grand lecteur). Détails : `AMELIORATIONS_NOTES.md`.

## Compiler et essayer l'application

Le workflow GitHub Actions (voir plus haut) reste le moyen de référence
pour obtenir un APK à jour : aucune compilation locale n'a été possible
pendant le développement de ce projet, seul un retour utilisateur sur
appareil réel a confirmé le bon fonctionnement du premier build.

## Compiler sans Android Studio

1. Aller dans l'onglet **Actions** du dépôt GitHub.
2. Sélectionner le workflow **Build PulseCast APK**.
3. Cliquer sur **Run workflow**, choisir `debug` ou `release`.
4. Récupérer l'APK dans les artefacts du run une fois le job terminé.

## Limites connues et pistes d'évolution

Voir `PHASE4_NOTES.md` et `AMELIORATIONS_NOTES.md` pour le détail complet. En résumé :
- Pas d'import OPML par UI (le parseur existe depuis la Phase 1, sans écran dédié).
- "Bottom sheet extensible" = fondu enchaîné mini-lecteur/grand lecteur, pas un morphing continu pendant le geste de glisser.
- Pas de nouvelle tentative automatique si l'import d'un flux RSS échoue.
- Pas de mise en page deux-volets pour l'écran totalement déplié à plat (seul un plafonnement de largeur a été fait).
- Les changements les plus récents (pliables, fond flouté) n'ont pas pu être vérifiés par une compilation locale — voir l'avertissement en tête de `AMELIORATIONS_NOTES.md`.
