package com.github.standobyte.jojo.client.input;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.input.controlscheme.ClientKey;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.entity_useitem.ClStandClickPacket;
import com.github.standobyte.jojo.subsystems.entity_useitem.ServerSideLivingClick;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class StandVanillaClickInput {

	@SubscribeEvent
	public static void onVanillaClickInput(InteractionKeyMappingTriggered event) {
		int keyCode = event.isAttack() ? InputConstants.MOUSE_BUTTON_LEFT : event.isUseItem() ? InputConstants.MOUSE_BUTTON_RIGHT : -1;
		if (keyCode < 0) {
			return;
		}
		
		if (handleVanillaMappedAbilityInput(event, keyCode)) {
			return;
		}
		
		StandEntity stand = ClientGlobals.playerStandEntity;
		if (stand != null && keyCode == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (standCanRightClickItems && (stand.isManuallyControlled() || !InputHandler.inputsDisabled && ServerSideLivingClick.isEntityHoldingAnItem(stand))) {
				event.setCanceled(true);
				event.setSwingHand(false);
				
				ClientKey key = ClientKey.make(InputConstants.Type.MOUSE, keyCode);
				InputHandler.getInstance().putHeldKeyTimer(key, new HeldKeyTimer(key, false, KeyModifier.NONE));

				HitResult target = Minecraft.getInstance().hitResult;
				PacketDistributor.sendToServer(new ClStandClickPacket(target, key.keyId(), InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND));
			}
		}
	}
	
	private static boolean handleVanillaMappedAbilityInput(InteractionKeyMappingTriggered event, int keyCode) {
		ClientKey key = ClientKey.make(InputConstants.Type.MOUSE, keyCode);
		InputHandler inputHandler = InputHandler.getInstance();
		if (inputHandler == null) {
			return false;
		}
		HeldKeyTimer heldKey = inputHandler.getHeldKeyTimer(key);
		if (heldKey != null) {
			if (heldKey.cancelVanilla) {
				event.setCanceled(true);
				event.setSwingHand(false);
				return true;
			}
			return false;
		}
		
		if (!shouldHandleVanillaMappedAbilityInput(inputHandler)) {
			return false;
		}
		if (inputHandler.input(key, InputConstants.PRESS, 0)) {
			event.setCanceled(true);
			event.setSwingHand(false);
			return true;
		}
		return false;
	}

	private static boolean shouldHandleVanillaMappedAbilityInput(InputHandler inputHandler) {
		if (ClientGlobals.playerStandEntity != null) {
			return true;
		}
		if (inputHandler.curPowerClassToggle == PowerClass.STAND) {
			return true;
		}
		return !inputHandler.hotbarsSelection.isEmpty();
	}
	
	private static boolean standCanRightClickItems = true;
}
