package ci.cmobs;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.player.PlayerEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class PostureClientProbe {
    private static final String PREFIX = "com.ashtonthedev.custommobsspawner.client.";
    private static final Path COORD = Path.of(System.getProperty("cmobs.ci.posture"));
    private static String completed = "", active = "";
    private static int ticks;
    private static String lastScreen = "";
    private static Object field(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }
    private static Object member(Object record, String name) throws Exception {
        Method m = record.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(record);
    }
    private static int percent(Object value) {
        return Math.round(((Number)value).floatValue() * 100);
    }
    public static void register() {
        System.out.println("CMOBS_POSTURE_CLIENT_PROBE_INIT");
        ClientTickEvents.END_CLIENT_TICK.register(PostureClientProbe::tick);
    }
    private static void tick(MinecraftClient client) {
        String screen = client.currentScreen == null ? "none" : client.currentScreen.getClass().getName();
        if (!screen.equals(lastScreen)) {
            System.out.println("CMOBS_POSTURE_SCREEN " + screen + " connected=" + (client.player != null));
            lastScreen = screen;
        }
        if (client.player == null || client.world == null) return;
        try {
            String me = client.player.getName().getString();
            Path phaseFile = COORD.resolve("phase.txt");
            String phase = Files.exists(phaseFile) ? Files.readString(phaseFile).trim() : "";
            if (phase.equals("stop")) { client.scheduleStop(); return; }
            if (phase.equals("miss-packet") && !phase.equals(completed)) {
                if (me.equals("CMobsA")) {
                    Identifier channel = new Identifier("cmobs", "failed_attack");
                    if (!ClientPlayNetworking.canSend(channel)) throw new IllegalStateException("Missing failed attack channel");
                    ClientPlayNetworking.send(channel, PacketByteBufs.empty());
                }
                Files.writeString(COORD.resolve(phase + "-" + me + ".ok"), "sent");
                completed = phase;
                return;
            }
            if (client.world.getPlayers().size() < 2) return;
            if (!client.player.isAlive()) throw new IllegalStateException("Unexpected fixture player death: " + me);
            if (!Files.exists(COORD.resolve(me + ".ready"))) Files.writeString(COORD.resolve(me + ".ready"), "ready");
            if (phase.equals(completed)) return;
            String[] args = phase.split("\\|");
            if (args.length != 8) return;
            String label = args[0], targetName = args[1], view = args[6];
            int expected = Integer.parseInt(args[2]);
            boolean visible = Boolean.parseBoolean(args[3]), staggered = Boolean.parseBoolean(args[4]);
            boolean selfBar = Boolean.parseBoolean(args[5]);
            boolean owner = me.equals(targetName);
            boolean remoteBar = Boolean.parseBoolean(args[7]);
            if (!phase.equals(active)) {
                active = phase;
                ticks = 0;
                if (client.currentScreen != null) client.setScreen(null);
                client.options.setPerspective(switch (view) {
                    case "front" -> Perspective.THIRD_PERSON_FRONT;
                    case "back", "inventory" -> Perspective.THIRD_PERSON_BACK;
                    default -> Perspective.FIRST_PERSON;
                });
                if (view.equals("inventory")) client.setScreen(new InventoryScreen(client.player));
            }
            if (++ticks < 12 || client.getOverlay() != null) return;
            PlayerEntity target = client.world.getPlayers().stream()
                    .filter(p -> p.getName().getString().equals(targetName)).findFirst().orElse(null);
            if (target == null) return;
            Class<?> overhead = Class.forName(PREFIX + "MobPostureBarRenderer");
            Object state = ((Map<?,?>)field(overhead, "POSTURE_STATES")).get(target.getId());
            boolean wantOverhead = visible && (owner ? selfBar : remoteBar);
            if (ticks % 60 == 0) {
                System.out.println("CMOBS_POSTURE_PHASE_DIAGNOSTIC " + label + " " + me
                        + " alive=" + client.player.isAlive() + " expected=" + expected
                        + " overhead=" + state + " wantOverhead=" + wantOverhead
                        + " screen=" + client.currentScreen);
            }
            if ((state != null) != wantOverhead) return;
            if (state != null) {
                if (percent(member(state, "fraction")) != expected ||
                        (boolean)member(state, "staggered") != staggered ||
                        percent(member(state, "staggerDamageFraction")) != (staggered ? 100 : 0)) return;
            }
            if (owner) {
                Class<?> hud = Class.forName(PREFIX + "PostureHudOverlay");
                if ((boolean)field(hud, "showPosture") != visible) return;
                if (visible && percent(field(hud, "postureFraction")) != expected) return;
            }
            try (NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                image.writeTo(COORD.resolve(label + "-" + me + ".png"));
            }
            Files.writeString(COORD.resolve(label + "-" + me + ".ok"),
                    "owner=" + owner + " expected=" + expected + " overhead=" + (state != null)
                    + " staggered=" + staggered + " view=" + view);
            System.out.println("CMOBS_POSTURE_CLIENT_PHASE_OK " + label + " " + me);
            completed = phase;
        } catch (Exception e) { throw new IllegalStateException("Posture client probe failed", e); }
    }
}
