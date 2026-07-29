package com.github.standobyte.jojo.api.client.render;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Hides selected parts of vanilla humanoid armor models for one entity render.
 *
 * <p>Addons declare the physical parts hidden for the current rendered owner.
 * The core armor-layer hook applies that selection after vanilla chooses the
 * equipment-slot parts and restores the shared armor model after each piece.
 * A scope never applies to a different entity, even when renderers reuse the
 * same model instance.</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class ScopedHumanoidArmorVisibility {
	@Nullable private static OwnerScope activeOwner;
	@Nullable private static ArmorMutation activeMutation;

	private ScopedHumanoidArmorVisibility() {}

	public static void hidePartsForCurrentRender(
			LivingEntity owner, Part... parts) {
		if (owner == null || parts == null || parts.length == 0) {
			return;
		}
		if (activeOwner != null && !activeOwner.matches(owner)) {
			clearCurrentRender();
		}
		if (activeOwner == null) {
			activeOwner = new OwnerScope(
					owner, owner.getUUID(),
					EnumSet.noneOf(Part.class));
		}
		for (Part part : parts) {
			if (part != null) {
				activeOwner.parts.add(part);
			}
		}
	}

	@ApiStatus.Internal
	public static void applyForArmorPiece(
			LivingEntity owner, HumanoidModel<?> model) {
		restoreArmorMutation();
		OwnerScope scope = activeOwner;
		if (scope == null || model == null || !scope.matches(owner)) {
			return;
		}
		VisibilitySnapshot snapshot =
				VisibilitySnapshot.capture(model);
		activeMutation = new ArmorMutation(model, snapshot);
		for (Part part : scope.parts) {
			part.setVisible(model, false);
		}
	}

	@ApiStatus.Internal
	public static void restoreAfterArmorPiece(
			HumanoidModel<?> model) {
		ArmorMutation mutation = activeMutation;
		if (mutation != null && mutation.model == model) {
			restoreArmorMutation();
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void beforeLivingRender(RenderLivingEvent.Pre<?, ?> event) {
		clearCurrentRender();
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void beforePlayerRender(RenderPlayerEvent.Pre event) {
		clearCurrentRender();
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void afterLivingRender(RenderLivingEvent.Post<?, ?> event) {
		clearIfOwner(event.getEntity());
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void afterPlayerRender(RenderPlayerEvent.Post event) {
		clearIfOwner(event.getEntity());
	}

	@SubscribeEvent
	public static void afterEntityStage(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			clearCurrentRender();
		}
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		clearCurrentRender();
	}

	private static void clearIfOwner(LivingEntity owner) {
		OwnerScope scope = activeOwner;
		if (scope != null && scope.matches(owner)) {
			clearCurrentRender();
		}
	}

	static void clearCurrentRender() {
		restoreArmorMutation();
		activeOwner = null;
	}

	private static void restoreArmorMutation() {
		ArmorMutation mutation = activeMutation;
		activeMutation = null;
		if (mutation != null) {
			mutation.snapshot.restore(mutation.model);
		}
	}

	public enum Part {
		HEAD,
		BODY,
		LEFT_ARM,
		RIGHT_ARM,
		LEFT_LEG,
		RIGHT_LEG;

		void setVisible(HumanoidModel<?> model, boolean visible) {
			switch (this) {
			case HEAD -> {
				model.head.visible = visible;
				model.hat.visible = visible;
			}
			case BODY -> model.body.visible = visible;
			case LEFT_ARM -> model.leftArm.visible = visible;
			case RIGHT_ARM -> model.rightArm.visible = visible;
			case LEFT_LEG -> model.leftLeg.visible = visible;
			case RIGHT_LEG -> model.rightLeg.visible = visible;
			}
		}
	}

	private static final class OwnerScope {
		private final LivingEntity owner;
		private final UUID ownerId;
		private final EnumSet<Part> parts;

		private OwnerScope(
				LivingEntity owner,
				UUID ownerId,
				EnumSet<Part> parts) {
			this.owner = owner;
			this.ownerId = ownerId;
			this.parts = parts;
		}

		private boolean matches(LivingEntity owner) {
			return this.owner == owner
					&& owner != null
					&& ownerId.equals(owner.getUUID());
		}
	}

	private record ArmorMutation(
			HumanoidModel<?> model,
			VisibilitySnapshot snapshot) {}

	record VisibilitySnapshot(
			boolean head,
			boolean hat,
			boolean body,
			boolean leftArm,
			boolean rightArm,
			boolean leftLeg,
			boolean rightLeg) {
		static VisibilitySnapshot capture(HumanoidModel<?> model) {
			return new VisibilitySnapshot(
					model.head.visible,
					model.hat.visible,
					model.body.visible,
					model.leftArm.visible,
					model.rightArm.visible,
					model.leftLeg.visible,
					model.rightLeg.visible);
		}

		void restore(HumanoidModel<?> model) {
			model.head.visible = head;
			model.hat.visible = hat;
			model.body.visible = body;
			model.leftArm.visible = leftArm;
			model.rightArm.visible = rightArm;
			model.leftLeg.visible = leftLeg;
			model.rightLeg.visible = rightLeg;
		}
	}
}
