package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.core.powersystem.standpower.entity.StandEntity;

@Mixin(value = StandEntity.class, remap = false)
public abstract class TimeEraseStandPickMixin {
    @Shadow public abstract LivingEntity getUser();

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$ignoreLocalProjection(CallbackInfoReturnable<Boolean> cir) {
        if (getUser() instanceof KingCrimsonTimeEraseDecoyEntity decoy
                && decoy.getOwner() instanceof Player player && player.isLocalPlayer()) {
            cir.setReturnValue(false);
        }
    }
}
