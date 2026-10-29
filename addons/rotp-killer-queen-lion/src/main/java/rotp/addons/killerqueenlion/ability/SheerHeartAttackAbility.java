package rotp.addons.killerqueenlion.ability;

import java.util.UUID;

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

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.KillerQueenPhase1Spec;
import rotp.addons.killerqueenlion.entity.SheerHeartAttackEntity;
import rotp.addons.killerqueenlion.state.KillerQueenStateService;
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
    public static final int BOMB_COUNT = 7;
    public static final double RING_STEP = 1.2566D;
    public static final double RING_RADIUS = 0.8D;

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
            sayShout(powerUser, KillerQueenLionAddon.KIRA_SHEER_HEART);
        }
    }

    // 1.16 SheerHeartAttack kept no target (no standKeepsTarget, no target
    // requirement): its task's target was always empty, so no bomb was
    // given the entity it was pressed at
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
        return KillerQueenLionAddon.state(player).hasSheerHeart();
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        if (BitesTheDustLock.applies(context)) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
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

            // 1.16 SheerHeartAttack.standPerform: seven bombs on a ring
            // around the Stand, and only the first gets the task's target
            // (none, see takesPressedTarget).
            // The step is not 2*PI/7, so the last two land on the first two.
            UUID summonLink = null;
            for (int i = 0; i < BOMB_COUNT; i++) {
                SheerHeartAttackEntity sheerHeart =
                        KillerQueenLionAddon.SHEER_HEART_ENTITY.get()
                                .create(level());
                if (sheerHeart == null) {
                    return;
                }
                sheerHeart.bind(
                        user, stand, i == 0 ? initialTarget : null);
                if (sheerHeart.isRemoved()) {
                    return;
                }
                sheerHeart.setPos(
                        stand.getX() + Math.cos(i * RING_STEP) * RING_RADIUS,
                        stand.getY(),
                        stand.getZ() + Math.sin(i * RING_STEP) * RING_RADIUS);
                if (summonLink == null) {
                    summonLink = sheerHeart.getUUID();
                } else {
                    sheerHeart.joinSummon(summonLink);
                }
                level().addFreshEntity(sheerHeart);
                if (i == 0) {
                    KillerQueenStateService.linkSheerHeart(
                            user, sheerHeart);
                }
            }
        }
    }
}
