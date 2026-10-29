package rotp.addons.stonefree.entity;

import javax.annotation.Nullable;

import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class StoneFreeStandEntity extends StandEntity {
    private static final EntityDataAccessor<Boolean> FOREARM_PRESENT =
            SynchedEntityData.defineId(
                    StoneFreeStandEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHORT_FOREARM =
            SynchedEntityData.defineId(
                    StoneFreeStandEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CAPTURE_ACTIVE =
            SynchedEntityData.defineId(
                    StoneFreeStandEntity.class,
                    EntityDataSerializers.BOOLEAN);

    public StoneFreeStandEntity(
            EntityType<? extends StoneFreeStandEntity> type,
            Level level) {
        super(type, level);
    }

    public boolean hasForearm() {
        return entityData.get(FOREARM_PRESENT);
    }

    public boolean hasShortForearm() {
        return entityData.get(SHORT_FOREARM);
    }

    public boolean isCaptureActive() {
        return entityData.get(CAPTURE_ACTIVE);
    }

    public void setForearmState(
            boolean forearm, boolean shortForearm) {
        entityData.set(FOREARM_PRESENT, forearm);
        entityData.set(SHORT_FOREARM, shortForearm);
    }

    public void setCaptureActive(boolean capture) {
        entityData.set(CAPTURE_ACTIVE, capture);
    }

    // Arms that still perform a user string from the user's Stand-power slot
    // stay for it: an unsummon started as they went idle or as their own
    // action ended is stopped before it ticks, and the string's end sends
    // them away (leaveAfterUserString).
    @Override
    public void tick() {
        if (!level().isClientSide() && isArmsOnlyMode()
                && getCurStandAction() instanceof EntityActionInstance action
                && action.ability == ModSpecialActions.STAND_UNSUMMON.get()
                && !action.isOver()
                && performsUserString(null)) {
            stopRetraction();
        }
        super.tick();
    }

    // retractAndUnsummon does nothing while any ability key is down and
    // nothing asks again, so idle arms leave as they do when an action ends.
    public void leaveAfterUserString() {
        leaveAfterUserString(null);
    }

    // Idle also means no other user string these arms perform from the
    // user's Stand-power slot; the one whose end asks is still set there.
    public void leaveAfterUserString(
            @Nullable EntityActionInstance ending) {
        if (isArmsOnlyMode() && getCurStandAction() == null
                && !performsUserString(ending)) {
            startStandUnsummon();
        }
    }

    private boolean performsUserString(
            @Nullable EntityActionInstance ending) {
        LivingEntity user = getUser();
        EntityActionInstance action = user != null
                ? LivingComponentAction.getCurEntityAction(
                        user, PowerClass.STAND)
                : null;
        return action != null && action != ending && !action.isOver()
                && action.getPerformer() == this;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FOREARM_PRESENT, true);
        builder.define(SHORT_FOREARM, false);
        builder.define(CAPTURE_ACTIVE, false);
    }
}
