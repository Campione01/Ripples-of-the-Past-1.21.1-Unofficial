package rotp.addons.whitesnake.disc;

import java.util.Optional;

import rotp.core.mechanics.standdisc.StandDiscItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record DiscPayload(
        Type type,
        InteractionHand hand,
        ItemStack carrier,
        CompoundTag memoryPayload) {
    public DiscPayload {
        carrier = carrier.copyWithCount(1);
        memoryPayload = memoryPayload.copy();
    }

    @Override
    public ItemStack carrier() {
        return carrier.copy();
    }

    @Override
    public CompoundTag memoryPayload() {
        return memoryPayload.copy();
    }

    public boolean isStillHeldBy(LivingEntity user) {
        return ItemStack.isSameItemSameComponents(
                user.getItemInHand(hand), carrier);
    }

    public boolean consumeFrom(LivingEntity user) {
        if (!isStillHeldBy(user)) {
            return false;
        }
        if (type == Type.MEMORY_DISC
                || isAuthoritativeStandDisc()
                || !(user instanceof Player player)
                || !player.getAbilities().instabuild) {
            user.getItemInHand(hand).shrink(1);
        }
        return true;
    }

    public boolean isAuthoritativeStandDisc() {
        return type == Type.STAND_DISC
                && StandDiscAuthorityData.get(carrier).isPresent();
    }

    public boolean requiresReturnOnFailure() {
        return type == Type.MEMORY_DISC
                || isAuthoritativeStandDisc();
    }

    public static Optional<DiscPayload> capture(LivingEntity user) {
        Optional<DiscPayload> main = capture(
                InteractionHand.MAIN_HAND,
                user.getItemInHand(InteractionHand.MAIN_HAND));
        return main.isPresent()
                ? main
                : capture(
                        InteractionHand.OFF_HAND,
                        user.getItemInHand(InteractionHand.OFF_HAND));
    }

    public static Optional<DiscPayload> captureStandDisc(LivingEntity user) {
        return capture(user).filter(payload ->
                payload.type == Type.STAND_DISC);
    }

    private static Optional<DiscPayload> capture(
            InteractionHand hand, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        ItemStack carrier = stack.copyWithCount(1);
        if (StandDiscItem.getStandInstance(carrier) != null) {
            return Optional.of(new DiscPayload(
                    Type.STAND_DISC,
                    hand,
                    carrier,
                    new CompoundTag()));
        }

        if (carrier.getItem() instanceof MemoryDiscPayloadItem memoryDisc) {
            return memoryDisc.copyMemoryPayload(carrier)
                    .map(CompoundTag::copy)
                    .map(payload -> new DiscPayload(
                            Type.MEMORY_DISC,
                            hand,
                            carrier,
                            payload));
        }
        return Optional.empty();
    }

    public enum Type {
        NONE(""),
        STAND_DISC("stand_disc"),
        MEMORY_DISC("memory_disc");

        private final String serializedName;

        Type(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        public static Type fromSerializedName(String name) {
            for (Type type : values()) {
                if (type.serializedName.equals(name)) {
                    return type;
                }
            }
            return NONE;
        }
    }
}
