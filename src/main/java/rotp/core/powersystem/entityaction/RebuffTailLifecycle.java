package rotp.core.powersystem.entityaction;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.util.Mth;
import rotp.core.powersystem.entityaction.LivingComponentAction.RebuffVisualTail;

final class RebuffTailLifecycle {
	private RebuffTailLifecycle() {}

	static boolean shouldArm(boolean clientSide, boolean actionCleared,
			boolean originalAction, boolean previousWasRebuff) {
		return clientSide && actionCleared && originalAction && previousWasRebuff;
	}

	static boolean isExpired(float age) {
		return age < 0 || age >= RebuffVisualTail.DURATION_TICKS;
	}

	@Nullable
	static CapturedTime captureTime(ActionPhase phase, @Nullable ActionPhase prevFramePhase,
			float subtractFramePartialTick, float phasePartialTick, int curPhaseTick,
			float curPhaseLength, float fullTick, @Nullable Object2FloatMap<ActionPhase> skippedWindupPhase) {
		// The caller retains the phase/animation guards and the remembered phase after forceStop.
		float subtractPartialTick = prevFramePhase == phase ? subtractFramePartialTick
				: prevFramePhase == null ? phasePartialTick : 0;
		float phaseTick = curPhaseTick - subtractPartialTick;
		float phaseLength = Mth.ceil(curPhaseLength);
		if (skippedWindupPhase != null) {
			float skipped = skippedWindupPhase.getFloat(phase);
			phaseTick -= skipped;
			phaseLength -= skipped;
			for (ActionPhase earlierPhase : ActionPhase.values()) {
				fullTick -= skippedWindupPhase.getFloat(earlierPhase);
				if (earlierPhase == phase) break;
			}
		}
		if (phaseLength <= 0) return null;
		return new CapturedTime(phaseTick, Math.min(phaseTick / phaseLength, 1), fullTick);
	}

	record CapturedTime(float phaseTick, float phaseCompletion, float fullTick) {}
}
