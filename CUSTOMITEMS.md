# ✦ CustomItems — Wiki Complet

> **Plugin :** `fr.zyumie.customitems` | **Version :** 0.5 | **Paper 1.21.11+**

---

## 📑 Table des matières

1. [Système d'identification (PDC)](#-système-didentification-pdc)
2. [Armures Custom](#-armures-custom)
   - [Anti-Tempête](#-armure-anti-tempête)
   - [Fantôme](#-armure-fantôme)
   - [Sauvetage](#-armure-de-sauvetage)
   - [Âmes](#-armure-voleuse-dâmes)
3. [Armes Custom](#-armes-custom)
   - [Épée Céleste](#️-épée--masse-céleste)
   - [Arc Explosif](#-arc-explosif)
   - [Arc Ender](#-arc-ender-ender-bow)
   - [Épée Berserk](#️-épée-berserk)
4. [Resource Pack & Texture Customization](#-resource-pack--texture-customization)
   - [Système item_model (1.21.4+)](#système-item_model-1214)
   - [Structure des fichiers](#structure-des-fichiers)
   - [Modèles JSON par item](#modèles-json-par-item)
   - [Tooltips & identifiants](#tooltips--identifiants)
   - [Intégration dans le code](#intégration-dans-le-code)
5. [Commandes & Permissions](#-commandes--permissions)
6. [Configuration complète](#-configuration-complète)
7. [Événements & API](#-événements--api)

---

## 🔑 Système d'identification (PDC)

Chaque item custom est identifié via un **tag PDC** (`PersistentDataContainer`) invisible pour les joueurs, unique et résistant aux redémarrages.

```
NamespacedKey : customitems:custom_item
Type          : PersistentDataType.STRING
Valeur        : identifiant unique de l'item (voir tableau ci-dessous)
```

### Tableau des identifiants PDC

| Catégorie | Item | ID PDC | Matériau de base |
|-----------|------|--------|-----------------|
| Armure | Anti-Tempête | `storm_armor` | DIAMOND_* |
| Armure | Fantôme | `ghost_armor` | LEATHER_* |
| Armure | Sauvetage | `rescue_armor` | IRON_* |
| Armure | Âmes | `soul_armor` | NETHERITE_* |
| Arme | Épée Céleste | `celestial_sword` | DIAMOND_SWORD |
| Arme | Masse Céleste *(temporaire)* | `celestial_mace` | MACE |
| Arme | Arc Explosif | `explosive_bow` | BOW |
| Arme | Arc Ender | `ender_bow` | BOW |
| Arme | Épée Berserk | `berserk_sword` | NETHERITE_SWORD |

> **Lire l'ID d'un item en jeu :** L'ID est stocké dans les données NBT de l'item, non affiché dans le tooltip vanilla. Pour déboguer, utilisez `/data get entity @s SelectedItem` en jeu.

---

## 🛡 Armures Custom

### 🌩 Armure Anti-Tempête

**ID PDC :** `storm_armor` | **Config :** `armors.storm`

#### Mécanique
- Requiert les **4 pièces** pour l'immunité (configurable via `require-full-set`)
- Injecte un `Predicate<Player>` dans `StormManager` via réflexion au démarrage
- Lorsque `immunityChecker.test(player)` retourne `true`, **dégâts ET effets de tempête** sont annulés

#### Flux d'activation
```
CustomItems.onModuleEnable()
  └─ hookStorm()
       └─ StormManager.setImmunityChecker(p -> stormHandler.isImmune(p))
            └─ StormArmorHandler.isImmune(player)
                 └─ compte les 4 pièces PDC "storm_armor"
                      ├─ 4/4 → true  (immunité totale)
                      └─ < 4 → false (vulnérable)
```

#### Configuration
```yaml
armors:
  storm:
    enabled: true
    require-full-set: true          # false = 1 pièce suffit
    display-name: "&9&lArmure Anti-Tempête"
    lore:
      - "&7Forgée dans l'œil d'une tempête millénaire."
      - ""
      - "&9✦ Immunité totale à la tempête apocalyptique"
    helmet:
      material: DIAMOND_HELMET
      custom-model-data: 2001       # CMD legacy (si pas item_model)
    chestplate:
      material: DIAMOND_CHESTPLATE
      custom-model-data: 2002
    leggings:
      material: DIAMOND_LEGGINGS
      custom-model-data: 2003
    boots:
      material: DIAMOND_BOOTS
      custom-model-data: 2004
    enchantments:
      PROTECTION_ENVIRONMENTAL: 4
      DURABILITY: 3
    messages:
      equipped:   "&9⛈ Armure anti-tempête enfilée. Tu es protégé."
      unequipped: "&7L'armure anti-tempête a été retirée."
```

---

### 👻 Armure Fantôme

**ID PDC :** `ghost_armor` | **Config :** `armors.ghost`

#### Mécanique
1. **Set complet porté** → `PotionEffect INVISIBILITY (amplifier 1, durée MAX_VALUE)` appliqué
2. **Nametag caché** via `Scoreboard Team` avec `NAME_TAG_VISIBILITY = NEVER`
3. **Coup reçu** (`EntityDamageByEntityEvent`, damage > 0) → `makeVisible(player, true)`
   - Retire l'effet Invisibilité
   - Retire le joueur de la Team scoreboard
   - Planifie `runTaskLater(revealDuration * 20L)` → `makeInvisible()` à nouveau
4. **Pièce retirée** (détectée par `ArmorListener.checkArmorChanges`) → nettoyage complet

> ⚠️ **Limitation vanilla :** L'effet Invisibilité (même amplifier 1) peut laisser apparaître les particules de potion si `show_particles = true`. Passez `show_particles = false` dans le code (déjà configuré ainsi) pour un rendu plus propre. Pour masquer totalement l'armure, un **resource pack** avec des textures transparentes est recommandé.

#### Configuration
```yaml
armors:
  ghost:
    enabled: true
    require-full-set: true
    display-name: "&f&lArmure Fantôme"
    helmet:
      material: LEATHER_HELMET
      custom-model-data: 2011
      color: "0x000000"             # Dye noir — minimise la visibilité
    chestplate:
      material: LEATHER_CHESTPLATE
      custom-model-data: 2012
      color: "0x000000"
    leggings:
      material: LEATHER_LEGGINGS
      custom-model-data: 2013
      color: "0x000000"
    boots:
      material: LEATHER_BOOTS
      custom-model-data: 2014
      color: "0x000000"
    reveal-duration-seconds: 5      # Durée de visibilité après un coup
    messages:
      invisible:  "&f👻 Tu es invisible."
      revealed:   "&c💥 Invisibilité brisée pendant &e{duration}s&c !"
      restored:   "&f👻 Tu redeviens invisible."
```

---

### 🚨 Armure de Sauvetage

**ID PDC :** `rescue_armor` | **Config :** `armors.rescue`

#### Mécanique
```
EntityDamageEvent / EntityDamageByEntityEvent (victime = porteur)
  └─ 1 tick plus tard (runTaskLater 1L)
       └─ RescueArmorHandler.checkRescue(player)
            ├─ HP > trigger-health (défaut: 2.0 = 1 cœur) → ignoré
            ├─ cooldown actif → message remaining
            └─ OK → SafeTeleport.findSafe(origin, radius, 30 tentatives)
                      ├─ Position trouvée → particules PORTAL + son + tp
                      └─ Aucune position sûre → message erreur
```

#### Algorithme SafeTeleport
1. Génère un angle aléatoire `θ ∈ [0, 2π]` et une distance `d ∈ [0.3r, r]`
2. Descend depuis `Y_origine + 10` vers `Y_min` en cherchant :
   - `block[y]` : solide, non-liquide, non-feu
   - `block[y+1]` et `block[y+2]` : passables (air)
3. Jusqu'à 30 tentatives avant d'abandonner

#### Configuration
```yaml
armors:
  rescue:
    enabled: true
    require-full-set: false         # Une seule pièce suffit
    display-name: "&a&lArmure de Sauvetage"
    helmet:
      material: IRON_HELMET
      custom-model-data: 2021
    chestplate:
      material: IRON_CHESTPLATE
      custom-model-data: 2022
    leggings:
      material: IRON_LEGGINGS
      custom-model-data: 2023
    boots:
      material: IRON_BOOTS
      custom-model-data: 2024
    teleport-radius: 15             # Rayon max de téléportation (blocs)
    trigger-health: 2.0             # HP seuil (2.0 = 1 cœur)
    cooldown-seconds: 30            # Cooldown entre deux téléportations
    messages:
      triggered: "&a⚡ Téléportation d'urgence ! Tu as été sauvé."
      cooldown:  "&cCooldown actif encore &e{remaining}s&c."
```

---

### 💜 Armure Voleuse d'Âmes

**ID PDC :** `soul_armor` | **Config :** `armors.soul`

#### Mécanique
```
EntityDeathEvent (killer = porteur du set complet)
  └─ SoulArmorHandler.onKill(player)
       ├─ kills[player]++
       ├─ totalBonus = kills * hp-per-kill
       └─ applyBonus(player, totalBonus)
            ├─ Retire AttributeModifier "soul_armor_bonus" existant
            └─ Ajoute AttributeModifier(ADD_NUMBER, totalBonus)

Réinitialisation déclenchée par :
  ├─ Mort du joueur     → SoulArmorHandler.onDeath()
  └─ Pièce retirée      → SoulArmorHandler.onUnequip()
       ├─ Retire le AttributeModifier
       └─ Reclampe player.health si > nouveau MAX_HEALTH
```

> **Note technique :** L'utilisation de `Attribute.MAX_HEALTH` avec `AttributeModifier.Operation.ADD_NUMBER` est permanente tant que l'armure est portée. La valeur est correctement nettoyée à la mort/déséquipement même si le joueur se reconnecte (l'état est rechargé depuis DataStore).

#### Configuration
```yaml
armors:
  soul:
    enabled: true
    require-full-set: true
    display-name: "&5&lArmure Voleuse d'Âmes"
    helmet:
      material: NETHERITE_HELMET
      custom-model-data: 2031
    chestplate:
      material: NETHERITE_CHESTPLATE
      custom-model-data: 2032
    leggings:
      material: NETHERITE_LEGGINGS
      custom-model-data: 2033
    boots:
      material: NETHERITE_BOOTS
      custom-model-data: 2034
    hp-per-kill: 4.0                # HP bonus par kill (4.0 = 2 cœurs)
    max-bonus-hp: 0                 # 0 = illimité
    messages:
      kill-bonus:    "&5+2 cœurs ! &8[&5{total}&8 bonus actif(s)]"
      reset-death:   "&5💀 Âmes perdues à la mort."
      reset-unequip: "&5💔 Âmes libérées en retirant l'armure."
```

---

## ⚔️ Armes Custom

### ✨ Épée / Masse Céleste

**IDs PDC :** `celestial_sword` (forme initiale) · `celestial_mace` (forme en chute)

#### Flux complet
```
[1] Joueur tue une entité avec l'Épée Céleste
      └─ EntityDeathEvent → CelestialSwordHandler.onKillWithSword()
           ├─ inFlight.add(player)
           ├─ velocity.setY(launch-velocity)   → propulsion
           ├─ setFallDistance(-9999)            → annule dégâts de chute
           ├─ son FIREWORK_ROCKET_LAUNCH
           └─ runTaskLater(10 ticks) → equipMace()

[2] Joueur en l'air avec la Masse Céleste
      ├─ EntityDamageByEntityEvent (attacker en inFlight + CELESTIAL_MACE)
      │     └─ DamageListener → CelestialSwordHandler.onHitWithMace()
      │          ├─ event.setCancelled(true)
      │          ├─ target.setHealth(0)        → one-shot
      │          └─ launchPlayer() à nouveau   → boucle
      │
      └─ EntityDamageEvent (cause = FALL)
            └─ DamageListener.onEntityDamage()
                 ├─ event.setCancelled(true)    → 0 dégât de chute
                 └─ onFallLanding() → equipSword() + message "raté"
```

> ⏱ **Sécurité :** Si aucun atterrissage n'est détecté après 8 secondes (160 ticks), le joueur est retiré de `inFlight` et l'épée est restituée automatiquement.

#### Configuration
```yaml
weapons:
  celestial-sword:
    enabled: true
    display-name: "&e&l✦ Épée Céleste"
    material: DIAMOND_SWORD
    custom-model-data: 3001
    enchantments:
      DAMAGE_ALL: 5
      DURABILITY: 3
      FIRE_ASPECT: 2
    launch-velocity: 2.5            # Vitesse verticale (~30 blocs)
    mace-material: MACE             # Material 1.21 natif
    mace-display-name: "&e&l⚒ Masse Céleste"
    mace-custom-model-data: 3002
    messages:
      launch:       "&e⬆ Tu t'élèves vers les cieux !"
      mace-form:    "&e⚒ La masse est prête. Frappe !"
      kill-slam:    "&e💥 Impact céleste ! La cible est anéantie."
      miss-landing: "&e✓ Atterrissage sans dégât."
```

---

### 💥 Arc Explosif

**ID PDC :** `explosive_bow` | Tag flèche : `customitems:custom_bow_type = "explosive_bow"`

#### Mécanique
1. `ProjectileLaunchEvent` → flèche taguée PDC avec `"explosive_bow"`
2. `ProjectileHitEvent` → cible = joueur → `ExplosiveBowHandler.triggerExplosion()`
   - `real-explosion: false` → particules + son + dégâts AoE manuels (pas de destroy blocs)
   - `real-explosion: true` → `World.createExplosion()` (destroy blocs selon config serveur)
3. Boucle sur `world.getNearbyEntities(radius)` → `setNoDamageTicks(0)` + `damage()`
4. La flèche est `remove()` après impact pour éviter les doubles déclenchements

#### Configuration
```yaml
weapons:
  explosive-bow:
    enabled: true
    display-name: "&c&l💥 Arc Explosif"
    material: BOW
    custom-model-data: 3011
    enchantments:
      ARROW_DAMAGE: 3
      DURABILITY: 3
    explosion-radius: 4.0           # Rayon AoE (blocs)
    explosion-damage: 6.0           # Dégâts par entité dans le rayon
    real-explosion: false           # true = destruction de blocs
    explosion-particle: EXPLOSION_LARGE
    messages:
      explosion: "&c💥 Explosion ! &e{hits} &ccibles touchées."
```

---

### 🌀 Arc Ender (Ender Bow)

**ID PDC :** `ender_bow` | Tag flèche : `customitems:custom_bow_type = "ender_bow"`

#### Mécanique
```
ProjectileHitEvent (Arrow taguée "ender_bow", cible = Player)
  └─ EnderBowHandler.teleportBehindTarget(shooter, target)
       ├─ direction = target.getLocation().getDirection().normalize()
       ├─ behind = target.getLocation() - direction * teleport-offset
       ├─ behind.setDirection(direction)    → le tireur fait face à la cible
       ├─ Particules PORTAL (départ + arrivée)
       ├─ Son ENDERMAN_TELEPORT
       └─ shooter.teleport(behind)
```

#### Configuration
```yaml
weapons:
  ender-bow:
    enabled: true
    display-name: "&d&l🌀 Arc Ender"
    material: BOW
    custom-model-data: 3021
    enchantments:
      ARROW_DAMAGE: 2
      DURABILITY: 3
    teleport-offset: 2.0            # Blocs derrière la cible
    teleport-particle: PORTAL
    messages:
      teleported: "&d🌀 Téléporté derrière &e{target}&d !"
```

---

### ⚡ Épée Berserk

**ID PDC :** `berserk_sword` | **Config :** `weapons.berserk-sword`

#### Mécanique
```
PlayerInteractEvent (RIGHT_CLICK + HAND principal + berserk_sword en main)
  └─ BerserkSwordHandler.activate(player)
       ├─ Vérifie cooldown → message si actif
       ├─ applyAttackSpeed(player, 1024.0)
       │    └─ Attribute.ATTACK_SPEED + AttributeModifier(ADD_NUMBER, 1024)
       │         → supprime le cooldown d'attaque (style PvP 1.8)
       ├─ buffFactionMembers(player, rayon 10b)
       │    └─ getFactionOf(nearby) == getFactionOf(player)
       │         └─ applyAttackSpeed(nearby) + runTaskLater(durée) → removeAttackSpeed
       └─ runTaskLater(durée * 20L) → deactivate()
            └─ removeAttackSpeed() + message ended
```

> **Style PvP 1.8 :** La valeur `ATTACK_SPEED = 1024` est volontairement excessive (base = 4, 1.8 était ≈ 16). Cela supprime effectivement tout cooldown visible.

#### Configuration
```yaml
weapons:
  berserk-sword:
    enabled: true
    display-name: "&4&l⚡ Épée Berserk"
    material: NETHERITE_SWORD
    custom-model-data: 3031
    enchantments:
      DAMAGE_ALL: 5
      DURABILITY: 3
      LOOTING: 3
    berserk-duration-seconds: 10    # Durée du mode berserk
    cooldown-seconds: 300           # 5 minutes
    faction-buff-radius: 10.0       # Rayon du buff faction (blocs)
    attack-speed-value: 1024.0      # Valeur ATTACK_SPEED (supprime le cooldown)
    messages:
      activated:      "&4⚡ MODE BERSERK ! &e{duration}s &4de fureur !"
      faction-buffed: "&4⚡ &e{player} &4active le Berserk ! Tu es aussi en rage !"
      ended:          "&7Berserk terminé."
      cooldown:       "&cCooldown actif: &e{remaining}s &crestant(e)s."
```

---

## 🎨 Resource Pack & Texture Customization

### Système `item_model` (1.21.4+)

Depuis Paper **1.21.4**, la méthode recommandée pour lier un item à une texture custom n'est plus `setCustomModelData(int)` mais `itemModel(NamespacedKey)`. Cette approche :

- ✅ Pas de numéros entiers à gérer
- ✅ Le modèle JSON est directement référencé par son chemin
- ✅ Compatible avec les tooltips custom (les identifiants que tu as configurés)
- ✅ Indépendant du matériau de base

```java
// Exemple d'application dans ItemBuilder (Paper 1.21.4+)
meta.itemModel(new NamespacedKey("zyumie", "celestialSword"));
```

---

### Tooltips — Identifiants configurés

Voici les identifiants `item_model` actuellement assignés aux armes :

| Item | Namespace | Identifiant | Chemin modèle |
|------|-----------|-------------|---------------|
| Épée Céleste | `zyumie` | `celestialSword` | `assets/zyumie/models/item/celestialSword.json` |
| Masse Céleste | `zyumie` | `celestialMace` | `assets/zyumie/models/item/celestialMace.json` |
| Arc Explosif | `zyumie` | `ExplosiveBow` | `assets/zyumie/models/item/ExplosiveBow.json` |
| Arc Ender | `zyumie` | `EnderBow` | `assets/zyumie/models/item/EnderBow.json` |
| Épée Berserk | `zyumie` | `BerserkSword` | `assets/zyumie/models/item/BerserkSword.json` |

> **Convention :** Le namespace `zyumie` correspond au dossier `assets/zyumie/` dans le resource pack.

---

### Structure des fichiers

```
resourcepack/
├── pack.mcmeta
├── pack.png
└── assets/
    └── zyumie/
        ├── models/
        │   └── item/
        │       ├── celestialSword.json       ← Épée Céleste
        │       ├── celestialMace.json        ← Masse Céleste (forme en chute)
        │       ├── ExplosiveBow.json         ← Arc Explosif
        │       ├── EnderBow.json             ← Arc Ender
        │       ├── BerserkSword.json         ← Épée Berserk
        │       │
        │       ├── storm_armor_helmet.json   ← Armure Anti-Tempête
        │       ├── storm_armor_chest.json
        │       ├── storm_armor_legs.json
        │       ├── storm_armor_boots.json
        │       │
        │       ├── ghost_armor_helmet.json   ← Armure Fantôme
        │       ├── ghost_armor_chest.json    ← (transparent recommandé)
        │       ├── ghost_armor_legs.json
        │       ├── ghost_armor_boots.json
        │       │
        │       └── ...                       ← rescue, soul
        └── textures/
            └── item/
                ├── celestial_sword.png
                ├── celestial_mace.png
                ├── explosive_bow.png
                ├── ender_bow.png
                ├── berserk_sword.png
                └── ...
```

#### `pack.mcmeta`
```json
{
  "pack": {
    "pack_format": 46,
    "description": "Zyumie SMP — Custom Items v0.5"
  }
}
```

> `pack_format: 46` correspond à **Minecraft 1.21.4**. Pour 1.21.x antérieur à 1.21.4, utilisez `34`.

---

### Modèles JSON par item

#### Épée Céleste — `celestialSword.json`
```json
{
  "parent": "item/handheld",
  "textures": {
    "layer0": "zyumie:item/celestial_sword"
  }
}
```

#### Masse Céleste — `celestialMace.json`
```json
{
  "parent": "item/handheld",
  "textures": {
    "layer0": "zyumie:item/celestial_mace"
  }
}
```

#### Arc Explosif — `ExplosiveBow.json`
```json
{
  "parent": "item/bow",
  "textures": {
    "layer0": "zyumie:item/explosive_bow"
  },
  "overrides": [
    { "predicate": { "pulling": 1 },                            "model": "zyumie:item/explosive_bow_pulling_0" },
    { "predicate": { "pulling": 1, "pull": 0.65 },             "model": "zyumie:item/explosive_bow_pulling_1" },
    { "predicate": { "pulling": 1, "pull": 0.9 },              "model": "zyumie:item/explosive_bow_pulling_2" }
  ]
}
```

> 💡 Les états `pulling_0/1/2` sont optionnels — supprime les `overrides` si tu n'as qu'une texture statique.

#### Arc Ender — `EnderBow.json`
```json
{
  "parent": "item/bow",
  "textures": {
    "layer0": "zyumie:item/ender_bow"
  },
  "overrides": [
    { "predicate": { "pulling": 1 },              "model": "zyumie:item/ender_bow_pulling_0" },
    { "predicate": { "pulling": 1, "pull": 0.65 },"model": "zyumie:item/ender_bow_pulling_1" },
    { "predicate": { "pulling": 1, "pull": 0.9 }, "model": "zyumie:item/ender_bow_pulling_2" }
  ]
}
```

#### Épée Berserk — `BerserkSword.json`
```json
{
  "parent": "item/handheld",
  "textures": {
    "layer0": "zyumie:item/berserk_sword"
  }
}
```

#### Armure Anti-Tempête (exemple casque) — `storm_armor_helmet.json`
```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "zyumie:item/storm_armor_helmet"
  }
}
```

---

### Intégration dans le code

#### Méthode moderne (1.21.4+) — `item_model`

Ajoute cette méthode dans `ItemBuilder.java` pour les armes :

```java
// Dans ItemBuilder.buildWeapon(), après setCustomModelData :
String itemModelId = section.getString("item-model"); // ex: "celestialSword"
if (itemModelId != null && !itemModelId.isEmpty()) {
    NamespacedKey modelKey = new NamespacedKey("zyumie", itemModelId);
    meta.setItemModel(modelKey);
    // Note: setItemModel() est disponible depuis Paper 1.21.4
}
```

#### Dans `config.yml` (champ à ajouter par arme)

```yaml
weapons:
  celestial-sword:
    item-model: "celestialSword"    # ← chemin: assets/zyumie/models/item/celestialSword.json
    custom-model-data: 3001         # ← conservé pour compatibilité legacy

  celestial-sword:
    mace-item-model: "celestialMace"
    mace-custom-model-data: 3002

  explosive-bow:
    item-model: "ExplosiveBow"
    custom-model-data: 3011

  ender-bow:
    item-model: "EnderBow"
    custom-model-data: 3021

  berserk-sword:
    item-model: "BerserkSword"
    custom-model-data: 3031
```

#### Compatibilité `custom-model-data` (legacy pre-1.21.4)

Si tu cibles des versions antérieures à 1.21.4, `setCustomModelData(int)` reste fonctionnel. Les deux champs peuvent coexister : `item_model` prend la priorité sur les clients modernes.

---

### Résumé des CMD par item

| Item | `custom-model-data` | `ToolTips` | `item-model` |
|------|---------------------|------------|--------------|
| Storm Helmet | 2001 | None | `storm_armor_helmet` |
| Storm Chest | 2002 | None | `storm_armor_chest` |
| Storm Legs | 2003 | None | `storm_armor_legs` |
| Storm Boots | 2004 | None | `storm_armor_boots` |
| Ghost Helmet | 2011 | None | `ghost_armor_helmet` |
| Ghost Chest | 2012 | None | `ghost_armor_chest` |
| Ghost Legs | 2013 | None | `ghost_armor_legs` |
| Ghost Boots | 2014 | None | `ghost_armor_boots` |
| Rescue Helmet | 2021 | None | `rescue_armor_helmet` |
| Rescue Chest | 2022 | None | `rescue_armor_chest` |
| Rescue Legs | 2023 | None | `rescue_armor_legs` |
| Rescue Boots | 2024 | None | `rescue_armor_boots` |
| Soul Helmet | 2031 | None | `soul_armor_helmet` |
| Soul Chest | 2032 | None | `soul_armor_chest` |
| Soul Legs | 2033 | None | `soul_armor_legs` |
| Soul Boots | 2034 | None | `soul_armor_boots` |
| **Épée Céleste** | **3001** | celestialSword | **`celestialSword`** |
| **Masse Céleste** | **3002** | celestialMace | **`celestialMace`** |
| **Arc Explosif** | **3011** | ExplosiveBow | **`ExplosiveBow`** |
| **Arc Ender** | **3021** | EnderBow | **`EnderBow`** |
| **Épée Berserk** | **3031** | BerserkSword | **`BerserkSword`** |

---

## 🎮 Commandes & Permissions

### Commandes

| Commande | Alias | Description | Permission |
|----------|-------|-------------|------------|
| `/items give <joueur> <item>` | `/ci give`, `/customitems give` | Donne un item custom | `customitems.admin` |

**Noms d'items disponibles pour `/items give` :**

| Nom court | Nom complet | Donne |
|-----------|-------------|-------|
| `storm` | `storm_armor` | 4 pièces Armure Anti-Tempête |
| `ghost` | `ghost_armor` | 4 pièces Armure Fantôme |
| `rescue` | `rescue_armor` | 4 pièces Armure Sauvetage |
| `soul` | `soul_armor` | 4 pièces Armure Âmes |
| `celestial` | `celestial_sword` | Épée Céleste x1 |
| `explosive` | `explosive_bow` | Arc Explosif x1 |
| `ender` | `ender_bow` | Arc Ender x1 |
| `berserk` | `berserk_sword` | Épée Berserk x1 |

### Permissions

| Permission | Description | Défaut |
|------------|-------------|--------|
| `customitems.admin` | Toutes les commandes admin | OP |
| `storm.bypass` | Immunité aux effets Storm (accordée par l'armure) | false |

---

## ⚙️ Configuration complète

### `config.yml` — Paramètres généraux

```yaml
general:
  prefix: "&8[&6Items&8] &7"
  block-container-storage: false    # Empêche le stockage dans des coffres (WIP)
  show-particles: true              # Particules lors des effets actifs

messages:
  no-permission:    "&cTu n'as pas la permission."
  player-not-found: "&cJoueur introuvable: &e{player}"
  item-not-found:   "&cItem inconnu: &e{item}"
  give-success:     "&aItem &e{item} &adonné à &e{player}&a."
  give-received:    "&aTu as reçu: &e{item}&a."
```

### Champs communs aux armures

```yaml
armors:
  <id>:
    enabled: true|false             # Activer/désactiver le set
    require-full-set: true|false    # 4 pièces ou 1 seule suffit
    display-name: "..."             # Nom affiché (codes &)
    lore:                           # Lore (liste de lignes)
      - "ligne 1"
    helmet|chestplate|leggings|boots:
      material: MATERIAL_NAME       # Matériau Bukkit
      custom-model-data: 0          # CMD (0 = désactivé)
      item-model: ""                # item_model key (1.21.4+, "" = désactivé)
      color: null                   # Couleur hex pour LEATHER uniquement
    enchantments:
      NOM_ENCHANTEMENT: niveau
```

### Champs communs aux armes

```yaml
weapons:
  <id>:
    enabled: true|false
    display-name: "..."
    lore:
      - "ligne"
    material: MATERIAL_NAME
    custom-model-data: 0
    item-model: ""                  # Tooltip identifier (1.21.4+)
    enchantments:
      NOM_ENCHANTEMENT: niveau
```

---

## 🔧 Événements & API

### Événements écoutés

| Listener | Event | Action |
|----------|-------|--------|
| `ArmorListener` | `InventoryClickEvent` | Détecte équip/déséquip → check delta 1 tick |
| `ArmorListener` | Task 10 ticks | Tick `GhostArmorHandler.onArmorTick()` |
| `ArmorListener` | `PlayerJoinEvent/QuitEvent` | Load/unload + nettoyage |
| `DamageListener` | `EntityDamageByEntityEvent` | Ghost reveal · Celestial slam · Rescue trigger |
| `DamageListener` | `EntityDamageEvent` | Annule chute Céleste · Rescue trigger (dégâts non-entité) |
| `KillListener` | `EntityDeathEvent` | Celestial launch · Soul +HP |
| `KillListener` | `PlayerDeathEvent` | Reset Ghost · Berserk · Celestial · Soul |
| `ProjectileListener` | `ProjectileLaunchEvent` | Tag PDC sur les flèches des arcs custom |
| `ProjectileListener` | `ProjectileHitEvent` | AoE explosif · Tp Ender |
| `InteractListener` | `PlayerInteractEvent` | Clic droit Berserk → activation |

### Intégration Storm

```java
// Depuis CustomItems.hookStorm() via réflexion :
StormManager.setImmunityChecker(Predicate<Player> checker)

// Predicate retourne true si le joueur est immunisé
// → dégâts AND effets de tempête annulés pour ce joueur
```

### Détection d'un item custom depuis un autre plugin

```java
// Vérifier si un item est custom
var meta = item.getItemMeta();
var container = meta.getPersistentDataContainer();
NamespacedKey key = new NamespacedKey(customItemsPlugin, "custom_item");
String id = container.get(key, PersistentDataType.STRING);
// id = "celestial_sword", "ghost_armor", etc. — ou null si pas custom
```

---

*© 2026 Zyumie — Zyumie Production. Tous droits réservés.*
WIKIEOF
