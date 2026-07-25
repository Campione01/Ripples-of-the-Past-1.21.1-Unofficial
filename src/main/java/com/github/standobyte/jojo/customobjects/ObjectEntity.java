package com.github.standobyte.jojo.customobjects;

import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.util.functions.NBTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class ObjectEntity extends Entity implements IEntityWithComplexSpawn {
    private Type objectType;
    @Nullable private UUID owner;

    public ObjectEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public ObjectEntity(Level level, Type objectType) {
        this(ModEntityTypes.OBJECT.get(), level);
        this.objectType = objectType;
    }

    @Nullable
    public Type getObjectType() {
        return objectType;
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Override
    protected Component getTypeName() {
        if (objectType != null) {
            return Component.translatable(getType().getDescriptionId() + "." + objectType.name().toLowerCase(Locale.ROOT));
        }
        return super.getTypeName();
    }

    @Override
    public void tick() {
        super.tick();

        if (objectType == null) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        if ((onGround() || getFluidHeight(FluidTags.WATER) > 0.0D || getFluidHeight(FluidTags.LAVA) > 0.0D)
                && tickCount > 100) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        xo = getX();
        yo = getY();
        zo = getZ();
        Vec3 prevMovement = getDeltaMovement();
        double fluidHeight = getEyeHeight() - 0.11111111D;
        if (isInWater() && getFluidHeight(FluidTags.WATER) > fluidHeight) {
            setUnderwaterMovement();
        }
        else if (isInLava() && getFluidHeight(FluidTags.LAVA) > fluidHeight) {
            setUnderLavaMovement();
        }
        else if (!isNoGravity()) {
            setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        }

        if (level().isClientSide()) {
            noPhysics = false;
        }
        else {
            noPhysics = !level().noCollision(this);
            if (noPhysics) {
                moveTowardsClosestSpace(getX(), (getBoundingBox().minY + getBoundingBox().maxY) / 2.0D, getZ());
            }
        }

        Vec3 movement = getDeltaMovement();
        if (!onGround() || movement.horizontalDistanceSqr() > 1.0E-5D || (tickCount + getId()) % 4 == 0) {
            move(MoverType.SELF, movement);
            double friction = 0.98D;
            if (onGround()) {
                BlockPos below = BlockPos.containing(getX(), getY() - 1.0D, getZ());
                friction = level().getBlockState(below).getBlock().getFriction() * 0.98D;
            }

            movement = getDeltaMovement().multiply(friction, 0.98D, friction);
            if (onGround() && movement.y < 0.0D) {
                movement = movement.multiply(1.0D, -0.5D, 1.0D);
            }
            setDeltaMovement(movement);
        }

        boolean movedBlock = Mth.floor(xo) != Mth.floor(getX())
                || Mth.floor(yo) != Mth.floor(getY())
                || Mth.floor(zo) != Mth.floor(getZ());
        int soundCheckInterval = movedBlock ? 2 : 40;
        if (tickCount % soundCheckInterval == 0 && level().getFluidState(blockPosition()).is(FluidTags.LAVA) && !fireImmune()) {
            playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + random.nextFloat() * 0.4F);
        }

        hasImpulse |= updateInWaterStateAndDoFluidPushing();
        if (!level().isClientSide()) {
            double changedMovement = getDeltaMovement().subtract(prevMovement).lengthSqr();
            if (changedMovement > 0.01D) {
                hasImpulse = true;
            }
        }
    }

    private void setUnderwaterMovement() {
        Vec3 movement = getDeltaMovement();
        setDeltaMovement(movement.x * 0.99D, movement.y + (movement.y < 0.06D ? 5.0E-4D : 0.0D), movement.z * 0.99D);
    }

    private void setUnderLavaMovement() {
        Vec3 movement = getDeltaMovement();
        setDeltaMovement(movement.x * 0.95D, movement.y + (movement.y < 0.06D ? 5.0E-4D : 0.0D), movement.z * 0.95D);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (objectType != null) {
            NBTUtil.putEnum(tag, "ObjType", objectType);
        }
        tag.putInt("Age", tickCount);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        objectType = NBTUtil.getEnum(tag, "ObjType", Type.class);
        tickCount = tag.getInt("Age");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(objectType != null);
        if (objectType != null) {
            buffer.writeEnum(objectType);
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        objectType = buffer.readBoolean() ? buffer.readEnum(Type.class) : null;
    }

    public static boolean isToothSourceTag(@Nullable CompoundTag tag) {
        return tag != null
                && ModEntityTypes.OBJECT.getId().toString().equals(tag.getString("id"))
                && tag.contains("ObjType", Tag.TAG_INT)
                && NBTUtil.getEnum(tag, "ObjType", Type.class) == Type.TOOTH;
    }

    public enum Type {
        TOOTH
    }
}
