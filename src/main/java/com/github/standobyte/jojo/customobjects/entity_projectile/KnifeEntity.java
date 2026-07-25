package com.github.standobyte.jojo.customobjects.entity_projectile;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracker;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracking;
import com.github.standobyte.jojo.subsystems.itemtracking.KnownItemState;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.stands.goldexperience.GEStuckObjectsState;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class KnifeEntity extends AbstractArrow {
	private static final EntityDataAccessor<Integer> TEX_VARIANT = SynchedEntityData.defineId(KnifeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> TIME_STOP_FLIGHT_TICKS = SynchedEntityData.defineId(KnifeEntity.class, EntityDataSerializers.INT);
	@Nullable private Vec3 timeStopHitMotion;

	public KnifeEntity(EntityType<? extends KnifeEntity> entityType, Level level) {
		super(entityType, level);
	}

	public KnifeEntity(Level level, LivingEntity owner, ItemStack pickupItemStack) {
		super(ModEntityTypes.KNIFE.get(), owner.getX(), owner.getEyeY() - 0.1F, owner.getZ(), level, pickupItemStack, null);
		setOwner(owner);
	}

	public KnifeEntity(Level level, double x, double y, double z, ItemStack pickupItemStack) {
		super(ModEntityTypes.KNIFE.get(), x, y, z, level, pickupItemStack, null);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(TEX_VARIANT, TexVariant.KNIFE.ordinal());
		builder.define(TIME_STOP_FLIGHT_TICKS, 0);
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(ModItems.KNIFE.get());
	}

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return ModSoundEvents.KNIFE_HIT.get();
	}

	public void setTimeStopFlightTicks(int ticks) {
		entityData.set(TIME_STOP_FLIGHT_TICKS, ticks);
	}

	public boolean canMoveInStoppedTime() {
		return getTimeStopFlightTicks() > 0;
	}

	private int getTimeStopFlightTicks() {
		return entityData.get(TIME_STOP_FLIGHT_TICKS);
	}

	@Override
	public void tick() {
		if (!level().isClientSide() && isTimeStoppedAroundThis()) {
			if (getTimeStopFlightTicks() <= 0) {
				setDeltaMovement(Vec3.ZERO);
				return;
			}
			setTimeStopFlightTicks(getTimeStopFlightTicks() - 1);
		}

		super.tick();

		if (!level().isClientSide() && !isTimeStoppedAroundThis() && timeStopHitMotion != null) {
			setDeltaMovement(timeStopHitMotion);
			timeStopHitMotion = null;
		}
		if (!level().isClientSide() && !inGround) {
			cutSoftBlocksOnPath();
		}
	}

	private boolean isTimeStoppedAroundThis() {
		if (level() instanceof ServerLevel serverLevel && serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return state.isTimeStopped(this);
		}
		return false;
	}

	private void cutSoftBlocksOnPath() {
		Vec3 pos = position();
		Vec3 nextPos = pos.add(getDeltaMovement());
		HitResult hitResult = level().clip(new ClipContext(pos, nextPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
		if (hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos blockPos = ((BlockHitResult) hitResult).getBlockPos();
		BlockState blockState = level().getBlockState(blockPos);
		Block block = blockState.getBlock();
		if (block == Blocks.COBWEB) {
			level().destroyBlock(blockPos, true, getOwner());
			setDeltaMovement(getDeltaMovement().scale(0.8D));
		}
		else if (block == Blocks.TRIPWIRE) {
			level().destroyBlock(blockPos, true, getOwner());
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		if (cutSoftBlock(result.getBlockPos())) {
			return;
		}
		super.onHitBlock(result);
	}

	private boolean cutSoftBlock(BlockPos blockPos) {
		BlockState blockState = level().getBlockState(blockPos);
		Block block = blockState.getBlock();
		if (block == Blocks.COBWEB) {
			level().destroyBlock(blockPos, true, getOwner());
			setDeltaMovement(getDeltaMovement().scale(0.8D));
			return true;
		}
		if (block == Blocks.TRIPWIRE) {
			level().destroyBlock(blockPos, true, getOwner());
			return true;
		}
		return false;
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (isTimeStoppedAroundThis()) {
			timeStopHitMotion = getDeltaMovement();
			setDeltaMovement(Vec3.ZERO);
			setTimeStopFlightTicks(0);
			return;
		}

		Entity target = result.getEntity();
		Entity shooter = getOwner();
		DamageSource damageSource = damageSources().arrow(this, shooter != null ? shooter : this);
		int damage = Mth.ceil(Mth.clamp(getDeltaMovement().length() * getBaseDamage(), 0.0D, 2.147483647E9D));
		if (DamageUtil.hurtThroughInvulTicks(target, damageSource, damage)) {
			if (target instanceof LivingEntity living) {
				doPostHurtEffects(living);
			}
			playSound(getHitGroundSoundEvent(), 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
			discard();
		}
	}

	@Override
	protected void doPostHurtEffects(LivingEntity target) {
		Level level = level();
		if (level.isClientSide()) {
			return;
		}
		ItemStack knifeStack = getPickupItem();
		ItemTracker tracker = ItemTracking.getItemTracker(knifeStack, level);
		boolean trackerMoved = tracker != null;
		if (tracker != null) {
			tracker.setAtEntity(knifeStack, target.getId(), level, KnownItemState.STUCK_KNIFE,
					trackerId -> GEStuckObjectsState.get(target).getStuckKnives() > 0);
		}
		if (!trackerMoved) {
			GEStuckObjectsState.get(target).incrementStuckKnife();
		}
	}

	public void setKnifeType(TexVariant type) {
		entityData.set(TEX_VARIANT, type.ordinal());
	}

	public ResourceLocation getKnifeTexture() {
		return getKnifeType().texPath;
	}

	private TexVariant getKnifeType() {
		return TexVariant.byId(entityData.get(TEX_VARIANT));
	}

	@Override
	public void addAdditionalSaveData(CompoundTag compound) {
		super.addAdditionalSaveData(compound);
		compound.putInt("TimeStopTicks", getTimeStopFlightTicks());
		compound.putInt("KnifeType", getKnifeType().ordinal());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		super.readAdditionalSaveData(compound);
		setTimeStopFlightTicks(compound.getInt("TimeStopTicks"));
		setKnifeType(TexVariant.byId(compound.getInt("KnifeType")));
	}

	public enum TexVariant {
		KNIFE(JojoMod.resLoc("textures/entity/projectiles/knife.png")),
		SCALPEL(JojoMod.resLoc("textures/entity/projectiles/knife_scalpel.png")),
		FISH(JojoMod.resLoc("textures/entity/projectiles/knife_fish.png"));

		private final ResourceLocation texPath;

		TexVariant(ResourceLocation texPath) {
			this.texPath = texPath;
		}

		private static TexVariant byId(int id) {
			TexVariant[] values = values();
			return id >= 0 && id < values.length ? values[id] : KNIFE;
		}
	}
}
