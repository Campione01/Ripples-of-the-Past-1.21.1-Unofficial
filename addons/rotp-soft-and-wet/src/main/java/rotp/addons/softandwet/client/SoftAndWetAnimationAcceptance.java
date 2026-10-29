package rotp.addons.softandwet.client;

import java.util.List;

public final class SoftAndWetAnimationAcceptance {
    private static final float MIN_POSE_MAGNITUDE = 1.0E-4F;

    private SoftAndWetAnimationAcceptance() {}

    public static boolean frameAccepted(
            FrameEvidence frame,
            String expectedAnimationOwner,
            String expectedPhase) {
        return frame != null
                && expectedAnimationOwner.equals(frame.animationOwner())
                && expectedPhase.equals(frame.phase())
                && frame.exactSelectedDefinition()
                && frame.selectedAnimId() != null
                && !frame.selectedAnimId().isBlank()
                && frame.definitionClass() != null
                && !frame.definitionClass().isBlank()
                && frame.definitionIdentity() != 0
                && frame.expectedDefinitionIdentity() != 0
                && frame.definitionIdentity()
                        == frame.expectedDefinitionIdentity()
                && frame.selectedDefinitionPoseHash() != 0L
                && frame.selectedDefinitionPoseMatches()
                && frame.unmodifiedPoseHash() != 0L
                && frame.finalPoseHash() != 0L
                && frame.modelPoseHash() != 0L
                && frame.animatedBoneCount() > 0
                && frame.appliedBoneCount() > 0
                && frame.poseMagnitude() > MIN_POSE_MAGNITUDE
                && frame.modelPoseApplied();
    }

    public static boolean timelineAccepted(
            List<FrameEvidence> frames,
            String expectedAnimationOwner,
            String expectedPhase,
            boolean motionRequired) {
        if (frames == null || frames.size() < 2
                || frames.stream().anyMatch(frame -> !frameAccepted(
                        frame,
                        expectedAnimationOwner,
                        expectedPhase))) {
            return false;
        }
        long phaseTicks = frames.stream()
                .map(frame -> (int) Math.floor(frame.phaseTick()))
                .distinct()
                .limit(2)
                .count();
        long definitions = frames.stream()
                .map(FrameEvidence::definitionIdentity)
                .distinct()
                .limit(2)
                .count();
        if (phaseTicks < 2 || definitions != 1) {
            return false;
        }
        if (!motionRequired) {
            return true;
        }
        return frames.stream()
                .map(FrameEvidence::finalPoseHash)
                .distinct()
                .limit(2)
                .count() >= 2
                && frames.stream()
                        .map(FrameEvidence::modelPoseHash)
                        .distinct()
                        .limit(2)
                        .count() >= 2;
    }

    public record FrameEvidence(
            long frameId,
            float phaseTick,
            String phase,
            String animationOwner,
            String selectedAnimId,
            String definitionClass,
            int definitionIdentity,
            int expectedDefinitionIdentity,
            boolean exactSelectedDefinition,
            long selectedDefinitionPoseHash,
            boolean selectedDefinitionPoseMatches,
            long unmodifiedPoseHash,
            long finalPoseHash,
            long modelPoseHash,
            int animatedBoneCount,
            int appliedBoneCount,
            float poseMagnitude,
            boolean modelPoseApplied) {}
}
