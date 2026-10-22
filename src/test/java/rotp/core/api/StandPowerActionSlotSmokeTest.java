package rotp.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionPhaseTimePacket;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionTargetPacket;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionWithOBBSyncPacket;
import rotp.core.powersystem.entityaction.syncdata.TrActionSynchedDataPacket;

import net.minecraft.world.entity.LivingEntity;

/**
 * FEATURE_STAND_POWER_ACTION_SLOT_V1: the user's Stand-power action slot (LivingComponentAction.StandPowerSlot) and the
 * two-argument accessors that take a power class, the slot ids the sync packets carry, the order the user's two slots
 * tick in (the non-Stand power's first, by class name in EntityDataOrder) and one input buffer per power.
 * StandPowerActionSlot116GameTests checks the behaviour.
 */
final class StandPowerActionSlotSmokeTest {
	private StandPowerActionSlotSmokeTest() {}

	static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1)
				&& "stand_power_action_slot_v1".equals(RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1),
				"Stand-power action slot feature missing");
		try {
			for (String name : new String[] { "getComponent", "getExistingComponent", "getCurEntityAction" }) {
				Method accessor = LivingComponentAction.class.getDeclaredMethod(name, LivingEntity.class, PowerClass.class);
				check(Modifier.isPublic(accessor.getModifiers()) && Modifier.isStatic(accessor.getModifiers()),
						"LivingComponentAction#" + name + "(LivingEntity, PowerClass) must be public static");
				Method old = LivingComponentAction.class.getDeclaredMethod(name, LivingEntity.class);
				check(Modifier.isPublic(old.getModifiers()) && Modifier.isStatic(old.getModifiers()),
						"LivingComponentAction#" + name + "(LivingEntity) must stay public static");
			}
			check(EntityActionInstance.class.getDeclaredMethod("actionSlot").getReturnType() == int.class
					&& LivingComponentAction.class.getDeclaredMethod("slotId").getReturnType() == int.class
					&& EntityActionInputState.class.getDeclaredMethod("inputBuffer", PowerClass.class) != null,
					"the slot ids and the per-power input buffer must be reachable");
			for (Class<?> packet : new Class<?>[] { TrEntityActionPhaseTimePacket.class, TrEntityActionTargetPacket.class,
					TrEntityActionWithOBBSyncPacket.class, TrActionSynchedDataPacket.class }) {
				check(packet.getDeclaredMethod("slot").getReturnType() == int.class, packet.getSimpleName() + " carries no slot");
			}
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Stand-power action slot API surface missing", error);
		}
		Class<?> slot = LivingComponentAction.StandPowerSlot.class;
		check(slot.getSuperclass() == LivingComponentAction.class && Modifier.isFinal(slot.getModifiers()),
				"the Stand-power slot must be a final LivingComponentAction");
		// DataEventListeners keys an entity's ticking data by class, ordered by EntityDataOrder, then by class name
		check(LivingComponentAction.class.getName().compareTo(slot.getName()) < 0,
				"the user's own slot (its non-Stand power's actions) must tick before its Stand-power slot, as 1.16 ticked the"
						+ " non-Stand power first");
		check(LivingComponentAction.MAIN_SLOT == 0 && LivingComponentAction.STAND_POWER_SLOT == 1,
				"slot ids changed: the old packet constructors send slot 0");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
