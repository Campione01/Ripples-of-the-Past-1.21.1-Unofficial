package rotp.addons.heavensdoor.item;

import java.util.List;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.state.BookStateService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class PageItem extends Item {
    public PageItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(
            LivingEntity target,
            LivingEntity creator,
            long generation) {
        ItemStack stack = new ItemStack(
                HeavensDoorAddon.PAGE_ITEM.get());
        stack.set(
                HeavensDoorAddon.PAGE_DATA.get(),
                new PageData(
                        target.getUUID(),
                        creator.getUUID(),
                        HeavensDoorAddon.bookState(target).pageDimension() != null
                                ? HeavensDoorAddon.bookState(target).pageDimension()
                                : target.level().dimension().location(),
                        generation,
                        target.getDisplayName().getString()));
        return stack;
    }

    public static PageData data(ItemStack stack) {
        return stack != null
                && stack.is(HeavensDoorAddon.PAGE_ITEM.get())
                ? stack.get(HeavensDoorAddon.PAGE_DATA.get())
                : null;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity holder,
            int slot,
            boolean selected) {
        if (!level.isClientSide()) {
            validateLoadedTarget(stack, level);
        }
    }

    @Override
    public boolean onEntityItemUpdate(
            ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide()) {
            validateLoadedTarget(stack, entity.level());
        }
        return false;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        PageData data = data(stack);
        if (data == null) {
            tooltip.add(Component.translatable(
                            "rotp_hd.page.no_current_player")
                    .withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        tooltip.add(Component.translatable(
                        "rotp_hd.page.current_player",
                        data.targetName())
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static void validateLoadedTarget(
            ItemStack stack, Level level) {
        PageData data = data(stack);
        if (data == null) {
            stack.setCount(0);
            return;
        }
        ServerLevel current =
                level instanceof ServerLevel serverLevel
                        ? serverLevel
                        : null;
        if (current == null || current.getServer() == null) {
            return;
        }
        LivingEntity target = BookStateService.findLoadedEntity(
                current.getServer(), data.targetUuid());
        // An absent UUID is not evidence of death; never force-load its chunk.
        if (target != null) {
            if (!BookStateService.pageMatches(target, data)) {
                stack.setCount(0);
            }
        }
    }
}
