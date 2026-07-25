package com.github.standobyte.jojo.powersystem.playerpower.client_screens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ui.screen_jojomenu.PaperButton;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.PlaceholderScreen;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.Tab;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.TabCategory;
import com.github.standobyte.jojo.client.ui.utils.Scrolling;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerData;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerType;
import com.github.standobyte.jojo.powersystem.unlockableskill.ClLearnSkillPacket;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlayerPowerSkillsScreen extends PlaceholderScreen {
	private static final int TEXT_COLOR = 0xFF000000;
	private static final int DIM_TEXT_COLOR = 0xFF606060;
	private static final int LOCKED_COLOR = 0xFF902020;
	private static final int LEARNED_COLOR = 0xFF207020;
	private static final int PAPER_COLOR = 0xFFF4F0E6;
	private static final int PANEL_COLOR = 0xFFE9E1D0;
	private static final int BORDER_COLOR = 0xFF1D1A16;
	private static final int HOVER_COLOR = 0xFFE0D6BE;
	private static final int SELECTED_COLOR = 0xFFD1C29C;
	private static final int LIST_X = 16;
	private static final int LIST_Y = 48;
	private static final int LIST_WIDTH = 88;
	private static final int LIST_HEIGHT = 145;
	private static final int DETAIL_X = 112;
	private static final int DETAIL_Y = 48;
	private static final int DETAIL_WIDTH = 101;
	private static final int ROW_HEIGHT = 18;

	private final Supplier<? extends PlayerPowerType<?>> powerTypeSupplier;
	private final Scrolling listScrolling = new Scrolling(LIST_HEIGHT, 0);
	private final List<UnlockableSkill> skills = new ArrayList<>();
	private final List<Ability> abilities = new ArrayList<>();
	@Nullable private UnlockableSkill selectedSkill;
	@Nullable private Ability selectedAbility;
	private boolean abilityOverview;
	private Button learnSkillButton;
	private Button resetSkillsButton;
	private Button learnAllSkillsButton;

	public PlayerPowerSkillsScreen(Component title, TabCategory category, Tab tab,
			Supplier<? extends PlayerPowerType<?>> powerTypeSupplier) {
		super(title, category, tab);
		this.powerTypeSupplier = powerTypeSupplier;
	}

	@Override
	protected void init() {
		int x = getWindowX(this);
		int y = getWindowY(this);
		learnSkillButton = addRenderableWidget(new PaperButton(x + 144, y + 201, 80, 20,
				Component.translatable("jojo_ripples.stand_skills.learn"),
				button -> {
					PlayerPowerType<?> powerType = powerTypeSupplier.get();
					if (!abilityOverview && selectedSkill != null && powerType != null) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.learnSkill(
								PowerClass.PLAYER_POWER, powerType.getId(), selectedSkill.skillName));
					}
				}));
		resetSkillsButton = addRenderableWidget(new PaperButton(x + 154, y + 201, 70, 20,
				Component.translatable("jojo_ripples.stand_skills.reset"),
				button -> {
					PlayerPowerType<?> powerType = powerTypeSupplier.get();
					if (!abilityOverview && powerType != null) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.resetAll(
								PowerClass.PLAYER_POWER, powerType.getId()));
					}
				}));
		resetSkillsButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.note.creative_only")));
		learnAllSkillsButton = addRenderableWidget(new PaperButton(x + 80, y + 201, 70, 20,
				Component.translatable("jojo_ripples.stand_skills.learn_all"),
				button -> {
					PlayerPowerType<?> powerType = powerTypeSupplier.get();
					if (!abilityOverview && powerType != null) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.learnAll(
								PowerClass.PLAYER_POWER, powerType.getId()));
					}
				}));
		learnAllSkillsButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.note.creative_only")));
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		super.render(gui, mouseX, mouseY, partialTick);
		int x = getWindowX(this);
		int y = getWindowY(this);
		renderPanels(gui, x, y);

		Player player = Minecraft.getInstance().player;
		PlayerPower playerPower = player != null ? PlayerPower.get(player) : null;
		PlayerPowerType<?> powerType = powerTypeSupplier.get();
		PlayerPowerData data = playerPower != null && powerType != null && playerPower.getPowerType() == powerType
				? (PlayerPowerData) playerPower.getCurTypeData() : null;
		gui.drawString(font, Component.translatable("jojo_ripples.player_power.skills.title",
				powerType != null ? powerType.getName(playerPower) : Component.empty()), x + 16, y + 18, TEXT_COLOR, false);

		if (data == null || playerPower == null) {
			setButtonsVisible(false);
			drawWrapped(gui, Component.translatable("jojo_ripples.hamon.stats.unavailable"),
					x + 16, y + 52, getWindowWidth() - 32, DIM_TEXT_COLOR);
			renderTabTooltip(gui, this, mouseX, mouseY);
			return;
		}

		refreshSkills(data, playerPower);
		updateButtons(data, playerPower, player);
		if (abilityOverview) {
			Ability hovered = getHoveredAbility(mouseX, mouseY);
			renderAbilityList(gui, playerPower, hovered);
			renderAbilityDetails(gui, playerPower);
		}
		else {
			UnlockableSkill hovered = getHoveredSkill(mouseX, mouseY);
			renderSkillList(gui, data, hovered);
			renderDetails(gui, data, playerPower, player);
		}
		renderTabTooltip(gui, this, mouseX, mouseY);
	}

	private void renderPanels(GuiGraphics gui, int x, int y) {
		gui.fill(x + 10, y + 10, x + getWindowWidth() - 10, y + 194, PAPER_COLOR);
		drawBorder(gui, x + LIST_X - 4, y + LIST_Y - 5, LIST_WIDTH + 8, LIST_HEIGHT + 10);
		gui.fill(x + LIST_X - 3, y + LIST_Y - 4, x + LIST_X + LIST_WIDTH + 3, y + LIST_Y + LIST_HEIGHT + 4, PANEL_COLOR);
		drawBorder(gui, x + DETAIL_X - 4, y + DETAIL_Y - 5, DETAIL_WIDTH + 8, LIST_HEIGHT + 10);
		gui.fill(x + DETAIL_X - 3, y + DETAIL_Y - 4, x + DETAIL_X + DETAIL_WIDTH + 3, y + DETAIL_Y + LIST_HEIGHT + 4, PANEL_COLOR);
	}

	private void drawBorder(GuiGraphics gui, int x, int y, int width, int height) {
		gui.fill(x, y, x + width, y + 1, BORDER_COLOR);
		gui.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR);
		gui.fill(x, y, x + 1, y + height, BORDER_COLOR);
		gui.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);
	}

	private void setButtonsVisible(boolean visible) {
		learnSkillButton.visible = visible;
		resetSkillsButton.visible = visible;
		learnAllSkillsButton.visible = visible;
	}

	private void refreshSkills(PlayerPowerData data, PlayerPower playerPower) {
		boolean showAbilityOverview = data.getAllSkills().isEmpty();
		if (abilityOverview != showAbilityOverview) {
			listScrolling.setScrollOffset(0);
		}
		abilityOverview = showAbilityOverview;
		skills.clear();
		abilities.clear();
		if (abilityOverview) {
			selectedSkill = null;
			abilities.addAll(playerPower.getMoveset().abilities.values().stream()
					.filter(Ability::addToControlSchemeEditing)
					.toList());
			if (!abilities.contains(selectedAbility)) {
				selectedAbility = null;
			}
			listScrolling.setContentsHeight(abilities.size() * ROW_HEIGHT + 2);
			return;
		}
		selectedAbility = null;
		for (UnlockableSkill skill : data.getAllSkills().values()) {
			if (!skill.hidden) {
				skills.add(skill);
			}
		}
		listScrolling.setContentsHeight(skills.size() * ROW_HEIGHT + 2);
	}

	private void updateButtons(PlayerPowerData data, PlayerPower playerPower, Player player) {
		if (abilityOverview) {
			setButtonsVisible(false);
			return;
		}
		boolean creative = player != null && player.isCreative();
		boolean selectedLearned = selectedSkill != null && data.isSkillUnlocked(selectedSkill.skillName);
		ConditionCheck canLearn = selectedSkill != null ? selectedSkill.canUnlockFromMenu(playerPower, data) : ConditionCheck.NEGATIVE;
		learnSkillButton.visible = selectedSkill != null && !selectedLearned;
		learnSkillButton.active = selectedSkill != null && (creative || canLearn.isPositive());
		if (creative && selectedSkill != null && !selectedLearned) {
			learnSkillButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.player_power.skills.creative_unlock")));
		}
		else {
			Component warning = canLearn.getWarning();
			learnSkillButton.setTooltip(warning != null ? Tooltip.create(warning.plainCopy().withStyle(ChatFormatting.RED)) : null);
		}
		resetSkillsButton.visible = selectedSkill == null && creative && !skills.isEmpty();
		learnAllSkillsButton.visible = selectedSkill == null && creative && !skills.isEmpty();
	}

	private void renderSkillList(GuiGraphics gui, PlayerPowerData data, @Nullable UnlockableSkill hovered) {
		int x = getWindowX(this) + LIST_X;
		int y = getWindowY(this) + LIST_Y;
		if (skills.isEmpty()) {
			drawWrapped(gui, Component.translatable("jojo_ripples.player_power.skills.none"), x + 3, y + 5, LIST_WIDTH - 6, DIM_TEXT_COLOR);
			return;
		}
		listScrolling.pushOffsetScissor(gui, y, x, x + LIST_WIDTH);
		for (int i = 0; i < skills.size(); ++i) {
			UnlockableSkill skill = skills.get(i);
			int rowY = y + i * ROW_HEIGHT;
			boolean selected = skill == selectedSkill;
			boolean learned = data.isSkillUnlocked(skill.skillName);
			gui.fill(x, rowY, x + LIST_WIDTH, rowY + ROW_HEIGHT - 1,
					selected ? SELECTED_COLOR : skill == hovered ? HOVER_COLOR : PANEL_COLOR);
			int color = learned ? LEARNED_COLOR : skill.implemented == UnlockableSkill.DevStatus.NYI ? DIM_TEXT_COLOR : TEXT_COLOR;
			gui.drawString(font, trimToWidth(skill.textName, LIST_WIDTH - 8), x + 4, rowY + 5, color, false);
		}
		listScrolling.pop(gui);
	}

	private void renderAbilityList(GuiGraphics gui, PlayerPower playerPower, @Nullable Ability hovered) {
		int x = getWindowX(this) + LIST_X;
		int y = getWindowY(this) + LIST_Y;
		if (abilities.isEmpty()) {
			drawWrapped(gui, Component.translatable("jojo_ripples.player_power.skills.ability_overview.none"),
					x + 3, y + 5, LIST_WIDTH - 6, DIM_TEXT_COLOR);
			return;
		}
		listScrolling.pushOffsetScissor(gui, y, x, x + LIST_WIDTH);
		for (int i = 0; i < abilities.size(); ++i) {
			Ability ability = abilities.get(i);
			int rowY = y + i * ROW_HEIGHT;
			gui.fill(x, rowY, x + LIST_WIDTH, rowY + ROW_HEIGHT - 1,
					ability == selectedAbility ? SELECTED_COLOR : ability == hovered ? HOVER_COLOR : PANEL_COLOR);
			gui.drawString(font, trimToWidth(ability.getName(playerPower), LIST_WIDTH - 8),
					x + 4, rowY + 5, TEXT_COLOR, false);
		}
		listScrolling.pop(gui);
	}

	private void renderAbilityDetails(GuiGraphics gui, PlayerPower playerPower) {
		int x = getWindowX(this) + DETAIL_X;
		int y = getWindowY(this) + DETAIL_Y;
		if (selectedAbility != null) {
			y = drawWrapped(gui, selectedAbility.getName(playerPower), x, y, DETAIL_WIDTH, TEXT_COLOR) + 4;
		}
		drawWrapped(gui, Component.translatable("jojo_ripples.player_power.skills.ability_overview"),
				x, y, DETAIL_WIDTH, DIM_TEXT_COLOR);
	}

	private void renderDetails(GuiGraphics gui, PlayerPowerData data, PlayerPower playerPower, Player player) {
		int x = getWindowX(this) + DETAIL_X;
		int y = getWindowY(this) + DETAIL_Y;
		if (selectedSkill == null) {
			drawWrapped(gui, Component.translatable("jojo_ripples.player_power.skills.not_selected"), x, y, DETAIL_WIDTH, DIM_TEXT_COLOR);
			if (player != null && player.isCreative() && !skills.isEmpty()) {
				drawWrapped(gui, Component.translatable("jojo_ripples.player_power.skills.creative_hint"), x, y + 28, DETAIL_WIDTH, DIM_TEXT_COLOR);
			}
			return;
		}
		boolean learned = data.isSkillUnlocked(selectedSkill.skillName);
		ConditionCheck canLearn = selectedSkill.canUnlockFromMenu(playerPower, data);
		y = drawWrapped(gui, selectedSkill.textName, x, y, DETAIL_WIDTH, TEXT_COLOR) + 3;
		gui.drawString(font, learned ? Component.translatable("jojo_ripples.hamon.skills.learned")
				: canLearn.isPositive() ? Component.translatable("jojo_ripples.stand_skills.learn")
						: Component.translatable("jojo_ripples.hamon.skills.locked"),
				x, y, learned ? LEARNED_COLOR : canLearn.isPositive() ? TEXT_COLOR : LOCKED_COLOR, false);
		y += 13;
		y = drawWrapped(gui, selectedSkill.textDesc, x, y, DETAIL_WIDTH, TEXT_COLOR) + 4;
		if (!selectedSkill.unlocksAbilities.isEmpty()) {
			y = drawWrapped(gui, Component.translatable("jojo_ripples.hamon.skills.unlocks",
					String.join(", ", selectedSkill.unlocksAbilities)), x, y, DETAIL_WIDTH, DIM_TEXT_COLOR) + 3;
		}
		if (selectedSkill.textControls != null) {
			y = drawWrapped(gui, Component.translatable("jojo_ripples.player_power.skills.controls",
					selectedSkill.textControls), x, y, DETAIL_WIDTH, DIM_TEXT_COLOR) + 3;
		}
		Component warning = canLearn.getWarning();
		if (!learned && warning != null && (player == null || !player.isCreative())) {
			drawWrapped(gui, warning.plainCopy().withStyle(ChatFormatting.RED), x, y, DETAIL_WIDTH, LOCKED_COLOR);
		}
	}

	private int drawWrapped(GuiGraphics gui, Component text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(text, width)) {
			gui.drawString(font, line, x, y, color, false);
			y += 10;
		}
		return y;
	}

	private Component trimToWidth(Component text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		String value = text.getString();
		String trimmed = font.plainSubstrByWidth(value, Math.max(0, width - font.width("...")));
		return Component.literal(trimmed + "...");
	}

	@Nullable
	private UnlockableSkill getHoveredSkill(double mouseX, double mouseY) {
		int x = getWindowX(this) + LIST_X;
		int y = getWindowY(this) + LIST_Y;
		if (mouseX < x || mouseX > x + LIST_WIDTH) {
			return null;
		}
		int rowY = listScrolling.getYHovered(y, (int) mouseY);
		if (rowY < 0) {
			return null;
		}
		int index = rowY / ROW_HEIGHT;
		return index >= 0 && index < skills.size() ? skills.get(index) : null;
	}

	@Nullable
	private Ability getHoveredAbility(double mouseX, double mouseY) {
		int x = getWindowX(this) + LIST_X;
		int y = getWindowY(this) + LIST_Y;
		if (mouseX < x || mouseX > x + LIST_WIDTH) {
			return null;
		}
		int rowY = listScrolling.getYHovered(y, (int) mouseY);
		if (rowY < 0) {
			return null;
		}
		int index = rowY / ROW_HEIGHT;
		return index >= 0 && index < abilities.size() ? abilities.get(index) : null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (clickTab(mouseX, mouseY, button, this)) {
			return true;
		}
		if (button == 0) {
			if (abilityOverview) {
				Ability hovered = getHoveredAbility(mouseX, mouseY);
				if (hovered != null) {
					selectedAbility = hovered;
					return true;
				}
			}
			else {
				UnlockableSkill hovered = getHoveredSkill(mouseX, mouseY);
				if (hovered != null) {
					selectedSkill = hovered;
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		listScrolling.scroll(scrollY);
		return true;
	}
}
