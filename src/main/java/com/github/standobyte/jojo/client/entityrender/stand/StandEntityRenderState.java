package com.github.standobyte.jojo.client.entityrender.stand;

import java.util.ArrayList;
import java.util.List;

import com.github.standobyte.jojo.client.entityrender.EntityActionRenderState;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.v1_21_4_stuff.renderstate.HumanoidRenderState;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

public class StandEntityRenderState extends HumanoidRenderState {
	public StandSkin skin;
	public final EntityActionRenderState action = new EntityActionRenderState();
	public int tint = -1;
	public float alpha = 1;
	public StandVisualContext visualContext = StandVisualContext.preview();
	public HumanoidPart[] visibleParts = HumanoidPart.ALL;
	public ObstructionRenderMode obstructionRenderMode = ObstructionRenderMode.NONE;
	public HumanoidPart[] classicSolidParts = HumanoidPart.NONE;
	public HumanoidPart[] classicOutlineParts = HumanoidPart.NONE;
	public int classicOutlineColor = -1;
	public boolean doScalingFromStandSkin;
	public boolean silverChariotArmorVisible = true;
	public boolean silverChariotRapierVisible = true;
	public boolean silverChariotRapierOnFire;
	public boolean visibleForSpectator;

    public static void extractStandRenderState(StandEntity entity, StandEntityRenderState reusedState, float partialTick) {
		reusedState.visibleParts = armsOnlyVisibleParts(entity);
		reusedState.resetObstruction();
		reusedState.tint = -1;
		reusedState.visibleForSpectator = visibleForSpectator(entity);
		float lifecycleAlpha = entity.clientStuff.getAlpha(entity, partialTick);
		StandVisualContext context = StandVisualContext.world(entity, lifecycleAlpha);
		reusedState.visualContext = context;
		reusedState.alpha = context.effectiveAlpha();
		reusedState.silverChariotArmorVisible = entity.isSilverChariotArmorVisible();
		reusedState.silverChariotRapierVisible = entity.isSilverChariotRapierVisible();
		reusedState.silverChariotRapierOnFire = entity.isSilverChariotRapierOnFire();
		reusedState.visibleParts = filterMissingParts(entity, reusedState.visibleParts);
    }

	static HumanoidPart[] filterMissingParts(StandEntity entity, HumanoidPart[] parts) {
		StandPower power = entity.getUserPower();
		if (power == null) {
			return parts;
		}
		return power.getStandInstance()
				.map(standInstance -> {
					List<HumanoidPart> visible = new ArrayList<>(parts.length);
					for (HumanoidPart part : parts) {
						if (partVisibleWithMissingStandParts(standInstance::hasPart, part)) {
							visible.add(part);
						}
					}
					return visible.toArray(HumanoidPart[]::new);
				})
				.orElse(parts);
	}

	public void resetObstruction() {
		obstructionRenderMode = ObstructionRenderMode.NONE;
		classicSolidParts = HumanoidPart.NONE;
		classicOutlineParts = HumanoidPart.NONE;
		classicOutlineColor = -1;
	}

	public enum ObstructionRenderMode {
		NONE,
		CLASSIC_ARMS_ONLY,
		CLASSIC_OUTLINE
	}

	private static boolean partVisibleWithMissingStandParts(java.util.function.Predicate<StandPart> hasPart, HumanoidPart part) {
		return switch (part) {
			case HEAD, BODY -> hasPart.test(StandPart.MAIN_BODY);
			case LEFT_ARM, RIGHT_ARM -> hasPart.test(StandPart.ARMS);
			case LEGS -> hasPart.test(StandPart.LEGS);
		};
	}

	private static HumanoidPart[] armsOnlyVisibleParts(StandEntity entity) {
		if (!entity.isArmsOnlyMode()) {
			return HumanoidPart.ALL;
		}
		boolean mainArm = entity.showArm(InteractionHand.MAIN_HAND);
		boolean offArm = entity.showArm(InteractionHand.OFF_HAND);
		if (mainArm && offArm) {
			return HumanoidPart.ARMS_ONLY;
		}
		HumanoidArm arm = offArm ? entity.getMainArm().getOpposite() : entity.getMainArm();
		return arm == HumanoidArm.RIGHT ? HumanoidPart.RIGHT_ARM_ONLY : HumanoidPart.LEFT_ARM_ONLY;
	}

	private static boolean visibleForSpectator(StandEntity entity) {
		Player player = Minecraft.getInstance().player;
		return player != null && entity.underInvisibilityEffect() && JojoModUtil.seesInvisibleAsSpectator(player);
	}
}
