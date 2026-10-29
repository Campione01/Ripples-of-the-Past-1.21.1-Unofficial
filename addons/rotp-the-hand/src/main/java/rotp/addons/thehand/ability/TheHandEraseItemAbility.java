package rotp.addons.thehand.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.thehand.TheHandAddon;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class TheHandEraseItemAbility extends StandEntityAbility {
    public static final float STAMINA_PER_TICK = 1.0F;
    public static final int BASE_ERASE_COUNT = 2;
    public static final int UNLOCKED_ERASE_COUNT = 4;

    public TheHandEraseItemAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, EraseItemAction::new);
        // 1.16 holdType() with no standRecoveryTicks: no recovery, so a
        // pressed action replaced the hold at once (isCancelable)
        setButtonHoldPhase(ActionPhase.PERFORM);
        // 1.16 InitStands.java:133 kept the builder's FULL: the whole Stand
        // comes out, so its offset shows
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        ConditionCheck item = checkItemToErase(context.getUser());
        if (!item.isPositive()) {
            return item;
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 tickHeldAction re-ran checkConditions: the hold stops with the
    // message once the offhand stack is gone. Stamina stays a tick cost.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        return checkItemToErase(context.getUser());
    }

    // What the erase needs when pressed and on every held tick.
    private static ConditionCheck checkItemToErase(LivingEntity user) {
        if (!(user instanceof Player)) {
            return ConditionCheck.NEGATIVE;
        }
        ItemStack stack = user.getOffhandItem();
        if (stack.isEmpty()) {
            return ConditionCheck.createNegative("item_offhand");
        }
        if (isProtected(stack)) {
            return ConditionCheck.createNegative(
                    "not_possible_erase_article");
        }
        return ConditionCheck.POSITIVE;
    }

    private static boolean isProtected(ItemStack stack) {
        return stack.is(Blocks.BEDROCK.asItem())
                || stack.is(Blocks.BARRIER.asItem());
    }

    public static final class EraseItemAction
            extends EntityActionInstance {
        public EraseItemAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (getPerformer() instanceof StandEntity stand) {
                // 1.16 standOffsetFromUser(0.667, 0.2, 0.0) is (left, forward,
                // y); TheHandEraseItem is a plain StandEntityAction, so the
                // view yaw turns it
                setStandOffset(
                        new Vec3(0.667D, 0.0D, 0.2D),
                        StandOffsetFromUser.Rotations.HEAD,
                        false);
                if (!level().isClientSide()) {
                    StandUtil.playStandEntitySound(
                            stand,
                            TheHandAddon
                                    .THE_HAND_ERASURE_BARRAGE_SOUND,
                            1.0F,
                            1.0F);
                }
            }
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (!(user instanceof Player player)) {
                startRecovery();
                return;
            }
            ItemStack stack = player.getItemInHand(
                    InteractionHand.OFF_HAND);
            if (stack.isEmpty() || isProtected(stack)) {
                startRecovery();
                return;
            }
            StandPower standPower = StandPower.get(player);
            int count = standPower != null
                    && standPower.isAbilityUnlocked(
                            TheHandAddon.ERASURE_BARRAGE_NAME)
                    ? UNLOCKED_ERASE_COUNT
                    : BASE_ERASE_COUNT;
            // An emptied stack ends the hold on the next tick's check,
            // with item_offhand, as in 1.16.
            stack.shrink(Math.min(stack.getCount(), count));
            // 1.16 StandEntityTask: the tick cost follows the erase and an
            // empty bar does not stop the hold.
            StandAbilityStamina.consume(
                    ability, standPower, STAMINA_PER_TICK, true);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() != ActionPhase.RECOVERY) {
                startRecovery();
            }
        }

        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }
    }
}
