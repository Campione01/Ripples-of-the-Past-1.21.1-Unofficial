package com.github.standobyte.jojo.client.ui;

import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

// FIXME (wasd screen) mouse wheel scroll
// FIXME (wasd screen) when holding Shift/Ctrl, the keys get released and don't work
// fun fact: did you know that if you're holding W/S and X at the same time, you cannot jump?
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public interface ScreenLetsUseWASD {

	public static boolean canUseWhenOpen(Screen screen) {
		return screen instanceof ScreenLetsUseWASD;
	}

	@SubscribeEvent
	public static void onKeyEvent(InputEvent.Key event) {
		Minecraft mc = Minecraft.getInstance();
		if (ScreenLetsUseWASD.canUseWhenOpen(mc.screen)) {
			InputConstants.Key key = InputConstants.getKey(event.getKey(), event.getScanCode());
			switch (event.getAction()) {
				case InputConstants.PRESS -> {
					KeyMapping.set(key, true);
					KeyMapping.click(key);
				}
				case InputConstants.RELEASE -> {
					KeyMapping.set(key, false);
				}
			}
		}
	}

	@SubscribeEvent
	public static void keepMovementKeysSynced(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		syncMovementKeys(mc);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void keepMovementKeysSynced(MovementInputUpdateEvent event) {
		Minecraft mc = Minecraft.getInstance();
		if (event.getEntity() == mc.player) {
			syncMovementKeys(mc, event.getInput());
		}
	}

	public static void syncMovementKeys(Minecraft mc) {
		if (mc.player == null || !ScreenLetsUseWASD.canUseWhenOpen(mc.screen)) {
			return;
		}
		syncMovementKeys(mc, mc.player.input);
	}

	private static void syncMovementKeys(Minecraft mc, Input input) {
		if (mc.player == null || !ScreenLetsUseWASD.canUseWhenOpen(mc.screen)) {
			return;
		}
		long window = mc.getWindow().getWindow();
		boolean up = syncKey(mc.options.keyUp, window);
		boolean down = syncKey(mc.options.keyDown, window);
		boolean left = syncKey(mc.options.keyLeft, window);
		boolean right = syncKey(mc.options.keyRight, window);
		boolean jump = syncKey(mc.options.keyJump, window);
		syncKey(mc.options.keySprint, window);
		boolean shift = syncKey(mc.options.keyShift, window);
		applyMovementInput(input, up, down, left, right, jump, shift);
	}

	private static boolean syncKey(KeyMapping keyMapping, long window) {
		InputConstants.Key key = keyMapping.getKey();
		if (key.getType() == InputConstants.Type.KEYSYM) {
			boolean down = InputConstants.isKeyDown(window, key.getValue());
			KeyMapping.set(key, down);
			return down;
		}
		return keyMapping.isDown();
	}

	private static void applyMovementInput(Input input, boolean up, boolean down, boolean left, boolean right, boolean jump, boolean shift) {
		input.up = up;
		input.down = down;
		input.left = left;
		input.right = right;
		input.jumping = jump;
		input.shiftKeyDown = shift;
		input.forwardImpulse = calculateImpulse(up, down);
		input.leftImpulse = calculateImpulse(left, right);
	}

	private static float calculateImpulse(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0.0F;
		}
		return positive ? 1.0F : -1.0F;
	}
}
