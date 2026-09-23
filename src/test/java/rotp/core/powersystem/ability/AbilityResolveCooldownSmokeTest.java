package rotp.core.powersystem.ability;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;

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

		verifyCooldownArithmetic(type, plain, split, halved, technical);
		verifyResolveOnlyForStandUsers(plain, split, halved);
	}

	// The value getCooldown returns for a Stand user out of and in Resolve.
	private static void verifyCooldownArithmetic(AbilityType<Ability> type,
			Ability plain, Ability split, Ability halved, Ability technical) {
		check(plain.cooldownTicksFor(false) == 300
				&& plain.cooldownTicksFor(true) == 0,
				"cooldown(n) must be n out of Resolve and 0 in Resolve");
		check(split.cooldownTicksFor(false) == 80
				&& split.cooldownTicksFor(true) == 20,
				"cooldown(t, a) must be t + a out of Resolve and only t in Resolve");
		check(halved.cooldownTicksFor(false) == 125
				&& halved.cooldownTicksFor(true) == 75,
				"cooldown(t, a, 0.5F) must halve only the additional part in Resolve");
		check(ability(type, "odd").cooldown(10, 5, 0.5F).cooldownTicksFor(true) == 12,
				"the multiplied additional part must truncate like 1.16's (int) cast");
		check(technical.cooldownTicksFor(false) == 30
				&& technical.cooldownTicksFor(true) == 30,
				"a technical-only cooldown must not change in Resolve");
		check(ability(type, "kept").cooldown(0, 40, 1.0F).cooldownTicksFor(true) == 40,
				"multiplier 1 must keep the whole cooldown in Resolve");
		Ability none = ability(type, "none");
		check(none.cooldownTicksFor(false) == 0
				&& none.cooldownTicksFor(true) == 0,
				"an ability without a cooldown must have none in or out of Resolve");
	}

	// Resolve counts only for a StandPower whose user has the effect.
	private static void verifyResolveOnlyForStandUsers(Ability plain, Ability split, Ability halved) {
		check(!Ability.isStandUserInResolve(null),
				"no power context must not count as Resolve");
		check(plain.getCooldown(null, -1) == 300,
				"no power context must pay the full cooldown");

		PlayerPower nonStand = allocate(PlayerPower.class);
		check(!Ability.isStandUserInResolve(nonStand),
				"a non-Stand power must never get the Resolve multiplier");
		check(plain.getCooldown(nonStand, -1) == 300
				&& split.getCooldown(nonStand, -1) == 80
				&& halved.getCooldown(nonStand, -1) == 125,
				"a non-Stand power must pay the full cooldown");

		StandPower userless = allocate(StandPower.class);
		check(!Ability.isStandUserInResolve(userless),
				"a Stand power without a user must not count as Resolve");
	}

	// A power without its entity: getCooldown's routing reads only the power's class and user.
	private static <T> T allocate(Class<T> powerType) {
		try {
			Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
			Field singleton = unsafeClass.getDeclaredField("theUnsafe");
			singleton.setAccessible(true);
			Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
			return powerType.cast(allocateInstance.invoke(singleton.get(null), powerType));
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError("could not allocate " + powerType.getSimpleName(), e);
		}
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
