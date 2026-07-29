package com.github.standobyte.jojoimpl.npc.rps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.effect.UserStandEffects;
import com.github.standobyte.jojoimpl.stands.boyiiman.BoyIIManStandPartTakenEffect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public class RockPaperScissorsGame {
    public static final int WINS_NEEDED = 3;

    public enum Pick {
        ROCK,
        PAPER,
        SCISSORS;

        public static Pick random(RandomSource random) {
            return values()[random.nextInt(values().length)];
        }

        public boolean beats(Pick opponentPick) {
            return switch (this) {
                case ROCK -> opponentPick == SCISSORS;
                case PAPER -> opponentPick == ROCK;
                case SCISSORS -> opponentPick == PAPER;
            };
        }

        public boolean ties(Pick opponentPick) {
            return this == opponentPick;
        }

        public ParticleOptions particle() {
            return switch (this) {
                case ROCK -> ModParticles.RPS_ROCK.get();
                case PAPER -> ModParticles.RPS_PAPER.get();
                case SCISSORS -> ModParticles.RPS_SCISSORS.get();
            };
        }
    }

    public enum Result {
        WIN,
        LOSE,
        DRAW
    }

    private final UUID player;
    private final UUID opponent;
    private final boolean opponentIsNpc;
    private Pick playerPick;
    private Pick opponentPick;
    private Pick opponentThoughtsPick;
    private boolean opponentCanReadThoughts;
    private final long sessionEpoch;
    private int cheatUsedRound;
    private boolean cheatedBefore;
    private int round = 1;
    private int playerWins = 0;
    private int opponentWins = 0;
    private final List<Pick> playerPreviousPicks = new ArrayList<>();
    private final List<Pick> opponentPreviousPicks = new ArrayList<>();

    public RockPaperScissorsGame(ServerPlayer player, UUID opponent, boolean opponentIsNpc) {
        this(player.getUUID(), opponent, opponentIsNpc, null, null, null,
                1, 0, 0, newSessionEpoch(), 0, false);
    }

    public RockPaperScissorsGame(UUID player, UUID opponent, boolean opponentIsNpc, Pick playerPick, Pick opponentPick, Pick opponentThoughtsPick, int round, int playerWins, int opponentWins) {
        this(player, opponent, opponentIsNpc, playerPick, opponentPick,
                opponentThoughtsPick, round, playerWins, opponentWins,
                newSessionEpoch(), 0, false);
    }

    private RockPaperScissorsGame(UUID player, UUID opponent, boolean opponentIsNpc,
            Pick playerPick, Pick opponentPick, Pick opponentThoughtsPick,
            int round, int playerWins, int opponentWins, long sessionEpoch,
            int cheatUsedRound, boolean cheatedBefore) {
        this.player = player;
        this.opponent = opponent;
        this.opponentIsNpc = opponentIsNpc;
        this.playerPick = playerPick;
        this.opponentPick = opponentPick;
        this.opponentThoughtsPick = opponentThoughtsPick;
        this.round = round;
        this.playerWins = playerWins;
        this.opponentWins = opponentWins;
        this.sessionEpoch = sessionEpoch != 0L
                ? sessionEpoch
                : newSessionEpoch();
        this.cheatUsedRound = cheatUsedRound;
        this.cheatedBefore = cheatedBefore;
    }

    private static long newSessionEpoch() {
        UUID random = UUID.randomUUID();
        long epoch = random.getMostSignificantBits()
                ^ random.getLeastSignificantBits();
        return epoch != 0L ? epoch : 1L;
    }

    public UUID player() {
        return player;
    }

    public UUID opponent() {
        return opponent;
    }

    public boolean opponentIsNpc() {
        return opponentIsNpc;
    }

    public int round() {
        return round;
    }

    public long sessionEpoch() {
        return sessionEpoch;
    }

    public boolean tryUseCheat(long requestedSessionEpoch) {
        if (requestedSessionEpoch != sessionEpoch
                || isMatchOver()
                || cheatUsedRound == round) {
            return false;
        }
        cheatUsedRound = round;
        return true;
    }

    /**
     * @return true only for the first accepted cheat in this match
     */
    public boolean markCheatedBefore() {
        boolean firstUse = !cheatedBefore;
        cheatedBefore = true;
        return firstUse;
    }

    public boolean hasCheatedBefore() {
        return cheatedBefore;
    }

    public int playerWins() {
        return playerWins;
    }

    public int opponentWins() {
        return opponentWins;
    }

    public List<Pick> playerPreviousPicks() {
        return Collections.unmodifiableList(playerPreviousPicks);
    }

    public List<Pick> opponentPreviousPicks() {
        return Collections.unmodifiableList(opponentPreviousPicks);
    }

    public boolean isMatchOver() {
        return playerWins >= WINS_NEEDED || opponentWins >= WINS_NEEDED;
    }

    public boolean playerWonMatch() {
        return playerWins >= WINS_NEEDED;
    }

    public boolean opponentWonMatch() {
        return opponentWins >= WINS_NEEDED;
    }

    public void submitPlayer(Pick pick) {
        this.playerPick = pick;
    }

    public void submitOpponent(Pick pick) {
        this.opponentPick = pick;
    }

    public void setOpponentThoughts(Pick pick) {
        this.opponentThoughtsPick = pick;
    }

    public void clearOpponentThoughts() {
        this.opponentThoughtsPick = null;
    }

    public void setOpponentCanReadThoughts(boolean canReadThoughts) {
        this.opponentCanReadThoughts = canReadThoughts;
        if (!canReadThoughts) {
            this.opponentThoughtsPick = null;
        }
    }

    public boolean opponentCanReadThoughts() {
        return opponentCanReadThoughts;
    }

    public Pick playerPick() {
        return playerPick;
    }

    public Pick opponentPick() {
        return opponentPick;
    }

    public Pick opponentThoughtsPick() {
        return opponentThoughtsPick;
    }

    public boolean isResolved() {
        return playerPick != null && opponentPick != null;
    }

    public Result resolve() {
        if (!isResolved()) {
            throw new IllegalStateException("RPS game is not resolved yet");
        }
        if (playerPick == opponentPick) {
            return Result.DRAW;
        }
        return switch (playerPick) {
            case ROCK -> opponentPick == Pick.SCISSORS ? Result.WIN : Result.LOSE;
            case PAPER -> opponentPick == Pick.ROCK ? Result.WIN : Result.LOSE;
            case SCISSORS -> opponentPick == Pick.PAPER ? Result.WIN : Result.LOSE;
        };
    }

    public Result advanceRoundAfterResolve() {
        Result result = resolve();
        if (result != Result.DRAW) {
            playerPreviousPicks.add(playerPick);
            opponentPreviousPicks.add(opponentPick);
        }
        switch (result) {
            case WIN -> playerWins++;
            case LOSE -> opponentWins++;
            case DRAW -> {}
        }
        if (!isMatchOver()) {
            round++;
            playerPick = null;
            opponentPick = null;
            opponentThoughtsPick = null;
            opponentCanReadThoughts = false;
        }
        return result;
    }

    public void applyBoyIIManRoundResult(ServerLevel level, LivingEntity roundWinner, LivingEntity roundLoser, Result result) {
        if (result == Result.DRAW || roundWinner == null || roundLoser == null) {
            return;
        }
        int winnerScore = roundWinner.getUUID().equals(player) ? playerWins : opponentWins;
        boyIIManRoundWon(roundWinner, roundLoser, winnerScore);
        returnPartsIfBoyIIManLostFinal(roundWinner, roundLoser, winnerScore);
    }

    private void boyIIManRoundWon(LivingEntity roundWinner, LivingEntity roundLoser, int winnerScore) {
        StandPower winnerStand = PowerClass.STAND.attachGet(roundWinner);
        StandPower loserStand = PowerClass.STAND.attachGet(roundLoser);
        if (winnerStand == null || loserStand == null) {
            return;
        }
        if (loserStand.hasPower() && winnerStand.getPowerType() == ModStands.BOY_II_MAN.get()) {
            if (winnerScore < WINS_NEEDED) {
                StandPart limbs = winnerScore == 1 ? StandPart.ARMS : winnerScore == 2 ? StandPart.LEGS : null;
                if (limbs != null) {
                    loserStand.getStandInstance().ifPresent(stand -> {
                        if (stand.hasPart(limbs) && stand.getStandType() != null) {
                            StandInstance takenParts = new StandInstance(stand.getStandType());
                            for (StandPart standPart : StandPart.values()) {
                                if (standPart != limbs) {
                                    takenParts.removePart(standPart);
                                }
                            }
                            stand.removePart(limbs);
                            winnerStand.userStandEffects.addEffect(new BoyIIManStandPartTakenEffect(takenParts).withTarget(roundLoser));
                        }
                    });
                }
            }
            else if (winnerScore == WINS_NEEDED) {
                StandInstance wholeStand = loserStand.getStandInstance().map(StandInstance::copy).orElse(null);
                if (wholeStand != null) {
                    loserStand.setStandInstance(Optional.empty());
                    winnerStand.userStandEffects.addEffect(new BoyIIManStandPartTakenEffect(wholeStand).withTarget(roundLoser));
                }
            }
        }
    }

    private void returnPartsIfBoyIIManLostFinal(LivingEntity roundWinner, LivingEntity roundLoser, int winnerScore) {
        if (winnerScore != WINS_NEEDED) {
            return;
        }
        StandPower winnerStand = PowerClass.STAND.attachGet(roundWinner);
        StandPower loserStand = PowerClass.STAND.attachGet(roundLoser);
        if (winnerStand == null || loserStand == null || loserStand.getPowerType() != ModStands.BOY_II_MAN.get()) {
            return;
        }

        UserStandEffects boyIIManEffects = loserStand.userStandEffects;
        List<BoyIIManStandPartTakenEffect> effectsToCheck = boyIIManEffects
                .getEffectsOfType(ModStandAbilities.EFFECT_BIIM_STAND_PART_TAKE.get())
                .filter(effect -> roundWinner.getUUID().equals(effect.getTargetUUID()))
                .toList();
        for (BoyIIManStandPartTakenEffect effect : effectsToCheck) {
            if (winnerStand.hasPower() && winnerStand.getStandInstance().isPresent()) {
                StandInstance winnerStandPartsLeft = winnerStand.getStandInstance().get();
                StandInstance partsTaken = effect.getPartsTaken();
                if (partsTaken != null && partsTaken.getStandId().equals(winnerStandPartsLeft.getStandId())) {
                    Set<StandPart> partsToReturn = partsTaken.getAllParts();
                    if (partsToReturn.stream().allMatch(part -> !winnerStandPartsLeft.hasPart(part))) {
                        boyIIManEffects.removeEffect(effect);
                        if (partsToReturn.contains(StandPart.ARMS)) {
                            roundWinner.removeEffect(MobEffects.WEAKNESS);
                            roundWinner.removeEffect(MobEffects.DIG_SLOWDOWN);
                        }
                        if (partsToReturn.contains(StandPart.LEGS)) {
                            roundWinner.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                        }
                    }
                }
            }
            else {
                boyIIManEffects.removeEffect(effect);
            }
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Player", player);
        tag.putUUID("Opponent", opponent);
        tag.putBoolean("OpponentIsNpc", opponentIsNpc);
        if (playerPick != null) {
            tag.putString("PlayerPick", playerPick.name());
        }
        if (opponentPick != null) {
            tag.putString("OpponentPick", opponentPick.name());
        }
        if (opponentThoughtsPick != null) {
            tag.putString("OpponentThoughtsPick", opponentThoughtsPick.name());
        }
        writePickList(tag, "PlayerPreviousPicks", playerPreviousPicks);
        writePickList(tag, "OpponentPreviousPicks", opponentPreviousPicks);
        tag.putInt("Round", round);
        tag.putInt("PlayerWins", playerWins);
        tag.putInt("OpponentWins", opponentWins);
        tag.putLong("SessionEpoch", sessionEpoch);
        tag.putInt("CheatUsedRound", cheatUsedRound);
        tag.putBoolean("CheatedBefore", cheatedBefore);
        return tag;
    }

    private static void writePickList(CompoundTag tag, String key, List<Pick> picks) {
        CompoundTag listTag = new CompoundTag();
        listTag.putInt("Size", picks.size());
        for (int i = 0; i < picks.size(); i++) {
            listTag.putString(String.valueOf(i), picks.get(i).name());
        }
        tag.put(key, listTag);
    }

    private static List<Pick> readPickList(CompoundTag tag, String key) {
        List<Pick> picks = new ArrayList<>();
        if (!tag.contains(key, 10)) {
            return picks;
        }
        CompoundTag listTag = tag.getCompound(key);
        int size = listTag.getInt("Size");
        for (int i = 0; i < size; i++) {
            if (listTag.contains(String.valueOf(i))) {
                picks.add(Pick.valueOf(listTag.getString(String.valueOf(i))));
            }
        }
        return picks;
    }

    public static RockPaperScissorsGame load(CompoundTag tag) {
        Pick playerPick = tag.contains("PlayerPick") ? Pick.valueOf(tag.getString("PlayerPick")) : null;
        Pick opponentPick = tag.contains("OpponentPick") ? Pick.valueOf(tag.getString("OpponentPick")) : null;
        Pick opponentThoughtsPick = tag.contains("OpponentThoughtsPick") ? Pick.valueOf(tag.getString("OpponentThoughtsPick")) : null;
        RockPaperScissorsGame game = new RockPaperScissorsGame(
                tag.getUUID("Player"),
                tag.getUUID("Opponent"),
                tag.getBoolean("OpponentIsNpc"),
                playerPick,
                opponentPick,
                opponentThoughtsPick,
                tag.getInt("Round"),
                tag.contains("PlayerWins") ? tag.getInt("PlayerWins") : 0,
                tag.contains("OpponentWins") ? tag.getInt("OpponentWins") : 0,
                tag.contains("SessionEpoch")
                        ? tag.getLong("SessionEpoch")
                        : newSessionEpoch(),
                tag.contains("CheatUsedRound")
                        ? tag.getInt("CheatUsedRound")
                        : 0,
                tag.getBoolean("CheatedBefore"));
        game.playerPreviousPicks.addAll(readPickList(tag, "PlayerPreviousPicks"));
        game.opponentPreviousPicks.addAll(readPickList(tag, "OpponentPreviousPicks"));
        return game;
    }

}
