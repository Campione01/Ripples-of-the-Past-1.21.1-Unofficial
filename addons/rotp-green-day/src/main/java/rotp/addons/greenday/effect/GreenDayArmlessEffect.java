package rotp.addons.greenday.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class GreenDayArmlessEffect extends MobEffect {
    public static final int COLOR = 0xFF0000;
    public static final int PICKUP_DELAY_TICKS = 60;

    private final HumanoidArm missingArm;

    public GreenDayArmlessEffect(boolean right) {
        super(MobEffectCategory.HARMFUL, COLOR);
        missingArm = right
                ? HumanoidArm.RIGHT
                : HumanoidArm.LEFT;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)
                || entity.getMainArm().getOpposite()
                        != missingArm) {
            return true;
        }
        ItemStack offhand =
                entity.getItemInHand(InteractionHand.OFF_HAND);
        if (offhand.isEmpty()) {
            return true;
        }
        ItemEntity dropped = new ItemEntity(
                level,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                offhand.copy());
        dropped.setPickUpDelay(PICKUP_DELAY_TICKS);
        entity.setItemInHand(
                InteractionHand.OFF_HAND,
                ItemStack.EMPTY);
        level.addFreshEntity(dropped);
        return true;
    }
}
