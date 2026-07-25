package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.explosion.CustomExplosion;
import com.github.standobyte.jojo.init.ModCustomExplosions;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;

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
			HamonAbilityHelpers.hamonHurtWithAmount(living, damage,
					HamonAbilityHelpers.hamonDamageSource(level, getDirectSourceEntity(), getDirectSourceEntity()));
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
