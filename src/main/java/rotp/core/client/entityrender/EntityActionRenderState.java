package rotp.core.client.entityrender;

import javax.annotation.Nullable;

import rotp.core.client.entityanim.PreFrameEntityAnimCalc;
import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.barrage.BarrageSwings;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimatedEntity;
import rotp.core.client.entityrender.RipplesPlayerRenderState.RipplesRenderStateExtensionMixin;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.compat.v1_21_4.renderstate.HumanoidRenderState;
import rotp.core.compat.v1_21_4.renderstate.LivingEntityRenderState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

public class EntityActionRenderState {
	@Nullable public AnimFramePose pose;
	@Nullable public BarrageSwings barrageSwings;
	@Nullable public ActionAnimIdentifier animId;
	public boolean armsObstructView;
	
	public static void extract(EntityActionRenderState renderState, LivingEntity entity, float partialTick) {
		AnimatedEntity preCalcPose = (AnimatedEntity) entity;
		renderState.pose = preCalcPose.jojo_ripples$getModelPose(AnimatedEntity.PoseType.FINAL);
		renderState.barrageSwings = PreFrameEntityAnimCalc.getBarrageSwings(entity);
		EntityActionInstance curAction = LivingComponentAction.getCurEntityAction(entity);
		renderState.animId = curAction != null ? curAction.getEntityAnim() : null;
		renderState.armsObstructView = armsObstructView(renderState.animId);
	}

	private static boolean armsObstructView(@Nullable ActionAnimIdentifier animId) {
		if (animId == null) return false;
		return switch (animId.name()) {
			case "flame_burst", "flameBurst", "fireball", "crossfire_hurricane", "crossfire_hurricane_special" -> true;
			default -> false;
		};
	}

	public static boolean setupModelAnim(HumanoidModel<?> model, HumanoidRenderState vanillaRenderState, RipplesPlayerRenderState modRenderState) {
		if (modRenderState.entityAction.pose != null) {
			RotpAnimDefinition.animate(model, modRenderState.entityAction.pose);
			return true;
		}
		return false;
	}
	
	
	@Nullable
	public static EntityActionRenderState getFrom(LivingEntityRenderState vanillaRenderState) {
		if (vanillaRenderState instanceof StandEntityRenderState standEntity) {
			return standEntity.action;
		}
		if (vanillaRenderState instanceof RipplesRenderStateExtensionMixin playerMixin) {
			RipplesPlayerRenderState playerExtension = playerMixin.get();
			return playerExtension != null ? playerExtension.entityAction : null;
		}
		return null;
	}
}
