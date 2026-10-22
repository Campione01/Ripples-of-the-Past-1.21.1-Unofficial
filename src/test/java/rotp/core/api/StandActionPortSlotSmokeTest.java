package rotp.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * FEATURE_STAND_ACTION_PORT_SLOT_V1: EntityActionAbility#performsInUsersStandPowerSlot (a port of a 1.16 StandAction runs
 * in its user's Stand-power slot, false by default, true for the time stop), EntityActionInstance#posesItsStand (false by
 * default, true for the time stop's action) and LivingComponentAction#getPoseComponent. StandActionPortSlot116GameTests
 * checks the behaviour.
 */
final class StandActionPortSlotSmokeTest {
	private StandActionPortSlotSmokeTest() {}

	static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_ACTION_PORT_SLOT_V1)
				&& "stand_action_port_slot_v1".equals(RotpAddonApi.FEATURE_STAND_ACTION_PORT_SLOT_V1)
				&& RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1),
				"Stand action port slot feature missing");
		try {
			Method slotHook = EntityActionAbility.class.getDeclaredMethod("performsInUsersStandPowerSlot");
			check(overridableBoolean(slotHook), "EntityActionAbility#performsInUsersStandPowerSlot() must be a public"
					+ " overridable boolean");
			Method poseHook = EntityActionInstance.class.getDeclaredMethod("posesItsStand");
			check(overridableBoolean(poseHook), "EntityActionInstance#posesItsStand() must be a public overridable boolean");
			Method poseComponent = LivingComponentAction.class.getDeclaredMethod("getPoseComponent", LivingEntity.class);
			check(Modifier.isPublic(poseComponent.getModifiers()) && Modifier.isStatic(poseComponent.getModifiers()),
					"LivingComponentAction#getPoseComponent(LivingEntity) must be public static");
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Stand action port slot API surface missing", error);
		}
		EntityActionAbility plain = new AbilityType<EntityActionAbility>(id("rotp_test", "plain_action"),
				(type, abilityId) -> new EntityActionAbility(type, abilityId, EntityActionInstance::new))
				.createInstance(new AbilityId(null, id("rotp_test", "stand"), "plain_action"));
		check(!plain.performsInUsersStandPowerSlot(),
				"an action must stay in its performer's slot by default, as 1.16's Stand task did");
		check(!new EntityActionInstance(null).posesItsStand(),
				"an action must not pose its Stand from its user's slot by default: 1.16 drew a Stand's pose from its task");
		TimeStopAbility timeStop = new AbilityType<TimeStopAbility>(id("jojo_ripples", "time_stop"), TimeStopAbility::new)
				.createInstance(new AbilityId(null, id("jojo_ripples", "the_world"), "time_stop"));
		check(timeStop.performsInUsersStandPowerSlot(),
				"the time stop, a 1.16 StandAction its user's power held, must run in its user's Stand-power slot");
		check(new TimeStopAbility.TimeStopAction(timeStop).posesItsStand(),
				"the time stop must draw its Stand's windup pose, as 1.16 StandEntity.getCurPose did");
	}

	private static boolean overridableBoolean(Method method) {
		int modifiers = method.getModifiers();
		return Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)
				&& method.getReturnType() == boolean.class;
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
