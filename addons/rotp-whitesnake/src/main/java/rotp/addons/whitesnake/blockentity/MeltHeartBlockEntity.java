package rotp.addons.whitesnake.blockentity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MeltHeartBlockEntity extends BlockEntity {
    private static final String OWNER_ID_TAG = "OwnerId";
    private static final String ABILITY_NAME = "melt_your_heart";

    @Nullable
    private UUID ownerId;

    public MeltHeartBlockEntity(
            BlockPos position, BlockState state) {
        super(
                WhitesnakeAddon.MELT_HEART_BLOCK_ENTITY.get(),
                position,
                state);
    }

    public void initialize(UUID ownerId) {
        this.ownerId = ownerId;
        setChanged();
    }

    public static void serverTick(
            Level level,
            BlockPos position,
            BlockState state,
            MeltHeartBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)
                || blockEntity.hasValidOwner(serverLevel)) {
            return;
        }
        serverLevel.removeBlock(position, false);
    }

    private boolean hasValidOwner(ServerLevel level) {
        if (ownerId == null) {
            return false;
        }
        LivingEntity owner = findOwner(level, ownerId);
        return owner != null
                && owner.level() == level
                && WhitesnakeAddon.isActiveWhitesnakeUser(
                        owner, ABILITY_NAME);
    }

    @Nullable
    private static LivingEntity findOwner(
            ServerLevel level, UUID ownerId) {
        Entity entity = level.getEntity(ownerId);
        if (entity instanceof LivingEntity living) {
            return living;
        }
        ServerPlayer player =
                level.getServer().getPlayerList().getPlayer(ownerId);
        return player;
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerId != null) {
            tag.putUUID(OWNER_ID_TAG, ownerId);
        }
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerId = tag.hasUUID(OWNER_ID_TAG)
                ? tag.getUUID(OWNER_ID_TAG)
                : null;
    }
}
