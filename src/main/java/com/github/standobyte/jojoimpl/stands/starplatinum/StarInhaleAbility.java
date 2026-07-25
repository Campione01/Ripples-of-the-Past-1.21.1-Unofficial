package com.github.standobyte.jojoimpl.stands.starplatinum;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.EntityStoppableSoundInstance;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandOffsetFromUser;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class StarInhaleAbility extends StandEntityAbility {
    private static final float INHALE_STAMINA_COST_TICK = 2F;
    private static final int INHALE_COOLDOWN = 80;
    private static final int INHALE_HOLD_DURATION_MAX = 80;

    public StarInhaleAbility(AbilityType<?> abilityType, AbilityId abilityId) {
    	super(abilityType, abilityId, InhaleAbilityInstance::new);
    	cooldown(INHALE_COOLDOWN);
    	setDefaultPhaseLength(ActionPhase.PERFORM, 80);
    	partsRequired(StandPart.MAIN_BODY);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
    	return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
    	ConditionCheck check = super.checkSpecificConditions(context);
    	return check.isPositive() ? StandAbilityStamina.check(context, INHALE_STAMINA_COST_TICK) : check;
    }

    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
    	int cooldown = super.getCooldown(context, ticksHeld);
    	if (cooldown <= 0) {
    		return 0;
    	}
    	int held = Mth.clamp(ticksHeld, 0, INHALE_HOLD_DURATION_MAX);
    	return cooldown * held / INHALE_HOLD_DURATION_MAX;
    }

    public static class InhaleAbilityInstance extends EntityActionInstance {
    	private boolean cooldownSet;

        public InhaleAbilityInstance(EntityActionType ability) {
            super(ability);
        }

        private static final double RANGE = 12.0;

        @Override
        public void onActionSet(@Nullable EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            setStandOffset(0, -0.25, StandOffsetFromUser.Rotations.BODY, false);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM) {
                return;
            }

            Level level = level();
            if (!(performer instanceof StandEntity standEntity)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (!level.isClientSide()) {
                StandPower standPower = user != null ? StandPower.get(user) : null;
                if (!StandAbilityStamina.consume(ability, standPower, INHALE_STAMINA_COST_TICK, true)) {
                    startRecovery();
                    return;
                }
            }

            Vec3 mouthPos = standEntity.position()
                    .add(0, standEntity.getBbHeight() * 0.75F, 0)
                    .add(new Vec3(0, standEntity.getBbHeight() / 16F, standEntity.getBbWidth() * 0.5F)
                            .xRot(-standEntity.getXRot() * MathUtil.DEG_TO_RAD)
                            .yRot(-standEntity.getYRot() * MathUtil.DEG_TO_RAD));

            Vec3 spLookVec = standEntity.getLookAngle();
            level.getEntities(standEntity, standEntity.getBoundingBox().inflate(RANGE, RANGE, RANGE),
                    entity -> spLookVec.dot(entity.position().subtract(standEntity.position()).normalize()) > 0.886
                            && standEntity.hasLineOfSight(entity)
                            && entity.distanceToSqr(standEntity) > 0.5
                            && !entity.is(user)
                            && canInhaleAffectEntity(level, entity)
            ).forEach(entity -> {
                double distance = entity.distanceTo(standEntity);

                double efficiency = standEntity.getStandEfficiency();
                Vec3 suctionVec = mouthPos.subtract(entity.getBoundingBox().getCenter())
                        .normalize().scale(0.5 * efficiency);

                entity.setDeltaMovement(distance > 2 ?
                        entity.getDeltaMovement().add(suctionVec.scale(1 / distance))
                        : suctionVec.scale(Math.max(distance - 1, 0)));

                if (!level.isClientSide() && distance < 4 && entity instanceof LivingEntity livingEntity) {
					DamageUtil.suffocateTick(livingEntity, standEntity, 0.025F);
                }
            });

            if (level.isClientSide()) {
                for (int i = 0; i < MathUtil.fractionRandomInc(2.5); i++) {
                    spawnAirStreamParticle(level, mouthPos, spLookVec);
                }
            }
        }

        private void spawnAirStreamParticle(Level level, Vec3 mouthPos, Vec3 lookVec) {
            Vec3 particlePos = mouthPos.add(lookVec.scale(RANGE)
                    .xRot((float) ((Math.random() * 2 - 1) * Math.PI / 6))
                    .yRot((float) ((Math.random() * 2 - 1) * Math.PI / 6)));
            Vec3 vecToStand = mouthPos.subtract(particlePos).normalize().scale(0.75);
            level.addParticle(ModParticles.AIR_STREAM.get(), particlePos.x, particlePos.y, particlePos.z, vecToStand.x, vecToStand.y, vecToStand.z);
        }

        private boolean canInhaleAffectEntity(Level level, Entity entity) {
            if (level instanceof ServerLevel serverLevel && serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
                return !serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get()).shouldFreeze(entity);
            }
            return !level.isClientSide() || !TimeStopState.shouldFreezeClientEntity(entity);
        }

        @Override
        public void onButtonStopHold() {
			startRecovery();
        }

        private void setCooldownFromHeldTicks() {
        	if (cooldownSet || level().isClientSide()) {
        		return;
        	}
        	LivingEntity user = getPowerUser();
        	StandPower standPower = user != null ? StandPower.get(user) : null;
        	if (standPower != null && ability instanceof StarInhaleAbility inhaleAbility) {
        		int ticksHeld = Mth.floor(Math.min(getFullTicksPassed(), INHALE_HOLD_DURATION_MAX));
        		inhaleAbility.setCooldownOnUse(standPower, ticksHeld);
        		cooldownSet = true;
        	}
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.RECOVERY) {
            	setCooldownFromHeldTicks();
            }
            Level level = level();
            if (!level.isClientSide()) {
                return;
            }

            if (newPhase == ActionPhase.PERFORM) {
                if (performer instanceof StandEntity stand) {
                	if (!stand.isVisibleForAll() && !ClientGlobals.canHearStands) {
                		return;
                	}
                    EntityStoppableSoundInstance soundInstance = new EntityStoppableSoundInstance(
                            ClientsideSoundsHelper.withStandSkin(ModSoundEvents.STAR_PLATINUM_INHALE.get(), stand),
                            stand.getSoundSource(),
                            1.0F,
                            1.0F,
                            stand,
                            this.id,
                            () -> this.isOver() || this.getPhase() != ActionPhase.PERFORM
                    );
                    ClientsideSoundsHelper.playNonVanillaClassSound(soundInstance);
                }
            }
        }
        
        @Override
    	public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
    		return true;
    	}
    }

}
