package com.github.standobyte.jojoimpl.stands.hierophant;

import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.mechanics.resolve.ResolveCounter;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class HGEmeraldEntity extends ModdedProjectileEntity {
	private static final Vec3 EMERALD_OFFSET = new Vec3(0.0D, -0.3D, 0.75D);

	private boolean lowerKnockback;
	private boolean breakBlocks;
	private boolean grantsEmeraldSplashTraining;

	public HGEmeraldEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.HG_EMERALD.get(), shooter, level);
	}

	public HGEmeraldEntity(EntityType<? extends HGEmeraldEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public int ticksLifespan() {
		return 100;
	}

	@Override
	protected float getBaseDamage() {
		return 1.0F;
	}

	@Override
	protected float knockbackMultiplier() {
		return lowerKnockback ? 0.5F : super.knockbackMultiplier();
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return breakBlocks ? 1.5F : 0.0F;
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return EMERALD_OFFSET;
	}

	@Override
	public boolean standDamage() {
		return true;
	}

	public void setLowerKnockback(boolean lowerKnockback) {
		this.lowerKnockback = lowerKnockback;
	}

	public void setBreakBlocks(boolean breakBlocks) {
		this.breakBlocks = breakBlocks;
	}

	public void grantEmeraldSplashTraining() {
		this.grantsEmeraldSplashTraining = true;
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (!level().isClientSide() && entityHurt && grantsEmeraldSplashTraining) {
			Entity target = entityRayTraceResult.getEntity();
			StandPower standPower = userStandPower.get();
			if (standPower != null && ResolveCounter.attackingTargetGivesResolve(target)) {
				var data = standPower.getCurTypeData();
				if (data != null) {
					data.addAbilityLearningProgressPoints(
							HierophantEmeraldSplashAbility.EMERALD_SPLASH_LEARNING_ABILITY,
							HierophantEmeraldSplashAbility.EMERALD_SPLASH_LEARNING_PER_HIT,
							HierophantEmeraldSplashAbility.EMERALD_SPLASH_MAX_TRAINING,
							standPower);
				}
			}
		}
	}
}
