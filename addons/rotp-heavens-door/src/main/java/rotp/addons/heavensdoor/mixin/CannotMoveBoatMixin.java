package rotp.addons.heavensdoor.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import rotp.addons.heavensdoor.state.CannotMoveRules;

@Mixin(Boat.class)
public abstract class CannotMoveBoatMixin {
    @Inject(method = "controlBoat", at = @At("HEAD"))
    private void heavensDoor$noPaddling(CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (boat.getControllingPassenger() instanceof LivingEntity rider && CannotMoveRules.blocksSelf(rider)) {
            boat.setInput(false, false, false, false);
        }
    }
}
