package rotp.addons.thelock.state;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class LockState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 3;

    private static final String VERSION_TAG = "Version";
    private static final String ACTIVE_TAG = "Active";
    private static final String OWNER_TAG = "Owner";
    private static final String CONTROLLER_TAG = "Controller";
    private static final String GUILT_TAG = "Guilt";
    private static final String CHAT_CHARGED_TAG = "ChatCharged";

    private boolean active;
    @Nullable
    private UUID ownerUuid;
    @Nullable
    private UUID controllerUuid;
    private float guilt;
    private boolean chatCharged;

    public boolean active() {
        return active && ownerUuid != null;
    }

    @Nullable
    public UUID ownerUuid() {
        return ownerUuid;
    }

    @Nullable
    public UUID controllerUuid() {
        return controllerUuid;
    }

    public float guilt() {
        return guilt;
    }

    public boolean chatCharged() {
        return chatCharged;
    }

    public boolean isOwnedBy(UUID owner) {
        return owner != null && owner.equals(ownerUuid) && active();
    }

    public boolean lock(UUID owner, @Nullable UUID controller) {
        if (owner == null) {
            return clear();
        }
        boolean changed = !active
                || !owner.equals(ownerUuid)
                || !Objects.equals(controller, controllerUuid)
                || guilt != 0.0F
                || chatCharged;
        active = true;
        ownerUuid = owner;
        controllerUuid = controller;
        guilt = 0.0F;
        chatCharged = false;
        return changed;
    }

    public boolean addGuilt(float amount) {
        if (!active() || !Float.isFinite(amount) || amount <= 0.0F) {
            return false;
        }
        float next = LockRules.clampGuilt(guilt + amount);
        if (Float.compare(next, guilt) == 0) {
            return false;
        }
        guilt = next;
        return true;
    }

    public boolean markChatCharged() {
        if (!active() || chatCharged) {
            return false;
        }
        chatCharged = true;
        return true;
    }

    public boolean clear() {
        boolean changed = active
                || ownerUuid != null
                || controllerUuid != null
                || guilt != 0.0F
                || chatCharged;
        active = false;
        ownerUuid = null;
        controllerUuid = null;
        guilt = 0.0F;
        chatCharged = false;
        return changed;
    }

    public void applyClientSnapshot(
            boolean newActive,
            @Nullable UUID newOwner,
            @Nullable UUID newController,
            float newGuilt,
            boolean newChatCharged) {
        if (!newActive || newOwner == null) {
            clear();
            return;
        }
        active = true;
        ownerUuid = newOwner;
        controllerUuid = newController;
        guilt = LockRules.clampGuilt(newGuilt);
        chatCharged = newChatCharged;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (!active()) {
            return tag;
        }
        tag.putInt(VERSION_TAG, SERIALIZATION_VERSION);
        tag.putBoolean(ACTIVE_TAG, true);
        tag.putUUID(OWNER_TAG, ownerUuid);
        if (controllerUuid != null) {
            tag.putUUID(CONTROLLER_TAG, controllerUuid);
        }
        tag.putFloat(GUILT_TAG, guilt);
        tag.putBoolean(CHAT_CHARGED_TAG, chatCharged);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        int version = tag.getInt(VERSION_TAG);
        if ((version != 2 && version != SERIALIZATION_VERSION)
                || !tag.getBoolean(ACTIVE_TAG)
                || !tag.hasUUID(OWNER_TAG)
                || (version == 2 && !tag.hasUUID(CONTROLLER_TAG))) {
            return;
        }
        active = true;
        ownerUuid = tag.getUUID(OWNER_TAG);
        controllerUuid = tag.hasUUID(CONTROLLER_TAG)
                ? tag.getUUID(CONTROLLER_TAG) : null;
        guilt = LockRules.clampGuilt(tag.getFloat(GUILT_TAG));
        chatCharged = tag.getBoolean(CHAT_CHARGED_TAG);
    }
}
