package com.github.standobyte.jojo.client.entityrender;

import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.v1_21_4_stuff.renderstate.HumanoidRenderState;

import net.minecraft.world.entity.LivingEntity;

public class RipplesPlayerRenderState {
	public EntityActionRenderState entityAction = new EntityActionRenderState();

	public static void extract(LivingEntity entity, HumanoidRenderState vanillaRenderState, RipplesPlayerRenderState modRenderState, 
			float partialTick/*, ItemModelResolver itemModelResolver*/) {
		EntityActionRenderState.extract(modRenderState.entityAction, entity, partialTick);
		if (finishExtraction(modRenderState, LivingComponentAction.getCurEntityAction(entity) != null)) {
			vanillaRenderState.isCrouching = false;
		}
	}

	static boolean finishExtraction(RipplesPlayerRenderState modRenderState, boolean hasPlayerAction) {
		if (!hasPlayerAction) {
			// FINAL also carries addon and persistent poses without a player action.
			// EntityActionRenderState.extract replaces it with null on an idle frame.
			modRenderState.entityAction.barrageSwings = null;
			modRenderState.entityAction.animId = null;
			modRenderState.entityAction.armsObstructView = false;
		}
		return modRenderState.entityAction.pose != null;
	}
	
	public static interface RipplesRenderStateExtensionMixin {
		public RipplesPlayerRenderState get(); 
	}
}
