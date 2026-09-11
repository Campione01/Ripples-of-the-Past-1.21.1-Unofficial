package com.github.standobyte.jojo.api.client.render;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class ClientSkyPresentationProvidersSmokeTest {
	private ClientSkyPresentationProvidersSmokeTest() {}

	public static void main(String[] args) {
		run();
		System.out.println("Client sky presentation smoke tests passed: provider selection, "
				+ "Iris time inputs, nonlinear angle conversion and inactive cleanup");
	}

	public static void run() {
		ClientSkyPresentationProviders.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation active = id("active");

		ClientSkyPresentationProviders.register(failed, query -> {
			throw new IllegalStateException("expected smoke failure");
		});
		ClientSkyPresentationProviders.register(
				active,
				query -> new ClientSkyPresentation() {
					@Override
					public float timeOfDay(
							float original,
							float partialTick) {
						return 0.25F;
					}

					@Override
					public float skyDarken(
							float original,
							float partialTick) {
						return 1.0F;
					}

					@Override
					public float starBrightness(
							float original,
							float partialTick) {
						return 0.0F;
					}

					@Override
					public Vec3 skyColor(
							Vec3 original,
							Vec3 cameraPosition,
							float partialTick) {
						return new Vec3(0.56D, 0.75D, 1.0D);
					}
				});

		check(ClientSkyPresentationProviders.timeOfDay(
						null, 0.5F, 0.75F)
				== 0.25F,
				"sky time presentation was not applied");
		check(ClientSkyPresentationProviders.skyDarken(
						null, 0.5F, 0.1F)
				== 1.0F,
				"sky-darken presentation was not applied");
		check(ClientSkyPresentationProviders.starBrightness(
						null, 0.5F, 1.0F)
				== 0.0F,
				"star-brightness presentation was not applied");
		check(ClientSkyPresentationProviders.skyColor(
						null,
						Vec3.ZERO,
						0.5F,
						Vec3.ZERO)
				.equals(new Vec3(0.56D, 0.75D, 1.0D)),
				"sky-color presentation was not applied");
		check(ClientSkyPresentationProviders.registeredOwners()
						.equals(List.of(failed, active)),
				"sky presentation provider order changed");
		expectIllegalState(() ->
				ClientSkyPresentationProviders.register(
						active, query -> null));
		ClientSkyPresentationProviders.resetForTests();
		verifyShaderPresentation();
	}

	private static void verifyShaderPresentation() {
		for (int raw : new int[] { -1, 0, 6000, 18000, 24000, Integer.MAX_VALUE }) {
			check(ClientSkyPresentationProviders.shaderWorldTime(null, 0.5F, raw, 0.5F) == raw,
					"no-provider shader time changed the original Iris value");
		}
		ClientSkyPresentationProviders.register(id("identity"), query -> new ClientSkyPresentation() {});
		check(ClientSkyPresentationProviders.shaderWorldTime(null, 0.5F, 18000, 0.5F) == 18000,
				"a provider without a time override changed shader time");
		check(ClientSkyPresentationProviders.shaderTimeOfDay(null, 0.5F, 0.812345F) == 0.812345F,
				"an unchanged shader sky angle was recomputed");
		ClientSkyPresentationProviders.resetForTests();

		boolean[] active = { true };
		float[] angle = { 0.0F };
		ClientSkyPresentation presentation = new ClientSkyPresentation() {
			@Override
			public float timeOfDay(float original, float partialTick) {
				return angle[0];
			}
		};
		ClientSkyPresentationProviders.register(id("shader_daylight"), query -> active[0] ? presentation : null);
		try {
			check(ClientSkyPresentationProviders.shaderTimeOfDay(null, 0.5F, 0.5F) == 0.0F,
					"daylight presentation did not override the Iris celestial angle");
			check(ClientSkyPresentationProviders.shaderWorldTime(null, 0.5F, 18000, 0.5F) == 6000,
					"noon angle zero must map to 6000 ticks, not midnight");
			angle[0] = 0.25F;
			check(ClientSkyPresentationProviders.shaderWorldTime(null, 0.5F, 18000, 0.5F) == 12785,
					"quarter sky angle must invert the daylight curve instead of multiplying by 24000");
			for (int tick = 0; tick < 24000; tick++) {
				double phase = tick / 24000.0D - 0.25D;
				phase -= Math.floor(phase);
				float originalAngle = (float) (2.0D * phase
						+ 0.5D - Math.cos(Math.PI * phase) * 0.5D) / 3.0F;
				check(ClientSkyPresentationProviders.worldTimeFromSkyAngle(-1, -1.0F, originalAngle) == tick,
						"sky angle conversion failed round trip at tick " + tick);
			}
			for (float invalid : new float[] { Float.NaN, Float.POSITIVE_INFINITY,
					Float.NEGATIVE_INFINITY, -0.1F, 1.0F, 2.0F }) {
				angle[0] = invalid;
				check(ClientSkyPresentationProviders.shaderTimeOfDay(null, 0.5F, 0.5F) == 0.5F
						&& ClientSkyPresentationProviders.shaderWorldTime(null, 0.5F, 18000, 0.5F) == 18000,
						"invalid presentation contaminated Iris time inputs");
			}
			angle[0] = 0.5F;
			check(ClientSkyPresentationProviders.shaderWorldTime(null, 0.5F, 12345, 0.5F) == 12345,
					"unchanged angle must preserve the supplied Iris time exactly");
			active[0] = false;
			angle[0] = 0.0F;
			check(ClientSkyPresentationProviders.shaderTimeOfDay(null, 0.5F, 0.5F) == 0.5F
					&& ClientSkyPresentationProviders.shaderWorldTime(null, 0.5F, 18000, 0.5F) == 18000,
					"leaving the daylight provider's range retained a stale override");
		}
		finally {
			ClientSkyPresentationProviders.resetForTests();
		}
		check(ClientSkyPresentationProviders.shaderWorldTime(null, 0.5F, 18000, 0.5F) == 18000,
				"clearing providers retained shader time state");
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
				"duplicate sky presentation provider was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
