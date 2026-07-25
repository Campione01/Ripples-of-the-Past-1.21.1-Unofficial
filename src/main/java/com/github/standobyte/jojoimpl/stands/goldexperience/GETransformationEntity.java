package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mechanics.BleedingEffect;
import com.github.standobyte.jojo.mrpresident.CocoJumboTurtleEntity;
import com.github.standobyte.jojo.util.functions.NBTUtil;
import com.github.standobyte.jojo.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.event.EventHooks;

public class GETransformationEntity extends PathfinderMob implements IEntityWithComplexSpawn {
    private static final int TURN_BACK_TICKS = 10;
    private static final EntityDataAccessor<Boolean> DATA_TURNING_BACK = SynchedEntityData.defineId(GETransformationEntity.class,
            EntityDataSerializers.BOOLEAN);

    public enum FollowTargetMode {
        TRACK,
        AGGRO_TRACK,
        AGGRO_FORGETFUL,
        DELIVERY
    }

    private ItemStack sourceItem = ItemStack.EMPTY;
    private ItemStack sourceEntityItemView = ItemStack.EMPTY;
    @Nullable private CompoundTag sourceEntityNbt;
    @Nullable private BlockState sourceBlock;
    @Nullable private CompoundTag sourceBlockEntityNbt;
    @Nullable private Entity transformationTarget;
    @Nullable private Entity clientSourceEntity;
    @Nullable private UUID followTarget;
    @Nullable private FollowTargetMode followTargetMode;
    @Nullable private UUID hostId;
    @Nullable private UUID restoreOwnerId;
    @Nullable private Vec3 hostFollowOffset;
    private boolean sourceRestored;
    private boolean turningBack;
    private boolean startSoundPlayed;
    private boolean revertSoundPlayed;
    private int duration;
    private float renderAsItemTime;
    private boolean targetSpawned;
    public int actionCooldown;

    public GETransformationEntity(EntityType<? extends GETransformationEntity> type, Level level) {
        super(type, level);
    }

    public GETransformationEntity withSourceItem(ItemStack sourceItem) {
        this.sourceItem = sourceItem.copy();
        if (!this.sourceItem.isEmpty()) {
            this.sourceItem.setCount(1);
        }
        return this;
    }

    public GETransformationEntity withSourceBlock(BlockState sourceBlock) {
        return withSourceBlock(sourceBlock, null);
    }

    public GETransformationEntity withSourceBlock(BlockState sourceBlock, @Nullable CompoundTag sourceBlockEntityNbt) {
        this.sourceBlock = sourceBlock;
        this.sourceBlockEntityNbt = sourceBlockEntityNbt != null ? sourceBlockEntityNbt.copy() : null;
        return this;
    }

    public GETransformationEntity withSourceEntity(CompoundTag sourceEntityNbt, ItemStack sourceEntityItemView) {
        this.sourceEntityNbt = sourceEntityNbt.copy();
        this.sourceEntityItemView = sourceEntityItemView.copy();
        if (!this.sourceEntityItemView.isEmpty()) {
            this.sourceEntityItemView.setCount(1);
        }
        return this;
    }

    public GETransformationEntity withTransformationTarget(Entity transformationTarget) {
        this.transformationTarget = transformationTarget;
        return this;
    }

    public GETransformationEntity withDuration(int duration) {
        this.duration = Math.max(duration, 1);
        this.renderAsItemTime = Math.min(this.duration / 3.0F, 20.0F);
        return this;
    }

    public int getDuration() {
        return duration;
    }

    public boolean isTurningBack() {
        return turningBack || entityData.get(DATA_TURNING_BACK);
    }

    @Nullable
    public Entity getTransformationTarget() {
        return transformationTarget;
    }

    @Nullable
    public Entity getSourceEntityForRender() {
        if (clientSourceEntity != null) {
            return clientSourceEntity;
        }
        if (sourceEntityNbt != null) {
            clientSourceEntity = EntityType.create(sourceEntityNbt, level()).orElse(null);
        }
        return clientSourceEntity;
    }

    @Nullable
    public BlockState getSourceBlockState() {
        return sourceBlock;
    }

    public float getTfProgressTime(float partialTick) {
        float time = Math.min(tickCount + partialTick, duration);
        return isTurningBack() ? duration - time : time;
    }

    public float getRenderAsItemTime() {
        return renderAsItemTime;
    }

    @Override
    public void refreshDimensions() {
        double x = getX();
        double y = getY();
        double z = getZ();
        super.refreshDimensions();
        setPos(x, y, z);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions dimensions = super.getDefaultDimensions(pose);
        float progressTime = getTfProgressTime(0.0F);
        float renderAsItemTime = getRenderAsItemTime();
        if (progressTime < renderAsItemTime) {
            float scale = 1.0F - progressTime / Math.max(renderAsItemTime, 1.0F);
            if (scale > 0.0F) {
                Entity sourceEntity = getSourceEntityForRender();
                if (sourceEntity != null) {
                    return sourceEntity.getDimensions(pose).scale(scale);
                }
                if (sourceBlock != null) {
                    return EntityDimensions.scalable(1.0F, 1.0F).scale(scale);
                }
            }
        }
        else if (transformationTarget != null) {
            float targetPhase = Math.max(duration - renderAsItemTime, 1.0F);
            float scale = 1.0F - (duration - progressTime) / targetPhase;
            if (scale > 0.0F) {
                return transformationTarget.getDimensions(pose).scale(scale);
            }
        }
        return dimensions;
    }

    public GETransformationEntity withFollowTarget(@Nullable UUID entity, @Nullable FollowTargetMode mode,
            @Nullable LivingEntity standUser) {
        if (entity == null || mode == null) {
            this.followTarget = null;
            this.followTargetMode = null;
            return this;
        }
        if (standUser != null && standUser.getUUID().equals(entity)) {
            mode = switch (mode) {
                case AGGRO_TRACK -> FollowTargetMode.TRACK;
                case AGGRO_FORGETFUL -> null;
                default -> mode;
            };
        }
        this.followTarget = mode != null ? entity : null;
        this.followTargetMode = mode;
        return this;
    }

    @Nullable
    public UUID getFollowTarget() {
        return followTarget;
    }

    @Nullable
    public FollowTargetMode getFollowTargetMode() {
        return followTargetMode;
    }

    public ItemStack getSourceItemView() {
        if (!sourceEntityItemView.isEmpty()) {
            return sourceEntityItemView.copy();
        }
        return sourceItem.copy();
    }

    public GETransformationEntity withHost(@Nullable LivingEntity host) {
        if (host == null) {
            clearHost();
            return this;
        }
        this.hostId = host.getUUID();
        this.hostFollowOffset = new Vec3(0.0D, host.getBbHeight() - 0.5D, 0.0D);
        startRiding(host, true);
        return this;
    }

    @Override
    public void tick() {
        playTransformationSound();
        if (!level().isClientSide() && transformationTarget != null && !targetSpawned && duration > 0
                && tickCount >= duration) {
            finishTransformation();
            return;
        }
        if (isTurningBack() && sourceEntityNbt == null && sourceBlock != null) {
            int timeLeft = duration - tickCount;
            float timeAsBlock = getRenderAsItemTime();
            if (timeLeft - 1 <= timeAsBlock && timeLeft > timeAsBlock) {
                Vec3 pos = Vec3.atBottomCenterOf(blockPosition());
                moveTo(pos.x, pos.y, pos.z, getYRot(), getXRot());
            }
        }
        if (!level().isClientSide() && !isTurningBack() && !targetSpawned) {
            tickHost();
        }
        if (!isRemoved()) {
            refreshDimensions();
        }
        super.tick();
    }

    @Override
    public void travel(Vec3 travelVector) {
        double fluidHeight = getEyeHeight() - 0.11111111D;
        Vec3 deltaMovement = getDeltaMovement();
        if (isInWater() && getFluidHeight(FluidTags.WATER) > fluidHeight) {
            setDeltaMovement(
                    deltaMovement.x * 0.99D,
                    deltaMovement.y + (deltaMovement.y < 0.06D ? 5.0E-4D : 0.0D),
                    deltaMovement.z * 0.99D);
        }
        else if (isInLava() && getFluidHeight(FluidTags.LAVA) > fluidHeight) {
            setDeltaMovement(
                    deltaMovement.x * 0.95D,
                    deltaMovement.y + (deltaMovement.y < 0.06D ? 5.0E-4D : 0.0D),
                    deltaMovement.z * 0.95D);
        }
        else if (!isNoGravity()) {
            setDeltaMovement(deltaMovement.add(0.0D, -0.04D, 0.0D));
        }

        deltaMovement = getDeltaMovement();
        if (!onGround() || deltaMovement.horizontalDistanceSqr() > 1.0E-5D || (tickCount + getId()) % 4 == 0) {
            move(MoverType.SELF, deltaMovement);
            double inertia = 0.98D;
            if (onGround()) {
                BlockPos below = BlockPos.containing(getX(), getY() - 1.0D, getZ());
                inertia = level().getBlockState(below).getBlock().getFriction() * 0.98D;
            }
            deltaMovement = deltaMovement.multiply(inertia, 0.98D, inertia);
            if (onGround() && deltaMovement.y < 0.0D) {
                deltaMovement = deltaMovement.multiply(1.0D, -0.5D, 1.0D);
            }
            setDeltaMovement(deltaMovement);
        }
    }

    @Override
    public void rideTick() {
        super.rideTick();
        Entity vehicle = getVehicle();
        if (vehicle instanceof LivingEntity host && isHost(host) && hostFollowOffset != null) {
            Vec3 position = host.position().add(hostFollowOffset);
            setPos(position.x, position.y, position.z);
        }
    }

    @Override
    public boolean isPickable() {
        Entity vehicle = getVehicle();
        return super.isPickable() && !(vehicle instanceof LivingEntity host && isHost(host));
    }

    private void tickHost() {
        LivingEntity host = getHost();
        if (hostId == null || host == null || !host.isAlive()) {
            clearHost();
            return;
        }
        if (getVehicle() != host && !startRiding(host, true)) {
            clearHost();
            return;
        }
        if (tickCount > 40) {
            hostBleeding(host);
            clearHost();
        }
        else if (tickCount > 0 && tickCount % 10 == 9) {
            dealDamageToHost(host);
        }
    }

    @Nullable
    private LivingEntity getHost() {
        Entity vehicle = getVehicle();
        if (vehicle instanceof LivingEntity living && isHost(living)) {
            return living;
        }
        if (level() instanceof ServerLevel serverLevel && hostId != null) {
            Entity entity = serverLevel.getEntity(hostId);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private boolean isHost(Entity entity) {
        return hostId != null && entity.getUUID().equals(hostId);
    }

    private void clearHost() {
        if (getVehicle() instanceof LivingEntity host && isHost(host)) {
            stopRiding();
        }
        hostId = null;
        hostFollowOffset = null;
    }

    private void hostBleeding(LivingEntity host) {
        if (hostFollowOffset != null) {
            BleedingEffect.setNextParticlesPos(host, host.position().add(hostFollowOffset).add(0.0D, 0.5D, 0.0D));
        }
        host.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 200, 1, false, false, true));
    }

    private void dealDamageToHost(LivingEntity host) {
        DamageUtil.hurtThroughInvulTicks(host, DamageUtil.make(level(), ModDamageTypes.STAND_ARROW, this), 2.0F);
    }

    private void playTransformationSound() {
        if (!level().isClientSide() || !ClientGlobals.canHearStands) {
            return;
        }
        if (isTurningBack()) {
            playRevertSound();
        }
        else if (tickCount == 1 && !startSoundPlayed) {
            level().playLocalSound(getX(), getY(), getZ(),
                    ModSoundEvents.GOLD_EXPERIENCE_LIFE_START.get(), getSoundSource(), 1.0F, 1.0F, false);
            startSoundPlayed = true;
        }
    }

    private void playRevertSound() {
        if (level().isClientSide() && ClientGlobals.canHearStands && !revertSoundPlayed) {
            level().playLocalSound(getX(), getY(), getZ(),
                    ModSoundEvents.GOLD_EXPERIENCE_LIFE_REVERT.get(), getSoundSource(), 1.0F, 1.0F, false);
            revertSoundPlayed = true;
        }
    }

    @Nullable
    private Entity finishTransformation() {
        if (level().isClientSide() || transformationTarget == null || targetSpawned) {
            return null;
        }

        if (turningBack) {
            restoreSourceFromTransformation();
            if (transformationTarget.isAlive()) {
                transformationTarget.discard();
            }
            targetSpawned = true;
            discard();
            return null;
        }

        Entity target = transformationTarget;
        target.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
        target.setDeltaMovement(getDeltaMovement());
        target.setOnGround(onGround());
        if (target instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.playAmbientSound();
        }
        else if (target instanceof ItemEntity itemEntity) {
            itemEntity.setNoPickUpDelay();
        }
        copyStatus(this, target);

        if (level().addFreshEntity(target)) {
            if (target instanceof Mob mob) {
                applyFollowTargetOnSpawn(mob);
            }
            targetSpawned = true;
            discard();
            return target;
        }
        return null;
    }

    private void applyFollowTargetOnSpawn(Mob mob) {
        if (!(level() instanceof ServerLevel serverLevel) || followTarget == null || followTargetMode == null) {
            return;
        }
        if (followTargetMode == FollowTargetMode.AGGRO_TRACK) {
            mob.targetSelector.addGoal(0, new SpecificTargetGoal(mob, followTarget));
        }
        else if (followTargetMode == FollowTargetMode.AGGRO_FORGETFUL) {
            Entity target = serverLevel.getEntity(followTarget);
            if (target instanceof LivingEntity livingTarget) {
                mob.setTarget(livingTarget);
            }
        }
    }

    public void turnBackIntoSource(@Nullable LivingEntity owner) {
        if (!level().isClientSide() && !sourceRestored) {
            if (owner != null) {
                restoreOwnerId = owner.getUUID();
            }
            beginTurningBack();
        }
    }

    public static void turnEntityBack(net.minecraft.world.entity.Entity entity, @Nullable LivingEntity owner) {
        if (entity instanceof GETransformationEntity transformation) {
            transformation.turnBackIntoSource(owner);
        }
        else {
            restoreEntitySource(entity, owner, ItemStack.EMPTY, null, null, null, ItemStack.EMPTY);
        }
    }

    public static void restoreEntitySource(Entity entity, @Nullable LivingEntity owner, ItemStack sourceItem,
            @Nullable BlockState sourceBlock, @Nullable CompoundTag sourceBlockEntityNbt,
            @Nullable CompoundTag sourceEntityNbt) {
        restoreEntitySource(entity, owner, sourceItem, sourceBlock, sourceBlockEntityNbt, sourceEntityNbt, ItemStack.EMPTY);
    }

    public static void restoreEntitySource(Entity entity, @Nullable LivingEntity owner, ItemStack sourceItem,
            @Nullable BlockState sourceBlock, @Nullable CompoundTag sourceBlockEntityNbt,
            @Nullable CompoundTag sourceEntityNbt, ItemStack sourceEntityItemView) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof GETransformationEntity transformation) {
                transformation.turnBackIntoSource(owner);
                return;
            }
            GETransformationEntity reverse = ModEntityTypes.GE_LIFEFORM_TRANSFORMATION.get().create(entity.level());
            if (reverse != null) {
                reverse.withTransformationTarget(entity).withDuration(TURN_BACK_TICKS);
                if (!sourceItem.isEmpty()) {
                    reverse.withSourceItem(sourceItem);
                }
                if (sourceEntityNbt != null) {
                    reverse.withSourceEntity(sourceEntityNbt, sourceEntityItemView);
                }
                if (sourceBlock != null) {
                    reverse.withSourceBlock(rotateDirectionalBlockToward(sourceBlock, entity), sourceBlockEntityNbt);
                }
                copyStatus(entity, reverse);
                reverse.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                reverse.beginTurningBack();
                if (entity.level().addFreshEntity(reverse)) {
                    dropRevertedEntityContents(entity);
                    entity.discard();
                    return;
                }
            }
            restoreSourceImmediately(entity, owner, sourceItem, sourceBlock, sourceBlockEntityNbt, sourceEntityNbt);
        }
        dropRevertedEntityContents(entity);
        entity.discard();
    }

    private static void dropRevertedEntityContents(Entity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = living.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    living.spawnAtLocation(stack.copy());
                    living.setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
        if (entity instanceof CocoJumboTurtleEntity turtle) {
            turtle.dropKey();
        }
    }

    private void beginTurningBack() {
        if (turningBack || entityData.get(DATA_TURNING_BACK)) {
            return;
        }
        clearHost();
        turningBack = true;
        entityData.set(DATA_TURNING_BACK, true);
        if (!targetSpawned && transformationTarget != null && transformationTarget.isAlive()) {
            transformationTarget.discard();
        }
        reverseTransformationProgress();
        playRevertSound();
    }

    private void reverseTransformationProgress() {
        int ticks = TURN_BACK_TICKS;
        if (tickCount < ticks) {
            tickCount = duration - tickCount;
            return;
        }

        float prevItemTime = getRenderAsItemTime();
        if (tickCount > prevItemTime) {
            renderAsItemTime = ticks / 3.0F;
            duration = (int) ((duration - prevItemTime) * (ticks - renderAsItemTime)
                    / (tickCount - prevItemTime) + renderAsItemTime);
        }
        else {
            renderAsItemTime = ticks * prevItemTime / Math.max(tickCount, 1);
            duration = Mth.ceil(renderAsItemTime);
        }
        tickCount = duration - ticks;
    }

    private void restoreSourceFromTransformation() {
        if (sourceRestored) {
            return;
        }
        if (sourceEntityNbt != null && restoreSourceEntity(this, sourceEntityNbt)) {
            sourceRestored = true;
        }
        else if (!sourceItem.isEmpty()) {
            dropSourceItem(level(), getX(), getY(), getZ(), sourceItem);
            sourceRestored = true;
        }
        else if (sourceBlock != null) {
            restoreSourceBlock(getRestoreEventEntity(), level(), blockPosition(), sourceBlock, sourceBlockEntityNbt);
            sourceRestored = true;
        }
    }

    private Entity getRestoreEventEntity() {
        if (restoreOwnerId != null && level() instanceof ServerLevel serverLevel) {
            Entity owner = serverLevel.getEntity(restoreOwnerId);
            if (owner != null) {
                return owner;
            }
        }
        return this;
    }

    private static void restoreSourceImmediately(Entity entity, @Nullable LivingEntity owner, ItemStack sourceItem,
            @Nullable BlockState sourceBlock, @Nullable CompoundTag sourceBlockEntityNbt,
            @Nullable CompoundTag sourceEntityNbt) {
        if (sourceEntityNbt != null) {
            restoreSourceEntity(entity, sourceEntityNbt);
        }
        else if (!sourceItem.isEmpty()) {
            dropSourceItem(entity.level(), entity.getX(), entity.getY(), entity.getZ(), sourceItem);
        }
        else if (sourceBlock != null) {
            restoreSourceBlock(owner != null ? owner : entity, entity.level(), entity.blockPosition(), sourceBlock, sourceBlockEntityNbt);
        }
    }

    private static void dropSourceItem(Level level, double x, double y, double z, ItemStack sourceItem) {
        ItemEntity itemEntity = new ItemEntity(level, x, y, z, sourceItem.copy());
        itemEntity.setNoPickUpDelay();
        level.addFreshEntity(itemEntity);
    }

    private static boolean restoreSourceEntity(Entity currentEntity, CompoundTag sourceEntityNbt) {
        Entity sourceEntity = EntityType.create(sourceEntityNbt, currentEntity.level()).orElse(null);
        if (sourceEntity == null) {
            return false;
        }
        copyStatus(currentEntity, sourceEntity);
        sourceEntity.moveTo(currentEntity.getX(), currentEntity.getY(), currentEntity.getZ(),
                currentEntity.getYRot(), currentEntity.getXRot());
        return currentEntity.level().addFreshEntity(sourceEntity);
    }

    private static void copyStatus(Entity from, Entity to) {
        if (from.isOnFire()) {
            to.setRemainingFireTicks(from.getRemainingFireTicks());
        }
        if (from.hasCustomName() && !(to instanceof ItemEntity)) {
            to.setCustomName(from.getCustomName());
        }
        if (from.isPassenger() && from.getVehicle() != null) {
            to.startRiding(from.getVehicle(), true);
        }
        if (from.isVehicle()) {
            from.getPassengers().forEach(passenger -> passenger.startRiding(to, true));
        }
        to.setDeltaMovement(from.getDeltaMovement());
    }

    private static BlockState rotateDirectionalBlockToward(BlockState state, Entity entity) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty directionProperty) {
                Direction bestDirection = null;
                double bestDot = -Double.MAX_VALUE;
                Vec3 look = entity.getLookAngle();
                for (Direction direction : directionProperty.getPossibleValues()) {
                    double dot = look.dot(new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ()));
                    if (dot > bestDot) {
                        bestDot = dot;
                        bestDirection = direction;
                    }
                }
                if (bestDirection != null) {
                    return state.setValue(directionProperty, bestDirection);
                }
            }
        }
        return state;
    }

    private static void restoreSourceBlock(@Nullable Entity eventEntity, Level level, BlockPos blockPos, BlockState sourceBlock,
            @Nullable CompoundTag sourceBlockEntityNbt) {
        sourceBlock = Block.updateFromNeighbourShapes(sourceBlock, level, blockPos);
        BlockState currentState = level.getBlockState(blockPos);
        if ((currentState.isAir() || currentState.canBeReplaced()) && sourceBlock.canSurvive(level, blockPos)) {
            if (!EventHooks.onBlockPlace(eventEntity, BlockSnapshot.create(level.dimension(), level, blockPos), Direction.UP)) {
                if (eventEntity != null && eventEntity.isOnFire()) {
                    sourceBlock.onCaughtFire(level, blockPos, Direction.UP,
                            eventEntity instanceof LivingEntity living ? living : null);
                }
                else {
                    level.setBlock(blockPos, sourceBlock, Block.UPDATE_ALL);
                    if (sourceBlockEntityNbt != null) {
                        BlockEntity blockEntity = BlockEntity.loadStatic(blockPos, sourceBlock, sourceBlockEntityNbt, level.registryAccess());
                        if (blockEntity != null) {
                            level.setBlockEntity(blockEntity);
                        }
                    }
                }
            }
            return;
        }

        BlockEntity sourceBlockEntity = sourceBlockEntityNbt != null
                ? BlockEntity.loadStatic(blockPos, sourceBlock, sourceBlockEntityNbt, level.registryAccess())
                : null;
        if (!(sourceBlock.getBlock() instanceof BaseFireBlock)) {
            level.levelEvent(2001, blockPos, Block.getId(sourceBlock));
        }
        Block.dropResources(sourceBlock, level, blockPos, sourceBlockEntity);
        if (sourceBlockEntity instanceof Container container) {
            Containers.dropContents(level, blockPos, container);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TURNING_BACK, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TURNING_BACK.equals(key) && entityData.get(DATA_TURNING_BACK) && !turningBack) {
            turningBack = true;
            reverseTransformationProgress();
            playRevertSound();
        }
    }

    @Override
    public boolean isInvisible() {
        return super.isInvisible() || targetSpawned;
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(tickCount);
        buffer.writeVarInt(duration);
        buffer.writeFloat(renderAsItemTime);
        buffer.writeBoolean(isTurningBack());
        buffer.writeBoolean(targetSpawned);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, sourceItem);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, sourceEntityItemView);
        buffer.writeNbt(sourceEntityNbt != null ? sourceEntityNbt.copy() : null);
        CompoundTag sourceBlockNbt = new CompoundTag();
        NBTUtil.put(sourceBlockNbt, "Block", sourceBlock, BlockState.CODEC);
        buffer.writeNbt(sourceBlockNbt);
        buffer.writeNbt(sourceBlockEntityNbt != null ? sourceBlockEntityNbt.copy() : null);
        writeRenderEntity(buffer, !targetSpawned ? transformationTarget : null);
        writeRenderEntity(buffer, createSourceEntityForRender());
        buffer.writeBoolean(hostId != null);
        if (hostId != null) {
            buffer.writeUUID(hostId);
            buffer.writeBoolean(hostFollowOffset != null);
            if (hostFollowOffset != null) {
                buffer.writeDouble(hostFollowOffset.x);
                buffer.writeDouble(hostFollowOffset.y);
                buffer.writeDouble(hostFollowOffset.z);
            }
            Entity vehicle = getVehicle();
            buffer.writeVarInt(vehicle instanceof LivingEntity host && isHost(host) ? vehicle.getId() : -1);
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        tickCount = buffer.readVarInt();
        duration = buffer.readVarInt();
        renderAsItemTime = buffer.readFloat();
        turningBack = buffer.readBoolean();
        entityData.set(DATA_TURNING_BACK, turningBack);
        targetSpawned = buffer.readBoolean();
        sourceItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        sourceEntityItemView = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        sourceEntityNbt = buffer.readNbt();
        clientSourceEntity = null;
        CompoundTag sourceBlockNbt = buffer.readNbt();
        sourceBlock = sourceBlockNbt != null ? NBTUtil.getOptional(sourceBlockNbt, "Block", BlockState.CODEC).orElse(null) : null;
        sourceBlockEntityNbt = buffer.readNbt();
        transformationTarget = readRenderEntity(buffer, level());
        clientSourceEntity = readRenderEntity(buffer, level());
        if (buffer.readBoolean()) {
            hostId = buffer.readUUID();
            hostFollowOffset = buffer.readBoolean()
                    ? new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
                    : null;
            int hostEntityId = buffer.readVarInt();
            Entity host = hostEntityId >= 0 ? level().getEntity(hostEntityId) : null;
            if (host instanceof LivingEntity livingHost && isHost(livingHost)) {
                startRiding(livingHost, true);
            }
        }
        else {
            clearHost();
        }
    }

    @Nullable
    private Entity createSourceEntityForRender() {
        return sourceEntityNbt != null ? EntityType.create(sourceEntityNbt, level()).orElse(null) : null;
    }

    private static void writeRenderEntity(RegistryFriendlyByteBuf buffer, @Nullable Entity entity) {
        ResourceLocation entityTypeId = entity != null ? BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) : null;
        buffer.writeBoolean(entityTypeId != null);
        if (entityTypeId == null || entity == null) {
            return;
        }

        buffer.writeResourceLocation(entityTypeId);
        buffer.writeByte(Mth.floor(entity.getXRot() * 256.0F / 360.0F));
        buffer.writeByte(Mth.floor(entity.getYRot() * 256.0F / 360.0F));
        buffer.writeByte((byte) (entity.getYHeadRot() * 256.0F / 360.0F));
        if (entity instanceof IEntityWithComplexSpawn complexSpawn) {
            complexSpawn.writeSpawnData(buffer);
        }

        List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().getNonDefaultValues();
        if (values != null) {
            for (SynchedEntityData.DataValue<?> value : values) {
                value.write(buffer);
            }
        }
        buffer.writeByte(255);
    }

    @Nullable
    private static Entity readRenderEntity(RegistryFriendlyByteBuf buffer, Level level) {
        if (!buffer.readBoolean()) {
            return null;
        }

        ResourceLocation entityTypeId = buffer.readResourceLocation();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityTypeId);
        Entity entity = type != null ? type.create(level) : null;
        float pitch = (buffer.readByte() * 360.0F) / 256.0F;
        float yaw = (buffer.readByte() * 360.0F) / 256.0F;
        float headYaw = (buffer.readByte() * 360.0F) / 256.0F;

        if (entity instanceof IEntityWithComplexSpawn complexSpawn) {
            complexSpawn.readSpawnData(buffer);
        }

        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
        int id;
        while ((id = buffer.readUnsignedByte()) != 255) {
            values.add(SynchedEntityData.DataValue.read(buffer, id));
        }

        if (entity == null) {
            JojoMod.getLogger().warn("Failed to read Gold Experience transformation render entity {}", entityTypeId);
            return null;
        }

        entity.setYRot(yaw % 360.0F);
        entity.setXRot(Mth.clamp(pitch, -90.0F, 90.0F) % 360.0F);
        entity.yRotO = entity.getYRot();
        entity.xRotO = entity.getXRot();
        entity.setYHeadRot(headYaw);
        entity.setYBodyRot(headYaw);
        if (entity instanceof LivingEntity living) {
            living.yHeadRotO = living.yHeadRot;
            living.yBodyRotO = living.yBodyRot;
        }
        if (!values.isEmpty()) {
            try {
                entity.getEntityData().assignValues(values);
            }
            catch (RuntimeException e) {
                JojoMod.getLogger().warn("Failed to apply Gold Experience transformation render entity data for {}", entityTypeId, e);
            }
        }
        return entity;
    }

    @Nullable
    private CompoundTag writeTransformationTargetNbt() {
        if (transformationTarget == null || targetSpawned) {
            return null;
        }
        String targetId = transformationTarget.getEncodeId();
        if (targetId == null) {
            return null;
        }
        CompoundTag targetTag = new CompoundTag();
        targetTag.putString("id", targetId);
        transformationTarget.saveWithoutId(targetTag);
        targetTag.remove("Passengers");
        return targetTag;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("GEAge", tickCount);
        nbt.putInt("GEDuration", duration);
        nbt.putFloat("GERenderAsItemTime", renderAsItemTime);
        nbt.putBoolean("GETargetSpawned", targetSpawned);
        nbt.putBoolean("GETurningBack", turningBack);
        nbt.putBoolean("GEStartSoundPlayed", startSoundPlayed);
        nbt.putBoolean("GERevertSoundPlayed", revertSoundPlayed);
        if (!sourceItem.isEmpty()) {
            nbt.put("GESourceItem", sourceItem.save(registryAccess()));
        }
        if (sourceEntityNbt != null) {
            nbt.put("GESourceEntity", sourceEntityNbt.copy());
        }
        if (!sourceEntityItemView.isEmpty()) {
            nbt.put("GESourceEntityItemView", sourceEntityItemView.save(registryAccess()));
        }
        NBTUtil.put(nbt, "GESourceBlock", sourceBlock, BlockState.CODEC);
        if (sourceBlockEntityNbt != null) {
            nbt.put("GESourceBlockEntity", sourceBlockEntityNbt.copy());
        }
        nbt.putBoolean("GESourceRestored", sourceRestored);
        nbt.putInt("GEActionCooldown", actionCooldown);
        if (followTarget != null && followTargetMode != null) {
            nbt.putUUID("GEFollowTarget", followTarget);
            nbt.putString("GEFollowMode", followTargetMode.name());
        }
        if (hostId != null) {
            nbt.putUUID("GEHost", hostId);
            if (hostFollowOffset != null) {
                nbt.putDouble("GEHostOffsetY", hostFollowOffset.y);
            }
        }
        if (restoreOwnerId != null) {
            nbt.putUUID("GERestoreOwner", restoreOwnerId);
        }
        if (transformationTarget != null && !targetSpawned) {
            CompoundTag targetTag = writeTransformationTargetNbt();
            if (targetTag != null) nbt.put("GETargetEntity", targetTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        tickCount = nbt.getInt("GEAge");
        duration = nbt.getInt("GEDuration");
        renderAsItemTime = nbt.contains("GERenderAsItemTime", Tag.TAG_FLOAT)
                ? nbt.getFloat("GERenderAsItemTime")
                : Math.min(duration / 3.0F, 20.0F);
        targetSpawned = nbt.getBoolean("GETargetSpawned");
        turningBack = nbt.getBoolean("GETurningBack");
        startSoundPlayed = nbt.getBoolean("GEStartSoundPlayed");
        revertSoundPlayed = nbt.getBoolean("GERevertSoundPlayed");
        if (nbt.contains("GESourceItem", Tag.TAG_COMPOUND)) {
            sourceItem = ItemStack.parseOptional(registryAccess(), nbt.getCompound("GESourceItem"));
        }
        sourceEntityNbt = nbt.contains("GESourceEntity", Tag.TAG_COMPOUND)
                ? nbt.getCompound("GESourceEntity").copy() : null;
        if (nbt.contains("GESourceEntityItemView", Tag.TAG_COMPOUND)) {
            sourceEntityItemView = ItemStack.parseOptional(registryAccess(), nbt.getCompound("GESourceEntityItemView"));
        }
        clientSourceEntity = null;
        sourceBlock = NBTUtil.getOptional(nbt, "GESourceBlock", BlockState.CODEC).orElse(null);
        sourceBlockEntityNbt = nbt.contains("GESourceBlockEntity", Tag.TAG_COMPOUND)
                ? nbt.getCompound("GESourceBlockEntity").copy() : null;
        sourceRestored = nbt.getBoolean("GESourceRestored");
        actionCooldown = nbt.getInt("GEActionCooldown");
        followTarget = nbt.hasUUID("GEFollowTarget") ? nbt.getUUID("GEFollowTarget") : null;
        followTargetMode = null;
        if (followTarget != null && nbt.contains("GEFollowMode", Tag.TAG_STRING)) {
            try {
                followTargetMode = FollowTargetMode.valueOf(nbt.getString("GEFollowMode"));
            }
            catch (IllegalArgumentException ignored) {
                followTarget = null;
            }
        }
        hostId = nbt.hasUUID("GEHost") ? nbt.getUUID("GEHost") : null;
        hostFollowOffset = hostId != null && nbt.contains("GEHostOffsetY", Tag.TAG_DOUBLE)
                ? new Vec3(0.0D, nbt.getDouble("GEHostOffsetY"), 0.0D) : null;
        restoreOwnerId = nbt.hasUUID("GERestoreOwner") ? nbt.getUUID("GERestoreOwner") : null;
        if (nbt.contains("GETargetEntity", Tag.TAG_COMPOUND)) {
            transformationTarget = EntityType.create(nbt.getCompound("GETargetEntity"), level()).orElse(null);
        }
    }

    private static final class SpecificTargetGoal extends Goal {
        private final Mob mob;
        private final UUID targetUuid;
        @Nullable private LivingEntity targetEntity;

        private SpecificTargetGoal(Mob mob, UUID targetUuid) {
            this.mob = mob;
            this.targetUuid = targetUuid;
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Nullable
        private LivingEntity getTargetEntity() {
            if (targetEntity != null) {
                if (targetEntity.isAlive()) {
                    return targetEntity;
                }
                targetEntity = null;
            }
            if (mob.level() instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(targetUuid);
                if (entity instanceof LivingEntity livingTarget) {
                    targetEntity = livingTarget;
                }
            }
            return targetEntity;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTargetEntity();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = getTargetEntity();
            return target != null && target.isAlive();
        }

        @Override
        public void start() {
            LivingEntity target = getTargetEntity();
            if (target != null) {
                mob.setTarget(target);
            }
        }

        @Override
        public void tick() {
            LivingEntity target = getTargetEntity();
            if (target != null && mob.getTarget() != target) {
                mob.setTarget(target);
            }
        }
    }
}
