package com.github.standobyte.jojo.customobjects.entity_projectile;

import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.customobjects.EntityStandVisibility;
import com.github.standobyte.jojo.customobjects.EntityWithStandSkin;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;
import com.github.standobyte.jojo.util.objects_java.LazyNullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.event.EventHooks;

public abstract class DamagingEntity extends Projectile implements IEntityWithComplexSpawn, EntityStandVisibility, EntityWithStandSkin {
	protected static final Vec3 DEFAULT_POS_OFFSET = new Vec3(0.0D, -0.3D, 0.0D);
	protected float damageFactor = 1F;
	// only used for OwnerBoundProjectileEntity
	protected double speedFactor = 1F;
	protected LivingEntity livingEntityOwner = null;
	protected LivingEntity powerUser = null;
	protected Supplier<StandPower> userStandPower = () -> null;
	protected Supplier<PlayerPower> userPlayerPower = () -> null;
	protected ResourceLocation standType = null;
	protected Optional<ResourceLocation> standSkin = Optional.empty();

	public DamagingEntity(EntityType<? extends DamagingEntity> entityType, @Nullable LivingEntity owner, Level level) {
		this(entityType, level);
		if (owner != null) {
			setOwner(owner);
			setLivingOwner(owner);
			float yRot = owner.getYRot();
			float xRot = owner.getXRot();
			Vec3 pos = getPos(owner, 1.0F, yRot, xRot);
			setPos(pos.x, pos.y, pos.z);
			setRot(yRot, xRot);
		}
	}

	public DamagingEntity(EntityType<? extends DamagingEntity> entityType, Level level) {
		super(entityType, level);
	}

	public void setShootingPosOf(LivingEntity entity) {
		float yRot = entity.getYRot();
		float xRot = entity.getXRot();
		Vec3 pos = getPos(entity, 1.0F, yRot, xRot);
		setPos(pos.x, pos.y, pos.z);
		setRot(yRot, xRot);
	}

	protected final Vec3 getPos(LivingEntity owner, float partialTick, float yRot, float xRot) {
		return owner.getEyePosition(partialTick)
				.add(getOwnerRelativeOffset().add(
						getXRotOffset().xRot(-owner.getXRot() * MathUtil.DEG_TO_RAD))
						.yRot(-yRot * MathUtil.DEG_TO_RAD));
	}

	protected Vec3 getOwnerRelativeOffset() {
		return DEFAULT_POS_OFFSET;
	}

	protected Vec3 getXRotOffset() {
		return Vec3.ZERO;
	}

	@Override
	public LivingEntity getOwner() {
		if (livingEntityOwner == null) {
			Entity owner = super.getOwner();
			if (owner == null) {
				return null;
			}
			if (owner instanceof LivingEntity living) {
				setLivingOwner(living);
			}
		}
		return livingEntityOwner;
	}

	protected void setLivingOwner(LivingEntity entity) {
		this.livingEntityOwner = entity;
		this.powerUser = StandUtil.getStandUser(entity);
		this.userStandPower = LazyNullable.of(() -> this.powerUser != null ? StandPower.get(this.powerUser) : null);
		this.userPlayerPower = LazyNullable.of(() -> this.powerUser != null ? PlayerPower.get(this.powerUser) : null);
		StandPower standPower = this.userStandPower.get();
		if (standPower != null) {
			StandType type = standPower.getPowerType();
			if (type != null) {
				this.standType = type.getId();
			}
			this.standSkin = standPower.getSelectedSkin();
		}
	}

	@Override
	public void setOwner(Entity owner) {
		super.setOwner(owner);
		setLivingOwner(owner instanceof LivingEntity living ? living : null);
	}

	@Override
	public void tick() {
		super.tick();
		checkInsideBlocks();
		checkHit();
	}

	@Override
	public void lerpMotion(double x, double y, double z) {
		this.setDeltaMovement(x, y, z);
		if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
			double d0 = Math.sqrt(x * x + z * z);
			// literally the same vanilla shit but the angles are rotated IN THE DIRECTION OF THE MOVEMENT INSTEAD OF BEING FUCKING INVERTED
			this.setXRot(-(float)(Mth.atan2(y, d0) * MathUtil.RAD_TO_DEG));
			this.setYRot(-(float)(Mth.atan2(x, z) * MathUtil.RAD_TO_DEG));
			this.xRotO = this.getXRot();
			this.yRotO = this.getYRot();
			this.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
		}
	}

	protected void checkHit() {
		HitResult[] rayTrace = rayTrace();
		for (HitResult result : rayTrace) {
			if (result.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, result)) {
				onHit(result);
			}
		}
	}

	protected HitResult[] rayTrace() {
		return new HitResult[] { ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity) };
	}

	@Override
	protected void onHitEntity(EntityHitResult entityRayTraceResult) {
		if (!level().isClientSide() && isAlive()) {
			Entity target = entityRayTraceResult.getEntity();
			LivingEntity owner = getOwner();
			boolean entityHurt = hurtTarget(target, owner);
			int prevTargetFireTimer = target.getRemainingFireTicks();
			if (isOnFire()) {
				target.igniteForSeconds(5);
			}
			if (entityHurt) {
				if (owner instanceof StandEntity && target instanceof LivingEntity) {
					LivingEntity standUser = ((StandEntity) owner).getUser();
					if (standUser != null) {
						LivingEntity livingTarget = (LivingEntity) target;
						if (standUser instanceof Player player) {
							livingTarget.setLastHurtByPlayer(player);
							livingTarget.lastHurtByPlayerTime = 100;
						}
						livingTarget.setLastHurtByMob(standUser);
					}
				}
			}
			else {
				target.setRemainingFireTicks(prevTargetFireTimer);
			}
			afterEntityHit(entityRayTraceResult, entityHurt);
		}
		super.onHitEntity(entityRayTraceResult);
	}

	protected boolean checkPvpRules() {
		return true;
	}

	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		return hurtTarget(target, getDamageSource(owner), getDamageAmount());
	}

	protected boolean hurtTarget(Entity target, DamageSource dmgSource, float dmgAmount) {
		return target.hurt(dmgSource, dmgAmount);
	}

	protected DamageSource getDamageSource(LivingEntity owner) {
		ResourceKey<DamageType> damageTypeKey = getDamageTypeKey();
		Vec3 damagePosition = null;
		DamageSource damageSource = new DamageSource(DamageUtil.type(level(), damageTypeKey), this, owner, damagePosition);

		float knockbackMultiplier = knockbackMultiplier();
		if (knockbackMultiplier != 1) {
			DamageSourceModified knockback = (DamageSourceModified) damageSource;
			knockback.jojo_ripples$modifyKnockback(0, knockbackMultiplier);
		}

		return damageSource;
	}

	protected ResourceKey<DamageType> getDamageTypeKey() {
		if (standDamage()) {
			return usesFireDamageType() ? ModDamageTypes.STAND_PROJECTILE_FIRE : ModDamageTypes.STAND_PROJECTILE;
		}
		return ModDamageTypes.MOD_PROJECTILE;
	}

	protected boolean usesFireDamageType() {
		return false;
	}

	protected float knockbackMultiplier() {
		return 1F;
	}

	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {}

	@Override
	protected boolean canHitEntity(Entity entity) {
		if (super.canHitEntity(entity)) {
			LivingEntity owner = getOwner();
			if (owner == null) {
				return true;
			}
			if (entity instanceof LivingEntity) {
				if (entity.is(owner) || owner instanceof StandEntity && entity.is(((StandEntity) owner).getUser())) {
					return canHitOwner();
				}
				else {
					return owner.canAttack((LivingEntity) entity);
				}
			}
			return !(checkPvpRules() && 
					owner instanceof StandEntity stand && !stand.canAttackEntity(entity) || 
					owner instanceof Player player && entity instanceof Player targetPlayer && !player.canHarmPlayer(targetPlayer));
		}
		return false;
	}

	public boolean canHitOwner() {
		return false;
	}

	@Override
	protected void onHitBlock(BlockHitResult blockRayTraceResult) {
		super.onHitBlock(blockRayTraceResult);
		Level level = level();
		if (!level.isClientSide() && isAlive()) {
			BlockPos blockPos = blockRayTraceResult.getBlockPos();
			LivingEntity owner = getOwner();
			boolean brokenBlock = owner != null && !JojoModUtil.canEntityDestroy((ServerLevel) level, blockPos, level.getBlockState(blockPos), owner) ? 
					false
					: destroyBlock(blockRayTraceResult);
			afterBlockHit(blockRayTraceResult, brokenBlock);
		}
	}

	protected boolean destroyBlock(BlockHitResult blockRayTraceResult) {
		Level level = level();
		BlockPos blockPos = blockRayTraceResult.getBlockPos();
		BlockState blockState = level.getBlockState(blockPos);
		Direction face = blockRayTraceResult.getDirection();
		boolean brokenBlock = canBreakBlock(blockPos, blockState);
		if (isFiery() && blockState.isFlammable(level, blockPos, face)) {
			JojoModUtil.blockCatchFire(level, blockPos, blockState, face, getOwner());
			return false;
		}
		if (brokenBlock) {
			LivingEntity ownerOrStandUser = StandUtil.getStandUser(getOwner());
			boolean dropItem = ownerOrStandUser instanceof Player player ? !player.getAbilities().instabuild : true;
			brokenBlock = JojoModUtil.destroyBlock(level, blockPos, dropItem, getOwner());
		}
		return brokenBlock;
	}

	protected boolean canBreakBlock(BlockPos blockPos, BlockState blockState) {
		float hardness = blockState.getDestroySpeed(level(), blockPos);
		return hardness >= 0 && hardness <= getMaxHardnessBreakable();
	}

	protected void afterBlockHit(BlockHitResult blockRayTraceResult, boolean blockDestroyed) {}

	public boolean isFiery() {
		return false;
	}

	public abstract int ticksLifespan();

	protected abstract float getBaseDamage();

	protected float getDamageAmount() {
		float damage = getBaseDamage();
		if (standDamage() || getOwner() instanceof StandEntity) {
			damage *= JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get().floatValue();
		}
		if (debuffsFromStand()) {
			damage *= damageFactor;
		}
		return damage;
	}

	protected float getDamageFinalCalc(float damage) {
		return damage;
	}

	public void setDamageFactor(float damageFactor) {
		this.damageFactor = damageFactor;
	}

	public float getDamageFactor() {
		return damageFactor;
	}

	public void setSpeedFactor(double speedFactor) {
		this.speedFactor = speedFactor;
	}

	public double getSpeedFactor() {
		return speedFactor;
	}

	protected boolean debuffsFromStand() {
		return true;
	}

	protected abstract float getMaxHardnessBreakable();

	public abstract boolean standDamage();
	
	@Override
	public boolean onlyVisibleToStandUsers() {
		return standDamage();
	}
	
	
	@Override
	public boolean isInvisible() {
		if (level().isClientSide() && onlyVisibleToStandUsers() && standVisibleTo(ClientProxy.getClientPlayer())) {
			return super.isInvisible();
		}
		return onlyVisibleToStandUsers() || super.isInvisible();
	}
	
	@Override
	public boolean isInvisibleTo(Player player) {
		return isInvisibleToStandViewer(player, super.isInvisible());
	}
	
	@Override
	public boolean displayFireAnimation() {
		if (!level().isClientSide()) {
			return super.displayFireAnimation();
		}
		Player player = ClientProxy.getClientPlayer();
		return super.displayFireAnimation() && (!isInvisible() || !isInvisibleTo(player));
	}
	
	@Override
	public void playSound(SoundEvent sound, float volume, float pitch) {
		if (!this.isSilent()) {
			Level level = level();
			if (!level.isClientSide()) {
				StandUtil.broadcastSound((ServerLevel) level, position(), BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), 
						onlyVisibleToStandUsers(), userStandPower.get(), 
						this.getSoundSource(), volume, pitch);
			}
			else if (!onlyVisibleToStandUsers() || ClientGlobals.canHearStands) {
				StandPower standPower = this.userStandPower.get();
				level.playSound(null, getX(), getY(), getZ(), 
						standPower != null ? ClientsideSoundsHelper.withStandSkin(sound, standPower)
								: ClientsideSoundsHelper.withStandSkin(sound, getStandType(), getStandSkin()), 
						this.getSoundSource(), volume, pitch);
			}
		}
	}

	@Override
	public PlayerTeam getTeam() {
		LivingEntity owner = getOwner();
		return owner == null ? super.getTeam() : owner.getTeam();
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (standDamage() && !DamageUtil.canHurtStands(source)) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	public void moveTo(double x, double y, double z, float yRot, float xRot) {
		Vec3 pos = position();
		this.xo = pos.x;
		this.yo = pos.y;
		this.zo = pos.z;
		this.xOld = pos.x;
		this.yOld = pos.y;
		this.zOld = pos.z;
		setPosRaw(x, y, z);
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
		this.setYRot(yRot);
		this.setXRot(xRot);
		this.reapplyPosition();
	}

	
	public void withStandSkin(ResourceLocation standType, Optional<ResourceLocation> standSkin) {
		this.standType = standType;
		this.standSkin = standSkin != null ? standSkin : Optional.empty();
	}

	public void withStandSkin(Optional<ResourceLocation> standSkin) {
		this.standSkin = standSkin != null ? standSkin : Optional.empty();
	}

	@Override
	public ResourceLocation getStandType() {
		if (standType != null) {
			return standType;
		}
		StandPower userStand = this.userStandPower.get();
		if (userStand != null) {
			StandType standType = userStand.getPowerType();
			if (standType != null) {
				this.standType = standType.getId();
				return this.standType;
			}
		}
		
		return null;
	}
	
	@Override
	public Optional<ResourceLocation> getStandSkin() {
		if (standSkin.isPresent()) {
			return standSkin;
		}
		StandPower userStand = this.userStandPower.get();
		if (userStand != null) {
			this.standSkin = userStand.getSelectedSkin();
		}
		return standSkin;
	}
	

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putFloat("DamageFactor", damageFactor);
		nbt.putDouble("SpeedFactor", speedFactor);
		nbt.putInt("Age", tickCount);
		ResourceLocation standType = getStandType();
		if (standType != null) {
			nbt.putString("StandType", standType.toString());
		}
		getStandSkin().ifPresent(path -> nbt.putString("StandSkin", path.toString()));
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		damageFactor = nbt.getFloat("DamageFactor");
		speedFactor = nbt.getDouble("SpeedFactor");
		tickCount = nbt.getInt("Age");
		if (nbt.contains("StandType")) {
			standType = ResourceLocation.tryParse(nbt.getString("StandType"));
		}
		if (nbt.contains("StandSkin")) {
			standSkin = Optional.ofNullable(ResourceLocation.tryParse(nbt.getString("StandSkin")));
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeInt(tickCount);
		buffer.writeDouble(speedFactor);
		NetworkUtil.writeOptionally(getStandType(), buffer, ResourceLocation.STREAM_CODEC);
		NetworkUtil.writeOptional(getStandSkin(), buffer, ResourceLocation.STREAM_CODEC);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		tickCount = additionalData.readInt();
		speedFactor = additionalData.readDouble();
		NetworkUtil.readOptional(additionalData, ResourceLocation.STREAM_CODEC).ifPresent(readType -> this.standType = readType);
		standSkin = NetworkUtil.readOptional(additionalData, ResourceLocation.STREAM_CODEC);
	}

}
