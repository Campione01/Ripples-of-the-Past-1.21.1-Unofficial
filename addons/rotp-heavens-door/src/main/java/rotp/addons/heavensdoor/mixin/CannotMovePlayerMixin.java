package rotp.addons.heavensdoor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.player.Player;
import rotp.addons.heavensdoor.state.CannotMoveRules;

@Mixin(Player.class)
public abstract class CannotMovePlayerMixin {
    @ModifyExpressionValue(method = "travel", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z", opcode = Opcodes.GETFIELD))
    private boolean heavensDoor$keepGravity(boolean flying) {
        return flying && !CannotMoveRules.blocksSelf((Player) (Object) this);
    }

    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void heavensDoor$noVoluntaryGliding(CallbackInfoReturnable<Boolean> ci) {
        if (CannotMoveRules.blocksSelf((Player) (Object) this)) {
            ci.setReturnValue(false);
        }
    }
}
