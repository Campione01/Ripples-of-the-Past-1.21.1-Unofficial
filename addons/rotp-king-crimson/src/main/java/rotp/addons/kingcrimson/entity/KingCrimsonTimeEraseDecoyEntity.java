package rotp.addons.kingcrimson.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.adventure.npc.PowerUserMobEntity;
import rotp.core.network.s2c.TrSetStandEntityPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.Player;
import rotp.core.powersystem.standpower.StandUtil;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KingCrimsonTimeEraseDecoyEntity
        extends PowerUserMobEntity
        implements IEntityWithComplexSpawn {
    private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
            SynchedEntityData.defineId(
                    KingCrimsonTimeEraseDecoyEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>>
            DATA_OWNER_UUID = SynchedEntityData.defineId(
                    KingCrimsonTimeEraseDecoyEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_SKIN_PARTS =
            SynchedEntityData.defineId(
                    KingCrimsonTimeEraseDecoyEntity.class,
                    EntityDataSerializers.INT);

    @Nullable
    private LivingEntity owner;
    // 1.16 KCConfig timeEraseDuration; the client takes the server's value from the spawn data
    private int lifespanTicks =
            KingCrimsonConfig.timeEraseDurationTicks();
    private boolean clientStandPresentationPending = true;
    private boolean removingStand;

    public KingCrimsonTimeEraseDecoyEntity(
            EntityType<? extends KingCrimsonTimeEraseDecoyEntity> type,
            Level level) {
        super(type, level);
        PowerClass.STAND.attachGet(this);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER_ID, -1);
        builder.define(DATA_OWNER_UUID, Optional.empty());
        builder.define(DATA_SKIN_PARTS, 0);
    }

    public void setOwner(ServerPlayer newOwner) {
        owner = newOwner;
        entityData.set(DATA_OWNER_ID, newOwner.getId());
        entityData.set(
                DATA_OWNER_UUID,
                Optional.of(newOwner.getUUID()));
        entityData.set(
                DATA_PROFILE,
                Optional.of(new ResolvableProfile(
                        newOwner.getGameProfile())));

        int skinParts = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (newOwner.isModelPartShown(part)) {
                skinParts |= part.getMask();
            }
        }
        entityData.set(DATA_SKIN_PARTS, skinParts);
        copyOwnerPresentation(newOwner);

        StandPower sourcePower = StandPower.get(newOwner);
        StandPower decoyPower = PowerClass.STAND.attachGet(this);
        if (sourcePower != null) {
            decoyPower.setStandInstance(
                    sourcePower.getStandInstance());
        }
    }

    @Nullable
    public LivingEntity getOwner() {
        resolveOwner();
        return owner;
    }

    @Override
    public boolean isPickable() {
        return !(getOwner() instanceof Player player && player.isLocalPlayer())
                && super.isPickable();
    }

    public static boolean isOwnProjection(Entity target, @Nullable Entity aiming) {
        LivingEntity user = aiming instanceof LivingEntity living ? StandUtil.getStandUser(living) : null;
        Entity projected = target instanceof StandEntity stand ? stand.getUser() : target;
        return user != null && projected instanceof KingCrimsonTimeEraseDecoyEntity decoy
                && decoy.getOwner() == user;
    }

    public boolean isSkinPartShown(PlayerModelPart part) {
        return (entityData.get(DATA_SKIN_PARTS)
                & part.getMask()) != 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            fixClientStandPresentation();
            return;
        }

        if (!isValidDecoy() || tickCount > lifespanTicks) {
            discard();
            return;
        }
        if (tickCount == 1) {
            summonStandSilently();
        }
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected void customServerAiStep() {
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && !removingStand) {
            removeSummonedStand();
        }
        super.remove(reason);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityData.get(DATA_OWNER_ID));
        Optional<UUID> ownerUuid =
                entityData.get(DATA_OWNER_UUID);
        buffer.writeBoolean(ownerUuid.isPresent());
        ownerUuid.ifPresent(buffer::writeUUID);
        buffer.writeVarInt(lifespanTicks);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        entityData.set(DATA_OWNER_ID, buffer.readVarInt());
        entityData.set(
                DATA_OWNER_UUID,
                buffer.readBoolean()
                        ? Optional.of(buffer.readUUID())
                        : Optional.empty());
        lifespanTicks = buffer.readVarInt();
        resolveOwner();
    }

    private void copyOwnerPresentation(LivingEntity source) {
        copyPosition(source);
        setYBodyRot(source.yBodyRot);
        yBodyRotO = source.yBodyRotO;
        setYHeadRot(source.yHeadRot);
        yHeadRotO = source.yHeadRotO;
        setPose(source.getPose());
        setShiftKeyDown(source.isShiftKeyDown());
        setSprinting(source.isSprinting());
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND,
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD}) {
            setItemSlot(slot, source.getItemBySlot(slot).copy());
        }
    }

    private boolean isValidDecoy() {
        LivingEntity currentOwner = getOwner();
        return currentOwner instanceof ServerPlayer player
                && currentOwner.isAlive()
                && currentOwner.level() == level()
                && KingCrimsonAddon.temporalState(player)
                        .isErasing(level().getGameTime());
    }

    private void summonStandSilently() {
        StandPower decoyPower = PowerClass.STAND.attachGet(this);
        if (!(decoyPower.getPowerType()
                instanceof EntityStandType standType)
                || decoyPower.isSummoned()) {
            return;
        }
        Entity created = standType.getEntityType().create(level());
        if (!(created instanceof StandEntity stand)) {
            return;
        }
        stand.withStandType(standType);

        LivingEntity source = this;
        LivingEntity currentOwner = getOwner();
        StandPower sourcePower = StandPower.get(currentOwner);
        if (sourcePower != null
                && sourcePower.getSummonedStandEntity() != null) {
            source = sourcePower.getSummonedStandEntity();
        }
        stand.copyPosition(source);
        stand.copyStandUserRotation(source);
        // in its summon pose a Stand's body is on its yaw from its spawn on
        // (core prepareFreshSummonState); copyStandUserRotation leaves it at 0
        stand.yBodyRot = stand.getYRot();
        stand.yBodyRotO = stand.yBodyRot;
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        // The decoy explicitly owns this visual Stand's lifetime.
        stand.healthLinkedWithUser = false;
        decoyPower.setSummonedStand(stand);

        if (!level().addFreshEntity(stand)) {
            decoyPower.setSummonedStand(null);
            stand.discard();
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                this,
                new TrSetStandEntityPacket(
                        getId(), stand.getId()));
    }

    private void fixClientStandPresentation() {
        if (!clientStandPresentationPending) {
            return;
        }
        StandPower power = StandPower.get(this);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        if (stand == null) {
            return;
        }
        stand.tickCount = Math.max(stand.tickCount, 999);
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        stand.overlayTickCount = Math.max(
                stand.overlayTickCount, 999);
        clientStandPresentationPending = false;
    }

    private void removeSummonedStand() {
        removingStand = true;
        try {
            StandPower power = StandPower.get(this);
            StandEntity stand = power != null
                    ? power.getSummonedStandEntity()
                    : null;
            if (power != null) {
                power.setSummonedStand(null);
            }
            if (stand != null) {
                stand.remove(RemovalReason.DISCARDED);
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    this,
                    new TrSetStandEntityPacket(getId(), 0));
        } finally {
            removingStand = false;
        }
    }

    private void resolveOwner() {
        Optional<UUID> expectedUuid =
                entityData.get(DATA_OWNER_UUID);
        if (owner != null
                && !owner.isRemoved()
                && owner.level() == level()
                && owner.getId() == entityData.get(DATA_OWNER_ID)
                && expectedUuid
                        .filter(owner.getUUID()::equals)
                        .isPresent()) {
            return;
        }
        owner = null;
        Entity byId = level().getEntity(
                entityData.get(DATA_OWNER_ID));
        if (byId instanceof LivingEntity living
                && expectedUuid
                        .filter(living.getUUID()::equals)
                        .isPresent()) {
            owner = living;
            return;
        }
        if (level() instanceof ServerLevel serverLevel
                && expectedUuid.isPresent()) {
            Entity byUuid = serverLevel.getEntity(
                    expectedUuid.get());
            if (byUuid instanceof LivingEntity living) {
                owner = living;
            }
        }
    }
}
