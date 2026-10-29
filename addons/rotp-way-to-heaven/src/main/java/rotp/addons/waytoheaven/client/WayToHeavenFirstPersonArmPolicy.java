package rotp.addons.waytoheaven.client;

public final class WayToHeavenFirstPersonArmPolicy {
    private WayToHeavenFirstPersonArmPolicy() {}

    public static boolean shouldRender(
            boolean eventCanceled,
            boolean renderingExtraArm,
            boolean mainHandEvent,
            boolean playerInvisible,
            boolean abilitySelectedAndEnabled,
            boolean offHandFree,
            boolean vanillaAlreadyRendersOffHandArm) {
        return !eventCanceled
                && !renderingExtraArm
                && mainHandEvent
                && !playerInvisible
                && abilitySelectedAndEnabled
                && offHandFree
                && !vanillaAlreadyRendersOffHandArm;
    }
}
