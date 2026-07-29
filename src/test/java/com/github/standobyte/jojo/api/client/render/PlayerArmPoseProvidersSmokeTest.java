package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;

public final class PlayerArmPoseProvidersSmokeTest {
	private PlayerArmPoseProvidersSmokeTest() {}

	public static void run() {
		PlayerArmPoseProviders.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation active = id("active");
		List<String> postSetup = new ArrayList<>();

		PlayerArmPoseProviders.register(
				failed,
				new PlayerArmPoseProvider() {
					@Override
					public HumanoidModel.ArmPose armPose(
							PlayerArmPoseQuery query) {
						throw new IllegalStateException(
								"expected smoke failure");
					}

					@Override
					public void applyPostSetup(
							PlayerArmModelQuery query) {
						postSetup.add("failed-owner");
					}
				});
		PlayerArmPoseProviders.register(
				active,
				new PlayerArmPoseProvider() {
					@Override
					public HumanoidModel.ArmPose armPose(
							PlayerArmPoseQuery query) {
						return HumanoidModel.ArmPose.CROSSBOW_HOLD;
					}

					@Override
					public void applyPostSetup(
							PlayerArmModelQuery query) {
						postSetup.add("active-owner");
					}
				});

		check(PlayerArmPoseProviders.resolve(
						new PlayerArmPoseQuery(null, null, null))
				== HumanoidModel.ArmPose.CROSSBOW_HOLD,
				"arm-pose failure prevented the later provider");
		PlayerArmPoseProviders.applyPostSetup(
				new PlayerArmModelQuery(null, null));
		check(postSetup.equals(
						List.of("failed-owner", "active-owner")),
				"post-setup arm providers did not compose in order");
		check(PlayerArmPoseProviders.registeredOwners()
						.equals(List.of(failed, active)),
				"arm-pose provider order changed");
		expectIllegalState(() -> PlayerArmPoseProviders.register(
				active, new PlayerArmPoseProvider() {}));
		PlayerArmPoseProviders.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate arm-pose provider was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
