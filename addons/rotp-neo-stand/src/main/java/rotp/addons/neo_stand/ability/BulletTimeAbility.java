package rotp.addons.neo_stand.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.addons.neo_stand.NeoStandAddon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;

public final class BulletTimeAbility extends StandEntityAbility {
    public BulletTimeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, BulletTimeAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 (021 aa/aa/ac.java:60, :70-83) registered cooldown(0, 300)
        // but overrode getCooldownAdditional to 0 and getCooldownTechnical
        // to the remaining ticks of the bullet time effect, 60 as the press
        // had just applied it: the cooldown a press set was the bullet
        // time's own length, all technical, so Resolve never shortened it
        cooldown(BulletTimeService.DURATION_TICKS, 0);
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.MAIN_BODY);
    }

    // 1.16 star_platinum_bullet_time (021 aa/aa/ac.java, af/ab.java :81)
    // was a StandAction its user's power performed beside the Stand's
    // task, never a task of the Stand
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 StandAction.onClick summoned the Stand only while the power was
    // not active: an arms-only Star Platinum stays arms only
    @Override
    protected AutoSummonMode getAutoSummonMode(
            StandPower standPower, LivingEntity user) {
        return standPower.getSummonedStandEntity() != null
                ? AutoSummonMode.DISABLED
                : super.getAutoSummonMode(standPower, user);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context.getUser();
        return user != null && !isTimeStopped(user)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        Ability inherited =
                super.replaceWithSubAbility(context, abilities);
        LivingEntity user =
                context != null ? context.getUser() : null;
        if (inherited != this
                || user == null
                || !isActive(user)) {
            return inherited;
        }
        Ability cancel = abilities.getContextVariation(
                NeoStandAddon.BULLET_TIME_CANCEL);
        return cancel != null ? cancel : this;
    }

    private static boolean isActive(LivingEntity user) {
        return user.level().isClientSide()
                ? BulletTimeService.isClientOwnerActive(
                        user.getUUID())
                : BulletTimeService.isActive(user);
    }

    private static boolean isTimeStopped(LivingEntity user) {
        ChunkPos position = new ChunkPos(user.blockPosition());
        return user.level().isClientSide()
                ? TimeStopState.getClientDisplayInstance(position)
                        .isPresent()
                : user.level()
                        .getData(ModDataAttachmentTypes.TIME_STOP.get())
                        .isTimeStopped(position);
    }

    public static final class BulletTimeAction
            extends EntityActionInstance {
        public BulletTimeAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(getPowerUser() instanceof ServerPlayer user)) {
                return;
            }
            if (!isTimeStopped(user)) {
                BulletTimeService.activate(user);
            }
        }
    }
}
