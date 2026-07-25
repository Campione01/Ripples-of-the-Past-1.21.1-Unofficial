package com.github.standobyte.jojo.client.entityrender;

import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.v1_21_4_stuff.renderstate.HumanoidRenderState;

import net.minecraft.world.entity.LivingEntity;

public class RipplesPlayerRenderState {
	public EntityActionRenderState entityAction = new EntityActionRenderState();

	public static void extract(LivingEntity entity, HumanoidRenderState vanillaRenderState, RipplesPlayerRenderState modRenderState, 
			float partialTick/*, ItemModelResolver itemModelResolver*/) {
		EntityActionRenderState.extract(modRenderState.entityAction, entity, partialTick);
		if (LivingComponentAction.getCurEntityAction(entity) == null) {
			modRenderState.entityAction.pose = null;
			modRenderState.entityAction.barrageSwings = null;
			modRenderState.entityAction.animId = null;
			modRenderState.entityAction.armsObstructView = false;
		}
		if (modRenderState.entityAction.pose != null) {
			vanillaRenderState.isCrouching = false;
		}
	}
	
	public static interface RipplesRenderStateExtensionMixin {
		public RipplesPlayerRenderState get(); 
	}
}
