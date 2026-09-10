package com.github.standobyte.jojo.client.entityrender;

import com.github.standobyte.jojo.client.entityanim.barrage.BarrageSwings;
import com.github.standobyte.jojo.client.entityanim.pose.AnimFramePose;
import com.github.standobyte.jojo.client.entityanim.pose.AnimatedEntity.PoseType;
import com.github.standobyte.jojo.mixin.client.modelanim.AnimatedEntityMixin;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;

public final class RipplesPlayerRenderStateSmokeTest {
	private RipplesPlayerRenderStateSmokeTest() {}

	public static void main(String[] args) {
		run();
		System.out.println("Player render-state pose preservation passed: addon, persistent, direct, idle.");
	}

	public static void run() {
		RipplesPlayerRenderState state = new RipplesPlayerRenderState();
		AnimatedEntityMixin frames = new AnimatedEntityMixin();
		AnimFramePose addonPose = pose("right_arm", -1.5F);
		AnimFramePose persistentPose = pose("left_arm", -0.9F);

		// These are the current FINAL snapshots copied by EntityActionRenderState.extract.
		frames.jojo_ripples$setModelPose(PoseType.FINAL, addonPose);
		AnimFramePose currentFinal = frames.jojo_ripples$getModelPose(PoseType.FINAL);
		state.entityAction.pose = currentFinal;
		seedActionMetadata(state);
		boolean hasActivePose = RipplesPlayerRenderState.finishExtraction(state, false);
		check(state.entityAction.pose == currentFinal,
				"an addon FINAL pose was cleared because its action belongs to a Stand");
		check(state.entityAction.pose.getIfPresent("right_arm").rotationOffset.x == -1.5F,
				"the addon casting arm pose changed during extraction");
		check(hasActivePose, "an active addon pose did not suppress the vanilla crouch override");
		checkNoActionMetadata(state);

		frames.jojo_ripples$setModelPose(PoseType.FINAL, persistentPose);
		currentFinal = frames.jojo_ripples$getModelPose(PoseType.FINAL);
		state.entityAction.pose = currentFinal;
		seedActionMetadata(state);
		RipplesPlayerRenderState.finishExtraction(state, false);
		check(state.entityAction.pose == currentFinal
				&& currentFinal.getIfPresent("left_arm").rotationOffset.x == -0.9F
				&& currentFinal.getIfPresent("right_arm") == null,
				"a persistent FINAL pose was cleared or replaced by the preceding addon pose");
		checkNoActionMetadata(state);

		frames.jojo_ripples$setModelPose(PoseType.FINAL, addonPose);
		currentFinal = frames.jojo_ripples$getModelPose(PoseType.FINAL);
		state.entityAction.pose = currentFinal;
		seedActionMetadata(state);
		BarrageSwings directBarrage = state.entityAction.barrageSwings;
		ActionAnimIdentifier directAnim = state.entityAction.animId;
		RipplesPlayerRenderState.finishExtraction(state, true);
		check(state.entityAction.pose == currentFinal
				&& state.entityAction.barrageSwings == directBarrage
				&& state.entityAction.animId == directAnim
				&& state.entityAction.armsObstructView,
				"direct player-action render state changed");

		// A new idle frame supplies null, even when this render state previously held a pose.
		frames.jojo_ripples$setModelPose(PoseType.UNMODIFIED, addonPose);
		frames.jojo_ripples$setModelPose(PoseType.UNMODIFIED, null);
		frames.jojo_ripples$setModelPose(PoseType.FINAL, null);
		state.entityAction.pose = frames.jojo_ripples$getModelPose(PoseType.FINAL);
		hasActivePose = RipplesPlayerRenderState.finishExtraction(state, false);
		check(state.entityAction.pose == null, "an idle frame resurrected a stale FINAL pose");
		check(frames.jojo_ripples$getModelPose(PoseType.UNMODIFIED) == null,
				"an idle frame retained the previous unmodified pose");
		check(!hasActivePose, "an idle frame retained the active-pose crouch override");
		checkNoActionMetadata(state);
	}

	private static AnimFramePose pose(String arm, float rotation) {
		AnimFramePose pose = new AnimFramePose();
		pose.getForModelPart(arm).rotationOffset.x = rotation;
		return pose;
	}

	private static void seedActionMetadata(RipplesPlayerRenderState state) {
		state.entityAction.barrageSwings = new BarrageSwings();
		state.entityAction.animId = ActionAnimIdentifier.getOrCreate("flame_burst", false);
		state.entityAction.armsObstructView = true;
	}

	private static void checkNoActionMetadata(RipplesPlayerRenderState state) {
		check(state.entityAction.barrageSwings == null
				&& state.entityAction.animId == null
				&& !state.entityAction.armsObstructView,
				"no-action extraction retained stale attack metadata");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
