# Money

Système d'économie du serveur. Gère le solde des joueurs et s'enregistre comme **provider Vault Economy** pour tout l'écosystème (FactionPlugin, ProfilePlugin, WeeklyQuestPlugin, ...).

## Dépendances

| Plugin | Type |
|---|---|
| Main | `depend` — framework core, `DataStore`, `ModuleManager` |
| Vault | `depend` — fournit l'interface `Economy` |

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/money [joueur]` | `/balance`, `/bal` | Affiche ton solde, ou celui d'un autre joueur si un pseudo est donné | — |
| `/pay <joueur> <montant>` | — | Transfère de l'argent à un joueur **en ligne** | — |
| `/moneytop [page]` | `/baltop` | Classement des joueurs les plus riches (en ligne + hors ligne) | — |
| `/eco <give\|set\|take> <joueur> <montant>` | — | Donne / fixe / retire le solde d'un joueur | `money.admin` |

Le montant accepte la virgule ou le point comme séparateur décimal (`10,5` ou `10.5`).

## Configuration (`config.yml`)

```yaml
economy:
  currency-singular: "Crédit"
  currency-plural: "Crédits"
  starting-balance: 500.0      # solde de départ d'un nouveau joueur
  min-transaction: 0.5         # montant minimum pour /pay
  max-balance: 999999999.0     # plafond de solde

physical-item:                 # préparé dans la config, PAS ENCORE câblé aux commandes
  enabled: false
  material: GOLD_NUGGET
  name: "&6Crédit"
  lore: ["&7Valeur: &e1 Crédit"]
  item-value: 1.0

moneytop:
  entries-per-page: 10
  cache-seconds: 30            # durée du cache du classement ; 0 = désactivé (toujours à jour, plus lent)

messages:
  # tous les textes affichés au joueur, couleurs en codes legacy (&8, &c, &e, ...)
```

## Architecture

| Fichier | Rôle |
|---|---|
| `Money.java` | Classe principale (`JavaPlugin` + `Module`). Dans `onModuleEnable()` : enregistre le provider Vault (`ServicePriority.Highest`), le listener de connexion et les 4 commandes. |
| `MoneyManager.java` | Cœur métier : cache `UUID → solde` (`ConcurrentHashMap`), persistence via `DataStore`, classement avec cache à durée de vie limitée. |
| `VaultEconomyProvider.java` | Implémente `net.milkbowl.vault.economy.Economy`, délègue tout à `MoneyManager`. Pas de support banque (`hasBankSupport()` = `false`). |
| `listener/PlayerListener.java` | Charge le solde à la connexion (async), sauvegarde + décharge le cache à la déconnexion (sync). |
| `command/` | `MoneyCommand`, `PayCommand`, `EcoCommand`, `MoneyTopCommand`. |
| `util/Msg.java` | Envoi de messages colorés (codes `&`) via `LegacyComponentSerializer`, utilisé par les 4 commandes. |

### API pour les autres modules

- `Money#getMoneyManager()` — accès direct au `MoneyManager` depuis un autre module Java de l'écosystème.
- Sinon, passer par Vault : `Bukkit.getServicesManager().getRegistration(Economy.class).getProvider()`.

### Notes techniques

- **`getBalance()`** a un filet de sécurité synchrone : si un joueur n'est pas encore en cache, il est chargé à la demande sur le thread appelant. Le cas normal passe par le chargement async à la connexion, donc ce filet ne se déclenche qu'en cas de lecture très précoce.
- **`/moneytop`** ne rescanne le `DataStore` en entier que si le cache a expiré (`moneytop.cache-seconds`) ; sinon il ressert la même liste triée déjà en mémoire.
- **`createPlayerAccount()`** et **`hasAccount()`** (interface Vault) retournent toujours `true` / déclenchent un chargement : il n'existe pas de notion de "compte inexistant" côté Money, un joueur a toujours un solde par défaut (`economy.starting-balance`).
- La sauvegarde à la déconnexion est volontairement **synchrone** (contrairement au chargement à la connexion, async) pour garantir l'intégrité des soldes à l'arrêt du serveur.

---
*Dernière révision : 22 août 2026.*
