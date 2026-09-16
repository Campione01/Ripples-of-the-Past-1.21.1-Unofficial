package rotp.core.client.entityanim.molang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import rotp.core.powersystem.entityaction.ActionOBB;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.compat.v1_21_4.renderstate.LivingEntityRenderState;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import team.unnamed.mocha.runtime.value.ObjectProperty;
import team.unnamed.mocha.runtime.value.ObjectValue;
import team.unnamed.mocha.runtime.value.Value;

public class AnimMolangQuery implements ObjectValue {
	public static final String NAMESPACE = "query";
	public static AnimMolangQuery instance = new AnimMolangQuery();
	
	// Nothing to initialise: CONTEXT below is initialised after this instance, so a reset() here would
	// read a null field. Each thread's context initialises itself on first use anyway.
	protected AnimMolangQuery() {}

	/*
	 * The Mocha scope holds this one instance, and two kinds of thread evaluate through it: the render
	 * thread every frame, and resource-reload workers baking cool poses while the old models are still
	 * being drawn. With the three properties shared, a worker's reset() and the render thread's
	 * fillContext() interleave both ways - the render thread can read a zeroed context for a frame, and
	 * a baked cool pose can capture a live entity's head rotation and stay wrong until the next reload.
	 * Each thread gets its own set instead; the scope, the engine and the property names are untouched.
	 */
	private static final ThreadLocal<QueryContext> CONTEXT = ThreadLocal.withInitial(QueryContext::new);

	public void fillContext(AnimMolangVariables variables) {
		CONTEXT.get().set(variables.xRot, variables.yRot, variables.extendablePartLength);
	}

	public void reset() {
		CONTEXT.get().set(0, 0, 0);
	}

	@Override
	public @Nullable ObjectProperty getProperty(@NotNull String name) {
		QueryContext context = CONTEXT.get();
		switch (name) {
			case "head_x_rotation": return context.head_x_rotation;
			case "head_y_rotation": return context.head_y_rotation;
			case "extendablePartLength": return context.extendablePartLength;
		}
		return null;
	}


	private static class QueryContext {
		ObjectProperty head_x_rotation;
		ObjectProperty head_y_rotation;
		ObjectProperty extendablePartLength;

		QueryContext() {
			set(0, 0, 0);
		}

		void set(float xRot, float yRot, float extendablePartLength) {
			this.head_x_rotation = ObjectProperty.property(Value.of(xRot), false);
			this.head_y_rotation = ObjectProperty.property(Value.of(yRot), false);
			this.extendablePartLength = ObjectProperty.property(Value.of(extendablePartLength), false);
		}
	}
	
	
	public static class AnimMolangVariables {
		public float xRot;
		public float yRot;
		public float extendablePartLength;
		
		public static AnimMolangVariables set(
				float xRot, 
				float yRot, 
				float extendablePartLength
				) {
			reusedState.xRot = xRot;
			reusedState.yRot = yRot;
			reusedState.extendablePartLength = extendablePartLength;
			return reusedState;
		}
		
		public static AnimMolangVariables reusedState = new AnimMolangVariables();
		protected AnimMolangVariables() {}
		
		public static AnimMolangVariables extract(LivingEntity entity, float partialTick) {
			AnimMolangVariables state = AnimMolangVariables.reusedState;
			
			float f = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
			float bodyRot = LivingEntityRenderState.solveBodyRot(entity, f, partialTick);
			state.yRot = Mth.wrapDegrees(f - bodyRot);
			state.xRot = LivingEntityRenderState.getXRot(entity, partialTick);

			state.extendablePartLength = 0;
			if (LivingComponentAction.getCurEntityAction(entity) instanceof ActionOBB obbToRender && obbToRender.extendableOBB() != null){
				state.extendablePartLength = obbToRender.extendableOBB().getAnimLength(partialTick);
	        }
	        return state;
		}
		
	}
	
}

