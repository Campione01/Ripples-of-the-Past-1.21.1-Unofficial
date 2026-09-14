package rotp.core.impl.powers.pillarman.abilities;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.customobjects.explosion.CustomExplosion;
import rotp.core.init.ModBlocks;
import rotp.core.init.ModCustomExplosions;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModParticles;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.stands.magiciansred.MRFlameEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

public class PillarmanSelfDetonationAbility extends PillarmanActionAbility {
	private static final int HOLD_TO_FIRE_TICKS = 60;

	public PillarmanSelfDetonationAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.HEAT, false, 150.0F, 0.0F, 0.0F, 0,
				SelfDetonationInstance::new);
		setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	public static class SelfDetonationInstance extends EntityActionInstance {
		public SelfDetonationInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (ability instanceof PillarmanSelfDetonationAbility detonationAbility
					&& (newPhase == ActionPhase.BUTTON_CHARGE || newPhase == ActionPhase.PERFORM)) {
				userWalkSpeed = detonationAbility.heldWalkSpeed;
			}
			else {
				userWalkSpeed = 1.0F;
			}
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && getPhaseTick() < HOLD_TO_FIRE_TICKS) {
				forceStop();
				syncPhaseChanges();
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					PillarmanActionAbility.auraEffect(user, ModParticles.HAMON_AURA_RED.get(), 12);
					PillarmanActionAbility.auraEffect(user, ModParticles.BOILING_BLOOD_POP.get(), 1);
				}
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide() || !(ability instanceof PillarmanSelfDetonationAbility detonationAbility)) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			Power<?> context = detonationAbility.getUserPower(user);
			if (!detonationAbility.consumeEnergy(context)) {
				return;
			}
			DamageSource damageSource = DamageUtil.make(level, ModDamageTypes.PILLAR_MAN_SELF_DETONATION, user, user);
			PillarmanExplosion explosion = new PillarmanExplosion(level, user, damageSource,
					user.getX(), user.getY(), user.getZ(), 3.0F, true, Explosion.BlockInteraction.DESTROY);
			CustomExplosion.explode(explosion);
			if (!(user instanceof Player player) || !player.getAbilities().instabuild) {
				user.hurt(level.damageSources().explosion(user, user), 40.0F);
				user.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
			}
			if (context != null
					&& context.getDataForAbility(
							detonationAbility) != null) {
				context.getDataForAbility(detonationAbility)
						.syncOnUpdate(user);
			}
		}
	}

	public static class PillarmanExplosion extends CustomExplosion {
		@Nullable
		private final LivingEntity sourcePillarman;

		public PillarmanExplosion(Level level, double x, double y, double z, float radius) {
			super(level, x, y, z, radius);
			this.sourcePillarman = null;
		}

		public PillarmanExplosion(Level level, @Nullable Entity source, @Nullable DamageSource damageSource,
				double x, double y, double z, float radius, boolean fire, Explosion.BlockInteraction blockInteraction) {
			super(level, source, damageSource, x, y, z, radius, fire, blockInteraction);
			this.sourcePillarman = source instanceof LivingEntity living ? living : null;
		}

		@Override
		protected void filterEntities(List<Entity> entities) {
			if (sourcePillarman != null) {
				Iterator<Entity> iter = entities.iterator();
				while (iter.hasNext()) {
					Entity entity = iter.next();
					if (entity.is(sourcePillarman) || !JojoModUtil.canHarm(sourcePillarman, entity)) {
						iter.remove();
					}
				}
			}
		}

		@Override
		protected void spawnFire() {
			if (sourcePillarman == null || EventHooks.canEntityGrief(level, sourcePillarman)) {
				for (BlockPos pos : getToBlow()) {
					if (level.isEmptyBlock(pos)) {
						if (!level.isEmptyBlock(pos.below()) && level.random.nextFloat() < 0.25F) {
							level.setBlockAndUpdate(pos, ModBlocks.BOILING_BLOOD.get().defaultBlockState()
									.setValue(LiquidBlock.LEVEL, 7));
						}
						else {
							level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
						}
					}
					else {
						BlockState blockState = level.getBlockState(pos);
						MRFlameEntity.meltIceAndSnow(level, blockState, pos);
					}
				}
			}
		}

		@Override
		public ResourceLocation getExplosionType() {
			return ModCustomExplosions.PILLAR_MAN_DETONATION;
		}
	}
}
