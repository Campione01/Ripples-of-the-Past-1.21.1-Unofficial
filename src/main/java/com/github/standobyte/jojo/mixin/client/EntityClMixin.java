package com.github.standobyte.jojo.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.github.standobyte.jojo.util.functions.JojoModUtil;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

@Mixin(Entity.class)
public abstract class EntityClMixin {
	@Redirect(method = "isInvisibleTo", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z"))
	private boolean jojo_ripples$possessionAwareSpectatorVisibility(Player player) {
		return JojoModUtil.seesInvisibleAsSpectator(player);
	}
}
