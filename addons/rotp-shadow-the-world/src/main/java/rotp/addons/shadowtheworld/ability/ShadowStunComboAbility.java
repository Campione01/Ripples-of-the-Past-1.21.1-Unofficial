package rotp.addons.shadowtheworld.ability;

import rotp.core.init.ModSoundEvents;
import rotp.core.network.s2c.BloodParticlesPacket;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.CrosshairRule;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ShadowStunComboAbility
        extends StandEntityAbility {
    // 1.16 StunComboAttack's own staminaCost(25) ran after InitStands' 250
    private static final float STAMINA = 25.0F;
    private static final ActionAnimIdentifier ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "stuncomboattack", false);

    public ShadowStunComboAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, StunComboAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 20);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 5);
        cooldown(120);
        resolveLevelToUnlock(2);
        // 1.16 SHIFT variation of super heavy punch: shouts on sneak
        setPlaysVoiceLineOnSneak();
        partsRequired(StandPart.ARMS);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 stun_combo_attack .shout(SHADOW_WORLD_KNIVES_THROW)
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, ShadowTheWorldAddon.SHADOW_WORLD_KNIVES_THROW);
        }
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIM;
    }

    // 1.16 StunComboAttack.standMovesByItself: true for the whole task, so
    // the Stand stays where the combo puts it and cannot be moved manually
    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return true;
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return true;
    }

    @Override
    public boolean lockStandManualMovement(
            StandPower standPower, StandEntity standEntity) {
        return true;
    }

    // 1.16 StunComboAttack.standKeepsTarget true: the task kept the block or
    // entity it was pressed at, or the one a hit struck
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.BLOCK
                || target.getType() == ActionTarget.TargetType.ENTITY;
    }

    // 1.16 StandEntityTask.rotateStand turned the Stand to the task's target
    // before the task ticked; in the perform moveStandForward then gave it
    // the user's yaw back and left the pitch toward the target. Both sides
    // turn here, so a client shows what its server holds.
    @Override
    public void rotateStandTowardsTarget(
            StandEntity standEntity,
            EntityActionInstance action,
            ActionTarget target) {
        super.rotateStandTowardsTarget(standEntity, action, target);
        LivingEntity user = standEntity.getUser();
        if (action.getPhase() == ActionPhase.PERFORM && user != null) {
            faceUserYaw(standEntity, user);
        }
    }

    // The head stays where the task-target turn put it: 1.16
    // moveStandForward called setRot, which never touched the head. The body
    // follows the yaw at the start of the Stand's next tick (core
    // tickHeadTurn), as in 1.16.
    private static void faceUserYaw(StandEntity stand, LivingEntity user) {
        stand.setYRot(user.getYRot());
    }

    private static final class StunComboAction
            extends EntityActionInstance {
        private int punches;

        private StunComboAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected ActionTarget pressedTaskTarget() {
            return getActionTargetSnapshot(level());
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            if (!(level() instanceof ServerLevel)
                    || !(ability
                            instanceof ShadowStunComboAbility combo)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                forceStop();
                return;
            }
            captureActionTargetFromAim(user);
            combo.setCooldownOnUse(power);
        }

        @Override
        public void actionTick() {
            if (!(level() instanceof ServerLevel level)
                    || getPhase() != ActionPhase.PERFORM
                    || !(getPerformer()
                            instanceof StandEntity stand)
                    || !(ability
                            instanceof ShadowStunComboAbility combo)) {
                return;
            }
            LivingEntity user = getPowerUser();
            int tick = (int) getPhaseTick();
            // 1.16 standPerform ran StandEntityLightAttack.standPerform on
            // perform tick 0, before that tick's step: +0.1 to the finisher
            // meter and a hit on the task's target
            if (tick == 0 && user != null) {
                stand.addFinisherMeter(0.1F);
                strike(level, stand, user, getTaskTarget(level), false);
                // 1.16 StandEntityTask paid getStaminaCost after standPerform
                // with the result ignored; a short bar just drops to 0.
                StandAbilityStamina.consume(
                        (Ability) combo,
                        StandPower.get(user),
                        STAMINA,
                        false);
            }
            // 1.16 moveStandForward, on every perform tick: 1.8 blocks along
            // the user's look from where the Stand is, if that stays within
            // 3 blocks of the user's feet, turned to the user's look
            if (user != null) {
                Vec3 moved = stand.position().add(
                        user.getLookAngle().scale(1.8D));
                if (moved.distanceTo(user.position()) <= 3.0D) {
                    stand.setPos(moved);
                }
                faceUserYaw(stand, user);
            }
            if (tick != 1 && tick != 4 && tick != 12) {
                return;
            }
            punches++;
            StandUtil.playStandEntitySound(
                    stand,
                    ModSoundEvents.STAND_PUNCH_HEAVY_SWING,
                    1.0F,
                    0.65F + stand.getRandom().nextFloat() * 0.2F);
            if (user == null) {
                startRecovery();
                return;
            }
            // 1.16: an entity the task holds that died is not struck. Its
            // stopTask could not cancel a perform (canBeCanceled), so neither
            // this nor the third hit ends the combo before its 20 ticks.
            ActionTarget held = getTaskTarget(level);
            Entity heldEntity = held.getEntity();
            boolean heldDead =
                    held.getType() == ActionTarget.TargetType.ENTITY
                            && (heldEntity == null || !heldEntity.isAlive());
            if (!heldDead) {
                strike(level, stand, user, held, punches >= 3);
            }
            if (punches >= 3) {
                ShadowAbilitySupport
                        .unsummonIfUnstable(user);
            }
        }

        private void strike(
                ServerLevel level,
                StandEntity stand,
                LivingEntity user,
                ActionTarget held,
                boolean finalHit) {
            // 1.16 StandEntity.punch: the held target while the Stand reaches
            // it, else what the user aims at; setTaskTarget of that, which a
            // task that does not keep it ignores
            ActionTarget aimed = StandEntityPunchAbility.getFreshPunchTarget(
                    stand, held, true, CrosshairRule.BLOCK_OR_ENTITY);
            // aimWithThisOrUser turned the Stand toward what the strike aimed
            // at, just before the hit
            stand.turnTowardsStrike(StandEntityPunchAbility.strikeAim(
                    stand, aimed, CrosshairRule.BLOCK_OR_ENTITY));
            setTaskTarget(aimed.isEmpty(level) ? null : aimed);
            switch (aimed.getType()) {
                case BLOCK -> punchBlock(level, stand, aimed);
                case ENTITY -> {
                    Entity target = aimed.getEntity();
                    if (target != null) {
                        punchEntity(level, stand, target, finalHit);
                    }
                }
                default -> {}
            }
        }

        // 1.16 StandBlockPunch: the light punch's block rule, and the impact
        // at the block's centre unless the block breaks instantly
        private static void punchBlock(
                ServerLevel level, StandEntity stand, ActionTarget block) {
            boolean impact = StandEntityPunchAbility.playHitSound(block, level);
            Vec3 center = Vec3.atCenterOf(block.getBlockPos());
            StandEntityPunchAbility.StandEntityPunch.hitBlockTarget(
                    block, level, stand, true);
            if (impact) {
                playImpact(level, stand, center);
            }
        }

        // 1.16 StunComboAttack.punchEntity, of any entity
        private void punchEntity(
                ServerLevel level,
                StandEntity stand,
                Entity target,
                boolean finalHit) {
            float damage =
                    StandStatFormulas.getLightAttackDamage(
                            stand.getAttackDamage())
                            * 1.2F;
            // read before the hit updates the Stand immunity it checks
            boolean admitted = damage > 0
                    && stand.canAttackEntity(target)
                    && !(target instanceof LivingEntity living
                            && StandEntity.isStandAttackInvulnerable(living));
            boolean hurt = standEntityAttack(
                    stand,
                    target,
                    makePunchDamageSource(),
                    damage);
            if (admitted) {
                // 1.16 addKnockback(0.1 / 0.5): StandEntityPunch.doAttack
                // pushed a living target it hurt by half of that, then
                // knockbackTarget pushed any target it attacked once more
                float strength = finalHit ? 0.25F : 0.05F;
                if (hurt && target instanceof LivingEntity) {
                    knockback(stand, target, strength);
                }
                knockback(stand, target, strength);
            }
            if (target instanceof LivingEntity living) {
                if (!hurt) {
                    return;
                }
                if (finalHit) {
                    living.addEffect(
                            new MobEffectInstance(
                                    ShadowTheWorldAddon
                                            .KNOCKDOWN,
                                    60,
                                    0,
                                    false,
                                    false));
                }
                // 1.16 afterAttack's BloodParticlesPacket, for a hit that
                // landed on a living target
                PacketDistributor.sendToPlayersTrackingChunk(
                        level,
                        new ChunkPos(living.blockPosition()),
                        new BloodParticlesPacket(
                                new Vec3(
                                        living.getX(),
                                        living.getY()
                                                + living.getBbHeight()
                                                        * 0.5D,
                                        living.getZ()),
                                0.3F,
                                8,
                                living.getId()));
            }
            // 1.16 attackTarget: the impact of a living target's hit that
            // landed, and of every hit on another entity
            playImpact(level, stand, target.getBoundingBox().getCenter());
        }

        // 1.16 DamageUtil.knockback along the Stand-to-target yaw; a target
        // that is not living gets that motion set
        private static void knockback(
                StandEntity stand, Entity target, float strength) {
            Vec3 toTarget = target.position().subtract(stand.position());
            float yaw = (float) -Mth.atan2(toTarget.x, toTarget.z)
                    * Mth.RAD_TO_DEG;
            float radians = yaw * Mth.DEG_TO_RAD;
            if (target instanceof LivingEntity living) {
                living.knockback(
                        strength, Mth.sin(radians), -Mth.cos(radians));
            } else {
                target.setDeltaMovement(
                        new Vec3(0.0D, 0.0D, strength).yRot(-radians));
            }
        }

        private static void playImpact(
                ServerLevel level, StandEntity stand, Vec3 pos) {
            StandUtil.broadcastSound(
                    level,
                    pos,
                    ModSoundEvents.THE_WORLD_PUNCH_HEAVY,
                    true,
                    stand.getUserPower(),
                    stand.getSoundSource(),
                    1.0F,
                    1.0F);
        }
    }
}
