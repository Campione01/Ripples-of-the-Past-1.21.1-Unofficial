package rotp.addons.echoes.state;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.init.EchoesSounds;
import rotp.addons.echoes.init.EchoesStands;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

public final class EchoesChatService {
    private static final int ACTIVE_TALK_COOLDOWN_TICKS = 1200;

    private static final String[] COUNTER_LINES = {
        "\u627e\u6b7b\uff1f",
        "\u5567\u3002",
        "\u522b\u60f9\u6211\u3002",
        "\u4f60\u5b8c\u4e86\u3002",
        "...\u54fc\u3002",
        "\u81ea\u627e\u7684\u3002"
    };
    private static final String[] SHIT_PHRASES = {
        "S.H.I.T.",
        "S.H.I.T.!",
        "S.H.I.T.!!",
        "S.H.I.T!!!",
        "S.H.I.T. \u6765\u4e86\uff01",
        "S.H.I.T. \u5e72\u6d3b\uff01",
        "S.H.I.T. \u51bb\u7ed3\uff01",
        "S.H.I.T. \u9644\u9b54\uff01",
        "S.H.I.T. \u8d70\u4f60\uff01",
        "S.H.I.T. \u4e0a\uff01",
        "S.H.I.T. \u7ed9\u6211\u52a8\uff01",
        "S.H.I.T. \u522b\u52a8\uff01"
    };
    private static final String[] ACTIVE_TALKS = {
        "\u6709\u60c5\u51b5\u3002",
        "\u5567\u3002",
        "\u65e0\u804a\u3002",
        "\u54fc\u3002",
        "\u70e6\u3002",
        "...",
        "\u6ca1\u610f\u601d\u3002",
        "\u56f0\u4e86\u3002",
        "...\u54fc\u3002",
        "\u5567\uff0c\u53c8\u6765\u4e86\u3002",
        "...\u55ef\uff1f",
        "\u6ca1\u5174\u8da3\u3002"
    };

    // 092 kept nicknames for the server session (survive death)
    private static final Map<UUID, String> NICKNAMES =
            new ConcurrentHashMap<>();

    private EchoesChatService() {}

    public static void playSkillSound(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        long gameTime = player.level().getGameTime();
        if (!state.skillSoundReady(gameTime)) {
            return;
        }
        player.serverLevel().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getRandom().nextBoolean()
                        ? EchoesSounds.SKILL_USE_SOUND.get()
                        : EchoesSounds.SKILL_USE_SOUND_2.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        if (player.getRandom().nextFloat() < 0.3F) {
            player.displayClientMessage(
                    Component.literal(
                            "\u00a77"
                                    + random(
                                            player,
                                            SHIT_PHRASES)),
                    true);
        }
        state.resetSkillSoundCooldown(gameTime);
    }

    public static void tickPassive(ServerPlayer player) {
        if (player.getAbilities().instabuild
                || !RotpEchoes.isEchoesUser(player)) {
            return;
        }
        EchoesUserState state = RotpEchoes.userState(player);
        long gameTime = player.level().getGameTime();
        long lastTalk = state.lastActiveTalk();
        if (lastTalk != Long.MIN_VALUE
                && gameTime - lastTalk < ACTIVE_TALK_COOLDOWN_TICKS) {
            return;
        }
        if (player.getRandom().nextFloat() < 0.001F) {
            sendEchoLine(player, random(player, ACTIVE_TALKS), false);
            state.setLastActiveTalk(gameTime);
        }
    }

    public static void showCounterLine(ServerPlayer player) {
        sendEchoLine(player, random(player, COUNTER_LINES), false);
    }

    public static boolean isAct3User(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.getPowerType() == EchoesStands.ECHOES_ACT3.get();
    }

    // 092 EchoAIHandler.handleChat: ACT3 only, 5-tick cooldown
    public static boolean handleChat(
            ServerPlayer player, String message) {
        if (message == null
                || message.isBlank()
                || !isAct3User(player)) {
            return false;
        }
        EchoesUserState state = RotpEchoes.userState(player);
        long gameTime = player.level().getGameTime();
        long lastCommand = state.lastCommandTime();
        if (lastCommand != Long.MIN_VALUE
                && gameTime >= lastCommand
                && gameTime - lastCommand
                        < EchoesChatReplies.COOLDOWN_TICKS) {
            return false;
        }
        IntUnaryOperator random =
                bound -> player.getRandom().nextInt(bound);
        // 092 read the client language; the server gets it from options
        String lang = EchoesChatReplies.language(
                player.clientInformation().language());
        String name = NICKNAMES.getOrDefault(
                player.getUUID(), player.getName().getString());
        EchoesChatReplies.Reply reply =
                EchoesChatReplies.reply(message, lang, name, random);
        if (reply.nickname() != null) {
            NICKNAMES.put(player.getUUID(), reply.nickname());
        }
        sendEchoLine(
                player,
                EchoesChatReplies.withSuffix(reply.text(), random),
                false);
        state.setLastCommandTime(gameTime);
        return true;
    }

    private static void sendEchoLine(
            ServerPlayer player, String line, boolean actionBar) {
        player.displayClientMessage(
                Component.literal(
                        "\u00a77\u56de\u97f3ACT3\u00a78: \u00a7f"
                                + line),
                actionBar);
    }

    private static String random(
            ServerPlayer player, String[] choices) {
        return choices[player.getRandom().nextInt(choices.length)];
    }
}
