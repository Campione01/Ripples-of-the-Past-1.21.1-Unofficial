package rotp.core.client.entityanim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

import org.joml.Vector3f;

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
 * Cool poses are calculated while a resource reload parses stand skins, which happens on vanilla's
 * background reload workers rather than on the render thread. Everything that path writes through
 * therefore has to belong to the worker, not to the render thread that keeps drawing meanwhile.
 */
public final class AnimPoseThreadIsolationSmokeTest {
	private AnimPoseThreadIsolationSmokeTest() {}

	public static void main(String[] args) {
		run();
		System.out.println("Anim pose thread isolation smoke tests passed: parse-time poses keep off the "
				+ "render thread's shared scratch, and a reload does not disturb a frame in flight");
	}

	public static void run() {
		verifyStaticPosesLeaveRenderScratchUntouched();
		verifyStaticPosesMatchTheRenderPath();
		verifyReloadDoesNotCorruptFramesInFlight();
	}

	private static void verifyStaticPosesLeaveRenderScratchUntouched() {
		AnimFramePose.reused.clear();
		check(AnimFramePose.reused.pose.isEmpty(), "test fixture failed to empty the shared render pose");

		RotpAnimDefinition anim = definition();
		anim.initStaticPoses(timestamps());

		check(AnimFramePose.reused.pose.isEmpty(),
				"calculating cool poses wrote into the render thread's shared pose");
		check(anim.coolPoses != null && anim.coolPoses.size() == timestamps().size(),
				"cool poses were not calculated");
	}

	private static void verifyStaticPosesMatchTheRenderPath() {
		// The off-thread path has to produce the same numbers as the shared-scratch one, or the fix
		// would trade a race for a silently different pose.
		RotpAnimDefinition offThread = definition();
		offThread.initStaticPoses(timestamps());

		RotpAnimDefinition onRenderThread = definition();
		FloatList timestamps = timestamps();
		for (int i = 0; i < timestamps.size(); i++) {
			String expected = describe(onRenderThread.calcAnimPose(null, null, timestamps.getFloat(i), 1));
			check(describe(offThread.coolPoses.get(i)).equals(expected),
					"an off-thread cool pose differs from the same frame calculated on the render thread");
		}
	}

	private static void verifyReloadDoesNotCorruptFramesInFlight() {
		FloatList timestamps = timestamps();
		RotpAnimDefinition rendered = definition();
		String[] expected = new String[timestamps.size()];
		for (int i = 0; i < timestamps.size(); i++) {
			expected[i] = describe(rendered.calcAnimPose(null, null, timestamps.getFloat(i), 1));
		}

		int reloadWorkers = 4;
		int iterations = 400;
		AtomicBoolean renderDone = new AtomicBoolean();
		CyclicBarrier start = new CyclicBarrier(reloadWorkers + 1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		List<Thread> workers = new ArrayList<>();

		// One render thread, because that is how many there are; several reload workers, because
		// vanilla submits every reload listener's prepare() to the same background executor.
		Thread renderThread = new Thread(() -> {
			try {
				start.await();
				for (int pass = 0; pass < iterations; pass++) {
					for (int i = 0; i < timestamps.size(); i++) {
						check(describe(rendered.calcAnimPose(null, null, timestamps.getFloat(i), 1)).equals(expected[i]),
								"a frame calculated during a reload disagrees with the undisturbed one");
					}
				}
			}
			catch (Throwable error) {
				failures.add(error);
			}
			finally {
				renderDone.set(true);
			}
		}, "anim-render");
		workers.add(renderThread);

		for (int i = 0; i < reloadWorkers; i++) {
			Thread worker = new Thread(() -> {
				try {
					start.await();
					while (!renderDone.get()) {
						// Each worker parses its own definition, the way a reload builds new ones.
						RotpAnimDefinition parsed = definition();
						parsed.initStaticPoses(timestamps());
						for (int frame = 0; frame < expected.length; frame++) {
							check(describe(parsed.coolPoses.get(frame)).equals(expected[frame]),
									"a cool pose parsed during rendering disagrees with the undisturbed one");
						}
					}
				}
				catch (Throwable error) {
					failures.add(error);
				}
			}, "anim-reload-" + i);
			workers.add(worker);
		}

		for (Thread worker : workers) worker.start();
		for (Thread worker : workers) {
			try {
				worker.join();
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new AssertionError("interrupted while waiting for an anim worker", interrupted);
			}
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("pose calculation is not thread-isolated: " + failures.get(0), failures.get(0));
		}
	}


	private static FloatList timestamps() {
		return new FloatArrayList(new float[] { 0, 0.25F, 0.5F, 0.75F, 1 });
	}

	private static RotpAnimDefinition definition() {
		Map<String, List<IAnimationChannel>> bones = Map.of(
				"body", List.of(
						channel(AnimationChannel.Targets.ROTATION, new float[][] {
								{ 0, 0, 0 }, { 30, -15, 5 }, { -10, 20, 0 } }),
						channel(AnimationChannel.Targets.POSITION, new float[][] {
								{ 0, 0, 0 }, { 1, 2, -3 }, { 0, -4, 0 } })),
				"head_rot", List.of(
						channel(AnimationChannel.Targets.ROTATION, new float[][] {
								{ 5, 0, 0 }, { -25, 12, 0 }, { 0, 0, 7 } })),
				"left_arm", List.of(
						channel(AnimationChannel.Targets.SCALE, new float[][] {
								{ 1, 1, 1 }, { 1.5F, 0.5F, 2 }, { 1, 1, 1 } })));
		return new RotpAnimDefinition(1, OptionalFloat.empty(), bones, null, null);
	}

	private static AnimationChannelQuery channel(AnimationChannel.Target target, float[][] values) {
		KeyframeQuery[] keyframes = new KeyframeQuery[values.length];
		for (int i = 0; i < values.length; i++) {
			keyframes[i] = KeyframeQuery.constant(
					new Vector3f(values[i][0], values[i][1], values[i][2]),
					i / (float) (values.length - 1),
					Interpolations.LINEAR);
		}
		return new AnimationChannelQuery(target, keyframes);
	}

	private static String describe(AnimFramePose pose) {
		StringBuilder described = new StringBuilder();
		for (String modelPart : pose.pose.keySet().stream().sorted().toList()) {
			ModelPartFrame frame = pose.getIfPresent(modelPart);
			described.append(modelPart).append('=')
					.append(frame.positionOffset).append(frame.rotationOffset).append(frame.scaleOffset).append(';');
		}
		return described.toString();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
