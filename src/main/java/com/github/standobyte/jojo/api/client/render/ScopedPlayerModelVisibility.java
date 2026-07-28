package com.github.standobyte.jojo.api.client.render;

import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Temporarily hides the vanilla player model while an addon-owned replacement
 * layer renders.
 *
 * <p>The player renderer reuses model instances between entities. Addons must
 * therefore not retain part visibility in per-player maps and assume a matching
 * {@link RenderPlayerEvent.Post} will always run. This scope restores the exact
 * prior state after the render, before another player render, and at the end of
 * the entity stage.</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class ScopedPlayerModelVisibility {
	private static final Part[] ALL_PARTS = Part.values();
	@Nullable private static ActiveScope activeScope;

	/**
	 * Hides the standard player body and clothing parts for the current render.
	 * Calling this more than once for the same model in one render is harmless.
	 */
	public static void hideForCurrentRender(
			AbstractClientPlayer player,
			PlayerModel<?> model) {
		hidePartsForCurrentRender(player, model, ALL_PARTS);
	}

	/**
	 * Hides only the requested vanilla player parts for the current render.
	 * Multiple addons may add parts to the same scope without losing the
	 * visibility state captured before the first mutation.
	 */
	public static void hidePartsForCurrentRender(
			AbstractClientPlayer player,
			PlayerModel<?> model,
			Part... parts) {
		if (activeScope != null) {
			if (!activeScope.matches(player.getUUID(), model)) {
				restoreActive();
			}
		}
		if (activeScope == null) {
			VisibilitySnapshot snapshot =
					VisibilitySnapshot.capture(model);
			activeScope = new ActiveScope(
					player.getUUID(), model, snapshot);
		}
		for (Part part : parts) {
			part.setVisible(model, false);
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

	static boolean hasActiveScope() {
		return activeScope != null;
	}

	static void restoreActive() {
		ActiveScope scope = activeScope;
		activeScope = null;
		if (scope != null) {
			scope.snapshot.restore(scope.model);
		}
	}

	public enum Part {
		HEAD,
		HAT,
		BODY,
		JACKET,
		LEFT_ARM,
		LEFT_SLEEVE,
		RIGHT_ARM,
		RIGHT_SLEEVE,
		LEFT_LEG,
		LEFT_PANTS,
		RIGHT_LEG,
		RIGHT_PANTS;

		void setVisible(
				PlayerModel<?> model,
				boolean visible) {
			switch (this) {
			case HEAD -> model.head.visible = visible;
			case HAT -> model.hat.visible = visible;
			case BODY -> model.body.visible = visible;
			case JACKET -> model.jacket.visible = visible;
			case LEFT_ARM -> model.leftArm.visible = visible;
			case LEFT_SLEEVE -> model.leftSleeve.visible = visible;
			case RIGHT_ARM -> model.rightArm.visible = visible;
			case RIGHT_SLEEVE -> model.rightSleeve.visible = visible;
			case LEFT_LEG -> model.leftLeg.visible = visible;
			case LEFT_PANTS -> model.leftPants.visible = visible;
			case RIGHT_LEG -> model.rightLeg.visible = visible;
			case RIGHT_PANTS -> model.rightPants.visible = visible;
			}
		}
	}

	private record ActiveScope(
			UUID playerId,
			PlayerModel<?> model,
			VisibilitySnapshot snapshot) {
		boolean matches(
				UUID playerId,
				PlayerModel<?> model) {
			return this.playerId.equals(playerId)
					&& this.model == model;
		}
	}

	record VisibilitySnapshot(
			boolean head,
			boolean hat,
			boolean body,
			boolean jacket,
			boolean leftArm,
			boolean leftSleeve,
			boolean rightArm,
			boolean rightSleeve,
			boolean leftLeg,
			boolean leftPants,
			boolean rightLeg,
			boolean rightPants) {
		static VisibilitySnapshot capture(PlayerModel<?> model) {
			return new VisibilitySnapshot(
					model.head.visible,
					model.hat.visible,
					model.body.visible,
					model.jacket.visible,
					model.leftArm.visible,
					model.leftSleeve.visible,
					model.rightArm.visible,
					model.rightSleeve.visible,
					model.leftLeg.visible,
					model.leftPants.visible,
					model.rightLeg.visible,
					model.rightPants.visible);
		}

		void restore(PlayerModel<?> model) {
			model.head.visible = head;
			model.hat.visible = hat;
			model.body.visible = body;
			model.jacket.visible = jacket;
			model.leftArm.visible = leftArm;
			model.leftSleeve.visible = leftSleeve;
			model.rightArm.visible = rightArm;
			model.rightSleeve.visible = rightSleeve;
			model.leftLeg.visible = leftLeg;
			model.leftPants.visible = leftPants;
			model.rightLeg.visible = rightLeg;
			model.rightPants.visible = rightPants;
		}
	}

	private ScopedPlayerModelVisibility() {}
}
