package rotp.addons.hermitpurple.ability;

import rotp.core.init.ModSoundEvents;
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
import rotp.addons.hermitpurple.entity.HermitGrappleVineEntity;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class HermitGrappleAbility
        extends NoPoseStandEntityAbility {
    private final boolean commandGrab;
    private final boolean bindEntities;
    private final boolean entityGrapple;

    public HermitGrappleAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                type -> new GrappleAction(
                        type,
                        isCommandGrab(abilityId),
                        bindsEntities(abilityId)));
        commandGrab = isCommandGrab(abilityId);
        entityGrapple = isEntityGrapple(abilityId);
        bindEntities = commandGrab || entityGrapple;
        usageGroup = commandGrab
                ? AbilityUsageGroup.GRAB
                : AbilityUsageGroup.UTILITY;
        // 1.16 holdType(): hold-only, the task starts in PERFORM and the
        // standWindupDuration only timed the raise animation.
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 hp_grab, hp_vine and hp_vine_entity (061 InitStands:71, 76-77)
        // set no standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        setButtonHoldPhase(ActionPhase.PERFORM);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        if (!commandGrab
                || abilities == null) {
            return this;
        }
        LivingEntity user = context.getUser();
        if (user == null) {
            return this;
        }
        HermitPurpleStandEntity stand =
                HermitPurpleAddon.controller(user);
        if (stand == null || stand.getAttachedGrabVine() == null) {
            return this;
        }
        // 1.16 HPGrabCommand: both follow-ups also need Throwables Infusion.
        boolean infusion = HermitHamon.hasSkill(
                user, ModHamonSkills.THROWABLES_INFUSION.get());
        boolean overdrive = infusion && HermitHamon.hasSkill(
                user,
                ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE.get());
        boolean healing = infusion && HermitHamon.hasSkill(
                user, ModHamonSkills.HEALING_TOUCH.get());
        String replacement = null;
        if (overdrive && healing) {
            replacement = user.isShiftKeyDown()
                    ? HermitPurpleAddon.HP_GRAB_HEAL_NAME
                    : HermitPurpleAddon.HP_GRAB_OD_NAME;
        }
        else if (overdrive) {
            replacement = HermitPurpleAddon.HP_GRAB_OD_NAME;
        }
        else if (healing) {
            replacement = HermitPurpleAddon.HP_GRAB_HEAL_NAME;
        }
        Ability ability = replacement != null
                ? abilities.getContextVariation(replacement)
                : null;
        return ability != null ? ability : this;
    }

    private static boolean isCommandGrab(AbilityId abilityId) {
        return HermitPurpleAddon.HP_GRAB_NAME.equals(
                abilityId.nameInMoveset());
    }

    private static boolean isEntityGrapple(AbilityId abilityId) {
        return HermitPurpleAddon.HP_VINE_ENTITY_NAME.equals(
                abilityId.nameInMoveset());
    }

    private static boolean bindsEntities(AbilityId abilityId) {
        return isCommandGrab(abilityId)
                || isEntityGrapple(abilityId);
    }

    // 1.16 HPGrapple.standKeepsTarget: only the shift variation
    // (hp_vine_entity) kept the entity it was pressed at
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return entityGrapple
                && target.getType() == ActionTarget.TargetType.ENTITY;
    }

    // 1.16 PowerBaseImpl.checkTarget emptied a pressed target standKeepsTarget
    // refused: hp_vine and hp_grab took none, hp_vine_entity only an entity
    // checkStandTarget allowed.
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return keepsTaskTargetType(standEntity, target)
                && super.takesPressedTarget(standEntity, target);
    }

    public static final class GrappleAction
            extends EntityActionInstance {
        private final boolean commandGrab;
        private final boolean bindEntities;
        private boolean releasedWhileAttached;

        public GrappleAction(
                EntityActionType ability,
                boolean commandGrab,
                boolean bindEntities) {
            super(ability);
            this.commandGrab = commandGrab;
            this.bindEntities = bindEntities;
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            LivingEntity user = getPowerUser();
            if (user != null) {
                ActionTarget aimed = captureActionTargetFromAim(user);
                // 1.16 hp_grab and hp_vine kept no task target, so the Stand
                // took its user's look (defaultRotation); hp_vine_entity
                // turned to an entity its task kept.
                if (performer instanceof StandEntity stand
                        && ability instanceof HermitGrappleAbility grapple
                        && grapple.keepsTaskTarget(stand, aimed)) {
                    keepStandAimedAtTarget(aimed);
                }
            }
            aimAs = AimingEntity.PLAYER;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(performer
                            instanceof HermitPurpleStandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                startRecovery();
                return;
            }

            EntityType<HermitGrappleVineEntity> type =
                    (commandGrab
                            ? HermitPurpleAddon
                                    .HP_GRAB_VINE_ENTITY
                            : HermitPurpleAddon
                                    .HP_GRAPPLING_VINE_ENTITY)
                            .get();
            float hamonDamage = 0.0F;
            if (bindEntities && !commandGrab) {
                hamonDamage = HermitHamon.data(user)
                        .filter(hamon -> hamon.isSkillLearned(
                                ModHamonSkills
                                        .THROWABLES_INFUSION
                                        .get()))
                        .filter(hamon -> hamon.getEnergy() > 0.0F)
                        .map(hamon -> {
                            float cost = hamon
                                    .getHamonStrengthLevel()
                                    / 4.0F;
                            hamon.hamonPointsFromAction(
                                    rotp.core.impl
                                            .powers.hamon.HamonData
                                            .HamonStat.STRENGTH,
                                    cost);
                            hamon.hamonPointsFromAction(
                                    rotp.core.impl
                                            .powers.hamon.HamonData
                                            .HamonStat.CONTROL,
                                    cost);
                            return hamon.getActionEfficiency(
                                    cost, true);
                        })
                        .orElse(0.0F);
                if (hamonDamage > 0.0F) {
                    // 1.16 HPGrapple: Hamon concentration at the user.
                    user.playSound(
                            ModSoundEvents.HAMON_CONCENTRATION.get(),
                            1.0F,
                            1.0F);
                }
            }
            HermitGrappleVineEntity vine =
                    new HermitGrappleVineEntity(
                            type,
                            stand,
                            level(),
                            bindEntities,
                            commandGrab,
                            hamonDamage);
            vine.setPos(vine.getOriginPoint(1.0F));
            vine.shootFromRotation(
                    user,
                    user.getXRot(),
                    user.getYRot(),
                    0.0F,
                    (float) (commandGrab
                            ? HermitPurpleSpec.GRAB_VINE_SPEED
                            : HermitPurpleSpec.GRAPPLE_VINE_SPEED),
                    0.0F);
            if (commandGrab) {
                addProjectileWithStandStats(vine);
            }
            else {
                // 1.16 HPGrapple used addFreshEntity: no stand speed factor.
                vine.withStandSkin(
                        stand.getStandType(), stand.getStandSkin());
                level().addFreshEntity(vine);
            }
            stand.replaceHeldVine(vine);
            if (commandGrab) {
                HermitPurpleSkins.playThrowVoice(ability, user, stand);
            }
            else if (hamonDamage > 0.0F) {
                HermitPurpleSkins.playChargedVoice(user, stand);
            }
            HermitPurpleSkins.playWebSound(stand);
            // 1.16 hp_grab, hp_vine and hp_vine_entity: standSound(PERFORM,
            // VINE_TRHOW); hp_grap is the block catch.
            StandUtil.playStandEntitySound(
                    stand,
                    HermitPurpleAddon.HP_THROW.get(),
                    1.0F,
                    1.0F);
            if (commandGrab) {
                // 1.16 StandEntityTask paid the cost after the perform and
                // ignored the result: a short bar only drops to 0.
                StandAbilityStamina.consume(
                        ability,
                        power,
                        HermitPurpleSpec.GRAB_STAMINA,
                        false);
            }
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM) {
                return;
            }
            // 1.16 holdType(): no hold cap. A grab released on a caught
            // target ends with its vine.
            if (commandGrab) {
                if (releasedWhileAttached
                        && performer
                                instanceof HermitPurpleStandEntity stand
                        && stand.getHeldVine() == null) {
                    startRecovery();
                }
                return;
            }
            // 1.16 staminaCostTick(1): paid every perform tick with the result
            // ignored, so an empty bar does not end the hold.
            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    HermitPurpleSpec.GRAPPLE_STAMINA_PER_TICK,
                    true);
        }

        @Override
        public void onButtonStopHold() {
            if (commandGrab
                    && performer
                            instanceof HermitPurpleStandEntity stand
                    && stand.getAttachedGrabVine() != null) {
                releasedWhileAttached = true;
                return;
            }
            startRecovery();
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 hp_grab (HPGrabCommand), hp_vine and hp_vine_entity (HPGrapple)
        // were holdType Stand actions
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // 1.16 StandEntityAction.canClickDuringTask :495-497: not chainable
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
