# CMobs — dev checkpoint
Baseline main/dev 4ccd122, Fabric 1.20.1, Java 17, Gradle 8.8. Main must remain untouched.
## Stage 1 (active)
- Confirmed baseline and inspected source/build/docs. No CI or tests in baseline.
- Fixed unanchored cmobs/ .gitignore; all assets/cmobs, data/cmobs and cmobs/default_configs absent in baseline. Restore authoritative original source or matching JAR, not recreated tooltip content.
- Nine exact local compile JARs in run/mods are untracked. Resolve from runner's authoritative checkout or pack, record versions/hashes. Botania is used unconditionally by ModItems but not declared as required; audit runtime dependency metadata.
- Sibling cozy CI uses Windows labels background and visual-test (ADADV_BACKGROUND_RUNNER_LABEL / ADADV_VISUAL_RUNNER_LABEL overrides). Actual cmobs assignment not confirmed: runner API denied.
- Acceptance: clean build, dedicated server boot + custom mob datapack probe, client boot + screenshots, automated gameplay tests. No live result yet. Do not count skipped/queued as passes.
## Stages
1. Infrastructure above; isolated runtimes and reproducible dependencies.
2. Mobs: attributes/health, NBT/equipment/loot/passengers, replacement/group/spawn stand/cap, reload, unload/restart. Investigate Ice and Fire Dragons, Cyclopes, Trolls initialization/tick attribute reset without overwriting external modifiers.
3. Skills: all seven triggers and action/condition families, delays/cooldowns, targeting, projectiles/auras/summons/Spell Engine, recursion and exactly-once damage.
4. Combat: posture/parry/stagger/combo, SmitherZ gems/upgrades, mana, effects, damage and multiplayer. Audit actual gem JSON and Java effects, not lore. Include swap, death, disconnect and missing optional mods.
5. Profile MSPT/TPS, memory, FPS and network; entity scans, timed skill discovery, UUID/cooldown lookups, state cleanup, cached definitions, particle traffic. Measure before changing.
6. Refactor CustomSkillRegistry without schema/ID/default/persistence changes.
7. Expand player/gem/weapon skills. Tier 1 passive, tier 2 finisher, tier 3 Purified/Attuned/Embraced facets, tiers 4–5 scale duration/chance/amplifier/cap. Prior concepts (not current behavior): Ruby Bloodlust/Execution, Sapphire Mana Ward/Rebound, Diamond Resolve/Bulwark, Emerald Growth/Bloom, Citrine Static/Discharge, Tanzanite Soulchill/Shatter, Jade Focus/Hunter's Mark, Amethyst Shadowflow/Shadow State; Topaz Kindle/Fireburst vs later crit/Expose proposal must be reconciled with actual code. Weapon combo state + unique Spell Engine active per family: Sword Flow, Claymore Momentum, Dagger Precision, Spear Rhythm, Rapier Tempo, Hammer Fracture, Axe Cleave, Sickle Harvest. Prior combo thresholds 3/6/15; audit and tune. Legendary candidates Starless Night (Event Horizon), Heartstealer (Crimson Tithe), Frost Slayer (Winter's Sentence). Reuse existing animation/skill infrastructure; test tier, swap, proc duplication and combo consumption.
8. Disposable ADADV-2.0 instance minus AutoModpack; preserve originals/saves. L2Hostility, Spell Engine, Better Combat, RPGMana, Zenith, Pehkui and installed integrations; visual runner for HUD/animations/particles.
Batch related work, consolidate tests, keep latest run/commit/evidence here; do not weaken tests or repeatedly audit finished work. Never mark stages complete before live evidence.
