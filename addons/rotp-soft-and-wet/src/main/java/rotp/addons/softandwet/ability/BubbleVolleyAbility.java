package rotp.addons.softandwet.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.softandwet.SoftAndWetAddon;
import rotp.addons.softandwet.entity.SoftAndWetBubbleEntity;
import net.minecraft.world.entity.LivingEntity;

public final class BubbleVolleyAbility extends StandEntityAbility {
    public static final int PERFORM_TICKS = 20;
    public static final int RECOVERY_TICKS = 20;
    public static final int COOLDOWN_TICKS = 60;
    public static final float STAMINA_COST_TICK = 3.0F;
    public static final double BASE_FIRE_RATE = 1.5D;
    public static final float MIN_SHOT_VELOCITY = 0.1F;
    public static final float SHOT_VELOCITY_RANGE = 0.6F;
    public static final float SHOT_INACCURACY = 10.0F;

    public static final int EFFECT_PERFORM_TICKS = 20;
    public static final int EXPLOSION_PERFORM_TICKS = 10;
    public static final int EFFECT_COOLDOWN_TICKS = 120;
    public static final float EFFECT_STAMINA_COST_TICK = 6.0F;
    public static final float EFFECT_SHOT_INACCURACY = 1.0F;
    public static final float WALL_MIN_SHOT_VELOCITY = 0.01F;
    public static final float WALL_SHOT_VELOCITY_RANGE = 0.01F;
    public static final float WALL_SHOT_INACCURACY = 20.0F;

    private static final ActionAnimIdentifier BUBBLES_ANIM =
            ActionAnimIdentifier.getOrCreate("bubbles", false);

    private final Pattern pattern;

    public BubbleVolleyAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        this(abilityType, abilityId, Pattern.VOLLEY);
    }

    public BubbleVolleyAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Pattern pattern) {
        super(abilityType, abilityId, BubbleAction::new);
        this.pattern = pattern;
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(
                ActionPhase.PERFORM, pattern.performTicks());
        setDefaultPhaseLength(
                ActionPhase.RECOVERY, pattern.recoveryTicks());
        cooldown(pattern.cooldownTicks());
        if (pattern.resolveLevel() >= 0) {
            resolveLevelToUnlock(pattern.resolveLevel());
        }
        // 1.16 bubble stun was the SHIFT variation of bubble blind
        if (pattern == Pattern.STUN) {
            setPlaysVoiceLineOnSneak();
        }
    }

    public Pattern pattern() {
        return pattern;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return BUBBLES_ANIM;
    }

    public enum Pattern {
        VOLLEY(
                PERFORM_TICKS,
                RECOVERY_TICKS,
                COOLDOWN_TICKS,
                STAMINA_COST_TICK,
                -1,
                true,
                SoftAndWetBubbleEntity.Kind.DAMAGING,
                MIN_SHOT_VELOCITY,
                SHOT_VELOCITY_RANGE,
                SHOT_INACCURACY),
        BLIND(
                EFFECT_PERFORM_TICKS,
                RECOVERY_TICKS,
                EFFECT_COOLDOWN_TICKS,
                EFFECT_STAMINA_COST_TICK,
                2,
                false,
                SoftAndWetBubbleEntity.Kind.BLIND,
                MIN_SHOT_VELOCITY,
                SHOT_VELOCITY_RANGE,
                EFFECT_SHOT_INACCURACY),
        STUN(
                EFFECT_PERFORM_TICKS,
                RECOVERY_TICKS,
                EFFECT_COOLDOWN_TICKS,
                EFFECT_STAMINA_COST_TICK,
                2,
                false,
                SoftAndWetBubbleEntity.Kind.STUN,
                MIN_SHOT_VELOCITY,
                SHOT_VELOCITY_RANGE,
                EFFECT_SHOT_INACCURACY),
        EXPLOSION(
                EXPLOSION_PERFORM_TICKS,
                RECOVERY_TICKS,
                EFFECT_COOLDOWN_TICKS,
                EFFECT_STAMINA_COST_TICK,
                3,
                false,
                SoftAndWetBubbleEntity.Kind.EXPLOSION,
                MIN_SHOT_VELOCITY,
                SHOT_VELOCITY_RANGE,
                EFFECT_SHOT_INACCURACY),
        WALL(
                PERFORM_TICKS,
                RECOVERY_TICKS,
                COOLDOWN_TICKS,
                STAMINA_COST_TICK,
                -1,
                true,
                SoftAndWetBubbleEntity.Kind.WALL,
                WALL_MIN_SHOT_VELOCITY,
                WALL_SHOT_VELOCITY_RANGE,
                WALL_SHOT_INACCURACY);

        private final int performTicks;
        private final int recoveryTicks;
        private final int cooldownTicks;
        private final float staminaCostTick;
        private final int resolveLevel;
        private final boolean continuous;
        private final SoftAndWetBubbleEntity.Kind projectileKind;
        private final float minVelocity;
        private final float velocityRange;
        private final float inaccuracy;

        Pattern(
                int performTicks,
                int recoveryTicks,
                int cooldownTicks,
                float staminaCostTick,
                int resolveLevel,
                boolean continuous,
                SoftAndWetBubbleEntity.Kind projectileKind,
                float minVelocity,
                float velocityRange,
                float inaccuracy) {
            this.performTicks = performTicks;
            this.recoveryTicks = recoveryTicks;
            this.cooldownTicks = cooldownTicks;
            this.staminaCostTick = staminaCostTick;
            this.resolveLevel = resolveLevel;
            this.continuous = continuous;
            this.projectileKind = projectileKind;
            this.minVelocity = minVelocity;
            this.velocityRange = velocityRange;
            this.inaccuracy = inaccuracy;
        }

        public int performTicks() {
            return performTicks;
        }

        public int recoveryTicks() {
            return recoveryTicks;
        }

        public int cooldownTicks() {
            return cooldownTicks;
        }

        public float staminaCostTick() {
            return staminaCostTick;
        }

        public int resolveLevel() {
            return resolveLevel;
        }

        public boolean continuous() {
            return continuous;
        }

        public SoftAndWetBubbleEntity.Kind projectileKind() {
            return projectileKind;
        }

        public float minVelocity() {
            return minVelocity;
        }

        public float velocityRange() {
            return velocityRange;
        }

        public float inaccuracy() {
            return inaccuracy;
        }
    }

    public static final class BubbleAction extends EntityActionInstance {
        private boolean voiceLinePlayed;
        private boolean standSoundPlayed;
        private boolean singleShotFired;

        public BubbleAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 standOffsetFront() with no getOffsetFromUser override: half a
        // block ahead at the default height through every phase, recovery
        // included, full mode only, turned by the view yaw
        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            setStandOffset(
                    0.0D,
                    0.5D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()) {
                return;
            }
            if (!(getPerformer() instanceof StandEntity stand)) {
                startRecovery();
                return;
            }

            LivingEntity user = getPowerUser();
            StandPower standPower =
                    user != null ? StandPower.get(user) : null;
            Pattern pattern = pattern();
            if (user == null || standPower == null) {
                startRecovery();
                return;
            }

            playActivationAudio(user, stand, pattern);
            if (pattern.continuous()) {
                double fireRate = BASE_FIRE_RATE
                        * StandStatFormulas.projectileFireRateScaling(
                                stand, standPower);
                runFractionalTimes(
                        () -> shootBubble(stand, pattern),
                        fireRate,
                        stand.getRandom()::nextDouble);
            } else if (!singleShotFired) {
                shootBubble(stand, pattern);
                singleShotFired = true;
            }
            // 1.16 StandEntityTask paid staminaCostTick after each standTickPerform and ignored the result
            StandAbilityStamina.consume(
                    ability,
                    standPower,
                    pattern.staminaCostTick(),
                    true);
        }

        private void playActivationAudio(
                LivingEntity user,
                StandEntity stand,
                Pattern pattern) {
            if (!voiceLinePlayed) {
                // 1.16 sneak rule: no shout while sneaking
                sayShoutOf(ability, user, SoftAndWetAddon.JOSUKE_BUBBLE_SHOOT);
                voiceLinePlayed = true;
            }
            if (!standSoundPlayed) {
                StandUtil.playStandEntitySound(
                        stand,
                        pattern == Pattern.VOLLEY
                                        || pattern == Pattern.WALL
                                ? SoftAndWetAddon
                                        .SOFT_AND_WET_BUBBLE_SOUND
                                : SoftAndWetAddon
                                        .SOFT_AND_WET_SHIELD_UNSUMMON,
                        1.0F,
                        1.0F);
                standSoundPlayed = true;
            }
        }

        private void shootBubble(
                StandEntity stand, Pattern pattern) {
            SoftAndWetBubbleEntity bubble =
                    new SoftAndWetBubbleEntity(
                            stand,
                            level(),
                            pattern.projectileKind());
            bubble.setShootingPosOf(stand);
            float velocity = pattern.minVelocity()
                    + stand.getRandom().nextFloat()
                            * pattern.velocityRange();
            // 1.16 standEntity.shootProjectile(bubble, velocity, inaccuracy)
            bubble.shootFromRotation(stand, velocity,
                    stand.getProjectileInaccuracy(pattern.inaccuracy()));
            addProjectileWithStandStats(bubble);
        }

        private Pattern pattern() {
            return ability instanceof BubbleVolleyAbility bubbles
                    ? bubbles.pattern()
                    : Pattern.VOLLEY;
        }

        private static void runFractionalTimes(
                Runnable action, double times, RandomDouble random) {
            int wholeTimes = (int) Math.floor(times);
            for (int i = 0; i < wholeTimes; i++) {
                action.run();
            }
            double fraction = times - wholeTimes;
            if (fraction > 0.0D
                    && random.nextDouble() < fraction) {
                action.run();
            }
        }
    }

    @FunctionalInterface
    private interface RandomDouble {
        double nextDouble();
    }
}
