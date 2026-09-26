<div align="center">

[![Paper](https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/supported/paper_vector.svg)](https://papermc.io/)
[![Purpur](https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/supported/purpur_vector.svg)](https://purpurmc.org/)

</div>

---

<div align="center">

# 🌿 MFHOPSCW — v0.5


**Écosystème de plugins Minecraft custom — PaperMC 1.21.x**

[![Version](https://img.shields.io/badge/version-0.5-purple?style=flat-square)](.)
[![Paper](https://img.shields.io/badge/PaperMC-1.21.x-blue?style=flat-square)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)](.)
[![Plugins](https://img.shields.io/badge/modules-12-blueviolet?style=flat-square)](.)

*Conçu & développé par **Zyumie / YuyuCorp***

</div>

---

## 📁 Documentation

- 📖 [Readme](README.md)
- 💵 [Money](MONEY.md)
- 👥 [Profil](PROFIL.md)
- 🛠️ [CustomItems](CUSTOMITEMS.md)

---

## 📦 Vue d'ensemble

| Couche | Plugin | Package | Rôle |
|--------|--------|---------|------|
| **Noyau** | `Main` | `fr.zyumie.main` | Core — ModuleManager, DataStore, MainAPI |
| **Noyau** | `Oxygen` | `fr.zyumie.main.oxygen` | Jauge O₂, asphyxie, OxygenSupplier API |
| **Noyau** | `OxygenArmor` | `fr.zyumie.oxygenArmor` | Armure O₂ custom, bossbar, recharge ArmorStand |
| **Économie** | `Money` | `fr.zyumie.money` | Monnaie virtuelle, provider Vault |
| **Économie** | `ShopAdmin` | `fr.zyumie.shopadmin` | Boutique admin GUI, catégories, quantités |
| **Économie** | `Hdv` | `fr.zyumie.hdv` | Hôtel des Ventes joueur-à-joueur |
| **Gameplay** | `Faction` | `fr.zyumie.faction` | Factions, 5 grades, bank liée à Vault |
| **Gameplay** | `WeeklyQuest` | `fr.zyumie.quest` | Quêtes hebdo, cycle 12 semaines, récompenses faction |
| **Gameplay** | `Profile` | `fr.zyumie.profile` | Profil joueur — argent, faction, bank |
| **Gameplay** | `Home` | `fr.zyumie.home` | /sethome débloqué après 1ère mort, cooldown 5min |
| **Monde** | `Storm` | `fr.zyumie.storm` | Tempête apocalyptique hebdomadaire, cycle réel |
| **Items** | `CustomItems` | `fr.zyumie.customitems` | 4 armures + 4 armes custom, PDC-based |

---

## 🗂 Architecture & Dépendances

```
SMP/  (Gradle multi-module)
│
├── Main                    ← Noyau — MainAPI, ModuleManager, DataStore (YAML|MySQL)
│   ├── Oxygen              ← depend: [Main]
│   │   └── OxygenArmor     ← depend: [Main, Oxygen]
│   ├── Money               ← depend: [Main, Vault]   → Vault Economy provider
│   │   ├── ShopAdmin       ← depend: [Main, Vault, Money]
│   │   └── Hdv             ← depend: [Main, Vault, Money]
│   ├── Faction             ← depend: [Main, Vault]   → FactionAPI
│   │   ├── WeeklyQuest     ← depend: [Main] + import direct Faction
│   │   └── Profile         ← depend: [Main] + import direct Faction
│   ├── Home                ← depend: [Main]
│   ├── Storm               ← depend: [Main]           → setImmunityChecker(Predicate)
│   │   └── CustomItems     ← depend: [Main], softdepend: [Storm, Faction]
│   └── NewV5 (Minsane)     ← softdepend de Main uniquement (MisaneBridge stub)
```

> **Règle d'or :** chaque plugin déclare ses `depend` dans `plugin.yml` → Bukkit garantit l'ordre de chargement. Aucune dépendance circulaire.

---

## 🧱 Main

**Package :** `fr.zyumie.main` | **Softdepend :** `Vault`, `NewV5`

Point d'entrée de l'écosystème. Tous les plugins fils appellent `MainAPI.get()` pour accéder aux services partagés.

### Fonctionnalités
- `ModuleManager` — active/désactive chaque module via `config.yml` sans retirer le jar
- `DataStore` — abstraction YAML ↔ MySQL (HikariCP) switchable via une seule config
- `MainAPI.get()` — accès statique thread-safe pour tous les modules
- `MisaneBridge` — softdepend NewV5 (stub complété quand l'API Minsane est connue)

### Commandes
| Commande | Description |
|----------|-------------|
| `/mainadmin reload` | Recharge la config + tous les modules |
| `/mainadmin list` | Liste les modules actifs/inactifs |

### Configuration (`config.yml`)
```yaml
storage:
  type: YAML          # ou MYSQL
  mysql:
    host: localhost
    port: 3306
    database: smp
    user: root
    password: ""
    poolSize: 10

modules:
  oxygen: true
  oxygen-armor: true
  money: true
  faction: true
  # ...
```

---

## 💨 Oxygen

**Package :** `fr.zyumie.main.oxygen` | **Depend :** `Main`

### Fonctionnalités
- Jauge 0–100 % par joueur (persistée via `DataStore`)
- Surface = Y ≥ seuil configurable → perte progressive `%/s`
- Sous terre → régénération `%/s`
- Asphyxie à 0 % : **2 cœurs** + **Blindness** + **Nausée** à intervalle configurable
- Interface `OxygenSupplier` → permet à des sources externes (armure, etc.) de couvrir la déplétion
- Events custom : `OxygenChangeEvent`, `PlayerOxygenDepletedEvent`

### Commandes
| Commande | Permission |
|----------|------------|
| `/oxygen set <joueur> <%>` | `oxygenplugin.admin` |
| `/oxygen get <joueur>` | `oxygenplugin.admin` |
| `/oxygen reload` | `oxygenplugin.admin` |

---

## 🛡 OxygenArmor

**Package :** `fr.zyumie.oxygenArmor` | **Depend :** `Main`, `Oxygen`

### Fonctionnalités
- Plastron en cuir custom tagué PDC (`oxygen_armor`)
- Couleur interpolée **Blanc → Noir** selon le % de réserve
- **BossBar** temps réel (Blanc > 50 %, Jaune > 20 %, Rouge ≤ 20 %)
- **Recharge sur ArmorStand** (2h = 7200s par défaut)
  - ⚡ **Optimisation lag** : la tâche ne tick que si un joueur est dans le chunk
- **Craft custom** : poser les items au sol → **clic droit** sur le bloc trigger → armure craftée
  - Ingrédients, trigger-block et effets configurables dans `config.yml`
- S'enregistre comme `OxygenSupplier` auprès de `OxygenAPI` → consomme la réserve avant les poumons

### Commandes
| Commande | Description |
|----------|-------------|
| `/oxygenarmor give [joueur]` | Donne l'armure à 100 % |

---

## 💰 Money

**Package :** `fr.zyumie.money` | **Depend :** `Main`, `Vault`

S'enregistre comme provider Vault Economy (`ServicePriority.HIGHEST`) → tous les plugins utilisant Vault (Faction, Quêtes, Shops…) utilisent automatiquement ce plugin.

### Fonctionnalités
- Monnaie virtuelle (stockage `DataStore`)
- Item physique optionnel (configurable)
- Transaction : **min 0.5 / max 999 999 999** (configurables)
- Classement paginé (joueurs online + offline)
- Devise configurable (nom singulier/pluriel)

### Commandes
| Commande | Description |
|----------|-------------|
| `/money [joueur]` | Voir son solde ou celui d'un autre |
| `/balance`, `/bal` | Alias de `/money` |
| `/pay <joueur> <montant>` | Payer un joueur |
| `/moneytop [page]` | Classement des richesses |
| `/baltop` | Alias de `/moneytop` |
| `/eco give\|set\|take <joueur> <montant>` | Admin — permission `money.admin` |

---

## 🏪 ShopAdmin

**Package :** `fr.zyumie.shopadmin` | **Depend :** `Main`, `Vault`, `Money`

### Fonctionnalités
- GUI 6 rangées avec `InventoryHolder` custom → **items non prenables**
- Catégories dans la rangée 0, items centrés (28/page), navigation en rangée 5
- Shift+clic → sélecteur de quantité personnalisé
- `Shop.yml` : catégories, items, buy/sell price, stock (-1 = illimité)
- `Gui.yml` : layout complet, matériaux, noms, lore, `custom-model-data` pour resource pack
- `/shop reload` recharge tout à chaud

### Commandes
| Commande | Description |
|----------|-------------|
| `/shop` | Ouvre la boutique |
| `/shop reload` | Recharge Shop.yml + Gui.yml |

---

## 🏦 Hdv

**Package :** `fr.zyumie.hdv` | **Depend :** `Main`, `Vault`, `Money`

### Fonctionnalités
- Mise en vente : tient l'item en main → clic sur "Vendre" → GUI création (prix ±1/±100, durée ±1h)
- Achat avec GUI de confirmation (évite les achats accidentels)
- Taxe % sur chaque vente (prélevée au vendeur), frais de création fixes
- Expiration automatique → items récupérables via `/hdv claim`
- Annulation clic droit sur sa propre annonce
- Sérialisation `ItemStack` en Base64 pour la persistance
- `Gui.yml` avec `custom-model-data` sur tous les boutons

### Commandes
| Commande | Description |
|----------|-------------|
| `/hdv` | Ouvre le HDV |
| `/hdv claim` | Récupère les items expirés/annulés |
| `/hdv reload` | Recharge la config |

---

## ⚔️ FactionPlugin

**Package :** `fr.zyumie.faction` | **Depend :** `Main`, `Vault`

### Grades (ordre hiérarchique décroissant)

| Grade | Permissions |
|-------|-------------|
| **Chef** | Tout — invite, kick, promote, demote, deposit, withdraw, delete |
| **Bras-Droit** | invite, kick, promote, demote, deposit, withdraw |
| **Modo** | invite, kick, deposit |
| **Membre** | deposit |
| **Invité** | Aucune |

> Grades, couleurs et permissions entièrement configurables dans `config.yml`

### Fonctionnalités
- Bank de faction liée à Vault (lookup **lazy** → pas de dépendance au démarrage)
- `FactionAPI` exposée pour `ProfilePlugin` et `WeeklyQuestPlugin` (import direct)
- Confirmation dissolution avec fenêtre de 10 secondes
- Tab-complete sur tous les sous-commandes

### Commandes (`/f`)
`create` · `delete` · `invite` · `join` · `leave` · `kick` · `promote` · `demote` · `info [faction]` · `list` · `bank` · `deposit <montant>` · `withdraw <montant>`

---

## 📋 WeeklyQuestPlugin

**Package :** `fr.zyumie.quest` | **Depend :** `Main` | **Softdepend :** `FactionPlugin`, `Vault`

### Fonctionnalités
- Cycle **12 semaines**, 3 quêtes actives par semaine par faction
- Types de quêtes : `KILL_MOB`, `MINE_BLOCK`, `CRAFT_ITEM`, `TRAVEL_DISTANCE`
- Récompenses déposées dans la **bank de faction** (via import direct `FactionPlugin`)
- Pool de quêtes 100 % configurable dans `config.yml` (id, week, amount, reward-money, reward-message)
- Barre de progression visuelle `[████░░░░]` dans `/quests`

### Commandes
| Commande | Description |
|----------|-------------|
| `/quests [faction]` | Affiche les quêtes de la semaine avec progression |

---

## 👤 ProfilePlugin

**Package :** `fr.zyumie.profile` | **Depend :** `Main` | **Softdepend :** `FactionPlugin`, `Vault`

Affiche un profil en boîte stylisée :
- Statut en ligne / hors ligne
- 💰 Argent personnel (Vault)
- ⚔ Faction
- 🏦 Bank de faction

### Commandes
| Commande | Description |
|----------|-------------|
| `/profile [joueur]` | Affiche le profil (soi ou un autre) |

---

## 🏠 HomePlugin

**Package :** `fr.zyumie.home` | **Depend :** `Main`

### Règles
1. `/sethome` n'est disponible qu'**après une première mort**
2. `/home` est bloqué pendant **5 minutes** après chaque mort
3. Stockage via `DataStore` (YAML/MySQL selon config Main)

### Commandes
| Commande | Description |
|----------|-------------|
| `/sethome` | Définit le home (débloqué après mort) |
| `/home` | Téléportation (cooldown post-mort) |
| `/delhome` | Supprime le home |

---

## ⛈ Storm

**Package :** `fr.zyumie.storm` | **Depend :** `Main`

### Algorithme de planification hebdomadaire

```
Chaque lundi (semaine réelle) :
  D ← tirage aléatoire dans possible-durations (défaut: [1,2,3,4,5])
  J ← tirage aléatoire dans {1..7-D+1}  (contrainte: J+D-1 ≤ 7)

Exemple: D=5 → J ∈ {Lun, Mar, Mer}  (sinon la tempête déborde du dimanche)
```

### Fonctionnalités
- Nuit éternelle (`DO_DAYLIGHT_CYCLE = false`)
- Orage permanent (storm + thunder verrouillés)
- Détection surface : scan vertical bloc par bloc → précis sur toute la hauteur 1.21 (-64 à +320)
- Dégâts : `player.setNoDamageTicks(0)` + `player.damage()` → dégâts réels même après un éclair
- Persistance JSON → survit aux redémarrages
- **Hook `CustomItems`** : `StormManager.setImmunityChecker(Predicate<Player>)` injecté par réflexion

### Commandes
| Commande | Description |
|----------|-------------|
| `/storm start [1-5]` | Force une tempête de N jours |
| `/storm stop` | Arrêt immédiat |
| `/storm status` | État actuel ou prochaine tempête planifiée |
| `/storm schedule` | Regénère le planning de la semaine |
| `/storm reload` | Recharge la config à chaud |

---

## ✦ CustomItems

**Package :** `fr.zyumie.customitems` | **Depend :** `Main` | **Softdepend :** `Storm`, `FactionPlugin`

Tous les items sont identifiés via **PDC** (`NamespacedKey` + `PersistentDataType.STRING`) → aucun conflit avec le vanilla.

### 🛡 Armures

| Armure | ID PDC | Mécanique |
|--------|--------|-----------|
| **Anti-Tempête** | `storm_armor` | Set complet → immunité totale Storm (Predicate injecté dans StormManager) |
| **Fantôme** | `ghost_armor` | Invisibilité + nametag caché (Scoreboard Team) — brisée 5s à la moindre touche |
| **Sauvetage** | `rescue_armor` | ≤ 1 cœur → téléportation aléatoire sécurisée (rayon 15b, cooldown configurable) |
| **Âmes** | `soul_armor` | +2 cœurs par kill (AttributeModifier MAX_HEALTH) — reset total si pièce retirée ou mort |

### ⚔️ Armes

| Arme | ID PDC | Mécanique |
|------|--------|-----------|
| **Épée Céleste** | `celestial_sword` | Kill → propulsion +30b → transformation en Masse → slam = one-shot + re-propulsion — raté = 0 dégât de chute |
| **Arc Explosif** | `explosive_bow` | Flèche taguée PDC → impact joueur → explosion AoE (dégâts sans destruction de blocs) |
| **Arc Ender** | `ender_bow` | Flèche taguée PDC → impact joueur → tireur téléporté 2b derrière la cible |
| **Épée Berserk** | `berserk_sword` | Clic droit → AttackSpeed ×∞ 10s (style PvP 1.8) + buff membres de faction à 10b (CD 5min) |

### Commandes
| Commande | Description |
|----------|-------------|
| `/items give <joueur> <item>` | Donne l'item (set 4 pièces pour les armures) |

**Noms disponibles :** `storm`, `ghost`, `rescue`, `soul`, `celestial`, `explosive`, `ender`, `berserk`

---

## 📁 Structure du projet

```
SMP/
├── settings.gradle.kts
├── build.gradle.kts
│
├── Main/src/main/java/fr/zyumie/main/
│   ├── MainPlugin.java
│   ├── api/           MainAPI.java
│   ├── module/        Module.java, ModuleManager.java
│   ├── storage/       DataStore.java, YamlDataStore.java, MySQLDataStore.java
│   ├── integration/   MisaneBridge.java
│   └── util/          AdminCommand.java
│
├── Oxygen/src/main/java/fr/zyumie/main/oxygen/
│   ├── OxygenPlugin.java, OxygenManager.java
│   ├── api/           OxygenAPI.java, OxygenSupplier.java, events/
│   ├── data/          OxygenData.java
│   ├── listener/      PlayerConnectionListener.java
│   └── task/          OxygenTickTask.java
│
├── OxygenArmor/src/main/java/fr/zyumie/oxygenArmor/
│   ├── OxygenArmor.java (Module + JavaPlugin + commande)
│   ├── ArmorManager.java, ArmorOxygenSupplier.java
│   ├── data/          ArmorData.java
│   ├── listener/      ArmorEquipListener.java, CustomCraftListener.java
│   ├── task/          ArmorDisplayTask.java, ArmorStandChargeTask.java
│   └── util/          ArmorItemUtil.java
│
├── Money/             {command, listener} + VaultEconomyProvider
├── ShopAdmin/         {gui, command, listener, model} + Shop.yml, Gui.yml
├── Hdv/               {gui, command, listener, model} + Gui.yml
│
├── FactionPlugin/     {api, command, listener, model}
├── WeeklyQuestPlugin/ {command, listener, model}
├── ProfilePlugin/     {command}
├── HomePlugin/        {command, listener}
│
├── Storm/             StormManager, StormScheduler, StormState + storm_state.json
└── CustomItems/
    ├── ItemRegistry.java, CustomItems.java
    ├── handler/armor/   Storm, Ghost, Rescue, Soul
    ├── handler/weapon/  Celestial, ExplosiveBow, EnderBow, Berserk
    ├── listener/        Armor, Damage, Kill, Projectile, Interact
    ├── command/         GiveItemCommand.java
    └── util/            ItemBuilder.java, SafeTeleport.java
```

---

## ⚙️ Installation

```bash
# 1. Cloner le repo
git clone https://github.com/zyumie-prod/SMP.git

# 2. Builder tous les modules
./gradlew build

# 3. Copier les jars dans /plugins
# L'ordre de chargement est garanti par les déclarations depend/softdepend

# 4. Démarrer le serveur
# 5. Configurer plugins/Main/config.yml (stockage YAML ou MySQL)
```

> **Pré-requis :** PaperMC 1.21.11+ · Java 21 · Vault · Optionnel: NewV5 (Minsane)

---

## 🔑 Permissions globales

| Permission | Plugin | Défaut |
|------------|--------|--------|
| `mainplugin.admin` | Main | OP |
| `oxygenplugin.admin` | Oxygen | OP |
| `oxygenarmor.admin` | OxygenArmor | OP |
| `money.admin` | Money | OP |
| `shopadmin.admin` | ShopAdmin | OP |
| `hdv.admin` | Hdv | OP |
| `storm.admin` | Storm | OP |
| `storm.bypass` | Storm/CustomItems | Accordée par Armure Anti-Tempête |
| `customitems.admin` | CustomItems | OP |

---

<div align="center">

© 2026 Zyumie. Tous droits réservés.  
Aucune utilisation, modification ou redistribution sans autorisation explicite.

</div>
