package com.github.standobyte.jojo.api.client.render;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class ClientSkyPresentationProvidersSmokeTest {
	private ClientSkyPresentationProvidersSmokeTest() {}

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
