package com.github.standobyte.jojo.api.network;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.api.network.AbilityNetworkDiagnostics.Stage;
import com.github.standobyte.jojo.powersystem.ability.Ability;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

@ApiStatus.Internal
public final class ServerAbilityNetworkDiagnostics {
	private ServerAbilityNetworkDiagnostics() {}

	public static void recordAbility(
			Stage stage,
			@Nullable LivingEntity entity,
			@Nullable Ability ability,
			short key,
			String inputEvent,
			int unreadExtraBytes,
			String detail) {
		if (!AbilityNetworkDiagnostics.isRecordingEnabled()) {
			return;
		}
		DiagnosticsWriteAccess.requireCaller(
				ServerAbilityNetworkDiagnostics.class,
				"com.github.standobyte.jojo.network.c2s.ClAbilityInputPacket$Handler");
		MinecraftServer server = entity != null ? entity.getServer() : null;
		AbilityNetworkDiagnostics.recordServerAbility(
				stage,
				entity,
				ability,
				key,
				inputEvent,
				unreadExtraBytes,
				server != null && server.isSameThread(),
				server,
				detail);
	}
}
