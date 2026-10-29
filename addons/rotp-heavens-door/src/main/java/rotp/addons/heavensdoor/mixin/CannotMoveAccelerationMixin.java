package rotp.addons.heavensdoor.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import rotp.addons.heavensdoor.state.CannotMoveRules;

@Mixin(Entity.class)
public abstract class CannotMoveAccelerationMixin {
    @ModifyVariable(method = "moveRelative", at = @At("HEAD"), argsOnly = true)
    private Vec3 heavensDoor$noInputAcceleration(Vec3 input) {
        return (Object) this instanceof LivingEntity subject && CannotMoveRules.blocksTravel(subject)
                ? Vec3.ZERO : input;
    }
}
