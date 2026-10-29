package rotp.addons.killerqueen.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.KillerQueenPhase1Spec;
import rotp.addons.killerqueen.state.KillerQueenAuthority;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BlockBombAbility
        extends StandEntityAbility {
    public static final int WINDUP_TICKS = 5;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 0;
    public static final int COOLDOWN_TICKS = 60;
    public static final double DETONATE_STAND_REACH = 9.0D;

    public BlockBombAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, BlockBombAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        cooldown(COOLDOWN_TICKS);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        // 1.16 BlockBombAction: refused while Bites the Dust is armed
        if (check.isPositive()
                && BitesTheDustKitLock.locked(context)) {
            return ConditionCheck.NEGATIVE;
        }
        return check;
    }

    // 1.16 BlockBombAction.standKeepsTarget: a block, which the Stand's
    // offset turns to (the default lockOnTargetPosition)
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.BLOCK;
    }

    // 1.16 BlockBombAction.standKeepsTarget refused every entity, so a press
    // at one left the task without a target and the Stand did not turn to it
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() != ActionTarget.TargetType.ENTITY;
    }

    // 1.16 BlockBombAction.replaceAction: with a block bomb set and the
    // user's Killer Queen inside the user's box grown by 9 blocks, the slot
    // is kq_block_explode in place of the plant
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        Ability detonate = context != null
                ? context.getMoveset().getAbility(
                        KillerQueenAddon.BLOCK_BOMB_DETONATE_NAME)
                : null;
        return detonate != null
                && standNearUser(context)
                && detonate.isAbilityAvailable(context)
                ? detonate
                : super.replaceWithSubAbility(context, abilities);
    }

    private static boolean standNearUser(Power<?> context) {
        LivingEntity user = context.getUser();
        StandEntity stand = KillerQueenAuthority.currentStand(user);
        return stand != null
                && stand.getBoundingBox().intersects(
                        user.getBoundingBox()
                                .inflate(DETONATE_STAND_REACH));
    }

    public static final class BlockBombAction
            extends EntityActionInstance {
        public BlockBombAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            ActionTarget target =
                    captureActionTargetFromAim(stand);
            keepStandAimedAtTarget(target);
            aimAs = AimingEntity.STAND;
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
            // 1.16 kq_block_bomb standSound(USER_KQ) (InitStands :87): Kira's
            // voice at the Stand as the perform began, whatever came of it
            StandUtil.playStandEntitySound(
                    stand, KillerQueenAddon.USER_KIRA, 1.0F, 1.0F);
            StandPower power = StandPower.get(user);
            // 1.16 BlockBombAction.standPerform: silent below the floor
            if (power != null
                    && power.getStamina()
                            > KillerQueenPhase1Spec
                                    .BLOCK_BOMB_MARK_STAMINA_FLOOR) {
                markTarget(stand, user);
            }
            // 1.16 StandEntityTask pays the cost after standPerform,
            // whatever came of it
            StandAbilityStamina.consume(
                    ability,
                    power,
                    KillerQueenPhase1Spec.BLOCK_BOMB_MARK_STAMINA,
                    false);
        }

        // 1.16 BlockBombAction.standPerform cast its own ray as it
        // performed, whatever the press aimed at: from the user's eyes (the
        // Stand's under manual control) over the Stand's max range, entities
        // first and through blocks (JojoModUtil.rayTrace). An entity on it
        // left no bomb; else the bomb went on the block whose outline it met
        // and the Stand to the point it met.
        private void markTarget(
                StandEntity stand, ServerPlayer user) {
            LivingEntity aiming =
                    stand.isManuallyControlled() ? stand : user;
            Vec3 eyes = aiming.getEyePosition();
            Vec3 look = aiming.getLookAngle();
            double range = stand.getMaxRange();
            if (!HitResultUtil.clipMultipleEntities(
                            eyes, look, range, level(), aiming,
                            entity -> entity != stand
                                    && entity != user,
                            0.0D, 0.0D)
                    .isEmpty()) {
                return;
            }
            BlockHitResult hit = HitResultUtil.clipBlocks(
                    new ClipContext(
                            eyes,
                            eyes.add(look.normalize().scale(range)),
                            ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE,
                            aiming),
                    level());
            if (hit.getType() != HitResult.Type.BLOCK
                    || level().getBlockState(hit.getBlockPos())
                            .isAir()) {
                return;
            }
            KillerQueenStateService.markBlockBomb(
                    user, hit.getBlockPos());
            stand.moveTo(hit.getLocation());
        }
    }
}
