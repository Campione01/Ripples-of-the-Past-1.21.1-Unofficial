package rotp.addons.heavensdoor.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rotp.addons.heavensdoor.client.CannotMoveFovRules;

@Mixin(AbstractClientPlayer.class)
public abstract class CannotMoveFovMixin {
    @ModifyExpressionValue(method = "getFieldOfViewModifier", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double heavensDoor$normalFovSpeed(double original) {
        return CannotMoveFovRules.movementSpeedForFov((AbstractClientPlayer) (Object) this, original);
    }
}
