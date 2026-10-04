package rotp.core.powersystem.entityaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import it.unimi.dsi.fastutil.objects.Object2FloatArrayMap;
import rotp.core.powersystem.entityaction.RebuffTailLifecycle.CapturedTime;

/** Pure production decisions and source bindings, not executed client callbacks or render integration. */
public final class RebuffLifecycleSmokeTest {
	private static final String COMPONENT =
			"src/main/java/rotp/core/powersystem/entityaction/LivingComponentAction.java";
	private static final String RENDER =
			"src/main/java/rotp/core/client/entityanim/PreFrameEntityAnimCalc.java";
	private static final String ARM = compact("""
			if (RebuffTailLifecycle.shouldArm(entity.level().isClientSide(), this.action == null,
			        prevAction == stoppingAction, prevAction instanceof HamonRebuffOverdrive)) {
			    clRebuffTail = stoppedRebuff;
			}
			""");
	private static final String CAPTURE_GUARD = compact("""
			if (phase == null || rebuff.getCurPhaseLength() <= 0 || animSet == null || animId == null) return null;
			""");
	private static final String CAPTURE_CALL = compact("""
			RebuffTailLifecycle.CapturedTime captured = RebuffTailLifecycle.captureTime(phase, stopped.prevFramePhase,
			        stopped.subtractFramePartialTick, stopped.phasePartialTick, stopped.curPhaseTick,
			        stopped.getCurPhaseLength(), stopped.calcFullTicks(phase, stopped.getPhaseTick()), stopped.skippedWindupPhase);
			""");

	private RebuffLifecycleSmokeTest() {}

	public static void main(String[] args) {
		verifyArmingDecisions();
		verifyCaptureClocks();
		verifySkippedPhaseClocks();
		verifyCaptureBoundaries();
		verifyExpiryBoundaries();
		String component = read(COMPONENT);
		String render = read(RENDER);
		verifyComponentBindings(component);
		verifyRenderBindings(render);
		verifyNegativeSourceControls(component, render);
		System.out.println("Rebuff lifecycle smoke tests passed: 16 arming combinations, capture clocks, expiry boundaries, "
				+ "production bindings and 10 negative in-memory source controls; no live-client or rollback execution.");
	}

	private static void verifyArmingDecisions() {
		for (int flags = 0; flags < 16; flags++) {
			boolean client = (flags & 1) != 0;
			boolean cleared = (flags & 2) != 0;
			boolean sameOriginal = (flags & 4) != 0;
			boolean wasRebuff = (flags & 8) != 0;
			check(RebuffTailLifecycle.shouldArm(client, cleared, sameOriginal, wasRebuff) == (flags == 15),
					"only a committed client stop of the same Rebuff may arm: flags=" + flags);
		}
		check(!RebuffTailLifecycle.shouldArm(true, true, true, false),
				"null-to-null has no previous Rebuff and must not re-arm");
		check(!RebuffTailLifecycle.shouldArm(true, false, true, true),
				"replacement must not arm the stopped Rebuff");
	}

	private static void verifyCaptureClocks() {
		assertTime(RebuffTailLifecycle.captureTime(ActionPhase.WINDUP, null,
				0.75F, 0.25F, 6, 14, 6.25F, null), 5.75F, 23F / 56, 6.25F,
				"first frame subtracts phasePartialTick, not the previous-frame value");
		assertTime(RebuffTailLifecycle.captureTime(ActionPhase.WINDUP, ActionPhase.WINDUP,
				0.75F, 0.25F, 6, 14, 6.25F, null), 5.25F, 0.375F, 6.25F,
				"same rendered phase preserves its recorded partial subtraction");
		assertTime(RebuffTailLifecycle.captureTime(ActionPhase.PERFORM, ActionPhase.WINDUP,
				0.75F, 0.25F, 6, 10, 20.25F, null), 6, 0.6F, 20.25F,
				"a changed phase does not subtract either old partial");
		assertTime(RebuffTailLifecycle.captureTime(ActionPhase.RECOVERY, ActionPhase.RECOVERY,
				0.25F, 0.75F, 5, 15.2F, 29.25F, null), 4.75F, 19F / 64, 29.25F,
				"fractional phase length rounds up before computing completion");
	}

	private static void verifySkippedPhaseClocks() {
		Object2FloatArrayMap<ActionPhase> skipped = new Object2FloatArrayMap<>();
		skipped.put(ActionPhase.BUTTON_CHARGE, 0.5F);
		skipped.put(ActionPhase.WINDUP, 2F);
		skipped.put(ActionPhase.PERFORM, 1.25F);
		skipped.put(ActionPhase.RECOVERY, 4F);
		assertTime(RebuffTailLifecycle.captureTime(ActionPhase.WINDUP, ActionPhase.WINDUP,
				0.25F, 0.5F, 7, 14, 7.5F, skipped), 4.75F, 19F / 48, 5,
				"windup removes current skip and only earlier/current full-clock skips");
		assertTime(RebuffTailLifecycle.captureTime(ActionPhase.PERFORM, ActionPhase.PERFORM,
				0.25F, 0.5F, 5, 10, 19.25F, skipped), 3.5F, 0.4F, 15.5F,
				"perform must not subtract a later recovery skip");
		assertTime(RebuffTailLifecycle.captureTime(ActionPhase.RECOVERY, ActionPhase.RECOVERY,
				0.25F, 0.5F, 5, 16, 29.25F, skipped), 0.75F, 0.0625F, 21.5F,
				"recovery removes all preceding and current full-clock skips");
		check(skipped.size() == 4 && skipped.getFloat(ActionPhase.BUTTON_CHARGE) == 0.5F
				&& skipped.getFloat(ActionPhase.WINDUP) == 2F && skipped.getFloat(ActionPhase.PERFORM) == 1.25F
				&& skipped.getFloat(ActionPhase.RECOVERY) == 4F, "capture must not mutate the action's skipped-phase map");

		skipped.clear();
		skipped.put(ActionPhase.BUTTON_CHARGE, 0.5F);
		skipped.put(ActionPhase.WINDUP, 3.5F);
		assertTime(RebuffTailLifecycle.captureTime(ActionPhase.WINDUP, null,
				0.75F, 0.25F, 4, 3.2F, 8, skipped), 0.25F, 0.5F, 4,
				"ceil must precede fractional skipped-length subtraction");
	}

	private static void verifyCaptureBoundaries() {
		Object2FloatArrayMap<ActionPhase> skipped = new Object2FloatArrayMap<>();
		skipped.put(ActionPhase.WINDUP, 14F);
		check(RebuffTailLifecycle.captureTime(ActionPhase.WINDUP, null,
				0, 0, 6, 14, 6, skipped) == null, "zero effective phase length must reject capture");
		skipped.put(ActionPhase.WINDUP, 15F);
		check(RebuffTailLifecycle.captureTime(ActionPhase.WINDUP, null,
				0, 0, 6, 14, 6, skipped) == null, "negative effective phase length must reject capture");
		assertTime(RebuffTailLifecycle.captureTime(ActionPhase.WINDUP, null,
				0, 0, 20, 14, 14, null), 20, 1, 14, "completion remains upper-clamped");
		assertTime(RebuffTailLifecycle.captureTime(ActionPhase.WINDUP, null,
				0, 0.25F, 0, 14, 0.25F, null), -0.25F, -1F / 56, 0.25F,
				"capture preserves the existing negative fractional start without a new lower clamp");
	}

	private static void verifyExpiryBoundaries() {
		check(!RebuffTailLifecycle.isExpired(0) && !RebuffTailLifecycle.isExpired(9)
				&& !RebuffTailLifecycle.isExpired(Math.nextDown(10F)), "tail remains valid before ten ticks");
		check(RebuffTailLifecycle.isExpired(10) && RebuffTailLifecycle.isExpired(10.25F),
				"tail expires at exactly ten ticks, not the following tick");
		check(RebuffTailLifecycle.isExpired(-Float.MIN_VALUE), "backwards entity time invalidates the tail");
		check(RebuffTailLifecycle.isExpired(Float.POSITIVE_INFINITY)
				&& RebuffTailLifecycle.isExpired(Float.NEGATIVE_INFINITY)
				&& !RebuffTailLifecycle.isExpired(Float.NaN), "extraction must preserve existing float comparisons");
	}

	private static void verifyComponentBindings(String source) {
		String capture = body(source, "privateRebuffVisualTailcaptureRebuffTail(");
		check(capture.equals(compact("""
				ActionPhase phase = rebuff.captureClientFadePhase();
				ResourceLocation animSet = rebuff.ability.getEntityAnimSet(entity);
				ActionAnimIdentifier animId = rebuff.getEntityAnim();
				""") + CAPTURE_GUARD + "EntityActionInstancestopped=rebuff;" + CAPTURE_CALL + compact("""
				if (captured == null) return null;
				return new RebuffVisualTail(entity.level().dimension().location(), entity.tickCount,
				        animSet, animId, phase, captured.phaseTick(), captured.phaseCompletion(), captured.fullTick());
				""")), "capture must use remembered phase, guarded live inputs and the production helper result");

		String transition = body(source, "privateHeldInputsetActionInternal(");
		requireOrdered(transition,
				"if(setActionCallback!=null&&setActionCallback.onActionSet(action)){returnnull;}",
				"EntityActionInstancestoppingAction=this.action;",
				"RebuffVisualTailstoppedRebuff=entity.level().isClientSide()&&action==null"
						+ "&&stoppingActioninstanceofHamonRebuffOverdriverebuff?captureRebuffTail(rebuff):null;",
				"this.action._beforeActionRemoved(action);",
				"EntityActionInstanceprevAction=this.action;",
				"assignAction(action,networkGeneration,authoritativeClientState);",
				"action._onActionStarted(prevAction);",
				"comboString.clear();", ARM, "returnaction;");
		check(transition.endsWith(ARM + "returnaction;") && occurrences(transition, "clRebuffTail=") == 1,
				"arming must be the guarded final commit, with no unconditional tail assignment/reset");

		String getter = body(source, "publicRebuffVisualTailgetClientRebuffTail(");
		check(getter.equals(compact("""
				if (!entity.level().isClientSide()) return null;
				if (clRebuffTail != null) {
				    float age = clRebuffTail.elapsedTicks(entity.tickCount, partialTick);
				    if (action != null || !entity.isAlive() || entity.isRemoved() || entity.isSpectator()
				            || !clRebuffTail.dimension().equals(entity.level().dimension().location())
				            || RebuffTailLifecycle.isExpired(age)) {
				        clRebuffTail = null;
				    }
				}
				return clRebuffTail;
				""")), "getter must retain side/null short circuits and destructive lifecycle/expiry invalidation");
		check(body(source, "privatevoidassignAction(").contains(compact("""
				if (action != null) {
				    action.performer = entity;
				    if (entity.level().isClientSide()) {
				        clearClientRebuffTail();
				    }
				""")), "a non-null client replacement must clear the prior tail");
		check(body(source, "publicvoidtick(").startsWith(
				"if(entity.level().isClientSide()){getClientRebuffTail(0);}"), "ticks must expire tails without a render read");
		check(body(source, "publicvoidclearClientRebuffTail(").equals("clRebuffTail=null;"),
				"explicit tail clearing must remain destructive");
	}

	private static void verifyRenderBindings(String source) {
		String render = body(source, "publicstaticAnimFramePosegetLivingPose(");
		requireOrdered(render, compact("""
				if (actionComponent != null) {
				    if (hasPrimaryPose) actionComponent.clearClientRebuffTail();
				    else {
				        rebuffPartialTick = ClientTimeStopHandler.getConstantEntityPartialTick(living, partialTick);
				        rebuffTail = actionComponent.getClientRebuffTail(rebuffPartialTick);
				    }
				}
				"""), "if(hasPrimaryPose||rebuffTail!=null){", compact("""
				else if (rebuffTail != null) {
				    animVariables.animSet = rebuffTail.animSet();
				    animVariables.animId = rebuffTail.animId();
				    animVariables.time = rebuffTail.fullTick();
				    animVariables.actionPhase = rebuffTail.phase();
				    animVariables.phaseTime = rebuffTail.phaseTick();
				    animVariables.phaseCompletion = rebuffTail.phaseCompletion();
				}
				"""), "floattailElapsed=rebuffTail!=null?rebuffTail.elapsedTicks(living.tickCount,rebuffPartialTick):0;",
				"if(rebuffTail!=null){timeSeconds+=anim.clock.elapsed(tailElapsed);}",
				"pose=anim.calcAnimPose(molangVariables,actionComponent!=null?actionComponent.clPrevPunchPose:null,timeSeconds,1);",
				"if(rebuffTail!=null)pose.blendWeight=rebuffTail.blendWeight(tailElapsed);",
				"elseif(rebuffTail!=null){actionComponent.clearClientRebuffTail();}");
	}

	private static void verifyNegativeSourceControls(String component, String render) {
		expectComponentRejection(replaceOnce(component, ARM,
				"if(false){clRebuffTail=stoppedRebuff;}"), "removed arming helper call");
		expectComponentRejection(replaceOnce(component, "clRebuffTail=stoppedRebuff;", ""), "removed arming assignment");
		expectComponentRejection(replaceOnce(component, CAPTURE_CALL,
				"RebuffTailLifecycle.CapturedTimecaptured=null;"), "removed capture helper call");
		expectComponentRejection(replaceOnce(component, CAPTURE_GUARD, ""), "removed capture preconditions");
		expectComponentRejection(replaceOnce(component, "RebuffTailLifecycle.isExpired(age)", "false"), "bypassed expiry");
		expectComponentRejection(replaceOnce(component,
				"publicvoidtick(){if(entity.level().isClientSide()){getClientRebuffTail(0);}",
				"publicvoidtick(){"), "removed tick expiry");
		expectRenderRejection(replaceOnce(render,
				"rebuffTail=actionComponent.getClientRebuffTail(rebuffPartialTick);", ""), "removed render tail acquisition");
		expectRenderRejection(replaceOnce(render, "if(hasPrimaryPose||rebuffTail!=null){", "if(hasPrimaryPose){"),
				"removed render tail admission");
		expectRenderRejection(replaceOnce(render, "timeSeconds+=anim.clock.elapsed(tailElapsed);", ""),
				"removed advancing fade clock");
		expectRenderRejection(replaceOnce(render,
				"if(rebuffTail!=null)pose.blendWeight=rebuffTail.blendWeight(tailElapsed);", ""), "removed fade weight binding");
	}

	private static void expectComponentRejection(String source, String label) {
		expectRejection(() -> verifyComponentBindings(source), label);
	}

	private static void expectRenderRejection(String source, String label) {
		expectRejection(() -> verifyRenderBindings(source), label);
	}

	private static void expectRejection(Runnable guard, String label) {
		try {
			guard.run();
		}
		catch (AssertionError expected) {
			return;
		}
		throw new AssertionError("source guard accepted negative control: " + label);
	}

	private static String replaceOnce(String source, String original, String replacement) {
		check(occurrences(source, original) == 1, "negative control must replace exactly one source site: " + original);
		return source.replace(original, replacement);
	}

	private static int occurrences(String source, String token) {
		int count = 0;
		for (int at = source.indexOf(token); at >= 0; at = source.indexOf(token, at + token.length())) count++;
		return count;
	}

	private static void requireOrdered(String source, String... tokens) {
		int after = 0;
		for (String token : tokens) {
			int at = source.indexOf(token, after);
			check(at >= 0, "missing or reordered production binding: " + token);
			after = at + token.length();
		}
	}

	private static String body(String source, String signature) {
		int method = source.indexOf(signature);
		check(method >= 0, "missing production method: " + signature);
		int start = source.indexOf('{', method);
		check(start >= 0, "missing method body: " + signature);
		int depth = 1;
		for (int i = start + 1; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '{') depth++;
			else if (c == '}' && --depth == 0) return source.substring(start + 1, i);
		}
		throw new AssertionError("unterminated method body: " + signature);
	}

	private static String compact(String source) {
		return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\\r\\n]*", "").replaceAll("\\s+", "");
	}

	private static String read(String path) {
		try {
			return compact(Files.readString(Path.of(path)));
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void assertTime(CapturedTime actual, float phaseTick, float completion, float fullTick, String label) {
		check(actual != null, label + ": missing capture");
		near(actual.phaseTick(), phaseTick, label + ": phase tick");
		near(actual.phaseCompletion(), completion, label + ": completion");
		near(actual.fullTick(), fullTick, label + ": full tick");
	}

	private static void near(float actual, float expected, String label) {
		check(Float.isFinite(actual) && Math.abs(actual - expected) <= 0.00001F,
				label + ": expected " + expected + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
