package com.github.standobyte.jojo.init;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.StatusEffectModified;
import com.github.standobyte.jojo.mechanics.BleedingEffect;
import com.github.standobyte.jojo.mechanics.FreezeEffect;
import com.github.standobyte.jojo.mechanics.HamonShockEffect;
import com.github.standobyte.jojo.mechanics.HamonSpreadEffect;
import com.github.standobyte.jojo.mechanics.HypnosisEffect;
import com.github.standobyte.jojo.mechanics.ImmobilizeEffect;
import com.github.standobyte.jojo.mechanics.StunEffect;
import com.github.standobyte.jojo.mechanics.UndeadRegenerationEffect;
import com.github.standobyte.jojo.mechanics.VampireSunBurnEffect;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.mechanics.standarrow.StandVirusEffect;
import com.github.standobyte.jojoimpl.stands.goldexperience.GELifeshotEffect;
import com.github.standobyte.jojoimpl.stands.goldexperience.GELifeshotState;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class ModStatusEffects {
	public static final DeferredRegister<MobEffect> STATUS_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, JojoMod.MOD_ID);


	public static final Set<Holder<? extends MobEffect>> TRACKED_EFFECTS = new HashSet<>();

	public static final DeferredHolder<MobEffect, ResolveModeEffect> RESOLVE = STATUS_EFFECTS.register("resolve", 
			id -> new ResolveModeEffect(MobEffectCategory.BENEFICIAL, 0xC6151F));

	public static final DeferredHolder<MobEffect, StatusEffectModified> SUN_RESISTANCE = STATUS_EFFECTS.register("sun_resistance",
			id -> new StatusEffectModified(MobEffectCategory.BENEFICIAL, 0xFFD54A).setUncurable());

	public static final DeferredHolder<MobEffect, VampireSunBurnEffect> VAMPIRE_SUN_BURN = STATUS_EFFECTS.register("sun_burn",
			id -> new VampireSunBurnEffect().setUncurable());

	public static final DeferredHolder<MobEffect, StatusEffectModified> SPIRIT_VISION = STATUS_EFFECTS.register("spirit_vision",
			id -> new StatusEffectModified(MobEffectCategory.BENEFICIAL, 0x8E45FF).setUncurable());

	public static final DeferredHolder<MobEffect, StatusEffectModified> FULL_INVISIBILITY = STATUS_EFFECTS.register("full_invisibility",
			id -> new StatusEffectModified(MobEffectCategory.BENEFICIAL, 0x7F8392).setUncurable());

	public static final DeferredHolder<MobEffect, StatusEffectModified> TIME_STOP = STATUS_EFFECTS.register("time_stop",
			id -> new StatusEffectModified(MobEffectCategory.BENEFICIAL, 0x707070).setUncurable());

	public static final DeferredHolder<MobEffect, FreezeEffect> FREEZE = STATUS_EFFECTS.register("freeze",
			id -> new FreezeEffect(MobEffectCategory.HARMFUL, 0xD6D6FF));

	public static final DeferredHolder<MobEffect, StunEffect> STUN = STATUS_EFFECTS.register("stun",
			id -> new StunEffect(0x404040));

	public static final DeferredHolder<MobEffect, HamonShockEffect> HAMON_SHOCK = STATUS_EFFECTS.register("hamon_shock",
			id -> new HamonShockEffect(0xFFC10A));

	public static final DeferredHolder<MobEffect, ImmobilizeEffect> IMMOBILIZE = STATUS_EFFECTS.register("immobilize",
			id -> new ImmobilizeEffect(0x404040));

	public static final DeferredHolder<MobEffect, BleedingEffect> BLEEDING = STATUS_EFFECTS.register("bleeding", 
			id -> new BleedingEffect(MobEffectCategory.HARMFUL, 0x990000));

	public static final DeferredHolder<MobEffect, UndeadRegenerationEffect> UNDEAD_REGENERATION = STATUS_EFFECTS.register("undead_regeneration",
			id -> new UndeadRegenerationEffect(MobEffectCategory.BENEFICIAL, MobEffects.REGENERATION.value().getColor()));

	public static final DeferredHolder<MobEffect, HamonSpreadEffect> HAMON_SPREAD = STATUS_EFFECTS.register("hamon_spread",
			id -> new HamonSpreadEffect(MobEffectCategory.HARMFUL, 0xFFC10A).setUncurable());

	public static final DeferredHolder<MobEffect, HypnosisEffect> HYPNOSIS = STATUS_EFFECTS.register("hypnosis",
			id -> new HypnosisEffect(0xFFFF00));

	public static final DeferredHolder<MobEffect, StatusEffectModified> CHEAT_DEATH = STATUS_EFFECTS.register("cheat_death",
			id -> new StatusEffectModified(MobEffectCategory.BENEFICIAL, 0xEADB84).setUncurable());

	public static final DeferredHolder<MobEffect, StandVirusEffect> STAND_VIRUS = STATUS_EFFECTS.register("stand_virus",
			id -> new StandVirusEffect(MobEffectCategory.HARMFUL, 0xC10019));

	public static final DeferredHolder<MobEffect, StatusEffectModified> MISSHAPEN_FACE = STATUS_EFFECTS.register("misshapen_face",
			id -> new StatusEffectModified(MobEffectCategory.HARMFUL, 0x808080));

	public static final DeferredHolder<MobEffect, StatusEffectModified> MISSHAPEN_ARMS = STATUS_EFFECTS.register("misshapen_arms",
			id -> new StatusEffectModified(MobEffectCategory.HARMFUL, 0x808080));

	public static final DeferredHolder<MobEffect, StatusEffectModified> MISSHAPEN_LEGS = STATUS_EFFECTS.register("misshapen_legs",
			id -> new StatusEffectModified(MobEffectCategory.HARMFUL, 0x808080));

	public static final DeferredHolder<MobEffect, GELifeshotEffect> SENSORY_OVERLOAD = STATUS_EFFECTS.register("sensory_overload",
			id -> new GELifeshotEffect(0xD88F1F));
	

	@SubscribeEvent
	public static void afterRegister(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			TRACKED_EFFECTS.add(RESOLVE);
			TRACKED_EFFECTS.add(FULL_INVISIBILITY);
			TRACKED_EFFECTS.add(TIME_STOP);
			TRACKED_EFFECTS.add(FREEZE);
			TRACKED_EFFECTS.add(VAMPIRE_SUN_BURN);
			TRACKED_EFFECTS.add(HAMON_SPREAD);
			TRACKED_EFFECTS.add(HYPNOSIS);
			TRACKED_EFFECTS.add(CHEAT_DEATH);
			TRACKED_EFFECTS.add(IMMOBILIZE);
			TRACKED_EFFECTS.add(STUN);
			TRACKED_EFFECTS.add(HAMON_SHOCK);
			TRACKED_EFFECTS.add(BLEEDING);
			TRACKED_EFFECTS.add(STAND_VIRUS);
		});
	}

	public static boolean isStunned(LivingEntity entity) {
		return entity.hasEffect(STUN) || entity.hasEffect(HAMON_SHOCK);
	}
	
	
	@ApiStatus.Internal
	public static void trackAddEffect(MobEffectInstance effectInstance, LivingEntity entity) {
		if (!entity.level().isClientSide() && TRACKED_EFFECTS.contains(effectInstance.getEffect())) {
			((ServerChunkCache) entity.getCommandSenderWorld().getChunkSource()).broadcast(entity, 
					new ClientboundUpdateMobEffectPacket(entity.getId(), effectInstance, false));
		}
	}

	@ApiStatus.Internal
	public static void trackRemoveEffect(Holder<MobEffect> effect, LivingEntity entity) {
		if (!entity.level().isClientSide() && TRACKED_EFFECTS.contains(effect)) {
			((ServerChunkCache) entity.getCommandSenderWorld().getChunkSource()).broadcast(entity, 
					new ClientboundRemoveMobEffectPacket(entity.getId(), effect));
		}
	}

	@SubscribeEvent
	public static void syncTrackedEffects(PlayerEvent.StartTracking event) {
		if (event.getTarget() instanceof LivingEntity tracked) {
			ServerPlayer player = (ServerPlayer) event.getEntity();
			for (MobEffectInstance effectInstance : tracked.getActiveEffectsMap().values()) {
				if (TRACKED_EFFECTS.contains(effectInstance.getEffect())) {
					player.connection.send(new ClientboundUpdateMobEffectPacket(tracked.getId(), effectInstance, false));
				}
			}
		}
	}


	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onPotionAdded(MobEffectEvent.Added event) {
		LivingEntity entity = event.getEntity();
		if (!entity.level().isClientSide()) {
			MobEffectInstance effectInstance = event.getEffectInstance();
			trackAddEffect(effectInstance, entity);
			if (effectInstance.getEffect().is(SENSORY_OVERLOAD)) {
				GELifeshotState.get(entity).setSendLifeshotNextTick();
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void trackedPotionRemoved(MobEffectEvent.Remove event) {
		LivingEntity entity = event.getEntity();
		if (!entity.level().isClientSide()) {
			MobEffectInstance effect = event.getEffectInstance();
			if (effect != null) {
				trackRemoveEffect(effect.getEffect(), entity);
			}
			else {
				trackRemoveEffect(event.getEffect(), entity);
			}
		}
	}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void trackedPotionExpired(MobEffectEvent.Expired event) {
        LivingEntity entity = event.getEntity();
		if (!entity.level().isClientSide()) {
			MobEffectInstance effect = event.getEffectInstance();
			if (effect != null) {
				trackRemoveEffect(effect.getEffect(), entity);
			}
		}
    }
}
