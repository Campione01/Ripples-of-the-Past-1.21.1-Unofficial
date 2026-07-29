package com.github.standobyte.jojo.mixin.client.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityRuntime;
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityRuntime.LocalFrame;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerDirectionalGravityMixin {
	@WrapOperation(method = "aiStep",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/player/"
							+ "LocalPlayer;setDeltaMovement"
							+ "(Lnet/minecraft/world/phys/Vec3;)V"))
	private void jojo_ripples$directionalGravityFlightInput(
			LocalPlayer player, Vec3 vanillaMovement,
			Operation<Void> original) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(player);
		if (direction == Direction.DOWN) {
			original.call(player, vanillaMovement);
			return;
		}
		Vec3 before = player.getDeltaMovement();
		double verticalInput = vanillaMovement.y - before.y;
		Vec3 local = DirectionalGravityTransforms.toLocal(
				direction, before).add(0.0, verticalInput, 0.0);
		original.call(player,
				DirectionalGravityTransforms.toWorld(
						direction, local));
	}

	@WrapOperation(method = "aiStep",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/player/"
							+ "LocalPlayer;sinkInFluid"
							+ "(Lnet/neoforged/neoforge/fluids/"
							+ "FluidType;)V"))
	private void jojo_ripples$directionalGravityFluidSink(
			LocalPlayer player, FluidType fluid,
			Operation<Void> original) {
		try (LocalFrame ignored =
				DirectionalGravityRuntime.enterLocalFrame(player)) {
			original.call(player, fluid);
		}
	}
}
