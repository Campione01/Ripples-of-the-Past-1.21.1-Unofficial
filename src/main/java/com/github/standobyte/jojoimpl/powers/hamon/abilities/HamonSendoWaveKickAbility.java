package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class HamonSendoWaveKickAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 1000.0F;
	private static final float HAMON_DAMAGE = 3.0F;
	private static final float KNOCKBACK_STRENGTH = 0.75F;
	private static final ActionAnimIdentifier SENDO_WAVE_KICK_ANIM = ActionAnimIdentifier.getOrCreate("sendo_wave_kick", false);
	private static final ActionAnimIdentifier SENDO_WAVE_KICK_LEFT_ITEM_ANIM = ActionAnimIdentifier.getOrCreate("sendo_wave_kick_l", false);
	private static final ActionAnimIdentifier SENDO_WAVE_KICK_RIGHT_ITEM_ANIM = ActionAnimIdentifier.getOrCreate("sendo_wave_kick_r", false);
	private static final ActionAnimIdentifier SENDO_WAVE_KICK_BOTH_ITEMS_ANIM = ActionAnimIdentifier.getOrCreate("sendo_wave_kick_lr", false);
	private static final ActionAnimIdentifier[] SENDO_WAVE_KICK_ANIMS = new ActionAnimIdentifier[] {
			SENDO_WAVE_KICK_ANIM,
			SENDO_WAVE_KICK_LEFT_ITEM_ANIM,
			SENDO_WAVE_KICK_RIGHT_ITEM_ANIM,
			SENDO_WAVE_KICK_BOTH_ITEMS_ANIM
	};

	public HamonSendoWaveKickAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, SendoWaveKickInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, SendoWaveKickInstance.USUAL_SENDO_WAVE_KICK_DURATION);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context != null ? context.getUser() : null;
		return user != null && user.onGround() ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		LivingEntity user = action != null ? action.getPowerUser() : null;
		return user != null ? SENDO_WAVE_KICK_ANIMS[getSendoWaveKickAnimIndex(user)] : super.getEntityAnim(action);
	}

	private static int getSendoWaveKickAnimIndex(LivingEntity user) {
		boolean leftHandHasItem = !user.getItemInHand(handForSide(user, HumanoidArm.LEFT)).isEmpty();
		boolean rightHandHasItem = !user.getItemInHand(handForSide(user, HumanoidArm.RIGHT)).isEmpty();
		return (leftHandHasItem ? 1 : 0) + (rightHandHasItem ? 2 : 0);
	}

	private static InteractionHand handForSide(LivingEntity user, HumanoidArm side) {
		return user.getMainArm() == side ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
	}

	public static boolean onUserIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !target.isAlive()) {
			return false;
		}
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(target);
		return action instanceof SendoWaveKickInstance
				&& action.getPhase() == ActionPhase.PERFORM
				&& isMeleeAttack(event.getSource());
	}

	private static boolean isMeleeAttack(DamageSource damageSource) {
		return damageSource.getEntity() instanceof LivingEntity
				&& damageSource.getDirectEntity() != null
				&& damageSource.getDirectEntity().is(damageSource.getEntity());
	}

	public static class SendoWaveKickInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private static final int USUAL_SENDO_WAVE_KICK_DURATION = 10;
		private final Set<UUID> damagedEntities = new HashSet<>();
		private int positionWaitingTimer = 0;
		private boolean gavePoints;
		private boolean capturedHamonPointsEnergy;
		private float hamonPointsEnergy;
		private float initialYRot;

		public SendoWaveKickInstance(EntityActionType ability) { super(ability); }

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				initialYRot = user.getYRot();
			}
		}

		public float getInitialYRot() {
			return initialYRot;
		}

		@Override
		protected void _onTick() {
			captureHamonPointsEnergy();
			super._onTick();
		}

		private void captureHamonPointsEnergy() {
			if (capturedHamonPointsEnergy || getPhase() != ActionPhase.PERFORM || getPhaseTick() >= 1
					|| level().isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				float pointsEnergy = Math.min(ENERGY_COST, hamon.getEnergy());
				float efficiency = hamon.getActionEfficiency(pointsEnergy, true, ModHamonSkills.SENDO_WAVE_KICK.get(), user);
				hamonPointsEnergy = pointsEnergy * efficiency;
				capturedHamonPointsEnergy = true;
			});
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			LivingEntity user = getPowerUser();
			if (user == null) return;
			damagedEntities.clear();
			user.setOnGround(false);
			if (level.isClientSide()) {
				Vec3 leap = Vec3.directionFromRotation(Mth.clamp(user.getXRot(), -45.0F, -18.0F), user.getYRot())
						.scale(1.0D + user.getAttributeValue(Attributes.MOVEMENT_SPEED) * 5.0D);
				user.setDeltaMovement(leap.x, leap.y * 0.5D, leap.z);
				user.hurtMarked = true;
			}
			else {
				positionWaitingTimer = -1;
				hitTargets(user, level);
			}
		}

		@Override
		public void actionTick() {
			LivingEntity user = getPowerUser();
			if (user == null || getPhase() != ActionPhase.PERFORM) {
				return;
			}
			user.fallDistance = 0;
			if (level().isClientSide()) {
				HamonSparksLoopSound.playSparkSound(user, user.position().add(0.0D, user.getBbHeight() * 0.25D, 0.0D), 1.0F, true);
				return;
			}
			if (getPhaseTick() < 1) {
				return;
			}
			if (shouldStop(user)) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			hitTargets(user, level());
		}

		private boolean shouldStop(LivingEntity user) {
			if (positionWaitingTimer >= 0) {
				positionWaitingTimer = -1;
			}
			return positionWaitingTimer < 0 && (user.onGround() || !user.level().getFluidState(user.blockPosition()).isEmpty())
					|| positionWaitingTimer >= USUAL_SENDO_WAVE_KICK_DURATION;
		}

		private void hitTargets(LivingEntity user, Level level) {
			boolean points = false;
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, kickHitbox(user),
					entity -> entity != user && entity.isAlive() && JojoModUtil.canHarm(user, entity))) {
				if (damagedEntities.add(target.getUUID())) {
					boolean kickDamage = dealPhysicalDamage(level, user, target);
					boolean hamonDamage = HamonAbilityHelpers.hamonHurt(target, user, HAMON_DAMAGE);
					if (kickDamage || hamonDamage) {
						knockbackSideways(user, target);
						if (user instanceof Player player) {
							HamonUtil.emitHamonSparkParticles(level, player, target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), 1.0F);
						}
					}
					if (hamonDamage) {
						points = true;
					}
				}
			}
			if (!gavePoints && points) {
				PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, hamonPointsEnergy);
					hamon.syncOnUpdate(user);
				});
				gavePoints = true;
			}
		}

		private static boolean dealPhysicalDamage(Level level, LivingEntity user, LivingEntity target) {
			DamageSource damageSource = user instanceof Player player
					? level.damageSources().playerAttack(player)
					: level.damageSources().mobAttack(user);
			return target.hurt(damageSource, DamageUtil.getDamageWithoutHeldItem(user));
		}

		private static void knockbackSideways(LivingEntity user, LivingEntity target) {
			Vec3 vecToTarget = target.position().subtract(user.position());
			boolean left = Mth.wrapDegrees(user.yBodyRot - MathUtil.yRotDegFromVec(vecToTarget)) < 0.0F;
			float knockbackYRot = (60.0F + user.getRandom().nextFloat() * 30.0F) * (left ? 1.0F : -1.0F);
			knockbackYRot += (float) -Mth.atan2(vecToTarget.x, vecToTarget.z) * MathUtil.RAD_TO_DEG;
			target.knockback(KNOCKBACK_STRENGTH,
					Mth.sin(knockbackYRot * MathUtil.DEG_TO_RAD),
					-Mth.cos(knockbackYRot * MathUtil.DEG_TO_RAD));
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				user.fallDistance = 0;
			}
		}
	}

	public static AABB kickHitbox(LivingEntity user) {
		float xzAngle = -user.getYRot() * MathUtil.DEG_TO_RAD;
		Vec3 lookVec = new Vec3(Math.sin(xzAngle), 0.0D, Math.cos(xzAngle));
		Vec3 hitboxXZCenter = user.position().add(lookVec.scale(user.getBbWidth() * 0.75F));
		return new AABB(hitboxXZCenter, hitboxXZCenter)
				.inflate(user.getBbWidth() * 1.25F, 0.125D, user.getBbWidth() * 1.25F)
				.expandTowards(0.0D, user.getBbHeight() / 2.0D, 0.0D);
	}
}

