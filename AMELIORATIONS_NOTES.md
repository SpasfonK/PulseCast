# Améliorations visuelles + support pliable (Honor Magic V2)

Ce tour de modifications fait suite au premier build fonctionnel de
l'APK. Deux demandes : améliorer le rendu visuel de façon proactive, et
prendre en compte les pliables book-style comme le Honor Magic V2, en
particulier dans le lecteur.

## Toujours pas de build local vérifié

Comme pour la Phase 4, cet environnement n'a ni SDK Android ni accès
réseau pour Gradle. Ces changements n'ont donc pas pu être compilés
localement — seule une relecture attentive garantit leur cohérence. Le
point le plus sujet à caution techniquement est le calcul de position de
la charnière en mode tabletop (voir section 2) : il repose sur une
hypothèse — que le contenu du grand lecteur démarre bien à `y=0` de la
fenêtre une fois le bottom sheet complètement déplié — que je ne peux
vérifier que par le raisonnement, pas par l'exécution.

## 1. Améliorations visuelles

- **Le thème Verre Dépoli affiche enfin ce qu'il promettait depuis la
  Phase 3** : la pochette en cours, floutée et assombrie, occupe tout
  l'écran du grand lecteur (`GlassBackdrop`). Avant ce tour, les surfaces
  translucides du thème n'avaient rien de flouté derrière elles — l'idée
  existait dans le `ColorScheme` mais n'était jamais rendue à l'écran.
- **L'illustration du grand lecteur est maintenant encadrée par
  `PulseCastSurface`** (le composant partagé de la Phase 3), au lieu
  d'une simple image recadrée : elle hérite donc automatiquement de
  l'ombre dure décalée en Néo-Brutaliste, du halo néon en Synthwave, et
  d'une micro-bordure ailleurs.
- **Halo d'accent dynamique en OLED** : un halo doux dans la couleur
  extraite de la pochette (Palette API) apparaît derrière l'illustration
  — l'effet boucle visuellement sur sa propre source, plutôt que de
  rester un détail invisible du `ColorScheme`.
- **Mini-lecteur** : une fine ligne de progression apparaît en haut de la
  barre (comme la notification système Android) — un repère toujours
  visible sans ouvrir le grand lecteur.
- **Transition mini-lecteur ↔ grand lecteur en fondu enchaîné**
  (`Crossfade`) plutôt qu'un remplacement instantané — la limite notée en
  Phase 4 est partiellement résolue (toujours pas un morphing continu lié
  au geste de glisser, mais un fondu net plutôt qu'une coupure brutale).
- **Icônes lecture/pause animées** (`Crossfade`) dans le mini-lecteur et
  le grand lecteur, avec un indicateur de chargement qui s'y fond pendant
  le buffering.
- **Confort sur grand écran** : le contenu du grand lecteur, de la
  bibliothèque et de la liste d'épisodes est désormais plafonné en
  largeur (480-680dp selon l'écran) et centré, pour éviter que tout ne
  s'étire de façon disproportionnée sur l'écran déplié du Magic V2
  (~2100px de large) ou sur une tablette.

## 2. Support du Honor Magic V2 (pliable book-style)

Nouveau module `ui/foldable/FoldingState.kt`, basé sur Jetpack
WindowManager (`androidx.window:window:1.3.0`, nouvelle dépendance) :

- `rememberFoldingFeature()` observe la charnière physique de l'appareil
  en continu (`WindowInfoTracker.windowLayoutInfo(activity)`). Renvoie
  `null` en permanence sur un téléphone classique — code strictement
  additif, sans impact sur l'immense majorité des utilisateurs.
- `PlayerLayoutMode` traduit cette info en deux postures pour le
  lecteur : `Normal` (téléphone classique, pliable à plat ou replié) et
  `Tabletop` (à moitié ouvert, charnière horizontale — l'appareil posé
  comme un petit chevalet, ce qui arrive typiquement quand on tourne le
  Magic V2 déplié à 90° et qu'on le pose).

**En mode Tabletop**, `FullPlayerScreen` répartit son contenu de part et
d'autre de la charnière plutôt que de l'ignorer :
- au-dessus : illustration (réduite, 55% de largeur, pour limiter le
  risque de débordement) + titre — en version compacte, sans sous-titre
  ni message d'erreur, pour tenir dans un espace potentiellement modeste ;
- la charnière elle-même reste vide (rien d'interactif ni de critique
  n'y est dessiné) ;
- en-dessous : scrubber, 5 sauts temporels, lecture/pause, vitesse.

Le bouton "Réduire" a été sorti du flux vertical (il flotte maintenant en
haut à droite via `Modifier.align`) : ça évite qu'il ne vienne fausser le
calcul de la hauteur du panneau du haut, qui doit correspondre exactement
à la position de la charnière.

**Limite assumée** : la répartition utilise les bornes réelles de la
charnière (`FoldingFeature.bounds`, converties en Dp), ce qui est
l'approche recommandée par Android — mais l'alignement pixel-perfect
dépend de l'hypothèse mentionnée plus haut. Si le rendu réel sur ton
Magic V2 est décalé, le point d'ajustement est le calcul de `hingeTopDp`
dans `FullPlayerScreen.kt`.

## Hors périmètre de ce tour

- Pas de mise en page deux-volets (liste + détail côte à côte) pour
  l'écran totalement déplié à plat : seul le plafonnement de largeur a
  été fait. Une vraie mise en page adaptative (liste à gauche, épisodes
  ou lecteur à droite) est un chantier plus large, à envisager si le
  confort actuel ne suffit pas à l'usage.
- Pas de test sur la posture "livre" (charnière verticale, à moitié
  ouverte) : moins pertinente pour un lecteur audio qu'une vidéo, donc
  non traitée spécifiquement — elle retombe sur le mode `Normal`.
