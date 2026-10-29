package rotp.addons.cream.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import rotp.addons.cream.CreamAddon;
import rotp.addons.cream.CreamConfig;
import rotp.addons.cream.entity.CreamEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class CreamEatAbility extends StandEntityAbility {
    public static final float STAMINA = 80.0F;

    private final boolean consumeStack;

    public CreamEatAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            boolean consumeStack) {
        super(abilityType, abilityId, EatAction::new);
        this.consumeStack = consumeStack;
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        resolveLevelToUnlock(1);
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        // 1.16 CreamEatItem(Stack) set this timer directly with setCooldownTimer, only for a user not in Creative, so
        // Resolve never shortened it and no technical part was left for Creative.
        cooldown(0, consumeStack
                ? CreamConfig.EAT_ITEM_STACK_COOLDOWN.get()
                : CreamConfig.EAT_ITEM_COOLDOWN.get(), 1.0F);
        return super.getCooldown(context, ticksHeld);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        LivingEntity user = context.getUser();
        if (CreamEntity.isInVoidState(user)) {
            return ConditionCheck.NEGATIVE;
        }
        if (user.getOffhandItem().isEmpty()) {
            return ConditionCheck.createNegative("no_item_in_offhand_cream");
        }
        // 1.16 had the 80 cost but no stamina gate.
        return super.checkSpecificConditions(context);
    }

    public static final class EatAction extends EntityActionInstance {
        private boolean consumed;

        public EatAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 standOffsetFromUser(0.3, 1.0, -0.7) is (left, forward, y):
        // full mode only, turned by the view yaw
        @Override
        public void onActionSet(@Nullable EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            setStandOffset(new Vec3(0.3D, -0.7D, 1.0D),
                    StandOffsetFromUser.Rotations.HEAD, false);
        }

        @Override
        public void onActionCleared(@Nullable EntityActionInstance newAction) {
            LivingEntity performer = getPerformer();
            if (performer == null
                    || performer.level().isClientSide()
                    || !(ability instanceof CreamEatAbility eat)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null ? StandPower.get(user) : null;
            if (power != null) {
                eat.setCooldownOnUse(power);
            }
        }

        @Override
        public void actionPerformStart() {
            if (consumed
                    || level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)
                    || !(ability instanceof CreamEatAbility eat)) {
                return;
            }
            consumed = true;
            LivingEntity user = getPowerUser();
            StandPower power = user != null
                    ? StandPower.get(user)
                    : null;
            if (user == null || power == null) {
                startRecovery();
                return;
            }
            // 1.16 drained the cost (to 0 when short) and still ate.
            power.consumeStamina(STAMINA);

            ItemStack offhand = user.getOffhandItem();
            if (offhand.isEmpty()) {
                startRecovery();
                return;
            }
            if (eat.consumeStack) {
                user.setItemInHand(
                        InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            else {
                offhand.shrink(1);
            }
            StandUtil.playStandEntitySound(
                    stand, CreamAddon.CREAM_VOID, 1.0F, 1.0F);
        }
    }
}
