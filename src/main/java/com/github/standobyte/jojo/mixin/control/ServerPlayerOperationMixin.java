package com.github.standobyte.jojo.mixin.control;

import java.util.OptionalInt;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.api.control.PlayerOperation;
import com.github.standobyte.jojo.api.control.PlayerOperationPolicies;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerOperationMixin {

	@Inject(
			method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;",
			at = @At("HEAD"),
			cancellable = true)
	private void rotp$interceptMenuOpen(
			@Nullable MenuProvider menuProvider,
			@Nullable Consumer<RegistryFriendlyByteBuf> extraDataWriter,
			CallbackInfoReturnable<OptionalInt> cir) {
		if (menuProvider != null
				&& PlayerOperationPolicies.intercept(
						(ServerPlayer) (Object) this,
						PlayerOperation.MENU_OPEN_STANDARD)) {
			cir.setReturnValue(OptionalInt.empty());
		}
	}
}
