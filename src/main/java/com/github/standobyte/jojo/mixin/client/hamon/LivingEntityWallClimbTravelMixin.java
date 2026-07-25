package com.github.standobyte.jojo.mixin.client.hamon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojoimpl.powers.hamon.client.HamonWallClimbingClientHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

@Mixin(LivingEntity.class)
public abstract class LivingEntityWallClimbTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$hamonWallClimbTravel(Vec3 travelVector, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && HamonWallClimbingClientHelper.travelWallClimb(player, travelVector)) {
			ci.cancel();
		}
	}
}
