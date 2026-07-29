package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed player arm-pose providers.
 *
 * <p>The first non-null vanilla pose wins in registration order. Final model
 * adjustments compose in registration order after {@link PlayerModel} has
 * copied its arm transforms to the sleeve parts. The registry retains no
 * player, model, level, or reload-scoped state.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class PlayerArmPoseProviders {
	private static final Map<ResourceLocation, PlayerArmPoseProvider>
			PROVIDERS = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private PlayerArmPoseProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			PlayerArmPoseProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate player arm-pose provider: " + owner);
		}
		publishSnapshot();
	}

	@Nullable
	@ApiStatus.Internal
	public static HumanoidModel.ArmPose resolve(
			AbstractClientPlayer player,
			InteractionHand hand) {
		return resolve(new PlayerArmPoseQuery(
				player, hand, player.getItemInHand(hand)));
	}

	@Nullable
	static HumanoidModel.ArmPose resolve(
			PlayerArmPoseQuery query) {
		for (Registration registration : snapshot) {
			try {
				HumanoidModel.ArmPose pose =
						registration.provider().armPose(query);
				if (pose != null) {
					return pose;
				}
			}
			catch (RuntimeException error) {
				logFailure(registration.owner(), "pose", error);
			}
		}
		return null;
	}

	@ApiStatus.Internal
	public static void applyPostSetup(
			Player player,
			PlayerModel<?> model) {
		applyPostSetup(new PlayerArmModelQuery(player, model));
	}

	static void applyPostSetup(PlayerArmModelQuery query) {
		for (Registration registration : snapshot) {
			try {
				registration.provider().applyPostSetup(query);
			}
			catch (RuntimeException error) {
				logFailure(
						registration.owner(),
						"post-setup adjustment",
						error);
			}
		}
	}

	private static void logFailure(
			ResourceLocation owner,
			String phase,
			RuntimeException error) {
		JojoMod.getLogger().error(
				"Player arm-pose provider {} failed during {}.",
				owner,
				phase,
				error);
	}

	private static void publishSnapshot() {
		List<Registration> registrations =
				new ArrayList<>(PROVIDERS.size());
		PROVIDERS.forEach((owner, provider) ->
				registrations.add(new Registration(owner, provider)));
		snapshot = List.copyOf(registrations);
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(PROVIDERS.keySet());
	}

	static synchronized void resetForTests() {
		PROVIDERS.clear();
		snapshot = List.of();
	}

	private record Registration(
			ResourceLocation owner,
			PlayerArmPoseProvider provider) {}
}
