package rotp.addons.lovers.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import rotp.addons.lovers.LoversEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

@Mixin(Player.class)
public abstract class PlayerAcceptedDamageMixin {
    // The accepted amount is read before armor wear and reduction, after
    // vanilla shield and hurt-window checks have already completed.
    @WrapOperation(method = "actuallyHurt", at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;getNewDamage()F",
            ordinal = 0,
            remap = false))
    private float lovers$shareAcceptedDamage(
            DamageContainer container, Operation<Float> original) {
        return LoversEvents.shareAcceptedDamage(
                (Player) (Object) this, container, original.call(container));
    }
}
