package rotp.core.client.ui.text;

import javax.annotation.Nullable;

import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.unlockableskill.UnlockableSkill;

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
		if (!Language.getInstance().has(baseKey)) {
			// Add-on Stands ported from 1.16 have no skill screen texts: name the skill after its ability
			// and leave a missing description or controls line empty instead of showing the raw key.
			if (!suffix.isEmpty()) {
				return Component.empty();
			}
			Ability ability = skillAbility(standPower, skill);
			if (ability != null) {
				return ability.getName(standPower);
			}
		}
		return Component.translatable(baseKey, args);
	}

	@Nullable
	private static Ability skillAbility(@Nullable StandPower standPower, UnlockableSkill skill) {
		if (standPower == null || !standPower.hasPower()) {
			return null;
		}
		Moveset moveset = standPower.getMoveset();
		Ability ability = moveset.getAbility(skill.skillName);
		if (ability == null && !skill.unlocksAbilities.isEmpty()) {
			ability = moveset.getAbility(skill.unlocksAbilities.get(0));
		}
		return ability;
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
