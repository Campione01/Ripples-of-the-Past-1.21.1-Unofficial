package com.github.standobyte.jojo.subsystems.movement_input_sync.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.subsystems.movement_input_sync.PlayerMovementInputData;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class PlayerMovementInputClientEvents {
	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof LocalPlayer player) {
			Minecraft mc = Minecraft.getInstance();
			Input vanillaInput = player.input;
			boolean sprint = sprintKeyDown(mc);
			PlayerMovementInputData.sendClientInput(player, vanillaInput.leftImpulse, vanillaInput.forwardImpulse,
					vanillaInput.jumping, vanillaInput.shiftKeyDown, sprint);
		}
	}

	private static boolean sprintKeyDown(Minecraft mc) {
		InputConstants.Key key = mc.options.keySprint.getKey();
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
		}
		return mc.options.keySprint.isDown();
	}
}
