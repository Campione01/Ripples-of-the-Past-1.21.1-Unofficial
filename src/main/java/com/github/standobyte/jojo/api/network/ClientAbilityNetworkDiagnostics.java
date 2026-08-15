package com.github.standobyte.jojo.api.network;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.api.network.AbilityNetworkDiagnostics.Stage;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

@ApiStatus.Internal
public final class ClientAbilityNetworkDiagnostics {
	private ClientAbilityNetworkDiagnostics() {}

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
				ClientAbilityNetworkDiagnostics.class,
				"com.github.standobyte.jojo.network.s2c.TrAbilityUsePacket$Handler");
		Minecraft mc = Minecraft.getInstance();
		AbilityNetworkDiagnostics.recordClientAbility(
				stage,
				entity,
				ability,
				key,
				inputEvent,
				unreadExtraBytes,
				mc.isSameThread(),
				mc.level,
				mc.getConnection(),
				detail);
	}

	public static void recordAction(
			Stage stage,
			@Nullable LivingEntity entity,
			int entityId,
			@Nullable EntityActionInstance action,
			long actionGeneration,
			String detail) {
		if (!AbilityNetworkDiagnostics.isRecordingEnabled()) {
			return;
		}
		DiagnosticsWriteAccess.requireCaller(
				ClientAbilityNetworkDiagnostics.class,
				"com.github.standobyte.jojo.powersystem.entityaction.netcode.ClientEntityActionSyncQueue");
		Minecraft mc = Minecraft.getInstance();
		AbilityNetworkDiagnostics.recordClientAction(
				stage,
				entity,
				entityId,
				action,
				actionGeneration,
				mc.isSameThread(),
				mc.level,
				mc.getConnection(),
				detail);
	}
}
