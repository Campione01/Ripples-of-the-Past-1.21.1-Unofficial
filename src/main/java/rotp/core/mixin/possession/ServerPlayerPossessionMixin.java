package rotp.core.mixin.possession;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 1.16 ServerPlayerEntityMixin: a possessing player cannot teleport or spectate something else
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPossessionMixin {

	// Spectator-menu teleport; possession itself uses the Set<RelativeMovement> overload
	@Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$possessionCancelTeleport(ServerLevel newLevel, double x, double y, double z,
			float yaw, float pitch, CallbackInfo ci) {
		if (LivingComponentPossession.isPossessingSomeone((ServerPlayer) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$possessionCancelSpectate(@Nullable Entity entityToSpectate, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (entityToSpectate != null && entityToSpectate != self && LivingComponentPossession.isPossessingSomeone(self)) {
			ci.cancel();
		}
	}
}
