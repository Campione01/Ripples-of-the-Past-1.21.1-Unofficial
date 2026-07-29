package com.github.standobyte.jojo.client.ui.screen_jojomenu;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.github.standobyte.jojo.api.rps.RpsCheatRegistrations;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.network.c2s.ClRPSGameInputPacket;
import com.github.standobyte.jojo.network.c2s.ClRPSPickThoughtsPacket;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsGame.Pick;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class RockPaperScissorsScreen extends Screen {
    private static final ClientGameState STATE = new ClientGameState();
    private static boolean closingFromServer = false;

    private final List<Button> pickButtons = new ArrayList<>();
    private final Map<Pick, Button> pickButtonsByPick = new EnumMap<>(Pick.class);
    @Nullable private Button cheatButton;

    public RockPaperScissorsScreen() {
        super(Component.translatable("jojo.rps.title"));
    }

    public static void open(int opponentId, List<Pick> playerPicks,
            List<Pick> opponentPicks, int round, long sessionEpoch) {
        STATE.enter(opponentId, playerPicks, opponentPicks, round,
                sessionEpoch);
        ClientProxy.openScreen(new RockPaperScissorsScreen());
    }

    public static void open() {
        open(-1, List.of(), List.of(), 1, 0L);
    }

    public static void updateState(List<Pick> playerPicks, List<Pick> opponentPicks, int round) {
        STATE.update(playerPicks, opponentPicks, round);
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof RockPaperScissorsScreen)) {
            ClientProxy.openScreen(new RockPaperScissorsScreen());
        }
    }

    public static void setPick(boolean opponentPick, @Nullable Pick pick) {
        if (opponentPick) {
            STATE.opponentPick = pick;
        }
        else {
            STATE.playerPick = pick;
        }
    }

    public static void setMindRead(int opponentId) {
        if (STATE.opponentId == opponentId || STATE.opponentId < 0) {
            STATE.opponentCanReadThoughts = true;
            STATE.lastSentThoughtsPick = null;
        }
    }

    public static void applyOpponentThoughts(boolean visible, @Nullable Pick pick) {
        STATE.opponentThoughtsVisible = visible;
        STATE.opponentThoughtsPick = pick;
    }

    public static void gameOver(boolean playerWon) {
        STATE.active = false;
        STATE.playerWon = playerWon;
        ClientProxy.setOverlayMessage(Component.translatable(playerWon ? "jojo.rps.won" : "jojo.rps.lost"), false);
        closeFromServer();
    }

    public static void closeFromServer() {
        closingFromServer = true;
        try {
            if (Minecraft.getInstance().screen instanceof RockPaperScissorsScreen) {
                ClientProxy.openScreen(null);
            }
        }
        finally {
            closingFromServer = false;
        }
        STATE.reset();
    }

    @Override
    protected void init() {
        super.init();
        pickButtons.clear();
        pickButtonsByPick.clear();
        int centerX = width / 2;
        int y = height / 2 + 28;
        addPickButton(Pick.ROCK, centerX - 108, y, 64);
        addPickButton(Pick.PAPER, centerX - 32, y, 64);
        addPickButton(Pick.SCISSORS, centerX + 44, y, 76);
        cheatButton = Button.builder(Component.translatable("jojo.rps.cheat_button"), b -> sendCheat())
                .pos(centerX - 42, y + 28)
                .size(84, 20)
                .build();
        addRenderableWidget(cheatButton);
    }

    private void addPickButton(Pick pick, int x, int y, int width) {
        Button button = Button.builder(pickName(pick), b -> sendPick(pick)).pos(x, y).size(width, 20).build();
        pickButtons.add(button);
        pickButtonsByPick.put(pick, button);
        addRenderableWidget(button);
    }

    private void sendPick(Pick pick) {
        if (STATE.active && STATE.playerPick == null) {
            STATE.playerPick = pick;
            PacketDistributor.sendToServer(ClRPSGameInputPacket.pick(pick));
        }
    }

    private void sendCheat() {
        PowerClass<?> cheatPower = currentCheatPower();
        if (STATE.active && cheatPower != null && !STATE.cheatedThisRound) {
            STATE.cheatedThisRound = true;
            PacketDistributor.sendToServer(ClRPSGameInputPacket.cheat(
                    cheatPower, STATE.sessionEpoch));
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        updateButtonState();
        sendThoughtsIfNeeded(mouseX, mouseY);
        super.render(gui, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int y = height / 2 - 74;
        gui.drawCenteredString(font, title, centerX, y, 0xFFFFFF);
        gui.drawCenteredString(font, Component.translatable("jojo.rps.round", Math.max(1, STATE.round)), centerX, y + 16, 0xE0E0E0);
        gui.drawCenteredString(font, Component.literal(scoreText()), centerX, y + 30, 0xFFE0A0);

        int historyY = y + 48;
        int rows = Math.min(STATE.playerPicks.size(), STATE.opponentPicks.size());
        if (rows == 0) {
            gui.drawCenteredString(font, Component.translatable("jojo.rps.no_resolved_rounds"), centerX, historyY, 0x909090);
        }
        for (int i = 0; i < rows; i++) {
            Pick playerPick = STATE.playerPicks.get(i);
            Pick opponentPick = STATE.opponentPicks.get(i);
            String marker = playerPick.beats(opponentPick) ? ">" : opponentPick.beats(playerPick) ? "<" : "=";
            gui.drawCenteredString(font,
                    Component.literal((i + 1) + ". ").append(Component.translatable("jojo.rps.history",
                            pickName(playerPick), Component.literal(marker), pickName(opponentPick))),
                    centerX, historyY + i * 12, 0xD8D8D8);
        }

        int currentY = height / 2 + 4;
        gui.drawCenteredString(font,
                Component.translatable("jojo.rps.current_picks", pickName(STATE.playerPick), pickName(currentOpponentPick())),
                centerX, currentY, 0xFFFFFF);
        if (STATE.opponentThoughtsVisible && STATE.opponentThoughtsPick != null) {
            gui.drawCenteredString(font,
                    Component.translatable("jojo.rps.opponent_leaning", pickName(STATE.opponentThoughtsPick)),
                    centerX, currentY + 14, 0xFFFFAA00);
            gui.drawCenteredString(font, Component.translatable("jojo.rps.mind_read_available"), centerX, currentY + 26, 0xFFFFAA00);
        }
        else if (STATE.opponentCanReadThoughts) {
            gui.drawCenteredString(font, Component.translatable("jojo.rps.your_thoughts_visible"), centerX, currentY + 14, 0xFFFFAA00);
        }
    }

    private void updateButtonState() {
        boolean enabled = STATE.active && STATE.playerPick == null;
        for (Button button : pickButtons) {
            button.active = enabled;
        }
        if (cheatButton != null) {
            PowerClass<?> cheatPower = currentCheatPower();
            cheatButton.visible = STATE.active && cheatPower != null;
            cheatButton.active = cheatButton.visible && !STATE.cheatedThisRound;
        }
    }

    private void sendThoughtsIfNeeded(int mouseX, int mouseY) {
        if (!STATE.active || !STATE.opponentCanReadThoughts) {
            return;
        }
        Pick hoveredPick = hoveredPick(mouseX, mouseY);
        if (hoveredPick != STATE.lastSentThoughtsPick) {
            STATE.lastSentThoughtsPick = hoveredPick;
            PacketDistributor.sendToServer(
                    new ClRPSPickThoughtsPacket(
                            STATE.sessionEpoch, hoveredPick));
        }
    }

    @Nullable
    private Pick hoveredPick(int mouseX, int mouseY) {
        for (var entry : pickButtonsByPick.entrySet()) {
            if (entry.getValue().isMouseOver(mouseX, mouseY)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Nullable
    private PowerClass<?> currentCheatPower() {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        StandPower standPower = StandPower.get(minecraft.player);
        if (RpsCheatRegistrations.find(standPower).isPresent()) {
            return PowerClass.STAND;
        }
        PlayerPower playerPower = PlayerPower.get(minecraft.player);
        if (playerPower == null || !playerPower.hasPower()) {
            return null;
        }
        if (playerPower.getPowerType() == ModPlayerPowers.HAMON.get()
                || playerPower.getPowerType() == ModPlayerPowers.VAMPIRISM.get()) {
            return PowerClass.PLAYER_POWER;
        }
        return null;
    }

    @Nullable
    private Pick currentOpponentPick() {
        return STATE.opponentPick != null ? STATE.opponentPick : STATE.opponentThoughtsPick;
    }

    private String scoreText() {
        int playerWins = 0;
        int opponentWins = 0;
        int rows = Math.min(STATE.playerPicks.size(), STATE.opponentPicks.size());
        for (int i = 0; i < rows; i++) {
            Pick playerPick = STATE.playerPicks.get(i);
            Pick opponentPick = STATE.opponentPicks.get(i);
            if (playerPick.beats(opponentPick)) {
                playerWins++;
            }
            else if (opponentPick.beats(playerPick)) {
                opponentWins++;
            }
        }
        return playerWins + " - " + opponentWins;
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && STATE.active) {
            PacketDistributor.sendToServer(ClRPSGameInputPacket.quitGame());
            STATE.active = false;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!closingFromServer && STATE.active) {
            PacketDistributor.sendToServer(ClRPSGameInputPacket.quitGame());
        }
        STATE.reset();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class ClientGameState {
        private boolean active = false;
        private int opponentId = -1;
        private long sessionEpoch;
        private int round = 1;
        private List<Pick> playerPicks = List.of();
        private List<Pick> opponentPicks = List.of();
        @Nullable private Pick playerPick;
        @Nullable private Pick opponentPick;
        private boolean opponentCanReadThoughts = false;
        private boolean opponentThoughtsVisible = false;
        @Nullable private Pick opponentThoughtsPick;
        @Nullable private Boolean playerWon;
        private boolean cheatedThisRound = false;
        @Nullable private Pick lastSentThoughtsPick;

        private void enter(int opponentId, List<Pick> playerPicks,
                List<Pick> opponentPicks, int round, long sessionEpoch) {
            this.active = true;
            this.opponentId = opponentId;
            this.sessionEpoch = sessionEpoch;
            update(playerPicks, opponentPicks, round);
            this.playerWon = null;
        }

        private void update(List<Pick> playerPicks, List<Pick> opponentPicks, int round) {
            this.active = true;
            this.round = round;
            this.playerPicks = List.copyOf(playerPicks);
            this.opponentPicks = List.copyOf(opponentPicks);
            this.playerPick = null;
            this.opponentPick = null;
            this.opponentCanReadThoughts = false;
            this.opponentThoughtsVisible = false;
            this.opponentThoughtsPick = null;
            this.cheatedThisRound = false;
            this.lastSentThoughtsPick = null;
        }

        private void reset() {
            this.active = false;
            this.opponentId = -1;
            this.sessionEpoch = 0L;
            this.round = 1;
            this.playerPicks = List.of();
            this.opponentPicks = List.of();
            this.playerPick = null;
            this.opponentPick = null;
            this.opponentCanReadThoughts = false;
            this.opponentThoughtsVisible = false;
            this.opponentThoughtsPick = null;
            this.playerWon = null;
            this.cheatedThisRound = false;
            this.lastSentThoughtsPick = null;
        }
    }
}
