package rotp.core.powersystem.standpower.entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 1.16 GameplayEventHandler.standBlockUserAttack: a Stand guarding in front of its user (following it, in the block
 * pose, facing the hit) also guards the hits aimed at the user. EventHandler calls the two steps where 1.16 ran them:
 * redirectToGuardingStand at the start of the attack (LivingAttackEvent), blockDamage where the hit lands
 * (LivingHurtEvent, before Hamon protection).
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class StandUserGuard {
	// 1.16 LivingUtilCap.latestExplosion. An explosion hurts in the tick it goes off, so this is emptied every tick.
	private static final Map<LivingEntity, Explosion> LATEST_EXPLOSION = new IdentityHashMap<>();
	// What the guard took off each hit this tick, for Resolve (see guardCut). Server thread only.
	private static final Map<DamageContainer, Float> GUARD_CUTS = new IdentityHashMap<>();
	// The hits the guard blocked whole this tick (see blockedWhole). Server thread only.
	private static final Set<DamageContainer> BLOCKED_WHOLE = Collections.newSetFromMap(new IdentityHashMap<>());
	// The hits on a user the guard is passing to its Stand right now (see isRedirecting). Server thread only.
	private static final Set<DamageContainer> REDIRECTING = Collections.newSetFromMap(new IdentityHashMap<>());

	private StandUserGuard() {}

	/**
	 * 1.16 onLivingHurtStart: a hit the guarding Stand can take goes to the Stand, which blocks it as a hit on itself;
	 * the user takes only what the health link passes on.
	 */
	public static void redirectToGuardingStand(LivingIncomingDamageEvent event) {
		LivingEntity user = event.getEntity();
		if (user.level().isClientSide() || event.isCanceled()) {
			return;
		}
		DamageSource source = event.getSource();
		StandEntity stand = guardFacing(user, source);
		if (stand != null && standTakesHit(stand, user, source)) {
			DamageContainer container = event.getContainer();
			REDIRECTING.add(container);
			try {
				stand.hurt(source, event.getAmount());
			}
			finally {
				REDIRECTING.remove(container);
			}
			event.setCanceled(true);
		}
	}

	/**
	 * Whether this hit on the user is the one the guard is passing to its Stand. StandEntity.knockback hands the
	 * Stand's knockback, already changed by the hit, on to the user, so the user's own copy of the hit must not
	 * change it again (DamageSourceModified.currentKnockbackSource).
	 */
	@ApiStatus.Internal
	public static boolean isRedirecting(DamageContainer container) {
		return !REDIRECTING.isEmpty() && REDIRECTING.contains(container);
	}

	/**
	 * Whether the guard blocked this hit on its user whole. 1.16 blockDamage cancelled such a hit in LivingHurtEvent,
	 * so no later hurt or damage handler saw it. The port lets it land at 0 (for the hurt cooldown and the block
	 * sound), so a LivingDamageEvent handler that must skip it checks this. Holds until the end of the server tick.
	 */
	public static boolean blockedWhole(DamageContainer container) {
		return !BLOCKED_WHOLE.isEmpty() && BLOCKED_WHOLE.contains(container);
	}

	/**
	 * 1.16 blockDamage: an explosion is cut by how squarely the guard faces it; another hit the Stand cannot take
	 * itself loses half the Stand's durability, with the block sound and no stagger. Returns whether the hit was
	 * blocked whole (1.16 cancelled it, so Hamon protection did not run).
	 */
	public static boolean blockDamage(LivingIncomingDamageEvent event) {
		LivingEntity user = event.getEntity();
		DamageSource source = event.getSource();
		if (user.level().isClientSide() || event.isCanceled() || source instanceof StandLinkDamageSource) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			Explosion explosion = sourceExplosion(user, source);
			StandEntity stand = explosion != null ? guardingStand(user) : null;
			if (stand != null) {
				double cos = explosion.center().subtract(user.position()).normalize().dot(stand.getLookAngle());
				float multiplier = explosionMultiplier(cos, stand.getDurability());
				if (multiplier < 1) {
					reduce(event, amount -> amount * multiplier, null);
				}
			}
			return false;
		}
		StandEntity stand = guardFacing(user, source);
		if (stand == null || standTakesHit(stand, user, source)) {
			return false;
		}
		double durability = stand.getDurability();
		if (durability <= 0) {
			return false;
		}
		return reduce(event, amount -> userHitAfterGuard(amount, durability), () -> {
			stand.playStandBlockSound(source);
			NoKnockbackOnBlocking.setOneTickKbRes(stand);
		});
	}

	/** 1.16: max(damage - durability / 2, 0). */
	public static float userHitAfterGuard(float amount, double durability) {
		return Math.max(amount - (float) durability / 2F, 0);
	}

	/** 1.16: max(1 - cos, 4 / durability) for an explosion in front of the guard; 1 behind it or at durability 4 or less. */
	public static float explosionMultiplier(double cos, double durability) {
		if (durability <= 4 || cos <= 0) {
			return 1;
		}
		return Math.max(1F - (float) cos, 4F / (float) durability);
	}

	@Nullable
	static StandEntity guardingStand(LivingEntity user) {
		StandPower power = StandPower.get(user);
		StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
		return stand != null && stand != user && stand.guardsHitsOnUser()
				&& stand.isFollowingUser() && stand.isStandBlocking() ? stand : null;
	}

	// 1.16: a hit with a direct entity and a position that the guard faces and can block
	@Nullable
	private static StandEntity guardFacing(LivingEntity user, DamageSource source) {
		Vec3 sourcePos = source.getSourcePosition();
		if (source instanceof StandLinkDamageSource || source.getDirectEntity() == null || sourcePos == null) {
			return null;
		}
		StandEntity stand = guardingStand(user);
		return stand != null && stand.canBlockDamage(source) && stand.canBlockFromAngle(sourcePos) ? stand : null;
	}

	// 1.16 !stand.isInvulnerableTo(source), which also held for the Stand's and its user's own hits
	private static boolean standTakesHit(StandEntity stand, LivingEntity user, DamageSource source) {
		Entity attacker = source.getEntity();
		return attacker != stand && attacker != user && !stand.isInvulnerableTo(source);
	}

	@FunctionalInterface
	private interface DamageCut {
		float apply(float amount);
	}

	/*
	 * 1.16 changed the LivingHurtEvent amount: the hit past a shield and, in the hurt cooldown, only its excess over
	 * lastHurt. Its effects came only for a hit that got that far, and lastHurt kept the whole hit.
	 * Only the Stand's block (onLanded) cancelled a hit it took whole; the explosion cut never did.
	 */
	private static boolean reduce(LivingIncomingDamageEvent event, DamageCut cut, @Nullable Runnable onLanded) {
		LivingEntity user = event.getEntity();
		DamageSource source = event.getSource();
		if (user.invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
			event.addReductionModifier(DamageContainer.Reduction.INVULNERABILITY, (container, lastHurt) -> {
				float amount = container.getNewDamage();
				float left = cut.apply(Math.max(amount - lastHurt, 0));
				if (left <= 0 && onLanded != null) {
					BLOCKED_WHOLE.add(container);
				}
				return amount - left;
			});
			if (onLanded != null) {
				event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
					onLanded.run();
					return reduction;
				});
			}
			return false;
		}
		float amount = event.getAmount();
		// 1.16's shield took the hit before the guard saw it
		boolean shieldTakesIt = user.isDamageSourceBlocked(source);
		float reduced = shieldTakesIt ? amount : cut.apply(amount);
		float removed = amount - reduced;
		if (removed > 0) {
			event.setAmount(reduced);
			GUARD_CUTS.merge(event.getContainer(), removed, Float::sum);
		}
		if (removed > 0 || onLanded != null) {
			event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
				if (onLanded != null) {
					onLanded.run();
				}
				if (removed > 0) {
					user.lastHurt += removed;
				}
				return reduction;
			});
		}
		boolean whole = !shieldTakesIt && reduced <= 0;
		if (whole && onLanded != null) {
			BLOCKED_WHOLE.add(event.getContainer());
		}
		return whole;
	}

	/**
	 * How much the guard took off this hit's amount. 1.16 counted Resolve (resolveOnHurtEvent, HIGHEST) before
	 * blockDamage (HIGH) cut the hit, so ResolveCounter adds this back. A cut made inside the hurt cooldown is a
	 * reduction the event amount never shows, so it is not counted here.
	 */
	@ApiStatus.Internal
	public static float guardCut(LivingIncomingDamageEvent event) {
		if (GUARD_CUTS.isEmpty()) {
			return 0;
		}
		Float cut = GUARD_CUTS.get(event.getContainer());
		return cut != null ? cut : 0;
	}

	@Nullable
	static Explosion sourceExplosion(LivingEntity entity, DamageSource source) {
		Explosion explosion = LATEST_EXPLOSION.get(entity);
		return explosion != null && explosion.damageSource == source ? explosion : null;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
		if (event.getLevel().isClientSide()) {
			return;
		}
		for (Entity entity : event.getAffectedEntities()) {
			if (entity instanceof LivingEntity living) {
				LATEST_EXPLOSION.put(living, event.getExplosion());
			}
		}
	}

	@SubscribeEvent
	public static void onServerTickEnd(ServerTickEvent.Post event) {
		if (!LATEST_EXPLOSION.isEmpty()) {
			LATEST_EXPLOSION.clear();
		}
		if (!GUARD_CUTS.isEmpty()) {
			GUARD_CUTS.clear();
		}
		if (!BLOCKED_WHOLE.isEmpty()) {
			BLOCKED_WHOLE.clear();
		}
	}
}
