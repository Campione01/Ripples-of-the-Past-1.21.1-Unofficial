package rotp.addons.echoes;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;

import rotp.addons.echoes.init.EchoesAttachments;
import rotp.addons.echoes.state.EchoesChatService;
import rotp.addons.echoes.state.EchoesGravityService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class EchoesCommands {
    // 092 ChatCommand: "/echo \u6e05\u9664\u51bb\u7ed3" releases stuck freezes
    public static final String CLEAR_FREEZE = "\u6e05\u9664\u51bb\u7ed3";
    // 092 cleared every freeze for anyone; clearing others' needs op 2
    public static final int CLEAR_ALL_PERMISSION = 2;

    private EchoesCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                literal("echo")
                        .then(argument(
                                "message",
                                StringArgumentType.greedyString())
                                .executes(context -> {
                                    if (!(context.getSource().getEntity()
                                            instanceof ServerPlayer player)) {
                                        context.getSource().sendFailure(
                                                Component.translatable(
                                                        "rotp_echoes.command"
                                                                + ".player_only"));
                                        return 0;
                                    }
                                    String message =
                                            StringArgumentType.getString(
                                                    context,
                                                    "message");
                                    // 092 checked this before the Stand
                                    if (message.trim()
                                            .toLowerCase(Locale.ROOT)
                                            .equals(CLEAR_FREEZE)) {
                                        boolean all = context.getSource()
                                                .hasPermission(
                                                        CLEAR_ALL_PERMISSION);
                                        int released =
                                                clearFreezes(player, all);
                                        player.displayClientMessage(
                                                Component.translatable(
                                                        all
                                                                ? "rotp_echoes.command"
                                                                        + ".clear_freeze.all"
                                                                : "rotp_echoes.command"
                                                                        + ".clear_freeze.own",
                                                        released)
                                                        .withStyle(
                                                                ChatFormatting.GREEN),
                                                false);
                                        return 1;
                                    }
                                    // 092 talked to ACT3 only
                                    if (!EchoesChatService.isAct3User(player)) {
                                        player.displayClientMessage(
                                                Component.translatable(
                                                        "rotp_echoes.command"
                                                                + ".requires_echoes"),
                                                false);
                                        return 0;
                                    }
                                    EchoesChatService.handleChat(
                                            player, message);
                                    return 1;
                                })));
    }

    // caller's own hand and target freezes; every one with op level 2
    public static int clearFreezes(ServerPlayer caller, boolean all) {
        List<LivingEntity> frozen = new ArrayList<>();
        for (ServerLevel level : caller.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living
                        && living.hasData(EchoesAttachments.FREEZE_STATE)
                        && RotpEchoes.freezeState(living).active()
                        && (all || caller.getUUID().equals(
                                RotpEchoes.freezeState(living).owner()))) {
                    frozen.add(living);
                }
            }
        }
        frozen.forEach(EchoesGravityService::releaseFrozenTarget);
        return frozen.size();
    }
}
