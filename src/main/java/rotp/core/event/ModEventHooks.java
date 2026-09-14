package rotp.core.event;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.api.timestop.TimeStopLifecycleEvent.RemovalReason;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

@ApiStatus.Internal
public abstract class ModEventHooks {

	public static RipplesAbilityKeyPressEvent onAbilityKeyPress(LivingEntity user, Ability ability, 
			InputMethod inputMethod, float clickHoldResolveTime) {
		RipplesAbilityKeyPressEvent event = new RipplesAbilityKeyPressEvent(user, ability, inputMethod, clickHoldResolveTime);
		event = NeoForge.EVENT_BUS.post(event);
		return event;
	}

	public static TimeStopLifecycleEvent.PreStart onTimeStopPreStart(
			ServerLevel level, TimeStopState.Instance instance) {
		return NeoForge.EVENT_BUS.post(
				new TimeStopLifecycleEvent.PreStart(level, instance));
	}

	public static void onTimeStopAdded(
			ServerLevel level, TimeStopState.Instance instance) {
		NeoForge.EVENT_BUS.post(
				new TimeStopLifecycleEvent.Added(level, instance));
	}

	public static void onTimeStopRemoved(
			ServerLevel level,
			TimeStopState.Instance instance,
			RemovalReason reason) {
		NeoForge.EVENT_BUS.post(
				new TimeStopLifecycleEvent.Removed(level, instance, reason));
	}
}
