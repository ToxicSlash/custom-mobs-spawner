package com.ashtonthedev.custommobsspawner.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DefaultConfigInstaller {
    private static final String VISCERAL_COMBAT_MOD_ID = "visceral_combat";
    private static final String VISCERAL_COMBAT_SERVER_CONFIG_RESOURCE = "/cmobs/default_configs/visceral_combat/server_v2.json5";

    private DefaultConfigInstaller() {
    }

    public static void install() {
        installVisceralCombatDefaults();
    }

    private static void installVisceralCombatDefaults() {
        if (!FabricLoader.getInstance().isModLoaded(VISCERAL_COMBAT_MOD_ID)) {
            return;
        }

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path marker = configDir.resolve("cmobs").resolve("visceral_combat_defaults_installed");
        if (Files.exists(marker)) {
            return;
        }

        Path target = configDir
                .resolve("visceral_combat")
                .resolve("server_v2.json5");
        if (copyBundledResource(VISCERAL_COMBAT_SERVER_CONFIG_RESOURCE, target)) {
            try {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, "Installed bundled Visceral Combat defaults from CMobs.\n");
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean copyBundledResource(String resourcePath, Path target) {
        try (InputStream input = DefaultConfigInstaller.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return false;
            }

            Files.createDirectories(target.getParent());
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
