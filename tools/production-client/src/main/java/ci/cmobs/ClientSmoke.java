package ci.cmobs;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientSmoke implements ClientModInitializer {
    private int ticks;
    private boolean finished;
    private String lastScreen = "";

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("cmobs")) {
            throw new IllegalStateException("CMobs production mod was not loaded");
        }
        if (System.getProperty("cmobs.ci.posture") != null) {
            PostureClientProbe.register();
            return;
        }
        System.out.println("CMOBS_CLIENT_PROBE_INITIALIZED");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            String screen = client.currentScreen == null ? "none" : client.currentScreen.getClass().getName();
            if (!screen.equals(lastScreen)) {
                System.out.println("CMOBS_CLIENT_SCREEN " + screen);
                lastScreen = screen;
            }
            if (!(client.currentScreen instanceof TitleScreen)) {
                ticks = 0;
                return;
            }
            if (++ticks < 40) return;
            finished = true;
            Path dir = client.runDirectory.toPath();
            try (NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                image.writeTo(dir.resolve("cmobs-title.png"));
                Files.writeString(dir.resolve("cmobs-title-ready.txt"),
                        "CMOBS_CLIENT_TITLE_OK " + image.getWidth() + "x" + image.getHeight());
                System.out.println("CMOBS_CLIENT_TITLE_OK");
                client.scheduleStop();
            } catch (Exception e) {
                throw new IllegalStateException("Client screenshot probe failed", e);
            }
        });
    }
}
