package com.ashtonthedev.custommobsspawner.command;

import com.ashtonthedev.custommobsspawner.data.CustomMobRegistry;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class CustomSummonCommand {
    private CustomSummonCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("csummon")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("custom_mob", IdentifierArgumentType.identifier())
                        .suggests((context, builder) -> CommandSource.suggestIdentifiers(CustomMobRegistry.ids(), builder))
                        .executes(context -> summon(
                                context.getSource(),
                                IdentifierArgumentType.getIdentifier(context, "custom_mob"),
                                context.getSource().getPosition()
                        ))
                        .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                                .executes(context -> summon(
                                        context.getSource(),
                                        IdentifierArgumentType.getIdentifier(context, "custom_mob"),
                                        Vec3ArgumentType.getVec3(context, "pos")
                                )))));
    }

    private static int summon(ServerCommandSource source, Identifier customMobId, Vec3d pos) {
        BlockPos blockPos = BlockPos.ofFloored(pos);
        Entity entity = CustomMobRegistry.spawn(customMobId, source.getWorld(), blockPos, source.getRotation().y);
        if (entity == null) {
            source.sendError(Text.literal("Unknown or invalid custom mob: " + customMobId));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Summoned custom mob " + customMobId), true);
        return 1;
    }
}
