package rotp.core.impl.stands.goldexperience;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.subsystems.itemtracking.ItemTracker;
import rotp.core.subsystems.itemtracking.ItemTracking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class GEItemMarkEffect extends StandEffectInstance {
    @Nullable private UUID itemTrackerId;
    @Nullable private ItemTracker itemTracker;

    public GEItemMarkEffect(EntityCustomEffectType<?> effectType) {
        super(effectType);
        needsTarget = false;
    }

    public GEItemMarkEffect withItemTracker(ItemTracker itemTracker) {
        this.itemTracker = itemTracker;
        this.itemTrackerId = itemTracker != null ? itemTracker.trackerId : null;
        return this;
    }

    @Nullable
    public ItemTracker getItemTracker(boolean update) {
        if (update && itemTrackerId != null && level != null) {
            itemTracker = ItemTracking.getItemTracker(itemTrackerId, level);
        }
        return itemTracker;
    }

    @Nullable
    public UUID getItemTrackerId() {
        return itemTrackerId;
    }

    @Override
    protected void start() {}

    @Override
    protected void tick() {
        if (itemTrackerId == null) {
            if (!level.isClientSide()) {
                remove();
            }
            return;
        }

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            ItemTracker tracker = getItemTracker(true);
            if (tracker == null || tracker.getItem() == null || tracker.getItem().isEmpty()
                    || !tracker.checkItemIsThere(serverLevel)) {
                remove();
            }
        }
    }

    @Override
    protected void stop() {
        if (!level.isClientSide() && itemTrackerId != null && level instanceof ServerLevel serverLevel) {
            ItemTracking.getItemTracking(level).stopTracking(itemTrackerId, serverLevel);
            itemTracker = null;
        }
    }

    @Override
    public void writeAdditionalPacketData(FriendlyByteBuf buf, boolean sendingToUser) {
        super.writeAdditionalPacketData(buf, sendingToUser);
        if (sendingToUser) {
            buf.writeBoolean(itemTrackerId != null);
            if (itemTrackerId != null) {
                buf.writeUUID(itemTrackerId);
            }
        }
    }

    @Override
    public void readAdditionalPacketData(FriendlyByteBuf buf, boolean clientIsUser) {
        super.readAdditionalPacketData(buf, clientIsUser);
        if (clientIsUser) {
            itemTrackerId = buf.readBoolean() ? buf.readUUID() : null;
            itemTracker = null;
        }
    }

    @Override
    protected void writeAdditionalSaveData(CompoundTag nbt) {
        super.writeAdditionalSaveData(nbt);
        if (itemTrackerId != null) {
            nbt.putUUID("ItemTracker", itemTrackerId);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.hasUUID("ItemTracker")) {
            itemTrackerId = nbt.getUUID("ItemTracker");
        }
    }

    public static boolean isItemMarked(ItemStack item, LivingEntity player) {
        ItemTracker tracker = ItemTracking.getItemTracker(item, player.level());
        if (tracker == null || tracker.getItem() == null || tracker.getItem().isEmpty()) {
            return false;
        }
        StandPower power = StandPower.get(player);
        return power != null && power.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_ITEM_MARK.get())
                .anyMatch(effect -> tracker.trackerId.equals(effect.getItemTrackerId()));
    }
}
