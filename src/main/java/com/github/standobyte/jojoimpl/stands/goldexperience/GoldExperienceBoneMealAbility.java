package com.github.standobyte.jojoimpl.stands.goldexperience;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.NoPoseStandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

public class GoldExperienceBoneMealAbility extends NoPoseStandEntityAbility {
    private static final int STAMINA_COST = 2;
    private static final double TARGET_RANGE = 64.0D;

    public GoldExperienceBoneMealAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, BoneMealAction::new);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 5);
        standAutoSummonMode(AutoSummonMode.MAIN_ARM);
    }
    
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (isSyncedBoneMealTargetTooFar(user.level(), user)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (!isValidBoneMealTarget(user.level(), user, null)) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck check = super.checkSpecificConditions(context);
        return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
    }
    
    @Override
    public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (action instanceof BoneMealAction boneMealAction) {
            ActionTarget target = findBoneMealTarget(level, powerUser, performer);
            boneMealAction.target = target;
            if (!target.isEmpty(level)) {
                action.standRotationTarget = target;
            }
        }
    }
    
    private static boolean isValidBoneMealTarget(Level level, LivingEntity user, @Nullable LivingEntity aiming) {
        ActionTarget target = findBoneMealTarget(level, user, aiming);
        return switch (target.getType()) {
            case ENTITY -> target.getMainEntity() instanceof AgeableMob animal && animal.isBaby();
            case BLOCK -> {
                BlockPos pos = target.getBlockPos();
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof BonemealableBlock || GETreeLeavesDecayState.isTreeStemBlock(state)) {
                    yield true;
                }
                BlockPos waterPos = pos.relative(target.getFace());
                yield canGrowWaterPlant(level, pos, waterPos, target.getFace());
            }
            default -> false;
        };
    }
    
    private static ActionTarget findBoneMealTarget(Level level, LivingEntity user, @Nullable LivingEntity aiming) {
        LivingEntity aimingEntity = aiming != null ? aiming : user;
        ActionTarget syncedTarget = getSyncedLookTarget(level, aimingEntity);
        if (!syncedTarget.isEmpty(level)
                && HitResultUtil.isTargetWithinRange(syncedTarget, aimingEntity, level, TARGET_RANGE, TARGET_RANGE)) {
            return syncedTarget;
        }
        return HitResultUtil.clip(
                aimingEntity.getEyePosition(),
                aimingEntity.getLookAngle(),
                TARGET_RANGE,
                TARGET_RANGE,
                level,
                entity -> entity instanceof AgeableMob animal && animal.isBaby(),
                aimingEntity,
                0);
    }

    private static ActionTarget getSyncedLookTarget(Level level, LivingEntity aimingEntity) {
        var aim = LivingComponentAction.getAim(aimingEntity);
        if (aim == null) {
            return ActionTarget.EMPTY;
        }
        ActionTarget target = aim.getTarget();
        return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
    }

    private static boolean isSyncedBoneMealTargetTooFar(Level level, LivingEntity aimingEntity) {
        ActionTarget syncedTarget = getSyncedLookTarget(level, aimingEntity);
        return !syncedTarget.isEmpty(level)
                && !HitResultUtil.isTargetWithinRange(syncedTarget, aimingEntity, level, TARGET_RANGE, TARGET_RANGE);
    }

    private static boolean applyOriginalBonemealEffect(Level level, BlockPos pos, Direction face) {
        if (BoneMealItem.growCrop(ItemStack.EMPTY, level, pos)) {
            if (!level.isClientSide()) {
                level.levelEvent(1505, pos, 5);
            }
            return true;
        }

        BlockPos waterPos = pos.relative(face);
        if (canGrowWaterPlant(level, pos, waterPos, face) && BoneMealItem.growWaterPlant(ItemStack.EMPTY, level, waterPos, face)) {
            if (!level.isClientSide()) {
                level.levelEvent(1505, waterPos, 5);
            }
            return true;
        }
        return false;
    }

    private static boolean canGrowWaterPlant(LevelReader level, BlockPos clickedPos, BlockPos waterPos, Direction face) {
        return level.getBlockState(clickedPos).isFaceSturdy(level, clickedPos, face)
                && level.getFluidState(waterPos).isSource();
    }

    public static class BoneMealAction extends EntityActionInstance {
        private ActionTarget target = ActionTarget.EMPTY;

        public BoneMealAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (level.isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }

            LivingEntity aimingEntity = performer instanceof StandEntity ? performer : user;
            target = !target.isEmpty(level) ? target.resolveEntityId(level) : findBoneMealTarget(level, user, aimingEntity);
            if (!target.isEmpty(level)) {
                standRotationTarget = target;
            }
            StandPower standPower = StandPower.get(user);
            if (!StandAbilityStamina.canPay(standPower, STAMINA_COST)) {
                StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
                return;
            }
            boolean success = switch (target.getType()) {
                case ENTITY -> {
                    if (target.getMainEntity() instanceof AgeableMob animal && animal.isBaby()) {
                        int age = animal.getAge();
                        animal.ageUp((int) ((-age / 20) * 0.2F), true);
                        yield true;
                    }
                    yield false;
                }
                case BLOCK -> {
                    BlockPos pos = target.getBlockPos();
                    boolean grown = false;
                    if (level instanceof ServerLevel serverLevel) {
                        GETreeLeavesDecayState.TreeDecay tree = GETreeLeavesDecayState.startDecay(serverLevel, pos, Integer.MAX_VALUE, 1);
                        if (tree != null) {
                            tree.logs().forEach(logPos -> level.levelEvent(1505, logPos, 5));
                            grown = true;
                        }
                    }
                    if (!grown) {
                        grown = applyOriginalBonemealEffect(level, pos, target.getFace());
                    }
                    yield grown;
                }
                default -> false;
            };

            if (success) {
                StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
            }
        }
    }
}
