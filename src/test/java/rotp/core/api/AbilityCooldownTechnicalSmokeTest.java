package rotp.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import rotp.core.impl.powers.hamon.abilities.HamonZoomPunchAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanRibsBladesAbility;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

import net.minecraft.resources.ResourceLocation;

/**
 * FEATURE_ABILITY_COOLDOWN_TECHNICAL_V1: Ability#getCooldownTechnical(Power), 1.16's Action.getCooldownTechnical, a public
 * overridable int getter: the technical part of cooldown(technical, additional), 0 for a plain cooldown(n), while the full
 * cooldown stays technical + additional; the time stop, Zoom Punch and Ribs Blades override it
 * (AbilityCooldownTechnical116GameTests check the core's values).
 */
final class AbilityCooldownTechnicalSmokeTest {
	private AbilityCooldownTechnicalSmokeTest() {}

	static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_COOLDOWN_TECHNICAL_V1)
				&& "ability_cooldown_technical_v1".equals(RotpAddonApi.FEATURE_ABILITY_COOLDOWN_TECHNICAL_V1),
				"Ability cooldown technical feature missing");
		try {
			Method technical = Ability.class.getDeclaredMethod("getCooldownTechnical", Power.class);
			int modifiers = technical.getModifiers();
			check(Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)
					&& technical.getReturnType() == int.class,
					"Ability#getCooldownTechnical(Power) must be a public overridable int getter");
			for (Class<?> overriding : new Class<?>[] {
					TimeStopAbility.class, HamonZoomPunchAbility.class, PillarmanRibsBladesAbility.class }) {
				check(overriding.getDeclaredMethod("getCooldownTechnical", Power.class).getReturnType() == int.class,
						overriding.getSimpleName() + " must report 1.16's technical cooldown");
			}
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Ability cooldown technical API surface missing", error);
		}

		AbilityType<Ability> type = new AbilityType<>(
				ResourceLocation.fromNamespaceAndPath("rotp_test", "cooldown_split"), Ability::new);
		Ability plain = type.createInstance(new AbilityId(null, null, "plain"));
		plain.cooldown(100);
		check(plain.getCooldownTechnical(null) == 0 && plain.getCooldown(null, -1) == 100,
				"cooldown(n) is all additional: technical " + plain.getCooldownTechnical(null) + ", full "
						+ plain.getCooldown(null, -1));
		Ability split = type.createInstance(new AbilityId(null, null, "split"));
		split.cooldown(20, 60);
		check(split.getCooldownTechnical(null) == 20 && split.getCooldown(null, -1) == 80,
				"cooldown(20, 60): technical " + split.getCooldownTechnical(null) + ", full " + split.getCooldown(null, -1));
		Ability none = type.createInstance(new AbilityId(null, null, "none"));
		check(none.getCooldownTechnical(null) == 0 && none.getCooldown(null, -1) == 0,
				"an ability without a cooldown has no technical part");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
