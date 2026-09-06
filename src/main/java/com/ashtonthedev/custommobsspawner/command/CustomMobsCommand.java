package com.ashtonthedev.custommobsspawner.command;

import com.ashtonthedev.custommobsspawner.data.CustomMobData;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class CustomMobsCommand {
    private CustomMobsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("cmobs")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("reload")
                        .executes(context -> reload(context.getSource())))
                .then(CommandManager.literal("cast")
                        .then(CommandManager.literal("spell")
                                .then(CommandManager.argument("spell", IdentifierArgumentType.identifier())
                                        .executes(context -> cast(
                                                context.getSource(),
                                                "spell",
                                                IdentifierArgumentType.getIdentifier(context, "spell")
                                        ))))
                        .then(CommandManager.literal("impact")
                                .then(CommandManager.argument("spell", IdentifierArgumentType.identifier())
                                        .executes(context -> cast(
                                                context.getSource(),
                                                "impact",
                                                IdentifierArgumentType.getIdentifier(context, "spell")
                                        ))))
                        .then(CommandManager.literal("rain")
                                .then(CommandManager.argument("spell", IdentifierArgumentType.identifier())
                                        .executes(context -> cast(
                                                context.getSource(),
                                                "rain",
                                                IdentifierArgumentType.getIdentifier(context, "spell")
                                        ))))));
    }

    private static int reload(ServerCommandSource source) {
        CustomMobData.reloadAll(source.getServer().getResourceManager());
        source.sendFeedback(() -> Text.literal("Reloaded custom mob data"), true);
        return 1;
    }

    private static int cast(ServerCommandSource source, String mode, Identifier spellId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!CustomSkillRegistry.debugCastSpell(player, mode, spellId)) {
            source.sendError(Text.literal("Failed to cast debug spell " + spellId + " as " + mode));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Debug cast " + spellId + " as " + mode), false);
        return 1;
    }
}
