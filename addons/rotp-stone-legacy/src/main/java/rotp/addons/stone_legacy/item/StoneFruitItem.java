package rotp.addons.stone_legacy.item;

import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.api.item.ItemUseParticleProvider;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.stone_legacy.StoneLegacyAddon;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class StoneFruitItem extends Item
        implements ItemUseParticleProvider {
    public static final String STONEFIED_TAG = "Stonefied";
    public static final int MAX_STAGE = 630;

    public StoneFruitItem(Properties properties) {
        super(properties);
    }

    public static int stage(ItemStack stack) {
        return Math.clamp(
                stack.getOrDefault(
                                DataComponents.CUSTOM_DATA,
                                CustomData.EMPTY)
                        .copyTag()
                        .getInt(STONEFIED_TAG),
                0,
                MAX_STAGE);
    }

    public static void setStage(ItemStack stack, int stage) {
        int clamped = Math.clamp(stage, 0, MAX_STAGE);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putInt(STONEFIED_TAG, clamped));
    }

    @Override
    public ItemStack getUseParticleStack(
            LivingEntity user,
            ItemStack usedStack) {
        return stage(usedStack) == MAX_STAGE
                ? new ItemStack(Items.BONE)
                : usedStack;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slot,
            boolean selected) {
        if (level.isClientSide()
                || !(entity instanceof LivingEntity living)) {
            return;
        }
        boolean held = selected
                || living.getOffhandItem() == stack;
        int oldStage = stage(stack);
        int newStage = oldStage;
        if (held) {
            float light = level.dimensionType().hasSkyLight()
                            && !level.dimensionType().hasCeiling()
                    ? living.getLightLevelDependentMagicValue()
                    : 0.0F;
            if (light > 0.0F) {
                float lightDelay = 30.0F / light / 6.0F;
                if (lightDelay > 0.0F) {
                    newStage = Math.min(
                            oldStage
                                    + (int) (30.0F
                                            / lightDelay
                                            * 6.0F),
                            MAX_STAGE);
                }
            }
        }
        else if (oldStage > 0) {
            newStage = Math.max(0, oldStage - 30);
        }
        if (newStage != oldStage) {
            setStage(stack, newStage);
        }
    }

    @Override
    public boolean onEntityItemUpdate(
            ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide()) {
            int oldStage = stage(stack);
            if (oldStage > 0) {
                setStage(stack, oldStage - 1);
            }
        }
        return false;
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack,
            Level level,
            LivingEntity living) {
        if (level.isClientSide()
                || !(living instanceof Player player)) {
            return stack;
        }

        boolean creative = player.getAbilities().instabuild;
        var bleeding = living.getEffect(
                ModStatusEffects.BLEEDING);
        if (!creative
                && bleeding != null
                && living.getHealth()
                        <= (living.getMaxHealth()
                                        + bleeding.getAmplifier() * 2.0F)
                                / 2.0F) {
            living.hurt(
                    DamageUtil.make(
                            level, StoneLegacyAddon.ASPHYXIA),
                    living.getHealth());
            return stack;
        }

        if (!creative) {
            living.hurt(
                    DamageUtil.make(
                            level, StoneLegacyAddon.ASPHYXIA),
                    living.getMaxHealth() / 2.0F);
        }

        PlayerPower power =
                PlayerPower.getOptional(player).orElse(null);
        if (power != null) {
            StoneLegacyAddon.state(player).setHamonOrigin(
                    player,
                    power.getPowerType()
                            == ModPlayerPowers.HAMON.get());
            power.setPowerType(
                    ModPlayerPowers.PILLAR_MAN.get());
        }
        return super.finishUsingItem(stack, level, living);
    }
}
