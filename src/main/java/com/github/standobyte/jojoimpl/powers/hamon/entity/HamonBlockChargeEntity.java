package com.github.standobyte.jojoimpl.powers.hamon.entity;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojoimpl.powers.hamon.HamonCharge;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class HamonBlockChargeEntity extends Entity implements IEntityWithComplexSpawn {
	private static final EntityDataAccessor<Boolean> CACTUS_EXPLOSION = SynchedEntityData.defineId(
			HamonBlockChargeEntity.class, EntityDataSerializers.BOOLEAN);
	private static final int CACTUS_EXPLOSION_RANGE = 4;

	private BlockPos blockPos = BlockPos.ZERO;
	@Nullable private HamonCharge charge;
	private int age;

	public HamonBlockChargeEntity(Level level, BlockPos blockPos) {
		this(ModEntityTypes.HAMON_BLOCK_CHARGE.get(), level);
		setChargedBlock(blockPos);
	}

	public HamonBlockChargeEntity(EntityType<?> entityType, Level level) {
		super(entityType, level);
		setNoGravity(true);
		noPhysics = true;
	}

	private void setChargedBlock(BlockPos blockPos) {
		this.blockPos = blockPos.immutable();
		setPos(blockPos.getX() + 0.5D, blockPos.getY(), blockPos.getZ() + 0.5D);
	}

	public void setCharge(float tickDamage, int chargeTicks, @Nullable LivingEntity hamonUser, float energySpent) {
		charge = new HamonCharge(tickDamage, chargeTicks, hamonUser, energySpent);
	}

	@Override
	public void tick() {
		super.tick();
		++age;
		if (charge == null || charge.shouldBeRemoved() || level().getBlockState(blockPos).isAir()) {
			discardAndRestoreTripwire();
			return;
		}
		if (!level().isClientSide()) {
			BlockState state = level().getBlockState(blockPos);
			charge.tick(null, blockPos, level(), new AABB(blockPos).inflate(0.1D));
			if (age == 60 && isCactusCharge(state)) {
				doCactusExplosion();
			}
		}
		else {
			HamonSparksLoopSound.playSparkSound(this, Vec3.atCenterOf(blockPos), 1.0F, true);
			CustomParticlesHelper.createHamonSparkParticles(null, getRandomX(0.5D), getRandomY(), getRandomZ(0.5D), 1);
		}
	}

	private boolean isCactusCharge(BlockState state) {
		return state.getBlock() instanceof CactusBlock || state.is(Blocks.POTTED_CACTUS);
	}

	private void doCactusExplosion() {
		if (level().isClientSide()) {
			return;
		}
		damageCactusExplosionTargets(Vec3.atCenterOf(blockPos));
		entityData.set(CACTUS_EXPLOSION, true);
		level().destroyBlock(blockPos, false, this);
		if (charge != null) {
			charge.decreaseTicks(charge.getTicks() + 1);
		}
	}

	private void damageCactusExplosionTargets(Vec3 center) {
		level().getEntities(this, new AABB(blockPos).inflate(CACTUS_EXPLOSION_RANGE)).forEach(entity -> {
			float damage = 0.2F * (3.0F * CACTUS_EXPLOSION_RANGE * CACTUS_EXPLOSION_RANGE
					- (float) entity.distanceToSqr(center));
			if (damage > 0.0F) {
				entity.hurt(level().damageSources().cactus(), damage);
			}
		});
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> parameter) {
		super.onSyncedDataUpdated(parameter);
		if (level().isClientSide() && CACTUS_EXPLOSION.equals(parameter) && entityData.get(CACTUS_EXPLOSION)) {
			for (int i = 0; i < 12; i++) {
				level().addParticle(ParticleTypes.SPLASH, getRandomX(1.0D), getY(0.5D), getRandomZ(1.0D),
						0.0D, 0.0D, 0.0D);
			}
			level().addParticle(ParticleTypes.EXPLOSION, getX(), getY(0.5D), getZ(), 1.0D, 0.0D, 0.0D);
			level().playLocalSound(getX(), getY(0.5D), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
					0.5F, 1.35F + random.nextFloat() * 0.15F, false);
		}
	}

	private void discardAndRestoreTripwire() {
		if (!level().isClientSide() && level().getBlockState(blockPos).is(Blocks.COBWEB)) {
			level().setBlock(blockPos, Blocks.TRIPWIRE.defaultBlockState(), 3);
		}
		discard();
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(CACTUS_EXPLOSION, false);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.contains("BlockPos")) {
			setChargedBlock(BlockPos.of(tag.getLong("BlockPos")));
		}
		age = tag.getInt("Age");
		if (tag.contains("HamonCharge", Tag.TAG_COMPOUND)) {
			charge = HamonCharge.fromNBT(tag.getCompound("HamonCharge"));
		}
		else {
			charge = null;
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putLong("BlockPos", blockPos.asLong());
		tag.putInt("Age", age);
		if (charge != null && !charge.shouldBeRemoved()) {
			tag.put("HamonCharge", charge.toNBT());
		}
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeBlockPos(blockPos);
		buffer.writeVarInt(age);
		buffer.writeBoolean(charge != null && !charge.shouldBeRemoved());
		if (charge != null && !charge.shouldBeRemoved()) {
			buffer.writeNbt(charge.toNBT());
		}
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		setChargedBlock(additionalData.readBlockPos());
		age = additionalData.readVarInt();
		if (additionalData.readBoolean()) {
			CompoundTag tag = additionalData.readNbt();
			charge = tag != null ? HamonCharge.fromNBT(tag) : null;
		}
	}
}
