# Profil (ProfilePlugin)

Affiche une carte de profil pour soi-même ou un autre joueur : statut en ligne, solde (via Vault) et faction (via FactionPlugin) — si ces modules sont présents. Deux modes d'affichage : **chat** (texte) ou **gui** (inventaire), au choix dans `config.yml`.

## Dépendances

| Plugin | Type |
|---|---|
| Main | `depend` — obligatoire |
| Faction | `softdepend` — optionnel, sans lui la faction n'est pas affichée |
| Vault | `softdepend` — optionnel, sans lui le solde n'est pas affiché |

## Commandes

| Commande | Description | Usage |
|---|---|---|
| `/profile [joueur]` | Affiche ton profil, ou celui d'un autre joueur si un pseudo est donné | `/profile [joueur]` |
| `/profile reload` | Recharge `config.yml` **et** `gui.yml` à chaud (sans redémarrer le serveur) — permission `profile.admin` | `/profile reload` |
| `/profile reload` | Recharge `config.yml` **et** `gui.yml` à chaud (`profile.admin`) | `/profile reload` |

Autocomplétion sur le premier argument (pseudos des joueurs en ligne).

## Configuration (`config.yml`)

```yaml
display:
  mode: chat   # "chat" ou "gui" — si "gui" mais l'expéditeur n'est pas un joueur (console), on retombe sur "chat"

messages:
  # tous les textes du mode chat + les textes communs (statut, "aucune faction", etc.)
  # couleurs au format MiniMessage (<dark_gray>, <red>, ...)
```

Tous les libellés, couleurs et textes de repli (« Aucune faction », « Module faction inactif », ...) sont dans `messages.*`.

## Configuration du GUI (`gui.yml`)

Utilisé uniquement si `display.mode: gui`. Layout, matériaux, noms et lores sont 100% configurables.

```yaml
title: "<dark_gray>Profil de <aqua>{player}"
rows: 3   # 1 à 6

filler:
  material: GRAY_STAINED_GLASS_PANE   # "NONE" pour désactiver le remplissage

items:
  head:          # tête (skin) du joueur ciblé — rendu automatique, "material" ignoré pour ce type
    slot: 13
    type: HEAD
  money:
    slot: 11
    type: MONEY
    material: GOLD_INGOT
  faction:
    slot: 15
    type: FACTION
    material: WHITE_BANNER
  faction-bank:
    slot: 22
    type: FACTION_BANK
    material: CHEST
```

**Layout par défaut** (3 lignes, centré autour de la tête) :

```
 .  .  .  .  .  .  .  .  .
 .  .  MONEY  .  HEAD  .  FACTION  .  .
 .  .  .  .  BANK  .  .  .  .
```

- `type` détermine le contenu dynamique injecté (`HEAD`, `MONEY`, `FACTION`, `FACTION_BANK`) ; n'importe quel autre item (slot/material/name/lore libres) peut être ajouté pour de la pure décoration.
- Placeholders disponibles dans `name`/`lore`/`title` : `{player}` `{status}` `{money}` `{faction}` `{bank}`.
- Ajouter/déplacer/retirer un item = juste éditer `gui.yml`, aucune recompilation nécessaire.
- Le GUI est en lecture seule : tout clic ou glisser-déposer à l'intérieur est annulé (fermeture normale via `Échap`).

## Architecture

| Fichier | Rôle |
|---|---|
| `Profile.java` | Classe principale. Charge `config.yml`, crée le `ProfileGuiManager` (qui charge `gui.yml`), enregistre `ProfileCommand` (executor + tab-completer) et `ProfileGuiListener` dans `onModuleEnable()`. |
| `ProfileData.java` | Record portant argent/faction/banque déjà résolus pour un joueur, calculé une seule fois et partagé entre l'affichage chat et l'affichage GUI. |
| `command/ProfileCommand.java` | Résout le joueur ciblé, calcule le `ProfileData` (Vault + Faction, soft-dépendances), puis délègue au rendu chat ou GUI selon `display.mode`. |
| `gui/ProfileGuiHolder.java` | Identifie les inventaires ouverts par `/profile` via `InventoryHolder` (pas par titre) — même convention que le reste de l'écosystème. |
| `gui/ProfileGuiManager.java` | Charge `gui.yml`, construit et ouvre l'inventaire (remplissage, items configurés, tête via `SkullMeta`). |
| `listener/ProfileGuiListener.java` | Annule tout clic/glisser-déposer dans le GUI (affichage en lecture seule). |

## Comportement selon les dépendances présentes

| Situation | Argent | Faction | Banque |
|---|---|---|---|
| Vault absent | `no-economy` | — | — |
| Faction absent ou désactivé | — | `no-faction-module` | `no-faction-bank` |
| Faction présent, joueur sans faction | solde normal | `no-faction` | `no-faction-selected` |
| Tout présent | solde formaté | nom de la faction | solde de la banque de faction |

(clés `messages.*` dans `config.yml`)

## Notes techniques

- La faction du joueur ciblé n'est résolue **qu'une seule fois** par `/profile`, dans `ProfileData` — réutilisée par le chat ET le GUI.
- Le nom du joueur et le nom de la faction ne sont jamais réinterprétés comme du MiniMessage : en mode chat ils sont insérés via `Placeholder.unparsed()`, en mode GUI ils sont échappés (`MiniMessage#escapeTags`) avant insertion dans les templates. Un nom de faction contenant une balise ne peut donc pas casser l'affichage d'un tiers.
- `Bukkit.getOfflinePlayerIfCached()` est utilisé pour résoudre un joueur cible hors ligne (pas de requête réseau bloquante) — si ce joueur n'a jamais rejoint le serveur, il ne sera pas trouvé.
- En mode GUI, si l'expéditeur de la commande n'est pas un joueur (console, commande bloc), l'affichage retombe automatiquement sur le mode chat.
- `reload` est un mot-clé réservé sur `/profile` (comme `/profile reload`), vérifié en code (`profile.admin`) — pas une permission attachée à la commande entière dans `plugin.yml`, pour que la consultation normale du profil reste libre d'accès. La complétion ne le suggère qu'aux joueurs qui ont la permission.
- `/profile reload` recharge `config.yml` (`reloadConfig()`) **et** `gui.yml` (`ProfileGuiManager#reload()`, qui relit le fichier depuis le disque) : les deux fichiers sont pris en compte immédiatement, sans redémarrage du serveur.

---
*Dernière révision : 22 août 2026.*
