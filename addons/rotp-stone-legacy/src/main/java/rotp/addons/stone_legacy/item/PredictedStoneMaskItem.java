package rotp.addons.stone_legacy.item;

import rotp.core.block.StoneMaskBlock;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.item.StoneMaskItem;
import rotp.core.mechanics.BleedingEffect;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class PredictedStoneMaskItem extends StoneMaskItem {
    private final PillarmanMode predictedMode;

    public PredictedStoneMaskItem(
            Properties properties,
            StoneMaskBlock block,
            PillarmanMode predictedMode) {
        super(properties, block);
        this.predictedMode = predictedMode;
    }

    @Override
    public MaskActivationResult tryBloodActivation(
            LivingEntity wearer,
            ItemStack stack) {
        PlayerPower power =
                PlayerPower.getOptional(wearer).orElse(null);
        PillarmanData pillarman = power != null
                ? power.getCurTypeData(ModPlayerPowers.PILLAR_MAN)
                        .orElse(null)
                : null;
        if (pillarman == null) {
            if (wearer instanceof ServerPlayer serverPlayer) {
                ModCriteriaTriggers.triggerMaskSuicide(serverPlayer);
            }
            wearer.hurt(
                    DamageUtil.make(
                            wearer.level(),
                            ModDamageTypes.STONE_MASK),
                    wearer.getMaxHealth());
            return MaskActivationResult.ACTIVATED;
        }
        if (pillarman.getEvolutionStage() >= 3) {
            return MaskActivationResult.REJECTED;
        }

        pillarman.setEvolutionStage(3, wearer);
        pillarman.setMode(predictedMode, wearer);
        BleedingEffect.applyActivationEffects(wearer, stack);
        return MaskActivationResult.ACTIVATED;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int inventorySlot,
            boolean isSelected) {
        int textureTicks = getActivatedTicks(stack);
        if (textureTicks <= 0) {
            return;
        }

        int nextTicks = textureTicks - 1;
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    if (nextTicks > 0) {
                        tag.putByte(
                                NBT_ACTIVATION_KEY,
                                (byte) nextTicks);
                    }
                    else {
                        tag.remove(NBT_ACTIVATION_KEY);
                    }
                });
        if (nextTicks > 0) {
            return;
        }

        ItemStack normalMask =
                stack.transmuteCopy(ModItems.STONE_MASK.get());
        normalMask.setCount(1);
        if (entity instanceof LivingEntity living
                && living.getItemBySlot(EquipmentSlot.HEAD)
                        == stack) {
            living.setItemSlot(
                    EquipmentSlot.HEAD, ItemStack.EMPTY);
            entity.spawnAtLocation(normalMask);
        }
        else if (entity instanceof Player player) {
            player.getInventory().setItem(
                    inventorySlot, normalMask);
        }
        else {
            stack.shrink(1);
            entity.spawnAtLocation(normalMask);
        }
    }

    public PillarmanMode predictedMode() {
        return predictedMode;
    }
}
