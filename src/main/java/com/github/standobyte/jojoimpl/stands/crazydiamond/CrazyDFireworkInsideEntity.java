package com.github.standobyte.jojoimpl.stands.crazydiamond;

import java.util.OptionalInt;
import java.util.UUID;

import com.github.standobyte.jojo.init.ModEntityTypes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CrazyDFireworkInsideEntity extends FireworkRocketEntity {
	private Entity entityInsideOf;
	private UUID entityInsideOfUUID;

	public CrazyDFireworkInsideEntity(EntityType<? extends FireworkRocketEntity> type, Level level) {
		super(type, level);
	}

	public CrazyDFireworkInsideEntity(Level level, ItemStack item, LivingEntity entity) {
		this(ModEntityTypes.FIREWORK_INSIDE.get(), level);
		setPos(entity.getX(), entity.getY(0.5D), entity.getZ());
		entity.startRiding(this, true);
		this.entityInsideOf = entity;
		this.entityInsideOfUUID = entity.getUUID();
		this.attachedToEntity = entity;
		entityData.set(DATA_ATTACHED_TO_TARGET, OptionalInt.of(entity.getId()));

		int flight = 1;
		if (!item.isEmpty()) {
			entityData.set(DATA_ID_FIREWORKS_ITEM, item.copy());
			Fireworks fireworks = item.get(DataComponents.FIREWORKS);
			if (fireworks != null) {
				flight += fireworks.flightDuration();
			}
		}
		setDeltaMovement(random.nextGaussian() * 0.001D, 0.05D, random.nextGaussian() * 0.001D);
		this.lifetime = flight * 10;
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
			if (entityInsideOf == null && entityInsideOfUUID != null) {
				entityInsideOf = ((ServerLevel) level()).getEntity(entityInsideOfUUID);
			}
			if (entityInsideOf != null && entityInsideOf.getVehicle() != this) {
				entityInsideOf.startRiding(this, true);
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
		return EntityType.FIREWORK_ROCKET.getDescription();
	}
}
