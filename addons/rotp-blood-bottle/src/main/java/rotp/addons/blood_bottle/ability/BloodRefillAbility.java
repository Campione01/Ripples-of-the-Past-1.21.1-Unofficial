package rotp.addons.blood_bottle.ability;

import rotp.addons.blood_bottle.BloodBottleAddon;
import rotp.addons.blood_bottle.BloodBottleConfig;
import rotp.addons.blood_bottle.BloodBottleEconomy;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.abilities.VampirismActionAbility;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class BloodRefillAbility
        extends VampirismActionAbility {
    private static final int HOLD_TO_FIRE_TICKS = 5;

    public BloodRefillAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                3,
                0.0F,
                BloodRefillInstance::new);
        setDefaultPhaseLength(
                ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public String getResourceNamespace() {
        return BloodBottleAddon.MOD_ID;
    }

    // No requiresVampireFullPower() override: in 1.16
    // VampirismData.isActionUnlocked only opened Blood Drain, Blood Gift and
    // (for former Hamon users) Hamon Suicide to a vampire below full power,
    // so the refill stays behind the core's full-power gate.

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return HOLD_TO_FIRE_TICKS;
    }

    // 1.16 holdToFire(5, false) held the refill until it fired, so a
    // release from any bind still cancels the windup.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.WINDUP
                || super.isActionHeld(action);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }

        LivingEntity user = context.getUser();
        if (!(user instanceof Player player)
                || !player.getOffhandItem().is(
                        Items.GLASS_BOTTLE)) {
            return ConditionCheck.createNegative("no_volume");
        }
        VampirismData data = getVampirismData(context);
        if (data == null
                || !data.hasBlood(
                        player, refillCost(data, player))) {
            return ConditionCheck.createNegative(
                    "no_energy_vampirism");
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 PowerBaseImpl.tickHeldAction reran checkRequirements on every
    // tick of the hold: an emptied off hand (no_volume) or blood short of the
    // refill (no_energy_vampirism) ends it with the message.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkSpecificConditions(context);
    }

    private static float refillCost(
            VampirismData data, LivingEntity user) {
        return BloodBottleEconomy.refillCost(
                data.getMaxBlood(user),
                BloodBottleConfig
                        .FILL_BOTTLE_MULTIPLIER.get());
    }

    public static final class BloodRefillInstance
            extends EntityActionInstance {
        public BloodRefillInstance(EntityActionType ability) {
            super(ability);
            userWalkSpeed = 0.75F;
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                forceStop();
            }
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (level.isClientSide()) {
                return;
            }
            LivingEntity living = getPowerUser();
            if (!(living instanceof Player player)
                    || !player.getOffhandItem().is(
                            Items.GLASS_BOTTLE)) {
                return;
            }
            // 1.16 stopHeldAction(true) checked once more as the hold
            // fired: a failed check fills nothing, silently.
            if (!(ability instanceof BloodRefillAbility refill)) {
                return;
            }
            Power<?> context = refill.getUserPower(player);
            if (context == null
                    || !refill.checkHeldActionConditions(this, context)
                            .isPositive()) {
                return;
            }

            VampirismData data =
                    getVampirismData(player);
            if (data == null) {
                return;
            }
            float cost = refillCost(data, player);
            boolean creative =
                    player.getAbilities().instabuild;
            if (!creative
                    && !data.consumeBlood(player, cost)) {
                return;
            }

            if (!creative) {
                player.getOffhandItem().shrink(1);
            }
            giveFilledBottle(player, creative);
            level.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.BOTTLE_FILL,
                    SoundSource.PLAYERS,
                    0.75F,
                    1.0F);
        }

        private static void giveFilledBottle(
                Player player, boolean creative) {
            ItemStack bottle =
                    new ItemStack(
                            BloodBottleAddon.BLOOD_BOTTLE.get());
            if (player.getMainHandItem().isEmpty()
                    && !player.getInventory().contains(bottle)) {
                player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
                return;
            }
            if (!creative && !player.addItem(bottle)) {
                player.spawnAtLocation(bottle);
            }
        }
    }
}
