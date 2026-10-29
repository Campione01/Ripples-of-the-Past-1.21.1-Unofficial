package rotp.addons.heavensdoor.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rotp.addons.heavensdoor.state.WrittenCommandService;

@Mixin(LivingEntity.class)
public abstract class WrittenCommandTimerMixin {
    @Inject(method = "tickEffects", at = @At("HEAD"))
    private void heavensDoor$prepareWrittenTimers(CallbackInfo ci) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (!target.level().isClientSide()) {
            WrittenCommandService.tick(target);
        }
    }

    // Reconcile outside vanilla's effect iterator; this is the only clock for both owned and detached timers.
    @Inject(method = "tickEffects", at = @At("TAIL"))
    private void heavensDoor$tickWrittenTimers(CallbackInfo ci) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (!target.level().isClientSide()) {
            WrittenCommandService.onEffectTick(target);
        }
    }
}
