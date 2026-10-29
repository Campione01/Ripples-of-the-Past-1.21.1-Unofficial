package rotp.addons.sexpistols.content.sexpistols;

import rotp.core.powersystem.standpower.StandPower;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsEffects;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public final class SexPistolsDebugCommand {
    private SexPistolsDebugCommand() {}

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spdebug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("hungry")
                                .executes(context ->
                                        setHungry(
                                                context.getSource(),
                                                true)))
                        .then(Commands.literal("reset_hunger")
                                .executes(context ->
                                        setHungry(
                                                context.getSource(),
                                                false)))
                        .then(Commands.literal("joyful")
                                .then(Commands.argument(
                                                "level",
                                                IntegerArgumentType
                                                        .integer(1, 5))
                                        .executes(context ->
                                                joyful(
                                                        context.getSource(),
                                                        IntegerArgumentType
                                                                .getInteger(
                                                                        context,
                                                                        "level")))))
                        .then(Commands.literal("clear_joyful")
                                .executes(context ->
                                        clearJoyful(
                                                context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context ->
                                        status(
                                                context.getSource()))));
    }

    private static int setHungry(
            CommandSourceStack source, boolean hungry)
            throws com.mojang.brigadier.exceptions
                    .CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SexPistolsCoordinator coordinator = coordinator(player);
        if (coordinator == null) {
            return noStand(source);
        }
        coordinator.debugSetHungry(player, hungry);
        int members = coordinator
                .loadedMembers(player.serverLevel())
                .size();
        source.sendSuccess(
                () -> Component.literal(
                        "Sex Pistols debug: shared hungry="
                                + hungry
                                + ", active pistols="
                                + members
                                + ", hungerTicks="
                                + coordinator.hungerTicks()),
                true);
        return members;
    }

    private static int joyful(
            CommandSourceStack source, int level)
            throws com.mojang.brigadier.exceptions
                    .CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (coordinator(player) == null) {
            return noStand(source);
        }
        player.addEffect(new MobEffectInstance(
                SexPistolsEffects.JOYFUL,
                SexPistolsJoyful.DURATION_TICKS,
                level - 1,
                false,
                false,
                true));
        source.sendSuccess(
                () -> Component.literal(
                        "Sex Pistols debug: applied Joyful level "
                                + level),
                true);
        return level;
    }

    private static int clearJoyful(
            CommandSourceStack source)
            throws com.mojang.brigadier.exceptions
                    .CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.removeEffect(SexPistolsEffects.JOYFUL);
        source.sendSuccess(
                () -> Component.literal(
                        "Sex Pistols debug: cleared Joyful"),
                true);
        return 1;
    }

    private static int status(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions
                    .CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SexPistolsCoordinator coordinator = coordinator(player);
        if (coordinator == null) {
            return noStand(source);
        }
        var members = coordinator.loadedMembers(
                player.serverLevel());
        long eating = members.stream()
                .filter(member -> member.getEatingTicks() > 0)
                .count();
        source.sendSuccess(
                () -> Component.literal(
                        "Sex Pistols debug: sharedHungry="
                                + coordinator.isHungry()
                                + ", sharedHungerTicks="
                                + coordinator.hungerTicks()
                                + ", pistols="
                                + members.size()
                                + ", eating="
                                + eating
                                + ", selected="
                                + coordinator.selectedNumber()),
                false);
        return members.size();
    }

    private static SexPistolsCoordinator coordinator(
            ServerPlayer player) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.hasPower()
                && power.getPowerType()
                        == SexPistolsStands.SEX_PISTOLS.get()
                && player.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)
                ? SexPistolsAttachments.coordinator(player)
                : null;
    }

    private static int noStand(CommandSourceStack source) {
        source.sendFailure(Component.literal(
                "Sex Pistols debug: player has no Sex Pistols stand"));
        return 0;
    }
}
