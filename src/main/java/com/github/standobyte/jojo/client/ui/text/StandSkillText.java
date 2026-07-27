package com.github.standobyte.jojo.client.ui.text;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.ability.controls.InputUseVanillaMapping;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill;

import net.minecraft.client.KeyMapping;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;

public final class StandSkillText {
	private StandSkillText() {}

	public static Component name(@Nullable StandPower standPower, UnlockableSkill skill) {
		return text(standPower, getSkin(standPower), skill, "");
	}

	public static Component name(@Nullable StandPower standPower, @Nullable StandSkin standSkin, UnlockableSkill skill) {
		return text(standPower, standSkin, skill, "");
	}
	
	public static Component desc(@Nullable StandPower standPower, @Nullable StandSkin standSkin, UnlockableSkill skill) {
		return text(standPower, standSkin, skill, ".desc");
	}
	
	public static Component controls(@Nullable StandPower standPower, @Nullable StandSkin standSkin, UnlockableSkill skill) {
		if ("grab".equals(skill.skillName) || "heavy_charged".equals(skill.skillName)) {
			return text(standPower, standSkin, skill, ".controls", grabChargedHeavyKeyName());
		}
		return text(standPower, standSkin, skill, ".controls");
	}
	
	public static Component text(@Nullable StandPower standPower, @Nullable StandSkin standSkin, UnlockableSkill skill, String suffix) {
		return text(standPower, standSkin, skill, suffix, new Object[0]);
	}

	public static Component text(@Nullable StandPower standPower, @Nullable StandSkin standSkin,
			UnlockableSkill skill, String suffix, Object... args) {
		String baseKey = baseKey(skill, suffix);
		if (standSkin != null && standSkin.hasTranslation(baseKey)) {
			return standSkin.translatable(baseKey, skinFormatArgs(args));
		}
		String standKey = standSkillKey(standPower, skill, suffix);
		if (standKey != null && Language.getInstance().has(standKey)) {
			return Component.translatable(standKey, args);
		}
		return Component.translatable(baseKey, args);
	}

	static Object[] skinFormatArgs(Object[] args) {
		Object[] formatArgs = args.clone();
		for (int i = 0; i < formatArgs.length; i++) {
			if (formatArgs[i] instanceof Component component) {
				formatArgs[i] = component.getString();
			}
		}
		return formatArgs;
	}

	private static Component grabChargedHeavyKeyName() {
		InputUseVanillaMapping sharedInput =
				(InputUseVanillaMapping) MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT;
		KeyMapping keyMapping = sharedInput.toClientKeybind();
		if (keyMapping == null) {
			return Component.translatable(MovesetBuilder.GRAB_CHARGED_HEAVY_KEY_MAPPING_NAME);
		}
		KeyModifier modifier = keyMapping.getKeyModifier();
		return modifier != null && modifier != KeyModifier.NONE
				? modifier.getCombinedName(keyMapping.getKey(), keyMapping::getTranslatedKeyMessage)
				: keyMapping.getTranslatedKeyMessage();
	}
	
	public static String baseKey(UnlockableSkill skill, String suffix) {
		return "jojo_ripples.skill." + skill.skillName + suffix;
	}
	
	@Nullable
	public static String standSkillKey(@Nullable StandPower standPower, UnlockableSkill skill, String suffix) {
		if (standPower == null || !standPower.hasPower()) {
			return null;
		}
		return "jojo_ripples.skill." + standPower.getPowerType().getId().getPath() + "." + skill.skillName + suffix;
	}
	
	@Nullable
	private static StandSkin getSkin(@Nullable StandPower standPower) {
		StandSkinsLoader loader = StandSkinsLoader.getInstance();
		return loader != null ? loader.getSkin(standPower) : null;
	}
}
