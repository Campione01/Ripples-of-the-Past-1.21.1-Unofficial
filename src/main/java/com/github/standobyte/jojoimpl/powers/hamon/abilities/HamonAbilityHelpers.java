package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import java.util.List;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.item.GlovesItem;
import com.github.standobyte.jojo.mechanics.HamonSpreadEffect;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.powers.hamon.EntityHamonChargeState;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Slice 5b bundle 7 helper — shared geometry / damage helpers for Hamon abilities.
 *
 * <p>Centralizes the original Hamon damage rules: non-Hamon-affected targets take
 * 20%, Pillar Men take 50%, Satiporoja scarf reduces or negates damage, and hits
 * bypass normal invulnerability ticks.</p>
 */
public final class HamonAbilityHelpers {
	private static final float[] DAMAGE_FOR_HAMON_SPREAD_EFFECT = new float[] { 5.0F, 12.5F, 25.0F, 50.0F, 125.0F };
	private static final float HAMON_SPREAD_DAMAGE_DECAY = 0.1F;
	private static final String HAMON_SPREAD_DAMAGE_TAG = "jojo_ripples.hamon_spread_damage";
	private static final String HAMON_SPREAD_DAMAGE_TICK_TAG = "jojo_ripples.hamon_spread_damage_tick";

	public static final class HamonAttackProperties {
		public static final HamonAttackProperties DEFAULT = new HamonAttackProperties(true);
		public static final HamonAttackProperties NO_SOURCE_ENTITY_HAMON_MULTIPLIER = new HamonAttackProperties(false);

		private final boolean sourceEntityHamonMultiplier;

		private HamonAttackProperties(boolean sourceEntityHamonMultiplier) {
			this.sourceEntityHamonMultiplier = sourceEntityHamonMultiplier;
		}
	}

	private HamonAbilityHelpers() {}

	@Nullable
	public static LivingEntity findFrontConeTarget(LivingEntity user, Level level, double reach, double minCos) {
		Vec3 lookXZ = user.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
		AABB cone = user.getBoundingBox().inflate(reach, 0.5, reach).expandTowards(lookXZ.scale(reach));
		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, cone,
				e -> e != user && e.isAlive() && e.isPickable());
		Vec3 origin = user.position();
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity c : candidates) {
			Vec3 to = c.position().subtract(origin);
			if (to.lengthSqr() < 1e-6) continue;
			double cos = lookXZ.dot(to.normalize());
			if (cos < minCos) continue;
			double sq = to.lengthSqr();
			if (sq < bestSq) {
				best = c;
				bestSq = sq;
			}
		}
		return best;
	}

	public static List<LivingEntity> findFrontConeTargets(LivingEntity user, Level level, double reach, double minCos) {
		Vec3 lookXZ = user.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
		AABB cone = user.getBoundingBox().inflate(reach, 0.5, reach).expandTowards(lookXZ.scale(reach));
		Vec3 origin = user.position();
		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, cone,
				e -> e != user && e.isAlive() && e.isPickable());
		candidates.removeIf(c -> {
			Vec3 to = c.position().subtract(origin);
			if (to.lengthSqr() < 1e-6) return true;
			return lookXZ.dot(to.normalize()) < minCos;
		});
		return candidates;
	}

	/**
	 * @return damage multiplier — 2.0 for undead targets (vampires / zombies / skeletons / phantoms / wither),
	 *         1.0 otherwise. Hamon's signature "extra damage to undead" lore semantic.
	 */
	public static float hamonDamageMultiplier(LivingEntity target) {
		if (!JojoDefinitions.isAffectedByHamon(target)) {
			return 0.2F;
		}
		return PlayerPower.getPowerData(target, ModPlayerPowers.PILLAR_MAN).isPresent() ? 0.5F : 1.0F;
	}

	public static boolean hamonHurt(LivingEntity target, LivingEntity user, float baseDamage) {
		return hamonHurt(target, baseDamage, user, user);
	}

	public static boolean hamonHurt(LivingEntity target, float baseDamage,
			@Nullable Entity directEntity, @Nullable Entity causingEntity) {
		if (targetHasHamonDamageImmunity(target)) {
			return false;
		}
		float damage = hamonDamageAmount(target, baseDamage);
		if (damage <= 0.0F) {
			return false;
		}
		return hamonHurtWithAmount(target, damage, hamonDamageSource(target.level(), directEntity, causingEntity));
	}

	public static DamageSource hamonDamageSource(Level level, @Nullable Entity directEntity, @Nullable Entity causingEntity) {
		return DamageUtil.make(level, ModDamageTypes.HAMON, directEntity, causingEntity);
	}

	public static boolean hamonHurtWithSource(LivingEntity target, float baseDamage, DamageSource source) {
		if (targetHasHamonDamageImmunity(target)) {
			return false;
		}
		float damage = hamonDamageAmount(target, baseDamage);
		return damage > 0.0F && hamonHurtWithAmount(target, damage, source);
	}

	public static boolean hamonHurtWithAmount(LivingEntity target, float damage, DamageSource source) {
		if (targetHasHamonDamageImmunity(target)) {
			return false;
		}
		float damageBeforeSourceMultiplier = damage;
		damage *= sourceEntityHamonMultiplier(source);
		int invulTicks = target.invulnerableTime;
		float lastHurt = target.lastHurt;
		target.invulnerableTime = 0;
		boolean hurt = target.hurt(source, damage);
		target.invulnerableTime = invulTicks;
		target.lastHurt = lastHurt;
		damage = damageBeforeSourceMultiplier;
		if (hurt) {
			target.hurtMarked = true;
			applyHamonSpread(target, damage, source);
			if (target instanceof ServerPlayer player
					&& target.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SATIPOROJA_SCARF.get())
					&& JojoDefinitions.isUndeadOrVampiric(target)) {
				ModCriteriaTriggers.triggerVampireHamonDamageScarf(player);
			}
		}
		return hurt;
	}

	public static boolean hamonHurtWithAmount(LivingEntity target, float damage, DamageSource source,
			HamonAttackProperties attackProperties) {
		if (attackProperties.sourceEntityHamonMultiplier) {
			return hamonHurtWithAmount(target, damage, source);
		}
		return hamonHurtWithoutSourceEntityMultiplier(target, damage, source);
	}

	private static boolean hamonHurtWithoutSourceEntityMultiplier(LivingEntity target, float damage, DamageSource source) {
		if (targetHasHamonDamageImmunity(target)) {
			return false;
		}
		int invulTicks = target.invulnerableTime;
		float lastHurt = target.lastHurt;
		target.invulnerableTime = 0;
		boolean hurt = target.hurt(source, damage);
		target.invulnerableTime = invulTicks;
		target.lastHurt = lastHurt;
		if (hurt) {
			target.hurtMarked = true;
			if (target instanceof ServerPlayer player
					&& target.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SATIPOROJA_SCARF.get())
					&& JojoDefinitions.isUndeadOrVampiric(target)) {
				ModCriteriaTriggers.triggerVampireHamonDamageScarf(player);
			}
		}
		return hurt;
	}

	private static float sourceEntityHamonMultiplier(DamageSource source) {
		if (!(source.getEntity() instanceof LivingEntity attacker)) {
			return 1.0F;
		}
		return PlayerPower.getPowerData(attacker, ModPlayerPowers.HAMON)
				.map(HamonData::getHamonDamageMultiplier)
				.orElse(1.0F);
	}

	private static void applyHamonSpread(LivingEntity target, float damageReceived, DamageSource source) {
		if (target.level().isClientSide() || damageReceived <= 0.0F
				|| !JojoDefinitions.isAffectedByHamon(target) || hasSatiporojaScarf(target)
				|| !(source.getEntity() instanceof LivingEntity attacker)) {
			return;
		}
		PlayerPower.getPowerData(attacker, ModPlayerPowers.HAMON)
				.filter(hamon -> hamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get()))
				.ifPresent(hamon -> addHamonSpreadDamage(target,
						damageReceived * hamon.getHamonDamageMultiplier(),
						!hasExplicitHamonSpreadApplication(attacker)));
	}

	private static boolean hasExplicitHamonSpreadApplication(LivingEntity attacker) {
		var action = LivingComponentAction.getCurEntityAction(attacker);
		return action instanceof HamonSunlightYellowOverdriveBarrageAbility.SYOverdriveBarrageInstance barrage
				&& "syo_barrage_finisher".equals(barrage.getAnimationForPhase().name());
	}

	private static void addHamonSpreadDamage(LivingEntity target, float damageReceived, boolean applyEffect) {
		CompoundTag data = target.getPersistentData();
		long gameTime = target.level().getGameTime();
		float receivedHamonDamage = data.getFloat(HAMON_SPREAD_DAMAGE_TAG);
		if (data.contains(HAMON_SPREAD_DAMAGE_TICK_TAG)) {
			long elapsedTicks = Math.max(gameTime - data.getLong(HAMON_SPREAD_DAMAGE_TICK_TAG), 0L);
			receivedHamonDamage = Math.max(receivedHamonDamage - elapsedTicks * HAMON_SPREAD_DAMAGE_DECAY, 0.0F);
		}
		receivedHamonDamage += damageReceived;
		data.putFloat(HAMON_SPREAD_DAMAGE_TAG, receivedHamonDamage);
		data.putLong(HAMON_SPREAD_DAMAGE_TICK_TAG, gameTime);

		for (int i = DAMAGE_FOR_HAMON_SPREAD_EFFECT.length - 1; i >= 0; i--) {
			if (receivedHamonDamage >= DAMAGE_FOR_HAMON_SPREAD_EFFECT[i]) {
				if (applyEffect) {
					int duration = 60 + 40 * i;
					HamonSpreadEffect.giveEffectTo(target, duration, i);
				}
				break;
			}
		}
	}

	private static boolean targetHasHamonDamageImmunity(LivingEntity target) {
		if (EntityHamonChargeState.get(target).hasHamonCharge()) {
			return true;
		}
		return PlayerPower.getPowerData(target, ModPlayerPowers.PILLAR_MAN)
				.map(PillarmanData::isStoneFormEnabled)
				.orElse(false);
	}

	public static boolean hamonHurtWithParticles(LivingEntity target, LivingEntity user,
			float baseDamage, ParticleOptions particles, int particleCount) {
		boolean hurt = hamonHurt(target, user, baseDamage);
		if (hurt && particles != null) {
			sendHamonParticles(target, particles, particleCount);
		}
		return hurt;
	}

	public static boolean hamonHurtThroughInvul(LivingEntity target, LivingEntity user, float baseDamage) {
		return hamonHurt(target, user, baseDamage);
	}

	public static void sendHamonParticles(LivingEntity target, ParticleOptions particles, int count) {
		if (count <= 0 || !(target.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		serverLevel.sendParticles(particles,
				target.getX(), target.getY(0.5D), target.getZ(), count,
				target.getBbWidth() * 0.25D, target.getBbHeight() * 0.25D, target.getBbWidth() * 0.25D, 0.05D);
	}

	public static ActionTarget getAimTarget(LivingEntity user, Level level) {
		var aim = LivingComponentAction.getAim(user);
		ActionTarget target = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
		return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
	}

	public static void doMeleeAttack(LivingEntity attacker, LivingEntity target) {
		if (attacker instanceof Player player) {
			player.attack(target);
		}
		else if (!attacker.level().isClientSide()) {
			attacker.doHurtTarget(target);
		}
	}

	public static float hamonDamageAmount(LivingEntity target, float baseDamage) {
		if (hasSatiporojaScarf(target)) {
			if (PlayerPower.getPowerData(target, ModPlayerPowers.HAMON).isPresent()) {
				return 0.0F;
			}
			baseDamage *= 0.25F;
		}
		return baseDamage * hamonDamageMultiplier(target);
	}

	private static boolean hasSatiporojaScarf(LivingEntity target) {
		return target.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SATIPOROJA_SCARF.get());
	}

	public static boolean isItemWeapon(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof GlovesItem) {
			return false;
		}
		if (stack.getItem() instanceof TieredItem) {
			return true;
		}
		return stack.getAttributeModifiers().modifiers().stream().anyMatch(entry ->
				entry.attribute().is(Attributes.ATTACK_DAMAGE)
				&& entry.slot().test(EquipmentSlot.MAINHAND)
				&& entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
				&& entry.modifier().amount() > 0.0D);
	}
}
