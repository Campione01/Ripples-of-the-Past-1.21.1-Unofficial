package rotp.addons.madeinheaven.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MadeInHeavenChopAbility
        extends StandEntityHeavyPunchAbility {
    public static final int BLEEDING_TICKS = 100;
    public static final int MAX_BLEEDING_AMPLIFIER = 5;
    public static final float EXTRA_KNOCKBACK = 0.5F;
    public static final float GUARD_BREAK_OFFSET = 0.25F;

    public MadeInHeavenChopAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = MadeInHeavenChop::new;
        cooldown(25);
        // 1.16 heavy finisher: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
    }

    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        if (usageGroup != AbilityUsageGroup.GRAB
                && LivingComponentGrab.getEntityGrabbedBy(
                        standEntity) != null) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 StandEntityAction.canClickDuringTask: the running Chop refused
        // another Chop with a plain NEGATIVE, so the press was not queued.
        EntityActionInstance current = standEntity.getCurStandAction();
        if (current != null && !current.isOver()
                && current.ability instanceof MadeInHeavenChopAbility) {
            return ConditionCheck.NEGATIVE;
        }
        // Donor Chop checks stamina instead of ordinary-heavy melee stats.
        return ConditionCheck.noMessage(
                standPower.getStamina() >= 50.0F);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 mih_chop .shout(MIH_CHOP_USER)
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, MadeInHeavenAddon.MIH_CHOP_USER);
        }
    }

    public static final class MadeInHeavenChop
            extends StandEntityHeavyPunch {
        private float shieldDisableChance;
        private ImpactSoundAttempt impactSoundAttempt;

        private static final class ImpactSoundAttempt {
            final Entity primary;
            boolean started;
            boolean hurt;

            ImpactSoundAttempt(Entity primary) {
                this.primary = primary;
            }
        }

        public MadeInHeavenChop(
                rotp.core.powersystem
                        .entityaction.type.EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (performer instanceof StandEntity stand) {
                ActionTarget target = getPunchTarget(stand);
                if (!level.isClientSide()) {
                    StandPower standPower = StandPower.get(getPowerUser());
                    // 1.16 Chop kept StandEntityHeavyAttack.standPerform -> StandEntity.punch -> aimWithThisOrUser,
                    // which turned the Stand toward what the strike aimed at before the hit
                    stand.turnTowardsStrike(isGrabVariation() ? target
                            : StandEntityPunchAbility.strikeAim(stand, target,
                                    StandEntityPunchAbility.CrosshairRule.ENTITY));
                    playHeavyPunchPerformSound(stand);
                    // 1.16 MIHHeavyBlockInstance has no impact sound.
                    boolean playImpact = target.getType() != TargetType.BLOCK
                            && StandEntityPunchAbility.playHitSound(target, level);
                    Holder<SoundEvent> impactSound = playImpact
                            ? getHeavyPunchImpactSound(target) : null;
                    Vec3 impactPosition = playImpact
                            ? target.getCenterPos() : null;
                    boolean livingPrimary = target.getType() == TargetType.ENTITY
                            && target.getMainEntity() instanceof LivingEntity;
                    DamageSource damageSource = makePunchDamageSource();
                    ((DamageSourceModified) damageSource)
                            .jojo_ripples$setStandInvulTicks(10);
                    float damage = StandStatFormulas.getHeavyAttackDamage(
                            stand.getAttackDamage());
                    float explosionRadius = calcExplosionRadius(stand);
                    ImpactSoundAttempt previousAttempt = impactSoundAttempt;
                    ImpactSoundAttempt attempt =
                            new ImpactSoundAttempt(target.getMainEntity());
                    impactSoundAttempt = attempt;
                    try {
                        lastDamageDealtToLiving = 0;
                        boolean deflectedTarget = StandEntityPunchAbility
                                .deflectSilverChariotProjectiles(stand, target);
                        switch (target.getType()) {
                            case ENTITY -> {
                                if (!deflectedTarget) {
                                    hitEntity(target, level, stand, damageSource,
                                            damage, explosionRadius);
                                }
                            }
                            case BLOCK -> hitBlock(target, level, stand,
                                    damageSource, damage, explosionRadius);
                            default -> {}
                        }
                    }
                    finally {
                        impactSoundAttempt = previousAttempt;
                    }
                    // 1.16 StandEntity.attackTarget: after doHit, a living
                    // target's only if it was hurt, any other's always
                    if (playImpact && (!livingPrimary || attempt.hurt)) {
                        StandUtil.broadcastSound((ServerLevel) level,
                                impactPosition, impactSound, true, standPower,
                                stand.getSoundSource(), 1, 1);
                    }
                    punchedTarget = target;
                    standPower.consumeStamina(50);
                }
                if (target.getType() == TargetType.ENTITY) {
                    standRotationTarget = target;
                }
                else {
                    aimAs = AimingEntity.CAMERA_ENTITY;
                }
                StandEntityPunchAbility.releaseUnlockedPunchTarget(stand, this);
            }
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damage,
                float explosionRadius) {
            Entity targetEntity = target.getMainEntity();
            if (targetEntity instanceof LivingEntity living) {
                ImpactSoundAttempt soundAttempt = impactSoundAttempt;
                boolean capturePrimary = soundAttempt != null
                        && soundAttempt.primary == targetEntity
                        && !soundAttempt.started;
                if (capturePrimary) {
                    // Claim before callbacks can reenter this action.
                    soundAttempt.started = true;
                }
                MobEffectInstance existing =
                        living.getEffect(
                                ModStatusEffects.BLEEDING);
                int duration = BLEEDING_TICKS;
                int amplifier = 1;
                if (existing != null) {
                    duration += existing.getDuration();
                    amplifier = Math.min(
                            existing.getAmplifier() + 1,
                            MAX_BLEEDING_AMPLIFIER);
                }
                living.addEffect(new MobEffectInstance(
                        ModStatusEffects.BLEEDING,
                        duration,
                        amplifier,
                        false,
                        false,
                        true));
                if (!usesDetachedPunchKnockback()) {
                    addKnockback(damageSource);
                }
                boolean nonLethal = getPunchModifiers().stream()
                        .anyMatch(modifier ->
                                modifier.makesAttackNonLethal(living));
                if (nonLethal) {
                    ((DamageSourceModified) damageSource)
                            .jojo_ripples$setNonLethal(true);
                    damage = Math.min(damage,
                            Math.max(living.getHealth() - 0.0001F, 0.0F));
                }
                float healthBeforeHit = living.getHealth();
                boolean hurt = usesDetachedPunchKnockback()
                        ? attemptHeavyPunch(stand, living, targetEntity,
                                damageSource, damage, true)
                        : standEntityAttack(stand, living,
                                damageSource, damage);
                if (capturePrimary) {
                    soundAttempt.hurt = hurt;
                }
                lastDamageDealtToLiving = hurt
                        ? Math.max(healthBeforeHit - living.getHealth(), 0)
                        : 0;
                afterHeavyPunchHit(stand, living,
                        damageSource, damage, hurt);
                // Donor Chop's afterAttack omits ordinary-heavy aftermath.
            }
            else if (targetEntity != null) {
                if (usesDetachedPunchKnockback()) {
                    attemptHeavyPunch(stand, targetEntity, targetEntity,
                            damageSource, damage, true);
                }
                else {
                    standEntityAttack(stand, targetEntity,
                            damageSource, damage);
                }
            }
        }

        @Override
        protected void hitBlock(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damage,
                float explosionRadius) {
            // The legacy chop explicitly refused block hits.
        }

        @Override
        protected float getAdditionalHeavyPunchKnockback(
                StandEntity stand) {
            return EXTRA_KNOCKBACK;
        }

        @Override
        protected boolean attemptHeavyPunch(
                StandEntity stand,
                Entity target,
                Entity primaryTarget,
                DamageSource damageSource,
                float rawDamage,
                boolean mainTarget) {
            float previousChance = shieldDisableChance;
            shieldDisableChance = target instanceof Player player
                    ? guardBreakChance(stand, player) : 0.0F;
            try {
                // Read admission here; only the shared hurt helper updates RAW history.
                if (target instanceof StandEntity targetStand
                        && rawDamage > 0.0F
                        && stand.canAttackEntity(targetStand)
                        && !StandEntity.isStandAttackInvulnerable(
                                targetStand)) {
                    float chance = guardBreakChance(stand, targetStand);
                    if (chance > 0.0F
                            && stand.getRandom().nextFloat() < chance) {
                        ((DamageSourceModified) damageSource)
                                .jojo_ripples$setBreakStandGuard(true);
                    }
                }
                return super.attemptHeavyPunch(
                        stand, target, primaryTarget, damageSource,
                        rawDamage, mainTarget);
            }
            finally {
                shieldDisableChance = previousChance;
            }
        }

        @Override
        protected void afterDetachedPunchHurt(
                StandEntity stand,
                LivingEntity target,
                DamageSource source,
                float rawDamage,
                boolean hurt) {
            super.afterDetachedPunchHurt(
                    stand, target, source, rawDamage, hurt);
            if (shieldDisableChance > 0.0F) {
                disableShieldOnHit(target, hurt, shieldDisableChance);
            }
        }

        private static float guardBreakChance(
                StandEntity stand, LivingEntity target) {
            double attackRange = stand.getAttributeValue(
                    Attributes.ENTITY_INTERACTION_RANGE);
            double proximity = 0.0D;
            if (attackRange > 0.0D) {
                Vec3 eye = stand.getEyePosition(1.0F);
                AABB targetBox = target.getBoundingBox();
                double distance;
                if (targetBox.contains(eye)) {
                    distance = 0.0D;
                }
                else {
                    Vec3 end = new Vec3(
                            Mth.lerp(0.5D, targetBox.minX,
                                    targetBox.maxX),
                            Mth.lerp(stand.getBbHeight() == 0
                                            ? 0
                                            : stand.getEyeHeight()
                                                    / stand.getBbHeight(),
                                    targetBox.minY, targetBox.maxY),
                            Mth.lerp(0.5D, targetBox.minZ,
                                    targetBox.maxZ));
                    distance = targetBox.clip(eye, end)
                            .map(hit -> eye.distanceTo(hit)
                                    - stand.getBbWidth() / 2)
                            .orElse(-1.0D);
                }
                proximity = 1.0D - distance / attackRange;
            }
            return Mth.clamp(
                    (float) proximity - GUARD_BREAK_OFFSET,
                    0.0F,
                    1.0F);
        }
    }
}
