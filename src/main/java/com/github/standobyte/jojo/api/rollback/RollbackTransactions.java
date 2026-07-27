package com.github.standobyte.jojo.api.rollback;

import java.util.Objects;

import com.github.standobyte.jojo.ServerSavedData;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.subsystems.rollback.RollbackTransactionManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Server-authoritative entry point for rollback transaction negotiation.
 *
 * <p>The ABI foundation is present, but the support matrix is not operational.
 * Consequently every otherwise valid commit is rejected before world
 * mutation.</p>
 */
public final class RollbackTransactions {
	private static final RollbackAdapterRegistry ADAPTERS =
			new RollbackAdapterRegistry();

	private RollbackTransactions() {}

	public static RollbackHandle begin(
			ServerPlayer owner, RollbackCapturePolicy policy) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(policy, "policy");
		MinecraftServer server = owner.getServer();
		if (server == null) {
			return RollbackHandle.rejected(
					owner.getUUID(),
					owner.level().dimension(),
					RollbackReason.SERVER_UNAVAILABLE);
		}
		if (!server.isSameThread()) {
			return RollbackHandle.rejected(
					owner.getUUID(),
					owner.level().dimension(),
					RollbackReason.NOT_SERVER_THREAD);
		}
		ADAPTERS.freeze();
		return owner.serverLevel()
				.getData(ModDataAttachmentTypes.ROLLBACK_TRANSACTIONS.get())
				.begin(owner, policy);
	}

	public static RollbackReadiness readiness(RollbackHandle handle) {
		Objects.requireNonNull(handle, "handle");
		if (handle.initialReason() != RollbackReason.NONE) {
			return RollbackReadiness.invalid(handle.initialReason());
		}
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		RollbackReadiness environmentFailure =
				validateEnvironment(server, handle);
		if (environmentFailure != null) {
			return environmentFailure;
		}
		ServerLevel level = server.getLevel(handle.dimension());
		var attachmentType = ModDataAttachmentTypes.ROLLBACK_TRANSACTIONS.get();
		if (level == null || !level.hasData(attachmentType)) {
			return RollbackReadiness.invalid(RollbackReason.UNKNOWN_HANDLE);
		}
		return level.getData(attachmentType).readiness(handle);
	}

	public static RollbackResult commit(
			ServerPlayer requester, RollbackHandle handle, int ticksBack) {
		Objects.requireNonNull(requester, "requester");
		Objects.requireNonNull(handle, "handle");
		MinecraftServer server = requester.getServer();
		RollbackReadiness environmentFailure =
				validateEnvironment(server, handle);
		if (environmentFailure != null) {
			return RollbackResult.rejected(
					environmentFailure.reason(), environmentFailure);
		}
		if (!requester.getUUID().equals(handle.ownerId())) {
			RollbackReadiness failure =
					RollbackReadiness.invalid(RollbackReason.OWNER_MISMATCH);
			return RollbackResult.rejected(failure.reason(), failure);
		}
		if (!requester.serverLevel().dimension().equals(handle.dimension())) {
			RollbackReadiness failure =
					RollbackReadiness.invalid(RollbackReason.DIMENSION_MISMATCH);
			return RollbackResult.rejected(failure.reason(), failure);
		}
		var attachmentType = ModDataAttachmentTypes.ROLLBACK_TRANSACTIONS.get();
		ServerLevel level = requester.serverLevel();
		if (!level.hasData(attachmentType)) {
			RollbackReadiness failure =
					RollbackReadiness.invalid(RollbackReason.UNKNOWN_HANDLE);
			return RollbackResult.rejected(failure.reason(), failure);
		}
		return level.getData(attachmentType)
				.commit(requester, handle, ticksBack);
	}

	public static void invalidate(
			RollbackHandle handle,
			RollbackInvalidationReason invalidationReason) {
		Objects.requireNonNull(handle, "handle");
		Objects.requireNonNull(invalidationReason, "invalidationReason");
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (validateEnvironment(server, handle) != null) {
			return;
		}
		ServerLevel level = server.getLevel(handle.dimension());
		var attachmentType = ModDataAttachmentTypes.ROLLBACK_TRANSACTIONS.get();
		if (level != null && level.hasData(attachmentType)) {
			level.getData(attachmentType).invalidate(handle, invalidationReason);
		}
	}

	public static RollbackAdapterRegistry adapters() {
		return ADAPTERS;
	}

	public static RollbackSupport support(RollbackCapability capability) {
		return RollbackSupportMatrix.support(
				Objects.requireNonNull(capability, "capability"));
	}

	private static RollbackReadiness validateEnvironment(
			MinecraftServer server, RollbackHandle handle) {
		if (handle.initialReason() != RollbackReason.NONE) {
			return RollbackReadiness.invalid(handle.initialReason());
		}
		if (server == null) {
			return RollbackReadiness.invalid(RollbackReason.SERVER_UNAVAILABLE);
		}
		if (!server.isSameThread()) {
			return RollbackReadiness.invalid(RollbackReason.NOT_SERVER_THREAD);
		}
		if (!ServerSavedData.get(server).getRuntimeEpoch()
				.equals(handle.serverEpoch())) {
			return RollbackReadiness.invalid(
					RollbackReason.SERVER_EPOCH_MISMATCH);
		}
		return null;
	}
}
