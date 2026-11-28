package ci.cmobs;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

public final class PostureServerProbe implements ModInitializer {
    private static final String PREFIX = "com.ashtonthedev.custommobsspawner.";
    private static Class<?> handler;
    private static Object config;
    private static Object getField(Class<?> owner, String name, Object object) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }
    private static void configure(String field, Object value) throws Exception {
        Field f = config.getClass().getField(field);
        f.set(config, value);
    }
    private static double number(String name, UUID id) throws Exception {
        Object value = ((Map<?,?>)getField(handler, name, null)).get(id);
        return value == null ? 0 : ((Number)value).doubleValue();
    }
    private static boolean contains(String name, UUID id) throws Exception {
        return ((Map<?,?>)getField(handler, name, null)).containsKey(id);
    }
    @Override public void onInitialize() {
        if (System.getProperty("cmobs.ci.posture") == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                handler = Class.forName(PREFIX + "combat.PlayerParryHandler");
                config = Class.forName(PREFIX + "config.CustomMobsSpawnerConfig").getMethod("get").invoke(null);
                // Only this disposable fixture: hold states long enough for two clients to capture them.
                configure("playerPostureRegenDelayTicks", 1200);
                configure("playerStaggerTicks", 1800);
                configure("showSelfOverheadPostureDebugBar", true);
                System.out.println("CMOBS_POSTURE_SERVER_READY");
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> System.out.println("CMOBS_POSTURE_SERVER_STOPPED"));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("cmobs_ci_skill").requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .then(CommandManager.argument("target", EntityArgumentType.entity())
                        .then(CommandManager.argument("action", StringArgumentType.word())
                            .executes(context -> skill(EntityArgumentType.getPlayer(context, "player"),
                                    EntityArgumentType.getEntity(context, "target"),
                                    StringArgumentType.getString(context, "action"))))))));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("cmobs_ci_posture").requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                    .then(CommandManager.argument("action", StringArgumentType.word())
                        .executes(context -> execute(EntityArgumentType.getPlayer(context, "target"),
                                  StringArgumentType.getString(context, "action")))))));
    }
    private static int skill(ServerPlayerEntity player, Entity target, String action) {
        if (!(target instanceof LivingEntity living)) throw new IllegalArgumentException("Living fixture target required");
        try {
            Class<?> registry = Class.forName(PREFIX + "skill.CustomSkillRegistry");
            switch (action) {
                case "melee" -> {
                    float before = living.getHealth();
                    player.attack(living);
                    if (!(living.getHealth() < before)) throw new IllegalStateException("Melee caused no damage");
                }
                case "ranged" -> {
                    ArrowEntity arrow = new ArrowEntity(player.getWorld(), player);
                    if (!living.damage(player.getWorld().getDamageSources().arrow(arrow, player), 4))
                        throw new IllegalStateException("Arrow damage rejected");
                }
                case "miss" -> registry.getMethod("queueFailedPlayerAttack", ServerPlayerEntity.class).invoke(null, player);
                case "success" -> {
                    registry.getMethod("queueFailedPlayerAttack", ServerPlayerEntity.class).invoke(null, player);
                    registry.getMethod("markSuccessfulPlayerAttack", ServerPlayerEntity.class).invoke(null, player);
                }
                case "spell" -> registry.getMethod("runSpellCast", ServerPlayerEntity.class, Identifier.class, List.class)
                        .invoke(null, player, new Identifier("cmobs_ci_mp", "spell_test"), List.of(living));
                case "wrongspell" -> registry.getMethod("runSpellCast", ServerPlayerEntity.class, Identifier.class, List.class)
                        .invoke(null, player, new Identifier("cmobs_ci_mp", "wrong_spell"), List.of(living));
                case "combohit" -> {
                    Class<?> combo = Class.forName(PREFIX + "combat.ComboHandler");
                    Method get = combo.getMethod("getCombo", ServerPlayerEntity.class);
                    int before = (int)get.invoke(null, player);
                    float hp = living.getHealth();
                    player.attack(living);
                    if (!(living.getHealth() < hp)) throw new IllegalStateException("Combo fixture melee caused no damage");
                    // Simulate a duplicate callback for the same tick; it must not grant another combo.
                    combo.getMethod("recordDamageSuccess", LivingEntity.class, DamageSource.class)
                            .invoke(null, living, player.getWorld().getDamageSources().playerAttack(player));
                    int after = (int)get.invoke(null, player);
                    if (after != before + 1) throw new IllegalStateException("Combo gain not exactly one: " + before + " -> " + after);
                    System.out.println("CMOBS_COMBO_STATE " + after);
                }
                case "combosnapshot" -> {
                    Class<?> combo = Class.forName(PREFIX + "combat.ComboHandler");
                    System.out.println("CMOBS_COMBO_STATE " + combo.getMethod("getCombo", ServerPlayerEntity.class).invoke(null, player));
                }
                case "mana" -> {
                    Class<?> api = Class.forName("com.cleannrooster.rpgmana.api.ManaInterface");
                    Class<?> compat = Class.forName(PREFIX + "compat.AmethystRpgManaCompat");
                    Method get = api.getMethod("getMana");
                    Method max = api.getMethod("getMaxMana");
                    Method spend = api.getMethod("spendMana", double.class);
                    double maximum = ((Number)max.invoke(player)).doubleValue();
                    double initial = ((Number)get.invoke(player)).doubleValue();
                    double startMana = Math.min(30, maximum / 2);
                    spend.invoke(player, startMana - initial);
                    Class<?> ctx = Class.forName(PREFIX + "compat.AmethystRpgManaCompat$ItemCostContext");
                    Object context = ctx.getConstructor(net.minecraft.world.World.class, LivingEntity.class)
                            .newInstance(player.getWorld(), player);
                    Method check = compat.getMethod("checkManaCost", int.class, ctx);
                    if (!(boolean)check.invoke(null, 20, context) || (boolean)check.invoke(null, 1000, context))
                        throw new IllegalStateException("Mana affordability mismatch");
                    compat.getMethod("spendMana", int.class, LivingEntity.class).invoke(null, 10, player);
                    double remaining = ((Number)get.invoke(player)).doubleValue();
                    if (Math.abs(remaining - (startMana - 10)) > .01) throw new IllegalStateException("Wrong mana spend " + remaining);
                    compat.getMethod("restoreMana", PlayerEntity.class, int.class).invoke(null, player, 1000);
                    double restored = ((Number)get.invoke(player)).doubleValue();
                    if (Math.abs(restored - maximum) > .01) throw new IllegalStateException("Restore exceeds/fails max mana " + restored);
                    Class<?> overcharge = Class.forName(PREFIX + "compat.RpgManaOverchargeCompat");
                    double cap = ((Number)overcharge.getMethod("getOverchargeMaxMana", PlayerEntity.class, api).invoke(null, player, player)).doubleValue();
                    compat.getMethod("restoreMana", PlayerEntity.class, int.class, boolean.class).invoke(null, player, 1000, true);
                    double charged = ((Number)get.invoke(player)).doubleValue();
                    if (Math.abs(charged - cap) > .01) throw new IllegalStateException("Overcharge cap mismatch " + charged + " expected " + cap);
                    spend.invoke(player, initial - charged);
                    System.out.println("CMOBS_MANA_COMPAT_OK " + startMana + " " + remaining + " " + restored + " " + charged);
                }
                case "combatreset" -> {
                    // Isolate the real combat cases from the intentionally long display fixture.
                    configure("playerPostureRegenDelayTicks", 60);
                    configure("playerStaggerTicks", 40);
                    for (String name : List.of("POSTURE_HEALTH", "POSTURE_DAMAGE", "POSTURE_REGEN_DELAYS",
                            "STAGGER_WINDOWS", "STAGGER_COOLDOWNS", "COMBAT_WINDOWS",
                            "POSTURE_INVULNERABILITY_WINDOWS", "PARRY_COUNTER_WINDOWS",
                            "LAST_TAKEN_POSTURE_DAMAGE_TICKS")) {
                        ((Map<?,?>)getField(handler, name, null)).remove(player.getUuid());
                    }
                    for (String name : List.of("STAGGERED_BLOCKS", "SUCCESSFUL_BLOCKS", "ACTIVE_PARRY_BLOCKS", "ARMED_WEAPON_BLOCKS")) {
                        ((java.util.Set<?>)getField(handler, name, null)).remove(player.getUuid());
                    }
                    player.clearStatusEffects();
                    player.stopUsingItem();
                    player.setHealth(player.getMaxHealth());
                }
                case "startblock" -> {
                    player.stopUsingItem();
                    player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
                }
                case "blockstate" -> {
                    double window = ((Number)config.getClass().getField("shieldParryWindowTicks").get(config)).doubleValue();
                    System.out.println("CMOBS_BLOCK_STATE " + player.isUsingItem() + " "
                            + player.getItemUseTime() + " " + Math.round(window));
                }
                case "parry", "block", "unblocked" -> {
                    if (action.equals("parry")) {
                        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
                        player.setCurrentHand(Hand.OFF_HAND);
                        handler.getMethod("armParryBlock", PlayerEntity.class, ItemStack.class)
                                .invoke(null, player, player.getOffHandStack());
                    } else if (action.equals("unblocked")) {
                        player.stopUsingItem();
                    }
                    double before = number("POSTURE_DAMAGE", player.getUuid());
                    double attackerBefore = number("POSTURE_DAMAGE", living.getUuid());
                    float health = player.getHealth();
                    int durability = player.getOffHandStack().getDamage();
                    System.out.println("CMOBS_COMBAT_BEFORE " + action + " using=" + player.isUsingItem()
                            + " useTicks=" + player.getItemUseTime() + " posture=" + before
                            + " health=" + health + " durability=" + durability);
                    boolean applied = player.damage(living instanceof PlayerEntity attacker ? player.getWorld().getDamageSources().playerAttack(attacker) : player.getWorld().getDamageSources().mobAttack(living), 6);
                    double after = number("POSTURE_DAMAGE", player.getUuid());
                    double attackerAfter = number("POSTURE_DAMAGE", living.getUuid());
                    System.out.println("CMOBS_COMBAT_AFTER " + action + " applied=" + applied
                            + " posture=" + after + " health=" + player.getHealth()
                            + " durability=" + player.getOffHandStack().getDamage());
                    if (action.equals("parry")) {
                        if (applied || player.getHealth() != health || !contains("PARRY_COUNTER_WINDOWS", player.getUuid())
                                || !contains("POSTURE_INVULNERABILITY_WINDOWS", player.getUuid())
                                || attackerAfter <= attackerBefore)
                            throw new IllegalStateException("Parry did not cancel damage and punish attacker");
                    } else if (action.equals("block")) {
                        if (applied || player.getHealth() != health || after <= before
                                || player.getOffHandStack().getDamage() <= durability)
                            throw new IllegalStateException("Block did not absorb damage and consume posture/durability");
                    } else if (!applied || player.getHealth() >= health || after <= before) {
                        throw new IllegalStateException("Unblocked hit did not damage health and posture");
                    }
                    System.out.println("CMOBS_COMBAT_PROBE " + action + " " + before + " " + after
                            + " " + health + " " + player.getHealth());
                    if (!action.equals("block")) player.stopUsingItem();
                }
                case "snapshot" -> {}
                default -> throw new IllegalArgumentException(action);
            }
            System.out.println("CMOBS_SKILL_PROBE " + action + " " + amp(player, StatusEffects.SPEED)
                    + " " + amp(player, StatusEffects.STRENGTH) + " " + amp(player, StatusEffects.HASTE)
                    + " " + amp(living, StatusEffects.RESISTANCE));
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("CMOBS_PROBE_FAILURE " + action + " " + e);
            throw new IllegalStateException("Skill test command failed", e);
        }
    }
    private static int amp(LivingEntity entity, StatusEffect effect) {
        StatusEffectInstance instance = entity.getStatusEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }
    private static int execute(ServerPlayerEntity player, String action) {
        try {
            switch (action) {
                case "partial", "stagger" -> {
                    Method m = handler.getMethod("applySkillPostureDamage", PlayerEntity.class, double.class, boolean.class);
                    m.invoke(null, player, action.equals("partial") ? 15.0 : 100000.0, true);
                }
                case "longDisplay" -> {
                    configure("playerPostureRegenDelayTicks", 1200);
                    configure("playerStaggerTicks", 1800);
                }
                case "hideSelf" -> configure("showSelfOverheadPostureDebugBar", false);
                case "showSelf" -> configure("showSelfOverheadPostureDebugBar", true);
                case "snapshot" -> {}
                default -> throw new IllegalArgumentException(action);
            }
            UUID id = player.getUuid();
            double health = number("POSTURE_HEALTH", id);
            double damage = number("POSTURE_DAMAGE", id);
            boolean stagger = contains("STAGGER_WINDOWS", id);
            boolean visible = player.isAlive() && !player.isSpectator() && (stagger || health > 0 || contains("COMBAT_WINDOWS", id)
                    || contains("POSTURE_REGEN_DELAYS", id));
            int percent = stagger ? 0 : health > 0 ? (int)Math.round(Math.max(0, Math.min(1, 1 - damage / health)) * 100) : 100;
            System.out.println("CMOBS_POSTURE_TRUTH " + player.getName().getString() + " "
                    + percent + " " + visible + " " + stagger);
            return 1;
        } catch (Exception e) { throw new IllegalStateException("Posture test command failed", e); }
    }
}
