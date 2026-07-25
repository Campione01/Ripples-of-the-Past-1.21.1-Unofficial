package com.github.standobyte.jojoimpl.stands.magiciansred;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.customobjects.explosion.CustomExplosion;
import com.github.standobyte.jojo.init.ModBlocks;
import com.github.standobyte.jojo.init.ModCustomExplosions;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.mechanics.resolve.ResolveCounter;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRestoreTerrainAbility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

public class MRCrossfireHurricaneEntity extends ModdedProjectileEntity {

	private static final int ORIGINAL_FIRE_SECONDS = 10;
	private static final int ORIGINAL_FIRE_TICKS = ORIGINAL_FIRE_SECONDS * 20;

	private boolean special;
	private float scale = 1F;
	private Vec3 targetPos;

	public MRCrossfireHurricaneEntity(boolean special, LivingEntity shooter, Level level) {
		super(special ? ModEntityTypes.MR_CROSSFIRE_HURRICANE_SPECIAL.get() : ModEntityTypes.MR_CROSSFIRE_HURRICANE.get(), shooter, level);
		this.special = special;
	}

	public MRCrossfireHurricaneEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.MR_CROSSFIRE_HURRICANE.get(), shooter, level);
	}

	public MRCrossfireHurricaneEntity(EntityType<? extends MRCrossfireHurricaneEntity> type, Level level) {
		super(type, level);
	}

	public void setSpecial(@Nullable Vec3 targetPos) {
		this.special = true;
		this.targetPos = targetPos;
		refreshDimensions();
	}

	public boolean isSpecial() {
		return special;
	}

	public void setScale(float scale) {
		this.scale = scale;
		refreshDimensions();
	}

	public float getScale() {
		return scale;
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return super.getDimensions(pose).scale(getScale());
	}

	@Override
	protected void moveProjectile() {
		super.moveProjectile();
		if (targetPos != null) {
			Vec3 movement = getDeltaMovement();
			double velocitySqr = movement.lengthSqr();
			if (velocitySqr <= 0) {
				return;
			}
			Vec3 targetVec = targetPos.subtract(position());
			double targetDistSqr = targetVec.lengthSqr();
			if (targetDistSqr <= 1.0E-7) {
				if (!level().isClientSide()) {
					explode();
					discard();
				}
				return;
			}
			if (velocitySqr < targetDistSqr) {
				Vec3 adjusted = movement.scale(targetDistSqr / velocitySqr).add(targetVec).normalize().scale(Math.sqrt(velocitySqr));
				setDeltaMovement(adjusted);
			}
			else if (!level().isClientSide()) {
				explode();
				discard();
			}
		}
	}

	@Override
	public int ticksLifespan() {
		return 100;
	}

	@Override
	protected float getBaseDamage() {
		return (special ? 2.0F : 6.0F) * scale;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0;
	}

	@Override
	public boolean standDamage() {
		return true;
	}

	@Override
	protected boolean usesFireDamageType() {
		return true;
	}

	@Override
	public void tick() {
		if (isInWaterOrRain()) {
			clearFire();
		}
		else {
			burnBlocksTick();
			super.tick();
		}
	}

	@Override
	public void clearFire() {
		super.clearFire();
		if (level() instanceof ServerLevel serverLevel) {
			JojoModUtil.extinguishFieryStandEntity(this, serverLevel);
		}
	}

	@Override
	public boolean isOnFire() {
		return false;
	}

	@Override
	public boolean isFiery() {
		return true;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return source.is(DamageTypeTags.IS_EXPLOSION) || super.isInvulnerableTo(source);
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	private void burnBlocksTick() {
		Level level = level();
		if (!(level instanceof ServerLevel world) || special || !JojoModUtil.breakingBlocksEnabled(level)) {
			return;
		}
		LivingEntity owner = getOwner();
		if (owner == null) {
			return;
		}

		AABB fireAABB = getBoundingBox().move(getDeltaMovement()).inflate(0.5);
		BlockPos pos1 = BlockPos.containing(fireAABB.minX, fireAABB.minY, fireAABB.minZ);
		BlockPos pos2 = BlockPos.containing(fireAABB.maxX, fireAABB.maxY, fireAABB.maxZ);

		for (int x = pos1.getX(); x <= pos2.getX(); x++) {
			for (int y = pos1.getY(); y <= pos2.getY(); y++) {
				for (int z = pos1.getZ(); z <= pos2.getZ(); z++) {
					BlockPos blockPos = new BlockPos(x, y, z);
					BlockState blockState = level.getBlockState(blockPos);
					if (JojoModUtil.canEntityDestroy(world, blockPos, blockState, owner)
							&& !MRFlameEntity.meltIceAndSnow(level, blockState, blockPos)
							&& blockState.isFlammable(level, blockPos, Direction.UP)) {
						blockState.onCaughtFire(level, blockPos, Direction.UP, getOwner());
						CrazyDRestoreTerrainAbility.rememberBrokenBlock(level, blockPos, blockState,
								Optional.ofNullable(level.getBlockEntity(blockPos)), Collections.emptyList());
						level.removeBlock(blockPos, false);
					}
				}
			}
		}

		setOnFire(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos1.getY(), pos2.getZ(), Direction.DOWN);
		setOnFire(pos1.getX(), pos2.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ(), Direction.UP);
		setOnFire(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos1.getZ(), Direction.NORTH);
		setOnFire(pos1.getX(), pos1.getY(), pos2.getZ(), pos2.getX(), pos2.getY(), pos2.getZ(), Direction.SOUTH);
		setOnFire(pos1.getX(), pos1.getY(), pos1.getZ(), pos1.getX(), pos2.getY(), pos2.getZ(), Direction.WEST);
		setOnFire(pos2.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ(), Direction.EAST);
	}

	private void setOnFire(int x1, int y1, int z1, int x2, int y2, int z2, Direction direction) {
		for (int x = x1; x <= x2; x++) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) {
					BlockPos blockPos = new BlockPos(x, y, z);
					if (level().isEmptyBlock(blockPos)) {
						LivingEntity user = StandUtil.getStandUser(getOwner());
						if (user != null && user.getBoundingBox().intersects(new AABB(blockPos))) {
							return;
						}
						BlockPos blockPosSolid = blockPos.relative(direction);
						BlockState blockState = level().getBlockState(blockPosSolid);
						if (!blockState.getCollisionShape(level(), blockPosSolid).isEmpty()) {
							level().setBlockAndUpdate(blockPos, ModBlocks.MAGICIANS_RED_FIRE.get().getStateForPlacement(level(), blockPos));
						}
					}
				}
			}
		}
	}

	@Override
	protected void afterBlockHit(BlockHitResult blockRayTraceResult, boolean brokenBlock) {
		explode();
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		explode();
	}

	private void explode() {
		if (!level().isClientSide()) {
			DamageSource damageSource = DamageUtil.make(level(), ModDamageTypes.STAND_EXPLOSION_FIRE, this, getOwner());
			CrossfireHurricaneExplosion explosion = new CrossfireHurricaneExplosion(level(), this,
					damageSource,
					getX(), getY(), getZ(),
					(special ? 1.0F : 3.0F) * getScale(), true, Explosion.BlockInteraction.KEEP);
			CustomExplosion.explode(explosion);
		}
	}

	public static class CrossfireHurricaneExplosion extends CustomExplosion {
		@Nullable
		private final MRCrossfireHurricaneEntity sourceProjectile;

		public CrossfireHurricaneExplosion(Level level, double x, double y, double z, float radius) {
			super(level, x, y, z, radius);
			this.sourceProjectile = null;
		}

		public CrossfireHurricaneExplosion(Level level, @Nullable Entity source, @Nullable DamageSource damageSource,
				double x, double y, double z, float radius, boolean fire, Explosion.BlockInteraction blockInteraction) {
			super(level, source, damageSource, x, y, z, radius, fire, blockInteraction);
			this.sourceProjectile = source instanceof MRCrossfireHurricaneEntity crossfire ? crossfire : null;
		}

		@Override
		protected void filterEntities(List<Entity> entities) {
			if (sourceProjectile != null) {
				LivingEntity owner = sourceProjectile.getOwner();
				LivingEntity standUser = owner instanceof StandEntity stand ? stand.getUser() : null;
				boolean canAffectStandUser = standUser != null;
				if (standUser != null) {
					StandPower standPower = StandPower.get(standUser);
					if (standPower != null) {
						canAffectStandUser = ResolveModeEffect.getEffectiveResolveLevel(standUser, standPower) < 4;
					}
				}
				Iterator<Entity> it = entities.iterator();
				while (it.hasNext()) {
					Entity entity = it.next();
					if (entity.is(owner) || (!canAffectStandUser && standUser != null && entity.is(standUser))) {
						it.remove();
					}
				}
			}
		}

		@Override
		protected void hurtEntity(Entity entity, float damage, Vec3 knockbackVec) {
			super.hurtEntity(entity, damage, knockbackVec);

			LivingEntity magiciansRed = sourceProjectile != null ? sourceProjectile.getOwner() : null;
			if (!entity.is(magiciansRed)) {
				DamageUtil.setOnFire(entity, ORIGINAL_FIRE_TICKS, true);
				if (!level.isClientSide() && sourceProjectile != null
						&& ResolveCounter.attackingTargetGivesResolve(entity)) {
					StandPower standPower = sourceProjectile.userStandPower.get();
					var data = standPower != null ? standPower.getCurTypeData() : null;
					if (data != null) {
						data.addAbilityLearningProgressPoints(
								MagiciansRedCrossfireHurricaneAbility.CROSSFIRE_HURRICANE_LEARNING_ABILITY,
								MagiciansRedCrossfireHurricaneAbility.CROSSFIRE_HURRICANE_LEARNING_PER_HIT,
								MagiciansRedCrossfireHurricaneAbility.CROSSFIRE_HURRICANE_MAX_TRAINING,
								standPower);
					}
				}
			}
		}

		@Override
		protected void spawnFire() {
			LivingEntity magiciansRed = sourceProjectile != null ? sourceProjectile.getOwner() : null;
			if (magiciansRed == null || EventHooks.canEntityGrief(level, magiciansRed)) {
				for (BlockPos pos : getToBlow()) {
					if (level.isEmptyBlock(pos)) {
						level.setBlockAndUpdate(pos, ModBlocks.MAGICIANS_RED_FIRE.get().getStateForPlacement(level, pos));
					}
					else if (sourceProjectile == null || !sourceProjectile.special) {
						BlockState blockState = level.getBlockState(pos);
						MRFlameEntity.meltIceAndSnow(level, blockState, pos);
					}
				}
			}
		}

		@Override
		public ResourceLocation getExplosionType() {
			return ModCustomExplosions.CROSSFIRE_HURRICANE;
		}
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeBoolean(special);
		buffer.writeBoolean(targetPos != null);
		if (targetPos != null) {
			buffer.writeDouble(targetPos.x);
			buffer.writeDouble(targetPos.y);
			buffer.writeDouble(targetPos.z);
		}
		buffer.writeFloat(scale);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		special = additionalData.readBoolean();
		if (additionalData.readBoolean()) {
			targetPos = new Vec3(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble());
		}
		else {
			targetPos = null;
		}
		scale = additionalData.readFloat();
		refreshDimensions();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putBoolean("Special", special);
		nbt.putFloat("Scale", scale);
		if (targetPos != null) {
			nbt.putDouble("TargetX", targetPos.x);
			nbt.putDouble("TargetY", targetPos.y);
			nbt.putDouble("TargetZ", targetPos.z);
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		special = nbt.getBoolean("Special");
		scale = nbt.getFloat("Scale");
		if (scale <= 0) {
			scale = 1F;
		}
		if (nbt.contains("TargetX") && nbt.contains("TargetY") && nbt.contains("TargetZ")) {
			targetPos = new Vec3(nbt.getDouble("TargetX"), nbt.getDouble("TargetY"), nbt.getDouble("TargetZ"));
		}
		else {
			targetPos = null;
		}
		refreshDimensions();
	}
}
