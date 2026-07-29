package com.github.standobyte.jojo.api.control;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server-authoritative one-way combat restrictions keyed by addon owner.
 *
 * <p>The API accepts already-resolved server entities. It deliberately has no
 * UUID-to-entity lookup entry point suitable for client commands.</p>
 *
 * <p>Within one owner, reapplying the same lease ID with identical parameters
 * is idempotent and returns the original token. Reusing that ID with different
 * parameters is a conflict. Different lease IDs for the same entity pair are
 * independent and all must be released before attacks are allowed.</p>
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class ControlledEntityCombatLeases {
	private static final Map<ResourceLocation, Owner> OWNERS =
			new LinkedHashMap<>();
	private static final Map<LeaseKey, LeaseRecord> LEASES =
			new LinkedHashMap<>();
	private static final OriginAdapter<Entity> ENTITY_ORIGIN_ADAPTER =
			new OriginAdapter<>() {
				@Override
				public boolean isProjectile(Entity entity) {
					return entity instanceof Projectile;
				}

				@Override
				@Nullable
				public Entity projectileOwner(Entity entity) {
					return entity instanceof Projectile projectile
							? projectile.getOwner()
							: null;
				}

				@Override
				@Nullable
				public AttackOrigin terminalOrigin(Entity entity) {
					if (entity instanceof StandEntity stand) {
						LivingEntity user = stand.getUser();
						StandPower power = user != null
								? StandPower.get(user)
								: null;
						return user != null
								&& power != null
								&& power.getSummonedStandEntity()
										== stand
								? new AttackOrigin(
										user.getUUID(),
										OriginKind.SUMMONED_STAND)
								: null;
					}
					return entity instanceof LivingEntity living
							? new AttackOrigin(
									living.getUUID(),
									OriginKind.SELF)
							: null;
				}
			};

	private ControlledEntityCombatLeases() {}

	public static synchronized Owner register(ResourceLocation ownerKey) {
		Objects.requireNonNull(ownerKey, "ownerKey");
		if (OWNERS.containsKey(ownerKey)) {
			throw new IllegalStateException(
					"Duplicate controlled-entity combat lease owner: "
							+ ownerKey);
		}
		Owner owner = new Owner(ownerKey);
		OWNERS.put(ownerKey, owner);
		return owner;
	}

	public static final class Owner {
		private final ResourceLocation key;

		private Owner(ResourceLocation key) {
			this.key = key;
		}

		public ResourceLocation key() {
			return key;
		}

		public AcquireResult acquireNoAttack(
				LivingEntity subject,
				ServerPlayer forbidden,
				ServerPlayer issuer,
				UUID leaseId,
				AttackOriginScope scope) {
			Objects.requireNonNull(subject, "subject");
			Objects.requireNonNull(forbidden, "forbidden");
			Objects.requireNonNull(issuer, "issuer");
			Objects.requireNonNull(leaseId, "leaseId");
			Objects.requireNonNull(scope, "scope");

			LeaseEndpoints endpoints = serverEndpoints(
					subject, forbidden, issuer);
			if (endpoints == null) {
				return AcquireResult.rejected(
						AcquireStatus.INVALID_SERVER_STATE);
			}
			return acquire(this, endpoints, leaseId, scope);
		}

		public ReleaseStatus release(Lease lease) {
			Objects.requireNonNull(lease, "lease");
			return ControlledEntityCombatLeases.release(this, lease);
		}

		AcquireResult acquireForTests(
				LeaseEndpoint subject,
				LeaseEndpoint forbidden,
				LeaseEndpoint issuer,
				UUID leaseId,
				AttackOriginScope scope) {
			return acquire(
					this,
					new LeaseEndpoints(subject, forbidden, issuer),
					leaseId,
					scope);
		}
	}

	public enum AcquireStatus {
		ACQUIRED,
		ALREADY_ACTIVE,
		CONFLICT,
		INVALID_SERVER_STATE
	}

	public enum ReleaseStatus {
		RELEASED,
		ALREADY_RELEASED,
		WRONG_OWNER
	}

	public record AcquireResult(
			AcquireStatus status,
			@Nullable Lease lease) {
		private static AcquireResult rejected(AcquireStatus status) {
			return new AcquireResult(status, null);
		}

		public boolean succeeded() {
			return status == AcquireStatus.ACQUIRED
					|| status == AcquireStatus.ALREADY_ACTIVE;
		}
	}

	/**
	 * Opaque capability returned by a successful acquisition.
	 * Only the owner handle that created it can release it.
	 */
	public static final class Lease {
		private final Owner owner;
		private final UUID leaseId;
		private boolean active = true;

		private Lease(Owner owner, UUID leaseId) {
			this.owner = owner;
			this.leaseId = leaseId;
		}

		public ResourceLocation ownerKey() {
			return owner.key;
		}

		public UUID leaseId() {
			return leaseId;
		}

		public boolean isActive() {
			synchronized (ControlledEntityCombatLeases.class) {
				return active;
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onAttackEntity(AttackEntityEvent event) {
		if (!(event.getEntity().level() instanceof ServerLevel)
				|| !(event.getTarget() instanceof LivingEntity target)) {
			return;
		}
		AttackOrigin origin = new AttackOrigin(
				event.getEntity().getUUID(), OriginKind.SELF);
		if (shouldBlock(target.getUUID(), List.of(origin))) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingIncomingDamage(
			LivingIncomingDamageEvent event) {
		if (!(event.getEntity().level() instanceof ServerLevel)) {
			return;
		}
		if (shouldBlockDamage(event.getSource(), event.getEntity())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDeath(LivingDeathEvent event) {
		if (!event.isCanceled()
				&& event.getEntity().level() instanceof ServerLevel) {
			releaseInvolving(event.getEntity().getUUID());
		}
	}

	@SubscribeEvent
	public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
		if (event.getLevel() instanceof ServerLevel) {
			releaseInvolving(event.getEntity().getUUID());
		}
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(
			PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			releaseInvolving(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onPlayerChangedDimension(
			PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			releaseInvolving(player.getUUID());
		}
	}

	static boolean shouldBlockDamage(
			DamageSource source, LivingEntity target) {
		return shouldBlock(
				target.getUUID(), resolveAttackOrigins(source));
	}

	static Set<UUID> resolveAttackOriginIds(DamageSource source) {
		Set<UUID> ids = new LinkedHashSet<>();
		for (AttackOrigin origin : resolveAttackOrigins(source)) {
			ids.add(origin.subjectId());
		}
		return ids;
	}

	private static List<AttackOrigin> resolveAttackOrigins(
			DamageSource source) {
		return resolveAttackOrigins(
				ENTITY_ORIGIN_ADAPTER,
				source.getDirectEntity(),
				source.getEntity());
	}

	@SafeVarargs
	private static <T> List<AttackOrigin> resolveAttackOrigins(
			OriginAdapter<T> adapter,
			T... roots) {
		LinkedHashSet<AttackOrigin> origins = new LinkedHashSet<>();
		Set<T> visited = Collections.newSetFromMap(
				new IdentityHashMap<>());
		for (T root : roots) {
			resolveAttackOrigin(root, origins, visited, adapter);
		}
		return List.copyOf(origins);
	}

	private static <T> void resolveAttackOrigin(
			@Nullable T node,
			Set<AttackOrigin> origins,
			Set<T> visited,
			OriginAdapter<T> adapter) {
		if (node == null || !visited.add(node)) {
			return;
		}
		if (adapter.isProjectile(node)) {
			T owner = adapter.projectileOwner(node);
			if (owner != null && owner != node) {
				resolveAttackOrigin(
						owner, origins, visited, adapter);
			}
			return;
		}
		AttackOrigin terminal = adapter.terminalOrigin(node);
		if (terminal != null) {
			origins.add(terminal);
		}
	}

	private static synchronized boolean shouldBlock(
			UUID targetId, List<AttackOrigin> origins) {
		pruneInvalid();
		if (origins.isEmpty() || LEASES.isEmpty()) {
			return false;
		}
		for (LeaseRecord record : LEASES.values()) {
			if (!record.endpoints.forbidden.id().equals(targetId)) {
				continue;
			}
			for (AttackOrigin origin : origins) {
				if (record.blocks(origin)) {
					return true;
				}
			}
		}
		return false;
	}

	private static synchronized AcquireResult acquire(
			Owner owner,
			LeaseEndpoints endpoints,
			UUID leaseId,
			AttackOriginScope scope) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(endpoints, "endpoints");
		Objects.requireNonNull(leaseId, "leaseId");
		Objects.requireNonNull(scope, "scope");
		pruneInvalid();
		if (!endpoints.isActive()) {
			return AcquireResult.rejected(
					AcquireStatus.INVALID_SERVER_STATE);
		}

		LeaseKey key = new LeaseKey(owner.key, leaseId);
		LeaseRecord existing = LEASES.get(key);
		if (existing != null) {
			if (existing.sameParameters(endpoints, scope)) {
				return new AcquireResult(
						AcquireStatus.ALREADY_ACTIVE,
						existing.lease);
			}
			return AcquireResult.rejected(AcquireStatus.CONFLICT);
		}

		Lease lease = new Lease(owner, leaseId);
		LEASES.put(key, new LeaseRecord(
				key, endpoints, scope, lease));
		return new AcquireResult(AcquireStatus.ACQUIRED, lease);
	}

	private static synchronized ReleaseStatus release(
			Owner owner, Lease lease) {
		if (lease.owner != owner) {
			return ReleaseStatus.WRONG_OWNER;
		}
		if (!lease.active) {
			return ReleaseStatus.ALREADY_RELEASED;
		}
		LeaseRecord record = LEASES.get(
				new LeaseKey(owner.key, lease.leaseId));
		if (record == null || record.lease != lease) {
			lease.active = false;
			return ReleaseStatus.ALREADY_RELEASED;
		}
		remove(record);
		return ReleaseStatus.RELEASED;
	}

	private static synchronized void releaseInvolving(UUID entityId) {
		List<LeaseRecord> matching = new ArrayList<>();
		for (LeaseRecord record : LEASES.values()) {
			if (record.endpoints.involves(entityId)) {
				matching.add(record);
			}
		}
		matching.forEach(ControlledEntityCombatLeases::remove);
	}

	private static void pruneInvalid() {
		List<LeaseRecord> invalid = new ArrayList<>();
		for (LeaseRecord record : LEASES.values()) {
			if (!record.endpoints.isActive()) {
				invalid.add(record);
			}
		}
		invalid.forEach(ControlledEntityCombatLeases::remove);
	}

	private static void remove(LeaseRecord record) {
		if (LEASES.remove(record.key, record)) {
			record.lease.active = false;
		}
	}

	@Nullable
	private static LeaseEndpoints serverEndpoints(
			LivingEntity subject,
			ServerPlayer forbidden,
			ServerPlayer issuer) {
		if (!(subject.level() instanceof ServerLevel level)
				|| forbidden.serverLevel() != level
				|| issuer.serverLevel() != level
				|| subject.isRemoved()
				|| forbidden.isRemoved()
				|| issuer.isRemoved()
				|| !subject.isAlive()
				|| !forbidden.isAlive()
				|| !issuer.isAlive()) {
			return null;
		}
		MinecraftServer server = level.getServer();
		ResourceKey<Level> dimension = level.dimension();
		return new LeaseEndpoints(
				new ServerEntityEndpoint(
						subject, server, dimension, false),
				new ServerEntityEndpoint(
						forbidden, server, dimension, true),
				new ServerEntityEndpoint(
						issuer, server, dimension, true));
	}

	interface LeaseEndpoint {
		UUID id();
		boolean isActive();
	}

	private record LeaseEndpoints(
			LeaseEndpoint subject,
			LeaseEndpoint forbidden,
			LeaseEndpoint issuer) {
		private boolean isActive() {
			return subject.isActive()
					&& forbidden.isActive()
					&& issuer.isActive();
		}

		private boolean involves(UUID entityId) {
			return subject.id().equals(entityId)
					|| forbidden.id().equals(entityId)
					|| issuer.id().equals(entityId);
		}

		private boolean sameIds(LeaseEndpoints other) {
			return subject.id().equals(other.subject.id())
					&& forbidden.id().equals(other.forbidden.id())
					&& issuer.id().equals(other.issuer.id());
		}
	}

	private static final class ServerEntityEndpoint
			implements LeaseEndpoint {
		private final WeakReference<LivingEntity> entity;
		private final UUID id;
		private final MinecraftServer server;
		private final ResourceKey<Level> dimension;
		private final boolean requireOnlinePlayer;

		private ServerEntityEndpoint(
				LivingEntity entity,
				MinecraftServer server,
				ResourceKey<Level> dimension,
				boolean requireOnlinePlayer) {
			this.entity = new WeakReference<>(entity);
			this.id = entity.getUUID();
			this.server = server;
			this.dimension = dimension;
			this.requireOnlinePlayer = requireOnlinePlayer;
		}

		@Override
		public UUID id() {
			return id;
		}

		@Override
		public boolean isActive() {
			LivingEntity current = entity.get();
			if (current == null
					|| current.isRemoved()
					|| !current.isAlive()
					|| !current.getUUID().equals(id)
					|| !(current.level() instanceof ServerLevel level)
					|| level.getServer() != server
					|| !level.dimension().equals(dimension)) {
				return false;
			}
			return !requireOnlinePlayer
					|| current instanceof ServerPlayer player
					&& server.getPlayerList().getPlayer(id) == player;
		}
	}

	enum OriginKind {
		SELF,
		SUMMONED_STAND
	}

	record AttackOrigin(UUID subjectId, OriginKind kind) {}

	interface OriginAdapter<T> {
		boolean isProjectile(T node);

		@Nullable
		T projectileOwner(T node);

		@Nullable
		AttackOrigin terminalOrigin(T node);
	}

	private record LeaseKey(
			ResourceLocation ownerKey, UUID leaseId) {}

	private record LeaseRecord(
			LeaseKey key,
			LeaseEndpoints endpoints,
			AttackOriginScope scope,
			Lease lease) {
		private boolean sameParameters(
				LeaseEndpoints otherEndpoints,
				AttackOriginScope otherScope) {
			return scope == otherScope
					&& endpoints.sameIds(otherEndpoints);
		}

		private boolean blocks(AttackOrigin origin) {
			if (!endpoints.subject.id().equals(origin.subjectId())) {
				return false;
			}
			return origin.kind() == OriginKind.SELF
					|| scope.includesSummonedStand();
		}
	}

	static synchronized boolean shouldBlockForTests(
			UUID targetId, AttackOrigin... origins) {
		return shouldBlock(targetId, List.of(origins));
	}

	@SafeVarargs
	static <T> List<AttackOrigin> resolveAttackOriginsForTests(
			OriginAdapter<T> adapter, T... roots) {
		return resolveAttackOrigins(adapter, roots);
	}

	static synchronized void releaseInvolvingForTests(UUID entityId) {
		releaseInvolving(entityId);
	}

	static synchronized int activeLeaseCountForTests() {
		pruneInvalid();
		return LEASES.size();
	}

	static synchronized void resetForTests() {
		for (LeaseRecord record : LEASES.values()) {
			record.lease.active = false;
		}
		LEASES.clear();
		OWNERS.clear();
	}

	static LeaseEndpoint endpointForTests(
			UUID id, BooleanSupplier active) {
		return new LeaseEndpoint() {
			@Override
			public UUID id() {
				return id;
			}

			@Override
			public boolean isActive() {
				return active.getAsBoolean();
			}
		};
	}
}
