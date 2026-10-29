package rotp.addons.kingcrimson.ability;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModSoundEvents;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.util.constants.EntityEvents;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.kingcrimson.KingCrimsonPhase2Spec;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

abstract class AbstractKingCrimsonStrikeAbility
        extends StandEntityPunchAbility {
    // 1.16 StandEntityHeavyAttack.punchEntity: setStandInvulTime(10)
    private static final int HEAVY_STAND_INVUL_TICKS = 10;
    // 1.16 StandEntityHeavyAttack.standTickWindup: the swing plays with three windup ticks left
    private static final int HEAVY_SWING_TICKS_BEFORE_PERFORM = 3;

    private final float damageMultiplier;
    private final Holder<SoundEvent> voiceLine;
    private Holder<SoundEvent> windupStandSound;
    private Holder<SoundEvent> entityImpactSound;
    private float performStaminaDrain;
    private float armorPiercingPerStrength =
            KingCrimsonPhase2Spec.ARMOR_PIERCING_PER_STRENGTH;
    private float addedKnockback;
    private float knockbackMultiplier = 1.0F;
    private float fixedDisableBlockingChance = -1.0F;
    private double areaRange;
    private double areaVerticalKnockback;
    private boolean consumesFinisher;
    private boolean buildsFinisher = true;

    protected AbstractKingCrimsonStrikeAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            float damageMultiplier,
            float staminaCost,
            int cooldownTicks,
            Holder<SoundEvent> impactSound,
            Holder<SoundEvent> voiceLine) {
        super(abilityType, abilityId, KingCrimsonStrikeAction::new);
        this.damageMultiplier = damageMultiplier;
        performStaminaDrain(staminaCost);
        this.voiceLine = voiceLine;
        partsRequired(StandPart.MAIN_BODY);
        standAutoSummonMode(AutoSummonMode.FULL);
        setDefaultPhaseLength(
                ActionPhase.WINDUP,
                StandStatFormulas.getHeavyAttackWindup(12.0D, 0.0F));
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY,
                StandStatFormulas.getHeavyAttackRecovery(12.0D));
        cooldown(cooldownTicks);
        punchImpactSound(impactSound);
    }

    // 1.16 punchEntity(...).impactSound(...) after copyProperties: a hit on an entity sounds this, a block the action's
    // own punchSound
    protected final void entityImpactSound(Holder<SoundEvent> sound) {
        entityImpactSound = sound;
    }

    protected final void armorPiercingPerStrength(float value) {
        armorPiercingPerStrength = Math.max(value, 0.0F);
    }

    protected final void knockback(float add, float multiply) {
        addedKnockback = add;
        knockbackMultiplier = Math.max(multiply, 0.0F);
    }

    protected final void fixedDisableBlockingChance(float chance) {
        fixedDisableBlockingChance = Mth.clamp(chance, 0.0F, 1.0F);
    }

    protected final void areaImpact(
            double range, double verticalKnockback) {
        areaRange = Math.max(range, 0.0D);
        areaVerticalKnockback = verticalKnockback;
    }

    protected final void consumesFinisher(boolean value) {
        consumesFinisher = value;
        if (value) {
            buildsFinisher = false;
        }
    }

    protected final void buildsFinisher(boolean value) {
        buildsFinisher = value;
    }

    // 1.16 StandEntityAction.Builder.standSound(Phase.WINDUP, ...): played at the Stand, also while sneaking
    protected final void windupStandSound(Holder<SoundEvent> sound) {
        windupStandSound = sound;
    }

    // 1.16 StandEntityTask: the task drains its cost once PERFORM starts and never refuses the action for it
    protected final void performStaminaDrain(float amount) {
        performStaminaDrain = Math.max(amount, 0.0F);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        if (!level.isClientSide() && performer instanceof StandEntity stand) {
            action.phasesLength.put(
                    ActionPhase.WINDUP,
                    StandStatFormulas.getHeavyAttackWindup(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
            action.phasesLength.put(
                    ActionPhase.RECOVERY,
                    StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
            if (heavyAttack()) {
                // 1.16 StandEntityAction.Builder: standPerformDuration 1
                action.phasesLength.put(ActionPhase.PERFORM, 1);
            }
            if (powerUser != null) {
                if (voiceLine != null) {
                    sayShout(powerUser, voiceLine);
                }
            }
        }
    }

    protected abstract void applyHitEffects(
            StandEntity stand,
            LivingEntity target);

    protected boolean hitsNonLivingPrimary() {
        return false;
    }

    protected boolean usesDetachedPrimaryKnockback() {
        return false;
    }

    protected boolean areaBeforePrimary() {
        return false;
    }

    // A 1.16 StandEntityHeavyAttack (Ground Punch, Desperate Eye Jab) rather than a light attack
    protected boolean heavyAttack() {
        return false;
    }

    // 1.16 getStandRecoveryTicks is getHeavyAttackRecovery(attack speed, last heavy finisher value): the heavy
    // attacks inherit it, Chop and Impale override it.
    protected boolean heavyRecoveryAtEntry() {
        return heavyAttack();
    }

    // 1.16 StandEntityHeavyAttack.punchEntity: StandStatFormulas.getHeavyAttackDamage, at least 1
    protected boolean heavyDamageFloor() {
        return false;
    }

    private float rawDamage(StandEntity stand) {
        double strength = stand.getAttackDamage();
        return (heavyDamageFloor()
                        ? StandStatFormulas.getHeavyAttackDamage(strength)
                        : (float) strength)
                * damageMultiplier;
    }

    private static final class KingCrimsonStrikeAction
            extends StandEntityPunch {
        private KingCrimsonStrikeAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected Holder<SoundEvent> getPunchImpactSound(ActionTarget target) {
            return target.getType() == TargetType.ENTITY
                    && ability instanceof AbstractKingCrimsonStrikeAbility strike
                    && strike.entityImpactSound != null
                    ? strike.entityImpactSound
                    : super.getPunchImpactSound(target);
        }

        // 1.16 getStandRecoveryTicks, read when RECOVERY starts (StandEntityTask.moveToPhase).
        private boolean samplesHeavyRecoveryEntry(ActionPhase newPhase) {
            return newPhase == ActionPhase.RECOVERY && phase != null && phase != newPhase
                    && performer instanceof StandEntity && !performer.level().isClientSide()
                    && ability instanceof AbstractKingCrimsonStrikeAbility strike && strike.heavyRecoveryAtEntry();
        }

        @Override
        public void setPhase(ActionPhase newPhase, int tick) {
            boolean samplesEntry = samplesHeavyRecoveryEntry(newPhase);
            float previousLength = samplesEntry ? phasesLength.getFloat(newPhase) : 0.0F;
            if (samplesEntry && performer instanceof StandEntity stand) {
                phasesLength.put(ActionPhase.RECOVERY, StandStatFormulas.getHeavyAttackRecovery(
                        stand.getAttackSpeed(), stand.getLastHeavyFinisherValue()));
            }
            super.setPhase(newPhase, tick);
            if (samplesEntry && Float.compare(previousLength, phasesLength.getFloat(newPhase)) != 0) {
                syncPhaseChanges();
            }
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (getPerformer() instanceof StandEntity stand) {
                if (stand.isArmsOnlyMode()) {
                    // Arms-only variants already use their dedicated idle
                    // offset; the full-body legacy offset lifts them on each
                    // repeated strike.
                    stand.offsetFromUser.resetToIdle();
                    stand.offsetFromUser.syncToTracking();
                }
                if (ability
                        instanceof AbstractKingCrimsonStrikeAbility strike
                        && strike.consumesFinisher
                        && !stand.level().isClientSide()) {
                    stand.setHeavyPunchFinisher();
                    stand.addFinisherMeter(-0.51F, 0);
                }
                if (ability
                        instanceof AbstractKingCrimsonStrikeAbility strike
                        && strike.windupStandSound != null
                        && !stand.level().isClientSide()) {
                    StandUtil.playStandEntitySound(
                            stand, strike.windupStandSound, 1.0F, 1.0F);
                }
            }
        }

        @Override
        public void actionTick() {
            if (performer instanceof StandEntity stand
                    && stand.level().isClientSide()
                    && !playedSwingSound
                    && ability instanceof AbstractKingCrimsonStrikeAbility strike
                    && strike.heavyAttack()
                    && calcFullTicks(ActionPhase.PERFORM, 0) - getFullTicksPassed()
                            <= HEAVY_SWING_TICKS_BEFORE_PERFORM
                    && ClientGlobals.canHearStand(stand)) {
                stand.level().playLocalSound(
                        stand.getX(), stand.getEyeY(), stand.getZ(),
                        ClientsideSoundsHelper.withStandSkin(
                                ModSoundEvents.STAND_PUNCH_HEAVY_SWING.get(),
                                stand),
                        stand.getSoundSource(),
                        1.0F,
                        // 1.16 StandEntityHeavyAttack.clPlayPunchSwingSound
                        0.65F + stand.getRandom().nextFloat() * 0.2F,
                        false);
                playedSwingSound = true;
            }
            super.actionTick();
        }

        // 1.16 KingCrimsonModel has one pose per strike; no clip is mirrored by the swinging arm.
        @Override
        public boolean usesHandedAnimation() {
            return false;
        }

        // 1.16 StandEntityHeavyAttack.isFreeRecovery: a heavy attack's recovery is free only for a finisher.
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return super.canBeCancelledInto(cancellingAbility)
                    && !(ability instanceof AbstractKingCrimsonStrikeAbility strike
                            && strike.heavyAttack()
                            && strike.isStandFinisherOf == null);
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (!(getPerformer() instanceof StandEntity stand)
                    || !(ability
                            instanceof AbstractKingCrimsonStrikeAbility
                            strikeAbility)) {
                return;
            }

            ActionTarget target = getPunchTarget(stand);
            if (!level.isClientSide()) {
                LivingEntity user = getPowerUser();
                if (user == null) {
                    forceStop();
                    return;
                }
                StandPower standPower = StandPower.get(user);
                // 1.16 StandEntityLightAttack / StandEntityHeavyAttack.standPerform -> StandEntity.punch ->
                // aimWithThisOrUser turned the Stand toward what the strike aimed at before the hit
                stand.turnTowardsStrike(isGrabVariation() ? target
                        : StandEntityPunchAbility.strikeAim(stand, target, strikeAbility.heavyAttack()
                                ? StandEntityPunchAbility.CrosshairRule.ENTITY
                                : StandEntityPunchAbility.CrosshairRule.BLOCK_OR_ENTITY));

                Holder<SoundEvent> impactSound =
                        getPunchImpactSound(target);
                boolean entityTarget =
                        target.getType() == TargetType.ENTITY;
                boolean heavyBlock = strikeAbility.heavyAttack()
                        && target.getType() == TargetType.BLOCK;
                // judged before the hit, as 1.16's StandBlockPunch.playImpactSound read the hardness of the block struck
                boolean playImpactSound = impactSound != null
                        && !heavyBlock
                        && StandEntityPunchAbility.playHitSound(
                                target, level);
                Vec3 impactSoundPos = playImpactSound
                        ? target.getCenterPos()
                        : null;

                if (strikeAbility.buildsFinisher) {
                    stand.addFinisherMeter(0.1F);
                }
                boolean deflected =
                        StandEntityPunchAbility
                                .deflectSilverChariotProjectiles(
                                        stand, target);
                if (strikeAbility.areaBeforePrimary()) {
                    strikeAbility.applyAreaImpact(
                            stand, target, punchedTargetEntity(target));
                }
                impactSoundDue = null;
                switch (target.getType()) {
                    case ENTITY -> {
                        if (!deflected) {
                            hitEntity(target, level, stand);
                        }
                    }
                    case BLOCK -> {
                        if (heavyBlock) {
                            // 1.16 HeavyPunchBlockInstance: the plain block punch, the blast, then the impact
                            // sound on any block that was not air (playImpactSound true)
                            StandEntityHeavyPunchAbility.StandEntityHeavyPunch.hitBlockAsHeavyPunch(
                                    target, level, stand, !isUserCreative(), impactSound);
                        }
                        else {
                            hitBlock(target, level, stand);
                        }
                    }
                    default -> {
                    }
                }
                // 1.16 StandEntity.attackTarget: the impact sound comes after the hit, which may have broken the
                // block, and an entity's only if the hit landed
                if (playImpactSound && (!entityTarget
                        || impactSoundDue == null || impactSoundDue)) {
                    StandUtil.broadcastSound(
                            (ServerLevel) level,
                            impactSoundPos,
                            impactSound,
                            true,
                            standPower,
                            stand.getSoundSource(),
                            1.0F,
                            1.0F);
                }
                if (!strikeAbility.areaBeforePrimary()) {
                    strikeAbility.applyAreaImpact(
                            stand, target, punchedTargetEntity(target));
                }
                punchedTarget = target;
                if (strikeAbility.performStaminaDrain > 0.0F
                        && standPower != null) {
                    standPower.consumeStamina(
                            strikeAbility.performStaminaDrain);
                }
            }

            if (target.getType() == TargetType.ENTITY) {
                standRotationTarget = target;
            } else {
                aimAs = AimingEntity.CAMERA_ENTITY;
            }
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand) {
            Entity targetEntity = target.getMainEntity();
            if (targetEntity != null
                    && !(targetEntity instanceof LivingEntity)
                    && ability instanceof AbstractKingCrimsonStrikeAbility strikeAbility
                    && strikeAbility.hitsNonLivingPrimary()) {
                float damage = strikeAbility.rawDamage(stand);
                boolean admitted = damage > 0.0F && stand.canAttackEntity(targetEntity);
                boolean hurt = false;
                if (admitted) {
                    hurt = standEntityAttack(stand, targetEntity, makePunchDamageSource(), damage);
                    if (strikeAbility.usesDetachedPrimaryKnockback()) {
                        strikeAbility.applyPrimaryKnockback(stand, targetEntity);
                    }
                }
                impactSoundDue = true;
                // 1.16 StandEntityPunch.doHit adds the light punch's addFinisher on every entity attempt.
                if (strikeAbility.buildsFinisher) {
                    stand.addFinisherMeter(0.1F);
                }
                if (hurt && strikeAbility.heavyAttack()) {
                    heavyHitHooks(stand, targetEntity);
                }
                return;
            }
            if (!(targetEntity instanceof LivingEntity targetLiving)
                    || !(ability
                            instanceof AbstractKingCrimsonStrikeAbility
                            strikeAbility)) {
                return;
            }

            DamageSource damageSource = makePunchDamageSource();
            if (damageSource instanceof DamageSourceModified modified) {
                modified.jojo_ripples$modifyKnockback(
                        strikeAbility.usesDetachedPrimaryKnockback()
                                ? 0.0F : strikeAbility.addedKnockback,
                        strikeAbility.knockbackMultiplier);
                if (strikeAbility.heavyAttack()) {
                    modified.jojo_ripples$setStandInvulTicks(
                            HEAVY_STAND_INVUL_TICKS);
                }
            }

            float damage = strikeAbility.rawDamage(stand);
            float armorPiercing =
                    (float) stand.getAttackDamage()
                            * strikeAbility
                                    .armorPiercingPerStrength;
            if (armorPiercing > 0.0F) {
                damage = DamageUtil.addArmorPiercing(
                        damage,
                        armorPiercing,
                        targetLiving,
                        damageSource);
            }

            float disableBlockingChance =
                    strikeAbility.fixedDisableBlockingChance >= 0.0F
                            ? strikeAbility.fixedDisableBlockingChance
                            : disableBlockingChance(
                                    stand, targetLiving);
            // Capture raw admission before guard break or virtual hurt can change target state.
            boolean admitted = damage > 0.0F && stand.canAttackEntity(targetLiving)
                    && !StandEntity.isStandAttackInvulnerable(targetLiving);
            if (targetLiving instanceof StandEntity targetStand
                    && admitted
                    && stand.getRandom().nextFloat()
                            < disableBlockingChance) {
                targetStand.breakStandBlocking(
                        StandStatFormulas.getGuardBreakTicks(
                                targetStand.getDurability()));
            }
            boolean hurt = false;
            impactSoundDue = false;
            if (standEntityAttack(stand, targetLiving, damageSource, damage)) {
                hurt = true;
                impactSoundDue = true;
                if (strikeAbility.usesDetachedPrimaryKnockback()) {
                    strikeAbility.applyPrimaryKnockback(stand, targetLiving);
                }
                strikeAbility.applyHitEffects(
                        stand, targetLiving);
                disableShield(
                        stand,
                        targetLiving,
                        disableBlockingChance);
            }
            // 1.16 StandEntityPunch.doHit adds the light punch's addFinisher on every entity attempt.
            if (strikeAbility.buildsFinisher) {
                stand.addFinisherMeter(0.1F);
            }
            if (admitted && strikeAbility.usesDetachedPrimaryKnockback()) {
                strikeAbility.applyPrimaryKnockback(stand, targetLiving);
            }
            if (hurt && strikeAbility.heavyAttack()) {
                heavyHitHooks(stand, targetLiving);
            }
        }

        // 1.16 StandEntity.getProximityRatio: the eye-ray distance over the Stand's melee reach
        private static float disableBlockingChance(
                StandEntity stand,
                LivingEntity target) {
            double attackRange = stand.getAttributeValue(
                    Attributes.ENTITY_INTERACTION_RANGE);
            double proximity = attackRange > 0.0D
                    ? 1.0D - ActionTargetRange.getDistance(
                            stand, target.getBoundingBox()) / attackRange
                    : 0.0D;
            return Mth.clamp(
                    (float) (proximity - 0.25D),
                    0.0F,
                    1.0F);
        }

        // 1.16 HeavyPunchInstance.afterAttack, after the knockback tail of a hurt target
        private static void heavyHitHooks(StandEntity stand, Entity target) {
            EntityActionAbility.onHitByHeavyAttack(target);
            Entity knockedBack = target;
            if (target instanceof StandEntity targetStand
                    && targetStand.isAlive()
                    && targetStand.getUser() != null) {
                knockedBack = targetStand.getUser();
            }
            KnockbackCollisionImpact impact =
                    KnockbackCollisionImpact.getHandler(knockedBack);
            if (impact != null) {
                impact.onPunchSetKnockbackImpact(
                                knockedBack.getDeltaMovement(), stand)
                        .withImpactExplosion(
                                Math.max(StandEntityHeavyPunchAbility
                                        .calcExplosionRadius(stand) - 0.5F, 0.0F),
                                null,
                                0.0F);
            }
        }

        private static void disableShield(
                StandEntity stand,
                LivingEntity target,
                float chance) {
            if (!(target instanceof Player player)
                    || chance <= 0.0F) {
                return;
            }
            ItemStack usedItem = player.getUseItem();
            if (usedItem.isEmpty()
                    || !usedItem.canPerformAction(
                            ItemAbilities.SHIELD_BLOCK)
                    || stand.getRandom().nextFloat() >= chance) {
                return;
            }
            player.getCooldowns().addCooldown(
                    usedItem.getItem(),
                    KingCrimsonPhase2Spec.SHIELD_COOLDOWN_TICKS);
                player.stopUsingItem();
                player.level().broadcastEntityEvent(
                        player,
                        EntityEvents.SHIELD_BREAK_SOUND);
        }

        private static Entity punchedTargetEntity(ActionTarget target) {
            return target.getType() == TargetType.ENTITY
                    ? target.getMainEntity()
                    : null;
        }
    }

    private void applyPrimaryKnockback(StandEntity stand, Entity target) {
        if (addedKnockback <= 0.0F) {
            return;
        }
        Vec3 toTarget = target.position().subtract(stand.position());
        float yaw = (float) -Mth.atan2(toTarget.x, toTarget.z);
        float strength = addedKnockback * 0.5F;
        if (target instanceof LivingEntity living) {
            living.knockback(strength, Mth.sin(yaw), -Mth.cos(yaw));
        }
        else {
            target.setDeltaMovement(new Vec3(0, 0, strength).yRot(-yaw));
        }
    }

    private void applyAreaImpact(
            StandEntity stand,
            ActionTarget target,
            Entity directHit) {
        if (areaRange <= 0.0D
                || stand.level().isClientSide()
                || target.getType() == TargetType.EMPTY) {
            return;
        }
        LivingEntity user = stand.getUser();
        if (user == null) {
            return;
        }

        Vec3 center = directHit != null ? directHit.position() : target.getCenterPos();
        AABB bounds = new AABB(center, center).inflate(areaRange);
        DamageSource source = new DamageSource(
                DamageUtil.type(
                        stand.level(),
                        DamageTypes.MOB_ATTACK),
                stand,
                user);
        for (Entity entity : stand.level().getEntities(
                stand,
                bounds,
                candidate -> candidate.isAlive()
                        && candidate != user
                        && candidate != directHit)) {
            if (entity instanceof LivingEntity living) {
                double distance = center.distanceTo(
                        living.position());
                float damage = (float) (stand.getAttackDamage()
                        * (0.5D - distance / areaRange));
                damage = Math.max(0.25F, damage);
                living.hurt(source, damage);
            }

            double pushX = (entity.getX() - center.x) * 0.4D;
            double pushZ = (entity.getZ() - center.z) * 0.4D;
            entity.push(pushX, areaVerticalKnockback, pushZ);
            entity.hurtMarked = true;
        }
    }
}
