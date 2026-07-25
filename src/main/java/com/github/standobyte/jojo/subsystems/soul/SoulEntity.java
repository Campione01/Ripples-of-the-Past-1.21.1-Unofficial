package com.github.standobyte.jojo.subsystems.soul;

import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class SoulEntity extends Entity implements IEntityWithComplexSpawn {

	private static final int DEFAULT_LIFESPAN_TICKS = 200;
	private static final int RESOLVE_UP_DURATION = 60;
	private static final double RESOLVE_UP_DRIFT = 0.06;
	private static final double NORMAL_DRIFT = 0.02;

	@Nullable
	private LivingEntity originEntity;
	@Nullable
	private UUID originUUID;
	@Nullable
	private LivingEntity noResolveEntity;
	@Nullable
	private UUID noResolveEntityUUID;
	private int lifeSpan = DEFAULT_LIFESPAN_TICKS;
	private int initialLifeSpan = DEFAULT_LIFESPAN_TICKS;
	private boolean resolveCanLvlUp;

	public SoulEntity(EntityType<? extends SoulEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public SoulEntity(Level level, LivingEntity originEntity, int lifeSpan) {
		this(level, originEntity, lifeSpan, false);
	}

	public SoulEntity(Level level, LivingEntity originEntity, int lifeSpan, boolean resolveCanLvlUp) {
		this(ModEntityTypes.SOUL.get(), level);
		setOriginEntity(originEntity);
		this.lifeSpan = lifeSpan;
		this.initialLifeSpan = lifeSpan;
		this.resolveCanLvlUp = resolveCanLvlUp;
	}

	private void setOriginEntity(@Nullable LivingEntity originEntity) {
		this.originEntity = originEntity;
		this.originUUID = originEntity != null ? originEntity.getUUID() : null;
		if (originEntity != null) {
			copyPosition(originEntity);
			originEntity.setRemainingFireTicks(-20);
		}
	}

	@Nullable
	public LivingEntity getOriginEntity() {
		return originEntity;
	}

	public int getLifeSpan() {
		return lifeSpan;
	}

	public int getInitialLifeSpan() {
		return initialLifeSpan;
	}

	public void setNoResolveToEntity(@Nullable LivingEntity entity) {
		this.noResolveEntity = entity;
		this.noResolveEntityUUID = entity != null ? entity.getUUID() : null;
	}

	@Override
	public void tick() {
		super.tick();
		resolveOriginEntity();
		if (originEntity == null || originEntity.isRemoved()
				|| tickCount > 1 && !originEntity.isDeadOrDying()) {
			restoreOriginCamera();
			discard();
			return;
		}
		boolean inResolveUp = isInResolveUpPhase();
		if (level().isClientSide()) {
			emitClientParticles(inResolveUp);
		}
		if (!level().isClientSide()) {
			if (tickCount == 1) {
				playSpawnSound();
			}
			resolveNoResolveEntity();
			giveResolveToNearbyAllies();
			giveResolveToLookTarget();
			if (lifeSpan-- <= 0) {
				dissipate();
				return;
			}
		}
		originEntity.deathTime = Math.min(originEntity.deathTime, 18);
		double drift = inResolveUp ? RESOLVE_UP_DRIFT : NORMAL_DRIFT;
		Vec3 driftVec = new Vec3(0.0, drift, 0.0);
		setDeltaMovement(getDeltaMovement().scale(0.96).add(driftVec));
		move(MoverType.SELF, getDeltaMovement());
	}

	private void resolveOriginEntity() {
		if (originEntity == null && originUUID != null && level() instanceof ServerLevel serverLevel) {
			Entity entity = serverLevel.getEntity(originUUID);
			if (entity instanceof LivingEntity living) {
				setOriginEntity(living);
			}
		}
	}

	private void resolveNoResolveEntity() {
		if (noResolveEntity == null && noResolveEntityUUID != null && level() instanceof ServerLevel serverLevel) {
			Entity entity = serverLevel.getEntity(noResolveEntityUUID);
			if (entity instanceof LivingEntity living) {
				setNoResolveToEntity(living);
			}
			else {
				noResolveEntityUUID = null;
			}
		}
	}

	private void giveResolveToNearbyAllies() {
		if (originEntity == null) {
			return;
		}
		AABB area = new AABB(getBoundingBox().getCenter(), getBoundingBox().getCenter()).inflate(24.0D);
		for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, area,
				entity -> !entity.is(originEntity)
						&& originEntity.isAlliedTo(entity)
						&& !(entity instanceof StandEntity))) {
			StandPower stand = StandPower.get(entity);
			if (stand != null && giveResolve(entity, stand)) {
				stand.getResolveCounter().soulAddResolveTeammate(stand);
			}
		}
	}

	private void giveResolveToLookTarget() {
		if (originEntity == null) {
			return;
		}
		Vec3 start = position().add(0.0D, getBbHeight() * 0.5D, 0.0D);
		ActionTarget target = HitResultUtil.clip(start, getLookAngle(), 32.0D, 32.0D, level(),
				entity -> entity instanceof LivingEntity living
						&& !StandUtil.getStandUser(living).is(originEntity),
				this, 1.0D);
		if (target.getType() == ActionTarget.TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity living) {
			LivingEntity standUser = StandUtil.getStandUser(living);
			StandPower stand = StandPower.get(standUser);
			if (stand != null && giveResolve(standUser, stand)) {
				stand.getResolveCounter().soulAddResolveLook(stand);
			}
		}
	}

	private boolean giveResolve(LivingEntity target, StandPower targetStandPower) {
		return target != noResolveEntity && targetStandPower.usesResolve()
				&& (resolveCanLvlUp || targetStandPower.getResolveLevel() >= targetStandPower.getMaxResolveLevel());
	}

	private boolean isInResolveUpPhase() {
		return lifeSpan <= RESOLVE_UP_DURATION && lifeSpan > 0;
	}

	private void emitClientParticles(boolean inResolveUp) {
		level().addParticle(ParticleTypes.SOUL,
				getX() + (random.nextDouble() - 0.5) * 0.4,
				getY() + 0.4 + (random.nextDouble() - 0.5) * 0.4,
				getZ() + (random.nextDouble() - 0.5) * 0.4,
				0.0, 0.02, 0.0);
		if (inResolveUp) {
			level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
					getX() + (random.nextDouble() - 0.5) * 0.3,
					getY() + 0.2 + random.nextDouble() * 0.5,
					getZ() + (random.nextDouble() - 0.5) * 0.3,
					0.0, 0.04, 0.0);
		}
	}

	private void playSpawnSound() {
		level().playSound(null, getX(), getY(), getZ(),
				SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 0.6F, 1.0F);
	}

	private void dissipate() {
		if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			for (int i = 0; i < 24; i++) {
				serverLevel.sendParticles(ParticleTypes.SOUL,
						getX(), getY() + 0.5, getZ(),
						1,
						(random.nextDouble() - 0.5) * 0.6,
						random.nextDouble() * 0.4,
						(random.nextDouble() - 0.5) * 0.6,
						0.05);
			}
		}
		level().playSound(null, getX(), getY(), getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.AMBIENT, 0.4F, 1.4F);
		restoreOriginCamera();
		discard();
	}

	public void skipAscension() {
		this.lifeSpan = 0;
	}

	public void handleRotationPacket(float yRot, float xRot) {
		setRot(Mth.wrapDegrees(yRot), Mth.clamp(xRot, -90.0F, 90.0F));
		this.yRotO = getYRot();
		this.xRotO = getXRot();
	}

	private void restoreOriginCamera() {
		if (!level().isClientSide() && originEntity instanceof ServerPlayer serverPlayer) {
			serverPlayer.connection.send(new ClientboundSetCameraPacket(serverPlayer));
		}
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		this.tickCount = tag.getInt("Age");
		this.lifeSpan = tag.contains("LifeSpan") ? tag.getInt("LifeSpan") : DEFAULT_LIFESPAN_TICKS;
		this.initialLifeSpan = tag.contains("InitialLifeSpan") ? tag.getInt("InitialLifeSpan") : this.lifeSpan;
		this.resolveCanLvlUp = tag.getBoolean("Resolve");
		if (tag.hasUUID("Origin")) {
			this.originUUID = tag.getUUID("Origin");
		}
		if (tag.hasUUID("NoResolve")) {
			this.noResolveEntityUUID = tag.getUUID("NoResolve");
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (originEntity != null) {
			originUUID = originEntity.getUUID();
		}
		if (noResolveEntity != null) {
			noResolveEntityUUID = noResolveEntity.getUUID();
		}
		tag.putInt("Age", this.tickCount);
		tag.putInt("LifeSpan", this.lifeSpan);
		tag.putInt("InitialLifeSpan", this.initialLifeSpan);
		tag.putBoolean("Resolve", resolveCanLvlUp);
		if (originUUID != null) {
			tag.putUUID("Origin", originUUID);
		}
		if (noResolveEntityUUID != null) {
			tag.putUUID("NoResolve", noResolveEntityUUID);
		}
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buf) {
		resolveOriginEntity();
		buf.writeInt(this.lifeSpan);
		buf.writeInt(this.originEntity != null ? this.originEntity.getId() : -1);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf buf) {
		this.lifeSpan = buf.readInt();
		this.initialLifeSpan = this.lifeSpan;
		int originId = buf.readInt();
		if (originId >= 0 && level().getEntity(originId) instanceof LivingEntity living) {
			setOriginEntity(living);
		}
	}
}
