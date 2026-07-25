package com.github.standobyte.jojoimpl.powers.hamon.entity;

import java.util.Comparator;

import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;
import com.github.standobyte.jojoimpl.powers.hamon.client.GliderFlightSound;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class LeavesGliderEntity extends Entity implements IEntityWithComplexSpawn {
	public static final float MAX_ENERGY = 200.0F;
	private static final double GRAVITY = -0.01D;
	private static final float MAX_HEALTH = 4.0F;
	private static final int MAX_PASSENGERS = 4;
	private static final Vec3[] OFFSETS = {
			new Vec3(0.0D, 0.0D, 0.625D),
			new Vec3(0.625D, 0.0D, 0.0D),
			new Vec3(-0.625D, 0.0D, 0.0D),
			new Vec3(0.0D, 0.0D, -0.625D)
	};

	private static final EntityDataAccessor<Boolean> IS_FLYING = SynchedEntityData.defineId(LeavesGliderEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> ENERGY = SynchedEntityData.defineId(LeavesGliderEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(LeavesGliderEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Byte> HAMON_USERS_CHARGING = SynchedEntityData.defineId(LeavesGliderEntity.class, EntityDataSerializers.BYTE);

	private BlockState leavesBlock = Blocks.OAK_LEAVES.defaultBlockState();
	private boolean inputLeft;
	private boolean inputRight;
	private float yRotDelta;
	private float passengersHeight;
	private float prevHealth = MAX_HEALTH;

	public LeavesGliderEntity(Level level) {
		this(ModEntityTypes.LEAVES_GLIDER.get(), level);
	}

	public LeavesGliderEntity(EntityType<?> type, Level level) {
		super(type, level);
	}

	private void setIsFlying(boolean flying) {
		entityData.set(IS_FLYING, flying);
	}

	public boolean isFlying() {
		return entityData.get(IS_FLYING);
	}

	public void setEnergy(float energy) {
		entityData.set(ENERGY, Math.max(0.0F, Math.min(MAX_ENERGY, energy)));
	}

	public float getEnergy() {
		return entityData.get(ENERGY);
	}

	public float getHealth() {
		return entityData.get(HEALTH);
	}

	public void setHealth(float health) {
		entityData.set(HEALTH, Mth.clamp(health, 0.0F, getMaxHealth()));
	}

	public float getMaxHealth() {
		return MAX_HEALTH;
	}

	public void setLeavesBlock(BlockState block) {
		if (block != null && !block.isAir()) {
			leavesBlock = block;
		}
	}

	public BlockState getLeavesBlock() {
		return leavesBlock;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			updateFlying();
		}
		moveGlider();
		if (!level().isClientSide()) {
			rechargeFromHamonUsers();
			if (getEnergy() <= 0.0F) {
				setHealth(getHealth() - 0.04F);
			}
			else if (getHealth() < getMaxHealth()) {
				setHealth(getHealth() + 0.1F);
			}
			if (getHealth() <= 0.0F) {
				discard();
			}
		}
		else if (getEnergy() > 0.0F) {
			tickClientChargingFeedback();
		}
	}

	private void updateFlying() {
		boolean wasFlying = isFlying();
		boolean flying = !onGround() && !isInWaterOrBubble();
		if (wasFlying && !flying) {
			setDeltaMovement(Vec3.ZERO);
			ejectPassengers();
		}
		setIsFlying(flying);
	}

	private void moveGlider() {
		if (isFlying() && isControlledByLocalInstance()) {
			updateRotationDelta();
			setYRot(getYRot() + yRotDelta);
			setXRot(0.0F);

			Vec3 horizontal = getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
			if (horizontal.lengthSqr() < 0.25D) {
				horizontal = horizontal.add(Vec3.directionFromRotation(0.0F, getYRot()).scale(0.05D));
			}
			double gravity = GRAVITY * (1 + getPassengers().size());
			setDeltaMovement(horizontal.x, getDeltaMovement().y + gravity, horizontal.z);
			move(MoverType.SELF, getDeltaMovement());
		}
		else {
			Vec3 motion = getDeltaMovement();
			setDeltaMovement(motion.x * 0.95D, Math.max(motion.y - 0.02D, -0.35D), motion.z * 0.95D);
			move(MoverType.SELF, getDeltaMovement());
		}
	}

	private void updateRotationDelta() {
		float delta = 3.5F - getPassengers().size() * 0.5F;
		if (inputLeft && !inputRight) {
			yRotDelta -= delta;
		}
		else if (!inputLeft && inputRight) {
			yRotDelta += delta;
		}
		else if (yRotDelta > 0.0F) {
			yRotDelta = Math.max(yRotDelta - delta * 0.05F, 0.0F);
		}
		else if (yRotDelta < 0.0F) {
			yRotDelta = Math.min(yRotDelta + delta * 0.05F, 0.0F);
		}
	}

	public void setInput(boolean left, boolean right) {
		this.inputLeft = left;
		this.inputRight = right;
	}

	private void rechargeFromHamonUsers() {
		boolean[] charging = new boolean[MAX_PASSENGERS];
		boolean infiniteEnergy = false;
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof Player player && player.getAbilities().instabuild
					&& PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).isPresent()) {
				infiniteEnergy = true;
				addPassengerIndex(charging, passenger);
			}
		}
		if (infiniteEnergy) {
			setEnergy(MAX_ENERGY);
			setHamonChargers(charging);
			return;
		}

		setEnergy(Math.max(getEnergy() - 2.0F, 0.0F));
		float missingEnergy = MAX_ENERGY - getEnergy();
		int hamonUsersWithEnergy = countHamonUsersWithEnergy();
		while (missingEnergy > 0.0F && hamonUsersWithEnergy > 0) {
			float energyFromEach = missingEnergy / hamonUsersWithEnergy;
			for (Entity passenger : getPassengers()) {
				if (!(passenger instanceof LivingEntity living)) {
					continue;
				}
				HamonData hamon = PlayerPower.getPowerData(living, ModPlayerPowers.HAMON).orElse(null);
				if (hamon == null || hamon.getEnergy() <= 0.0F) {
					continue;
				}
				float consumed = consumeEnergy(living, hamon, energyFromEach);
				if (consumed > 0.0F) {
					addPassengerIndex(charging, passenger);
					missingEnergy -= consumed;
				}
				if (consumed < energyFromEach) {
					hamonUsersWithEnergy--;
				}
			}
		}
		setEnergy(MAX_ENERGY - Math.max(missingEnergy, 0.0F));
		setHamonChargers(charging);
	}

	private int countHamonUsersWithEnergy() {
		int count = 0;
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof LivingEntity living
					&& PlayerPower.getPowerData(living, ModPlayerPowers.HAMON)
							.map(hamon -> hamon.getEnergy() > 0.0F)
							.orElse(false)) {
				count++;
			}
		}
		return count;
	}

	private float consumeEnergy(LivingEntity user, HamonData hamon, float energy) {
		float consumed = Math.min(energy, hamon.getEnergy());
		if (consumed <= 0.0F || !hamon.consumeEnergy(consumed, user)) {
			return 0.0F;
		}
		hamon.hamonPointsFromAction(HamonData.HamonStat.CONTROL, consumed);
		return consumed;
	}

	private void addPassengerIndex(boolean[] arr, Entity passenger) {
		int index = getPassengers().indexOf(passenger);
		if (index >= 0 && index < arr.length) {
			arr[index] = true;
		}
	}

	private void setHamonChargers(boolean[] passengerIndices) {
		byte data = 0;
		for (int i = MAX_PASSENGERS - 1; i >= 0; i--) {
			data <<= 1;
			if (passengerIndices[i]) {
				data |= 1;
			}
		}
		entityData.set(HAMON_USERS_CHARGING, data);
	}

	private byte getHamonChargers() {
		return entityData.get(HAMON_USERS_CHARGING);
	}

	private void tickClientChargingFeedback() {
		byte charging = getHamonChargers();
		boolean isBeingCharged = false;
		for (int i = 0; i < Math.min(MAX_PASSENGERS, getPassengers().size()); i++) {
			if (((charging >> i) & 1) == 0) {
				continue;
			}
			Entity passenger = getPassengers().get(i);
			if (passenger instanceof LivingEntity living) {
				CustomParticlesHelper.createHamonGliderChargeParticles(living);
				isBeingCharged = true;
			}
		}

		float energyRatio = Mth.clamp(getEnergy() / MAX_ENERGY, 0.0F, 1.0F);
		if (isBeingCharged || getRandom().nextFloat() < energyRatio * 0.2F) {
			HamonSparksLoopSound.playSparkSound(this, clSoundPos(), energyRatio);
			CustomParticlesHelper.createHamonSparkParticles(this,
					getRandomX(0.5D), getY(1.0D), getRandomZ(0.5D),
					MathUtil.fractionRandomInc(energyRatio * 2.0F));
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (player.isSecondaryUseActive() || this.is(player.getVehicle())) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide()) {
			return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < MAX_PASSENGERS;
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return isVehicle() && getPassengers().get(0) instanceof LivingEntity living ? living : null;
	}

	@Override
	protected void addPassenger(Entity passenger) {
		if (!isVehicle()) {
			setXRot(passenger.getXRot());
			setYRot(passenger.getYRot());
			passenger.setYBodyRot(passenger.getYRot());
			liftFromGround(passenger);
			Vec3 riderMovement = passenger.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
			Vec3 gliderRotVec = Vec3.directionFromRotation(0.0F, getYRot());
			setDeltaMovement(gliderRotVec.scale(Math.max(riderMovement.dot(gliderRotVec), 0.05D)));
		}
		super.addPassenger(passenger);
		updateBbHeight();
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		updateBbHeight();
	}

	private void liftFromGround(Entity passenger) {
		if (!onGround()) {
			return;
		}
		double lift = passenger.getBbHeight() + 1.0D;
		move(MoverType.SELF, new Vec3(0.0D, lift, 0.0D));
	}

	private void updateBbHeight() {
		passengersHeight = isVehicle()
				? (float) getPassengers().stream().max(Comparator.comparingDouble(Entity::getBbHeight)).get().getBbHeight()
				: 0.0F;
		refreshDimensions();
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		EntityDimensions defaultSize = super.getDimensions(pose);
		return EntityDimensions.scalable(defaultSize.width(), defaultSize.height() + passengersHeight)
				.withEyeHeight(defaultSize.eyeHeight());
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction callback) {
		if (hasPassenger(passenger)) {
			int index = getPassengers().indexOf(passenger);
			if (index >= 0 && index < MAX_PASSENGERS) {
				Vec3 offset = OFFSETS[index].yRot(-getYRot() * MathUtil.DEG_TO_RAD);
				callback.accept(passenger,
						getX() + offset.x,
						getY(1.0D) - super.getDimensions(Pose.STANDING).height() - passenger.getBbHeight(),
						getZ() + offset.z);
			}
		}
	}

	@Override
	public boolean hurt(DamageSource damageSource, float amount) {
		if (isInvulnerableTo(damageSource)) {
			return false;
		}
		Entity source = damageSource.getDirectEntity();
		if (source != null && this.is(source.getVehicle())) {
			return false;
		}
		if (!level().isClientSide() && isAlive()) {
			if (source instanceof LivingEntity livingSource) {
				float energy = Math.min(getEnergy(), 100.0F);
				HamonAbilityHelpers.hamonHurt(livingSource, energy * 0.04F, this, null);
				setEnergy(getEnergy() - energy);
			}
			setHealth(getHealth() - amount);
			markHurt();
			return true;
		}
		return true;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return super.isInvulnerableTo(source) || source.is(ModDamageTypes.HAMON);
	}

	@Override
	public boolean shouldRiderSit() {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource source) {
		return false;
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	public boolean isPickable() {
		return !level().isClientSide() || net.minecraft.client.Minecraft.getInstance().player == null
				|| net.minecraft.client.Minecraft.getInstance().player.getRootVehicle() != getRootVehicle();
	}

	@Override
	public boolean canCollideWith(Entity entity) {
		return entity != getControllingPassenger();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(IS_FLYING, false);
		builder.define(ENERGY, MAX_ENERGY);
		builder.define(HEALTH, MAX_HEALTH);
		builder.define(HAMON_USERS_CHARGING, (byte) 0);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		setIsFlying(nbt.getBoolean("Flight"));
		setEnergy(nbt.getFloat("Energy"));
		setHealth(nbt.contains("Health") ? nbt.getFloat("Health") : MAX_HEALTH);
		if (nbt.contains("LeavesBlock")) {
			setLeavesBlock(Block.stateById(nbt.getInt("LeavesBlock")));
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		nbt.putBoolean("Flight", isFlying());
		nbt.putFloat("Energy", getEnergy());
		nbt.putFloat("Health", getHealth());
		nbt.putInt("LeavesBlock", Block.getId(leavesBlock));
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeFloat(getEnergy());
		buffer.writeInt(Block.getId(leavesBlock));
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		setEnergy(additionalData.readFloat());
		setLeavesBlock(Block.stateById(additionalData.readInt()));
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (level().isClientSide()) {
			if (IS_FLYING.equals(key) && isFlying()) {
				GliderFlightSound.play(this);
			}
			else if (HEALTH.equals(key)) {
				float health = getHealth();
				if (health < prevHealth) {
					float diff = prevHealth - health;
					addLeavesParticles(Math.max((int) (diff * 100.0F), 1));
					Vec3 soundPos = clSoundPos();
					SoundEvent hitSound = leavesBlock.getSoundType(level(), blockPosition(), this).getHitSound();
					SoundType soundType = leavesBlock.getSoundType(level(), blockPosition(), this);
					level().playLocalSound(soundPos.x, soundPos.y, soundPos.z, hitSound, getSoundSource(),
							(soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.8F, false);
				}
				prevHealth = health;
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level().isClientSide() && isAlive()) {
			SoundType soundType = leavesBlock.getSoundType(level(), blockPosition(), this);
			Vec3 soundPos = clSoundPos();
			level().playLocalSound(soundPos.x, soundPos.y, soundPos.z, soundType.getBreakSound(),
					SoundSource.NEUTRAL, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F, false);
			addLeavesParticles(200);
		}
		super.remove(reason);
	}

	private Vec3 clSoundPos() {
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		return player != null && player.getVehicle() == this
				? new Vec3(player.getX(), getY(1.0D), player.getZ())
				: new Vec3(getX(), getY(1.0D), getZ());
	}

	private void addLeavesParticles(int count) {
		BlockParticleOption leavesParticle = new BlockParticleOption(ParticleTypes.BLOCK, leavesBlock);
		for (int i = 0; i < count; i++) {
			level().addParticle(leavesParticle, getRandomX(0.5D), getY(1.0D), getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
		}
	}
}
