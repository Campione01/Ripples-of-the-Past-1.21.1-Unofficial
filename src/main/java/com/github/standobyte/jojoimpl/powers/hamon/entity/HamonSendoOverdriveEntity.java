package com.github.standobyte.jojoimpl.powers.hamon.entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class HamonSendoOverdriveEntity extends Entity implements IEntityWithComplexSpawn {
	private static final int WAVE_TICK_LENGTH = 15;
	private static final int WAVE_ADD_TICK = 4;
	private static final double WIDTH = 2.0D;
	public static final float KNOCKBACK_FACTOR = 0.0F;

	@Nullable private Entity user;
	@Nullable private UUID userUUID;
	private int userNetworkId = -1;
	@Nullable private BlockPos targetedBlockPos;
	@Nullable private Direction targetedFace;
	private int gavePoints;
	private float points;
	@Nullable private Direction.Axis axis;
	public float radius;
	public float sparksAngle = (float) Math.PI * 2.0F;
	private int wavesToAdd;
	private int addedWaves;
	private final List<Wave> waves = new LinkedList<>();
	private int tickLifeSpan;
	public float damage;
	private final Map<UUID, Integer> otherWaveHitCooldown = new HashMap<>();

	public HamonSendoOverdriveEntity(Level level, LivingEntity user, Direction.Axis axis) {
		this(ModEntityTypes.SENDO_HAMON_OVERDRIVE.get(), level);
		setUser(user);
		this.axis = axis;
	}

	public HamonSendoOverdriveEntity(EntityType<?> type, Level level) {
		super(type, level);
		setNoGravity(true);
		noPhysics = true;
	}

	private void setUser(@Nullable Entity user) {
		this.user = user;
		this.userUUID = user != null ? user.getUUID() : this.userUUID;
		this.userNetworkId = user != null ? user.getId() : this.userNetworkId;
	}

	public HamonSendoOverdriveEntity setRadius(float radius) {
		this.radius = Math.max(radius, 0.0F);
		return this;
	}

	public HamonSendoOverdriveEntity setWaveDamage(float damage) {
		this.damage = Math.max(damage, 0.0F);
		return this;
	}

	public HamonSendoOverdriveEntity setWavesCount(int waves) {
		this.wavesToAdd = Math.max(waves, 0);
		this.tickLifeSpan = this.wavesToAdd * WAVE_ADD_TICK + WAVE_TICK_LENGTH;
		return this;
	}

	public HamonSendoOverdriveEntity setStatPoints(float points) {
		this.points = Math.max(points, 0.0F);
		return this;
	}

	public void setBlockTarget(BlockPos targetedBlockPos, Direction targetedFace) {
		this.targetedBlockPos = targetedBlockPos;
		this.targetedFace = targetedFace;
	}

	@Nullable
	public BlockPos getTargetedBlockPos() {
		return targetedBlockPos;
	}

	@Nullable
	public Direction getTargetedFace() {
		return targetedFace;
	}

	@Override
	public void tick() {
		if (tickCount <= tickLifeSpan) {
			super.tick();
			tickOtherWaveCooldowns();
			if (tickCount % WAVE_ADD_TICK == 0 && addedWaves++ < wavesToAdd) {
				if (level().isClientSide()) {
					spawnClientSparks();
				}
				else {
					waves.add(new Wave());
					playWaveSound();
				}
			}
			setBoundingBox(makeHurtHitBox(radius));
			if (!level().isClientSide()) {
				Iterator<Wave> iterator = waves.iterator();
				while (iterator.hasNext()) {
					Wave wave = iterator.next();
					wave.tick(this);
					if (wave.remove()) {
						iterator.remove();
					}
				}
			}
			else {
				AABB box = makeHurtHitBox(radius);
				Vec3 cameraPos = ClientProxy.getCameraPos();
				Vec3 soundPos = new Vec3(
						Mth.clamp(cameraPos.x, box.minX, box.maxX),
						Mth.clamp(cameraPos.y, box.minY, box.maxY),
						Mth.clamp(cameraPos.z, box.minZ, box.maxZ));
				HamonSparksLoopSound.playSparkSound(this, soundPos, 1.0F, true);
			}
		}
		else if (!level().isClientSide()) {
			discard();
		}
	}

	private void tickOtherWaveCooldowns() {
		otherWaveHitCooldown.entrySet().removeIf(entry -> entry.getValue() <= 1);
		otherWaveHitCooldown.replaceAll((uuid, ticks) -> ticks - 1);
	}

	private void playWaveSound() {
		if (addedWaves >= wavesToAdd) {
			return;
		}
		Vec3 soundPos = getBoundingBox().getCenter();
		level().playSound(null, soundPos.x, soundPos.y, soundPos.z, ModSoundEvents.HAMON_SPARK.get(),
				SoundSource.AMBIENT, 0.25F, 1.0F + (random.nextFloat() - 0.5F) * 0.15F);
	}

	private void spawnClientSparks() {
		Vec3 center = getBoundingBox().getCenter();
		if (axis == null) {
			return;
		}
		switch (axis) {
		case X:
			spawnSparksCircle(center.add(0.55D, 0.0D, 0.0D), axis, radius);
			spawnSparksCircle(center.add(-0.55D, 0.0D, 0.0D), axis, radius);
			break;
		case Y:
			spawnSparksCircle(center.add(0.0D, 0.55D, 0.0D), axis, radius);
			spawnSparksCircle(center.add(0.0D, -0.55D, 0.0D), axis, radius);
			break;
		case Z:
			spawnSparksCircle(center.add(0.0D, 0.0D, 0.55D), axis, radius);
			spawnSparksCircle(center.add(0.0D, 0.0D, -0.55D), axis, radius);
			break;
		}
	}

	private void spawnSparksCircle(Vec3 center, Direction.Axis axis, float radius) {
		if (!level().isClientSide() || axis == null || radius <= 0.0F) {
			return;
		}
		double minAngle;
		double maxAngle;
		if (axis == Direction.Axis.Y) {
			double yRot = (getYRot() + 90.0F) * MathUtil.DEG_TO_RAD;
			minAngle = -sparksAngle / 2.0D + yRot;
			maxAngle = sparksAngle / 2.0D + yRot;
		}
		else {
			minAngle = -Math.PI;
			maxAngle = Math.PI;
		}
		double step = 0.2D / radius;
		for (double angle = minAngle; angle < maxAngle; angle += Math.PI * step) {
			Vec3 particleVec = switch (axis) {
			case X -> new Vec3(0.0D, Math.sin(angle), Math.cos(angle));
			case Y -> new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
			case Z -> new Vec3(Math.sin(angle), Math.cos(angle), 0.0D);
			};
			Vec3 motion = particleVec.scale(radius / WAVE_TICK_LENGTH);
			CustomParticlesHelper.addSendoHamonOverdriveParticle(level(), ModParticles.HAMON_SPARK.get(), axis,
					center.x, center.y, center.z, motion.x, motion.y, motion.z, WAVE_TICK_LENGTH);
		}
	}

	private AABB getHurtHitbox(int tick) {
		return makeHurtHitBox(radius * (double) (tick + 1) / WAVE_TICK_LENGTH);
	}

	private AABB makeHurtHitBox(double radius) {
		Vec3 center = getBoundingBox().getCenter();
		AABB hitBox = new AABB(center, center);
		if (axis != null) {
			hitBox = switch (axis) {
			case X -> hitBox.inflate(WIDTH * 0.5D, radius, radius);
			case Y -> hitBox.inflate(radius, WIDTH * 0.5D, radius);
			case Z -> hitBox.inflate(radius, radius, WIDTH * 0.5D);
			};
		}
		return hitBox;
	}

	private boolean checkHurtAngle(Entity target) {
		if (targetedFace != null && targetedFace.getAxis() == Direction.Axis.Y) {
			Vec3 sendoCenter = new Vec3(getX(), 0.0D, getZ());
			Vec3 entityPos = new Vec3(target.getX(), 0.0D, target.getZ());
			Vec3 vecToEntity = entityPos.subtract(sendoCenter);
			float angle = MathUtil.yRotDegFromVec(vecToEntity);
			float diff = Mth.wrapDegrees(angle - getYRot()) * MathUtil.DEG_TO_RAD;
			return diff >= -sparksAngle / 2.0F && diff <= sparksAngle / 2.0F;
		}
		return true;
	}

	@Nullable
	private Entity getUser() {
		if (user != null && !user.isAlive()) {
			user = null;
		}
		if (user == null) {
			if (userUUID != null && level() instanceof ServerLevel serverLevel) {
				user = serverLevel.getEntity(userUUID);
			}
			else if (userNetworkId >= 0) {
				user = level().getEntity(userNetworkId);
			}
		}
		return user;
	}

	private boolean canHurtTarget(LivingEntity target) {
		Entity user = getUser();
		return target.isAlive()
				&& EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)
				&& (user == null || !target.is(user));
	}

	private boolean tryOtherWaveHitCooldown(LivingEntity target) {
		UUID uuid = target.getUUID();
		if (otherWaveHitCooldown.getOrDefault(uuid, 0) > 0) {
			return false;
		}
		otherWaveHitCooldown.put(uuid, WAVE_ADD_TICK);
		return true;
	}

	private boolean dealHamonDamage(LivingEntity target) {
		float amount = HamonAbilityHelpers.hamonDamageAmount(target, damage);
		if (amount <= 0.0F) {
			return false;
		}
		DamageSource source = HamonAbilityHelpers.hamonDamageSource(level(), this, getUser());
		if (source instanceof DamageSourceModified modified) {
			modified.jojo_ripples$modifyKnockback(0.0F, KNOCKBACK_FACTOR);
		}
		return HamonAbilityHelpers.hamonHurtWithAmount(target, amount, source);
	}

	private void givePointsToUser() {
		if (level().isClientSide() || points <= 0.0F) {
			return;
		}
		if (gavePoints++ < 6 || gavePoints % 4 == 0) {
			Entity user = getUser();
			if (user instanceof LivingEntity livingUser) {
				PlayerPower.getPowerData(livingUser, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, points * 0.25F);
					hamon.syncOnUpdate(livingUser);
				});
			}
		}
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
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		if (nbt.hasUUID("Owner")) {
			userUUID = nbt.getUUID("Owner");
		}
		userNetworkId = nbt.getInt("OwnerId");
		gavePoints = nbt.getInt("GavePoints");
		points = nbt.getFloat("Points");
		axis = readEnumOrdinal(nbt, "Axis", Direction.Axis.class);
		radius = nbt.getFloat("Radius");
		wavesToAdd = nbt.getInt("WavesToAdd");
		addedWaves = nbt.getInt("WavesAdded");
		waves.clear();
		for (int tick : nbt.getIntArray("Waves")) {
			waves.add(new Wave(tick));
		}
		sparksAngle = nbt.getFloat("SparksAngle");
		tickLifeSpan = nbt.getInt("LifeSpan");
		tickCount = nbt.getInt("Age");
		damage = nbt.getFloat("Damage");
		if (nbt.contains("TargetedBlock")) {
			targetedBlockPos = BlockPos.of(nbt.getLong("TargetedBlock"));
		}
		targetedFace = readEnumOrdinal(nbt, "TargetedFace", Direction.class);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		if (userUUID != null) {
			nbt.putUUID("Owner", userUUID);
		}
		nbt.putInt("OwnerId", userNetworkId);
		nbt.putInt("GavePoints", gavePoints);
		nbt.putFloat("Points", points);
		writeEnumOrdinal(nbt, "Axis", axis);
		nbt.putFloat("Radius", radius);
		nbt.putInt("WavesToAdd", wavesToAdd);
		nbt.putInt("WavesAdded", addedWaves);
		nbt.putIntArray("Waves", waves.stream().mapToInt(wave -> wave.tick).toArray());
		nbt.putFloat("SparksAngle", sparksAngle);
		nbt.putInt("LifeSpan", tickLifeSpan);
		nbt.putInt("Age", tickCount);
		nbt.putFloat("Damage", damage);
		if (targetedBlockPos != null) {
			nbt.putLong("TargetedBlock", targetedBlockPos.asLong());
		}
		writeEnumOrdinal(nbt, "TargetedFace", targetedFace);
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(userNetworkId);
		writeOptionalEnum(buffer, axis);
		buffer.writeFloat(radius);
		buffer.writeVarInt(wavesToAdd);
		buffer.writeVarInt(addedWaves);
		buffer.writeFloat(sparksAngle);
		buffer.writeVarInt(tickLifeSpan);
		buffer.writeVarInt(tickCount);
		buffer.writeBoolean(targetedBlockPos != null);
		if (targetedBlockPos != null) {
			buffer.writeBlockPos(targetedBlockPos);
		}
		writeOptionalEnum(buffer, targetedFace);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		userNetworkId = additionalData.readVarInt();
		axis = readOptionalEnum(additionalData, Direction.Axis.class);
		radius = additionalData.readFloat();
		wavesToAdd = additionalData.readVarInt();
		addedWaves = additionalData.readVarInt();
		sparksAngle = additionalData.readFloat();
		tickLifeSpan = additionalData.readVarInt();
		tickCount = additionalData.readVarInt();
		if (additionalData.readBoolean()) {
			targetedBlockPos = additionalData.readBlockPos();
		}
		targetedFace = readOptionalEnum(additionalData, Direction.class);
		user = null;
	}

	private static <T extends Enum<T>> void writeEnumOrdinal(CompoundTag nbt, String key, @Nullable T value) {
		if (value != null) {
			nbt.putInt(key, value.ordinal());
		}
	}

	@Nullable
	private static <T extends Enum<T>> T readEnumOrdinal(CompoundTag nbt, String key, Class<T> enumClass) {
		if (!nbt.contains(key)) {
			return null;
		}
		int ordinal = nbt.getInt(key);
		T[] values = enumClass.getEnumConstants();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
	}

	private static <T extends Enum<T>> void writeOptionalEnum(RegistryFriendlyByteBuf buffer, @Nullable T value) {
		buffer.writeBoolean(value != null);
		if (value != null) {
			buffer.writeEnum(value);
		}
	}

	@Nullable
	private static <T extends Enum<T>> T readOptionalEnum(RegistryFriendlyByteBuf buffer, Class<T> enumClass) {
		return buffer.readBoolean() ? buffer.readEnum(enumClass) : null;
	}

	private static class Wave {
		private int tick;
		private final Set<UUID> hitEntities = new java.util.HashSet<>();

		private Wave() {
		}

		private Wave(int tick) {
			this.tick = tick;
		}

		private void tick(HamonSendoOverdriveEntity entity) {
			List<LivingEntity> targets = entity.level().getEntitiesOfClass(LivingEntity.class,
					entity.getHurtHitbox(tick), target -> entity.canHurtTarget(target) && !hitEntities.contains(target.getUUID()));
			for (LivingEntity target : targets) {
				if (entity.checkHurtAngle(target)
						&& entity.tryOtherWaveHitCooldown(target)
						&& entity.dealHamonDamage(target)) {
					entity.givePointsToUser();
				}
				hitEntities.add(target.getUUID());
			}
			tick++;
		}

		private boolean remove() {
			if (tick >= WAVE_TICK_LENGTH) {
				hitEntities.clear();
				return true;
			}
			return false;
		}
	}
}
