package com.github.standobyte.jojo.api.client.render;

import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Temporarily changes a shared vanilla player model's arm pose for one render.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class ScopedPlayerModelPose {
	@Nullable private static ActiveScope activeScope;

	public static void setArmPoseForCurrentRender(
			AbstractClientPlayer player,
			PlayerModel<?> model,
			HumanoidArm arm,
			HumanoidModel.ArmPose pose) {
		if (activeScope != null
				&& !activeScope.matches(player.getUUID(), model)) {
			restoreActive();
		}
		if (activeScope == null) {
			activeScope = new ActiveScope(
					player.getUUID(),
					model,
					ArmPoseSnapshot.capture(model));
		}
		if (arm == HumanoidArm.LEFT) {
			model.leftArmPose = pose;
		}
		else {
			model.rightArmPose = pose;
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void beforePlayerRender(RenderPlayerEvent.Pre event) {
		restoreActive();
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void afterPlayerRender(RenderPlayerEvent.Post event) {
		ActiveScope scope = activeScope;
		if (scope != null
				&& scope.matches(
						event.getEntity().getUUID(),
						event.getRenderer().getModel())) {
			restoreActive();
		}
	}

	@SubscribeEvent
	public static void afterEntityStage(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			restoreActive();
		}
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		restoreActive();
	}

	static void restoreActive() {
		ActiveScope scope = activeScope;
		activeScope = null;
		if (scope != null) {
			scope.snapshot.restore(scope.model);
		}
	}

	private record ActiveScope(
			UUID playerId,
			PlayerModel<?> model,
			ArmPoseSnapshot snapshot) {
		boolean matches(UUID playerId, PlayerModel<?> model) {
			return this.playerId.equals(playerId)
					&& this.model == model;
		}
	}

	record ArmPoseSnapshot(
			HumanoidModel.ArmPose left,
			HumanoidModel.ArmPose right) {
		static ArmPoseSnapshot capture(PlayerModel<?> model) {
			return new ArmPoseSnapshot(
					model.leftArmPose,
					model.rightArmPose);
		}

		void restore(PlayerModel<?> model) {
			model.leftArmPose = left;
			model.rightArmPose = right;
		}
	}

	private ScopedPlayerModelPose() {}
}
