package com.github.standobyte.jojo.mixin.client.controls;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.MouseHandler;

@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {

	@Invoker("onMove")
	void jojo_ripples$onMove(long window, double x, double y);

	@Accessor("xpos")
	void jojo_ripples$setXpos(double x);

	@Accessor("ypos")
	void jojo_ripples$setYpos(double y);
}
