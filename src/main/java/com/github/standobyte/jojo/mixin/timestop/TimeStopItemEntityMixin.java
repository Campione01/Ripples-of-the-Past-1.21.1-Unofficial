package com.github.standobyte.jojo.mixin.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

@Mixin(ItemEntity.class)
public abstract class TimeStopItemEntityMixin {
    @Shadow private int pickupDelay;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$freezeItemEntityTickInTimeStop(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (itemEntity.level() instanceof ServerLevel serverLevel) {
            var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
            if (serverLevel.hasData(attachmentType)) {
                TimeStopState state = serverLevel.getData(attachmentType);
                if (state.shouldFreeze(itemEntity)) {
                    if (pickupDelay > 0 && pickupDelay != 32767) {
                        pickupDelay--;
                    }
                    ci.cancel();
                }
            }
        }
    }
}
