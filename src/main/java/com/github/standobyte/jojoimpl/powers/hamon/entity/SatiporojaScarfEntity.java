package com.github.standobyte.jojoimpl.powers.hamon.entity;

import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanExtendingBodyPartEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class SatiporojaScarfEntity extends PillarmanExtendingBodyPartEntity {
	public static final float SCARF_SWING_ENERGY_COST = 600.0F;
	private float yRotOffset;
	private HumanoidArm side = HumanoidArm.RIGHT;
	private boolean gaveHamonPoints;

	public SatiporojaScarfEntity(LivingEntity owner, Level level, HumanoidArm side) {
		super(ModEntityTypes.SATIPOROJA_SCARF.get(), owner, level);
		this.side = side;
		initYRotOffset();
	}

	public SatiporojaScarfEntity(EntityType<? extends SatiporojaScarfEntity> type, Level level) {
		super(type, level);
	}

	private void initYRotOffset() {
		yRotOffset = side == HumanoidArm.RIGHT ? -67.5F : 67.5F;
	}

	@Override
	protected float updateDistance() {
		if (side == HumanoidArm.RIGHT) {
			yRotOffset = Math.min(yRotOffset + 135.0F / ticksLifespan(), 67.5F);
		}
		else {
			yRotOffset = Math.max(yRotOffset - 135.0F / ticksLifespan(), -67.5F);
		}
		return super.updateDistance();
	}

	@Override
	protected float movementSpeed() {
		return 1.5F;
	}

	@Override
	public int ticksLifespan() {
		return 10;
	}

	@Override
	protected Vec3 originOffset(float yRot, float xRot, double distance) {
		return super.originOffset(yRot + yRotOffset, xRot, distance);
	}

	@Override
	protected boolean hurtTarget(Entity target, LivingEntity owner) {
		return target instanceof LivingEntity living && owner != null
				&& HamonAbilityHelpers.hamonHurt(living, owner, 0.6F);
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityHit, boolean entityHurt) {
		if (entityHurt && !gaveHamonPoints) {
			PlayerPower.getPowerData(getOwner(), ModPlayerPowers.HAMON).ifPresent(hamon -> {
				gaveHamonPoints = true;
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, SCARF_SWING_ENERGY_COST);
				if (getOwner() != null) {
					hamon.syncOnUpdate(getOwner());
				}
			});
		}
	}

	@Override
	protected float getBaseDamage() {
		return 0.0F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0.0F;
	}

	@Override
	public boolean standDamage() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putBoolean("LeftArm", side == HumanoidArm.LEFT);
		nbt.putBoolean("PointsGiven", gaveHamonPoints);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		side = nbt.getBoolean("LeftArm") ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		gaveHamonPoints = nbt.getBoolean("PointsGiven");
		initYRotOffset();
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeBoolean(side == HumanoidArm.LEFT);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		side = additionalData.readBoolean() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		initYRotOffset();
	}
}
