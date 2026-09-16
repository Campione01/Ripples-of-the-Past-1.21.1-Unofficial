package rotp.core.client.entityanim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonArray;

import rotp.core.client.entityanim.molang.AnimMolangQuery;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.client.entityanim.molang.animelement.AnimationChannelQuery;
import rotp.core.client.entityanim.molang.animelement.IAnimationChannel;
import rotp.core.client.entityanim.molang.animelement.KeyframeQuery;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimFramePose.ModelPartFrame;
import rotp.core.util.objects_java.OptionalFloat;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationChannel.Interpolations;

/**
 * The Mocha scope holds a single {@link AnimMolangQuery}, and both the render thread and vanilla's
 * resource-reload workers evaluate keyframe queries through it. The render thread fills it with the
 * live entity's head rotation every frame; a worker baking cool poses resets it to zero first. While
 * the three properties were shared, those two interleaved in both directions - and the damage in the
 * baking direction outlives the reload, because a cool pose that captured a live head rotation keeps
 * that rotation until the next reload.
 *
 * <p>{@link AnimPoseThreadIsolationSmokeTest} covers the pose scratch; it cannot cover this, because
 * its keyframes are numeric literals and so never reach the Molang engine at all.
 */
public final class MolangQueryThreadIsolationSmokeTest {
	private MolangQueryThreadIsolationSmokeTest() {}

	/** Any value a head rotation can take that is clearly not the reset value. */
	private static final float LIVE_HEAD_ROTATION = 41;

	public static void main(String[] args) {
		run();
		System.out.println("Molang query thread isolation smoke tests passed: a reload worker's reset and "
				+ "the render thread's live head rotation no longer overwrite each other");
	}

	public static void run() {
		KeyframesMolangEngine.init();
		verifyResetOnAnotherThreadLeavesTheFilledContextAlone();
		verifyFillOnAnotherThreadDoesNotLeakIntoAReset();
		verifyBakedCoolPosesNeverCaptureALiveHeadRotation();
	}

	/**
	 * Render thread's direction: a worker resetting mid-frame must not blank the context the render
	 * thread already filled. Barrier-ordered, so this fails every time on shared state, not sometimes.
	 */
	private static void verifyResetOnAnotherThreadLeavesTheFilledContextAlone() {
		CyclicBarrier gate = new CyclicBarrier(2);
		float[] seenByRenderThread = new float[1];

		runTogether(
				() -> {
					AnimMolangQuery.instance.fillContext(AnimMolangVariables.set(LIVE_HEAD_ROTATION, 0, 0));
					await(gate);
					await(gate);
					seenByRenderThread[0] = headXRotation();
				},
				() -> {
					await(gate);
					AnimMolangQuery.instance.reset();
					await(gate);
					check(headXRotation() == 0, "a reload worker read the render thread's head rotation after reset()");
				});

		check(seenByRenderThread[0] == LIVE_HEAD_ROTATION,
				"a reload worker's reset() blanked the head rotation the render thread had filled, saw "
						+ seenByRenderThread[0]);
	}

	/**
	 * The worse direction: a worker resets, the render thread fills before the worker evaluates, and
	 * the worker bakes a live entity's head rotation into a pose that then stays wrong until the next
	 * reload.
	 */
	private static void verifyFillOnAnotherThreadDoesNotLeakIntoAReset() {
		CyclicBarrier gate = new CyclicBarrier(2);
		float[] seenByReloadWorker = new float[1];

		runTogether(
				() -> {
					AnimMolangQuery.instance.reset();
					await(gate);
					await(gate);
					seenByReloadWorker[0] = headXRotation();
				},
				() -> {
					await(gate);
					AnimMolangQuery.instance.fillContext(AnimMolangVariables.set(LIVE_HEAD_ROTATION, 0, 0));
					await(gate);
					check(headXRotation() == LIVE_HEAD_ROTATION,
							"the render thread lost its own head rotation to a reload worker's reset()");
				});

		check(seenByReloadWorker[0] == 0,
				"a cool pose baked on a reload worker captured the render thread's live head rotation, saw "
						+ seenByReloadWorker[0]);
	}

	/**
	 * The same thing end to end: workers bake cool poses out of a query-driven channel the way a
	 * resource reload does, while the render thread keeps drawing with a live head rotation.
	 */
	private static void verifyBakedCoolPosesNeverCaptureALiveHeadRotation() {
		int reloadWorkers = 4;
		int renderIterations = 4000;
		FloatList timestamps = timestamps();
		RotpAnimDefinition rendered = definition();

		AtomicBoolean renderDone = new AtomicBoolean();
		CyclicBarrier start = new CyclicBarrier(reloadWorkers + 1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		List<Thread> threads = new ArrayList<>();

		// One render thread, because that is how many there are.
		Thread renderThread = new Thread(() -> {
			try {
				await(start);
				for (int pass = 0; pass < renderIterations; pass++) {
					for (int i = 0; i < timestamps.size(); i++) {
						AnimFramePose frame = rendered.calcAnimPose(
								AnimMolangVariables.set(LIVE_HEAD_ROTATION, 0, 0), null, timestamps.getFloat(i), 1);
						check(headXOf(frame) == LIVE_HEAD_ROTATION,
								"a frame drawn during a reload lost its live head rotation, saw " + headXOf(frame));
					}
				}
			}
			catch (Throwable error) {
				failures.add(error);
			}
			finally {
				renderDone.set(true);
			}
		}, "molang-render");
		threads.add(renderThread);

		for (int i = 0; i < reloadWorkers; i++) {
			threads.add(new Thread(() -> {
				try {
					await(start);
					while (!renderDone.get()) {
						// Each worker parses its own definition, the way a reload builds new ones.
						RotpAnimDefinition parsed = definition();
						parsed.initStaticPoses(timestamps());
						for (AnimFramePose coolPose : parsed.coolPoses) {
							check(headXOf(coolPose) == 0,
									"a cool pose baked during rendering captured a live head rotation, saw "
											+ headXOf(coolPose));
						}
					}
				}
				catch (Throwable error) {
					failures.add(error);
				}
			}, "molang-reload-" + i));
		}

		for (Thread thread : threads) thread.start();
		for (Thread thread : threads) join(thread);

		if (!failures.isEmpty()) {
			throw new AssertionError("molang query evaluation is not thread-isolated: " + failures.get(0),
					failures.get(0));
		}
	}


	private static float headXRotation() {
		return (float) AnimMolangQuery.instance.getProperty("head_x_rotation").value().getAsNumber();
	}

	/** The x offset of the one bone the fixture animates, which is driven straight by the query. */
	private static float headXOf(AnimFramePose pose) {
		ModelPartFrame frame = pose.getIfPresent("head_rot");
		check(frame != null, "the fixture animation did not produce the bone it animates");
		return frame.positionOffset.x();
	}

	private static FloatList timestamps() {
		return new FloatArrayList(new float[] { 0, 0.5F, 1 });
	}

	/**
	 * A single bone whose x position is the head rotation query itself, so whatever the evaluation
	 * read shows up unchanged in the pose. POSITION only scales y, leaving x alone.
	 */
	private static RotpAnimDefinition definition() {
		KeyframeQuery[] keyframes = new KeyframeQuery[3];
		for (int i = 0; i < keyframes.length; i++) {
			JsonArray vec = new JsonArray();
			vec.add("query.head_x_rotation");
			vec.add("0");
			vec.add("0");
			keyframes[i] = KeyframeQuery.parseJsonVec(vec, i / (float) (keyframes.length - 1), Interpolations.LINEAR);
			check(!keyframes[i].isNumericLiteral(),
					"the fixture keyframe was folded to a literal, so it would never reach the Molang engine");
		}
		Map<String, List<IAnimationChannel>> bones = Map.of(
				"head_rot", List.<IAnimationChannel>of(
						new AnimationChannelQuery(AnimationChannel.Targets.POSITION, keyframes)));
		return new RotpAnimDefinition(1, OptionalFloat.empty(), bones, null, null);
	}

	private static void runTogether(Runnable first, Runnable second) {
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		Thread a = new Thread(guard(first, failures), "molang-a");
		Thread b = new Thread(guard(second, failures), "molang-b");
		a.start();
		b.start();
		join(a);
		join(b);
		if (!failures.isEmpty()) {
			throw new AssertionError(failures.get(0).getMessage(), failures.get(0));
		}
	}

	private static Runnable guard(Runnable body, List<Throwable> failures) {
		return () -> {
			try {
				body.run();
			}
			catch (Throwable error) {
				failures.add(error);
			}
		};
	}

	private static void await(CyclicBarrier barrier) {
		try {
			barrier.await();
		}
		catch (Exception interrupted) {
			throw new AssertionError("interrupted while ordering the molang threads", interrupted);
		}
	}

	private static void join(Thread thread) {
		try {
			thread.join();
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError("interrupted while waiting for a molang thread", interrupted);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
