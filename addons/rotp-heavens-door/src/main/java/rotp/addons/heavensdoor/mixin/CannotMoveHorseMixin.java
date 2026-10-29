package rotp.addons.heavensdoor.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import rotp.addons.heavensdoor.state.CannotMoveRules;

@Mixin(AbstractHorse.class)
public abstract class CannotMoveHorseMixin {
    @Shadow protected float playerJumpPendingScale;

    // A rider's jump waits on the mount until it stands on the ground; a held mount must not keep it for later.
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void heavensDoor$noPendingJump(CallbackInfo ci) {
        if (CannotMoveRules.blocksTravel((AbstractHorse) (Object) this)) {
            playerJumpPendingScale = 0.0F;
        }
    }
}
