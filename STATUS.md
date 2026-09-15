# STATUS.md — PulseCast (état au 15/09/2026, fin de session Kilo)

> Document de passation : à lire en priorité si vous reprenez le projet dans
> une nouvelle session. Tout ce qui suit a été vérifié factuellement.

## 1. Le projet en bref

- **PulseCast** : lecteur de podcasts Android natif (Kotlin, Jetpack Compose,
  Media3/ExoPlayer, Room, DataStore). Sans publicité. Mono-module `app/`.
- Package : `com.pulsecast.app` ; **applicationId debug = `com.pulsecast.app.debug`**
  (suffixe `.debug`) → installer les APK debug successifs conserve la base Room.
- Développé **sans compilation locale possible** (pas de JDK/SDK Android sur la
  machine). La **seule** vérification de compilation est le workflow GitHub
  Actions. Chaque itération = 1 push + 1 run CI (~3-4 min).
- 4 phases du « Gauntlet Loop » livrées avant l'arrivée de l'agent (voir
  `PHASE1..4_NOTES.md`) : Gradle+CI, PlaybackService, système de thèmes, UI.

## 2. Infra de build & dépôt

- Dépôt : https://github.com/SpasfonK/PulseCast (public, branche `master`).
- `gh` CLI authentifié (compte **SpasfonK**, scopes repo+workflow).
- Workflow : `.github/workflows/build-apk.yml` — `workflow_dispatch` avec
  entrée `build_type` (debug/release). JDK 17 temurin, Gradle 8.9 (wrapper
  régénéré en CI, le jar du wrapper est volontairement absent du dépôt).
- Déclencher un build :
  `gh workflow run build-apk.yml --ref master -f build_type=debug`
- Suivre : `gh run watch <run_id> --interval 30 --exit-status`
- Logs d'échec : `gh run view <run_id> --log-failed` (chercher `e: ` pour les
  erreurs Kotlin).
- APK = **artefact** du run (`pulsecast-debug-N`), téléchargeable ~14 jours
  depuis l'onglet Actions → run → Artifacts. Le numéro N du run correspond au
  build. Dernier build vert de cette session : **run #7** (artefact
  `pulsecast-debug-7` attendu) — voir git log pour le commit exact.

## 3. Ce qui est livré et fonctionne (confirmé par l'utilisateur)

- **Page d'accueil** (destination de démarrage) :
  - Rangée **Mes abonnements** en grille 3 colonnes (pochette, titre, badge
    du nombre d'épisodes non lus) + bouton « Tout voir » → bibliothèque.
    → *l'utilisateur valide : « l'accueil est bcp mieux ».*
  - **À la une** : top podcasts Apple (API `rss.applemarketingtools.com`,
    pays FR) avec badge de rang ; feedUrl résolu à la demande via
    `itunes.apple.com/lookup` au moment de l'abonnement.
  - 7 rangées thématiques par recherche iTunes (`term` FR + `country=FR`).
    ⚠ Le paramètre `genreId` de l'API est **ignoré** (vérifié) : ne pas
    compter dessus, rester sur des `term` localisés.
  - Recherche intégrée (barre en haut → résultats → fiche podcast).
  - Fiche podcast (ModalBottomSheet) avec bouton S'abonner.
- **Bibliothèque** : liste des abonnements, ajout par URL RSS, **import OPML**
  (sélecteur de fichier `OpenDocument`, parseur `OpmlParser` maison) avec
  progression et erreurs unitaires affichées.
- **Lecteur** : mini-lecteur persistant + grand lecteur (bottom sheet), 5
  sauts temporels, vitesse 0.8–2.5×, sauvegarde de position (Phase 2, non
  re-testée depuis).
- **Navigation basse** Accueil/Bibliothèque (masquée sur l'écran épisodes).
- 4 thèmes visuels (OLED, Verre Dépoli, Néo-Brutaliste, Synthwave) appliqués
  en direct via `PulseCastTheme` + jetons `PulseCastThemeExtras`.

## 4. 🔴 BUG OUVERT #1 — « Liste des épisodes vide » (PRIORITÉ MAX)

Symptôme utilisateur : après import OPML, cliquer un abonnement → liste
d'épisodes **vide/noire**. Persiste après les correctifs des builds 5 et 6.

### Déjà tenté (sans confirmation de résolution)
1. Import OPML réparateur : `FeedRepository.fetchAndStoreFeed` est idempotent —
   si le podcast existe sans épisodes, le flux est re-téléchargé et les
   épisodes insérés (`importOpmlFeeds` ne saute que les flux déjà complets).
2. Auto-réparation à l'ouverture : `EpisodeListViewModel.init` déclenche
   `refresh()` si `episodeDao.countForPodcast(podcastId) == 0`.
3. Parseur tolérant : accepte désormais `type="video/*"` (podcasts vidéo
   Podcast Addict très probable dans l'OPML de l'utilisateur) et
   `<media:content>` en repli de `<enclosure>`.

### État des investigations (faits vérifiés)
- DAO/VM/écran revus ligne à ligne : les requêtes Room sont correctes
  (`observeUnplayedForPodcast` : `is_played = 0` + filtre titre null-safe).
- Le parseur **n'échoue jamais bruyamment** : `RssParser.parse()` avale le XML
  malformé (catch → END_DOCUMENT) et peut renvoyer 0 épisodes **sans
  exception**. Un flux injoignable/malformé devient silencieusement un
  podcast sans épisodes.
- Flux français réels testés (Audiomeans, Acast) : RSS 2.0, `<enclosure
  type="audio/mpeg">` → compatibles parseur. Donc le bug n'est PAS
  systématique sur les flux standards.
- Pistes restantes (à creuser en priorité) :
  a. **Demander à l'utilisateur ce qu'affiche le bouton « Actualiser le
     flux »** sur l'écran d'un podcast vide (message ajouté en build 6 :
     « N nouveaux épisodes importés » / « Aucun épisode trouvé dans ce
     flux » / « Impossible d'actualiser : … »). C'est LE discriminant :
     réseau ? parse ? rien ne se passe (→ utilisateur encore sur un vieil APK) ?
  b. Vérifier que l'utilisateur a bien installé le **dernier artefact**
     (les numéros de run artefact vs. sa version installée).
  c. Si « Aucun épisode trouvé » : récupérer 2-3 `feed_url` de SON OPML
     (faire un export exemple) et analyser le XML réel (curl) : Atom ?
     enclosures exotiques ? Un support `<entry>` Atom est à envisager.
  d. Logcat via `adb logcat` si l'utilisateur peut brancher le téléphone.
  e. Vérifier en base (Device Explorer / adb) : `SELECT podcast_id,
     COUNT(*) FROM episodes GROUP BY podcast_id`.

### Détails d'implémentation utiles
- `data/FeedRepository.kt` = point d'entrée unique d'import (URL, OPML,
  découverte). Y centraliser tout nouveau correctif.
- Le message de refresh est exposé par `EpisodeListViewModel.refreshMessage`.

## 5. 🟡 BUG OUVERT #2 — Sélecteur de style visuel (corrigé ce round, à retester)

- Symptôme : cliquer l'icône palette n'offrait « aucun choix » — la
  `ModalBottomSheet` ne s'affichait pas de façon fiable (probable conflit
  avec le `BottomSheetScaffold` racine qui gère déjà le mini-lecteur).
- Correctif appliqué (build 7) : **suppression de la modale** ; le sélecteur
  `ThemeSelector` est désormais une **section intégrée à l'écran Accueil**,
  ouverte/fermée par l'icône palette (`showStylePicker`). Le choix passe par
  `ThemeViewModel.selectTheme` → DataStore → `PulseCastTheme` recompose tout
  l'arbre (aucune fenêtre popup impliquée).
- Deux instances de `ThemeViewModel` coexistent (racine + destination Nav
  « home ») : **c'est voulu et sûr**, les deux sont adossées au même
  DataStore singleton (`preferencesDataStore`) qui diffuse les changements.
- À faire retester : ouvrir l'accueil → icône palette → choisir Synthwave /
  Néo-Brutaliste → toute l'UI doit changer instantanément.

## 6. Carte des fichiers (ce qui compte)

```
app/src/main/java/com/pulsecast/app/
├── MainActivity.kt                  # edge-to-edge + permission notifications
├── PulseCastApplication.kt          # singleton Room (pulsecast.db, v1)
├── data/
│   ├── FeedRepository.kt            # ★ import flux idempotent/réparateur
│   ├── catalog/PodcastCatalogApi.kt # recherche/lookup iTunes + top Apple (FR)
│   ├── parser/RssParser.kt          # parseur RSS tolérant (audio+video, media:content)
│   ├── parser/OpmlParser.kt         # parseur OPML (outline xmlUrl, récursif)
│   └── local/                       # Room : PulseCastDatabase, DAOs, entités
│       └── dao/EpisodeDao.kt        # + countForPodcast, observeUnplayedCounts
├── playback/                        # PlaybackService (Media3), ViewModel, constantes
├── theme/                           # AppTheme (4 styles), ThemeSpecs, ThemeViewModel (DataStore)
└── ui/
    ├── PulseCastApp.kt              # racine : Surface fond thème, BottomSheetScaffold
    │                                #   mini-lecteur, NavHost, barre Accueil/Bibliothèque
    ├── components/                  # MiniPlayer, PulseCastSurface, ThemeSelector,
    │                                #   PodcastArtworkCard, KeywordFilterDialog
    └── screens/
        ├── home/                    # HomeScreen + DiscoverViewModel (accueil complet)
        ├── library/                 # PodcastLibraryScreen/VM (URL + OPML + abonnements)
        ├── episodes/                # EpisodeListScreen/VM (onglets lu/non-lu, refresh)
        └── player/                  # FullPlayerScreen
```

## 7. Pièges techniques identifiés (à ne pas redécouvrir)

- **CI seule** : toute erreur de compile coûte un run. Avant de push,
  relire : imports, nullabilité, noms de params Compose.
- **Kotlin** : une fonction à bloc `{ }` n'a pas de return implicite (déjà
  corrigé une fois dans `parseSearchResults`) ; les smart casts ne marchent
  **pas** sur les propriétés déléguées (`by collectAsState()`) — assigner à
  un `val` local avant un test de nullité.
- **`continue`/`break` interdits dans un lambda non-inline** (ex. inside
  `withContext {}`) ; OK dans `forEach`/`repeat` (inline) et dans `try`
  direct d'une boucle.
- **API iTunes** : `genreId` ignoré → utiliser des `term` FR + `country=FR`.
  Le top Apple ne donne pas `feedUrl` → `lookup?id=` à la demande.
  Artwork 100x100 → remplacer le dernier segment par `/600x600bb.jpg`.
- **Icônes Compose** : `material-icons-core` seul ne suffit pas (`Pause` est
  en extended — dépendance déjà ajoutée).
- **Thème** : ne JAMAIS changer les `id` de `AppTheme` (persistés en
  DataStore). Le fond de fenêtre doit être peint par la `Surface` racine.
- **RssParser** : avale le XML malformé sans exception → toujours logguer/
  afficher le nombre d'épisodes trouvés pour diagnostiquer.
- Git : avertissements LF→CRLF bénins sous Windows.
- L'artefact CI expire sous 14 jours.

## 8. Prochaines étapes priorisées

1. **Diagnostic épisodes** (voir §4) — question à l'utilisateur + analyse de
   son OPML réel. C'est le seul vrai bug bloquant restant.
2. Faire retester le sélecteur de style (§5).
3. Tests de lecture : jouer un épisode (Phase 2 jamais re-testée), position,
   vitesse, mini-lecteur.
4. Import OPML : export OPML inverse (bouton « Exporter ») ; import de
   dossiers/catégories.
5. Rafraîchissement automatique périodique des abonnements (WorkManager) —
   actuellement uniquement manuel/à l'ouverture.
6. Marquer-écouté automatique à 95 % (la règle SQL existe dans
   `updatePlaybackPosition`), écran « En cours d'écoute » sur l'accueil.
7. Export de schéma Room : créer `app/schemas/` en CI si migration future
   (version 1 → 2), avec `Migration` explicite obligatoire.

## 9. Commandes utiles

```powershell
# Build debug
gh workflow run build-apk.yml --ref master -f build_type=debug
gh run list --workflow=build-apk.yml --limit 1
gh run watch <run_id> --interval 30 --exit-status

# Logs d'un échec de compile
gh run view <run_id> --log-failed

# Commit + push (dépôt déjà initialisé, remote origin OK)
git add -A; git commit -m "..."; git push
```
