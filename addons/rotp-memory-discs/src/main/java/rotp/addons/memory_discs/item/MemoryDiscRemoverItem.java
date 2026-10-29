package rotp.addons.memory_discs.item;

import java.util.List;

import rotp.core.util.functions.ItemUtil;
import rotp.addons.memory_discs.service.PlayerPowerTransitionService;
import rotp.addons.memory_discs.service.PlayerPowerTransitionService.Result;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class MemoryDiscRemoverItem extends Item {
    private final Mode mode;
    private final boolean oneTimeUse;

    public MemoryDiscRemoverItem(
            Properties properties,
            Mode mode,
            boolean oneTimeUse) {
        super(properties);
        this.mode = mode;
        this.oneTimeUse = oneTimeUse;
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
                PlayerPowerTransitionService.clearPower(player);
        if (!result.applied()) {
            MemoryDiscItem.showRejection(player, result);
            return InteractionResultHolder.fail(stack);
        }

        if (mode == Mode.EJECT
                && result.previousType() != null) {
            ItemUtil.giveItemTo(
                    player,
                    PowerDiscData.newFilledDisc(
                            result.previousType()),
                    true);
        }
        if (oneTimeUse
                && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.jojo.creative_only_tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public Mode mode() {
        return mode;
    }

    public boolean oneTimeUse() {
        return oneTimeUse;
    }

    public enum Mode {
        REMOVE,
        EJECT
    }
}
