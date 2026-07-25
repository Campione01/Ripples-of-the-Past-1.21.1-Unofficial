package com.github.standobyte.jojo.worldgen.structure;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PillarmanTempleEngravingEntity extends HangingEntity {
	public static final int ENGRAVING_WIDTH = 48;
	public static final int ENGRAVING_HEIGHT = 48;
	private static final EntityDataAccessor<Integer> DATA_TEXTURE_ID = SynchedEntityData.defineId(
			PillarmanTempleEngravingEntity.class, EntityDataSerializers.INT);

	public PillarmanTempleEngravingEntity(EntityType<? extends PillarmanTempleEngravingEntity> type, Level level) {
		super(type, level);
	}

	public PillarmanTempleEngravingEntity(Level level, BlockPos pos, Direction facing, int textureId) {
		super(ModEntityTypes.PILLARMAN_TEMPLE_ENGRAVING.get(), level, pos);
		setTextureId(textureId);
		setDirection(facing);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_TEXTURE_ID, 0);
	}

	public int getTextureId() {
		return entityData.get(DATA_TEXTURE_ID);
	}

	private void setTextureId(int textureId) {
		entityData.set(DATA_TEXTURE_ID, textureId);
	}

	@Override
	protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
		Vec3 center = Vec3.atCenterOf(pos).relative(direction, -0.46875D);
		Direction.Axis axis = direction.getAxis();
		double xSize = axis == Direction.Axis.X ? 0.0625D : 3.0D;
		double zSize = axis == Direction.Axis.Z ? 0.0625D : 3.0D;
		return AABB.ofSize(center, xSize, 3.0D, zSize);
	}

	@Override
	public boolean survives() {
		return true;
	}

	@Override
	public void dropItem(@Nullable Entity breaker) {
	}

	@Override
	public void playPlacementSound() {
	}

	@Override
	public void moveTo(double x, double y, double z, float yRot, float xRot) {
		setPos(x, y, z);
	}

	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		setPos(x, y, z);
	}

	@Override
	public Vec3 trackingPosition() {
		return Vec3.atLowerCornerOf(pos);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		tag.putByte("facing", (byte) direction.get2DDataValue());
		tag.putInt("TexId", getTextureId());
		super.addAdditionalSaveData(tag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		setTextureId(tag.getInt("TexId"));
		String facingKey = tag.contains("facing") ? "facing" : "Facing";
		direction = Direction.from2DDataValue(tag.getByte(facingKey));
		super.readAdditionalSaveData(tag);
		setDirection(direction);
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		return new ClientboundAddEntityPacket(this, direction.get3DDataValue(), pos);
	}

	@Override
	public void recreateFromPacket(ClientboundAddEntityPacket packet) {
		super.recreateFromPacket(packet);
		setDirection(Direction.from3DDataValue(packet.getData()));
	}

	@Override
	public ItemStack getPickResult() {
		return ItemStack.EMPTY;
	}
}
