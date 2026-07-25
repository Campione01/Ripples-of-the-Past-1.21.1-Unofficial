package com.github.standobyte.jojo.client.ui.text;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

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
		return text(standPower, standSkin, skill, ".controls");
	}
	
	public static Component text(@Nullable StandPower standPower, @Nullable StandSkin standSkin, UnlockableSkill skill, String suffix) {
		String baseKey = baseKey(skill, suffix);
		if (standSkin != null && standSkin.hasTranslation(baseKey)) {
			return standSkin.translatable(baseKey);
		}
		String standKey = standSkillKey(standPower, skill, suffix);
		if (standKey != null && Language.getInstance().has(standKey)) {
			return Component.translatable(standKey);
		}
		return Component.translatable(baseKey);
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
