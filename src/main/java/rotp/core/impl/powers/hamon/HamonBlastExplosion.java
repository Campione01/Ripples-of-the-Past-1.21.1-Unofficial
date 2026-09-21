package rotp.core.impl.powers.hamon;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import rotp.core.customobjects.explosion.CustomExplosion;
import rotp.core.init.ModCustomExplosions;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers.HamonAttackProperties;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HamonBlastExplosion extends CustomExplosion {
	private float hamonDamage;

	public HamonBlastExplosion(Level level, double x, double y, double z, float radius) {
		super(level, x, y, z, radius);
	}

	public HamonBlastExplosion(Level level, @Nullable Entity source, @Nullable DamageSource damageSource,
			double x, double y, double z, float radius) {
		super(level, source, damageSource, x, y, z, radius, false, Explosion.BlockInteraction.KEEP);
	}

	public void setHamonDamage(float hamonDamage) {
		this.hamonDamage = hamonDamage;
	}

	@Override
	protected List<Entity> getAffectedEntities(AABB area) {
		Entity source = getDirectSourceEntity();
		return level.getEntitiesOfClass(LivingEntity.class, area,
				EntitySelector.ENTITY_STILL_ALIVE.and(EntitySelector.NO_SPECTATORS)
						.and(entity -> source == null || !entity.is(source)))
				.stream().map(Entity.class::cast).collect(Collectors.toList());
	}

	@Override
	public float getEntityDamageAmount(Entity entity, double impact) {
		return super.getEntityDamageAmount(entity, impact) * hamonDamage;
	}

	@Override
	protected void hurtEntity(Entity entity, float damage, Vec3 knockbackVec) {
		if (entity instanceof LivingEntity living) {
			float scaledDamage = HamonAbilityHelpers.hamonDamageAmount(living, damage);
			if (scaledDamage <= 0.0F) {
				return;
			}
			HamonAbilityHelpers.hamonHurtWithAmount(living, scaledDamage,
					HamonAbilityHelpers.hamonDamageSource(level, getDirectSourceEntity(), getDirectSourceEntity()),
					HamonAttackProperties.NO_SOURCE_ENTITY_HAMON_MULTIPLIER);
		}
	}

	@Override
	protected void playSound() {}

	@Override
	protected void spawnParticles() {}

	@Override
	public ResourceLocation getExplosionType() {
		return ModCustomExplosions.HAMON;
	}
}
