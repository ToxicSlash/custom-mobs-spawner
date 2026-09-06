# Custom Mobs Spawner

Fabric 1.20.1 mod for data-driven custom mobs, replacements, extra spawn rules, skills, and posture combat.

The mod namespace is `cmobs`.

## Data Paths

Use these datapack paths:

```text
data/<namespace>/cmobs/custom_mobs/**/*.json
data/<namespace>/cmobs/replacements/*.json
data/<namespace>/cmobs/groups/*.json
data/<namespace>/cmobs/spawn_rules/*.json
data/<namespace>/cmobs/skills/*.json
data/<namespace>/cmobs/player_skills/*.json
data/<namespace>/cmobs/player_skills/gem_skills/**/*.json
data/<namespace>/spells/*.json
```

Bundled examples live in:

```text
src/main/resources/data/cmobs/cmobs/skills/examples
docs/examples
```

Skill files can be nested. For example, `data/cmobs/cmobs/skills/zombie/knight_parry.json` becomes skill id `cmobs:zombie/knight_parry`. Files in `cmobs/player_skills` use the same schema and are attached to players automatically. Bundled gem examples live under `data/cmobs/cmobs/player_skills/gem_skills/<gem>/`.

## Custom Mobs

Custom mob definitions wrap a vanilla or modded entity type and apply names, tags, equipment, attributes, loot tables, NBT, and skills.

```json
{
  "entity": "minecraft:zombie",
  "custom_name": "Example Knight",
  "custom_name_visible": true,
  "tags": ["example_knight"],
  "skills": ["cmobs:lost/knight_overhead_strike"],
  "attributes": [
    {"id": "minecraft:generic.max_health", "base": 60.0},
    {"id": "minecraft:generic.attack_damage", "base": 10.0}
  ],
  "health": 60.0,
  "equipment": {
    "mainhand": {"id": "minecraft:iron_sword", "count": 1},
    "offhand": {"id": "minecraft:shield", "count": 1}
  },
  "loot_table": "minecraft:entities/zombie",
  "nbt": "{CanPickUpLoot:0b,PersistenceRequired:1b}"
}
```

Summon one directly:

```mcfunction
/csummon cmobs:overworld/lost/lost_knight
/csummon cmobs:overworld/lost/lost_knight ~ ~ ~
```

Optional spawn integrations can run command-backed setup for Pehkui and L2Hostility after the custom mob is spawned. These fields are ignored if the target command does not exist.

```json
{
  "pehkui": {
    "hitbox": 1.2,
    "model": 1.2,
    "delay_ticks": 1
  },
  "l2hostility": {
    "level": {
      "operation": "setAndRerollTrait",
      "amount": 100
    },
    "clear_traits": true,
    "traits": [
      {"id": "l2hostility:soul_burner", "level": 3, "operation": "set"},
      {"id": "l2hostility:counter_strike", "level": 1, "operation": "add"}
    ]
  }
}
```

`pehkui.hitbox` sets `pehkui:hitbox_height` and `pehkui:hitbox_width`; `pehkui.model` sets `pehkui:model_height` and `pehkui:model_width`. Exact keys such as `hitbox_height`, `model_width`, or custom `types` entries are also supported. L2Hostility level operations support `set`, `add`, `setAndRerollTrait`, and `addAndRerollTrait`. Trait operations support `set`, `add` as an alias for setting/adding the rank, and `remove`.

## Replacements

Replacement rules run once when mobs load.

```json
{
  "source": "#cmobs:replaceable",
  "chance": 0.15,
  "replacements": [
    {"custom_mob": "cmobs:overworld/zombie/frail_zombie", "weight": 8},
    {"entity": "minecraft:creeper", "weight": 1},
    {"function": "cmobs:spawn/special", "weight": 1}
  ]
}
```

`source` accepts an entity id or an entity type tag. If `chance` succeeds, one replacement entry is chosen by weight.

Replacement entries can also target a mob group:

```json
{
  "source": "minecraft:zombified_piglin",
  "chance": 0.08,
  "dimensions": ["minecraft:the_nether"],
  "replacements": [
    {"group": "cmobs:charred", "weight": 1}
  ]
}
```

Groups live in `data/<namespace>/cmobs/groups/*.json`. Group replacements are ignored for mobs spawned by spawners; single-mob replacements still run, and non-empty equipment from the spawner source mob overrides the replacement's custom equipment.

```json
{
  "members": [
    {"custom_mob": "cmobs:nether/charred/kindling", "x": 0.0, "y": 0.0, "z": 0.0},
    {"custom_mob": "cmobs:nether/charred/charred_hog", "x": 1.4, "y": 0.0, "z": 0.4}
  ],
  "pools": [
    {
      "chance": 0.4,
      "x": -1.4,
      "y": 0.0,
      "z": 0.4,
      "entries": [
        {"custom_mob": "cmobs:nether/charred/charred_enforcer", "weight": 80},
        {"custom_mob": "cmobs:nether/charred/charred_sentinel", "weight": 30}
      ]
    }
  ]
}
```

`members` always attempt to spawn, unless a member has its own `chance`. Each `pool` rolls its `chance` and then chooses one weighted `entries` item. Entries can use `custom_mob` or `entity`, and positions are offsets from the mob being replaced. `offset: [x, y, z]` is also accepted.

## Spawn Rules

Spawn rules can target a vanilla entity, custom mob, or function.

```json
{
  "custom_mob": "cmobs:overworld/zombie/frail_zombie",
  "dimensions": ["minecraft:overworld"],
  "biomes": ["#minecraft:is_overworld"],
  "min_light": 0,
  "max_light": 7,
  "blocks": ["#minecraft:base_stone_overworld"],
  "extra_spawn": true,
  "interval_ticks": 200,
  "attempts_per_player": 1,
  "chance": 0.5,
  "min_distance": 24,
  "max_distance": 48,
  "spawn_group": "monster",
  "group_mob_cap": 70,
  "mob_cap": 4,
  "obey_do_mob_spawning": true
}
```

The old custom zombie spawn rule is not active anymore, but a preserved example is in `docs/examples/spawn_rules/custom_zombie.json`.

## Skills

Supported triggers:

```text
when_hurt
when_attacks
when_spell_cast
on_ranged_hit
failed_attack
when_killed
timed
```

Common skill-level fields include:

```json
{
  "trigger": "timed",
  "interval_ticks": 20,
  "chance": 1.0,
  "predicate": "cmobs:skills/example",
  "requires_living_target": true,
  "trigger_range": 12.0,
  "target_range": 8.0,
  "hit_range": 3.0,
  "cooldown_on_start": false,
  "global_cooldown_ticks": 40,
  "cooldown_ticks": 100
}
```

Useful examples:

```text
skills/examples/timed_sequence_combo.json
skills/examples/telegraph_release_attack.json
skills/examples/stance_parry_aura.json
skills/examples/projectile_action_examples.json
skills/examples/particle_arc_and_sound_offsets.json
skills/examples/teleport_action_examples.json
skills/examples/random_action_examples.json
skills/examples/when_hurt_defense.json
skills/examples/when_killed_explosion.json
```

## Conditions

Skill-level and action-level `conditions` accept one object or an array. `target` defaults to `self`; use `target` to check the current attack target or hurt source context.

```json
{
  "conditions": [
    {
      "type": "health",
      "max": 100.0
    },
    {
      "type": "nearby_entity",
      "entity_type": "minecraft:player",
      "radius": 30.0
    },
    {
      "type": "effect",
      "id": "cmobs:precision",
      "amount": {
        "min": 1,
        "max": 10
      }
    },
    {
      "type": "weapon_gem",
      "slot": "mainhand",
      "gem_type": "ruby",
      "gem_tier": 2
    }
  ]
}
```

`nearby_entity` also accepts `min_distance`, `max_distance`, `min_count`, `include_self`, `tags`, `exclude_tags`, `entity_types`, and `exclude_entity_types`. `effect` conditions also accept `min_level` / `max_level`, and `status_effect` is accepted as an alias. `weapon_gem` checks the selected item stack's `Gems` NBT; it accepts `id` / `gem_id`, or Smitherz-style `gem_type` and `gem_tier` values such as `topaz` and `1`.

Spell JSON can add cmobs cast conditions:

```json
"cmobs_conditions": [
  {
    "type": "effect",
    "id": "cmobs:flow",
    "min_level": 5,
    "max_level": 10
  }
]
```

`min_level` / `max_level` use the displayed stack count, so an amplifier of `4` is level `5`.

## Status Effects

Use `effect` actions to apply vanilla or registered mod effects. Use `effect_clear` with an `id` to remove one effect, or without an `id` to clear all effects from the current action context.

```json
{
  "type": "effect",
  "id": "cmobs:bloodlust",
  "target": "self",
  "duration": 80,
  "amplifier": 0,
  "stack": true,
  "amplifier_increment": 1,
  "max_amplifier": 2,
  "stack_duration": true,
  "duration_increment": 80,
  "max_duration": 120
}
```

`amplifier` is zero-based. `stack: true` stacks against the existing effect amplifier, `stack_duration: true` extends the existing duration, and `proc_coefficient_duration: true` scales durations by the active skill proc coefficient.

Registered `cmobs` effects:

| Effect | Mechanics |
| --- | --- |
| `cmobs:vulnerable` | Harmful. Increases posture damage taken by `vulnerablePostureDamageMultiplierPerLevel` per displayed level. |
| `cmobs:turning_slow` | Harmful. Clamps mob yaw/body/head turning to `6 / level` degrees per tick, minimum 3 degrees. |
| `cmobs:flow` | Attack speed +2%, posture damage +2, and posture damage multiplier +2% per level. Cleared on mainhand swap. |
| `cmobs:momentum` | Attack range +0.2, posture damage +5, and posture damage multiplier +5% per level. Cleared on mainhand swap. |
| `cmobs:precision` | Attack speed +2% and movement speed +3% per level, optional crit chance +2.5% per level. Decays one stack at a time when expiring and is cleared on mainhand swap. |
| `cmobs:bloodlust` | Attack speed +3% per level. Optionally adds spell haste +5%, rage +4%, and RPGMana cost -5% per level when those attributes exist. |
| `cmobs:kindle` | Optionally adds fire and arcane spell power +4% per level. |
| `cmobs:mana_ward` | Optionally adds warding +4, RPGMana regen +0.5, and RPGMana cost -5% per level. |
| `cmobs:mana_influx` | Optionally adds RPGMana regen +10 per level. |
| `cmobs:combustion` | Harmful. Smokes every tick and deals `2 + 2 * amplifier` damage every 10 ticks to the affected entity, bypassing armor, shields, and normal hurt invulnerability. Resistance/effect reductions and protection enchantments still apply. At expiry it detonates with `simplyskills:fire_explosion`, dealing `15 + 5 * amplifier` damage in the spell radius or 6 blocks. Damaged mobs have a 10% chance to receive a 20 tick combustion chain. |
| `cmobs:static` | Optional crit chance +4% for Zenith Attributes and Spell Power. Used as the charge checked by `static_chain`. |
| `cmobs:verdant_growth` | Optionally adds damage reflection +6% and life steal +3% per level. |
| `cmobs:resolve` | Armor +4 and posture damage resistance +0.05 per level. |
| `cmobs:soulchill` | Harmful. Movement speed -4% per level, optional damage taken +2% per level. |
| `cmobs:shadowflow` | Optionally adds crit chance +2.5% and dodge chance +2% per level. |
| `cmobs:focus` | Optionally adds draw speed +4%, arrow velocity +4%, and armor shred +0.15 per level. |
| `cmobs:shadowphase` | Movement speed +15% per level, optional dodge chance +15% per level. |

Optional attribute modifiers are only added when the companion mod has registered that attribute.

## Mana Potions And Capacitors

`cmobs:lesser_mana_potion` restores 25 RPGMana, uses the drink animation for 12 ticks, has a 40 tick item cooldown, leaves a glass bottle, and grants a small amount of XP based on actual mana restored.

`cmobs:greater_mana_potion` restores 100 RPGMana and can restore into the player's overcharge capacity. Amethyst Imbuement mana potions are also bridged to RPGMana and restore 50 mana when RPGMana is present.

`cmobs:mana_capacitor` is a belt trinket and Botania mana item. It stores 3750 Botania mana, equal to 300 RPGMana, and adds +80 `cmobs:mana_overcharge` while equipped in the belt slot. If a player drops to 20 RPGMana or lower and does not already have `cmobs:mana_influx`, it consumes 40 stored RPGMana and applies Mana Influx for 80 ticks.

## Gem Player Skills

Gem skills are normal player skill JSONs, usually gated by a `weapon_gem` condition and a mainhand item tag.

```json
{
  "trigger": "when_attacks",
  "chance": 0.03,
  "proc_coefficient": {
    "type": "attack_speed",
    "baseline_attack_speed": 1.6,
    "min": 0.75,
    "max": 2.0,
    "spell_min_interval_ticks": 20,
    "spell_coef": 3.0
  },
  "combo_increase_proc%": 0.05,
  "combo_minimum": 3,
  "conditions": [
    {
      "type": "weapon_gem",
      "gem_type": "topaz",
      "gem_tier": 1
    }
  ],
  "require_mainhand": "#cmobs:usable_weapons"
}
```

`proc_coefficient` scales proc chance by held attack speed or ranged draw speed. In spell-hit context it uses the spell damage coefficient, respects `spell_min_interval_ticks`, and uses `spell_coef` to scale combo-based proc chance. `combo_minimum` blocks procs until the player reaches that combo count, then `combo_increase_proc%` adds chance per combo count before the final chance is clamped from 0 to 1.

Bundled tier 1 gem effects:

| Gem | Trigger | Effect |
| --- | --- | --- |
| Amethyst | `when_attacks` | Stacks `cmobs:shadowflow` up to level 10. At level 10 it grants `cmobs:shadowphase` for 100 ticks and clears Shadowflow. |
| Citrine | `when_attacks` and `timed` | Stacks `cmobs:static` up to level 3. A timed discharge checks Static level 3+ every 40 ticks and casts `static_chain` with `cmobs:static_beam`, range 16, and up to 2 chains. |
| Diamond | `when_attacks` | Stacks `cmobs:resolve` up to level 3. |
| Emerald | `when_attacks` | Stacks `cmobs:verdant_growth` up to level 3. |
| Jade | `on_ranged_hit` | Requires `#cmobs:bows`, starts at combo 2, and stacks `cmobs:focus` up to level 5. |
| Ruby | `when_attacks` | Stacks `cmobs:bloodlust` up to level 3, with extra feedback sounds and particles at stack thresholds. |
| Sapphire | `when_attacks` | Stacks `cmobs:mana_ward` up to level 3, with extra feedback sounds and particles at stack thresholds. |
| Tanzanite | `when_attacks` | Applies `cmobs:soulchill` to the target up to level 3. At Soulchill level 3+ it also applies `more_rpg_classes:frozen_solid` for 60 ticks. |
| Topaz | `when_attacks` | Stacks `cmobs:kindle` on the player up to level 3. Its inflict skill applies `cmobs:combustion` to the target for 40 ticks, stacking up to level 3 without extending duration. |

## Action Basics

Common action targets are `self`, `owner`, `target`, `players`, `mobs`, and `living`. Area actions use `radius`; area filters can use `tags`, `exclude_tags`, `entity_type`, `entity_types`, `exclude_entity_type`, and `exclude_entity_types`.

Delayed action lists use relative `delay_ticks`. Each delay is added to the previous actions in the same list.

```json
{
  "actions": [
    {
      "type": "sound",
      "id": "minecraft:entity.generic.drink",
      "delay_ticks": 0
    },
    {
      "type": "heal",
      "amount": 8.0,
      "delay_ticks": 32
    },
    {
      "type": "delay",
      "ticks": 5
    },
    {
      "type": "equip",
      "slot": "mainhand",
      "item": {
        "id": "minecraft:iron_sword"
      }
    }
  ]
}
```

`random` picks one weighted choice. A choice may be a single action or an `actions` array.

```json
{
  "type": "random",
  "actions": [
    {
      "weight": 2,
      "action": {
        "type": "effect",
        "id": "minecraft:speed",
        "duration": 100,
        "amplifier": 1
      }
    },
    {
      "weight": 1,
      "actions": [
        {
          "type": "sound",
          "id": "minecraft:entity.zombie.ambient"
        },
        {
          "type": "heal",
          "base_health_percent": 0.10
        }
      ]
    }
  ]
}
```

Use `{ "type": "clear_combo" }` in an action list to reset the player's weapon combo.

## Healing

Use `{ "type": "heal" }` to restore health. It supports the same `target` and `radius` fields as effect actions.

```json
{
  "type": "heal",
  "target": "mobs",
  "radius": 6.0,
  "amount": 4.0,
  "current_health_percent": 0.10,
  "base_health_percent": 0.05
}
```

`amount` is flat health points. `current_health_percent` heals from current health, `base_health_percent` heals from the entity's base max-health attribute, and `max_health_percent` heals from current max health after modifiers. Values are additive and can be scaled with `heal_scale`.

## Combat Actions

Damage, posture damage, knockback, stuns, and reflected damage are native actions.

```json
[
  {
    "type": "damage",
    "target": "players",
    "radius": 4.0,
    "amount": 6.0,
    "knockback": true
  },
  {
    "type": "posture_damage",
    "target": "players",
    "radius": 4.0,
    "amount": 35.0,
    "blockable": true
  },
  {
    "type": "knockback",
    "target": "players",
    "radius": 4.0,
    "strength": 0.7,
    "y_strength": 0.2
  },
  {
    "type": "stun",
    "duration": 30,
    "slowness_amplifier": 4,
    "mining_fatigue_amplifier": 1
  },
  {
    "type": "reflect_damage",
    "multiplier": 0.5,
    "knockback": true
  }
]
```

`reflect_damage` uses the incoming damage source, so it is mainly useful on `when_hurt` skills. `damage` can also use `use_attack_damage: true` with `damage_scale`. Player damage actions can add `combo_scale`, which adds `combo_count * combo_scale` to the final damage scale before damage is applied.

## Equipment, Summons, And Rally

```json
[
  {
    "type": "equip",
    "slot": "offhand",
    "item": {
      "id": "minecraft:shield",
      "count": 1
    }
  },
  {
    "type": "summon_custom_mob",
    "id": "cmobs:zombie/miner",
    "count": 2,
    "forward_offset": 1.5,
    "target_owner_target": true
  },
  {
    "type": "rally_target",
    "target": "mobs",
    "radius": 18.0,
    "entity_type": "minecraft:zombie",
    "max_targets": 6,
    "stop_navigation": true
  }
]
```

Equipment stacks accept the same item object shape as custom mob equipment. `summon_custom_mob` also accepts `custom_mob` as an id key.

## Movement And Animation

```json
[
  {
    "type": "swing",
    "hand": "mainhand"
  },
  {
    "type": "lunge",
    "strength": 0.45,
    "y_strength": 0.05
  },
  {
    "type": "dodge",
    "distance": 2.0,
    "fallback_velocity": true
  },
  {
    "type": "attack_cooldown",
    "ticks": 20
  }
]
```

Player actions can request Better Combat animations:

```json
{
  "type": "swing",
  "better_combat": true,
  "animation": "bettercombat:one_handed_slash_horizontal_right",
  "length": 20.0,
  "upswing": 0.5
}
```

## Particle Arcs

Particle actions support relative offsets, absolute `x/y/z`, and arcs. The complete arc example is:

```json
{
  "type": "particle",
  "id": "minecraft:crit",
  "count": 20,
  "y_offset": 1.0,
  "spread": 0.12,
  "speed": 0.08,
  "forward_offset": 0.0,
  "side_offset": 0.0,
  "vertical_offset": 0.0,
  "arc": {
    "points": 12,
    "count_per_point": 2,
    "radius": 2.5,
    "start_degrees": -90.0,
    "end_degrees": 90.0,
    "y_offset": 1.0,
    "end_y_offset": 1.0,
    "forward_offset": 0.0,
    "side_offset": 0.0,
    "spread": 0.08,
    "speed": 0.06
  }
}
```

`forward_offset`, `side_offset`, and `vertical_offset` are relative to the caster facing. Absolute `x/y/z` overrides relative positioning for non-arc particles.

For area actions with `target` and `radius`, use `radius_forward_offset`, `radius_side_offset`, and `radius_vertical_offset` to move only the target search area. This lets a particle or sound still emit at each selected target without inheriting the area offset.

Actions may include a `conditions` block with the same shape as skill-level `conditions`. If an action condition fails, only that action is skipped.

## Projectiles

Projectile actions support:

```json
{
  "type": "projectile",
  "projectile_type": "minecraft:arrow",
  "target": "target",
  "trajectory": "arc",
  "arc": true,
  "arc_lift_scale": 0.16,
  "velocity": 1.2,
  "amount": 1,
  "spread": 1.0,
  "y_offset": 1.4,
  "forward_offset": 0.6,
  "trail": {
    "id": "minecraft:crit",
    "count": 2,
    "points": 2,
    "interval_ticks": 1,
    "y_offset": 0.0,
    "spread": 0.02,
    "speed": 0.0
  },
  "hit_enemy": [],
  "hit_ground": []
}
```

`target` can be `target` or `forward`. `trajectory: "arc"` or `"arc": true` adds vertical lift when a living target exists.

Use `trail` for one projectile particle trail, or `trails` for an array of trail objects. Trail fields are `id`, `count`, `points`, `interval_ticks`, `start_delay_ticks`, `y_offset`, `spread`, and `speed`. `points` interpolates between the projectile's previous and current position to reduce gaps on fast projectiles.

On projectile impact, `particle`, `sound`, and `explosion` actions automatically receive the impact `x/y/z`.

`hit_ground` and `hit_enemy` can also create an anchored aura at the impact position:

```json
{
  "type": "aura",
  "duration_ticks": 100,
  "tick_interval_ticks": 5,
  "radius": 4.0,
  "target": "players",
  "aura_start": [],
  "aura_tick": [
    {
      "type": "particle",
      "id": "minecraft:portal",
      "aura_interval_ticks": 10,
      "count": 18,
      "y_offset": 0.2,
      "spread": 1.4,
      "speed": 0.02
    },
    {
      "type": "knockback",
      "target": "players",
      "aura_interval_ticks": 20,
      "strength": -0.18,
      "y_strength": 0.02
    }
  ],
  "aura_end": []
}
```

Negative `knockback.strength` pulls nearby targets toward the aura center. Positive strength pushes them away.

`tick_interval_ticks` is the parent/default aura cadence. Individual `aura_tick` and `aura_nearby` actions can set `aura_interval_ticks` to run faster or slower than the parent cadence; existing `interval_ticks` also works. The aura scheduler wakes at the smallest configured interval, but nearby target scans only run on ticks where a target-side aura action is due, so visual-only faster ticks stay cheap.

## Grenades

`grenade` launches an item entity, ticks optional projectile trails, and runs actions when the fuse expires.

```json
{
  "type": "grenade",
  "item": "minecraft:gunpowder",
  "target": "target",
  "horizontal_velocity": 0.7,
  "vertical_velocity": 0.45,
  "spread": 0.5,
  "fuse_ticks": 45,
  "trail": {
    "id": "minecraft:smoke",
    "count": 2,
    "interval_ticks": 2,
    "spread": 0.02
  },
  "detonate_actions": [
    {
      "type": "particle",
      "id": "minecraft:explosion",
      "count": 1
    },
    {
      "type": "damage",
      "target": "players",
      "radius": 4.0,
      "amount": 8.0
    }
  ]
}
```

`detonate_actions` may include normal actions plus `spell_engine_impact` and `spell_engine_rain`.

## Spell Engine Actions

Use `spell` for projectile spells, `spell_impact` for immediate area impacts, and `spell_rain` for raining projectiles over targets.

```json
[
  {
    "type": "spell",
    "spell": "wizards:arcane_bolt",
    "target": "target",
    "target_y_scale": 0.65,
    "velocity": 0.55,
    "damage_override": 3.0,
    "count": 1,
    "play_release_effects": true
  },
  {
    "type": "spell_impact",
    "spell": "cmobs:static_beam",
    "target": "living",
    "radius": 5.0,
    "damage_override": 10.0
  },
  {
    "type": "spell_rain",
    "spell": "wizards:arcane_bolt",
    "target": "players",
    "radius": 8.0,
    "count": 3,
    "launch_height": 7.0,
    "launch_radius": 1.5
  }
]
```

`static_chain` uses `cmobs:static_beam` by default and chains from the caster to nearby hostile targets.

```json
{
  "type": "static_chain",
  "range": 8.0,
  "chain_chance": 0.6,
  "max_chains": 3,
  "beam_points": 24,
  "impact_particles": 10,
  "contribute_combo": false
}
```

## Commands

Native actions are preferred, but function and command actions are available for compatibility.

```json
[
  {
    "type": "function",
    "id": "cmobs:debug/example"
  },
  {
    "type": "command",
    "command": "say cmobs skill fired"
  }
]
```

## Combat Attributes

Registered attributes:

```text
cmobs:parry_window_ticks
cmobs:posture_health
cmobs:posture_regen
cmobs:posture_damage
cmobs:pvp_taken_posture_damage_multiplier
cmobs:stagger_damage_multiplier
cmobs:attack_range
```

Item tags:

```text
cmobs:parry_swords
cmobs:knives
cmobs:rapiers
cmobs:claymores
```

Items in `cmobs:knives` and `cmobs:rapiers` get configurable bonus posture damage and bonus stagger damage while held in the main hand. Stagger damage has a configurable base multiplier of `1.5`; a configured weapon bonus of `1.0` displays as `+100%` and doubles the configured base stagger multiplier.

Config fields in `config/cmobs.json`:

```json
{
  "visceralCombatLungeScalingEnabled": true,
  "visceralCombatExtraLungeScale": 0.2,
  "visceralCombatMinimumExtraLunge": 0.01,
  "showSelfOverheadPostureDebugBar": true,
  "playerBasePostureHealth": 100.0,
  "playerBasePostureRegen": 80.0,
  "mobBasePostureHealth": 15.0,
  "mobBasePostureRegen": 100.0,
  "maxHealthPostureScale": 0.5,
  "maxHealthPostureBonusCap": 1000.0,
  "mobMaxHealthPostureScale": 3.0,
  "mobMaxHealthPostureBonusCap": 10000.0,
  "vulnerablePostureDamageMultiplierPerLevel": 0.5,
  "baseStaggerDamageMultiplier": 2.0,
  "shieldParryWindowTicks": 5.0,
  "twoHandedParryWindowTicks": 2.0,
  "shieldPostureHealthBonus": 50.0,
  "twoHandedPostureHealthBonus": 25.0,
  "playerParryCounterWindowTicks": 10,
  "playerStaggerTicks": 40,
  "playerStaggerCooldownTicks": 80,
  "playerPostureRegenDelayTicks": 60,
  "playerParryPostureInvulnerabilityTicks": 5,
  "playerParryPostureRestore": 10.0,
  "playerParryPostureDamage": 40.0,
  "playerMaxPostureDamagePerHit": 25.0,
  "playerPvpPostureDamageMultiplier": 1.0,
  "playerTakenDamagePostureMultiplier": 0.25,
  "playerBlockedDamagePostureMultiplier": 1.25,
  "playerBlockedSkillPostureMultiplier": 1.15,
  "playerMaxTakenDamagePostureDamage": 80.0,
  "mobPostureRegenDelayTicks": 80,
  "mobPostureDisplayTicks": 400,
  "mobStaggerTicks": 40,
  "mobStaggerCooldownTicks": 80,
  "mobParryPostureDamage": 80.0,
  "knifePostureDamageBonus": 10.0,
  "knifeStaggerDamageMultiplierBonus": 1.0,
  "rapierPostureDamageBonus": 10.0,
  "rapierStaggerDamageMultiplierBonus": 1.0
}
```

Posture formulas:

```text
mob posture max = mobBasePostureHealth + cmobs:posture_health attribute bonus + min(mobMaxHealthPostureBonusCap, max_health * mobMaxHealthPostureScale)
mob posture regen per second = mobBasePostureRegen + cmobs:posture_regen attribute bonus
player posture max = playerBasePostureHealth + equipped parry item posture + min(maxHealthPostureBonusCap, max_health * maxHealthPostureScale)
vulnerable posture damage = posture_damage * (1 + vulnerablePostureDamageMultiplierPerLevel * vulnerable_level)
pvp posture damage = min(playerMaxPostureDamagePerHit, posture_damage * playerPvpPostureDamageMultiplier)
stagger hit damage = damage * baseStaggerDamageMultiplier * weapon_stagger_bonus_factor
```
