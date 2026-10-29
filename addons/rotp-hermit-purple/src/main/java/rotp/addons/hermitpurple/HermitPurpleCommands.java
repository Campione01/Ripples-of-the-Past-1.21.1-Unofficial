package rotp.addons.hermitpurple;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;

import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.addons.hermitpurple.network.HermitTargetPayloads;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class HermitPurpleCommands {
    private HermitPurpleCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                literal("hermit_target")
                        .then(mode(
                                "player",
                                HermitPurpleSpec
                                        .TARGET_MODE_PLAYER))
                        .then(mode(
                                "entity",
                                HermitPurpleSpec
                                        .TARGET_MODE_ENTITY))
                        .then(mode(
                                "stand_user",
                                HermitPurpleSpec
                                        .TARGET_MODE_STAND))
                        .then(mode(
                                "biomes",
                                HermitPurpleSpec
                                        .TARGET_MODE_BIOME))
                        .then(literal("random")
                                .executes(context -> select(
                                        context.getSource(),
                                        HermitPurpleSpec
                                                .TARGET_MODE_RANDOM,
                                        "random"))));
        event.getDispatcher().register(
                literal("hermit_structure")
                        .then(argument(
                                "target",
                                StringArgumentType.greedyString())
                                .executes(context -> select(
                                        context.getSource(),
                                        HermitPurpleSpec
                                                .TARGET_MODE_STRUCTURE,
                                        StringArgumentType.getString(
                                                context,
                                                "target")))));
    }

    private static com.mojang.brigadier.builder
            .LiteralArgumentBuilder<CommandSourceStack> mode(
                    String literalName, int mode) {
        return literal(literalName)
                .then(argument(
                        "target",
                        StringArgumentType.greedyString())
                        .executes(context -> select(
                                context.getSource(),
                                mode,
                                StringArgumentType.getString(
                                        context, "target"))));
    }

    private static int select(
            CommandSourceStack source, int mode, String target) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        HermitPurpleStandEntity controller =
                HermitPurpleAddon.controller(player);
        if (controller == null
                || !HermitTargetPayloads.valid(
                        player, mode, target)) {
            source.sendFailure(Component.translatable(
                    "rotp_zhp.command.invalid_target"));
            return 0;
        }
        controller.setTargetSelection(mode, target);
        source.sendSuccess(
                () -> Component.translatable(
                        "rotp_zhp.command.target_set",
                        target),
                false);
        return 1;
    }
}
