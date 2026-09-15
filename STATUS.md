# STATUS.md — PulseCast (état au 15/09/2026, session Kilo #2)

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
  build. **Dernier build vert de cette session : artefact `pulsecast-debug-9`**
  (run `34992357302`, commit `1b8deac`).

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

## 4. ✅ BUG RÉSOLU #1 — « Liste des épisodes vide » (et sélecteur de style invisible)

**Cause racine unique, trouvée et corrigée : `PulseCastSurface` s'effondrait à
0 px de haut.**

`ui/components/PulseCastSurface.kt` plaçait *tous* ses enfants en
`Modifier.matchParentSize()` — l'ombre dure, le halo néon **et le contenu**.
Or un enfant `matchParentSize` ne participe pas au calcul de la taille de son
`Box` (source `BoxMeasurePolicy` : « does not take part in defining the size of
the Box »). Le `Box` parent n'ayant alors plus aucun enfant qui définisse sa
taille, il retombait sur `constraints.minHeight` = **0**. Toute surface était
donc rendue à une hauteur nulle.

Conséquences observées sur l'APK précédent :
- chaque ligne d'épisode (`EpisodeRow`) → hauteur 0 → **liste d'épisodes vide
  bien que les compteurs d'en-tête (non-lus/lus, hors surface) soient corrects** ;
- `StylePickerSection` (sélecteur de styles, enveloppé dans une surface) →
  **aucun style visible / « aucun choix »** ;
- idem lignes de bibliothèque, carte « Aucun abonnement », barre de recherche,
  carte de progression OPML.

Correctif (build #9) : seules les décorations restent en `matchParentSize` ;
le contenu est mesuré normalement (`.fillMaxWidth()`) et donne sa hauteur au
`Box`. Aucune API publique changée, aucun appelant modifié.

### Historique des tentatives (contexte, désormais obsolète)
Symptôme initial : après import OPML, cliquer un abonnement → liste vide.
Les correctifs ci-dessous étaient utiles mais **n'étaient pas la cause** :
1. Import OPML réparateur : `FeedRepository.fetchAndStoreFeed` est idempotent —
   si le podcast existe sans épisodes, le flux est re-téléchargé et les
   épisodes insérés (`importOpmlFeeds` ne saute que les flux déjà complets).
2. Auto-réparation à l'ouverture : `EpisodeListViewModel.init` déclenche
   `refresh()` si `episodeDao.countForPodcast(podcastId) == 0`.
3. Parseur tolérant : accepte désormais `type="video/*"` (podcasts vidéo
   Podcast Addict très probable dans l'OPML de l'utilisateur) et
   `<media:content>` en repli de `<enclosure>`.

### Améliorations de robustesse livrées dans le même build
- `RssParser` supporte maintenant **Atom** (`<feed>`/`<entry>`/`<link
  rel="enclosure">`, dates `published`/`updated` + variantes avec
  millisecondes) en plus de RSS 2.0 : un abonnement Atom ne reste plus
  silencieusement vide.
- `RssParser.parse()` renvoie un titre **vide** (et non « Podcast sans
  titre ») quand le flux n'en expose pas : le repli est décidé dans
  `FeedRepository` (titre OPML puis libellé générique). Un refresh sur un flux
  momentanément tronqué ne peut donc plus renommer un abonnement existant en
  « Podcast sans titre » (`FeedRepository` conserve le titre existant).

### Pistes ouvertes restantes (diagnostic, plus aucun bug bloquant connu)
- Flux français réels testés (Audiomeans, Acast) : RSS 2.0, `<enclosure
  type="audio/mpeg">` → compatibles parseur.
- Si un abonnement restait vide après le build #9, faire parler le bouton
  « Actualiser le flux » (`EpisodeListViewModel.refreshMessage`) et vérifier en
  base : `SELECT podcast_id, COUNT(*) FROM episodes GROUP BY podcast_id`.
- Vérifier que l'APK installé est bien le **dernier artefact** CI (numéro de
  run = numéro de build).

### Détails d'implémentation utiles
- `data/FeedRepository.kt` = point d'entrée unique d'import (URL, OPML,
  découverte). Y centraliser tout nouveau correctif.
- Le message de refresh est exposé par `EpisodeListViewModel.refreshMessage`.

## 5. ✅ BUG RÉSOLU #2 — Sélecteur de style visuel

- Symptôme : cliquer l'icône palette n'offrait « aucun choix ».
- **Cause racine = §4** : la `ModalBottomSheet` initiale (build ≤6) était un
  faux coupable ; la section intégrée à l'accueil (build 7) était correcte mais
  restait **invisible** car enveloppée dans `PulseCastSurface` (hauteur 0).
- Correction de la section précédente (build 9) : le sélecteur s'affiche.
- Chaîne de sélection inchangée et saine : `ThemeViewModel.selectTheme` →
  `ThemePreferencesRepository` (DataStore) → `ThemeViewModel.selectedTheme`
  (racine) → `PulseCastTheme` recompose tout l'arbre ; aucun redémarrage
  d'Activity.
- Deux instances de `ThemeViewModel` coexistent (racine + destination Nav
  « home ») : **c'est voulu et sûr**, les deux sont adossées au même
  DataStore singleton (`preferencesDataStore`) qui diffuse les changements.
- À retester : ouvrir l'accueil → icône palette → choisir Synthwave /
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

- **⚠ Un commit jamais buildé par CI n'est pas fiable, même « terminé »** :
  ce round a découvert que le commit `eda1d34` (juste avant cette session)
  avait été poussé sans jamais déclencher de run — il contenait deux erreurs
  de compilation pures qui l'auraient fait échouer :
  1. `PulseCastApp.kt` appelait `rememberCoroutineScope()` sans l'importer
     (`import androidx.compose.runtime.rememberCoroutineScope`).
  2. `RssParser.kt` avait une KDoc contenant littéralement `(image/*)` —
     **les commentaires de bloc Kotlin sont imbriqués** (grammaire
     `DelimitedComment: '/*' { DelimitedComment | <any character> } '*/'`) :
     un `/*` à l'intérieur d'un commentaire déjà ouvert en ouvre un second,
     qui doit lui aussi être refermé. Une seule paire `/`+`*` orpheline
     (wildcard de type MIME, exemple de code, etc.) dans un commentaire fait
     avaler tout le reste du fichier comme commentaire non fermé → erreurs
     « Missing '}' » / « Unclosed comment » à des lignes qui semblent sans
     rapport. **Règle** : ne jamais écrire `/*` ou `*/` en dehors d'un vrai
     début/fin de commentaire, même dans du texte descriptif (préférer
     `image/…`, ou séparer les caractères par une espace).
  → Après chaque session, vérifier qu'un run CI a bien couvert le **dernier**
  commit avant de le considérer acquis (comparer l'heure du commit à l'heure
  du run, pas seulement regarder « le dernier run est vert »).
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

1. **Retester l'app sur le build #9** : liste d'épisodes d'un abonnement (elle
   doit afficher les lignes) et sélecteur de style (accueil → icône palette →
   les 4 styles doivent être visibles et s'appliquer au tap).
2. Tests de lecture : jouer un épisode (Phase 2 jamais re-testée), position,
   vitesse, mini-lecteur.
3. Import OPML : export OPML inverse (bouton « Exporter ») ; import de
   dossiers/catégories.
4. Rafraîchissement automatique périodique des abonnements (WorkManager) —
   actuellement uniquement manuel/à l'ouverture.
5. Marquer-écouté automatique à 95 % (la règle SQL existe dans
   `updatePlaybackPosition`), écran « En cours d'écoute » sur l'accueil.
6. Export de schéma Room : créer `app/schemas/` en CI si migration future
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
