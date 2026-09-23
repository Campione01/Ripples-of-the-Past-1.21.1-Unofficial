package rotp.core.powersystem.ability;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.16's Action.Builder.cooldown(n) was cooldown(0, n) and StandAction's Resolve multiplier
 * defaulted to 0, so a plain Stand cooldown is gone while the user has Resolve. Only the
 * additional part is multiplied; an explicit multiplier and a technical part survive.
 */
public final class AbilityResolveCooldownSmokeTest {
	private AbilityResolveCooldownSmokeTest() {}

	public static void run() {
		AbilityType<Ability> type = new AbilityType<>(
				id("jojo_ripples", "resolve_cooldown_test"), Ability::new);

		Ability plain = ability(type, "plain").cooldown(300);
		check(plain.cooldownTechnicalTicks == 0
				&& plain.cooldownAdditionalTicks == 300
				&& plain.cooldownTicks == 300,
				"cooldown(n) must store n as the additional part");
		check(plain.cooldownResolveMultiplierApplies
				&& plain.cooldownResolveMultiplier == 0.0F,
				"cooldown(n) must be cleared by Resolve, as 1.16's default multiplier 0 did");

		Ability split = ability(type, "split").cooldown(20, 60);
		check(split.cooldownTechnicalTicks == 20
				&& split.cooldownAdditionalTicks == 60
				&& split.cooldownResolveMultiplierApplies
				&& split.cooldownResolveMultiplier == 0.0F,
				"cooldown(t, a) must keep t and clear a during Resolve");

		Ability halved = ability(type, "halved").cooldown(25, 100, 0.5F);
		check(halved.cooldownTechnicalTicks == 25
				&& halved.cooldownAdditionalTicks == 100
				&& halved.cooldownResolveMultiplier == 0.5F,
				"an explicit Resolve multiplier must be kept");

		Ability technical = ability(type, "technical").cooldown(30, 0);
		check(technical.cooldownTechnicalTicks == 30
				&& technical.cooldownAdditionalTicks == 0,
				"cooldown(n, 0) must stay a technical cooldown Resolve cannot shorten");

		Ability negative = ability(type, "negative").cooldown(-5);
		check(negative.cooldownTicks == 0
				&& negative.cooldownAdditionalTicks == 0,
				"a negative cooldown must clamp to zero");
	}

	private static Ability ability(AbilityType<Ability> type, String name) {
		return type.createInstance(new AbilityId(
				null, id("jojo_ripples", "test_stand"), name));
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
