package rotp.addons.waytoheaven;

import java.util.Collection;
import java.util.Collections;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;

import rotp.addons.waytoheaven.item.SinnerBoneItem;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class WayToHeavenCommands {
    private WayToHeavenCommands() {}

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("bone")
                        .requires(source ->
                                source.hasPermission(2))
                        .then(Commands.argument(
                                        "full",
                                        BoolArgumentType.bool())
                                .executes(context ->
                                        giveBone(
                                                context
                                                        .getSource(),
                                                Collections.singleton(
                                                        context
                                                                .getSource()
                                                                .getPlayerOrException()),
                                                BoolArgumentType
                                                        .getBool(
                                                                context,
                                                                "full"))))
                        .then(Commands.argument(
                                        "targets",
                                        EntityArgument.players())
                                .then(Commands.argument(
                                                "full",
                                                BoolArgumentType
                                                        .bool())
                                        .executes(context ->
                                                giveBone(
                                                        context
                                                                .getSource(),
                                                        EntityArgument
                                                                .getPlayers(
                                                                        context,
                                                                        "targets"),
                                                        BoolArgumentType
                                                                .getBool(
                                                                        context,
                                                                        "full"))))));

        dispatcher.register(
                Commands.literal("heaven")
                        .executes(context ->
                                sendHeavenInfo(
                                        Collections.singleton(
                                                context
                                                        .getSource()
                                                        .getPlayerOrException())))
                        .then(Commands.argument(
                                        "targets",
                                        EntityArgument.players())
                                .executes(context ->
                                        sendHeavenInfo(
                                                EntityArgument
                                                        .getPlayers(
                                                                context,
                                                                "targets")))));
    }

    private static int giveBone(
            CommandSourceStack source,
            Collection<ServerPlayer> targets,
            boolean full) {
        int sinners =
                full ? SinnerBoneItem.MAX_SINNERS : 1;
        for (ServerPlayer player : targets) {
            ItemStack stack =
                    new ItemStack(
                            WayToHeavenAddon.BONE.get());
            SinnerBoneItem.setSinners(stack, sinners);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.rotp_waytoheaven.bone.success",
                        targets.size(),
                        sinners),
                true);
        return targets.size();
    }

    private static int sendHeavenInfo(
            Collection<ServerPlayer> targets) {
        Component info = Component.translatable(
                        "heaven.chat.command.lib.info")
                .withStyle(
                        ChatFormatting.GRAY,
                        ChatFormatting.ITALIC);
        for (ServerPlayer player : targets) {
            player.sendSystemMessage(info);
        }
        return targets.size();
    }
}
