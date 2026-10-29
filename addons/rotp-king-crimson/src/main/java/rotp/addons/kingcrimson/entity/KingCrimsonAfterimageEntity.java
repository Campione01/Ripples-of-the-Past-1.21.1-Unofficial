package rotp.addons.kingcrimson.entity;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public final class KingCrimsonAfterimageEntity
        extends Entity implements IEntityWithComplexSpawn {
    /** The colour of 1.16's KC_RedGlow team (TextFormatting.RED), which coloured its afterimage outline. */
    private static final int TIME_ERASE_OUTLINE_COLOR =
            ChatFormatting.RED.getColor();

    @Nullable
    private LivingEntity originEntity;
    private int originEntityId = -1;
    @Nullable
    private UUID originUuid;
    private int ticksDelayed;
    private int delayTicks;
    private int lifespanTicks;
    /** A client-only Epitaph vision image: placed at the foreseen position instead of trailing the origin. */
    private boolean futureVision;
    /** A Time Erase afterimage outlined in red through walls; Epitaph vision images never are. */
    private boolean timeEraseOutline;
    private int lastVisionRefreshTick;
    private final Queue<PositionSample> originPositions =
            new ArrayDeque<>();

    public KingCrimsonAfterimageEntity(
            EntityType<? extends KingCrimsonAfterimageEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void initialize(
            LivingEntity origin,
            int delay,
            int lifespan) {
        setOriginEntity(origin);
        delayTicks = Math.max(delay, 0);
        lifespanTicks = Math.max(lifespan, 0);
    }

    public void initializeClient(
            int originId,
            UUID expectedOriginUuid,
            int delay,
            int lifespan) {
        originEntityId = originId;
        originUuid = expectedOriginUuid;
        delayTicks = Math.max(delay, 0);
        lifespanTicks = Math.max(lifespan, 0);
        resolveOrigin();
    }

    /**
     * Outlines this Time Erase afterimage in red, through walls, for as long as it lives. In 1.16 the outline was on
     * the full-color "present" copy (glowing, on the red KC_RedGlow team); that copy is gone, and the owner chose to
     * keep its outline on this moving one (2026-09-23). Only the Time Erase user's client ever holds these entities,
     * so, as in 1.16, the outline is that user's alone. On the client setGlowingTag only rewrites the glowing flag
     * from itself, so the flag is also set directly.
     */
    public void showTimeEraseOutline() {
        timeEraseOutline = true;
        setGlowingTag(true);
        if (level().isClientSide()) {
            setSharedFlag(FLAG_GLOWING, true);
        }
    }

    /** Turns this client entity into an Epitaph vision image of {@code originId}. */
    public void initializeFutureVision(int originId, UUID expectedOriginUuid) {
        initializeClient(originId, expectedOriginUuid, 0, Integer.MAX_VALUE);
        futureVision = true;
    }

    /** Moves the vision image to the latest foreseen position. */
    public void refreshFutureVision(Vec3 foreseen) {
        if (lastVisionRefreshTick == 0 && tickCount == 0) {
            moveTo(foreseen.x, foreseen.y, foreseen.z, getYRot(), getXRot());
        }
        else {
            setPos(foreseen.x, foreseen.y, foreseen.z);
        }
        lastVisionRefreshTick = tickCount;
    }

    public boolean isFutureVision() {
        return futureVision;
    }

    @Nullable
    public LivingEntity getOriginEntity() {
        resolveOrigin();
        return originEntity;
    }

    public int originEntityId() {
        return originEntityId;
    }

    @Nullable
    public UUID originUuid() {
        return originUuid;
    }

    public int delayTicks() {
        return delayTicks;
    }

    public int lifespanTicks() {
        return lifespanTicks;
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity origin = getOriginEntity();
        if (origin == null
                || !origin.isAlive()
                || tickCount > lifespanTicks) {
            discard();
            return;
        }
        if (futureVision) {
            if (tickCount - lastVisionRefreshTick
                    > KingCrimsonEpitaphForesightSpec.VISION_STALE_TICKS) {
                discard();
            }
            return;
        }

        originPositions.add(new PositionSample(
                origin.position(),
                origin.getXRot(),
                origin.getYRot()));
        if (++ticksDelayed > delayTicks) {
            PositionSample sample = originPositions.remove();
            moveTo(
                    sample.position().x,
                    sample.position().y,
                    sample.position().z,
                    sample.yRot(),
                    sample.xRot());
        }
    }

    @Override
    public boolean isInvisible() {
        LivingEntity origin = getOriginEntity();
        return super.isInvisible()
                || origin != null && origin.isInvisible();
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        LivingEntity origin = getOriginEntity();
        return super.isInvisibleTo(player)
                || origin != null && origin.isInvisibleTo(player);
    }

    @Override
    public boolean displayFireAnimation() {
        LivingEntity origin = getOriginEntity();
        return origin != null && origin.displayFireAnimation();
    }

    @Override
    public int getTeamColor() {
        return timeEraseOutline
                ? TIME_ERASE_OUTLINE_COLOR
                : super.getTeamColor();
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        delayTicks = tag.getInt("Delay");
        lifespanTicks = tag.getInt("Lifespan");
        if (tag.hasUUID("Origin")) {
            originUuid = tag.getUUID("Origin");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Delay", delayTicks);
        tag.putInt("Lifespan", lifespanTicks);
        if (originUuid != null) {
            tag.putUUID("Origin", originUuid);
        }
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(originEntityId);
        buffer.writeBoolean(originUuid != null);
        if (originUuid != null) {
            buffer.writeUUID(originUuid);
        }
        buffer.writeVarInt(delayTicks);
        buffer.writeVarInt(lifespanTicks);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        originEntityId = buffer.readVarInt();
        originUuid = buffer.readBoolean()
                ? buffer.readUUID()
                : null;
        delayTicks = buffer.readVarInt();
        lifespanTicks = buffer.readVarInt();
        resolveOrigin();
    }

    private void setOriginEntity(LivingEntity origin) {
        originEntity = origin;
        originEntityId = origin.getId();
        originUuid = origin.getUUID();
        copyPosition(origin);
    }

    private void resolveOrigin() {
        if (originEntity != null
                && !originEntity.isRemoved()
                && originEntity.level() == level()
                && originEntity.getId() == originEntityId
                && originEntity.getUUID().equals(originUuid)) {
            return;
        }
        originEntity = null;
        Entity candidate = level().getEntity(originEntityId);
        if (candidate instanceof LivingEntity living
                && living.getUUID().equals(originUuid)) {
            originEntity = living;
        }
    }

    private record PositionSample(
            Vec3 position, float xRot, float yRot) {
    }
}
