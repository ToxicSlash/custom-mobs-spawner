package ci.cmobs;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
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
                configure("playerStaggerTicks", 400);
                configure("showSelfOverheadPostureDebugBar", true);
                System.out.println("CMOBS_POSTURE_SERVER_READY");
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("cmobs_ci_posture").requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                    .then(CommandManager.argument("action", StringArgumentType.word())
                        .executes(context -> execute(EntityArgumentType.getPlayer(context, "target"),
                                  StringArgumentType.getString(context, "action")))))));
    }
    private static int execute(ServerPlayerEntity player, String action) {
        try {
            switch (action) {
                case "partial", "stagger" -> {
                    Method m = handler.getMethod("applySkillPostureDamage", PlayerEntity.class, double.class, boolean.class);
                    m.invoke(null, player, action.equals("partial") ? 15.0 : 100000.0, true);
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
            boolean visible = stagger || health > 0 || contains("COMBAT_WINDOWS", id)
                    || contains("POSTURE_REGEN_DELAYS", id);
            int percent = stagger ? 0 : health > 0 ? (int)Math.round(Math.max(0, Math.min(1, 1 - damage / health)) * 100) : 100;
            System.out.println("CMOBS_POSTURE_TRUTH " + player.getName().getString() + " "
                    + percent + " " + visible + " " + stagger);
            return 1;
        } catch (Exception e) { throw new IllegalStateException("Posture test command failed", e); }
    }
}
