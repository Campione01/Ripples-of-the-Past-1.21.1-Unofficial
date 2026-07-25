package com.github.standobyte.jojoimpl.stands.crazydiamond;

import java.util.UUID;

import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.util.functions.DamageUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CrazyDEyeOfEnderInsideEntity extends EyeOfEnder {
	private Entity entityInsideOf;
	private UUID entityInsideOfUUID;

	public CrazyDEyeOfEnderInsideEntity(EntityType<? extends EyeOfEnder> type, Level level) {
		super(type, level);
	}

	public CrazyDEyeOfEnderInsideEntity(Level level, LivingEntity entity) {
		this(ModEntityTypes.EYE_OF_ENDER_INSIDE.get(), level);
		setPos(entity.getX(), entity.getY(0.5D), entity.getZ());
		entity.startRiding(this, true);
		this.entityInsideOf = entity;
		this.entityInsideOfUUID = entity.getUUID();
	}

	@Override
	public Vec3 getPassengerRidingPosition(Entity passenger) {
		return position().add(0.0D, (getBbHeight() - passenger.getBbHeight()) / 2.0D, 0.0D);
	}

	@Override
	public boolean shouldRiderSit() {
		return false;
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			if (tickCount < 75) {
				if (entityInsideOf == null && entityInsideOfUUID != null) {
					entityInsideOf = ((ServerLevel) level()).getEntity(entityInsideOfUUID);
				}
				if (entityInsideOf != null && entityInsideOf.getVehicle() != this) {
					entityInsideOf.startRiding(this, true);
				}
			}
			else {
				if (isVehicle()) {
					getPassengers().forEach(passenger -> DamageUtil.hurtThroughInvulTicks(passenger,
							DamageUtil.make(level(), ModDamageTypes.EYE_OF_ENDER_SHARDS, this, this), 2.0F));
				}
				playSound(SoundEvents.ENDER_EYE_DEATH, 1.0F, 1.0F);
				level().levelEvent(2003, blockPosition(), 0);
				discard();
				return;
			}
		}
		super.tick();
	}

	@Override
	public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
		return !isVehicle() && super.shouldRender(cameraX, cameraY, cameraZ);
	}

	@Override
	protected Component getTypeName() {
		return EntityType.EYE_OF_ENDER.getDescription();
	}
}
