package rotp.addons.scarymonsters.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.ScaryMonstersService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ScaryDinosaurEntity extends PathfinderMob {
    /**
     * The 1.16 {@code SOURCE_WIDTH}/{@code SOURCE_HEIGHT} synched data: the bounding box of the creature
     * (or the 1.7x player) the dinosaur was made from; the renderer scales the model by it. 0 = native size.
     */
    private static final EntityDataAccessor<Float> SOURCE_WIDTH =
            SynchedEntityData.defineId(ScaryDinosaurEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SOURCE_HEIGHT =
            SynchedEntityData.defineId(ScaryDinosaurEntity.class, EntityDataSerializers.FLOAT);
    /** The 1.16 {@code PLAYER_CONTROLLED} synched data: the client leaves a ridden form dinosaur out of the revert marker. */
    private static final EntityDataAccessor<Boolean> PLAYER_CONTROLLED =
            SynchedEntityData.defineId(ScaryDinosaurEntity.class, EntityDataSerializers.BOOLEAN);

    /** Persistent-data key of the dinosaur snapshot a camouflage stone carries (the 1.16 name). */
    public static final String CAMOUFLAGE_TAG = "DinoSourceNBT";

    private UUID ownerUuid;
    private UUID possessedPlayerUuid;
    private boolean camouflaging;
    private CompoundTag sourceEntityNbt;
    /** Client-only: tick the current attack clip started on (the 1.16 GeckoLib attack controller). */
    public int clientAttackAnimStart = Integer.MIN_VALUE / 2;

    public ScaryDinosaurEntity(
            EntityType<? extends ScaryDinosaurEntity> type,
            Level level) {
        super(type, level);
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createDinosaurAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.25D, true));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 1.0D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this,
                Player.class,
                true,
                player -> ownerUuid == null
                        || !player.getUUID().equals(ownerUuid)));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SOURCE_WIDTH, 0.0F);
        builder.define(SOURCE_HEIGHT, 0.0F);
        builder.define(PLAYER_CONTROLLED, false);
    }

    public void setSourceSize(float width, float height) {
        entityData.set(SOURCE_WIDTH, width);
        entityData.set(SOURCE_HEIGHT, height);
    }

    public float getSourceWidth() { return entityData.get(SOURCE_WIDTH); }
    public float getSourceHeight() { return entityData.get(SOURCE_HEIGHT); }

    public void setOwnerUuid(UUID owner) { ownerUuid = owner; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public void setPossessedPlayerUuid(UUID player) { possessedPlayerUuid = player; }
    public UUID getPossessedPlayerUuid() { return possessedPlayerUuid; }
    @Override
    public boolean requiresCustomPersistence() {
        return possessedPlayerUuid != null || super.requiresCustomPersistence();
    }
    public void setPlayerControlled(boolean value) { entityData.set(PLAYER_CONTROLLED, value); }
    public boolean isPlayerControlled() { return entityData.get(PLAYER_CONTROLLED); }
    public void setSourceEntityNbt(CompoundTag value) {
        sourceEntityNbt = value != null ? value.copy() : null;
    }
    public CompoundTag getSourceEntityNbt() {
        return sourceEntityNbt != null ? sourceEntityNbt.copy() : new CompoundTag();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity passenger = getFirstPassenger();
        return isPlayerControlled()
                && passenger instanceof Player player
                && ownerUuid != null
                && ownerUuid.equals(player.getUUID())
                ? player : super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        setRot(player.getYRot(), player.getXRot() * 0.5F);
        yRotO = yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return new Vec3(player.xxa, 0.0D, player.zza);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return 1.3F;
    }

    @Override
    public boolean shouldRiderSit() { return false; }

    @Override
    protected void positionRider(
            Entity passenger, MoveFunction callback) {
        if (hasPassenger(passenger)) {
            callback.accept(
                    passenger,
                    getX(),
                    getY() + getBbHeight() * 0.75D,
                    getZ());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) tag.putUUID("OwnerUUID", ownerUuid);
        if (possessedPlayerUuid != null) tag.putUUID("PossessedPlayerUUID", possessedPlayerUuid);
        tag.putBoolean("PlayerControlled", isPlayerControlled());
        if (sourceEntityNbt != null) tag.put("SourceEntityNBT", sourceEntityNbt.copy());
        tag.putFloat("SourceWidth", getSourceWidth());
        tag.putFloat("SourceHeight", getSourceHeight());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerUuid = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        possessedPlayerUuid = tag.hasUUID("PossessedPlayerUUID")
                ? tag.getUUID("PossessedPlayerUUID") : null;
        setPlayerControlled(tag.getBoolean("PlayerControlled"));
        sourceEntityNbt = tag.contains("SourceEntityNBT")
                ? tag.getCompound("SourceEntityNBT") : null;
        setSourceSize(tag.getFloat("SourceWidth"), tag.getFloat("SourceHeight"));
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (dead) ScaryMonstersService.onPossessedDinosaurTerminal(this, true);
    }

    /** True from the moment the dinosaur is put into its camouflage stone: that removal does not end a possession. */
    public boolean isCamouflaging() { return camouflaging; }

    /** Null when the dinosaur could not be saved; it then stays as it is. */
    @Nullable
    public ItemEntity turnIntoBlock() {
        CompoundTag dino = new CompoundTag();
        if (!saveAsPassenger(dino)) return null;
        ItemEntity item = new ItemEntity(
                level(), getX(), getY(), getZ(),
                new ItemStack(Items.STONE));
        item.setUnlimitedLifetime();
        // 1.16 set a pickup delay of 1000000, longer than the stone lived. The stone lives on here, and the delay is
        // saved as a short, so only "never" keeps it out of inventories for good.
        item.setNeverPickUp();
        item.getPersistentData().put(CAMOUFLAGE_TAG, dino);
        camouflaging = true;
        discard();
        return item;
    }

    public static boolean revertFromBlock(
            ItemEntity item, ServerLevel level) {
        CompoundTag nbt = item.getPersistentData()
                .getCompound(CAMOUFLAGE_TAG);
        if (nbt.isEmpty()) return false;
        Entity entity = EntityType.loadEntityRecursive(
                nbt, level, loaded -> {
                    loaded.moveTo(item.position());
                    return loaded;
                });
        if (!(entity instanceof ScaryDinosaurEntity) || !level.addFreshEntity(entity)) return false;
        // Without its snapshot the stone leaves as plain stone, not as a destroyed camouflage.
        item.getPersistentData().remove(CAMOUFLAGE_TAG);
        item.discard();
        return true;
    }
}
