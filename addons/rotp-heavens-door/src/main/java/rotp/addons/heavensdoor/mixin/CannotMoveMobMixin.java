package rotp.addons.heavensdoor.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import rotp.addons.heavensdoor.state.CannotMoveRules;

@Mixin(Mob.class)
public abstract class CannotMoveMobMixin {
    // Flying and charging mobs steer by adding to their own velocity in the move control.
    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V"))
    private boolean heavensDoor$noSelfSteering(MoveControl control) {
        return !CannotMoveRules.blocksTravel((Mob) (Object) this);
    }
}
