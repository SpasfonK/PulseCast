# Gauntlet Check 3 — Auto-audit Phase 3

## 1. Les 4 thèmes (couleurs, typographie, formes)

| Thème | Fond | Accent | Typographie | Formes | Jeton distinctif |
|---|---|---|---|---|---|
| OLED Deep Minimal | Noir absolu `#000000` | **Dynamique** (Palette API sur la pochette, repli violet) | Sans-serif système, poids moyen, espacement resserré | Arrondis modérés (4-28dp) | `useDynamicAccentFromArtwork`, `borderWidth = 0.5dp` (micro-bordures) |
| Verre Dépoli | Bleu-nuit `#0D0F14` | Cyan-bleu doux, fixe | Sans-serif système, léger, espacement aéré | Arrondis généreux (12-40dp) | `useGlassBackground`, `surface`/`outline` semi-transparents (bordures lumineuses diffuses) |
| Néo-Brutaliste | **Clair** crème `#FFFDF5` | Jaune électrique / rose / cyan | Sans-serif système, graisse `Black`/`Bold` | 100 % anges vifs (`0.dp` partout) | `useHardShadow` + `hardShadowOffset(4dp,4dp)`, `borderWidth = 2dp` noir |
| Synthwave / Cyberpunk | Violet-nuit profond `#0B0221` | Néon cyan `#00F0FF` + magenta `#FF2E88` | Monospace, gras, espacement large | Angles coupés (`CutCornerShape`) | `useNeonGlow` (halo dégradé cyan→magenta flouté) |

Point de conception à noter : le Néo-Brutaliste est le seul thème **clair** des 4. C'est fidèle au mouvement "brutalist web design" (fonds crème/blancs, aplats vifs, noir pur) plutôt qu'un choix arbitraire, et ça crée un vrai contraste d'ambiance dans le sélecteur face aux 3 thèmes sombres.

La couleur dynamique (Palette API) n'est câblée qu'à l'OLED, conformément au cahier des charges — les 3 autres thèmes ont une identité colorée fixe, volontairement insensible à l'illustration en cours de lecture. `PaletteAccentExtractor` est prêt et testé mentalement (ordre de repli Vibrant → LightVibrant → DarkVibrant → Muted → couleur fixe) mais son appel réel (charger la pochette en bitmap, extraire hors thread principal) est différé à la Phase 4, quand un écran affichera vraiment une pochette.

Les 15 styles du type scale Material3 ne sont pas dupliqués 4 fois à la main : `buildTypography()` part du `Typography()` par défaut et ne surcharge que `fontFamily`/`fontWeight`/`letterSpacing`, ce qui réduit le risque de coquille et de divergence entre thèmes.

## 2. Composant sélecteur de style

`ThemeSelector` (LazyRow de `ThemeSwatchCard`) prévisualise les 4 styles avec leurs **propres** couleurs (chaque carte calcule sa `PulseCastThemeSpec` indépendamment du thème actif), tout en faisant porter la carte elle-même par `PulseCastSurface` — le conteneur commun introduit cette phase. Résultat concret : quand le Néo-Brutaliste est le thème actif, le sélecteur affiche lui-même ses cartes avec l'ombre dure décalée ; en Synthwave, avec le halo néon. C'est une démonstration directe, pas seulement déclarative, que les jetons `PulseCastThemeExtras` sont réellement branchés à de l'UI.

`ThemeShowcase` assemble `ThemeViewModel` + `PulseCastTheme` + `ThemeSelector` en un banc d'essai complet et fonctionnel (pas un simple extrait de code) : sélectionner une carte appelle `viewModel.selectTheme()`, qui écrit dans DataStore, dont le `Flow` remonte via `StateFlow` jusqu'au `collectAsState()` qui pilote `PulseCastTheme`. Une preview statique séparée (`ThemeShowcasePreview`, fixée sur Synthwave, sans ViewModel) garantit un rendu fiable dans Android Studio sans dépendre d'un contexte applicatif réel.

## 3. Changement de style instantané, sans recomposition lourde ni redémarrage d'Activity

C'est la propriété centrale à vérifier ici. Trois garanties, par construction :

**Aucun redémarrage d'Activity.** Le thème sélectionné circule uniquement comme état en mémoire (`StateFlow` issu de DataStore). Rien dans ce chemin ne touche une ressource, une configuration système ou n'appelle `recreate()` : l'écriture DataStore est un simple I/O asynchrone sur fichier, sans effet sur le cycle de vie de l'Activity. Une fois Phase 4 en place, `MainActivity.setContent { }` ne sera appelé qu'une fois, à la création ; tous les changements de thème ultérieurs passent uniquement par le système d'état de Compose.

**Aucune recomposition lourde.** `PulseCastTheme` recalcule sa `PulseCastThemeSpec` via `remember(appTheme, dynamicAccent) { ... }` : un calcul pur (allocations d'objets `ColorScheme`/`Typography`/`Shapes`/`PulseCastThemeExtras`), sans I/O, sans décodage d'image, sans travail sur bitmap. Le changement de thème ne déclenche donc qu'une recomposition des composables qui **lisent réellement** `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.shapes` ou `PulseCastTheme.extras` — c'est le fonctionnement natif du système de `CompositionLocal` de Compose : un composable qui ne lit aucune de ces valeurs (un `Text` à contenu et style totalement fixes, par exemple) ne recompose pas du tout quand le thème change. Il n'y a pas de `invalidate()` global ni de reconstruction de tout l'arbre.

**Portée de recomposition volontairement resserrée.** `ThemeSwatchCard` calcule sa `previewSpec` (couleurs de prévisualisation) via `remember(theme)` — clé sur le thème *prévisualisé*, pas sur le thème *actif* — donc les 4 cartes ne se recalculent jamais quand on change de style : seul le conteneur `PulseCastSurface` de chaque carte (qui lit `PulseCastTheme.extras`) et le texte/l'icône (qui lisent `MaterialTheme.colorScheme`) recomposent.

## Limites connues, à traiter en Phase 4
- `Modifier.blur()` (halo Synthwave) s'appuie sur `RenderEffect`, disponible à partir d'API 31 ; en dessous, le halo reste visible mais non flouté — documenté dans `PulseCastSurface.kt`, non bloquant.
- `PaletteAccentExtractor` n'est pour l'instant appelé par aucun écran réel (pas de pochette chargée avant Phase 4) : sa correction n'a pu être vérifiée que par relecture, pas par exécution sur une vraie image.
- Aucune préférence d'accessibilité (taille de police système, contraste renforcé) n'est encore prise en compte — à évaluer si besoin lors de la Phase 4.
