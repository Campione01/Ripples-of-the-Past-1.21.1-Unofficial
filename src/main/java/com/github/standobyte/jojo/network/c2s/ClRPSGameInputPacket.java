package com.github.standobyte.jojo.network.c2s;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.ServerSavedData;
import com.github.standobyte.jojo.api.rps.RpsCheatKind;
import com.github.standobyte.jojo.api.rps.RpsCheatRegistration;
import com.github.standobyte.jojo.api.rps.RpsCheatRegistrations;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.network.s2c.RPSGameStatePacket;
import com.github.standobyte.jojo.network.s2c.RPSOpponentPickThoughtsPacket;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsGame;
import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsGame.Pick;
import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsGame.Result;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClRPSGameInputPacket implements CustomPacketPayload {
    private static CustomPacketPayload.Type<ClRPSGameInputPacket> type;

    private final PacketType packetType;
    @Nullable private final Pick pick;
    @Nullable private final PowerClass<?> cheatPower;
    private final long cheatSessionEpoch;

    public static ClRPSGameInputPacket pick(Pick pick) {
        return new ClRPSGameInputPacket(
                PacketType.PICK, pick, null, 0L);
    }

    public static ClRPSGameInputPacket cheat(
            PowerClass<?> cheatPower, long sessionEpoch) {
        return new ClRPSGameInputPacket(
                PacketType.CHEAT, null, cheatPower, sessionEpoch);
    }

    public static ClRPSGameInputPacket quitGame() {
        return new ClRPSGameInputPacket(
                PacketType.QUIT, null, null, 0L);
    }

    private ClRPSGameInputPacket(PacketType packetType, @Nullable Pick pick,
            @Nullable PowerClass<?> cheatPower, long cheatSessionEpoch) {
        this.packetType = packetType;
        this.pick = pick;
        this.cheatPower = cheatPower;
        this.cheatSessionEpoch = cheatSessionEpoch;
    }

    public static class Handler implements PacketsRegister.PacketCodecHandler<ClRPSGameInputPacket> {
        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<ClRPSGameInputPacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ClRPSGameInputPacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, ClRPSGameInputPacket> STREAM_CODEC = StreamCodec.ofMember(
                ClRPSGameInputPacket::write, ClRPSGameInputPacket::new);

        @Override
        public void handle(ClRPSGameInputPacket payload, IPayloadContext context) {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            ServerSavedData data = ServerSavedData.get(serverPlayer.getServer());
            RockPaperScissorsGame game = data.rpsPvpGames.get(serverPlayer.getUUID());
            if (game == null
                    || !serverPlayer.getUUID().equals(game.player())) {
                return;
            }
            switch (payload.packetType) {
                case PICK -> handlePick(serverPlayer, data, game, payload.pick);
                case CHEAT -> handleCheat(serverPlayer, data, game,
                        payload.cheatPower,
                        payload.cheatSessionEpoch);
                case QUIT -> handleQuit(serverPlayer, data, game);
            }
        }
    }

    private static void handleCheat(ServerPlayer player, ServerSavedData data, RockPaperScissorsGame game,
            @Nullable PowerClass<?> cheatPower, long sessionEpoch) {
        if (cheatPower == PowerClass.STAND) {
            StandPower standPower = StandPower.get(player);
            RpsCheatRegistration registration =
                    RpsCheatRegistrations.find(standPower).orElse(null);
            if (registration == null
                    || registration.spec().kind()
                            != RpsCheatKind.MIND_READ) {
                return;
            }
            SoundEvent firstUseSound;
            try {
                firstUseSound =
                        registration.spec().firstUseSound().get();
            }
            catch (RuntimeException error) {
                JojoMod.getLogger().error(
                        "RPS cheat sound supplier {} failed.",
                        registration.owner(),
                        error);
                return;
            }
            if (firstUseSound == null) {
                JojoMod.getLogger().error(
                        "RPS cheat sound supplier {} returned null.",
                        registration.owner());
                return;
            }
            handleMindReadCheat(
                    player, data, game, sessionEpoch, firstUseSound);
            return;
        }

        if (cheatPower != PowerClass.PLAYER_POWER) {
            return;
        }
        PlayerPower playerPower = PlayerPower.get(player);
        if (playerPower == null || !playerPower.hasPower()) {
            return;
        }
        if (playerPower.getPowerType() == ModPlayerPowers.HAMON.get()) {
            handleMindReadCheat(player, data, game, sessionEpoch,
                    ModSoundEvents.HAMON_CONCENTRATION.get());
        }
        else if (playerPower.getPowerType() == ModPlayerPowers.VAMPIRISM.get()) {
            handleVampirismCheat(
                    player, data, game, sessionEpoch);
        }
    }

    private static void handleMindReadCheat(
            ServerPlayer player,
            ServerSavedData data,
            RockPaperScissorsGame game,
            long sessionEpoch,
            SoundEvent firstUseSound) {
        LivingEntity opponent = getOpponentEntity(player, game);
        if (opponent == null) {
            handleQuit(player, data, game);
            return;
        }

        ServerPlayer opponentPlayer = null;
        RockPaperScissorsGame opponentGame = null;
        if (!game.opponentIsNpc()) {
            if (!(opponent instanceof ServerPlayer serverOpponent)) {
                return;
            }
            opponentPlayer = serverOpponent;
            opponentGame =
                    data.rpsPvpGames.get(opponentPlayer.getUUID());
            if (!isReciprocalGame(
                    player, game, opponentPlayer, opponentGame)) {
                return;
            }
        }
        if (!game.tryUseCheat(sessionEpoch)) {
            return;
        }
        boolean firstUse = game.markCheatedBefore();

        Pick knownOpponentPick = game.opponentPick();
        if (knownOpponentPick == null && game.opponentIsNpc()) {
            knownOpponentPick = game.opponentThoughtsPick();
        }
        if (knownOpponentPick != null) {
            PacketDistributor.sendToPlayer(player, RPSGameStatePacket.setOpponentPick(knownOpponentPick, opponent.getId()));
            PacketDistributor.sendToPlayer(player, new RPSOpponentPickThoughtsPacket(true, knownOpponentPick));
        }

        if (opponentPlayer != null && opponentGame != null) {
            opponentGame.setOpponentCanReadThoughts(true);
            PacketDistributor.sendToPlayer(
                    opponentPlayer,
                    RPSGameStatePacket.mindRead(player.getId()));
        }

        if (firstUse) {
            player.serverLevel().playSound(
                    null, player, firstUseSound,
                    player.getSoundSource(), 1.0F, 1.0F);
        }
        data.setDirty();
    }

    private static void handleVampirismCheat(
            ServerPlayer player,
            ServerSavedData data,
            RockPaperScissorsGame game,
            long sessionEpoch) {
        LivingEntity opponent = getOpponentEntity(player, game);
        if (opponent == null) {
            handleQuit(player, data, game);
            return;
        }

        ServerPlayer opponentPlayer = null;
        RockPaperScissorsGame opponentGame = null;
        if (!game.opponentIsNpc() && opponent instanceof ServerPlayer serverOpponent) {
            opponentPlayer = serverOpponent;
            opponentGame = data.rpsPvpGames.get(opponentPlayer.getUUID());
            if (!isReciprocalGame(
                    player, game, opponentPlayer, opponentGame)) {
                return;
            }
        }

        if (!game.tryUseCheat(sessionEpoch)) {
            return;
        }
        boolean firstUse = game.markCheatedBefore();

        game.submitOpponent(Pick.ROCK);
        PacketDistributor.sendToPlayer(player,
                RPSGameStatePacket.setOpponentPick(
                        Pick.ROCK, opponent.getId()));
        if (opponentPlayer != null && opponentGame != null) {
            opponentGame.submitPlayer(Pick.ROCK);
            PacketDistributor.sendToPlayer(
                    opponentPlayer,
                    RPSGameStatePacket.setOwnPick(Pick.ROCK));
        }

        if (firstUse) {
            player.serverLevel().playSound(
                    null, player,
                    ModSoundEvents.VAMPIRE_EVIL_ATMOSPHERE.get(),
                    player.getSoundSource(), 1.0F, 1.0F);
        }
        if (game.isResolved()) {
            resolveRound(data, player, game, opponent, opponentPlayer, opponentGame);
        }
        data.setDirty();
    }

    private static boolean isReciprocalGame(
            ServerPlayer player,
            RockPaperScissorsGame game,
            ServerPlayer opponent,
            @Nullable RockPaperScissorsGame opponentGame) {
        return opponentGame != null
                && player.getUUID().equals(game.player())
                && opponent.getUUID().equals(game.opponent())
                && opponent.getUUID().equals(opponentGame.player())
                && player.getUUID().equals(opponentGame.opponent())
                && !opponentGame.opponentIsNpc();
    }

    private static void handlePick(ServerPlayer player, ServerSavedData data, RockPaperScissorsGame game, @Nullable Pick pick) {
        if (pick == null || game.playerPick() != null) {
            return;
        }
        game.submitPlayer(pick);
        PacketDistributor.sendToPlayer(player, RPSGameStatePacket.setOwnPick(pick));

        if (game.opponentIsNpc()) {
            Pick npcPick = game.opponentThoughtsPick() != null ? game.opponentThoughtsPick() : Pick.random(player.getRandom());
            game.submitOpponent(npcPick);
            LivingEntity npc = getOpponentEntity(player, game);
            PacketDistributor.sendToPlayer(player, RPSGameStatePacket.setOpponentPick(npcPick, npc != null ? npc.getId() : -1));
            if (npc != null) {
                resolveRound(data, player, game, npc, null, null);
            }
            else {
                data.rpsPvpGames.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, RPSGameStatePacket.leftGame());
            }
            data.setDirty();
            return;
        }

        ServerPlayer opponentPlayer = player.getServer().getPlayerList().getPlayer(game.opponent());
        if (opponentPlayer == null) {
            handleQuit(player, data, game);
            return;
        }
        RockPaperScissorsGame opponentGame = data.rpsPvpGames.get(opponentPlayer.getUUID());
        if (opponentGame == null) {
            handleQuit(player, data, game);
            return;
        }
        opponentGame.submitOpponent(pick);
        PacketDistributor.sendToPlayer(opponentPlayer, RPSGameStatePacket.setOpponentPick(pick, player.getId()));
        if (game.isResolved() && opponentGame.isResolved()) {
            resolveRound(data, player, game, opponentPlayer, opponentPlayer, opponentGame);
        }
        data.setDirty();
    }

    private static void resolveRound(ServerSavedData data, ServerPlayer player, RockPaperScissorsGame game, LivingEntity opponent,
            @Nullable ServerPlayer opponentPlayer, @Nullable RockPaperScissorsGame opponentGame) {
        Pick playerPick = game.playerPick();
        Pick opponentPick = game.opponentPick();
        Pick opponentPlayerPick = opponentGame != null ? opponentGame.playerPick() : null;
        Pick opponentOpponentPick = opponentGame != null ? opponentGame.opponentPick() : null;
        Result result = game.advanceRoundAfterResolve();
        Result opponentResult = null;
        if (opponentGame != null) {
            opponentResult = opponentGame.advanceRoundAfterResolve();
        }

        playRoundFeedback(player.serverLevel(), player, opponent, playerPick, opponentPick);

        LivingEntity roundWinner = null;
        LivingEntity roundLoser = null;
        if (result == Result.WIN) {
            roundWinner = player;
            roundLoser = opponent;
        }
        else if (result == Result.LOSE) {
            roundWinner = opponent;
            roundLoser = player;
        }
        game.applyBoyIIManRoundResult(player.serverLevel(), roundWinner, roundLoser, result);

        PacketDistributor.sendToPlayer(player, new RPSOpponentPickThoughtsPacket(false));
        PacketDistributor.sendToPlayer(player, RPSGameStatePacket.stateUpdated(game.playerPreviousPicks(), game.opponentPreviousPicks(), game.round()));
        player.sendSystemMessage(roundMessage(game, playerPick, opponentPick, result));
        if (opponentPlayer != null && opponentGame != null && opponentResult != null) {
            PacketDistributor.sendToPlayer(opponentPlayer, new RPSOpponentPickThoughtsPacket(false));
            PacketDistributor.sendToPlayer(opponentPlayer, RPSGameStatePacket.stateUpdated(
                    opponentGame.playerPreviousPicks(), opponentGame.opponentPreviousPicks(), opponentGame.round()));
            opponentPlayer.sendSystemMessage(roundMessage(opponentGame, opponentPlayerPick, opponentOpponentPick, opponentResult));
        }

        if (game.isMatchOver()) {
            finishGame(data, player, game);
            if (opponentPlayer != null && opponentGame != null) {
                finishGame(data, opponentPlayer, opponentGame);
            }
        }
    }

    private static void playRoundFeedback(ServerLevel level, LivingEntity player, LivingEntity opponent, Pick playerPick, Pick opponentPick) {
        if (playerPick != null) {
            level.sendParticles(playerPick.particle(), player.getX(), player.getY() + player.getBbHeight(), player.getZ(), 0, 0, 0, 0, 0);
        }
        if (opponentPick != null) {
            level.sendParticles(opponentPick.particle(), opponent.getX(), opponent.getY() + opponent.getBbHeight(), opponent.getZ(), 0, 0, 0, 0, 0);
        }
        double x = (player.getX() + opponent.getX()) * 0.5D;
        double y = (player.getY() + opponent.getY()) * 0.5D;
        double z = (player.getZ() + opponent.getZ()) * 0.5D;
        level.playSound(null, x, y, z, SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.AMBIENT, 1.0F, 2.0F);
    }

    private static Component roundMessage(RockPaperScissorsGame game, @Nullable Pick playerPick, @Nullable Pick opponentPick, Result result) {
        return Component.translatable("jojo.rps.round_result",
                Math.max(1, game.round() - 1),
                pickName(playerPick),
                pickName(opponentPick),
                resultName(result),
                game.playerWins(),
                game.opponentWins());
    }

    private static Component pickName(@Nullable Pick pick) {
        if (pick == null) {
            return Component.literal("?");
        }
        return switch (pick) {
            case ROCK -> Component.translatable("jojo.rps.rock");
            case PAPER -> Component.translatable("jojo.rps.paper");
            case SCISSORS -> Component.translatable("jojo.rps.scissors");
        };
    }

    private static Component resultName(Result result) {
        return switch (result) {
            case WIN -> Component.translatable("jojo.rps.result.win");
            case LOSE -> Component.translatable("jojo.rps.result.lose");
            case DRAW -> Component.translatable("jojo.rps.result.draw");
        };
    }

    private static void finishGame(ServerSavedData data, ServerPlayer player, RockPaperScissorsGame game) {
        boolean playerWon = game.playerWonMatch();
        PacketDistributor.sendToPlayer(player, RPSGameStatePacket.gameOver(playerWon));
        ModCriteriaTriggers.triggerRpsGame(player, playerWon, game.opponentWonMatch());
        data.rpsPvpGames.remove(player.getUUID());
    }

    private static void handleQuit(ServerPlayer player, ServerSavedData data, RockPaperScissorsGame game) {
        data.rpsPvpGames.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, RPSGameStatePacket.leftGame());
        if (!game.opponentIsNpc()) {
            ServerPlayer opponentPlayer = player.getServer().getPlayerList().getPlayer(game.opponent());
            if (opponentPlayer != null) {
                data.rpsPvpGames.remove(opponentPlayer.getUUID());
                PacketDistributor.sendToPlayer(opponentPlayer, RPSGameStatePacket.leftGame());
            }
        }
        data.setDirty();
    }

    @Nullable
    private static LivingEntity getOpponentEntity(ServerPlayer player, RockPaperScissorsGame game) {
        if (game.opponentIsNpc()) {
            if (player.serverLevel().getEntity(game.opponent()) instanceof LivingEntity living) {
                return living;
            }
            return null;
        }
        return player.getServer().getPlayerList().getPlayer(game.opponent());
    }

    public ClRPSGameInputPacket(RegistryFriendlyByteBuf buf) {
        this.packetType = buf.readEnum(PacketType.class);
        this.pick = packetType == PacketType.PICK ? NeoForgeStreamCodecs.enumCodec(Pick.class).decode(buf) : null;
        this.cheatPower = packetType == PacketType.CHEAT ? readPowerClass(buf) : null;
        this.cheatSessionEpoch =
                packetType == PacketType.CHEAT ? buf.readLong() : 0L;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(packetType);
        if (packetType == PacketType.PICK) {
            NeoForgeStreamCodecs.enumCodec(Pick.class).encode(buf, pick);
        }
        else if (packetType == PacketType.CHEAT) {
            buf.writeVarInt(cheatPower != null ? cheatPower.ordinal() : -1);
            buf.writeLong(cheatSessionEpoch);
        }
    }

    @Nullable
    private static PowerClass<?> readPowerClass(RegistryFriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        PowerClass<?>[] values = PowerClass.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }

    private enum PacketType {
        PICK,
        CHEAT,
        QUIT
    }
}
