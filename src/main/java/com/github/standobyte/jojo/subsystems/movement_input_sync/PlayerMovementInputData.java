package com.github.standobyte.jojo.subsystems.movement_input_sync;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Mirrors player movement keys to tracking clients for animation and controller presentation.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class PlayerMovementInputData {
	public float left;
	public float forward;
	public boolean jumping;
	public boolean shiftKeyDown;
	public boolean sprint;

	public int leftTimer;
	public int forwardTimer;
	public int jumpingTimer;
	public int shiftKeyDownTimer;
	public int sprintTimer;
	public boolean timeStopFloatActive;

	public boolean setValues(float left, float forward, boolean jumping, boolean shiftKeyDown, boolean sprint) {
		boolean changed = false;
		if (this.left != left) {
			this.left = left;
			this.leftTimer = 0;
			changed = true;
		}
		if (this.forward != forward) {
			this.forward = forward;
			this.forwardTimer = 0;
			changed = true;
		}
		if (this.jumping != jumping) {
			this.jumping = jumping;
			this.jumpingTimer = 0;
			changed = true;
		}
		if (this.shiftKeyDown != shiftKeyDown) {
			this.shiftKeyDown = shiftKeyDown;
			this.shiftKeyDownTimer = 0;
			changed = true;
		}
		if (this.sprint != sprint) {
			this.sprint = sprint;
			this.sprintTimer = 0;
			changed = true;
		}
		return changed;
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		PlayerMovementInputData input = get(event.getEntity());
		if (input != null) {
			input.tickTimers();
		}
	}

	public void tickTimers() {
		leftTimer++;
		forwardTimer++;
		jumpingTimer++;
		shiftKeyDownTimer++;
		sprintTimer++;
	}

	public static void sendClientInput(Player player, float left, float forward, boolean jumping, boolean shift, boolean sprint) {
		PlayerMovementInputData input = get(player);
		if (isValidInput(left, forward)
				&& input != null && input.setValues(left, forward, jumping, shift, sprint)) {
			PacketDistributor.sendToServer(new ClPlayerMovementInputPacket(player.getId(), left, forward, jumping, shift, sprint));
		}
	}

	public static void handleServerboundPacket(ClPlayerMovementInputPacket packet, Player sender) {
		if (!acceptsServerboundInput(packet, sender.getId())) {
			return;
		}
		PlayerMovementInputData input = get(sender);
		if (input != null && input.setValues(packet.left(), packet.forward(), packet.jumping(), packet.shift(), packet.sprint())) {
			PacketDistributor.sendToPlayersTrackingEntity(sender, new TrPlayerMovementInputPacket(
					sender.getId(), input.left, input.forward, input.jumping, input.shiftKeyDown, input.sprint));
		}
	}

	public static void handleTrackingClientboundPacket(TrPlayerMovementInputPacket packet) {
		if (!isValidInput(packet.left(), packet.forward())) {
			return;
		}
		Entity entity = ClientProxy.getEntityById(packet.entityId());
		PlayerMovementInputData input = get(entity);
		if (input != null) {
			input.setValues(packet.left(), packet.forward(), packet.jumping(), packet.shift(), packet.sprint());
		}
	}

	static boolean isValidInput(float left, float forward) {
		return Float.isFinite(left) && Float.isFinite(forward)
				&& Math.abs(left) <= 1.0F && Math.abs(forward) <= 1.0F;
	}

	static boolean acceptsServerboundInput(
			ClPlayerMovementInputPacket packet, int senderEntityId) {
		return packet.entityId() == senderEntityId
				&& isValidInput(packet.left(), packet.forward());
	}

	public static PlayerMovementInputData get(Entity player) {
		if (player != null) {
			return player.getData(ModDataAttachmentTypes.SYNCHED_MOVEMENT_INPUT.get());
		}
		return null;
	}
}
