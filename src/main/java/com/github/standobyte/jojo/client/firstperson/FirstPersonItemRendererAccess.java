package com.github.standobyte.jojo.client.firstperson;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public interface FirstPersonItemRendererAccess {
	float jojo_ripples$getEquipProgress(
			InteractionHand hand,
			float partialTick);

	boolean jojo_ripples$vanillaRendersBothMapArms(
			ItemStack mainHandItem);
}
