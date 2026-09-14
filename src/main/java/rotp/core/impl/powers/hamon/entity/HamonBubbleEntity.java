package rotp.core.impl.powers.hamon.entity;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HamonBubbleEntity extends ModdedProjectileEntity {
	public HamonBubbleEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.HAMON_BUBBLE.get(), shooter, level);
	}

	public HamonBubbleEntity(EntityType<? extends HamonBubbleEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (target instanceof LivingEntity living && owner != null) {
			HamonAbilityHelpers.hamonHurt(living, owner, getBaseDamage());
			return true;
		}
		return false;
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
