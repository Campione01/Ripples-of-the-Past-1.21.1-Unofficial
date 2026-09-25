package rotp.core.entityattachment;

import java.util.HashSet;
import java.util.Set;

import rotp.core.init.ModDataAttachmentTypes;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * 1.16 PlayerUtilCap.OneTimeNotification: chat hints a player gets only once, kept across death and relog.
 */
public class PlayerOneTimeNotifications implements INBTSerializable<CompoundTag> {
	public static final String POWER_CONTROLS = "POWER_CONTROLS";
	public static final String HIGH_STAND_RANGE = "HIGH_STAND_RANGE";

	private final Set<String> sent = new HashSet<>();

	public static PlayerOneTimeNotifications get(Player player) {
		return player.getData(ModDataAttachmentTypes.PLAYER_ONE_TIME_NOTIFICATIONS);
	}

	/** Sends the message unless it was sent before; true when sent now. */
	public static boolean send(ServerPlayer player, String notification, Component message) {
		if (!get(player).markSent(notification)) {
			return false;
		}
		player.sendSystemMessage(message);
		return true;
	}

	public boolean wasSent(String notification) {
		return sent.contains(notification);
	}

	/** False if it was already marked. */
	public boolean markSent(String notification) {
		return sent.add(notification);
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		for (String notification : sent) {
			tag.putBoolean(notification, true);
		}
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
		sent.clear();
		for (String notification : tag.getAllKeys()) {
			if (tag.getBoolean(notification)) {
				sent.add(notification);
			}
		}
	}
}
