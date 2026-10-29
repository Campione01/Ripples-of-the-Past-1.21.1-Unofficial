package rotp.addons.hermitpurple.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import rotp.addons.hermitpurple.HermitHamon;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.HermitPurpleSkins;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.addons.hermitpurple.entity.HermitVineProjectileEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;

public final class HermitVineAttackAbility
        extends NoPoseStandEntityAbility {
    private final boolean binding;

    public HermitVineAttackAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                type -> new VineAttack(
                        type,
                        HermitPurpleAddon.HP_VINE_SHIFT_NAME.equals(
                                abilityId.nameInMoveset())));
        binding = HermitPurpleAddon.HP_VINE_SHIFT_NAME.equals(
                abilityId.nameInMoveset());
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(
                ActionPhase.WINDUP,
                HermitPurpleSpec.VINE_ATTACK_WINDUP_TICKS);
        setDefaultPhaseLength(
                ActionPhase.PERFORM,
                HermitPurpleSpec.VINE_ATTACK_PERFORM_TICKS);
        // 1.16 cooldown(10, 5, 0.5f) and cooldown(30, 35, 0.5f): Resolve halves the
        // second part instead of removing it.
        cooldown(
                binding
                        ? 30
                        : HermitPurpleSpec
                                .VINE_ATTACK_COOLDOWN_TECHNICAL,
                binding
                        ? 35
                        : HermitPurpleSpec
                                .VINE_ATTACK_COOLDOWN_ADDITIONAL,
                0.5F);
        // 1.16 hp_vine_attack and hp_vine_shift were swingHand() without
        // withUserPunch: performing them reset the user's attack strength.
        setResetsAttackStrengthOnPerform();
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        // 1.16 HPVineAttack.replaceAction ran for both variants, so Shift
        // with the attack button also becomes the beat follow-up.
        if (abilities == null) {
            return this;
        }
        HermitPurpleStandEntity stand =
                HermitPurpleAddon.controller(context.getUser());
        if (stand != null
                && stand.getAttachedBindingLash() != null
                && stand.getFinisherMeter() > 0.5F) {
            Ability beat = abilities.getContextVariation(
                    HermitPurpleAddon.HP_BEAT_NAME);
            if (beat != null) {
                return beat;
            }
        }
        return this;
    }

    // 1.16 HPVineAttack.standKeepsTarget: any target
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return true;
    }

    public static final class VineAttack
            extends EntityActionInstance {
        private final boolean binding;
        private boolean windupSoundPlayed;

        public VineAttack(
                EntityActionType ability, boolean binding) {
            super(ability);
            this.binding = binding;
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            if (performer instanceof HermitPurpleStandEntity stand) {
                keepStandAimedAtTarget(
                        captureActionTargetFromAim(stand));
                // 1.16 hp_vine_shift: standSound(Phase.WINDUP, VINE_TRHOW),
                // played once as the task entered its wind-up. A restored
                // input lifecycle sets the same action again.
                if (binding
                        && !windupSoundPlayed
                        && !level().isClientSide()
                        && getPhase() == ActionPhase.WINDUP) {
                    windupSoundPlayed = true;
                    playThrowSound(stand);
                }
            }
            aimAs = AimingEntity.STAND;
        }

        private static void playThrowSound(
                HermitPurpleStandEntity stand) {
            StandUtil.playStandEntitySound(
                    stand,
                    HermitPurpleAddon.HP_THROW.get(),
                    1.0F,
                    1.0F);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(performer
                            instanceof HermitPurpleStandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }

            // 1.16 HPVineAttack.standPerform: one vine turned by
            // MathUtil.xRotYRotOffsets, one along the user's look.
            Vec2 spread = xRotYRotOffsets(
                    stand.getRandom().nextDouble() * 1.5D
                            / (binding ? 1 : 2) * Math.PI * 2.0D,
                    10.0D);
            spawnVine(stand, user, spread.y, spread.x);
            spawnVine(stand, user, 0.0F, 0.0F);
            // 1.16 HPVineAttack: both variants say the Overdrive line.
            if (HermitHamon.hasSkill(
                            user,
                            ModHamonSkills.THROWABLES_INFUSION.get())
                    && HermitHamon.hasChargedVines(user)) {
                HermitPurpleSkins.playChargedVoice(user, stand);
            }
            HermitPurpleSkins.playWebSound(stand);
            // 1.16 hp_vine_attack: standSound(Phase.PERFORM, VINE_TRHOW).
            if (!binding) {
                playThrowSound(stand);
            }
            else {
                if (HermitHamon.hasSkill(
                        user,
                        ModHamonSkills.HAMON_CUTTER.get())) {
                    HermitHamon.fireLearnedAbility(
                            user, "hamon_cutter");
                }
                if (HermitHamon.hasSkill(
                        user,
                        ModHamonSkills.BUBBLE_CUTTER.get())) {
                    HermitHamon.fireLearnedAbility(
                            user, "bubble_cutter");
                }
            }
            // 1.16 StandEntityTask paid the cost after the perform and ignored
            // the result: a short bar only drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    HermitPurpleSpec.VINE_ATTACK_STAMINA,
                    false);
        }

        private void spawnVine(
                HermitPurpleStandEntity stand,
                LivingEntity user,
                float yawOffset,
                float pitchOffset) {
            HermitVineProjectileEntity vine =
                    new HermitVineProjectileEntity(stand, level());
            vine.setBinding(binding);
            vine.setHamonCharged(
                    HermitHamon.hasSkill(
                                    user,
                                    ModHamonSkills
                                            .THROWABLES_INFUSION
                                            .get())
                            && HermitHamon.hasChargedVines(user));
            // 1.16 HPVineAttack.addProjectile created the vine at the
            // Stand's user and it followed the user's rotation.
            vine.setShootingPosOf(user);
            vine.shootFromRotation(
                    user,
                    user.getXRot() + pitchOffset,
                    user.getYRot() + yawOffset,
                    0.0F,
                    HermitPurpleSpec
                            .VINE_ATTACK_VELOCITY,
                    0.0F);
            // 1.16 used one owner-bound HPVineEntity for both variants.
            vine.initializeLashFrame(yawOffset, pitchOffset);
            addProjectileWithStandStats(vine);
            stand.registerLash(vine);
        }

        // 1.16 MathUtil.xRotYRotOffsets
        private static Vec2 xRotYRotOffsets(double angleXYRad, double z) {
            double xSq = -Math.cos(angleXYRad);
            double ySq = Math.sin(angleXYRad);
            xSq *= xSq;
            ySq *= ySq;
            double zSq = z * z;
            double angleXZ = Math.acos(
                    Math.sqrt((xSq + zSq) / (xSq + ySq + zSq)));
            double angleYZ = Math.acos(
                    Math.sqrt((ySq + zSq) / (xSq + ySq + zSq)));
            if (angleXYRad > Math.PI) {
                angleXZ *= -1;
            }
            if (angleXYRad > Math.PI / 2
                    && angleXYRad < Math.PI * 3 / 2) {
                angleYZ *= -1;
            }
            return new Vec2(
                    (float) Math.toDegrees(angleYZ),
                    (float) Math.toDegrees(angleXZ));
        }
    }
}
