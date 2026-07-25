package com.github.standobyte.jojoimpl.powers.hamon.entity;

import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanExtendingBodyPartEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SatiporojaScarfBindingEntity extends PillarmanExtendingBodyPartEntity {
	public SatiporojaScarfBindingEntity(LivingEntity owner, Level level) {
		super(ModEntityTypes.SATIPOROJA_SCARF_BINDING.get(), owner, level);
	}

	public SatiporojaScarfBindingEntity(EntityType<? extends SatiporojaScarfBindingEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			Entity ensnaredEntity = getEntityAttachedTo();
			if (ensnaredEntity == null || !ensnaredEntity.isAlive()) {
				Entity owner = getOwner();
				if (owner instanceof Player player) {
					player.getCooldowns().addCooldown(ModItems.SATIPOROJA_SCARF.get(), 0);
				}
				discard();
			}
		}
	}

	@Override
	protected Vec3 getNextOriginOffset() {
		return Vec3.ZERO;
	}

	@Override
	public int ticksLifespan() {
		return 100;
	}

	@Override
	protected boolean hurtTarget(Entity target, LivingEntity owner) {
		return target instanceof LivingEntity living && owner != null
				&& HamonAbilityHelpers.hamonHurt(living, owner, 0.003F);
	}

	@Override
	protected float getBaseDamage() {
		return 0.0F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0.0F;
	}

	@Override
	protected float movementSpeed() {
		return 0.001F;
	}

	@Override
	public boolean standDamage() {
		return false;
	}
}
