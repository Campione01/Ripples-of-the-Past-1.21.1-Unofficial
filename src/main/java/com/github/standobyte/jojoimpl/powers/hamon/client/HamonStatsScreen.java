package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.PaperButton;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.PlaceholderScreen;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.Tab;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.TabCategory;
import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.client.ui.utils.Scrolling;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.network.PacketDistributor;

public class HamonStatsScreen extends PlaceholderScreen {
    private static final ResourceLocation HAMON_WINDOW = JojoMod.resLoc("textures/gui/hamon_window.png");
    private static final ResourceLocation HAMON_BACKGROUND =
            JojoMod.resLoc("textures/gui/advancements/jojo.png");
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##");
    private static final DecimalFormat PERCENTAGE_FORMAT = new DecimalFormat("0.#");
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int DIM_TEXT_COLOR = 0xFFA7A7A7;
    private static final int WARNING_COLOR = 0xFFFF6B6B;
    private static final int POSITIVE_COLOR = 0xFF55FF55;
    private static final int NEGATIVE_COLOR = 0xFFFF5555;
    private static final int CONTENT_X = 9;
    private static final int CONTENT_Y = 18;
    private static final int CONTENT_WIDTH = 212;
    private static final int CONTENT_HEIGHT = 200;
    private static final int TEXT_WIDTH = 200;
    private static final int STAT_BAR_X = 163;
    private static final int EXERCISE_LEFT_X = 24;
    private static final int EXERCISE_RIGHT_X = 120;
    private static final int EXERCISES_TOTAL_X = 21;

    private final Scrolling scrolling = new Scrolling(CONTENT_HEIGHT, 0);
    private final int[] exerciseBarContentY = new int[HamonData.MAX_EXERCISES_NEEDED];
    private Button abandonButton;
    private Button confirmAbandonButton;
    private Button cancelAbandonButton;
    private boolean confirmAbandon;
    private int screenTicks;
    private boolean hamonStrengthLimited;
    private boolean hamonControlLimited;
    private int strengthStatContentY;
    private int controlStatContentY;
    private int breathingStatContentY;
    private int exercisesAverageContentY;
    private int exercisesDescriptionContentY;
    private List<FormattedCharSequence> exercisesDescriptionLines = List.of();
    private Component breathControlMaskComponent;

    public HamonStatsScreen(Component title, TabCategory category, Tab tab) {
        super(title, category, tab, HAMON_WINDOW);
    }

    @Override
    protected void init() {
        int x = getWindowX(this);
        int y = getWindowY(this);
        scrolling.scrollSpeed = 16;
        abandonButton = addRenderableWidget(new PaperButton(x + 13, y + 999, 204, 20,
                Component.translatable("hamon.abandon.tab"),
                button -> confirmAbandon = true));
        confirmAbandonButton = addRenderableWidget(new PaperButton(x + 13, y + 96, 100, 20,
                Component.translatable("gui.yes"), button -> {
                    PacketDistributor.sendToServer(new ClHamonAbandonButtonPacket());
                    onClose();
                }));
        cancelAbandonButton = addRenderableWidget(new PaperButton(x + 117, y + 96, 100, 20,
                Component.translatable("gui.no"), button -> confirmAbandon = false));
        setAllButtonsVisible(false);
    }

    @Override
    public void tick() {
        super.tick();
        screenTicks++;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // PlaceholderScreen renders widgets before its paper. Hide them for that pass,
        // then draw the active controls once after the Hamon contents.
        setAllButtonsVisible(false);
        super.render(gui, mouseX, mouseY, partialTick);
        int windowX = getWindowX(this);
        int windowY = getWindowY(this);
        renderTiledBackground(gui, windowX, windowY);

        Player localPlayer = Minecraft.getInstance().player;
        HamonData data = localPlayer != null
                ? PlayerPower.getPowerData(localPlayer, ModPlayerPowers.HAMON).orElse(null)
                : null;
        if (data == null) {
            gui.drawString(font, Component.translatable("jojo_ripples.hamon.stats.unavailable"),
                    windowX + 14, windowY + 24, DIM_TEXT_COLOR, false);
            renderTabTooltip(gui, this, mouseX, mouseY);
            return;
        }

        if (confirmAbandon) {
            renderAbandonConfirmation(gui, windowX, windowY);
            confirmAbandonButton.visible = true;
            cancelAbandonButton.visible = true;
            confirmAbandonButton.render(gui, mouseX, mouseY, partialTick);
            cancelAbandonButton.render(gui, mouseX, mouseY, partialTick);
            renderTabTooltip(gui, this, mouseX, mouseY);
            return;
        }

        renderScrollableContents(gui, data, localPlayer, windowX, windowY,
                mouseX, mouseY, partialTick);
        renderScrollBar(gui, windowX, windowY + CONTENT_Y);
        if (abandonButton.visible) {
            abandonButton.render(gui, mouseX, mouseY, partialTick);
        }
        renderHamonTooltips(gui, data, localPlayer, mouseX, mouseY, windowX, windowY);
        renderTabTooltip(gui, this, mouseX, mouseY);
    }

    private void renderTiledBackground(GuiGraphics gui, int windowX, int windowY) {
        int left = windowX + CONTENT_X;
        int top = windowY + CONTENT_Y;
        int right = left + CONTENT_WIDTH;
        int bottom = top + CONTENT_HEIGHT;
        gui.enableScissor(left, top, right, bottom);
        int scrollOffset = confirmAbandon ? 0 : (int) Math.floor(scrolling.scrollOffset) % 16;
        for (int y = top + scrollOffset - 16; y < bottom; y += 16) {
            for (int x = left; x < right; x += 16) {
                int width = Math.min(16, right - x);
                int height = Math.min(16, bottom - y);
                BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_BACKGROUND,
                        x, y, width, height, 0,
                        0, 0, width, height, 16, 16, BlitFloat.NO_TINT);
            }
        }
        gui.disableScissor();
    }

    private void renderAbandonConfirmation(GuiGraphics gui, int windowX, int windowY) {
        int centerX = windowX + getWindowWidth() / 2;
        int y = windowY + CONTENT_Y + 11;
        for (FormattedCharSequence line : font.split(
                Component.translatable("hamon.abandon.tab.desc"), CONTENT_WIDTH - 12)) {
            gui.drawCenteredString(font, line, centerX, y, TEXT_COLOR);
            y += 9;
        }
        y += 18;
        for (FormattedCharSequence line : font.split(
                Component.translatable("hamon.abandon.tab.desc2"), CONTENT_WIDTH - 12)) {
            gui.drawCenteredString(font, line, centerX, y, TEXT_COLOR);
            y += 9;
        }
    }

    private void renderScrollableContents(GuiGraphics gui, HamonData data, Player player,
            int windowX, int windowY, int mouseX, int mouseY, float partialTick) {
        int x = windowX + CONTENT_X + 2;
        int top = windowY + CONTENT_Y;
        int y = top + 6;
        scrolling.pushOffsetScissor(gui, top,
                windowX + CONTENT_X, windowX + CONTENT_X + CONTENT_WIDTH);

        strengthStatContentY = y - top;
        hamonStrengthLimited = renderStat(gui, data,
                Component.translatable("hamon.strength_level",
                        data.getHamonStrengthLevel(), HamonData.MAX_STAT_LEVEL),
                data.getHamonStrengthPoints(), data.getHamonStrengthLevel(),
                windowX, x, y, 203, 234);
        y = drawWrapped(gui, Component.translatable("hamon.strength_stat.desc"),
                x + 3, y + 12, TEXT_WIDTH, TEXT_COLOR) + 7;

        controlStatContentY = y - top;
        hamonControlLimited = renderStat(gui, data,
                Component.translatable("hamon.control_level",
                        data.getHamonControlLevel(), HamonData.MAX_STAT_LEVEL),
                data.getHamonControlPoints(), data.getHamonControlLevel(),
                windowX, x, y, 203, 239);
        y = drawWrapped(gui, Component.translatable("hamon.control_stat.desc"),
                x + 3, y + 12, TEXT_WIDTH, TEXT_COLOR) + 7;

        breathingStatContentY = y - top;
        float breathingLevel = data.getBreathingLevel();
        gui.drawString(font, Component.translatable("hamon.breathing_level",
                        (int) breathingLevel, (int) HamonData.MAX_BREATHING_LEVEL),
                x, y, TEXT_COLOR, false);
        boolean fullTraining = breathingLevel >= HamonData.MAX_BREATHING_LEVEL;
        drawStatBar(gui, windowX + STAT_BAR_X, y,
                203, fullTraining ? 249 : 244,
                fullTraining ? 1.0F : breathingLevel - (int) breathingLevel);
        if (fullTraining) {
            renderFullTrainingShine(gui, windowX + STAT_BAR_X, y, partialTick);
        }
        MutableComponent breathingDescription = Component.translatable("hamon.breathing_stat.desc");
        int configuredGap = JojoModConfig.getCommonConfigInstance(true).breathingHamonStatGap.get();
        if (configuredGap >= 0) {
            breathingDescription.append(Component.translatable("hamon.breathing_stat.desc.gap"));
        }
        y = drawWrapped(gui, breathingDescription,
                x + 3, y + 12, TEXT_WIDTH, TEXT_COLOR) + 8;

        y = renderExercises(gui, data, player, windowX, x, top, y, mouseX, mouseY);

        y = drawWrapped(gui, Component.translatable("jojo_ripples.hamon.training.average.desc"),
                x + 3, y, TEXT_WIDTH, TEXT_COLOR) + 4;

        exercisesDescriptionContentY = y - top;
        Component exercisesDescription = Component.translatable("hamon.breathing_stat.desc2",
                breathControlMaskHoverable(player));
        exercisesDescriptionLines = font.split(exercisesDescription, TEXT_WIDTH);
        y = drawLines(gui, exercisesDescriptionLines, x + 3, y, TEXT_COLOR) + 4;
        if (data.breathingCanGoDown(player)) {
            y = drawWrapped(gui, Component.translatable("hamon.breathing_stat.desc3"),
                    x + 3, y, TEXT_WIDTH, WARNING_COLOR) + 3;
        }
        if (configuredGap >= 0) {
            y = drawWrapped(gui, Component.translatable("hamon.breathing_stat.desc4", configuredGap),
                    x + 3, y, TEXT_WIDTH, DIM_TEXT_COLOR) + 3;
        }
        int abandonButtonContentY = y - top + 6;
        scrolling.setContentsHeight(abandonButtonContentY + 30);
        abandonButton.setY(top + abandonButtonContentY + (int) scrolling.scrollOffset);
        abandonButton.visible = abandonButton.getY() >= top
                && abandonButton.getY() + abandonButton.getHeight() <= top + CONTENT_HEIGHT;
        scrolling.pop(gui);
    }

    private boolean renderStat(GuiGraphics gui, HamonData data,
            Component label, int points, int level,
            int windowX, int x, int y, int fillU, int fillV) {
        gui.drawString(font, label, x, y, TEXT_COLOR, false);
        drawStatBar(gui, windowX + STAT_BAR_X, y, fillU, fillV, statProgress(points, level));
        boolean limited = level < HamonData.MAX_STAT_LEVEL && level >= data.getStatLevelLimit(true);
        if (limited) {
            blit(gui, windowX + STAT_BAR_X - 12, y, 8, 8,
                    230, 206, 8, 8, BlitFloat.NO_TINT);
        }
        return limited;
    }

    private int renderExercises(GuiGraphics gui, HamonData data, Player player,
            int windowX, int x, int top, int y, int mouseX, int mouseY) {
        exerciseBarContentY[HamonData.Exercise.MINING.ordinal()] = y - top;
        exerciseBarContentY[HamonData.Exercise.RUNNING.ordinal()] = y - top;
        drawExerciseBar(gui, windowX + EXERCISE_LEFT_X, y, data, HamonData.Exercise.MINING);
        drawExerciseBar(gui, windowX + EXERCISE_RIGHT_X, y, data, HamonData.Exercise.RUNNING);
        gui.drawCenteredString(font, HamonData.Exercise.MINING.getName(),
                windowX + EXERCISE_LEFT_X + 46, y, TEXT_COLOR);
        gui.drawCenteredString(font, HamonData.Exercise.RUNNING.getName(),
                windowX + EXERCISE_RIGHT_X + 46, y, TEXT_COLOR);

        y += 9;
        exerciseBarContentY[HamonData.Exercise.SWIMMING.ordinal()] = y - top;
        exerciseBarContentY[HamonData.Exercise.MEDITATION.ordinal()] = y - top;
        drawExerciseBar(gui, windowX + EXERCISE_LEFT_X, y, data, HamonData.Exercise.SWIMMING);
        drawExerciseBar(gui, windowX + EXERCISE_RIGHT_X, y, data, HamonData.Exercise.MEDITATION);
        if (mouseAtMeditationBar(mouseX, mouseY)) {
            gui.fill(windowX + EXERCISE_RIGHT_X + 1, y + 1,
                    windowX + EXERCISE_RIGHT_X + 91, y + 6, 0x4FFFFFFF);
        }
        gui.drawCenteredString(font, HamonData.Exercise.SWIMMING.getName(),
                windowX + EXERCISE_LEFT_X + 46, y, TEXT_COLOR);
        gui.drawCenteredString(font, HamonData.Exercise.MEDITATION.getName(),
                windowX + EXERCISE_RIGHT_X + 46, y, TEXT_COLOR);

        y += 11;
        exercisesAverageContentY = y - top;
        drawExercisesTotal(gui, windowX + EXERCISES_TOTAL_X, y, data, player);
        float breathingIncrease = data.getBreathingIncrease(player, false);
        if (breathingIncrease != 0.0F) {
            Component prediction = Component.literal((breathingIncrease > 0.0F ? "+" : "")
                    + NUMBER_FORMAT.format(breathingIncrease));
            gui.drawCenteredString(font, prediction, windowX + getWindowWidth() / 2,
                    y + 9, breathingIncrease > 0.0F ? POSITIVE_COLOR : NEGATIVE_COLOR);
        }
        return y + 24;
    }

    private int drawWrapped(GuiGraphics gui, Component text, int x, int y, int width, int color) {
        return drawLines(gui, font.split(text, width), x, y, color);
    }

    private int drawLines(GuiGraphics gui, List<FormattedCharSequence> lines, int x, int y, int color) {
        for (FormattedCharSequence line : lines) {
            gui.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void renderScrollBar(GuiGraphics gui, int windowX, int top) {
        int barX = windowX + 219;
        gui.fill(barX, top, barX + 2, top + CONTENT_HEIGHT, 0x33000000);
        int[] bounds = scrolling.getScrollBarBounds(0, 12);
        if (bounds != null) {
            gui.fill(barX, top + bounds[0], barX + 2, top + bounds[1], TEXT_COLOR);
        }
    }

    private void renderHamonTooltips(GuiGraphics gui, HamonData data, Player player,
            int mouseX, int mouseY, int windowX, int windowY) {
        int top = windowY + CONTENT_Y;
        int contentY = scrolling.getYHovered(top, mouseY);
        if (contentY < 0 || mouseX < windowX + CONTENT_X
                || mouseX >= windowX + CONTENT_X + CONTENT_WIDTH) {
            return;
        }

        if (renderStatTooltip(gui, data.getHamonStrengthPoints(), data.getHamonStrengthLevel(),
                hamonStrengthLimited, strengthStatContentY, contentY, mouseX, mouseY, windowX)) {
            return;
        }
        if (renderStatTooltip(gui, data.getHamonControlPoints(), data.getHamonControlLevel(),
                hamonControlLimited, controlStatContentY, contentY, mouseX, mouseY, windowX)) {
            return;
        }
        if (isInside(mouseX, contentY, windowX + STAT_BAR_X - 1,
                breathingStatContentY, 52, 8)) {
            gui.renderComponentTooltip(font, List.of(Component.literal(
                    NUMBER_FORMAT.format(data.getBreathingLevel()) + "/"
                            + NUMBER_FORMAT.format(HamonData.MAX_BREATHING_LEVEL))), mouseX, mouseY);
            return;
        }

        for (HamonData.Exercise exercise : List.of(
                HamonData.Exercise.MINING, HamonData.Exercise.RUNNING,
                HamonData.Exercise.SWIMMING, HamonData.Exercise.MEDITATION)) {
            int barX = exercise.ordinal() % 2 == 0
                    ? windowX + EXERCISE_LEFT_X : windowX + EXERCISE_RIGHT_X;
            int barY = exerciseBarContentY[exercise.ordinal()];
            if (isInside(mouseX, contentY, barX + 85, barY - 1, 8, 9)) {
                renderCompletedExerciseTooltip(gui, data, exercise, mouseX, mouseY);
                return;
            }
            if (isInside(mouseX, contentY, barX, barY, 92, 7)) {
                List<Component> tooltip = new ArrayList<>();
                if (exercise == HamonData.Exercise.MEDITATION) {
                    tooltip.add(Component.translatable("hamon.meditation_button"));
                    tooltip.add(Component.translatable("hamon.meditation_button.stability_hint")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
                else {
                    tooltip.add(exercise.getName());
                    tooltip.add(Component.literal(data.getExerciseTicks(exercise)
                            + "/" + exercise.getMaxTicks(data)));
                }
                renderWrappedTooltip(gui, tooltip, mouseX, mouseY);
                return;
            }
        }

        int averageX = windowX + EXERCISES_TOTAL_X;
        if (isInside(mouseX, contentY, averageX - 9, exercisesAverageContentY - 1, 8, 9)
                && data.getTrainingBonus(player, false) > 0.0F) {
            Component bonus = Component.translatable("hamon.training_bonus",
                    NUMBER_FORMAT.format(data.getTrainingBonus(player, true)));
            if (data.getBreathingIncrease(player, false) <= 0.0F) {
                bonus = bonus.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            }
            renderWrappedTooltip(gui, List.of(bonus), mouseX, mouseY);
            return;
        }
        if (isInside(mouseX, contentY, averageX + 186, exercisesAverageContentY - 1, 8, 9)) {
            renderCompletedExerciseTooltip(gui, data, null, mouseX, mouseY);
            return;
        }
        if (isInside(mouseX, contentY, averageX, exercisesAverageContentY, 194, 7)) {
            renderExercisesAverageTooltip(gui, data, player, mouseX, mouseY);
            return;
        }

        int descriptionLine = (contentY - exercisesDescriptionContentY) / 10;
        if (contentY >= exercisesDescriptionContentY
                && descriptionLine >= 0 && descriptionLine < exercisesDescriptionLines.size()) {
            int relativeX = mouseX - (windowX + CONTENT_X + 3);
            Style style = font.getSplitter().componentStyleAtWidth(
                    exercisesDescriptionLines.get(descriptionLine), relativeX);
            if (style != null && style.getHoverEvent() != null) {
                gui.renderComponentHoverEffect(font, style, mouseX, mouseY);
            }
        }
    }

    private boolean renderStatTooltip(GuiGraphics gui, int points, int level, boolean limited,
            int statContentY, int contentY, int mouseX, int mouseY, int windowX) {
        if (limited && isInside(mouseX, contentY,
                windowX + STAT_BAR_X - 12, statContentY, 8, 8)) {
            renderWrappedTooltip(gui, List.of(Component.translatable("hamon.stat_limited")), mouseX, mouseY);
            return true;
        }
        if (isInside(mouseX, contentY, windowX + STAT_BAR_X - 1,
                statContentY, 52, 8)) {
            if (level >= HamonData.MAX_STAT_LEVEL) {
                gui.renderComponentTooltip(font, List.of(Component.translatable("hamon.max_level")), mouseX, mouseY);
            }
            else {
                int pointsAtLevel = HamonData.pointsAtLevel(level);
                int pointsToNext = HamonData.pointsAtLevel(level + 1) - pointsAtLevel;
                gui.renderComponentTooltip(font, List.of(Component.literal(
                        (points - pointsAtLevel) + "/" + pointsToNext)), mouseX, mouseY);
            }
            return true;
        }
        return false;
    }

    private void renderExercisesAverageTooltip(GuiGraphics gui, HamonData data, Player player,
            int mouseX, int mouseY) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("hamon.exercise.all.count.message",
                data.getCompleteExercisesCount(), HamonData.MAX_EXERCISES_NEEDED));
        float breathingIncrease = data.getBreathingIncrease(player, false);
        float breathingBonus = data.getTrainingBonus(player, true);
        if (breathingIncrease > 0.0F) {
            if (breathingBonus > 0.0F) {
                tooltip.add(Component.translatable("hamon.exercise.all.day_end_increase.bonus",
                        NUMBER_FORMAT.format(breathingIncrease - breathingBonus),
                        NUMBER_FORMAT.format(breathingBonus)));
            }
            else {
                tooltip.add(Component.translatable("hamon.exercise.all.day_end_increase",
                        NUMBER_FORMAT.format(breathingIncrease)));
            }
        }
        else if (breathingIncrease < 0.0F) {
            tooltip.add(Component.translatable("hamon.exercise.all.day_end_decrease",
                    NUMBER_FORMAT.format(-breathingIncrease)));
        }
        else if (data.getCanSkipTrainingDays() > 0 && data.breathingCanGoDown(player)) {
            tooltip.add(Component.translatable("hamon.exercise.can_skip", data.getCanSkipTrainingDays()));
        }
        renderWrappedTooltip(gui, tooltip, mouseX, mouseY);
    }

    private void renderCompletedExerciseTooltip(GuiGraphics gui, HamonData data,
            HamonData.Exercise exercise, int mouseX, int mouseY) {
        boolean allExercises = exercise == null;
        boolean hasBuff = allExercises ? data.has4ExercisesBonus() : data.isExerciseComplete(exercise);
        Component hint = allExercises
                ? Component.translatable("hamon.exercise.full_completion_hint", HamonData.MAX_EXERCISES_NEEDED)
                : Component.translatable("hamon.exercise.completion_buff_hint");
        Component buff = allExercises
                ? Component.translatable("hamon.exercise.full_completion_buff",
                        PERCENTAGE_FORMAT.format(HamonData.ALL_EXERCISES_EFFICIENCY_ADD_MULTIPLIER * 100.0F),
                        Component.translatable("hamon.exercise.completion_buff_hint2"))
                : Component.translatable("hamon.exercise." + exercise.name().toLowerCase(Locale.ROOT)
                                + ".completion_buff",
                        PERCENTAGE_FORMAT.format(exercise.getBuffPercentage()),
                        Component.translatable("hamon.exercise.completion_buff_hint2"));
        List<Component> tooltip = new ArrayList<>();
        if (!hasBuff) {
            tooltip.add(hint);
            buff = buff.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        tooltip.add(buff);
        if (allExercises) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("hamon.exercise.full_completion_hint3"));
        }
        renderWrappedTooltip(gui, tooltip, mouseX, mouseY);
    }

    private void renderWrappedTooltip(GuiGraphics gui, List<Component> components, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component component : components) {
            lines.addAll(font.split(component, 150));
        }
        gui.renderTooltip(font, lines, mouseX, mouseY);
    }

    private Component breathControlMaskHoverable(Player player) {
        if (breathControlMaskComponent != null) {
            return breathControlMaskComponent;
        }
        ItemStack item = new ItemStack(ModItems.BREATH_CONTROL_MASK.get());
        item.enchant(player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.BINDING_CURSE), 1);
        breathControlMaskComponent = Component.translatable("hamon.breathing_stat.desc2.mask")
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(item))));
        return breathControlMaskComponent;
    }

    private void drawStatBar(GuiGraphics gui, int x, int y,
            int fillU, int fillV, float progress) {
        int fillWidth = (int) (50.0F * clamp01(progress));
        if (fillWidth > 0) {
            blit(gui, x, y + 1, fillWidth, 5, fillU, fillV, fillWidth, 5, BlitFloat.NO_TINT);
        }
        blit(gui, x - 1, y, 52, 7, 202, 227, 52, 7, BlitFloat.NO_TINT);
    }

    private void renderFullTrainingShine(GuiGraphics gui, int x, int y, float partialTick) {
        float ticks = screenTicks + partialTick;
        float duration = 7.0F;
        if (ticks >= duration) {
            return;
        }
        float xMin = x;
        float xMax = xMin + 50.0F;
        float shineX = xMin - 17.0F + 67.0F * ticks / duration;
        float clippedLeft = Math.max(xMin - shineX, 0.0F);
        float clippedRight = Math.max(shineX + 17.0F - xMax, 0.0F);
        float width = 17.0F - clippedLeft - clippedRight;
        if (width > 0.0F) {
            BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_WINDOW,
                    shineX + clippedLeft, y + 1.0F, width, 5.0F, 1.0F,
                    186.0F + clippedLeft, 249.0F, width, 5.0F,
                    256.0F, 256.0F, BlitFloat.NO_TINT);
        }
    }

    private void drawExerciseBar(GuiGraphics gui, int x, int y,
            HamonData data, HamonData.Exercise exercise) {
        int ticks = data.getExerciseTicks(exercise);
        int maxTicks = Math.max(exercise.getMaxTicks(data), 1);
        int fillWidth = Math.min(90, 90 * ticks / maxTicks);
        if (fillWidth > 0) {
            blit(gui, x + 1, y + 1, fillWidth, 5, 93, 250, fillWidth, 5, BlitFloat.NO_TINT);
        }
        blit(gui, x, y, 92, 7, 0, 249, 92, 7, BlitFloat.NO_TINT);
        blit(gui, x - 3, y - 1, 8, 8, 230, 92 + exercise.ordinal() * 16,
                16, 16, BlitFloat.NO_TINT);
        blit(gui, x + 85, y - 1, 8, 8, 230, 188, 16, 16,
                ticks >= maxTicks ? BlitFloat.NO_TINT : 0xFF000000);
    }

    private void drawExercisesTotal(GuiGraphics gui, int x, int y, HamonData data, Player player) {
        int complete = Math.min(data.getCompleteExercisesCount(), HamonData.MAX_EXERCISES_NEEDED);
        int completeWidth = Math.min(192, 48 * complete);
        int partialWidth = Math.min(192 - completeWidth,
                (int) (48.0F * clamp01(data.getMaxIncompleteExercise())));
        if (completeWidth > 0) {
            blit(gui, x + 1, y + 1, completeWidth, 5,
                    1, 234, completeWidth, 5, BlitFloat.NO_TINT);
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
        if (data.getTrainingBonus(player, false) > 0.0F) {
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
        return clamp01((float) (points - pointsAtLevel)
                / Math.max(pointsAtNextLevel - pointsAtLevel, 1));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(value, 1.0F));
    }

    private static boolean isInside(int mouseX, int mouseY,
            int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!confirmAbandon && button == 0 && mouseAtMeditationBar(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new ClHamonMeditationPacket());
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean mouseAtMeditationBar(double mouseX, double mouseY) {
        int windowX = getWindowX(this);
        int top = getWindowY(this) + CONTENT_Y;
        int contentY = scrolling.getYHovered(top, (int) mouseY);
        int barY = exerciseBarContentY[HamonData.Exercise.MEDITATION.ordinal()];
        return isInside((int) mouseX, contentY,
                windowX + EXERCISE_RIGHT_X, barY, 92, 7);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = getWindowX(this) + CONTENT_X;
        int y = getWindowY(this) + CONTENT_Y;
        if (mouseX >= x && mouseX < x + CONTENT_WIDTH
                && mouseY >= y && mouseY < y + CONTENT_HEIGHT) {
            scrolling.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        int x = getWindowX(this) + CONTENT_X;
        int y = getWindowY(this) + CONTENT_Y;
        if (button == 0 && mouseX >= x && mouseX < x + CONTENT_WIDTH
                && mouseY >= y && mouseY < y + CONTENT_HEIGHT) {
            scrolling.setScrollOffset(scrolling.scrollOffset + (float) dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void setAllButtonsVisible(boolean visible) {
        if (abandonButton != null) {
            abandonButton.visible = visible;
        }
        if (confirmAbandonButton != null) {
            confirmAbandonButton.visible = visible;
        }
        if (cancelAbandonButton != null) {
            cancelAbandonButton.visible = visible;
        }
    }
}
