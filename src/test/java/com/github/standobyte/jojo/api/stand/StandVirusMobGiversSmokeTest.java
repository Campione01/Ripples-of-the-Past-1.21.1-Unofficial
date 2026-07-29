package com.github.standobyte.jojo.api.stand;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class StandVirusMobGiversSmokeTest {
	private StandVirusMobGiversSmokeTest() {}

	public static void run() {
		ResourceLocation firstOwner =
				ResourceLocation.fromNamespaceAndPath("rotp_test", "first");
		ResourceLocation secondOwner =
				ResourceLocation.fromNamespaceAndPath("rotp_test", "second");
		StandVirusMobGiver first = giver(0.25F);
		StandVirusMobGiver second = giver(0.75F);

		StandVirusMobGivers.resetForTests();
		try {
			StandVirusMobGivers.register(firstOwner, first);
			StandVirusMobGivers.register(secondOwner, second);

			check(StandVirusMobGivers.get(firstOwner).orElseThrow() == first,
					"registered giver must remain owner-addressable");
			check(StandVirusMobGivers.registeredOwners().equals(
					List.of(firstOwner, secondOwner)),
					"Stand-virus giver registration order must be stable");
			expectThrows(IllegalStateException.class,
					() -> StandVirusMobGivers.register(firstOwner, second),
					"duplicate owner registration must be rejected");
			expectThrows(NullPointerException.class,
					() -> StandVirusMobGivers.find(null),
					"null Stand-virus targets must be rejected");

			check(StandVirusMobGivers.class
					.getMethod("find", LivingEntity.class)
					.getReturnType() == Optional.class,
					"find must expose an Optional match contract");
			verifyImmutableContextSurface();
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Stand-virus API surface changed", error);
		}
		finally {
			StandVirusMobGivers.resetForTests();
		}
	}

	private static StandVirusMobGiver giver(float survivalChance) {
		return new StandVirusMobGiver() {
			@Override
			public boolean matches(LivingEntity target) {
				return false;
			}

			@Override
			public float survivalChance(StandVirusMobGiverContext context) {
				return survivalChance;
			}

			@Override
			public boolean giveStand(StandVirusMobGiverContext context) {
				return true;
			}
		};
	}

	private static void verifyImmutableContextSurface()
			throws ReflectiveOperationException {
		check(StandVirusMobGiverContext.class.isRecord(),
				"Stand-virus giver context must remain immutable");
		String[] componentNames = Arrays.stream(
				StandVirusMobGiverContext.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toArray(String[]::new);
		check(Arrays.equals(componentNames, new String[] {
				"owner",
				"target",
				"arrowItem",
				"arrowShooter",
				"amplifier",
				"baseDamage"
		}), "Stand-virus giver context components changed");
		check(StandVirusMobGiverContext.class.getMethod("arrowItem")
				.getReturnType() == ItemStack.class,
				"Stand-virus arrow item must remain copy-addressable");
	}

	private static void expectThrows(
			Class<? extends Throwable> expected,
			Runnable action,
			String message) {
		try {
			action.run();
		}
		catch (Throwable actual) {
			if (expected.isInstance(actual)) {
				return;
			}
			throw new AssertionError(message, actual);
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
