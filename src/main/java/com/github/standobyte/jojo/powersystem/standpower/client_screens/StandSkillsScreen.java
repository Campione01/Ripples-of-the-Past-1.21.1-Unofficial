package com.github.standobyte.jojo.powersystem.standpower.client_screens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.client.standskin.sprites.AbilityIconSprites;
import com.github.standobyte.jojo.client.textsymbols.IconSymbols;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.IJojoMenuScreen;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.JojoMenuTabs;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.PaperButton;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.Tab;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.TabCategory;
import com.github.standobyte.jojo.client.ui.screen_widgets.ImageButton2;
import com.github.standobyte.jojo.client.ui.text.StandSkillText;
import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.client.ui.utils.GuiIcon;
import com.github.standobyte.jojo.client.ui.utils.Scrolling;
import com.github.standobyte.jojo.client.ui.utils.ScrollingText;
import com.github.standobyte.jojo.client.ui.utils.TextUtil;
import com.github.standobyte.jojo.client.ui.utils.tooltip.MutableTooltipWrapper;
import com.github.standobyte.jojo.client.ui.utils.tooltip.TooltipParams;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.TrainableAbility;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUnlockableSkill;
import com.github.standobyte.jojo.powersystem.standpower.type.StandTypePersistentData;
import com.github.standobyte.jojo.powersystem.standpower.type.StandTypePersistentData.StandExpSummary;
import com.github.standobyte.jojo.powersystem.unlockableskill.ClLearnSkillPacket;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill.DevStatus;
import com.google.common.collect.Iterables;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandSkillsScreen extends Screen implements IJojoMenuScreen {
	public static final ResourceLocation WINDOW = JojoMod.resLoc("textures/gui/paper_style/stand_skills.png");
	public static final GuiIcon SCROLL_BAR = new GuiIcon(WINDOW, 243, 58, 5, 162, 256, 256);
	public static final GuiIcon CROSS = new GuiIcon(JojoMod.resLoc("textures/gui/sprites/widget/cross8.png"), 8, 8);
	public static final GuiIcon CROSS_HIGHLIGHTED = new GuiIcon(JojoMod.resLoc("textures/gui/sprites/widget/cross8_highlighted.png"), 8, 8);
	
	protected TabCategory category;
	protected Tab tab;

	protected StandPower standPower;
	protected StandTypePersistentData levelingData;
	protected StandSkin standSkin;
	protected Iterable<StandUnlockableSkill> skills;
	@Nullable protected StandUnlockableSkill selectedSkill;
	
	protected Scrolling skillListScrolling;
	protected ScrollingText skillDescription;
	protected ScrollingText skillControls;
	
	protected Button learnSkillButton;
	protected Button trainSkillButton;
	protected Button resetSkillsButton;
	protected Button learnAllSkillsButton;
	protected MutableTooltipWrapper learnSkillTooltip;
	protected Map<String, ConditionCheck> unlockSkillChecks = new HashMap<>();
	
	protected Button deselectSkillButton;

	public StandSkillsScreen(Component title, TabCategory category, Tab tab) {
		this(title, category, tab,
				JojoMenuTabs.getPowerForMenu(PowerClass.STAND));
	}

	public StandSkillsScreen(
			Component title,
			TabCategory category,
			Tab tab,
			StandPower standPower) {
		super(title);
		this.category = category;
		this.tab = tab;
		this.standPower = standPower;
	}

	@Override
	public TabCategory getTabCategory() {
		return category;
	}

	@Override
	public Tab getTab() {
		return tab;
	}

	@Override
    protected void init() {
		int x = getWindowX(this);
		int y = getWindowY(this);
		
		levelingData = standPower.getCurTypeData();
		skills = standPower.getPowerType().getUnlockableSkills().values().stream()
				.filter(skill -> !skill.hidden)
				.toList();
		skillListScrolling = new Scrolling(162, Iterables.size(skills) * 20 + 2);
		standSkin = StandSkinsLoader.getInstance().getSkin(standPower);

		learnSkillButton = this.addRenderableWidget(new PaperButton(x + 144, y + 201, 80, 20, 
				Component.translatable("jojo_ripples.stand_skills.learn"), 
				button -> {
					if (standPower != null && standPower.hasPower() && selectedSkill != null) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.learnSkill(
								PowerClass.STAND, standPower.getPowerType().getId(), selectedSkill.skillName));
					}
				}));
		learnSkillButton.setTooltip(learnSkillTooltip = new MutableTooltipWrapper() {
			@Override
			public Tooltip updateToolip() {
				if (selectedSkill != null) {
					ConditionCheck canLearn = unlockSkillChecks.get(selectedSkill.skillName);
					if (!canLearn.isPositive()) {
						Component message = canLearn.getWarning();
						if (message != null) {
							return Tooltip.create(message.plainCopy().withStyle(ChatFormatting.RED));
						}
					}
				}
				return null;
			}
			
		});
		
		trainSkillButton = this.addRenderableWidget(new PaperButton(x + 144, y + 201, 80, 20,
				Component.translatable("jojo_ripples.stand_skills.train"),
				button -> {
					if (standPower != null && standPower.hasPower() && selectedSkill != null && minecraft.player != null && minecraft.player.isCreative()) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.trainSkill(
								PowerClass.STAND, standPower.getPowerType().getId(), selectedSkill.skillName));
					}
				}));
		trainSkillButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.stand_skills.train.creative_only")));
		
		resetSkillsButton = this.addRenderableWidget(new PaperButton(x + 154, y + 201, 70, 20, 
				Component.translatable("jojo_ripples.stand_skills.reset"), 
				button -> {
					if (standPower != null && standPower.hasPower()) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.resetAll(
								PowerClass.STAND, standPower.getPowerType().getId()));
					}
				}));
		resetSkillsButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.note.creative_only")));
		
		learnAllSkillsButton = this.addRenderableWidget(new PaperButton(x + 80, y + 201, 70, 20, 
				Component.translatable("jojo_ripples.stand_skills.learn_all"), 
				button -> {
					if (standPower != null && standPower.hasPower()) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.learnAll(
								PowerClass.STAND, standPower.getPowerType().getId()));
					}
				}));
		learnAllSkillsButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.note.creative_only")));
		
		skillDescription = new ScrollingText(x + 86, y + 87, 124, 105);
		skillControls = new ScrollingText(x + 100, y + 49, 117, 31);
		setSelectedSkill(this.selectedSkill);
		
		deselectSkillButton = addRenderableWidget(new ImageButton2(x + 68, y + 23, 8, 8, 
				CROSS, CROSS, CROSS_HIGHLIGHTED, CROSS_HIGHLIGHTED, 
				button -> setSelectedSkill(null)) {
			@Override public void playDownSound(SoundManager handler) {}
		});
	}

	protected static final int SKILL_LIST_X = 22;
	protected static final int SKILL_LIST_Y = 57;
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float p_283123_) {
		deselectSkillButton.visible = selectedSkill != null;
		StandExpSummary expSummary = levelingData.expSummary(standPower);
		
		this.renderBackground(guiGraphics, mouseX, mouseY, p_283123_);
		
		for (StandUnlockableSkill skill : skills) {
			unlockSkillChecks.put(skill.skillName, skill.canUnlockFromMenu(standPower, levelingData));
		}
		
		learnSkillButton.visible = selectedSkill != null && !levelingData.isSkillUnlocked(selectedSkill.skillName);
		learnSkillButton.active = selectedSkill != null && unlockSkillChecks.get(selectedSkill.skillName).isPositive();
		TrainableAbility selectedTrainable = getSelectedTrainableAbility();
		boolean selectedSkillUnlocked = selectedSkill != null && levelingData.isSkillUnlocked(selectedSkill.skillName);
		boolean canDirectTrain = minecraft.player != null && minecraft.player.isCreative();
		trainSkillButton.visible = selectedSkillUnlocked && selectedTrainable != null && canDirectTrain;
		trainSkillButton.active = trainSkillButton.visible && !isTrainingComplete(selectedTrainable);
		
		resetSkillsButton.visible = selectedSkill == null && minecraft.player.isCreative();
		learnAllSkillsButton.visible = selectedSkill == null && minecraft.player.isCreative();
		
		int x = getWindowX(this);
		int y = getWindowY(this);
		int width = getWindowWidth();
		int height = getWindowHeight();
		BlitFloat.blit(guiGraphics.pose(), Minecraft.getInstance(), WINDOW, 
				x, y, width, height, 0, 
				0, 0, width, height, 256, 256, 
				BlitFloat.NO_TINT);
//		int textColor = standSkin.getColor();
		int textColor = 0xFF000000;

		int skillListX = x + SKILL_LIST_X;
		int skillListY = y + SKILL_LIST_Y;
		skillListScrolling.pushOffsetScissor(guiGraphics, skillListY + 1, skillListX + 1, skillListX + 60);
		
		int spriteX = skillListX + 2;
		int spriteY = skillListY + 4;
		AbilityIconSprites abilityIconSprites = StandSkinsLoader.getInstance().abilityIcons;
		UnlockableSkill hovered = getHoveredSkill(mouseX, mouseY);
		
		for (StandUnlockableSkill skill : skills) {
			boolean isUnlocked = levelingData.isSkillUnlocked(skill.skillName);
			int expCostColor = !isUnlocked ? getExpCostColor(skill) : STAND_EXP_NUMBER_COLOR;
			boolean unlockedOrOnlyMissingStandExp = expCostColor == STAND_EXP_NUMBER_COLOR;
			int skillSpriteColor = unlockedOrOnlyMissingStandExp ? BlitFloat.NO_TINT : 0x40404040;
			
			TextureAtlasSprite icon = abilityIconSprites.getAbilityIcon(skill.skillName, standSkin);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			BlitFloat.blit(guiGraphics.pose(), minecraft, icon, 
					spriteX, spriteY, 16, 16, 0, skillSpriteColor);
			
			if (isUnlocked) {
				guiGraphics.drawString(font, String.valueOf(IconSymbols.CHECKMARK), spriteX + 18, spriteY + 4, 0xFFFFFFFF);
			}
			else {
				int expToUnlock = skill.expToUnlock;
				String expCostLine = expToUnlock > 0 ? String.valueOf(expToUnlock) : "-";
				guiGraphics.drawString(font, expCostLine, spriteX + 18, spriteY + 4, expCostColor, false);
			}
			if (skill.implemented == DevStatus.NYI) {
				guiGraphics.fill(spriteX - 1, spriteY - 1, spriteX + 37, spriteY + 17, 0x80FF0000);
			}
			spriteY += 20;
		}

		skillListScrolling.pop(guiGraphics);
		skillListScrolling.renderScrollBar(skillListX - 8, skillListY + 1, 0, 4, guiGraphics, SCROLL_BAR, 1);

		if (selectedSkill != null) {
			updateSelectedSkillText(selectedTrainable);
			TextUtil.drawRightAlignedString(guiGraphics, font, skinSkillName(selectedSkill), 
					x + getWindowWidth() - 10, y + 24, textColor, false);
			
			skillDescription.draw(4, 3, guiGraphics, this.minecraft.font, textColor, false);
			skillDescription.drawSmallScrollBar(guiGraphics);
			skillControls.draw(4, 3, guiGraphics, this.minecraft.font, textColor, false);
			skillControls.drawSmallScrollBar(guiGraphics);
		}
		
		int maxExp = expSummary.total - expSummary.spent;
		int exp = Math.min(levelingData.getExp(), maxExp);
		Component expLine = exp < maxExp ? Component.literal(String.valueOf(exp)) : Component.translatable("jojo_ripples.stand_exp.max");
		expLine = Component.literal(String.valueOf(IconSymbols.STAND_EXP)).append(expLine);
		guiGraphics.drawString(font, expLine, x + 41 - font.width(expLine) / 2, y + 33, STAND_EXP_NUMBER_COLOR, false);
		
		if (learnSkillButton.visible && selectedSkill != null) {
			int expCostColor = getExpCostColor(selectedSkill);
			if (expCostColor == STAND_EXP_NUMBER_COLOR) {
				Component expCost = Component.literal(IconSymbols.STAND_EXP + " " + String.valueOf(selectedSkill.expToUnlock));
				guiGraphics.drawString(font, expCost, 
						learnSkillButton.getX() - 4 - font.width(expCost), learnSkillButton.getY() + 6, 
						expCostColor, false);
			}
		}
		
		renderTabs(guiGraphics, this);
		
		if (hovered != null) {
			TooltipParams.set(TooltipParams.paperStyle(1));
			List<FormattedCharSequence> skillNameTooltip = new ArrayList<>();
			skillNameTooltip.add(skinSkillName(hovered).copy().withStyle(ChatFormatting.BLACK).getVisualOrderText());
			switch (hovered.implemented) {
				case WIP -> skillNameTooltip.add(Component.translatable("rotp_tag_wip")
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC).getVisualOrderText());
				case NYI -> skillNameTooltip.add(Component.translatable("rotp_tag_nyi")
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC).getVisualOrderText());
				default -> {}
			}
			setTooltipForNextRenderPass(skillNameTooltip);
		}
		else if (mouseX - x >= 22 && mouseX - x <= 59 && mouseY - y >= 30 && mouseY - y <= 43) {
			TooltipParams.set(TooltipParams.paperStyle(1));
			List<FormattedCharSequence> standExpTooltip = new ArrayList<>();
			standExpTooltip.add(Component.translatable("jojo_ripples.stand_exp")
					.withStyle(ChatFormatting.BLACK).getVisualOrderText());
			standExpTooltip.add(Component.translatable("jojo_ripples.stand_exp.total", expSummary.spent + exp, expSummary.total)
					.withStyle(ChatFormatting.BLACK).getVisualOrderText());
			if (expSummary.remainingHiddenSkills > 0) {
				standExpTooltip.add(Component.translatable("jojo_ripples.stand_exp.skills_left.hidden", expSummary.remainingSkills, expSummary.remainingHiddenSkills)
						.withStyle(ChatFormatting.BLACK).getVisualOrderText());
			}
			else {
				standExpTooltip.add(Component.translatable("jojo_ripples.stand_exp.skills_left", expSummary.remainingSkills)
						.withStyle(ChatFormatting.BLACK).getVisualOrderText());
			}
			standExpTooltip.addAll(Tooltip.splitTooltip(minecraft, Component.translatable("jojo_ripples.stand_exp.desc")
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
			setTooltipForNextRenderPass(standExpTooltip);
		}
		else {
			renderTabTooltip(guiGraphics, this, mouseX, mouseY);
		}

		for (Renderable renderable : this.renderables) {
			renderable.render(guiGraphics, mouseX, mouseY, p_283123_);
		}
	}
	
	public static final int STAND_EXP_NUMBER_COLOR = 0x00A000;
	protected Component skinSkillName(UnlockableSkill skill) {
		return StandSkillText.name(standPower, standSkin, skill);
	}
	
	protected Component skinSkillDesc(UnlockableSkill skill) {
		return StandSkillText.desc(standPower, standSkin, skill);
	}
	
	protected Component skinSkillControls(UnlockableSkill skill) {
		return StandSkillText.controls(standPower, standSkin, skill);
	}

	@Nullable
	protected TrainableAbility getSelectedTrainableAbility() {
		return selectedSkill != null ? getTrainableAbility(selectedSkill) : null;
	}

	@Nullable
	protected TrainableAbility getTrainableAbility(StandUnlockableSkill skill) {
		if (standPower == null || standPower.getMoveset() == null || skill == null) {
			return null;
		}
		Set<String> abilityNames = new LinkedHashSet<>();
		abilityNames.add(skill.skillName);
		abilityNames.addAll(skill.unlocksAbilities);
		for (String abilityName : abilityNames) {
			Ability ability = standPower.getMoveset().getAbility(abilityName);
			if (ability instanceof TrainableAbility trainable
					&& trainable.getLearningAbilityName() != null
					&& trainable.getMaxTrainingPoints(standPower) > 0.0F) {
				return trainable;
			}
		}
		return null;
	}

	protected boolean isTrainingComplete(TrainableAbility trainable) {
		String learningAbilityName = trainable.getLearningAbilityName();
		if (learningAbilityName == null) {
			return true;
		}
		float maxTraining = trainable.getMaxTrainingPoints(standPower);
		return levelingData.getAbilityLearningProgressPoints(learningAbilityName) >= maxTraining;
	}

	protected void updateSelectedSkillText(@Nullable TrainableAbility trainable) {
		if (selectedSkill == null) {
			return;
		}
		Component desc = skinSkillDesc(selectedSkill);
		if (trainable != null && levelingData.isSkillUnlocked(selectedSkill.skillName)) {
			desc = desc.copy().append("\n\n").append(trainingProgressText(trainable));
		}
		skillDescription.setText(font.split(desc, 111));
	}

	protected Component trainingProgressText(TrainableAbility trainable) {
		String learningAbilityName = trainable.getLearningAbilityName();
		float maxTraining = trainable.getMaxTrainingPoints(standPower);
		float points = learningAbilityName != null ? Math.max(0.0F, levelingData.getAbilityLearningProgressPoints(learningAbilityName)) : 0.0F;
		int percent = maxTraining > 0 ? Math.min(100, Math.round(Math.min(points, maxTraining) * 100.0F / maxTraining)) : 100;
		return Component.translatable("jojo_ripples.stand_skills.training_progress", percent);
	}
	
	protected int getExpCostColor(StandUnlockableSkill skill) {
		ConditionCheck check = unlockSkillChecks.get(skill.skillName);
		if (check != null && (check.isPositive() || check == StandUnlockableSkill.NOT_ENOUGH_EXP)) {
			return STAND_EXP_NUMBER_COLOR;
		}
		else {
			return 0xB0B0B0;
		}
	}
	
	@Nullable
	protected StandUnlockableSkill getHoveredSkill(double mouseX, double mouseY) {
		int x = getWindowX(this) + SKILL_LIST_X;
		int y = getWindowY(this) + SKILL_LIST_Y;
		if (mouseX >= x && mouseX <= x + 48) {
			int pos = skillListScrolling.getYHovered(y, (int) mouseY);
			if (pos >= 0) {
				int index = pos / 20;
				int pixel = pos % 20;
				if (pixel > 3 && index < Iterables.size(skills)) {
					return Iterables.get(skills, index);
				}
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (clickTab(mouseX, mouseY, button, this)) return true;
		StandUnlockableSkill skill = getHoveredSkill(mouseX, mouseY);
		if (skill != null) {
			setSelectedSkill(skill);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	public void setSelectedSkill(StandUnlockableSkill skill) {
		if (this.selectedSkill != skill) {
			skillDescription.scrolling.setScrollOffset(0);
			skillControls.scrolling.setScrollOffset(0);
		}
		this.selectedSkill = skill;
		if (skill != null) {
			updateSelectedSkillText(getSelectedTrainableAbility());
			skillControls.setText(font.split(skinSkillControls(selectedSkill), 101));
		}
		else {
			skillDescription.setText(null);
			skillControls.setText(null);
		}
		learnSkillButton.visible = skill != null;
	}

	@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (
				skillDescription.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || 
				skillControls.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
    	skillListScrolling.scroll(scrollY);
    	return true;
    }

}
