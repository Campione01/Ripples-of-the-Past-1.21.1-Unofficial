package rotp.addons.ultimate_lifeform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import rotp.addons.ultimate_lifeform.power.UltimateLiquidWalking;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// 1.16 UltimateLifeformLiquidWalkingMixin: the displacement collide() returned is cut short at a fluid surface.
@Mixin(Entity.class)
public abstract class EntityUltimateLiquidWalkingMixin {
    @ModifyVariable(method = "move", ordinal = 1, index = 3, at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 rotp_ul$fluidCollision(Vec3 originalDisplacement) {
        if (!((Object) this instanceof LivingEntity entity)) {
            return originalDisplacement;
        }
        return UltimateLiquidWalking.fluidCollision(entity, originalDisplacement);
    }
}
