package rotp.addons.memory_discs.item;

import java.util.List;

import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.util.functions.ItemUtil;
import rotp.addons.memory_discs.service.PlayerPowerTransitionService;
import rotp.addons.memory_discs.service.PlayerPowerTransitionService.Result;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class MemoryDiscItem extends Item {
    public MemoryDiscItem(Properties properties) {
        super(properties);
    }

    public static ItemStack withPower(
            ItemStack stack, PlayerPowerType<?> powerType) {
        return PowerDiscData.withPower(stack, powerType);
    }

    public static PlayerPowerType<?> getPowerFromStack(
            ItemStack stack) {
        return PowerDiscData.getPowerType(stack).orElse(null);
    }

    public static boolean hasPower(ItemStack stack) {
        return PowerDiscData.getPowerType(stack).isPresent();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(
                    stack, true);
        }

        Result result =
                PlayerPowerTransitionService.applyDisc(
                        player, stack);
        if (!result.applied()) {
            if (result.status()
                    == PlayerPowerTransitionService.Status
                            .NO_ACTIVE_POWER
                    && !PowerDiscData.hasStoredPowerId(stack)) {
                return InteractionResultHolder.success(stack);
            }
            showRejection(player, result);
            return InteractionResultHolder.fail(stack);
        }

        if (!player.getAbilities().instabuild) {
            if (result.previousType() != null) {
                dropPreviousDisc(
                        player, result.previousType());
            }
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return PowerDiscData.getPowerType(stack)
                .<Component>map(type -> Component.translatable(
                        "item.rotp_mds.memory_disc",
                        type.name.get()))
                .orElseGet(() -> PowerDiscData.hasStoredPowerId(stack)
                        ? Component.translatable(
                                "item.rotp_mds.invalid_memory_disc")
                        : Component.translatable(
                                "item.rotp_mds.empty_memory_disc"));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        PowerDiscData.getPowerType(stack).ifPresentOrElse(
                type -> tooltip.add(
                        type.name.get().copy()
                                .withStyle(ChatFormatting.GRAY)),
                () -> tooltip.add(Component.translatable(
                                PowerDiscData.hasStoredPowerId(stack)
                                        ? "item.rotp_mds."
                                                + "invalid_memory_disc."
                                                + "tooltip"
                                        : "item.rotp_mds."
                                                + "empty_memory_disc."
                                                + "tooltip")
                        .withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable(
                        "item.jojo.creative_only_tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void dropPreviousDisc(
            Player player, PlayerPowerType<?> previousType) {
        ItemEntity dropped = ItemUtil.dropAt(
                player,
                PowerDiscData.newFilledDisc(previousType));
        if (dropped != null) {
            dropped.setPickUpDelay(5);
            player.level().addFreshEntity(dropped);
        }
    }

    public static void showRejection(
            Player player, Result result) {
        if (!result.status().translationKey().isEmpty()) {
            player.displayClientMessage(
                    Component.translatable(
                            result.status().translationKey()),
                    true);
        }
    }
}
