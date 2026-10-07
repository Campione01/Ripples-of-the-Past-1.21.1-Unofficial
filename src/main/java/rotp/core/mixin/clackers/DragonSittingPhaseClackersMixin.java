package rotp.core.mixin.clackers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import rotp.core.customobjects.entity_projectile.ClackersEntity;

import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonSittingPhase;

// The 1.16 Clackers were arrows, which a perched dragon sets on fire without taking damage.
@Mixin(AbstractDragonSittingPhase.class)
public abstract class DragonSittingPhaseClackersMixin {
	@WrapOperation(
			method = "onHurt",
			at = @At(value = "CONSTANT", args = "classValue=net/minecraft/world/entity/projectile/AbstractArrow"))
	private boolean jojo$burnClackersLikeArrows(Object directEntity, Operation<Boolean> original) {
		return original.call(directEntity) || directEntity instanceof ClackersEntity;
	}
}
