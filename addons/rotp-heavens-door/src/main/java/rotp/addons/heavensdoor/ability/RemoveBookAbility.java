package rotp.addons.heavensdoor.ability;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.AimingEntity;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.state.BookState;
import rotp.addons.heavensdoor.state.BookStateRules;
import rotp.addons.heavensdoor.state.BookStateService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 RemoveBook was HD's heavy attack (InitStands :65): heavy windup
 * and recovery, ARMS required, 0 stamina, and no punch - perform only
 * takes the book off the target.
 */
public final class RemoveBookAbility
        extends StandEntityHeavyPunchAbility {
    public RemoveBookAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        this.createActionObj = RemoveBookAction::new;
        // 1.16 StandEntityHeavyAttack pose.
        this.anim = ActionAnimIdentifier.getOrCreate("heavy_punch", false);
        // 1.16 InitStands :65 standSound(field_219702_mJ), PERFORM phase.
        this.heavyPunchPerformSound(BuiltInRegistries.SOUND_EVENT
                .wrapAsHolder(SoundEvents.VILLAGER_WORK_LIBRARIAN));
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 remove_book did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        // 1.16 RemoveBook was a Stand entity action: the press summoned
        // Heaven's Door and went on; the core summons it after this check.
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 RemoveBook.checkTarget: the aimed entity must be a book. The
        // target is the mouse target, picked from the Stand under manual
        // control.
        LivingEntity target = TearOutAPageAbility.aimedTarget(this, user);
        LivingEntity book = user.level().isClientSide()
                ? clientSnapshotTarget(user, target)
                : BookStateService.ownedBookTarget(user, target);
        return book != null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("heavens_door_no_book");
    }

    private static LivingEntity clientSnapshotTarget(
            LivingEntity owner, LivingEntity rawTarget) {
        LivingEntity target = BookStateService.stateTarget(rawTarget);
        if (target == null
                || target == owner
                || owner.level() != target.level()
                || !target.isAlive()
                || owner.distanceToSqr(target)
                        > BookStateRules.ACTION_RANGE
                                * BookStateRules.ACTION_RANGE
                || !target.hasData(HeavensDoorAddon.BOOK_STATE)) {
            return null;
        }
        BookState snapshot = HeavensDoorAddon.bookState(target);
        return HeavensDoorFinisherSelectionRules.clientSnapshotAuthorizes(
                        snapshot.active(),
                        snapshot.ownerUuid(),
                        snapshot.targetUuid(),
                        snapshot.dimension() != null
                                ? snapshot.dimension().toString()
                                : null,
                        snapshot.expiresAt(),
                        owner.getUUID(),
                        target.getUUID(),
                        target.level().dimension().location().toString(),
                        target.level().getGameTime())
                ? target
                : null;
    }

    /** 1.16 RemoveBook.standPerform replaced the punch. */
    public static final class RemoveBookAction extends StandEntityHeavyPunch {
        public RemoveBookAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(performer instanceof StandEntity stand)) {
                return;
            }
            ActionTarget target = getPunchTarget(stand);
            if (!level().isClientSide()) {
                // The 1.16 perform sound played whether or not a book was hit.
                playHeavyPunchPerformSound(stand);
                // No damage and no stamina; only the book is removed.
                LivingEntity user = getPowerUser();
                LivingEntity aimed = target.getType() == TargetType.ENTITY
                        && target.getMainEntity() instanceof LivingEntity living
                        ? living
                        : null;
                LivingEntity book =
                        BookStateService.authorizedTarget(user, aimed);
                if (book == null && user != null
                        && ability instanceof RemoveBookAbility remove) {
                    book = BookStateService.authorizedTarget(
                            user, TearOutAPageAbility.aimedTarget(remove, user));
                }
                if (book != null) {
                    BookStateService.clear(book);
                }
            }
            if (target.getType() == TargetType.ENTITY) {
                standRotationTarget = target;
            } else {
                aimAs = AimingEntity.CAMERA_ENTITY;
            }
            StandEntityPunchAbility.releaseUnlockedPunchTarget(stand, this);
        }
    }
}
