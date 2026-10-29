package rotp.addons.killerqueen.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.KillerQueenPhase1Spec;
import rotp.addons.killerqueen.entity.SheerHeartAttackEntity;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class SheerHeartAttackAbility
        extends StandEntityAbility {
    public static final int WINDUP_TICKS = 0;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 0;
    public static final int COOLDOWN_TICKS = 200;

    public SheerHeartAttackAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, SheerHeartAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(
                KillerQueenPhase1Spec
                        .SHEER_HEART_RESOLVE_LEVEL);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 sheer_heart .shout(KQ_SH_SUMMON): a user voice line, silent while sneaking
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, KillerQueenAddon.KIRA_SHEER_HEART);
        }
    }

    // 1.16 SheerHeartAttack kept no target (no standKeepsTarget, no target
    // requirement): its task's target was always empty, so the bomb was
    // never given the entity it was pressed at
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return false;
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return super.isAbilityAvailable(context)
                && context.getUser() instanceof Player player
                && !sheerHeartPresent(player);
    }

    private static boolean sheerHeartPresent(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            KillerQueenStateService.releaseAbsentSheerHeart(serverPlayer);
        }
        return KillerQueenAddon.state(player).hasSheerHeart();
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        // 1.16 SheerHeartAttack: refused while Bites the Dust is armed
        if (BitesTheDustKitLock.locked(context)) {
            return ConditionCheck.NEGATIVE;
        }
        if (!(context.getUser() instanceof Player player)
                || sheerHeartPresent(player)) {
            return ConditionCheck.createNegative(
                    "killer_queen_sheer_heart_active");
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class SheerHeartAction
            extends EntityActionInstance {
        public SheerHeartAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (getPerformer() instanceof StandEntity stand) {
                setActionTargetSnapshot(
                        captureActionTargetFromAim(stand));
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer()
                            instanceof StandEntity stand)
                    || !(getPowerUser()
                            instanceof ServerPlayer user)) {
                return;
            }
            launch(stand, user);
            // 1.16 SheerHeartAttack had no stamina condition: the Stand task
            // pays the cost after standPerform and a short bar drops to 0
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    KillerQueenPhase1Spec.SHEER_HEART_STAMINA,
                    false);
        }

        private void launch(StandEntity stand, ServerPlayer user) {
            ActionTarget target =
                    getActionTargetSnapshot(level());
            Entity selected = target.getMainEntity();
            LivingEntity initialTarget =
                    selected instanceof LivingEntity living
                            && living != user
                            && !(living instanceof StandEntity)
                            && JojoModUtil.canHarm(user, living)
                                    ? living
                                    : null;

            SheerHeartAttackEntity sheerHeart =
                    KillerQueenAddon.SHEER_HEART_ENTITY.get()
                            .create(level());
            if (sheerHeart == null) {
                return;
            }
            sheerHeart.bind(user, stand, initialTarget);
            if (sheerHeart.isRemoved()) {
                return;
            }
            level().addFreshEntity(sheerHeart);
            KillerQueenStateService.linkSheerHeart(
                    user, sheerHeart);
        }
    }
}
