package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.PaperButton;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.PlaceholderScreen;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.Tab;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.TabCategory;
import com.github.standobyte.jojo.client.ui.utils.Scrolling;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.unlockableskill.ClLearnSkillPacket;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonPickTechniquePacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonResetSkillsButtonPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonResetSkillsButtonPacket.HamonSkillsTab;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonSkillDefinition;
import com.github.standobyte.jojoimpl.powers.hamon.HamonSkillDefinition.HamonSkillBranch;
import com.github.standobyte.jojoimpl.powers.hamon.HamonTechnique;
import com.github.standobyte.jojoimpl.powers.hamon.HamonTechniqueDefinition;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public class HamonSkillsScreen extends PlaceholderScreen {
	private static final ResourceLocation HAMON_WINDOW = JojoMod.resLoc("textures/gui/hamon_window.png");
	private static final ResourceLocation HAMON_SKILLS = JojoMod.resLoc("textures/gui/hamon_window_2.png");
	private static final int WINDOW_THIN_BORDER = 9;
	private static final int WINDOW_UPPER_BORDER = 18;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int DIM_TEXT_COLOR = 0xFFA7A7A7;
	private static final int LOCKED_COLOR = 0xFFFF6B6B;
	private static final int LEARNED_COLOR = 0xFF76FF76;
	private static final int ROW_COLOR = 0x33000000;
	private static final int HOVER_COLOR = 0x66303030;
	private static final int SELECTED_COLOR = 0x995D933C;
	private static final int LIST_X = 16;
	private static final int LIST_Y = 48;
	private static final int LIST_WIDTH = 92;
	private static final int LIST_HEIGHT = 144;
	private static final int DETAIL_X = 118;
	private static final int DETAIL_Y = 48;
	private static final int DETAIL_WIDTH = 96;
	private static final int SKILL_BOX_SIZE = 26;
	private static final int SKILL_ICON_SIZE = 16;
	private static final int SKILL_TREE_START_Y = 114;
	private static final int SKILL_TREE_BRANCH_WIDTH = 68;
	private static final int TECHNIQUE_CARD_ICON_SIZE = 18;
	private static final int TECHNIQUE_CARD_ICON_GAP = 2;
	private static final int TECHNIQUE_CARD_ICONS_PER_ROW = 4;
	private static final int TECHNIQUE_SLOT_SIZE = 28;
	private static final int TECHNIQUE_SLOT_GAP = 2;
	private static final int TECHNIQUE_SLOTS_PER_ROW = 3;
	private static final int TECHNIQUE_SLOTS_Y = DETAIL_Y + 27;
	private static final Map<HamonSkillBranch, Integer> BRANCH_INDEX = new EnumMap<>(HamonSkillBranch.class);

	static {
		BRANCH_INDEX.put(HamonSkillBranch.OVERDRIVE, 0);
		BRANCH_INDEX.put(HamonSkillBranch.INFUSION, 1);
		BRANCH_INDEX.put(HamonSkillBranch.FLEXIBILITY, 2);
		BRANCH_INDEX.put(HamonSkillBranch.HEALING, 0);
		BRANCH_INDEX.put(HamonSkillBranch.ATTRACTANT_REPELLENT, 1);
		BRANCH_INDEX.put(HamonSkillBranch.BODY_MANIPULATION, 2);
	}

	private final View view;
	private final Scrolling listScrolling = new Scrolling(LIST_HEIGHT, 0);
	@Nullable private HamonSkillDefinition selectedSkill;
	@Nullable private HamonTechniqueDefinition selectedTechnique;
	private Button learnSkillButton;
	private Button resetSkillsButton;
	private Button learnAllSkillsButton;
	private Button pickTechniqueButton;

	public HamonSkillsScreen(Component title, TabCategory category, Tab tab, View view) {
		super(title, category, tab, HAMON_WINDOW);
		this.view = view;
		if (view == View.TECHNIQUE && !ModHamonSkills.TECHNIQUE_DEFINITIONS.isEmpty()) {
			this.selectedTechnique = ModHamonSkills.TECHNIQUE_DEFINITIONS.get(0);
		}
	}

	@Override
	protected void init() {
		int x = getWindowX(this);
		int y = getWindowY(this);
		int skillsButtonY = view == View.TECHNIQUE ? y + 199 : y + 92;

		learnSkillButton = addRenderableWidget(new PaperButton(x + 152, skillsButtonY, 72, 20,
				Component.translatable("hamon.learnButton"),
				button -> {
					if (selectedSkill != null) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.learnSkill(
								PowerClass.PLAYER_POWER, ModPlayerPowers.HAMON.get().getId(), selectedSkill.name()));
					}
				}));
		resetSkillsButton = addRenderableWidget(new PaperButton(x + 16, skillsButtonY, 58, 20,
				Component.translatable("hamon.resetButton"),
				button -> {
					if (view.resetTab != null) {
						PacketDistributor.sendToServer(ClHamonResetSkillsButtonPacket.resetTab(view.resetTab));
					}
				}));
		resetSkillsButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.note.creative_only")));
		learnAllSkillsButton = addRenderableWidget(new PaperButton(x + 80, skillsButtonY, 68, 20,
				Component.translatable("jojo_ripples.stand_skills.learn_all"),
				button -> PacketDistributor.sendToServer(ClLearnSkillPacket.learnAll(
						PowerClass.PLAYER_POWER, ModPlayerPowers.HAMON.get().getId()))));
		learnAllSkillsButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.note.creative_only")));
		pickTechniqueButton = addRenderableWidget(new PaperButton(x + 152, y + 199, 72, 20,
				Component.translatable("hamon.pick_technique"),
				button -> {
					if (selectedTechnique != null) {
						PacketDistributor.sendToServer(ClHamonPickTechniquePacket.pickTechnique(selectedTechnique.name()));
					}
				}));
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		super.render(gui, mouseX, mouseY, partialTick);

		Player player = Minecraft.getInstance().player;
		HamonData data = player != null ? PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElse(null) : null;
		PlayerPower playerPower = player != null ? PlayerPower.get(player) : null;
		int x = getWindowX(this);
		int y = getWindowY(this);

		gui.drawString(font, view.title(), x + 16, y + 18, TEXT_COLOR, false);
		if (data == null || playerPower == null) {
			setButtonsVisible(false);
			gui.drawString(font, Component.translatable("jojo_ripples.hamon.stats.unavailable"), x + 16, y + 48, DIM_TEXT_COLOR, false);
			return;
		}

		if (view.stat != null) {
			renderGeneralHeader(gui, data, x, y);
		}
		else {
			String techniqueName = data.getCharacterTechniqueName();
			Component selected = techniqueName.isEmpty() ? Component.literal("-") : Component.translatable("hamon.technique." + techniqueName);
			gui.drawString(font, Component.translatable("jojo_ripples.hamon.technique.selected", selected),
					x + 16, y + 31, TEXT_COLOR, false);
		}

		updateButtons(data, playerPower, player);
		if (view == View.TECHNIQUE) {
			List<TechniqueCard> cards = buildTechniqueCards();
			listScrolling.setContentsHeight(cards.stream().mapToInt(card -> card.y + card.height).max().orElse(0));
			TechniqueCard hoveredCard = getHoveredTechniqueCard(cards, mouseX, mouseY);
			HamonSkillDefinition hoveredSkill = getHoveredTechniqueCardSkill(hoveredCard, mouseX, mouseY);
			renderTechniqueCards(gui, cards, hoveredCard, hoveredSkill, data, playerPower, player, mouseX, mouseY);
		}
		else {
			List<SkillNode> nodes = buildSkillNodes();
			SkillNode hovered = getHoveredSkillNode(nodes, mouseX, mouseY);
			renderGeneralSkillTree(gui, nodes, hovered, data, playerPower, player);
		}
		renderDetails(gui, data, playerPower, player, mouseX, mouseY);
		renderGeneralGuidanceTooltips(gui, data, mouseX, mouseY, x, y);
		renderTabTooltip(gui, this, mouseX, mouseY);
	}

	private void renderGeneralHeader(GuiGraphics gui, HamonData data, int x, int y) {
		int statLevel = view.stat == HamonData.HamonStat.STRENGTH
				? data.getHamonStrengthLevel()
				: data.getHamonControlLevel();
		Component levelText = view.stat == HamonData.HamonStat.STRENGTH
				? Component.translatable("hamon.strength_level", statLevel, HamonData.MAX_STAT_LEVEL)
				: Component.translatable("hamon.control_level", statLevel, HamonData.MAX_STAT_LEVEL);
		gui.drawString(font, levelText, x + 16, y + 31, TEXT_COLOR, false);

		int points = data.getSkillPoints(view.stat);
		Component pointsText = Component.translatable("hamon.skill_points", points);
		int pointsX = x + 202 - font.width(pointsText);
		gui.drawString(font, pointsText, pointsX, y + 31, points > 0 ? LEARNED_COLOR : LOCKED_COLOR, false);
		gui.drawString(font, Component.literal("?"), x + 207, y + 31, DIM_TEXT_COLOR, false);

		if (points > 0) {
			boolean teacherNearby = data.getTeacherSkills() != null;
			BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_WINDOW,
					x + 205, y + 19, 8, 8, 1,
					teacherNearby ? 248 : 239, 206, 8, 8, 256, 256,
					BlitFloat.NO_TINT);
		}
	}

	private void renderGeneralGuidanceTooltips(GuiGraphics gui, HamonData data,
			int mouseX, int mouseY, int x, int y) {
		if (view.stat == null) {
			return;
		}
		if (mouseX >= x + 204 && mouseX < x + 215 && mouseY >= y + 29 && mouseY < y + 42) {
			gui.renderComponentTooltip(font, List.of(nextSkillPointHint(data)), mouseX, mouseY);
			return;
		}
		if (data.getSkillPoints(view.stat) > 0
				&& mouseX >= x + 203 && mouseX < x + 215 && mouseY >= y + 17 && mouseY < y + 29) {
			gui.renderComponentTooltip(font, List.of(unspentPointsHint(data)), mouseX, mouseY);
		}
	}

	private Component nextSkillPointHint(HamonData data) {
		int statLevel = view.stat == HamonData.HamonStat.STRENGTH
				? data.getHamonStrengthLevel()
				: data.getHamonControlLevel();
		if (statLevel >= HamonData.MAX_STAT_LEVEL) {
			return Component.translatable("hamon.max_skill_points");
		}
		String key = view.stat == HamonData.HamonStat.STRENGTH
				? "hamon.next_point.strength"
				: "hamon.next_point.control";
		return Component.translatable(key, data.nextSkillPointLvl(view.stat));
	}

	private Component unspentPointsHint(HamonData data) {
		return Component.translatable(data.getTeacherSkills() != null
				? "hamon.unspent_points"
				: "hamon.unspent_points_no_teacher");
	}

	private void setButtonsVisible(boolean visible) {
		learnSkillButton.visible = visible;
		resetSkillsButton.visible = visible;
		learnAllSkillsButton.visible = visible;
		pickTechniqueButton.visible = visible;
	}

	private void updateButtons(HamonData data, PlayerPower playerPower, Player player) {
		boolean creative = player != null && player.isCreative();
		UnlockableSkill unlockable = selectedSkill != null ? data.getAllSkills().get(selectedSkill.name()) : null;
		ConditionCheck canLearn = unlockable != null ? unlockable.canUnlockFromMenu(playerPower, data) : ConditionCheck.NEGATIVE;
		boolean selectedLearned = selectedSkill != null && data.isSkillUnlocked(selectedSkill.name());
		boolean needsTechniquePick = view == View.TECHNIQUE && data.getCharacterTechnique() == null;
		learnSkillButton.visible = selectedSkill != null && !selectedLearned && !needsTechniquePick;
		learnSkillButton.active = unlockable != null && (creative || canLearn.isPositive());
		if (creative && selectedSkill != null && !selectedLearned) {
			learnSkillButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.player_power.skills.creative_unlock")));
		}
		else {
			Component warning = canLearn.getWarning();
			learnSkillButton.setTooltip(warning != null ? Tooltip.create(warning.plainCopy().withStyle(ChatFormatting.RED)) : null);
		}

		resetSkillsButton.visible = selectedSkill == null && view.resetTab != null && HamonData.canResetTab(player, view.resetTab);
		learnAllSkillsButton.visible = selectedSkill == null && creative;
		pickTechniqueButton.visible = needsTechniquePick && selectedTechnique != null;
		HamonTechnique technique = selectedTechnique != null ? ModHamonSkills.techniqueByName(selectedTechnique.name()) : null;
		pickTechniqueButton.active = technique != null && technique.canPick(data);
	}

	private List<TechniqueCard> buildTechniqueCards() {
		List<TechniqueCard> cards = new ArrayList<>();
		int y = 0;
		for (HamonTechniqueDefinition technique : ModHamonSkills.TECHNIQUE_DEFINITIONS) {
			int icons = technique.perksOnPick().size() + technique.skillIds().size();
			int iconRows = Math.max(1, (icons + TECHNIQUE_CARD_ICONS_PER_ROW - 1) / TECHNIQUE_CARD_ICONS_PER_ROW);
			int height = 22 + iconRows * (TECHNIQUE_CARD_ICON_SIZE + TECHNIQUE_CARD_ICON_GAP);
			cards.add(new TechniqueCard(technique, y, height));
			y += height + 4;
		}
		return cards;
	}

	private List<SkillNode> buildSkillNodes() {
		List<SkillNode> nodes = new ArrayList<>();
		int originX = getWindowX(this) + WINDOW_THIN_BORDER;
		int originY = getWindowY(this) + WINDOW_UPPER_BORDER;
		for (HamonSkillBranch branch : view.branches) {
			int branchIndex = BRANCH_INDEX.getOrDefault(branch, 0);
			List<HamonSkillDefinition> branchSkills = ModHamonSkills.SKILL_DEFINITIONS.stream()
					.filter(skill -> skill.branch() == branch)
					.collect(Collectors.toList());
			Map<String, Integer> tierCache = new HashMap<>();
			for (HamonSkillDefinition skill : branchSkills) {
				tierForSkill(skill, branch, tierCache);
			}
			for (int tier = 0; tier <= 2; tier++) {
				final int tierIndex = tier;
				List<HamonSkillDefinition> tierSkills = branchSkills.stream()
						.filter(skill -> tierCache.getOrDefault(skill.name(), 0) == tierIndex)
						.collect(Collectors.toList());
				int tierSize = tierSkills.size();
				for (int tierI = 0; tierI < tierSize; tierI++) {
					int gridX = tierSize == 1 ? 1 : tierI * (int) Math.ceil(3.0F / tierSize);
					int nodeX = originX + 9 + branchIndex * SKILL_TREE_BRANCH_WIDTH + 3 + gridX * 13;
					int nodeY = originY + SKILL_TREE_START_Y + tier * (tier < 2 ? 26 : 27);
					nodes.add(new SkillNode(tierSkills.get(tierI), nodeX, nodeY, tier == 2));
				}
			}
		}
		return nodes;
	}

	private int tierForSkill(HamonSkillDefinition skill, HamonSkillBranch branch, Map<String, Integer> tierCache) {
		Integer cached = tierCache.get(skill.name());
		if (cached != null) {
			return cached;
		}
		int tier = 0;
		for (String prerequisite : skill.prerequisiteSkills()) {
			HamonSkillDefinition prerequisiteSkill = ModHamonSkills.definitionFor(prerequisite);
			if (prerequisiteSkill != null && prerequisiteSkill.branch() == branch) {
				tier = Math.max(tier, tierForSkill(prerequisiteSkill, branch, tierCache) + 1);
			}
		}
		tier = Math.min(tier, 2);
		tierCache.put(skill.name(), tier);
		return tier;
	}

	private void renderGeneralSkillTree(GuiGraphics gui, List<SkillNode> nodes, @Nullable SkillNode hovered,
			HamonData data, PlayerPower playerPower, Player player) {
		int originX = getWindowX(this) + WINDOW_THIN_BORDER;
		int originY = getWindowY(this) + WINDOW_UPPER_BORDER;
		for (HamonSkillBranch branch : view.branches) {
			int branchIndex = BRANCH_INDEX.getOrDefault(branch, 0);
			int centerX = originX + 9 + branchIndex * SKILL_TREE_BRANCH_WIDTH + 3 + 13 + 13;
			gui.drawCenteredString(font, trimToWidth(branchTitle(branch), 75),
					centerX, originY + SKILL_TREE_START_Y - 18, TEXT_COLOR);
		}

		SkillNode selectedNode = findSkillNode(nodes, selectedSkill);
		for (SkillNode node : nodes) {
			renderSkillSquare(gui, node, data, playerPower, player, false, false);
		}
		if (selectedNode != null) {
			renderSkillSquare(gui, selectedNode, data, playerPower, player, true, false);
		}
		SkillNode requirementSource = selectedNode != null ? selectedNode : hovered;
		if (requirementSource != null) {
			for (String prerequisite : requirementSource.skill.prerequisiteSkills()) {
				if (!data.isSkillUnlocked(prerequisite)) {
					SkillNode missing = findSkillNode(nodes, ModHamonSkills.definitionFor(prerequisite));
					if (missing != null) {
						renderSkillSquare(gui, missing, data, playerPower, player, false, true);
					}
				}
			}
		}
		for (SkillNode node : nodes) {
			BlitFloat.blit(gui.pose(), Minecraft.getInstance(), skillIconPath(node.skill.name()),
					node.x + 5, node.y + 5, SKILL_ICON_SIZE, SKILL_ICON_SIZE, 0, BlitFloat.NO_TINT);
		}
	}

	private void renderSkillSquare(GuiGraphics gui, SkillNode node, HamonData data, PlayerPower playerPower, Player player,
			boolean selectedOverlay, boolean requirementOverlay) {
		float textureX = selectedOverlay || requirementOverlay ? SKILL_BOX_SIZE : 0;
		float textureY = selectedOverlay ? (node.finalSkill ? 78 : 0)
				: requirementOverlay ? (node.finalSkill ? 104 : 26)
				: stateTextureY(node, data, playerPower, player);
		BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_SKILLS,
				node.x, node.y, SKILL_BOX_SIZE, SKILL_BOX_SIZE, 0,
				textureX, textureY, SKILL_BOX_SIZE, SKILL_BOX_SIZE, 256, 256,
				BlitFloat.NO_TINT);
	}

	private int stateTextureY(SkillNode node, HamonData data, PlayerPower playerPower, Player player) {
		boolean learned = data.isSkillUnlocked(node.skill.name());
		boolean canLearn = canLearn(node.skill, data, playerPower, player);
		int base = node.finalSkill ? 78 : 0;
		return base + (learned ? 52 : canLearn ? 26 : 0);
	}

	private boolean canLearn(HamonSkillDefinition skill, HamonData data, PlayerPower playerPower, Player player) {
		if (player != null && player.isCreative()) {
			return true;
		}
		UnlockableSkill unlockable = data.getAllSkills().get(skill.name());
		return unlockable != null && unlockable.canUnlockFromMenu(playerPower, data).isPositive();
	}

	private ResourceLocation skillIconPath(String skillName) {
		return JojoMod.resLoc("textures/hamon/" + skillName + ".png");
	}

	@Nullable
	private SkillNode findSkillNode(List<SkillNode> nodes, @Nullable HamonSkillDefinition skill) {
		if (skill == null) {
			return null;
		}
		for (SkillNode node : nodes) {
			if (Objects.equals(node.skill, skill)) {
				return node;
			}
		}
		return null;
	}

	@Nullable
	private SkillNode getHoveredSkillNode(List<SkillNode> nodes, double mouseX, double mouseY) {
		for (SkillNode node : nodes) {
			if (mouseX >= node.x && mouseX < node.x + SKILL_BOX_SIZE
					&& mouseY >= node.y && mouseY < node.y + SKILL_BOX_SIZE) {
				return node;
			}
		}
		return null;
	}

	private void renderTechniqueCards(GuiGraphics gui, List<TechniqueCard> cards,
			@Nullable TechniqueCard hoveredCard, @Nullable HamonSkillDefinition hoveredSkill,
			HamonData data, PlayerPower playerPower, Player player, int mouseX, int mouseY) {
		int x = getWindowX(this) + LIST_X;
		int y = getWindowY(this) + LIST_Y;
		listScrolling.pushOffsetScissor(gui, y, x, x + LIST_WIDTH);
		for (TechniqueCard card : cards) {
			int cardY = y + card.y;
			boolean selected = Objects.equals(selectedTechnique, card.technique);
			boolean current = card.technique.name().equals(data.getCharacterTechniqueName());
			gui.fill(x, cardY, x + LIST_WIDTH - 2, cardY + card.height - 1,
					selected ? SELECTED_COLOR : card == hoveredCard ? HOVER_COLOR : ROW_COLOR);
			gui.drawString(font, trimToWidth(Component.translatable("hamon.technique." + card.technique.name()), LIST_WIDTH - 8),
					x + 3, cardY + 4, current ? LEARNED_COLOR : TEXT_COLOR, false);

			List<HamonSkillDefinition> cardSkills = techniqueCardSkills(card.technique);
			for (int i = 0; i < cardSkills.size(); i++) {
				int iconX = x + 3 + (i % TECHNIQUE_CARD_ICONS_PER_ROW) * (TECHNIQUE_CARD_ICON_SIZE + TECHNIQUE_CARD_ICON_GAP);
				int iconY = cardY + 18 + (i / TECHNIQUE_CARD_ICONS_PER_ROW) * (TECHNIQUE_CARD_ICON_SIZE + TECHNIQUE_CARD_ICON_GAP);
				HamonSkillDefinition skill = cardSkills.get(i);
				renderTechniqueCardIcon(gui, skill, iconX, iconY, data, playerPower, player,
						card.technique.isTechniquePerk(skill.name()));
			}
		}
		listScrolling.pop(gui);

		if (hoveredSkill != null) {
			gui.renderComponentTooltip(font, List.of(
					Component.translatable("hamonSkill." + hoveredSkill.name() + ".name"),
					Component.translatable("hamonSkill." + hoveredSkill.name() + ".desc")), mouseX, mouseY);
		}
	}

	private List<HamonSkillDefinition> techniqueCardSkills(HamonTechniqueDefinition technique) {
		List<HamonSkillDefinition> skills = new ArrayList<>();
		for (String skillName : technique.perksOnPick()) {
			HamonSkillDefinition skill = ModHamonSkills.definitionFor(skillName);
			if (skill != null) {
				skills.add(skill);
			}
		}
		for (String skillName : technique.skillIds()) {
			HamonSkillDefinition skill = ModHamonSkills.definitionFor(skillName);
			if (skill != null) {
				skills.add(skill);
			}
		}
		return skills;
	}

	private void renderTechniqueCardIcon(GuiGraphics gui, HamonSkillDefinition skill, int x, int y,
			HamonData data, PlayerPower playerPower, Player player, boolean perk) {
		boolean learned = data.isSkillUnlocked(skill.name());
		int textureY = learned ? 52 : canLearn(skill, data, playerPower, player) ? 26 : 0;
		BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_SKILLS,
				x, y, TECHNIQUE_CARD_ICON_SIZE, TECHNIQUE_CARD_ICON_SIZE, 0,
				0, textureY, 26, 26, 256, 256, BlitFloat.NO_TINT);
		if (Objects.equals(selectedSkill, skill)) {
			BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_SKILLS,
					x, y, TECHNIQUE_CARD_ICON_SIZE, TECHNIQUE_CARD_ICON_SIZE, 0,
					26, 0, 26, 26, 256, 256, BlitFloat.NO_TINT);
		}
		BlitFloat.blit(gui.pose(), Minecraft.getInstance(), skillIconPath(skill.name()),
				x + 3, y + 3, 12, 12, 0, BlitFloat.NO_TINT);
		if (perk) {
			gui.fill(x + 1, y + 1, x + 4, y + 3, 0xFFFFD86A);
		}
	}

	private void renderDetails(GuiGraphics gui, HamonData data, PlayerPower playerPower, Player player,
			int mouseX, int mouseY) {
		int x = getWindowX(this) + DETAIL_X;
		int y = getWindowY(this) + DETAIL_Y;
		if (selectedSkill == null) {
			if (view == View.TECHNIQUE && selectedTechnique != null) {
				renderTechniqueDetails(gui, data, x, y, mouseX, mouseY);
			}
			else {
				drawWrapped(gui, Component.translatable("jojo_ripples.hamon.skills.not_selected"), x, y, DETAIL_WIDTH, DIM_TEXT_COLOR);
				if (player != null && player.isCreative()) {
					drawWrapped(gui, Component.translatable("jojo_ripples.player_power.skills.creative_hint"), x, y + 24, DETAIL_WIDTH, DIM_TEXT_COLOR);
				}
			}
			return;
		}

		boolean creative = player != null && player.isCreative();
		UnlockableSkill unlockable = data.getAllSkills().get(selectedSkill.name());
		ConditionCheck canLearn = unlockable != null ? unlockable.canUnlockFromMenu(playerPower, data) : ConditionCheck.NEGATIVE;
		boolean learned = data.isSkillUnlocked(selectedSkill.name());
		y = drawWrapped(gui, Component.translatable("hamonSkill." + selectedSkill.name() + ".name"), x, y, DETAIL_WIDTH, TEXT_COLOR) + 3;
		gui.drawString(font, learned ? Component.translatable("jojo_ripples.hamon.skills.learned")
				: (creative || canLearn.isPositive()) ? Component.translatable("hamon.learnButton")
						: Component.translatable("jojo_ripples.hamon.skills.locked"),
				x, y, learned ? LEARNED_COLOR : (creative || canLearn.isPositive()) ? TEXT_COLOR : LOCKED_COLOR, false);
		y += 13;
		y = drawWrapped(gui, Component.translatable("hamonSkill." + selectedSkill.name() + ".desc"), x, y, DETAIL_WIDTH, TEXT_COLOR) + 4;
		if (!selectedSkill.prerequisiteSkills().isEmpty()) {
			y = drawWrapped(gui, Component.translatable("jojo_ripples.hamon.skills.prerequisites",
					String.join(", ", selectedSkill.prerequisiteSkills())), x, y, DETAIL_WIDTH, DIM_TEXT_COLOR) + 3;
		}
		if (!selectedSkill.unlocksAbilities().isEmpty()) {
			y = drawWrapped(gui, Component.translatable("jojo_ripples.hamon.skills.unlocks",
					String.join(", ", selectedSkill.unlocksAbilities())), x, y, DETAIL_WIDTH, DIM_TEXT_COLOR) + 3;
		}
		Component warning = canLearn.getWarning();
		if (!learned && warning != null && !creative) {
			drawWrapped(gui, warning.plainCopy().withStyle(ChatFormatting.RED), x, y, DETAIL_WIDTH, LOCKED_COLOR);
		}
	}

	private void renderTechniqueDetails(GuiGraphics gui, HamonData data, int x, int y, int mouseX, int mouseY) {
		gui.drawString(font, Component.translatable("hamon.technique." + selectedTechnique.name()), x, y, TEXT_COLOR, false);
		y += 14;
		HamonTechnique current = data.getCharacterTechnique();
		if (current != null && current.getName().equals(selectedTechnique.name())) {
			gui.drawString(font, Component.translatable("jojo_ripples.hamon.skills.learned"), x, y, LEARNED_COLOR, false);
		}

		int slotsY = getWindowY(this) + TECHNIQUE_SLOTS_Y;
		renderTechniqueSlots(gui, data, x, slotsY, mouseX, mouseY);
		int slotRows = (HamonData.techniqueSlotsCount() + TECHNIQUE_SLOTS_PER_ROW - 1) / TECHNIQUE_SLOTS_PER_ROW;
		y = slotsY + slotRows * (TECHNIQUE_SLOT_SIZE + TECHNIQUE_SLOT_GAP) + 3;
		if (!selectedTechnique.branchEfficiencies().isEmpty()) {
			for (var entry : selectedTechnique.branchEfficiencies().entrySet()) {
				Component line = Component.literal("+" + Math.round(entry.getValue() * 100.0F) + "% ")
						.append(branchTitle(entry.getKey()));
				gui.drawString(font, line, x, y, TEXT_COLOR, false);
				y += 11;
			}
		}
	}

	private void renderTechniqueSlots(GuiGraphics gui, HamonData data, int x, int y, int mouseX, int mouseY) {
		boolean nextFreeSlot = true;
		int hoveredIndex = -1;
		TechniqueSlotState hoveredState = null;
		HamonSkillDefinition hoveredSkill = null;
		for (int i = 0; i < HamonData.techniqueSlotsCount(); i++) {
			int slotX = x + (i % TECHNIQUE_SLOTS_PER_ROW) * (TECHNIQUE_SLOT_SIZE + TECHNIQUE_SLOT_GAP);
			int slotY = y + (i / TECHNIQUE_SLOTS_PER_ROW) * (TECHNIQUE_SLOT_SIZE + TECHNIQUE_SLOT_GAP);
			HamonSkillDefinition skill = techniqueSlotSkill(data, i);
			TechniqueSlotState state;
			if (!data.hasTechniqueLevel(i)) {
				state = TechniqueSlotState.LOCKED;
			}
			else if (skill != null) {
				state = TechniqueSlotState.HAS_SKILL;
			}
			else if (nextFreeSlot) {
				state = TechniqueSlotState.EMPTY_NEXT;
				nextFreeSlot = false;
			}
			else {
				state = TechniqueSlotState.EMPTY;
			}

			BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_SKILLS,
					slotX, slotY, 28, 28, 0,
					0, 156, 28, 28, 256, 256, BlitFloat.NO_TINT);
			switch (state) {
			case LOCKED -> BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_SKILLS,
					slotX + 1, slotY + 1, 26, 26, 0,
					52, 0, 26, 26, 256, 256, BlitFloat.NO_TINT);
			case EMPTY_NEXT -> BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_SKILLS,
					slotX + 1, slotY + 1, 26, 26, 0,
					0, 26, 26, 26, 256, 256, BlitFloat.NO_TINT);
			case EMPTY -> BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_SKILLS,
					slotX + 1, slotY + 1, 26, 26, 0,
					0, 0, 26, 26, 256, 256, BlitFloat.NO_TINT);
			case HAS_SKILL -> {
				BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_SKILLS,
						slotX + 1, slotY + 1, 26, 26, 0,
						0, 52, 26, 26, 256, 256, BlitFloat.NO_TINT);
				BlitFloat.blit(gui.pose(), Minecraft.getInstance(), skillIconPath(skill.name()),
						slotX + 6, slotY + 6, SKILL_ICON_SIZE, SKILL_ICON_SIZE, 0, BlitFloat.NO_TINT);
			}
			}

			if (mouseX >= slotX && mouseX < slotX + TECHNIQUE_SLOT_SIZE
					&& mouseY >= slotY && mouseY < slotY + TECHNIQUE_SLOT_SIZE) {
				hoveredIndex = i;
				hoveredState = state;
				hoveredSkill = skill;
			}
		}

		if (hoveredState == TechniqueSlotState.LOCKED) {
			gui.renderComponentTooltip(font, List.of(Component.translatable("hamon.technique_slot.locked",
					HamonData.techniqueSkillRequirement(hoveredIndex))), mouseX, mouseY);
		}
		else if (hoveredState == TechniqueSlotState.EMPTY_NEXT) {
			gui.renderComponentTooltip(font, List.of(Component.translatable("hamon.technique_slot.free")), mouseX, mouseY);
		}
		else if (hoveredState == TechniqueSlotState.HAS_SKILL && hoveredSkill != null) {
			gui.renderComponentTooltip(font, List.of(
					Component.translatable("hamonSkill." + hoveredSkill.name() + ".name"),
					Component.translatable("hamonSkill." + hoveredSkill.name() + ".desc")), mouseX, mouseY);
		}
	}

	@Nullable
	private HamonSkillDefinition techniqueSlotSkill(HamonData data, int slotIndex) {
		HamonTechnique current = data.getCharacterTechnique();
		if (current == null) {
			return null;
		}
		int learnedIndex = 0;
		for (String skillName : current.getSkillIds()) {
			if (data.isSkillUnlocked(skillName)) {
				if (learnedIndex == slotIndex) {
					return ModHamonSkills.definitionFor(skillName);
				}
				learnedIndex++;
			}
		}
		return null;
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
	private TechniqueCard getHoveredTechniqueCard(List<TechniqueCard> cards, double mouseX, double mouseY) {
		int x = getWindowX(this) + LIST_X;
		int y = getWindowY(this) + LIST_Y;
		if (mouseX < x || mouseX > x + LIST_WIDTH) {
			return null;
		}
		int rowY = listScrolling.getYHovered(y, (int) mouseY);
		if (rowY < 0) {
			return null;
		}
		for (TechniqueCard card : cards) {
			if (rowY >= card.y && rowY < card.y + card.height) {
				return card;
			}
		}
		return null;
	}

	@Nullable
	private HamonSkillDefinition getHoveredTechniqueCardSkill(@Nullable TechniqueCard card,
			double mouseX, double mouseY) {
		if (card == null) {
			return null;
		}
		int listX = getWindowX(this) + LIST_X;
		int listY = getWindowY(this) + LIST_Y;
		int contentY = listScrolling.getYHovered(listY, (int) mouseY);
		int localX = (int) mouseX - listX - 3;
		int localY = contentY - card.y - 18;
		int stride = TECHNIQUE_CARD_ICON_SIZE + TECHNIQUE_CARD_ICON_GAP;
		if (localX < 0 || localY < 0) {
			return null;
		}
		int column = localX / stride;
		int row = localY / stride;
		if (column >= TECHNIQUE_CARD_ICONS_PER_ROW
				|| localX % stride >= TECHNIQUE_CARD_ICON_SIZE
				|| localY % stride >= TECHNIQUE_CARD_ICON_SIZE) {
			return null;
		}
		int index = row * TECHNIQUE_CARD_ICONS_PER_ROW + column;
		List<HamonSkillDefinition> skills = techniqueCardSkills(card.technique);
		return index < skills.size() ? skills.get(index) : null;
	}

	private int getHoveredTechniqueSlot(double mouseX, double mouseY) {
		int x = getWindowX(this) + DETAIL_X;
		int y = getWindowY(this) + TECHNIQUE_SLOTS_Y;
		int stride = TECHNIQUE_SLOT_SIZE + TECHNIQUE_SLOT_GAP;
		int localX = (int) mouseX - x;
		int localY = (int) mouseY - y;
		if (localX < 0 || localY < 0
				|| localX % stride >= TECHNIQUE_SLOT_SIZE
				|| localY % stride >= TECHNIQUE_SLOT_SIZE) {
			return -1;
		}
		int column = localX / stride;
		int row = localY / stride;
		if (column >= TECHNIQUE_SLOTS_PER_ROW) {
			return -1;
		}
		int index = row * TECHNIQUE_SLOTS_PER_ROW + column;
		return index < HamonData.techniqueSlotsCount() ? index : -1;
	}

	@Nullable
	private HamonTechniqueDefinition techniqueDefinition(String name) {
		for (HamonTechniqueDefinition technique : ModHamonSkills.TECHNIQUE_DEFINITIONS) {
			if (technique.name().equals(name)) {
				return technique;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (clickTab(mouseX, mouseY, button, this)) {
			return true;
		}
		if (button == 0) {
			Player player = Minecraft.getInstance().player;
			HamonData data = player != null ? PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElse(null) : null;
			if (data != null) {
				if (view != View.TECHNIQUE) {
					SkillNode node = getHoveredSkillNode(buildSkillNodes(), mouseX, mouseY);
					if (node != null) {
						selectedSkill = node.skill;
						selectedTechnique = null;
						return true;
					}
				}
				else {
					int slotIndex = getHoveredTechniqueSlot(mouseX, mouseY);
					HamonSkillDefinition slotSkill = slotIndex >= 0 ? techniqueSlotSkill(data, slotIndex) : null;
					if (slotSkill != null) {
						selectedSkill = slotSkill;
						HamonTechnique current = data.getCharacterTechnique();
						if (current != null) {
							selectedTechnique = techniqueDefinition(current.getName());
						}
						return true;
					}

					List<TechniqueCard> cards = buildTechniqueCards();
					TechniqueCard card = getHoveredTechniqueCard(cards, mouseX, mouseY);
					if (card != null) {
						selectedTechnique = card.technique;
						selectedSkill = getHoveredTechniqueCardSkill(card, mouseX, mouseY);
						return true;
					}
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (view == View.TECHNIQUE) {
			listScrolling.scroll(scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private static Component branchTitle(HamonSkillBranch branch) {
		return Component.translatable(switch (branch) {
		case OVERDRIVE -> "hamon.skills.overdrive";
		case INFUSION -> "hamon.skills.infusion";
		case FLEXIBILITY -> "hamon.skills.flexibility";
		case HEALING -> "hamon.skills.life";
		case ATTRACTANT_REPELLENT -> "hamon.skills.attractant_repellent";
		case BODY_MANIPULATION -> "hamon.skills.body_manipulation";
		case CHARACTER_TECHNIQUE -> "hamon.techniques.tab";
		});
	}

	public enum View {
		STRENGTH(Component.translatable("hamon.strength_skills.tab"), HamonData.HamonStat.STRENGTH, HamonSkillsTab.STRENGTH,
				HamonSkillBranch.OVERDRIVE, HamonSkillBranch.INFUSION, HamonSkillBranch.FLEXIBILITY),
		CONTROL(Component.translatable("hamon.control_skills.tab"), HamonData.HamonStat.CONTROL, HamonSkillsTab.CONTROL,
				HamonSkillBranch.HEALING, HamonSkillBranch.ATTRACTANT_REPELLENT, HamonSkillBranch.BODY_MANIPULATION),
		TECHNIQUE(Component.translatable("hamon.techniques.tab"), null, HamonSkillsTab.TECHNIQUE,
				HamonSkillBranch.CHARACTER_TECHNIQUE);

		private final Component title;
		@Nullable private final HamonData.HamonStat stat;
		@Nullable private final HamonSkillsTab resetTab;
		private final HamonSkillBranch[] branches;

		private View(Component title, @Nullable HamonData.HamonStat stat, @Nullable HamonSkillsTab resetTab, HamonSkillBranch... branches) {
			this.title = title;
			this.stat = stat;
			this.resetTab = resetTab;
			this.branches = branches;
		}

		private Component title() {
			return title;
		}
	}

	private static enum TechniqueSlotState {
		LOCKED,
		EMPTY_NEXT,
		EMPTY,
		HAS_SKILL
	}

	private static final class SkillNode {
		private final HamonSkillDefinition skill;
		private final int x;
		private final int y;
		private final boolean finalSkill;

		private SkillNode(HamonSkillDefinition skill, int x, int y, boolean finalSkill) {
			this.skill = skill;
			this.x = x;
			this.y = y;
			this.finalSkill = finalSkill;
		}
	}

	private static final class TechniqueCard {
		private final HamonTechniqueDefinition technique;
		private final int y;
		private final int height;

		private TechniqueCard(HamonTechniqueDefinition technique, int y, int height) {
			this.technique = technique;
			this.y = y;
			this.height = height;
		}
	}
}
