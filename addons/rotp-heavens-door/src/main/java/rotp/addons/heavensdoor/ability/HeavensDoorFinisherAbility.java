package rotp.addons.heavensdoor.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.state.BookState;
import rotp.addons.heavensdoor.state.BookStateRules;
import rotp.addons.heavensdoor.state.BookStateService;
import net.minecraft.world.entity.LivingEntity;

public final class HeavensDoorFinisherAbility
        extends StandEntityHeavyPunchAbility {
    public HeavensDoorFinisherAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 HeavensDoorFinisherPunch :44 posed StandPose("finisher"),
        // the skin's own finisher clip; the moveset name has no clip.
        this.anim = ActionAnimIdentifier.getOrCreate("finisher", false);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user =
                HeavensDoorTargeting.activeUser(context);
        // 1.16 took the mouse target, picked from the Stand under manual
        // control
        LivingEntity target = user != null
                ? TearOutAPageAbility.aimedTarget(this, user)
                : null;
        LivingEntity authorized = user != null
                && user.level().isClientSide()
                        ? clientSnapshotTarget(user, target)
                        : BookStateService.authorizedTarget(user, target);
        return authorized != null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "heavens_door_no_book");
    }

    private static LivingEntity clientSnapshotTarget(
            LivingEntity owner, LivingEntity rawTarget) {
        LivingEntity target = BookStateService.stateTarget(rawTarget);
        if (owner == null
                || target == null
                || target == owner
                || owner.level() != target.level()
                || !owner.level().isClientSide()
                || !owner.isAlive()
                || !target.isAlive()
                || HeavensDoorAddon.controller(owner) == null
                || owner.distanceToSqr(target)
                        > BookStateRules.ACTION_RANGE
                                * BookStateRules.ACTION_RANGE
                || !target.hasData(HeavensDoorAddon.BOOK_STATE)) {
            return null;
        }
        BookState snapshot = HeavensDoorAddon.bookState(target);
        return HeavensDoorFinisherSelectionRules
                        .clientSnapshotAuthorizes(
                                snapshot.active(),
                                snapshot.ownerUuid(),
                                snapshot.targetUuid(),
                                snapshot.dimension() != null
                                        ? snapshot.dimension().toString()
                                        : null,
                                snapshot.expiresAt(),
                                owner.getUUID(),
                                target.getUUID(),
                                target.level()
                                        .dimension()
                                        .location()
                                        .toString(),
                                target.level().getGameTime())
                ? target
                : null;
    }
}
