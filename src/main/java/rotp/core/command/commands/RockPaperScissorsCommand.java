package rotp.core.command.commands;

import java.util.List;
import java.util.UUID;

import rotp.core.ServerSavedData;
import rotp.core.network.s2c.RPSGameStatePacket;
import rotp.core.impl.npc.rps.RockPaperScissorsGame;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class RockPaperScissorsCommand {
    private static final String LITERAL = "rockpaperscissors";
    private static final String SHORT_LITERAL = "rps";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(LITERAL)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))));
        dispatcher.register(Commands.literal(SHORT_LITERAL)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))));
        JojoCommandsCommand.addCommand(LITERAL);
        JojoCommandsCommand.addCommand(SHORT_LITERAL);
    }

    private static int execute(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player == target) {
            source.sendFailure(Component.translatable("jojo.rps.self"));
            return 0;
        }
        if (player.distanceToSqr(target) >= 16.0D) {
            source.sendFailure(Component.translatable("jojo.rps.too_far", target.getDisplayName()));
            return 0;
        }

        ServerSavedData data = ServerSavedData.get(player.getServer());
        if (!data.rpsPvpGames.consumeInvite(player, target)) {
            data.rpsPvpGames.invite(player, target);
            sendInvite(source, player, target);
            return 1;
        }
        startGame(data, player, target);
        data.setDirty();
        return 1;
    }

    private static void sendInvite(CommandSourceStack source, ServerPlayer player, ServerPlayer target) {
        String command = "/" + LITERAL + " " + player.getGameProfile().getName();
        MutableComponent commandComponent = Component.literal(command).withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
        target.sendSystemMessage(Component.translatable("jojo.rps.game_invite.text",
                player.getDisplayName(), commandComponent));
        source.sendSuccess(() -> Component.translatable("jojo.rps.game_invite.sent", target.getDisplayName()), false);
    }

    /** Starts (or resumes a paused) match for an accepted invite; public for gametests. */
    public static void startGame(ServerSavedData data, ServerPlayer player, ServerPlayer target) {
        endCurrentGame(data, player, target);
        endCurrentGame(data, target, player);
        data.rpsPvpGames.startPvp(player, target);
        RockPaperScissorsGame playerGame =
                data.rpsPvpGames.get(player.getUUID());
        RockPaperScissorsGame targetGame =
                data.rpsPvpGames.get(target.getUUID());
        PacketDistributor.sendToPlayer(player,
                RPSGameStatePacket.enteredGame(
                        target.getId(), List.copyOf(playerGame.playerPreviousPicks()),
                        List.copyOf(playerGame.opponentPreviousPicks()), playerGame.round(),
                        playerGame.sessionEpoch()));
        PacketDistributor.sendToPlayer(target,
                RPSGameStatePacket.enteredGame(
                        player.getId(), List.copyOf(targetGame.playerPreviousPicks()),
                        List.copyOf(targetGame.opponentPreviousPicks()), targetGame.round(),
                        targetGame.sessionEpoch()));
    }

    // a game left open by death or relog must not stay linked to its old opponent
    private static void endCurrentGame(ServerSavedData data, ServerPlayer player, ServerPlayer newOpponent) {
        UUID oldOpponent = data.rpsPvpGames.leave(player.getUUID());
        if (oldOpponent != null && !oldOpponent.equals(newOpponent.getUUID())) {
            ServerPlayer oldOpponentPlayer = player.getServer().getPlayerList().getPlayer(oldOpponent);
            if (oldOpponentPlayer != null) {
                PacketDistributor.sendToPlayer(oldOpponentPlayer, RPSGameStatePacket.leftGame());
            }
        }
    }
}
