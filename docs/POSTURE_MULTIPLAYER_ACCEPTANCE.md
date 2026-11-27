# Multiplayer posture acceptance (Stage 4)

Run on a disposable dedicated server with **two actual independent clients** on the visual runner (not integrated server or single player). Reuse the known-good L2Hostility two-client harness and shared DevHub standard. Preserve existing posture damage/regen, parry windows, cooldown and config defaults. Self overhead debug bar is intentionally allowed in F5 and inventory while enabled; inventory rendering is not a bug by itself.

| Scenario | Required observation |
| --- | --- |
| Baseline | Both clients idle; no stale combat bars; separate player IDs. |
| A attacks B; swap roles | Server authoritative remaining posture equals B HUD and A's overhead reading (within 1% packet quantization). A's own overhead is its own state, not B's. |
| Shield and sword parry / block | Actual damage, item durability and posture loss/restore agree with server rules; no double application with Better Combat. |
| Stagger | Owner HUD empty; overhead shows broken posture (full red); both clear/recover together; vulnerability color orange when applicable. |
| F5 rear and front views | Local overhead bar follows own state and camera; no extra or mirrored bar attached to another player; self toggle false hides own overhead only. |
| Inventory | Own preview may display overhead if enabled; same fraction as HUD and world model; no stale other-player data or clipping that prevents using inventory. |
| Range/tracking | Approach during ongoing fight; new viewer receives latest state; out of range, name-tag hidden or invisible players do not show overhead; return restores current state. |
| Death/respawn, dimension change, disconnect/reconnect | No old entity ID or world cache leakage; same/new UUID, entity ID reuse, cooldown and HUD transition checked. |
| Regeneration and idle | Both clients see equal change and disappearance; no stale state after unload, no unnecessary idle packet flood. |
| Network | Run normal and controlled latency/jitter, compare server tick samples and client arrival order, allow bounded transport latency (not differing state); record packet counts. |
| Full pack | Repeat with installed Better Combat, Spell Engine, RPGMana, Zenith and pack HUD mods; capture screenshot evidence in first person, both F5 angles, inventory and remote player. |

Capture screenshots and per-client/server numeric state snapshots; do not use screenshot-only inspection as a synchronization assertion. Do not declare multiplayer complete before actual two-client evidence. Test-only helpers must not ship in gameplay JAR.
