package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.util.List;
import java.util.Set;

import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.PaperButton;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.PlaceholderScreen;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.Tab;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.TabCategory;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonAbandonButtonPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonMeditationPacket;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.network.PacketDistributor;

public class HamonStatsScreen extends PlaceholderScreen {
    private static final ResourceLocation HAMON_WINDOW = JojoMod.resLoc("textures/gui/hamon_window.png");
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int DIM_TEXT_COLOR = 0xFFA7A7A7;
    private static final int WARNING_COLOR = 0xFFFF6B6B;
    private static final int STAT_BAR_X = 160;
    private static final int STRENGTH_Y = 35;
    private static final int CONTROL_Y = 50;
    private static final int BREATHING_Y = 65;
    private static final int EXERCISE_LEFT_X = 18;
    private static final int EXERCISE_RIGHT_X = 116;
    private static final int EXERCISE_FIRST_Y = 88;
    private static final int EXERCISE_SECOND_Y = 111;
    private static final int EXERCISES_TOTAL_X = 18;
    private static final int EXERCISES_TOTAL_Y = 136;

    private Button meditationButton;
    private Button abandonButton;
    private boolean confirmAbandon;

    public HamonStatsScreen(Component title, TabCategory category, Tab tab) {
        super(title, category, tab, HAMON_WINDOW);
    }

    @Override
    protected void init() {
        int x = getWindowX(this);
        int y = getWindowY(this);
        meditationButton = addRenderableWidget(new PaperButton(x + 16, y + 199, 108, 20,
                Component.translatable("hamon.meditation_button"),
                button -> PacketDistributor.sendToServer(new ClHamonMeditationPacket())));
        meditationButton.setTooltip(Tooltip.create(Component.translatable("hamon.meditation_button.stability_hint")));
        abandonButton = addRenderableWidget(new PaperButton(x + 132, y + 199, 82, 20,
                Component.translatable("hamon.abandon.tab"),
                button -> {
                    if (confirmAbandon) {
                        PacketDistributor.sendToServer(new ClHamonAbandonButtonPacket());
                        if (minecraft != null) {
                            minecraft.setScreen(null);
                        }
                    }
                    else {
                        confirmAbandon = true;
                    }
                }));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) {
            return;
        }
        HamonData data = PlayerPower.getPowerData(localPlayer, ModPlayerPowers.HAMON).orElse(null);

        int windowX = getWindowX(this);
        int windowY = getWindowY(this);
        int x = windowX + 16;

        gui.drawString(font, Component.translatable("hamon.stats.tab"), x, windowY + 21, TEXT_COLOR, false);
        if (data == null) {
            setButtonsVisible(false);
            gui.drawString(font, Component.translatable("jojo_ripples.hamon.stats.unavailable"), x, windowY + 45, DIM_TEXT_COLOR, false);
            return;
        }
        setButtonsVisible(true);
        updateButtons(data);
        renderStats(gui, data, windowX, windowY);
        renderExercises(gui, data, localPlayer, windowX, windowY);
        if (confirmAbandon) {
            renderAbandonWarning(gui, x, windowY + 149);
        }
        else {
            renderPrivateState(gui, data, windowX, windowY);
        }
        renderHamonTooltips(gui, data, localPlayer, mouseX, mouseY, windowX, windowY);
        renderTabTooltip(gui, this, mouseX, mouseY);
    }

    private void renderStats(GuiGraphics gui, HamonData data, int windowX, int windowY) {
        int strengthLevel = data.getHamonStrengthLevel();
        int controlLevel = data.getHamonControlLevel();
        float breathingLevel = data.getBreathingLevel();

        gui.drawString(font, Component.translatable("hamon.strength_level", strengthLevel, HamonData.MAX_STAT_LEVEL),
                windowX + 13, windowY + STRENGTH_Y, TEXT_COLOR, false);
        gui.drawString(font, Component.translatable("hamon.control_level", controlLevel, HamonData.MAX_STAT_LEVEL),
                windowX + 13, windowY + CONTROL_Y, TEXT_COLOR, false);
        gui.drawString(font, Component.translatable("hamon.breathing_level", (int) breathingLevel, (int) HamonData.MAX_BREATHING_LEVEL),
                windowX + 13, windowY + BREATHING_Y, TEXT_COLOR, false);

        drawStatBar(gui, windowX + STAT_BAR_X, windowY + STRENGTH_Y,
                203, 234, statProgress(data.getHamonStrengthPoints(), strengthLevel));
        drawStatBar(gui, windowX + STAT_BAR_X, windowY + CONTROL_Y,
                203, 239, statProgress(data.getHamonControlPoints(), controlLevel));
        boolean fullTraining = breathingLevel >= HamonData.MAX_BREATHING_LEVEL;
        float breathingProgress = fullTraining ? 1.0F : breathingLevel - (int) breathingLevel;
        drawStatBar(gui, windowX + STAT_BAR_X, windowY + BREATHING_Y,
                203, fullTraining ? 249 : 244, breathingProgress);
    }

    private void renderExercises(GuiGraphics gui, HamonData data, Player player, int windowX, int windowY) {
        gui.drawCenteredString(font, HamonData.Exercise.MINING.getName(), windowX + 64, windowY + 78, TEXT_COLOR);
        gui.drawCenteredString(font, HamonData.Exercise.RUNNING.getName(), windowX + 162, windowY + 78, TEXT_COLOR);
        gui.drawCenteredString(font, HamonData.Exercise.SWIMMING.getName(), windowX + 64, windowY + 101, TEXT_COLOR);
        gui.drawCenteredString(font, HamonData.Exercise.MEDITATION.getName(), windowX + 162, windowY + 101, TEXT_COLOR);

        drawExerciseBar(gui, windowX + EXERCISE_LEFT_X, windowY + EXERCISE_FIRST_Y, data, HamonData.Exercise.MINING);
        drawExerciseBar(gui, windowX + EXERCISE_RIGHT_X, windowY + EXERCISE_FIRST_Y, data, HamonData.Exercise.RUNNING);
        drawExerciseBar(gui, windowX + EXERCISE_LEFT_X, windowY + EXERCISE_SECOND_Y, data, HamonData.Exercise.SWIMMING);
        drawExerciseBar(gui, windowX + EXERCISE_RIGHT_X, windowY + EXERCISE_SECOND_Y, data, HamonData.Exercise.MEDITATION);

        gui.drawString(font, Component.translatable("hamon.exercise.all.count", data.getCompleteExercisesCount()),
                windowX + 16, windowY + 125, TEXT_COLOR, false);
        drawExercisesTotal(gui, windowX + EXERCISES_TOTAL_X, windowY + EXERCISES_TOTAL_Y, data, player);
    }

    private void renderPrivateState(GuiGraphics gui, HamonData data, int windowX, int windowY) {
        int x = windowX + 16;
        gui.drawString(font, Component.translatable("jojo_ripples.hamon.stats.stability",
                String.format("%.2f", data.getBreathStability())), x, windowY + 149, DIM_TEXT_COLOR, false);
        gui.drawString(font, Component.translatable("jojo_ripples.hamon.stats.training", data.getTrainingTicks()),
                x, windowY + 160, DIM_TEXT_COLOR, false);

        String techniqueName = data.getCharacterTechniqueName();
        Component techniqueText = techniqueName.isEmpty()
                ? Component.literal("-")
                : Component.translatable("hamon.technique." + techniqueName);
        gui.drawString(font, Component.translatable("hamon.techniques.tab").append(": ").append(techniqueText),
                x, windowY + 171, DIM_TEXT_COLOR, false);

        Set<String> teacherSkills = data.getTeacherSkills();
        Component teacherText = teacherSkills != null
                ? Component.translatable("jojo_ripples.hamon.teacher.nearby", teacherSkills.size())
                : Component.translatable("jojo_ripples.hamon.teacher.none");
        gui.drawString(font, teacherText, x, windowY + 182, DIM_TEXT_COLOR, false);
    }

    private void renderAbandonWarning(GuiGraphics gui, int x, int y) {
        List<FormattedCharSequence> lines = font.split(Component.translatable("hamon.abandon.tab.desc"), 196);
        for (int i = 0; i < Math.min(lines.size(), 3); i++) {
            gui.drawString(font, lines.get(i), x, y + i * 10, WARNING_COLOR, false);
        }
        gui.drawString(font, Component.translatable("hamon.abandon.tab.desc2"), x, y + 31, WARNING_COLOR, false);
    }

    private void renderHamonTooltips(GuiGraphics gui, HamonData data, Player player,
            int mouseX, int mouseY, int windowX, int windowY) {
        if (isInside(mouseX, mouseY, windowX + 12, windowY + STRENGTH_Y - 1, 201, 10)) {
            gui.renderComponentTooltip(font, List.of(Component.translatable("hamon.strength_stat.desc")), mouseX, mouseY);
            return;
        }
        if (isInside(mouseX, mouseY, windowX + 12, windowY + CONTROL_Y - 1, 201, 10)) {
            gui.renderComponentTooltip(font, List.of(Component.translatable("hamon.control_stat.desc")), mouseX, mouseY);
            return;
        }
        if (isInside(mouseX, mouseY, windowX + 12, windowY + BREATHING_Y - 1, 201, 10)) {
            gui.renderComponentTooltip(font, List.of(Component.translatable("hamon.breathing_stat.desc")), mouseX, mouseY);
            return;
        }

        HamonData.Exercise hovered = null;
        if (isInside(mouseX, mouseY, windowX + EXERCISE_LEFT_X - 3, windowY + EXERCISE_FIRST_Y - 1, 96, 9)) {
            hovered = HamonData.Exercise.MINING;
        }
        else if (isInside(mouseX, mouseY, windowX + EXERCISE_RIGHT_X - 3, windowY + EXERCISE_FIRST_Y - 1, 96, 9)) {
            hovered = HamonData.Exercise.RUNNING;
        }
        else if (isInside(mouseX, mouseY, windowX + EXERCISE_LEFT_X - 3, windowY + EXERCISE_SECOND_Y - 1, 96, 9)) {
            hovered = HamonData.Exercise.SWIMMING;
        }
        else if (isInside(mouseX, mouseY, windowX + EXERCISE_RIGHT_X - 3, windowY + EXERCISE_SECOND_Y - 1, 96, 9)) {
            hovered = HamonData.Exercise.MEDITATION;
        }
        if (hovered != null) {
            int ticks = data.getExerciseTicks(hovered);
            int max = hovered.getMaxTicks(data);
            gui.renderComponentTooltip(font, List.of(hovered.getName(), Component.literal(ticks + "/" + max)), mouseX, mouseY);
            return;
        }
        if (isInside(mouseX, mouseY, windowX + EXERCISES_TOTAL_X, windowY + EXERCISES_TOTAL_Y, 194, 8)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable("hamon.exercise.all.count", data.getCompleteExercisesCount()),
                    Component.translatable("hamon.breathing_stat.desc2",
                            breathControlMaskHoverable(player))), mouseX, mouseY);
        }
    }

    private static Component breathControlMaskHoverable(Player player) {
        ItemStack item = new ItemStack(ModItems.BREATH_CONTROL_MASK.get());
        item.enchant(player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.BINDING_CURSE), 1);
        return Component.translatable("hamon.breathing_stat.desc2.mask")
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(item))));
    }

    private void drawStatBar(GuiGraphics gui, int x, int y, int fillU, int fillV, float progress) {
        int fillWidth = Math.round(50.0F * clamp01(progress));
        if (fillWidth > 0) {
            blit(gui, x, y + 1, fillWidth, 5, fillU, fillV, fillWidth, 5, BlitFloat.NO_TINT);
        }
        blit(gui, x - 1, y, 52, 7, 202, 227, 52, 7, BlitFloat.NO_TINT);
    }

    private void drawExerciseBar(GuiGraphics gui, int x, int y, HamonData data, HamonData.Exercise exercise) {
        int ticks = data.getExerciseTicks(exercise);
        int maxTicks = Math.max(exercise.getMaxTicks(data), 1);
        int fillWidth = Math.min(90, 90 * ticks / maxTicks);
        if (fillWidth > 0) {
            blit(gui, x + 1, y + 1, fillWidth, 5, 93, 250, fillWidth, 5, BlitFloat.NO_TINT);
        }
        blit(gui, x, y, 92, 7, 0, 249, 92, 7, BlitFloat.NO_TINT);
        blit(gui, x - 3, y - 1, 8, 8, 230, 92 + exercise.ordinal() * 16, 16, 16, BlitFloat.NO_TINT);
        blit(gui, x + 85, y - 1, 8, 8, 230, 188, 16, 16,
                ticks >= maxTicks ? BlitFloat.NO_TINT : 0xFF000000);
    }

    private void drawExercisesTotal(GuiGraphics gui, int x, int y, HamonData data, Player player) {
        int complete = Math.min(data.getCompleteExercisesCount(), HamonData.MAX_EXERCISES_NEEDED);
        int completeWidth = Math.min(192, 48 * complete);
        int partialWidth = Math.min(192 - completeWidth, Math.round(48.0F * clamp01(data.getMaxIncompleteExercise())));
        if (completeWidth > 0) {
            blit(gui, x + 1, y + 1, completeWidth, 5, 1, 234, completeWidth, 5, BlitFloat.NO_TINT);
        }
        if (partialWidth > 0) {
            blit(gui, x + 1 + completeWidth, y + 1, partialWidth, 5,
                    1 + completeWidth, 239, partialWidth, 5, BlitFloat.NO_TINT);
        }
        int emptyWidth = 192 - completeWidth - partialWidth;
        if (emptyWidth > 0) {
            blit(gui, x + 1 + completeWidth + partialWidth, y + 1, emptyWidth, 5,
                    1 + completeWidth + partialWidth, 244, emptyWidth, 5, BlitFloat.NO_TINT);
        }
        blit(gui, x, y, 194, 7, 0, 227, 194, 7, BlitFloat.NO_TINT);
        if (data.getTrainingBonus(false) > 0.0F) {
            int bonusU = data.getBreathingIncrease(player, false) > 0.0F ? 230 : 239;
            blit(gui, x - 9, y - 1, 8, 8, bonusU, 216, 8, 8, BlitFloat.NO_TINT);
        }
        blit(gui, x + 186, y - 1, 8, 8, 230, 188, 16, 16,
                data.has4ExercisesBonus() ? BlitFloat.NO_TINT : 0xFF000000);
    }

    private void blit(GuiGraphics gui, int x, int y, int width, int height,
            int u, int v, int uWidth, int vHeight, int tint) {
        BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_WINDOW,
                x, y, width, height, 1,
                u, v, uWidth, vHeight, 256, 256, tint);
    }

    private static float statProgress(int points, int level) {
        if (level >= HamonData.MAX_STAT_LEVEL) {
            return 1.0F;
        }
        int pointsAtLevel = HamonData.pointsAtLevel(level);
        int pointsAtNextLevel = HamonData.pointsAtLevel(level + 1);
        return clamp01((float) (points - pointsAtLevel) / Math.max(pointsAtNextLevel - pointsAtLevel, 1));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(value, 1.0F));
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void setButtonsVisible(boolean visible) {
        meditationButton.visible = visible;
        abandonButton.visible = visible;
    }

    private void updateButtons(HamonData data) {
        meditationButton.setMessage(Component.translatable(data.isMeditating()
                ? "jojo_ripples.hamon.meditation.stop"
                : "hamon.meditation_button"));
        abandonButton.setMessage(Component.translatable(confirmAbandon ? "gui.yes" : "hamon.abandon.tab"));
    }
}
