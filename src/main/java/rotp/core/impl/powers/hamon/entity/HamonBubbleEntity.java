package rotp.core.impl.powers.hamon.entity;

import javax.annotation.Nullable;

import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.client.sound.HamonSparksLoopSound;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class HamonBubbleEntity extends ModdedProjectileEntity {
	public HamonBubbleEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.HAMON_BUBBLE.get(), shooter, level);
	}

	public HamonBubbleEntity(EntityType<? extends HamonBubbleEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && isSparkSoundTick(tickCount, getId())) {
			HamonSparksLoopSound.playSparkSound(this, position(), 0.25F, true);
			// 1.16: one Hamon spark with each crackle
			CustomParticlesHelper.createHamonSparkParticles(this, position(), 1);
		}
	}

	// 1.16: a bubble crackles once every 10 ticks, staggered by entity id
	public static boolean isSparkSoundTick(int tickCount, int entityId) {
		return tickCount % 10 == entityId % 10;
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (target instanceof LivingEntity living && owner != null) {
			// 1.16: hit effects only follow Hamon damage that lands
			return HamonAbilityHelpers.hamonHurt(living, owner, getBaseDamage());
		}
		return false;
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt) {
			giveStrengthPointsForHit(getOwner());
		}
	}

	// 1.16: a bubble hit trains Strength by a quarter of the held tick cost (50, as registered in HamonPowerType).
	static final float HELD_TICK_ENERGY_COST = 50F;

	static void giveStrengthPointsForHit(@Nullable LivingEntity owner) {
		if (owner != null && !owner.level().isClientSide()) {
			PlayerPower.getPowerData(owner, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, HELD_TICK_ENERGY_COST / 4F);
				hamon.syncOnUpdate(owner);
			});
		}
	}

	@Override
	public int ticksLifespan() {
		return 100;
	}

	@Override
	protected float getBaseDamage() {
		return 0.3F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0.0F;
	}

	@Override
	public boolean standDamage() {
		return false;
	}
}
