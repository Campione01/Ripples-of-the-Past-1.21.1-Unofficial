package rotp.addons.whitesnake.disc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.memory.MemoryDiscService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class MemoryDiscItem
        extends Item
        implements MemoryDiscPayloadItem {
    public MemoryDiscItem(Properties properties) {
        super(properties);
    }

    public static ItemStack withMemoryOf(
            LivingEntity target, UUID extractionId) {
        LivingEntity targetUser =
                MemoryDiscService.normalizeTarget(target);
        if (targetUser == null || extractionId == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack =
                new ItemStack(WhitesnakeAddon.MEMORY_DISC_ITEM.get());
        stack.set(
                WhitesnakeAddon.MEMORY_OWNER.get(),
                new MemoryDiscData(
                        targetUser.getUUID(),
                        extractionId,
                        targetUser.getDisplayName().getString()));
        return stack;
    }

    public static Optional<MemoryDiscData> getData(ItemStack stack) {
        if (stack == null
                || stack.isEmpty()
                || !(stack.getItem() instanceof MemoryDiscItem)) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                stack.get(WhitesnakeAddon.MEMORY_OWNER.get()));
    }

    public static Optional<HeldDisc> findHeld(
            LivingEntity holder) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = holder.getItemInHand(hand);
            Optional<MemoryDiscData> data = getData(stack);
            if (data.isPresent()) {
                return Optional.of(
                        new HeldDisc(hand, stack, data.get()));
            }
        }
        return Optional.empty();
    }

    public static Optional<HeldDisc> findHeldForTarget(
            LivingEntity holder, UUID targetId) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = holder.getItemInHand(hand);
            Optional<MemoryDiscData> data = getData(stack);
            if (data.isPresent()
                    && data.get().targetId().equals(targetId)) {
                return Optional.of(
                        new HeldDisc(hand, stack, data.get()));
            }
        }
        return Optional.empty();
    }

    public static boolean hasDiscForTarget(
            LivingEntity holder, UUID targetId) {
        if (holder instanceof Player player) {
            for (int slot = 0;
                    slot < player.getInventory().getContainerSize();
                    slot++) {
                Optional<MemoryDiscData> data = getData(
                        player.getInventory().getItem(slot));
                if (data.isPresent()
                        && data.get().targetId().equals(targetId)) {
                    return true;
                }
            }
            return false;
        }
        return matchesTarget(holder.getMainHandItem(), targetId)
                || matchesTarget(holder.getOffhandItem(), targetId);
    }

    private static boolean matchesTarget(
            ItemStack stack, UUID targetId) {
        return getData(stack)
                .map(MemoryDiscData::targetId)
                .filter(targetId::equals)
                .isPresent();
    }

    @Override
    public Optional<CompoundTag> copyMemoryPayload(
            ItemStack stack) {
        return getData(stack).map(MemoryDiscData::toPayloadTag);
    }

    @Override
    public boolean restoreMemory(
            ItemStack stack,
            LivingEntity target,
            CompoundTag payload) {
        return MemoryDiscService.restore(
                stack, target, payload).applied();
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        getData(stack).ifPresentOrElse(
                data -> tooltip.add(Component.translatable(
                                "rotp_whitesnake.memory_disc."
                                        + "current_player",
                                Component.literal(data.targetName())
                                        .withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.DARK_PURPLE)),
                () -> tooltip.add(Component.translatable(
                                "rotp_whitesnake.memory_disc."
                                        + "no_current_player")
                        .withStyle(ChatFormatting.DARK_PURPLE)));
    }

    public record HeldDisc(
            InteractionHand hand,
            ItemStack snapshot,
            MemoryDiscData data) {
        public HeldDisc {
            snapshot = snapshot.copyWithCount(1);
        }

        @Override
        public ItemStack snapshot() {
            return snapshot.copy();
        }

        public boolean isStillHeldBy(LivingEntity holder) {
            return ItemStack.isSameItemSameComponents(
                    holder.getItemInHand(hand), snapshot);
        }

        public boolean consumeFrom(LivingEntity holder) {
            if (!isStillHeldBy(holder)) {
                return false;
            }
            holder.getItemInHand(hand).shrink(1);
            return true;
        }
    }
}
