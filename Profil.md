# Profil (ProfilePlugin)

Affiche une carte de profil pour soi-même ou un autre joueur : statut en ligne, solde (via Vault) et faction (via FactionPlugin) — si ces modules sont présents.

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

Autocomplétion sur le premier argument (pseudos des joueurs en ligne).

## Architecture

| Fichier | Rôle |
|---|---|
| `Profile.java` | Classe principale (`JavaPlugin` + `Module`). Enregistre `ProfileCommand` comme executor **et** tab-completer dans `onModuleEnable()`. |
| `command/ProfileCommand.java` | Toute la logique : résolution du joueur ciblé, de l'économie (Vault, soft) et de la faction (Faction, soft), et rendu de la carte. |

Pas de `config.yml` : tous les textes sont codés en dur (MiniMessage) dans `ProfileCommand`.

## Comportement selon les dépendances présentes

| Situation | Ligne "Argent" | Ligne "Faction" | Ligne "Banque" |
|---|---|---|---|
| Vault absent | `Aucun plugin économie` | — | — |
| Faction absent ou désactivé | — | `Module faction inactif` | `N/A` |
| Faction présent, joueur sans faction | solde normal | `Aucune` | `Aucune faction` |
| Tout présent | solde formaté | nom de la faction | solde de la banque de faction |

## Notes techniques

- La faction du joueur ciblé n'est résolue **qu'une seule fois** par `/profile` (réutilisée pour la ligne Faction et la ligne Banque).
- Le nom du joueur et le nom de la faction sont toujours rendus en texte brut (`Component.text`), **jamais** repassés dans le parseur MiniMessage : un nom de faction contenant une balise (ex. `<click:...>`) ne peut donc pas devenir un composant actif dans le profil affiché à un tiers.
- `Bukkit.getOfflinePlayerIfCached()` est utilisé pour résoudre un joueur cible hors ligne (pas de requête réseau bloquante) — si ce joueur n'a jamais rejoint le serveur, il ne sera pas trouvé.

---
*Dernière révision : 22 août 2026.*
