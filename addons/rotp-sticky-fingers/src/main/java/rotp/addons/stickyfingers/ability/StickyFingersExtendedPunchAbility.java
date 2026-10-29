package rotp.addons.stickyfingers.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.entity.StickyFingersExtendedPunchEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class StickyFingersExtendedPunchAbility
        extends StandEntityAbility {
    private static final ActionAnimIdentifier RANGED_ATTACK_ANIM =
            ActionAnimIdentifier.getOrCreate("rangedAttack", false);

    public StickyFingersExtendedPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ExtendedPunchAction::new);
        setDefaultPhaseLength(
                ActionPhase.PERFORM,
                StickyFingersPhase1Spec.EXTENDED_PUNCH_PERFORM_TICKS);
        cooldown(
                StickyFingersPhase1Spec
                        .EXTENDED_PUNCH_COOLDOWN_TECHNICAL,
                StickyFingersPhase1Spec
                        .EXTENDED_PUNCH_COOLDOWN_ADDITIONAL);
        partsRequired(StandPart.ARMS);
        setIgnoresPerformerStun();
    }

    @Override
    public String getSpriteName(Power<?> context) {
        LivingEntity user = context != null
                ? context.getUser()
                : null;
        return user != null && user.isShiftKeyDown()
                ? super.getSpriteName(context) + "_pull"
                : super.getSpriteName(context);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return RANGED_ATTACK_ANIM;
    }

    public static final class ExtendedPunchAction
            extends EntityActionInstance {
        public ExtendedPunchAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            // 1.16 sticky_fingers_extended_punch standOffsetFront(), turned
            // by the view yaw
            setStandOffset(
                    0.0D,
                    0.5D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }

            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }

            // 1.16 .shout(BRUNO_HEAVY_PUNCH): silent while sneaking
            sayShoutOf(ability, user, StickyFingersAddon.BRUNO_HEAVY_PUNCH);
            stand.playSound(
                    StickyFingersAddon
                            .STICKY_FINGERS_EXTENDED_PUNCH_SWING
                            .get(),
                    1.0F,
                    1.0F);

            StickyFingersExtendedPunchEntity extendedPunch =
                    new StickyFingersExtendedPunchEntity(
                            stand, level());
            extendedPunch.setLifeSpan(
                    StickyFingersPhase1Spec
                            .EXTENDED_PUNCH_PERFORM_TICKS);
            extendedPunch.setPullTargets(user.isShiftKeyDown());
            extendedPunch.setShootingPosOf(stand);
            addProjectileWithStandStats(extendedPunch);
            // 1.16 StandEntityTask: the cost is paid after the perform with
            // the result ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    StickyFingersPhase1Spec.EXTENDED_PUNCH_STAMINA,
                    false);
        }
    }
}
