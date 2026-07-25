package com.github.standobyte.jojoimpl.stands.goldexperience.client;

import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.ClientEntityController;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceEntityLifeshotAbility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class ControllerConsciousness {
	private static ClientConsciousnessEntity consciousnessEntity;
	private static float leftImpulse;
	private static float forwardImpulse;
	private static boolean jumping;
	private static boolean shiftKeyDown;

	private ControllerConsciousness() {}

	public static void onSplitPacket(Vec3 deltaMovement) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null && player.hasEffect(ModStatusEffects.SENSORY_OVERLOAD)) {
			spawnConsciousness(mc, player);
			if (consciousnessEntity != null) {
				consciousnessEntity.lerpMotion(deltaMovement.x, deltaMovement.y, deltaMovement.z);
			}
		}
	}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.level == null || player == null) {
			stop(mc);
			return;
		}

		if (!player.hasEffect(ModStatusEffects.SENSORY_OVERLOAD) || !player.isAlive()) {
			stop(mc);
			return;
		}

		spawnConsciousness(mc, player);
		if (consciousnessEntity != null) {
			if (!consciousnessEntity.isAlive()) {
				stop(mc);
				return;
			}
			if (mc.getCameraEntity() != consciousnessEntity) {
				ClientUtil.setCameraEntityPreventShaderSwitch(consciousnessEntity);
			}
			consciousnessEntity.travelWithInput(leftImpulse, forwardImpulse, jumping, shiftKeyDown);
			ClientEntityController.sendLocalPlayerPosition(player);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onInputUpdate(MovementInputUpdateEvent event) {
		if (!isSplitActive()) {
			return;
		}
		Input input = event.getInput();
		leftImpulse = input.leftImpulse;
		forwardImpulse = input.forwardImpulse;
		jumping = input.jumping;
		shiftKeyDown = input.shiftKeyDown;
		ClientEntityController.clearInput(input);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void cancelAttackOrInteraction(InteractionKeyMappingTriggered event) {
		if ((event.isAttack() || event.isUseItem()) && isSplitActive()) {
			event.setCanceled(true);
			event.setSwingHand(false);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void cancelHandsRender(RenderHandEvent event) {
		if (isSplitActive()) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void cancelPlayerRender(RenderPlayerEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		if (isSplitActive()
				&& event.getEntity() == mc.player
				&& consciousnessEntity.shouldCancelPlayerRender()) {
			event.setCanceled(true);
		}
	}

	private static void spawnConsciousness(Minecraft mc, LocalPlayer player) {
		if (isSplitActive() || mc.level == null) {
			return;
		}
		ClientConsciousnessEntity entity = new ClientConsciousnessEntity(mc, mc.level, player);
		entity.setId(GoldExperienceEntityLifeshotAbility.CONSCIOUSNESS_ENTITY_ID);
		entity.absMoveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
		entity.setYHeadRot(player.yHeadRot);
		entity.setYBodyRot(player.yBodyRot);
		entity.setOldPosAndRot();
		mc.level.addEntity(entity);
		consciousnessEntity = entity;
		leftImpulse = 0.0F;
		forwardImpulse = 0.0F;
		jumping = false;
		shiftKeyDown = false;
		player.xxa = 0.0F;
		player.yya = 0.0F;
		player.zza = 0.0F;
		ClientUtil.setCameraEntityPreventShaderSwitch(entity);
	}

	private static boolean isSplitActive() {
		return consciousnessEntity != null && consciousnessEntity.isAlive();
	}

	private static void stop(Minecraft mc) {
		if (consciousnessEntity == null) {
			return;
		}
		Entity entity = consciousnessEntity;
		consciousnessEntity = null;
		if (mc.getCameraEntity() == entity && mc.player != null) {
			ClientUtil.setCameraEntityPreventShaderSwitch(mc.player);
		}
		if (mc.level != null) {
			mc.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
		}
		else {
			entity.discard();
		}
		leftImpulse = 0.0F;
		forwardImpulse = 0.0F;
		jumping = false;
		shiftKeyDown = false;
	}
}
