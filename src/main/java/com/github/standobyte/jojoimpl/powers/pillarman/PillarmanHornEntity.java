package com.github.standobyte.jojoimpl.powers.pillarman;

import com.github.standobyte.jojo.init.ModEntityTypes;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PillarmanHornEntity extends PillarmanExtendingBodyPartEntity {
	private static final Vec3 OFFSET = new Vec3(0.0D, 0.15F, 0.0D);

	public PillarmanHornEntity(LivingEntity entity, Level level) {
		super(ModEntityTypes.PILLAR_MAN_HORN.get(), entity, level);
	}

	public PillarmanHornEntity(EntityType<? extends PillarmanHornEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public boolean standDamage() {
		return false;
	}

	@Override
	protected float getBaseDamage() {
		return 0.5F;
	}

	@Override
	protected boolean shouldHurtThroughInvulTicks() {
		return true;
	}

	@Override
	protected float knockbackMultiplier() {
		return 0.0F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 5.0F;
	}

	@Override
	protected float movementSpeed() {
		return 0.4F;
	}

	@Override
	protected int timeAtFullLength() {
		return 4;
	}

	@Override
	protected float retractSpeed() {
		return movementSpeed() * 3F;
	}

	@Override
	public boolean isBodyPart() {
		return true;
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return OFFSET;
	}
}
