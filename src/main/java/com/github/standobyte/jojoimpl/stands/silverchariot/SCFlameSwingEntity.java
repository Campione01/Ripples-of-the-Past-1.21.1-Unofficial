package com.github.standobyte.jojoimpl.stands.silverchariot;

import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRFlameEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

public class SCFlameSwingEntity extends MRFlameEntity {
	private static final Vec3 OFFSET = new Vec3(0.0, -0.3, 0.75);

	public SCFlameSwingEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.SC_FLAME.get(), shooter, level);
	}

	public SCFlameSwingEntity(EntityType<? extends SCFlameSwingEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public int ticksLifespan() {
		return 20;
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return OFFSET;
	}

	@Override
	protected void afterBlockHit(BlockHitResult blockRayTraceResult, boolean blockDestroyed) {
		if (!level().isClientSide() && EventHooks.canEntityGrief(level(), this)) {
			super.afterBlockHit(blockRayTraceResult, blockDestroyed);
		}
	}
}
