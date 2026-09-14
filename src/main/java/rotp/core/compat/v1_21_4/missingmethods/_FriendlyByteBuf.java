package rotp.core.compat.v1_21_4.missingmethods;

import net.minecraft.network.FriendlyByteBuf;

public class _FriendlyByteBuf {

	public static void writeContainerId(FriendlyByteBuf buffer, int containerId) {
		buffer.writeVarInt(containerId);
	}
	
	public static int readContainerId(FriendlyByteBuf buffer) {
		return buffer.readVarInt();
	}
}
