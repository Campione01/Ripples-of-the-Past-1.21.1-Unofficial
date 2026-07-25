package com.github.standobyte.jojo.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.JojoModLivingVariables;
import com.github.standobyte.jojo.util.constants.EntityEvents;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

@Mixin(LivingEntity.class)
public abstract class LivingEntityClMixin extends Entity {
	public LivingEntityClMixin(EntityType<?> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
	public void jojo_ripples$cancelDyingBodyHurtEvent(byte eventId, CallbackInfo ci) {
		switch (eventId) {
		case EntityEvents.HURT:
		case EntityEvents.HURT_THORNS:
		case EntityEvents.HURT_DROWN:
		case EntityEvents.HURT_ON_FIRE:
		case EntityEvents.HURT_SWEET_BERRY_BUSH:
			if (JojoModLivingVariables.get((LivingEntity) (Entity) this).isDyingBody()) {
				ci.cancel();
			}
			break;
		default:
			break;
		}
	}
}
