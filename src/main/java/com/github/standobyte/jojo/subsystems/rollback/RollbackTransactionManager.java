package com.github.standobyte.jojo.subsystems.rollback;

import java.util.UUID;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.ServerSavedData;
import com.github.standobyte.jojo.api.rollback.RollbackCapturePolicy;
import com.github.standobyte.jojo.api.rollback.RollbackHandle;
import com.github.standobyte.jojo.api.rollback.RollbackInvalidationReason;
import com.github.standobyte.jojo.api.rollback.RollbackReadiness;
import com.github.standobyte.jojo.api.rollback.RollbackReason;
import com.github.standobyte.jojo.api.rollback.RollbackResult;
import com.github.standobyte.jojo.api.rollback.RollbackScope;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The only production owner of rollback transaction lifecycle state for one
 * server level.
 */
@ApiStatus.Internal
public final class RollbackTransactionManager {
	private final ServerLevel level;
	private final UUID serverEpoch;
	private final RollbackTransactionLedger ledger;

	public RollbackTransactionManager(ServerLevel level) {
		this.level = level;
		this.serverEpoch =
				ServerSavedData.get(level.getServer()).getRuntimeEpoch();
		this.ledger = new RollbackTransactionLedger(
				level.dimension(),
				serverEpoch,
				level.getServer()::isSameThread,
				level.getGameTime());
	}

	public RollbackHandle begin(
			ServerPlayer owner, RollbackCapturePolicy policy) {
		if (!level.getServer().isSameThread()) {
			return RollbackHandle.rejected(
					owner.getUUID(),
					level.dimension(),
					RollbackReason.NOT_SERVER_THREAD);
		}
		if (owner.serverLevel() != level) {
			return RollbackHandle.rejected(
					owner.getUUID(),
					level.dimension(),
					RollbackReason.DIMENSION_MISMATCH);
		}
		if (policy.scope().min().getY() < level.getMinBuildHeight()
				|| policy.scope().max().getY() >= level.getMaxBuildHeight()) {
			return RollbackHandle.rejected(
					owner.getUUID(),
					level.dimension(),
					RollbackReason.SCOPE_OUT_OF_BOUNDS);
		}
		if (!scopeLoaded(policy.scope())) {
			return RollbackHandle.rejected(
					owner.getUUID(),
					level.dimension(),
					RollbackReason.CHUNK_NOT_LOADED);
		}
		return ledger.begin(owner.getUUID(), policy);
	}

	public RollbackReadiness readiness(RollbackHandle handle) {
		return ledger.readiness(handle);
	}

	public RollbackResult commit(
			ServerPlayer requester, RollbackHandle handle, int ticksBack) {
		RollbackScope scope = ledger.scope(handle);
		if (scope != null && !scopeLoaded(scope)) {
			ledger.invalidate(
					handle, RollbackInvalidationReason.CHUNK_UNLOADED);
		}
		return ledger.commit(
				requester.getUUID(),
				requester.serverLevel().dimension(),
				handle,
				ticksBack);
	}

	public void invalidate(
			RollbackHandle handle,
			RollbackInvalidationReason invalidationReason) {
		ledger.invalidate(handle, invalidationReason);
	}

	public void invalidateOwner(
			UUID ownerId, RollbackInvalidationReason invalidationReason) {
		ledger.invalidateOwner(ownerId, invalidationReason);
	}

	public void tick() {
		ledger.invalidateUnloadedScopes(this::scopeLoaded);
		ledger.tick(level.getGameTime());
	}

	public void close(RollbackInvalidationReason invalidationReason) {
		ledger.invalidateAll(invalidationReason);
	}

	public UUID serverEpoch() {
		return serverEpoch;
	}

	private boolean scopeLoaded(RollbackScope scope) {
		for (long chunkX = scope.minChunkX();
				chunkX <= scope.maxChunkX();
				chunkX++) {
			for (long chunkZ = scope.minChunkZ();
					chunkZ <= scope.maxChunkZ();
					chunkZ++) {
				if (!level.hasChunk((int) chunkX, (int) chunkZ)) {
					return false;
				}
			}
		}
		return true;
	}
}
