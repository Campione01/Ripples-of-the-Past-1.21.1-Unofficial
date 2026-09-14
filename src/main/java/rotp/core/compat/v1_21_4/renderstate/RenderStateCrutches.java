package rotp.core.compat.v1_21_4.renderstate;

import rotp.core.client.entityrender.stand.StandEntityRenderState;

public class RenderStateCrutches {
	public static LivingEntityRenderState currentEntityRenderState;
	public static StandEntityRenderState currentStandEntityRenderState;

	public record Snapshot(LivingEntityRenderState entityRenderState, StandEntityRenderState standRenderState) {}

	public static Snapshot pushEntity(LivingEntityRenderState renderState) {
		Snapshot previous = snapshot();
		currentEntityRenderState = renderState;
		return previous;
	}

	public static Snapshot pushStand(StandEntityRenderState renderState) {
		Snapshot previous = snapshot();
		currentEntityRenderState = renderState;
		currentStandEntityRenderState = renderState;
		return previous;
	}

	public static void restore(Snapshot snapshot) {
		currentEntityRenderState = snapshot.entityRenderState();
		currentStandEntityRenderState = snapshot.standRenderState();
	}

	private static Snapshot snapshot() {
		return new Snapshot(currentEntityRenderState, currentStandEntityRenderState);
	}
}
