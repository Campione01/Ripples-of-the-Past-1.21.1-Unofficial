package rotp.core.impl.powers.hamon;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.explosion.CustomExplosion;
import rotp.core.init.ModCustomExplosions;
import rotp.core.init.ModDataAttachmentTypes;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

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

	// 1.16 HamonUtil.hamonChargedCreeperBlast: a Hamon-charged exploder adds a Hamon blast of the same radius.
	public static boolean chargedExploderBlast(Explosion explosion) {
		if (explosion instanceof HamonBlastExplosion || explosion.level.isClientSide()) {
			return false;
		}
		Entity exploder = explosion.getDirectSourceEntity();
		// hasData first so plain explosions do not attach charge state to every exploder
		if (exploder == null || !exploder.hasData(ModDataAttachmentTypes.HAMON_CHARGE)) {
			return false;
		}
		HamonCharge charge = EntityHamonChargeState.get(exploder).getHamonCharge();
		if (charge == null) {
			return false;
		}
		Vec3 center = explosion.center();
		HamonBlastExplosion blast = new HamonBlastExplosion(exploder.level(), exploder, null,
				center.x, center.y, center.z, explosion.radius());
		blast.setHamonDamage(charge.getDamage());
		return CustomExplosion.explode(blast);
	}

	@EventBusSubscriber(modid = JojoMod.MOD_ID)
	public static class Events {

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
			chargedExploderBlast(event.getExplosion());
		}
	}
}
