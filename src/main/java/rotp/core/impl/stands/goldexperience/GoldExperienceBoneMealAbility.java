package rotp.core.impl.stands.goldexperience;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
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
        Level level = user.level();
        ActionTarget target = GoldExperienceCreateLifeformAbility.getClientLookTarget(level, user);
        if (!target.isEmpty(level)
                && !ActionTargetRange.isTargetWithinRange(ActionTargetRange.standPerformer(user), target, level)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (!isValidBoneMealTarget(level, target)) {
            return ConditionCheck.NEGATIVE;
        }
        return super.checkSpecificConditions(context);
    }

    @Override
    public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (action instanceof BoneMealAction boneMealAction) {
            ActionTarget target = GoldExperienceCreateLifeformAbility.getClientLookTarget(level, powerUser);
            boneMealAction.target = target;
            if (!target.isEmpty(level)) {
                action.standRotationTarget = target;
            }
        }
    }

    // 1.16 GoldExperienceBoneMeal.checkTarget on the target the client sent (TargetRequirement.ANY: none is refused)
    private static boolean isValidBoneMealTarget(Level level, ActionTarget target) {
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
    
    // 1.16 standPerform: StandEntity.aimWithThisOrUser(64, task target) keeps the pressed target while it is in the
    // Stand's reach, else takes the precision ray from the user's eyes (not under remote control), then the Stand's
    private static ActionTarget aimWithThisOrUser(Level level, LivingEntity user, StandEntity stand, ActionTarget pressed) {
        if (!pressed.isEmpty(level) && isInStandReach(level, stand, pressed)) {
            return pressed;
        }
        ActionTarget aim = ActionTarget.EMPTY;
        if (!stand.isManuallyControlled()) {
            aim = StandPrecisionRay.trace(user, stand, TARGET_RANGE, 0.0D, true, false);
        }
        if (aim.isEmpty(level)) {
            aim = StandPrecisionRay.trace(stand, stand, TARGET_RANGE, 0.0D, true, false);
        }
        return aim;
    }

    // 1.16 StandEntity.isTargetInReach: getDistanceToTarget, 0 for a target without bounds, against the Stand's reach
    private static boolean isInStandReach(Level level, StandEntity stand, ActionTarget target) {
        AABB bounds = null;
        if (target.getType() == ActionTarget.TargetType.ENTITY && target.getMainEntity() != null) {
            bounds = target.getMainEntity().getBoundingBox();
        }
        else if (target.getType() == ActionTarget.TargetType.BLOCK) {
            BlockPos pos = target.getBlockPos();
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            bounds = shape.isEmpty() ? null : shape.bounds().move(pos);
        }
        double distance = bounds != null ? ActionTargetRange.getDistance(stand, bounds) : 0.0D;
        return distance <= stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    private static boolean applyOriginalBonemealEffect(Level level, Player player, BlockPos pos, Direction face) {
        if (BoneMealItem.applyBonemeal(ItemStack.EMPTY, level, pos, player)) {
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

            target = target.resolveEntityId(level);
            if (performer instanceof StandEntity stand) {
                target = aimWithThisOrUser(level, user, stand, target);
            }
            if (!target.isEmpty(level)) {
                standRotationTarget = target;
            }
            switch (target.getType()) {
                case ENTITY -> {
                    if (target.getMainEntity() instanceof AgeableMob animal && animal.isBaby()) {
                        int age = animal.getAge();
                        animal.ageUp((int) ((-age / 20) * 0.2F), true);
                    }
                }
                // 1.16 standPerform: blocks only for a player user, as that player (HamonHealing.bonemealEffect)
                case BLOCK -> {
                    if (!(user instanceof Player player)) {
                        break;
                    }
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
                        applyOriginalBonemealEffect(level, player, pos, target.getFace());
                    }
                }
                default -> {}
            }

            // 1.16 StandEntityTask paid after the perform, whatever it grew, and ignored the result
            StandAbilityStamina.consume(ability, StandPower.get(user), STAMINA_COST, false);
        }
    }
}
