package com.github.standobyte.jojo.mixin.stand.ge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojoimpl.stands.goldexperience.GEContainerDropGuard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;

@Mixin(Containers.class)
public abstract class ContainersDropContentsMixin {
    @Inject(method = "dropContents(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/Container;)V",
            at = @At("HEAD"), cancellable = true)
    private static void jojo_ripples$keepItemsOnGEBlockLifeform(Level level, BlockPos pos, Container container, CallbackInfo ci) {
        if (GEContainerDropGuard.shouldKeepItems(container)) {
            ci.cancel();
        }
    }
}
