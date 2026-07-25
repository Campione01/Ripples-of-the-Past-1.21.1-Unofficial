package com.github.standobyte.jojo.mixin.stand.ge;

import java.util.Collections;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojoimpl.stands.goldexperience.GEContainerDropGuard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
    @SuppressWarnings("rawtypes")
    @Inject(method = "getRecipesToAwardAndPopExperience", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$keepXpOnGEBlockLifeform(ServerLevel level, Vec3 popVec, CallbackInfoReturnable<List> ci) {
        if (GEContainerDropGuard.shouldKeepItems(this)) {
            ci.setReturnValue(Collections.emptyList());
        }
    }
}
