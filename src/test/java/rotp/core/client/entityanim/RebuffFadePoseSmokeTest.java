package rotp.core.client.entityanim;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.joml.Vector3f;

import com.google.gson.JsonParser;

import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.AnimMolangQuery;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.client.entityanim.molang.animelement.AnimationChannelQuery;
import rotp.core.client.entityanim.molang.animelement.IAnimationChannel;
import rotp.core.client.entityanim.molang.animelement.KeyframeQuery;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimFramePose.ModelPartFrame;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.LivingComponentAction.RebuffVisualTail;
import rotp.core.powersystem.entityaction.LivingComponentAction.TransactionSnapshot;
import rotp.core.util.objects_java.OptionalFloat;
import rotp.core.impl.powers.hamon.client.particle.custom.HamonAuraBodyPositionSmokeTest;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;

/** Exercises model transforms and immutable tail math, not action lifecycle or rendered-client behavior. */
public final class RebuffFadePoseSmokeTest {
	private static final float EPSILON = 0.00001F;
	private static final float[] VANILLA = {
			11, 12, 13, 0.75F, -0.625F, 1.25F, 1.25F, 2, 1.5F };
	private static final float[] ANIMATED = {
			6, -6, 14, 0.5F, 0.5F, -0.125F, 1.5F, 0.75F, 1.75F };
	// Independent numeric oracles: time, x, y, z before position/degree conversion.
	private static final float[][] CURVE_SAMPLES = {
			{ 0.125F, 0.125F, 2.5F, -2.99609375F },
			{ 0.625F, 3.125F, 3.75F, -2.51171875F },
			{ 0.875F, 6.125F, 3.25F, -1.78125F } };

	private RebuffFadePoseSmokeTest() {}

	public static void main(String[] args) {
		run();
		System.out.println("Rebuff fade pose smoke tests passed: eleven transform, Molang-clock, tail-math and snapshot checks; nine aura-offset checks");
	}

	public static void run() {
		KeyframesMolangEngine.init();
		verifyZeroWeightPreservesVanillaTransform();
		verifyFullWeightMatchesLegacyApply();
		verifyFadeWeightBlendsTowardAnimatedBindPose();
		verifyCopyAndResetKeepWeightLocal();
		verifyOrdinaryPoseAfterFade();
		verifyHeadYawUsesDonorWrapping();
		verifyTailAgeAndWeightArePure();
		verifyParsedMolangUsesFractionalAnimationTime();
		verifyResetAndCoolPosesUseTheirOwnSampleTime();
		verifyAnimationTimeIsThreadLocal();
		verifyTransactionSnapshotConstructorsPreserveTail();
		HamonAuraBodyPositionSmokeTest.run();
	}

	private static void verifyZeroWeightPreservesVanillaTransform() {
		ModelPart part = vanillaPart();
		animatedOffsets().apply(part, 0, true);
		assertTransform(part, VANILLA, "zero weight must retain vanilla, not restore bind pose");
	}

	private static void verifyFullWeightMatchesLegacyApply() {
		ModelPart weighted = vanillaPart();
		ModelPart legacy = vanillaPart();
		ModelPartFrame offsets = animatedOffsets();
		offsets.apply(weighted, 1, true);
		offsets.apply(legacy);
		assertTransform(weighted, ANIMATED, "full weight uses bind pose plus animation offsets");
		assertTransform(weighted, transform(legacy), "full weight must match the original apply path");
	}

	private static void verifyFadeWeightBlendsTowardAnimatedBindPose() {
		ModelPart part = vanillaPart();
		animatedOffsets().apply(part, 0.125F, false);
		assertTransform(part, new float[] {
				10.375F, 9.75F, 13.125F,
				0.71875F, -0.484375F, 1.078125F,
				1.28125F, 1.84375F, 1.53125F },
				"halfway OUTCUBIC fade blends the live vanilla transform, not scaled offsets alone");
	}

	private static void verifyCopyAndResetKeepWeightLocal() {
		AnimFramePose source = new AnimFramePose();
		near(source.blendWeight, 1, "new poses default to full weight");
		source.blendWeight = 0.125F;
		animatedOffsets().copyTo(source.getForModelPart("right_arm"));
		AnimFramePose copied = new AnimFramePose();
		copied.blendWeight = 0.75F;
		copied.getForModelPart("stale").rotationOffset.set(9, 8, 7);
		source.copyTo(copied);
		AnimFramePose deep = source.deepCopy();
		near(copied.blendWeight, 0.125F, "copyTo retains fade weight");
		near(deep.blendWeight, 0.125F, "deepCopy retains fade weight");
		check(copied.getIfPresent("stale") == null, "copyTo must remove stale parts");
		assertOffsets(copied.getIfPresent("right_arm"), "copyTo offsets");
		assertOffsets(deep.getIfPresent("right_arm"), "deepCopy offsets");
		deep.blendWeight = 0.5F;
		deep.getIfPresent("right_arm").positionOffset.set(100, 200, 300);
		near(source.blendWeight, 0.125F, "deepCopy weight is independent");
		assertOffsets(source.getIfPresent("right_arm"), "deepCopy must not share source vectors");
		assertOffsets(copied.getIfPresent("right_arm"), "copies must not share vectors");
		source.clear();
		near(source.blendWeight, 1, "clear restores default weight");
		check(source.pose.isEmpty(), "clear removes the active part map");
		ModelPartFrame reset = source.getForModelPart("right_arm");
		near(reset.positionOffset.lengthSquared(), 0, "cached position is reset");
		near(reset.rotationOffset.lengthSquared(), 0, "cached rotation is reset");
		near(reset.scaleOffset.lengthSquared(), 0, "cached scale is reset");
		near(copied.blendWeight, 0.125F, "clearing source does not reset its copy");
		assertOffsets(copied.getIfPresent("right_arm"), "clearing source must not mutate copied offsets");
	}

	private static void verifyOrdinaryPoseAfterFade() {
		Map<String, List<IAnimationChannel>> bones = Map.of("right_arm", List.of(
				new AnimationChannelQuery(AnimationChannel.Targets.ROTATION, new KeyframeQuery[] {
						KeyframeQuery.constant(new Vector3f(15, 0, 0), 0,
								AnimationChannel.Interpolations.LINEAR) })));
		RotpAnimDefinition ordinary = new RotpAnimDefinition(1, OptionalFloat.empty(), bones, null, null);
		AnimFramePose scratch = new AnimFramePose();
		scratch.blendWeight = 0.125F;
		animatedOffsets().copyTo(scratch.getForModelPart("right_arm"));
		scratch.getForModelPart("stale").rotationOffset.set(9, 8, 7);
		AnimFramePose result = ordinary.calcAnimPose(null, null, 0, 1, scratch, new Vector3f());
		check(result == scratch, "ordinary evaluator reuses the supplied scratch");
		near(result.blendWeight, 1, "ordinary calculation must not inherit the previous fade weight");
		check(result.getIfPresent("stale") == null, "ordinary calculation clears stale parts");
		ModelPart part = vanillaPart();
		result.getIfPresent("right_arm").apply(part, result.blendWeight, false);
		assertTransform(part, new float[] {
				2, -4, 6, 0.125F + (float) (Math.PI / 12), -0.25F, 0.5F, 1, 1, 1 },
				"ordinary pose after a fade has no residual weight, position or scale");
	}

	private static void verifyHeadYawUsesDonorWrapping() {
		float twoPi = (float) (Math.PI * 2);
		ModelPartFrame offsets = animatedOffsets();
		ModelPart head = vanillaPart();
		head.yRot = twoPi + 0.75F;
		offsets.apply(head, 0.125F, true);
		near(head.yRot, 0.71875F, "positive head yaw wraps before blending");
		near(head.xRot, 0.71875F, "head pitch still uses ordinary interpolation");
		near(head.zRot, 1.078125F, "head roll still uses ordinary interpolation");
		ModelPart negativeHead = vanillaPart();
		negativeHead.yRot = -twoPi - 0.75F;
		offsets.apply(negativeHead, 0.125F, true);
		near(negativeHead.yRot, -0.59375F, "negative head yaw wraps before blending");
		ModelPart otherBone = vanillaPart();
		otherBone.yRot = twoPi + 0.75F;
		offsets.apply(otherBone, 0.125F, false);
		near(otherBone.yRot, (twoPi + 0.75F) * 0.875F + 0.5F * 0.125F,
				"non-head yaw must not gain a new normalization policy");
		ModelPart zeroWeightHead = vanillaPart();
		zeroWeightHead.yRot = twoPi + 0.75F;
		offsets.apply(zeroWeightHead, 0, true);
		near(zeroWeightHead.yRot, twoPi + 0.75F, "zero weight must not rewrite even unwrapped head yaw");
	}

	private static void verifyTailAgeAndWeightArePure() {
		RebuffVisualTail tail = tailFixture();
		near(RebuffVisualTail.DURATION_TICKS, 10, "donor stop fade duration");
		near(tail.elapsedTicks(100, 0), 0, "tail starts at zero age");
		near(tail.elapsedTicks(101, 0.25F), 1.25F, "age combines entity ticks and partial tick once");
		near(tail.elapsedTicks(101, 0.25F), 1.25F, "repeated reads do not advance the clock");
		near(tail.elapsedTicks(99, 0), -1, "clock reversal remains visible to lifecycle invalidation");
		near(tail.elapsedTicks(101, -1), 1, "negative partial tick is clamped");
		near(tail.elapsedTicks(101, 2), 2, "partial tick above one is clamped");
		near(tail.blendWeight(tail.elapsedTicks(100, 0)), 1, "fade weight at tick zero");
		near(tail.blendWeight(tail.elapsedTicks(105, 0)), 0.125F, "OUTCUBIC remainder at tick five");
		near(tail.blendWeight(tail.elapsedTicks(110, 0)), 0, "fade weight at tick ten");
		near(tail.blendWeight(-1), 1, "negative age weight is clamped");
		near(tail.blendWeight(11), 0, "expired age weight is clamped");
	}

	private static void verifyParsedMolangUsesFractionalAnimationTime() {
		RotpAnimDefinition animation = parsedTimeAnimation();
		AnimFramePose scratch = new AnimFramePose();
		Vector3f target = new Vector3f();
		AnimMolangVariables variables = AnimMolangVariables.set(7, 11, 13);
		AnimMolangQuery.instance.fillContext(variables, 0.3125F);
		near(queryValue("anim_time"), 0.3125F, "fillContext accepts fractional clip seconds");
		near(queryValue("head_y_rotation"), 11, "fillContext preserves the entity query values");
		for (float[] sample : CURVE_SAMPLES) {
			AnimMolangQuery.instance.fillContext(variables, 99);
			AnimFramePose pose = animation.calcAnimPose(variables, null, sample[0], 1, scratch, target);
			near(queryValue("anim_time"), sample[0], "evaluation replaces stale animation time with clip seconds");
			assertCurve(pose, sample, 7, "parsed live Molang at " + sample[0]);
		}
		AnimMolangQuery.instance.reset();
	}

	private static void verifyResetAndCoolPosesUseTheirOwnSampleTime() {
		AnimMolangVariables variables = AnimMolangVariables.set(7, 11, 13);
		AnimMolangQuery.instance.fillContext(variables, 9);
		AnimMolangQuery.instance.reset(0.375F);
		near(queryValue("anim_time"), 0.375F, "reset retains the requested sample time");
		near(queryValue("head_x_rotation"), 0, "reset removes live head pitch");
		near(queryValue("head_y_rotation"), 0, "reset removes live head yaw");
		near(queryValue("extendablePartLength"), 0, "reset removes live extension");
		AnimMolangQuery.instance.fillContext(variables);
		near(queryValue("anim_time"), 0, "legacy fillContext defaults to zero time");
		RotpAnimDefinition parsed = parsedTimeAnimation();
		check(parsed.coolPoses != null && parsed.coolPoses.size() == CURVE_SAMPLES.length,
				"parsed coolPoseHere timeline must bake all sample timestamps");
		for (int i = 0; i < CURVE_SAMPLES.length; i++) {
			assertCurve(parsed.coolPoses.get(i), CURVE_SAMPLES[i], 0, "baked fractional cool pose " + i);
		}
		AnimFramePose pose = parsed.calcAnimPose(null, null, CURVE_SAMPLES[0][0], 1,
				new AnimFramePose(), new Vector3f());
		assertCurve(pose, CURVE_SAMPLES[0], 0, "null-context evaluation resets but retains clip time");
		AnimMolangQuery.instance.reset();
		near(queryValue("anim_time"), 0, "legacy reset defaults to zero time");
	}

	private static void verifyAnimationTimeIsThreadLocal() {
		CyclicBarrier gate = new CyclicBarrier(2);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread first = timeWorker(true, gate, failure);
		Thread second = timeWorker(false, gate, failure);
		first.start();
		second.start();
		try {
			first.join(10000);
			second.join(10000);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError("interrupted while joining Molang time workers", interrupted);
		}
		if (first.isAlive() || second.isAlive()) {
			first.interrupt();
			second.interrupt();
			throw new AssertionError("Molang time workers did not finish within their bounded waits");
		}
		if (failure.get() != null) throw new AssertionError("Molang sample-time isolation failed", failure.get());
	}

	private static Thread timeWorker(boolean first, CyclicBarrier gate, AtomicReference<Throwable> failure) {
		return new Thread(() -> {
			try {
				RotpAnimDefinition animation = parsedTimeAnimation();
				AnimFramePose scratch = new AnimFramePose();
				Vector3f target = new Vector3f();
				float[] sample = CURVE_SAMPLES[first ? 0 : 2];
				for (int i = 0; i < 12; i++) {
					if (!first) gate.await(5, TimeUnit.SECONDS);
					animation.calcAnimPose(null, null, sample[0], 1, scratch, target);
					if (first) gate.await(5, TimeUnit.SECONDS);
					gate.await(5, TimeUnit.SECONDS);
					near(queryValue("anim_time"), sample[0], "another thread must not replace this clip's time");
					assertCurve(scratch, sample, 0, "thread-owned parsed definition/scratch");
					gate.await(5, TimeUnit.SECONDS);
				}
			}
			catch (Throwable error) {
				failure.compareAndSet(null, error);
			}
		}, first ? "rebuff-time-first" : "rebuff-time-second");
	}

	private static void verifyTransactionSnapshotConstructorsPreserveTail() {
		TransactionSnapshot legacy = new TransactionSnapshot(null, null, 17, 41L, 3L);
		TransactionSnapshot explicitNull = new TransactionSnapshot(null, null, 17, 41L, 3L, null);
		check(legacy.equals(explicitNull), "legacy five-argument constructor delegates a null tail");
		check(legacy.rebuffVisualTail() == null, "legacy snapshot has no visual tail");
		RebuffVisualTail tail = tailFixture();
		TransactionSnapshot withTail = new TransactionSnapshot(null, null, 17, 41L, 3L, tail);
		check(withTail.action() == null && withTail.lifecycle() == null, "snapshot retains nullable action state");
		check(withTail.actionIdCounter() == 17 && withTail.actionGeneration() == 41L
				&& withTail.lifecycleRemovalAttempts() == 3L, "snapshot preserves existing counters");
		check(withTail.rebuffVisualTail() == tail, "six-argument snapshot retains the original immutable tail");
		check(withTail.rebuffVisualTail().startedTick() == 100, "snapshot must not restart the tail timestamp");
		near(withTail.rebuffVisualTail().elapsedTicks(105, 0.25F), 5.25F, "snapshot tail keeps its original age");
	}

	private static RebuffVisualTail tailFixture() {
		return new RebuffVisualTail(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), 100,
				ResourceLocation.fromNamespaceAndPath("jojo_ripples", "hamon"),
				new ActionAnimIdentifier("rebuff_overdrive", false), ActionPhase.PERFORM, 2, 0.2F, 16);
	}

	private static RotpAnimDefinition parsedTimeAnimation() {
		// Unequal axis breakpoints are intentional; this tests the query mechanism, not donor pose data.
		return ParseGeckoAnims.parseAnim(JsonParser.parseString("""
				{
				  "animation_length": 2,
				  "bones": { "right_arm": {
				    "position": [
				      "8 * query.anim_time * query.anim_time + query.head_x_rotation",
				      "query.anim_time < 0.5 ? 2 + 4 * query.anim_time : 4 - 2 * (query.anim_time - 0.5)",
				      "query.anim_time < 0.75 ? -3 + 2 * query.anim_time * query.anim_time * query.anim_time : -2.15625 + 3 * (query.anim_time - 0.75)"
				    ],
				    "rotation": [
				      "8 * query.anim_time * query.anim_time + query.head_x_rotation",
				      "query.anim_time < 0.5 ? 2 + 4 * query.anim_time : 4 - 2 * (query.anim_time - 0.5)",
				      "query.anim_time < 0.75 ? -3 + 2 * query.anim_time * query.anim_time * query.anim_time : -2.15625 + 3 * (query.anim_time - 0.75)"
				    ]
				  } },
				  "timeline": { "0.125": "coolPoseHere", "0.625": "coolPoseHere", "0.875": "coolPoseHere" }
				}
				""").getAsJsonObject());
	}

	private static void assertCurve(AnimFramePose pose, float[] sample, float headX, String message) {
		ModelPartFrame frame = pose.getIfPresent("right_arm");
		check(frame != null, message + " missing right_arm");
		float x = sample[1] + headX;
		near(frame.positionOffset.x(), x, message + " position x");
		near(frame.positionOffset.y(), -sample[2], message + " position y");
		near(frame.positionOffset.z(), sample[3], message + " position z");
		float radians = (float) (Math.PI / 180);
		near(frame.rotationOffset.x(), x * radians, message + " rotation x");
		near(frame.rotationOffset.y(), sample[2] * radians, message + " rotation y");
		near(frame.rotationOffset.z(), sample[3] * radians, message + " rotation z");
	}

	private static float queryValue(String name) {
		return (float) AnimMolangQuery.instance.getProperty(name).value().getAsNumber();
	}

	private static ModelPart vanillaPart() {
		ModelPart part = new ModelPart(List.of(), Map.of());
		part.setInitialPose(PartPose.offsetAndRotation(2, -4, 6, 0.125F, -0.25F, 0.5F));
		part.x = VANILLA[0];
		part.y = VANILLA[1];
		part.z = VANILLA[2];
		part.xRot = VANILLA[3];
		part.yRot = VANILLA[4];
		part.zRot = VANILLA[5];
		part.xScale = VANILLA[6];
		part.yScale = VANILLA[7];
		part.zScale = VANILLA[8];
		return part;
	}

	private static ModelPartFrame animatedOffsets() {
		ModelPartFrame frame = new ModelPartFrame();
		frame.positionOffset.set(4, -2, 8);
		frame.rotationOffset.set(0.375F, 0.75F, -0.625F);
		frame.scaleOffset.set(0.5F, -0.25F, 0.75F);
		return frame;
	}

	private static void assertOffsets(ModelPartFrame actual, String message) {
		check(actual != null, message + " missing part");
		ModelPart part = vanillaPart();
		actual.apply(part, 1, false);
		assertTransform(part, ANIMATED, message);
	}

	private static float[] transform(ModelPart part) {
		return new float[] { part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
				part.xScale, part.yScale, part.zScale };
	}

	private static void assertTransform(ModelPart actual, float[] expected, String message) {
		float[] values = transform(actual);
		for (int i = 0; i < values.length; i++) {
			near(values[i], expected[i], message + " component " + i);
		}
	}

	private static void near(float actual, float expected, String message) {
		if (!Float.isFinite(actual) || Math.abs(actual - expected) > EPSILON) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
