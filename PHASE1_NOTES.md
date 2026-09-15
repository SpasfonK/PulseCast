# Gauntlet Check 1 — Auto-audit Phase 1

## 1. Syntaxe Gradle
- Configuration Kotlin DSL, plugins déclarés une fois à la racine (`apply false`) puis appliqués dans `app/build.gradle.kts`.
- Kotlin 2.0.20 → le plugin `org.jetbrains.kotlin.plugin.compose` est requis en plus de `kotlin-android` (le compilateur Compose n'est plus embarqué dans le plugin Kotlin depuis la 2.0) : présent.
- `ksp { arg("room.schemaLocation", ...) }` configuré pour l'export de schéma Room, utile dès qu'une migration sera nécessaire.
- Versions de dépendances fixées sur des releases stables connues (AGP 8.6.0, Compose BOM 2024.09.00, Media3 1.4.1, Room 2.6.1). Elles ne sont pas forcément les toutes dernières patch disponibles aujourd'hui : à bumper librement, la CI a accès au réseau pour résoudre n'importe quelle version plus récente.

## 2. Workflow GitHub Actions
- **Point d'attention volontaire** : le dépôt ne contient pas de `gradle-wrapper.jar` binaire. Ce fichier est un `.jar` compilé — je ne peux pas le produire de façon fiable comme texte, et le fabriquer à la main aurait été le genre de raccourci que le protocole interdit.
  → À la place, le workflow installe Gradle via `gradle/actions/setup-gradle@v4` puis exécute `gradle wrapper --gradle-version 8.9` pour régénérer un wrapper propre à chaque run, avant d'appeler `./gradlew assembleDebug`/`assembleRelease` comme demandé.
  → Si tu ouvres le projet dans Android Studio en local, celui-ci régénère aussi automatiquement le wrapper au premier sync (comportement standard, pas de manipulation supplémentaire nécessaire).
- Déclenchement manuel (`workflow_dispatch`) avec choix `debug`/`release` : conforme.
- Cache Gradle, détection dynamique du chemin de l'APK généré, publication via `upload-artifact@v4` avec `if-no-files-found: error` (échoue bruyamment plutôt que silencieusement si l'APK est introuvable).

## 3. Requêtes SQL réactives (Room)
- `observeEpisodesForPodcast` / `observeUnplayedForPodcast` / `observePlayedForPodcast` acceptent un `filterPattern: String?` : `NULL` désactive le filtre, une valeur non-nulle applique un `LIKE '%...%'` sur le titre — correspond au filtre par mot-clé décrit (ex. "Intégrale").
- Règle de complétion à 95 % implémentée directement dans la requête `updatePlaybackPosition` (calcul de pourcentage en SQL, division protégée contre `durationMs = 0`), pour garantir l'atomicité avec l'écriture de la position.
- Contrainte `UNIQUE` sur `audio_url` + `OnConflictStrategy.IGNORE` : les ré-imports d'un flux déjà connu n'écrasent pas la progression de lecture existante.

## 4. Tolérance aux flux XML mal formés
- `RssParser` ignore toute balise/namespace non géré (Podcasting 2.0, itunes, media, atom) sans interrompre le parsing.
- `enclosure` sans `length` ou avec un `type` absent est accepté (seuls `url` et un `type` commençant par `audio` sont exploités).
- 5 formats de date testés séquentiellement, retombant sur `0L` sans exception si aucun ne correspond.
- Une exception levée par le pull-parser en cours de flux (XML tronqué) arrête proprement la boucle et renvoie les épisodes déjà collectés, plutôt que de faire échouer tout l'import.
- `OpmlParser` gère nativement les `<outline>` imbriqués (dossiers/catégories) puisque le parcours n'est pas récursif mais linéaire sur le flux d'événements.

## Limite connue, à surveiller en Phase 2+
- Dans `RssParser`, le titre du `<channel>` est capturé sur le premier `<title>` rencontré hors `<item>`. Si un flux atypique place un `<image><title>` avant le `<title>` du channel, le titre du podcast pourrait être erroné. Non observé sur les flux standards testés mentalement (RSS 2.0 classique + Podcasting 2.0) ; à durcir si un flux réel pose problème.
