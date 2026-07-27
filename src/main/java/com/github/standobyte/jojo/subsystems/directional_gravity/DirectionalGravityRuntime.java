package com.github.standobyte.jojo.subsystems.directional_gravity;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.core.JojoMod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class DirectionalGravityRuntime {
	private DirectionalGravityRuntime() {}

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Pre event) {
		DirectionalGravityApi.reconcileEffectiveDirection(
				event.getEntity());
	}
}
