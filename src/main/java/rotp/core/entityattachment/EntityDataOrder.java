package rotp.core.entityattachment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import rotp.core.JojoModLivingVariables;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectsMap;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonHypnosisState;
import rotp.core.impl.powers.hamon.ProjectileHamonChargeState;
import rotp.core.impl.stands.goldexperience.GELifeshotState;
import rotp.core.impl.stands.goldexperience.GEStuckObjectsState;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformState;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.mechanics.clothes.EntityClothesInventory;
import rotp.core.mechanics.coffin.PlayerCoffinSleepData;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.entity_externalcontainer.PlayerExternalContainers;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;

/**
 * The order in which {@link DataEventListeners} visits the data attached to one entity. Data of a listed class (or of
 * a subclass) takes that class's place; any other data follows, ordered by class name.
 */
final class EntityDataOrder {
	/**
	 * The order 1.16 ticked the same things in, for the data that reads other data within a tick: an action ends and
	 * sets its cooldown before its power counts cooldowns down and regenerates energy or stamina, the non-Stand power
	 * ticks before the Stand power, and a queued input is replayed against this tick's power state.
	 */
	private static final List<Class<?>> TICK = List.of(
			// vanilla ServerPlayerEntity.tick followed the possessed entity in the level's entity tick
			LivingComponentPossession.class,
			// GameplayEventHandler.onPlayerTick START: PlayerUtilCap.tick, ending with the continuous action
			PlayerVoiceLineData.class,
			GELifeshotState.class,
			GoldExperienceLifeformState.class,
			PlayerCoffinSleepData.class,
			LivingComponentAction.class,
			// then INonStandPower.tick and IStandPower.tick; PowerBaseImpl.tick ran tickHeldAction before tickCooldown
			PlayerPower.class,
			StandPower.class,
			// StandEntity.tick replayed the queued input against the state its user's power tick had left
			EntityActionInputState.class,
			// GameplayEventHandler.onLivingTick: LivingUtilCap.tick, then the vanilla effects (the Stand virus)
			JojoModLivingVariables.class,
			GEStuckObjectsState.class,
			HamonHypnosisState.class,
			EntityCustomEffectsMap.class,
			// no 1.16 tick
			EntityComponentController.class,
			LivingComponentGrab.class,
			EntityClothesInventory.class,
			PlayerExternalContainers.class,
			// GameplayEventHandler.onWorldTick END
			KnockbackCollisionImpact.class,
			ProjectileHamonChargeState.class,
			EntityHamonChargeState.class);

	static final Comparator<Class<?>> TICK_ORDER = order(TICK);
	/** ForgeBusEventSubscriber synced and cloned the non-Stand power, the Stand power, then the utility data. */
	static final Comparator<Class<?>> SYNC_ORDER = order(powersFirst());

	private EntityDataOrder() {}

	private static List<Class<?>> powersFirst() {
		List<Class<?>> order = new ArrayList<>(List.of(PlayerPower.class, StandPower.class, PlayerClientBroadcastedSettings.class));
		for (Class<?> type : TICK) {
			if (!order.contains(type)) {
				order.add(type);
			}
		}
		return order;
	}

	private static Comparator<Class<?>> order(List<Class<?>> listed) {
		ClassValue<Integer> place = new ClassValue<>() {
			@Override
			protected Integer computeValue(Class<?> type) {
				for (int i = 0; i < listed.size(); i++) {
					if (listed.get(i).isAssignableFrom(type)) {
						return i;
					}
				}
				return listed.size();
			}
		};
		return Comparator.<Class<?>>comparingInt(place::get).thenComparing(Class::getName);
	}
}
