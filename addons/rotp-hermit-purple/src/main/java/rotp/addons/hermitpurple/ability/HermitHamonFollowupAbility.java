package rotp.addons.hermitpurple.ability;

import rotp.core.init.ModParticles;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.hermitpurple.HermitHamon;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.HermitPurpleSkins;
import rotp.addons.hermitpurple.entity.HermitGrappleVineEntity;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.addons.hermitpurple.entity.HermitVineProjectileEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class HermitHamonFollowupAbility
        extends NoPoseStandEntityAbility {
    private final Mode mode;

    public HermitHamonFollowupAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        this(abilityType, abilityId, Mode.from(abilityType));
    }

    private HermitHamonFollowupAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Mode mode) {
        super(
                abilityType,
                abilityId,
                type -> new FollowupAction(type, mode));
        this.mode = mode;
        usageGroup = mode == Mode.HEAL
                ? AbilityUsageGroup.UTILITY
                : AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 hp_grab_od, hp_grab_sc, hp_grab_heal and hp_beat (061
        // InitStands:72-74, 85) set no standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        if (mode == Mode.BEAT) {
            resolveLevelToUnlock(1);
        }
    }

    @Override
    public boolean addToControlSchemeEditing() {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        HermitPurpleStandEntity stand =
                HermitPurpleAddon.controller(user);
        if (user == null || stand == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (mode == Mode.BEAT) {
            if (stand.getAttachedBindingLash() == null) {
                return ConditionCheck.NEGATIVE;
            }
            return HermitHamon.data(user)
                    .filter(hamon -> hamon.hasEnergy(
                            HermitPurpleSpec
                                    .HAMON_BEAT_ENERGY))
                    .isPresent()
                    ? ConditionCheck.POSITIVE
                    : ConditionCheck.createNegative(
                            "no_energy_hamon");
        }
        if (stand.getAttachedGrabVine() == null
                || !mode.hasRequiredSkill(user)) {
            return ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class FollowupAction
            extends EntityActionInstance {
        private final Mode mode;
        private LivingEntity target;
        private HermitGrappleVineEntity grabVine;
        private HermitVineProjectileEntity bindingLash;

        public FollowupAction(
                EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            if (!(performer
                    instanceof HermitPurpleStandEntity stand)) {
                return;
            }
            if (mode == Mode.BEAT) {
                bindingLash = stand.getAttachedBindingLash();
                target = bindingLash != null
                        ? bindingLash.getEntityAttachedTo()
                        : null;
            }
            else {
                grabVine = stand.getAttachedGrabVine();
                target = grabVine != null
                        ? grabVine.getEntityAttachedTo()
                        : null;
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || target == null
                    || !target.isAlive()) {
                startRecovery();
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }
            HermitHamon.data(user).ifPresent(hamon -> {
                switch (mode) {
                    case OVERDRIVE ->
                            overdrive(ability, user, hamon, target);
                    case SCARLET ->
                            scarlet(ability, user, hamon, target);
                    case HEAL -> heal(hamon, target);
                    case BEAT -> beat(user, hamon, target);
                }
            });
            if (mode != Mode.BEAT) {
                // 1.16 StandEntityTask paid the cost after the perform and
                // ignored the result: a short bar only drops to 0.
                StandAbilityStamina.consume(
                        ability,
                        StandPower.get(user),
                        HermitPurpleSpec.GRAB_FOLLOWUP_STAMINA,
                        false);
            }
            if (mode == Mode.BEAT && bindingLash != null) {
                bindingLash.discard();
            }
            startRecovery();
        }

        private static void overdrive(
                EntityActionType ability,
                LivingEntity user,
                HamonData hamon,
                LivingEntity target) {
            hamon.hamonPointsFromAction(
                    HamonData.HamonStat.STRENGTH,
                    HermitPurpleSpec
                            .GRAB_FOLLOWUP_POINTS);
            HermitHamon.hurt(
                    target,
                    user,
                    HermitPurpleSpec
                            .GRAB_OVERDRIVE_DAMAGE,
                    false);
            hamon.consumeEnergy(
                    hamon.getMaxEnergy()
                            * HermitPurpleSpec
                                    .GRAB_OVERDRIVE_ENERGY_RATIO,
                    user);
            HermitPurpleSkins.playOverdriveVoice(
                    ability, user, HermitPurpleAddon.controller(user));
        }

        private static void scarlet(
                EntityActionType ability,
                LivingEntity user,
                HamonData hamon,
                LivingEntity target) {
            HermitPurpleSkins.playOverdriveVoice(
                    ability, user, HermitPurpleAddon.controller(user));
            hamon.hamonPointsFromAction(
                    HamonData.HamonStat.STRENGTH,
                    HermitPurpleSpec
                            .GRAB_FOLLOWUP_POINTS);
            if (HermitHamon.hurt(
                    target,
                    user,
                    HermitPurpleSpec
                            .GRAB_OVERDRIVE_DAMAGE,
                    true)) {
                int seconds = Mth.floor(
                        2.0F
                                + 8.0F
                                * hamon.getHamonStrengthLevel()
                                / 60.0F
                                * 2.0F);
                target.igniteForSeconds(seconds);
            }
        }

        private static void heal(
                HamonData hamon, LivingEntity target) {
            float control = Mth.clamp(
                    (float) hamon.getHamonControlLevel() / 60.0F,
                    0.0F, 1.0F);
            float efficiency =
                    hamon.getActionEfficiency(750.0F, true);
            int duration = (int) (
                    (50.0F + efficiency * 50.0F)
                            * (1.0F + control));
            int amplifier = Mth.clamp(
                    (int) ((control - 1.0E-4F) * 3.0F
                            + (efficiency - 0.25F)
                                    * 4.0F / 3.0F
                            - 1.0F),
                    0,
                    2);
            target.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    duration,
                    amplifier));
            if (hamon.isSkillLearned(
                    ModHamonSkills.EXPEL_VENOM.get())) {
                target.removeEffect(MobEffects.POISON);
                target.removeEffect(MobEffects.WITHER);
                target.removeEffect(MobEffects.HUNGER);
                target.removeEffect(MobEffects.CONFUSION);
            }
            HamonAbilityHelpers.sendHamonParticles(
                    target,
                    ModParticles.HAMON_SPARK.get(),
                    Math.max(2, (int) (12.0F
                            * control * efficiency)));
        }

        private void beat(
                LivingEntity user,
                HamonData hamon,
                LivingEntity target) {
            float efficiency = hamon.getActionEfficiency(
                    HermitPurpleSpec.HAMON_BEAT_ENERGY,
                    true,
                    ModHamonSkills.OVERDRIVE.get(),
                    user);
            if (HermitHamon.hurt(
                    target,
                    user,
                    HermitPurpleSpec.HAMON_BEAT_DAMAGE
                            * efficiency,
                    false)) {
                level().playSound(
                        null,
                        target,
                        ModSoundEvents.HAMON_SYO_PUNCH.get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.5F);
                target.knockback(
                        1.25F,
                        user.getX() - target.getX(),
                        user.getZ() - target.getZ());
                float points = Math.min(
                        HermitPurpleSpec
                                .HAMON_BEAT_ENERGY,
                        hamon.getEnergy());
                hamon.consumeEnergy(
                        HermitPurpleSpec
                                .HAMON_BEAT_ENERGY,
                        user);
                hamon.hamonPointsFromAction(
                        HamonData.HamonStat.STRENGTH,
                        points * efficiency);
                hamon.syncOnUpdate(user);
            }
            HamonAbilityHelpers.doMeleeAttack(user, target);
            if (user instanceof Player player) {
                player.resetAttackStrengthTicker();
            }
        }
    }

    private enum Mode {
        OVERDRIVE,
        SCARLET,
        HEAL,
        BEAT;

        private static Mode from(AbilityType<?> abilityType) {
            return switch (abilityType.registryKey.getPath()) {
                case HermitPurpleAddon.HP_GRAB_OD_NAME ->
                        OVERDRIVE;
                case HermitPurpleAddon.HP_GRAB_SC_NAME ->
                        SCARLET;
                case HermitPurpleAddon.HP_GRAB_HEAL_NAME ->
                        HEAL;
                case HermitPurpleAddon.HP_BEAT_NAME -> BEAT;
                default -> throw new IllegalArgumentException(
                        "Unknown Hermit Purple follow-up: "
                                + abilityType.registryKey);
            };
        }

        // 1.16 grab follow-ups all pair their skill with Throwables Infusion.
        private boolean hasRequiredSkill(LivingEntity user) {
            if (this == BEAT) {
                return HermitHamon.data(user).isPresent();
            }
            if (!HermitHamon.hasSkill(
                    user,
                    ModHamonSkills.THROWABLES_INFUSION.get())) {
                return false;
            }
            return switch (this) {
                case OVERDRIVE -> HermitHamon.hasSkill(
                        user,
                        ModHamonSkills
                                .SUNLIGHT_YELLOW_OVERDRIVE
                                .get());
                case SCARLET -> HermitHamon.hasSkill(
                        user,
                        ModHamonSkills.SCARLET_OVERDRIVE.get());
                case HEAL -> HermitHamon.hasSkill(
                        user,
                        ModHamonSkills.HEALING_TOUCH.get());
                case BEAT -> false;
            };
        }
    }
}
