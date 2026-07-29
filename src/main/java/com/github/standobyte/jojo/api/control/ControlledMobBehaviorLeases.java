package com.github.standobyte.jojo.api.control;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Server-authoritative temporary mob behavior keyed by addon owner.
 *
 * <p>This implementation does not snapshot and replace goal selectors. An
 * aggressive state adds two core-owned goal objects and release removes those
 * exact objects by identity. Goals added, removed, or reordered by another mod
 * remain untouched. Peaceful state leaves the selectors intact and suppresses
 * targets and outgoing damage at the event boundary. Combat-memory fields
 * cleared by peaceful mode are intentionally not resurrected on release,
 * because doing so could overwrite newer state from another mod.</p>
 *
 * <p>PEACEFUL takes precedence while concurrent leases request different
 * modes. State is not persisted by core; a module that persists its command
 * mode must reacquire after the mob is loaded again.</p>
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class ControlledMobBehaviorLeases {
	private static final int AGGRESSIVE_GOAL_PRIORITY = 2;
	private static final double AGGRESSIVE_SPEED = 1.0D;
	private static final double AGGRESSIVE_RANGE = 8.0D;
	private static final boolean AGGRESSIVE_LONG_MEMORY = false;

	private static final Map<ResourceLocation, Owner> OWNERS =
			new LinkedHashMap<>();
	private static final Map<LeaseKey, LeaseRecord> LEASES =
			new LinkedHashMap<>();
	private static final Map<SubjectKey, SubjectState> SUBJECTS =
			new LinkedHashMap<>();
	private static final ThreadLocal<Set<Mob>> INTERNAL_TARGET_CHANGES =
			ThreadLocal.withInitial(() -> Collections.newSetFromMap(
					new IdentityHashMap<>()));

	private ControlledMobBehaviorLeases() {}

	public static synchronized Owner register(ResourceLocation ownerKey) {
		Objects.requireNonNull(ownerKey, "ownerKey");
		if (OWNERS.containsKey(ownerKey)) {
			throw new IllegalStateException(
					"Duplicate controlled-mob behavior lease owner: "
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

		public AcquireResult acquire(
				Mob subject,
				MobBehaviorMode mode,
				UUID leaseId) {
			Objects.requireNonNull(subject, "subject");
			Objects.requireNonNull(mode, "mode");
			Objects.requireNonNull(leaseId, "leaseId");
			MobSubjectAccess access = MobSubjectAccess.create(subject);
			if (access == null) {
				return AcquireResult.rejected(
						AcquireStatus.INVALID_SERVER_STATE);
			}
			return ControlledMobBehaviorLeases.acquire(
					this, access, mode, leaseId);
		}

		public ReleaseStatus release(Lease lease) {
			Objects.requireNonNull(lease, "lease");
			return ControlledMobBehaviorLeases.release(this, lease);
		}

		AcquireResult acquireForTests(
				SubjectAccess access,
				MobBehaviorMode mode,
				UUID leaseId) {
			return ControlledMobBehaviorLeases.acquire(
					this, access, mode, leaseId);
		}
	}

	public enum AcquireStatus {
		ACQUIRED,
		ALREADY_ACTIVE,
		CONFLICT,
		INVALID_SERVER_STATE,
		UNSUPPORTED_SUBJECT
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
	 * Opaque lease capability. It can only be released by its creating owner.
	 */
	public static final class Lease {
		private final Owner owner;
		private final UUID leaseId;
		private final SubjectKey subject;
		private final MobBehaviorMode mode;
		private boolean active = true;

		private Lease(
				Owner owner,
				UUID leaseId,
				SubjectKey subject,
				MobBehaviorMode mode) {
			this.owner = owner;
			this.leaseId = leaseId;
			this.subject = subject;
			this.mode = mode;
		}

		public ResourceLocation ownerKey() {
			return owner.key;
		}

		public UUID leaseId() {
			return leaseId;
		}

		public MobBehaviorMode mode() {
			return mode;
		}

		public boolean isActive() {
			synchronized (ControlledMobBehaviorLeases.class) {
				return active;
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingChangeTarget(
			LivingChangeTargetEvent event) {
		if (!(event.getEntity() instanceof Mob mob)
				|| !(mob.level() instanceof ServerLevel)) {
			return;
		}
		if (isPeaceful(mob)) {
			if (event.getNewAboutToBeSetTarget() != null) {
				event.setNewAboutToBeSetTarget(null);
			}
		}
		else if (!isInternalTargetChange(mob)) {
			relinquishCoreTarget(mob);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingIncomingDamage(
			LivingIncomingDamageEvent event) {
		if (!(event.getEntity().level() instanceof ServerLevel level)) {
			return;
		}
		Set<UUID> origins = ControlledEntityCombatLeases
				.resolveAttackOriginIds(event.getSource());
		if (hasPeacefulOrigin(level.getServer(), origins)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onEntityTickPre(EntityTickEvent.Pre event) {
		if (event.getEntity() instanceof Mob mob
				&& mob.level() instanceof ServerLevel) {
			tickSubject(mob);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onEntityTickPost(EntityTickEvent.Post event) {
		if (event.getEntity() instanceof Mob mob
				&& mob.level() instanceof ServerLevel) {
			tickSubject(mob);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDeath(LivingDeathEvent event) {
		if (!event.isCanceled()
				&& event.getEntity() instanceof Mob mob
				&& mob.level() instanceof ServerLevel) {
			releaseSubject(mob);
		}
	}

	@SubscribeEvent
	public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
		if (event.getEntity() instanceof Mob mob
				&& event.getLevel() instanceof ServerLevel) {
			releaseSubject(mob);
		}
	}

	private static synchronized AcquireResult acquire(
			Owner owner,
			SubjectAccess access,
			MobBehaviorMode mode,
			UUID leaseId) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(access, "access");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(leaseId, "leaseId");
		pruneInvalid();
		if (!access.isActive()) {
			return AcquireResult.rejected(
					AcquireStatus.INVALID_SERVER_STATE);
		}
		if (mode == MobBehaviorMode.AGGRESSIVE_NEAREST_8
				&& !access.supportsAggressive()) {
			return AcquireResult.rejected(
					AcquireStatus.UNSUPPORTED_SUBJECT);
		}

		SubjectKey subjectKey = new SubjectKey(
				access.scopeKey(), access.id());
		LeaseKey leaseKey = new LeaseKey(owner.key, leaseId);
		LeaseRecord existing = LEASES.get(leaseKey);
		if (existing != null) {
			if (existing.lease.subject.equals(subjectKey)
					&& existing.lease.mode == mode) {
				return new AcquireResult(
						AcquireStatus.ALREADY_ACTIVE,
						existing.lease);
			}
			return AcquireResult.rejected(AcquireStatus.CONFLICT);
		}

		SubjectState state = SUBJECTS.get(subjectKey);
		if (state == null) {
			state = new SubjectState(subjectKey, access);
			SUBJECTS.put(subjectKey, state);
		}
		else if (!state.access.sameSubject(access)) {
			return AcquireResult.rejected(
					AcquireStatus.INVALID_SERVER_STATE);
		}

		Lease lease = new Lease(owner, leaseId, subjectKey, mode);
		LeaseRecord record = new LeaseRecord(leaseKey, lease);
		LEASES.put(leaseKey, record);
		state.leases.put(leaseKey, record);
		state.applyEffectiveMode();
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
		LeaseKey key = new LeaseKey(owner.key, lease.leaseId);
		LeaseRecord record = LEASES.get(key);
		if (record == null || record.lease != lease) {
			lease.active = false;
			return ReleaseStatus.ALREADY_RELEASED;
		}
		removeRecord(record);
		return ReleaseStatus.RELEASED;
	}

	private static synchronized void tickSubject(Mob mob) {
		pruneInvalid();
		SubjectState state = stateFor(mob);
		if (state != null) {
			state.enforce();
		}
	}

	private static synchronized boolean isPeaceful(Mob mob) {
		pruneInvalid();
		SubjectState state = stateFor(mob);
		return state != null
				&& state.effectiveMode == MobBehaviorMode.PEACEFUL;
	}

	private static synchronized boolean hasPeacefulOrigin(
			MinecraftServer server, Set<UUID> origins) {
		pruneInvalid();
		if (origins.isEmpty()) {
			return false;
		}
		for (SubjectState state : SUBJECTS.values()) {
			if (state.key.scope == server
					&& state.effectiveMode
							== MobBehaviorMode.PEACEFUL
					&& origins.contains(state.key.subjectId)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private static SubjectState stateFor(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		SubjectState state = SUBJECTS.get(new SubjectKey(
				level.getServer(), mob.getUUID()));
		return state != null && state.access.matches(mob)
				? state
				: null;
	}

	private static synchronized void releaseSubject(Mob mob) {
		SubjectState state = stateFor(mob);
		if (state != null) {
			removeState(state);
		}
	}

	private static void pruneInvalid() {
		List<SubjectState> invalid = new ArrayList<>();
		for (SubjectState state : SUBJECTS.values()) {
			if (!state.access.isActive()) {
				invalid.add(state);
			}
		}
		invalid.forEach(ControlledMobBehaviorLeases::removeState);
	}

	private static void removeRecord(LeaseRecord record) {
		if (!LEASES.remove(record.key, record)) {
			return;
		}
		record.lease.active = false;
		SubjectState state = SUBJECTS.get(record.lease.subject);
		if (state == null) {
			return;
		}
		state.leases.remove(record.key);
		state.applyEffectiveMode();
		if (state.leases.isEmpty()) {
			SUBJECTS.remove(state.key, state);
		}
	}

	private static void removeState(SubjectState state) {
		state.access.removeAggressiveGoals();
		state.clearCoreTarget();
		for (LeaseRecord record : List.copyOf(state.leases.values())) {
			if (LEASES.remove(record.key, record)) {
				record.lease.active = false;
			}
		}
		state.leases.clear();
		state.effectiveMode = null;
		SUBJECTS.remove(state.key, state);
	}

	private static synchronized void beginInternalTargetChange(Mob mob) {
		INTERNAL_TARGET_CHANGES.get().add(mob);
	}

	private static synchronized void endInternalTargetChange(Mob mob) {
		Set<Mob> changes = INTERNAL_TARGET_CHANGES.get();
		changes.remove(mob);
		if (changes.isEmpty()) {
			INTERNAL_TARGET_CHANGES.remove();
		}
	}

	private static boolean isInternalTargetChange(Mob mob) {
		return INTERNAL_TARGET_CHANGES.get().contains(mob);
	}

	private static synchronized void claimCoreTarget(
			Mob mob, LivingEntity expected) {
		SubjectState state = stateFor(mob);
		if (state != null
				&& state.effectiveMode
						== MobBehaviorMode.AGGRESSIVE_NEAREST_8
				&& mob.getTarget() == expected) {
			state.claimCoreTarget(expected);
		}
	}

	private static synchronized void relinquishCoreTarget(Mob mob) {
		SubjectState state = stateFor(mob);
		if (state != null) {
			state.claimCoreTarget(null);
		}
	}

	private static synchronized void clearCoreTarget(
			Mob mob, @Nullable LivingEntity expected) {
		SubjectState state = stateFor(mob);
		if (state == null) {
			return;
		}
		LivingEntity claimed = state.coreTarget.get();
		if (claimed != null
				&& claimed == expected
				&& mob.getTarget() == claimed) {
			beginInternalTargetChange(mob);
			try {
				mob.setTarget(null);
			}
			finally {
				endInternalTargetChange(mob);
			}
		}
		state.claimCoreTarget(null);
	}

	interface SubjectAccess {
		Object scopeKey();
		UUID id();
		boolean isActive();
		boolean supportsAggressive();
		boolean sameSubject(SubjectAccess other);
		boolean matches(Mob mob);
		void installAggressiveGoals();
		void removeAggressiveGoals();
		void clearCombatState();
	}

	private static final class SubjectState {
		private final SubjectKey key;
		private final SubjectAccess access;
		private final Map<LeaseKey, LeaseRecord> leases =
				new LinkedHashMap<>();
		private WeakReference<LivingEntity> coreTarget =
				new WeakReference<>(null);
		@Nullable
		private MobBehaviorMode effectiveMode;

		private SubjectState(SubjectKey key, SubjectAccess access) {
			this.key = key;
			this.access = access;
		}

		private void applyEffectiveMode() {
			MobBehaviorMode next = effectiveMode();
			if (effectiveMode == MobBehaviorMode.AGGRESSIVE_NEAREST_8
					&& next
							!= MobBehaviorMode.AGGRESSIVE_NEAREST_8) {
				access.removeAggressiveGoals();
				clearCoreTarget();
			}
			effectiveMode = next;
			if (next == MobBehaviorMode.PEACEFUL) {
				access.clearCombatState();
			}
			else if (next
					== MobBehaviorMode.AGGRESSIVE_NEAREST_8) {
				access.installAggressiveGoals();
			}
		}

		@Nullable
		private MobBehaviorMode effectiveMode() {
			boolean aggressive = false;
			for (LeaseRecord record : leases.values()) {
				if (record.lease.mode == MobBehaviorMode.PEACEFUL) {
					return MobBehaviorMode.PEACEFUL;
				}
				aggressive = true;
			}
			return aggressive
					? MobBehaviorMode.AGGRESSIVE_NEAREST_8
					: null;
		}

		private void enforce() {
			if (effectiveMode == MobBehaviorMode.PEACEFUL) {
				access.clearCombatState();
			}
			else if (effectiveMode
					== MobBehaviorMode.AGGRESSIVE_NEAREST_8) {
				access.installAggressiveGoals();
			}
		}

		private void claimCoreTarget(
				@Nullable LivingEntity target) {
			coreTarget = new WeakReference<>(target);
		}

		private void clearCoreTarget() {
			if (access instanceof MobSubjectAccess mobAccess) {
				Mob mob = mobAccess.mob.get();
				if (mob != null) {
					ControlledMobBehaviorLeases.clearCoreTarget(
							mob, coreTarget.get());
				}
			}
			else {
				claimCoreTarget(null);
			}
		}
	}

	private static final class MobSubjectAccess
			implements SubjectAccess {
		private final WeakReference<Mob> mob;
		private final UUID id;
		private final MinecraftServer server;
		private final ResourceKey<Level> dimension;
		private final Goal attackGoal;
		private final Goal targetGoal;

		@Nullable
		private static MobSubjectAccess create(Mob mob) {
			if (!(mob.level() instanceof ServerLevel level)
					|| mob.isRemoved()
					|| !mob.isAlive()) {
				return null;
			}
			return new MobSubjectAccess(
					mob, level.getServer(), level.dimension());
		}

		private MobSubjectAccess(
				Mob mob,
				MinecraftServer server,
				ResourceKey<Level> dimension) {
			this.mob = new WeakReference<>(mob);
			this.id = mob.getUUID();
			this.server = server;
			this.dimension = dimension;
			this.attackGoal = new LeaseMeleeAttackGoal(mob);
			this.targetGoal = new LeaseNearestTargetGoal(mob);
		}

		@Override
		public Object scopeKey() {
			return server;
		}

		@Override
		public UUID id() {
			return id;
		}

		@Override
		public boolean isActive() {
			Mob current = mob.get();
			return current != null
					&& !current.isRemoved()
					&& current.isAlive()
					&& current.getUUID().equals(id)
					&& current.level() instanceof ServerLevel level
					&& level.getServer() == server
					&& level.dimension().equals(dimension);
		}

		@Override
		public boolean supportsAggressive() {
			Mob current = mob.get();
			return current != null
					&& !(current instanceof AmbientCreature);
		}

		@Override
		public boolean sameSubject(SubjectAccess other) {
			return other instanceof MobSubjectAccess mobAccess
					&& mob.get() == mobAccess.mob.get();
		}

		@Override
		public boolean matches(Mob other) {
			return mob.get() == other;
		}

		@Override
		public void installAggressiveGoals() {
			Mob current = mob.get();
			if (current == null || current instanceof AmbientCreature) {
				return;
			}
			if (!contains(current.goalSelector, attackGoal)) {
				current.goalSelector.addGoal(
						AGGRESSIVE_GOAL_PRIORITY, attackGoal);
			}
			if (!contains(current.targetSelector, targetGoal)) {
				current.targetSelector.addGoal(
						AGGRESSIVE_GOAL_PRIORITY, targetGoal);
			}
		}

		@Override
		public void removeAggressiveGoals() {
			Mob current = mob.get();
			if (current == null) {
				return;
			}
			current.goalSelector.removeGoal(attackGoal);
			current.targetSelector.removeGoal(targetGoal);
		}

		@Override
		public void clearCombatState() {
			Mob current = mob.get();
			if (current == null) {
				return;
			}
			if (current.getTarget() != null) {
				beginInternalTargetChange(current);
				try {
					current.setTarget(null);
				}
				finally {
					endInternalTargetChange(current);
				}
			}
			current.setLastHurtByMob(null);
			current.setLastHurtMob(null);
			current.setAggressive(false);
		}

		private static boolean contains(
				net.minecraft.world.entity.ai.goal.GoalSelector selector,
				Goal goal) {
			return selector.getAvailableGoals().stream()
					.anyMatch(wrapped -> wrapped.getGoal() == goal);
		}
	}

	private static final class LeaseNearestTargetGoal
			extends NearestAttackableTargetGoal<LivingEntity> {
		private LeaseNearestTargetGoal(Mob mob) {
			super(
					mob,
					LivingEntity.class,
					1,
					false,
					false,
					candidate -> candidate != mob);
		}

		@Override
		protected double getFollowDistance() {
			return AGGRESSIVE_RANGE;
		}

		@Override
		public void start() {
			beginInternalTargetChange(mob);
			try {
				super.start();
			}
			finally {
				endInternalTargetChange(mob);
			}
			claimCoreTarget(mob, target);
		}

		@Override
		public void stop() {
			clearCoreTarget(mob, target);
			target = null;
			targetMob = null;
		}
	}

	private static final class LeaseMeleeAttackGoal extends Goal {
		private final Mob mob;
		@Nullable
		private Path path;
		private long lastCanUseCheck;
		private int ticksUntilNextPathRecalculation;
		private int ticksUntilNextAttack;

		private LeaseMeleeAttackGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			long gameTime = mob.level().getGameTime();
			if (gameTime - lastCanUseCheck < 20L) {
				return false;
			}
			lastCanUseCheck = gameTime;
			LivingEntity target = mob.getTarget();
			if (!isValidTarget(target)) {
				return false;
			}
			path = mob.getNavigation().createPath(target, 0);
			return path != null || mob.isWithinMeleeAttackRange(target);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = mob.getTarget();
			if (!isValidTarget(target)) {
				return false;
			}
			return AGGRESSIVE_LONG_MEMORY
					|| !mob.getNavigation().isDone()
					|| mob.isWithinMeleeAttackRange(target);
		}

		@Override
		public void start() {
			if (path != null) {
				mob.getNavigation().moveTo(path, AGGRESSIVE_SPEED);
			}
			else if (mob.getTarget() != null) {
				mob.getNavigation().moveTo(
						mob.getTarget(), AGGRESSIVE_SPEED);
			}
			mob.setAggressive(true);
			ticksUntilNextPathRecalculation = 0;
			ticksUntilNextAttack = 0;
		}

		@Override
		public void stop() {
			mob.setAggressive(false);
			mob.getNavigation().stop();
			path = null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = mob.getTarget();
			if (!isValidTarget(target)) {
				return;
			}
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			ticksUntilNextPathRecalculation = Math.max(
					ticksUntilNextPathRecalculation - 1, 0);
			if (ticksUntilNextPathRecalculation <= 0
					&& mob.getSensing().hasLineOfSight(target)) {
				ticksUntilNextPathRecalculation =
						adjustedTickDelay(
								4 + mob.getRandom().nextInt(7));
				if (!mob.getNavigation().moveTo(
						target, AGGRESSIVE_SPEED)) {
					ticksUntilNextPathRecalculation += 15;
				}
			}
			ticksUntilNextAttack = Math.max(
					ticksUntilNextAttack - 1, 0);
			if (ticksUntilNextAttack <= 0
					&& mob.isWithinMeleeAttackRange(target)
					&& mob.getSensing().hasLineOfSight(target)) {
				ticksUntilNextAttack = adjustedTickDelay(20);
				mob.swing(InteractionHand.MAIN_HAND);
				mob.doHurtTarget(target);
			}
		}

		private boolean isValidTarget(
				@Nullable LivingEntity target) {
			return target != null
					&& target != mob
					&& target.isAlive()
					&& mob.canAttack(target)
					&& mob.distanceToSqr(target)
							<= AGGRESSIVE_RANGE * AGGRESSIVE_RANGE
					&& EntitySelector.NO_CREATIVE_OR_SPECTATOR
							.test(target);
		}
	}

	private record SubjectKey(Object scope, UUID subjectId) {}

	private record LeaseKey(
			ResourceLocation ownerKey, UUID leaseId) {}

	private record LeaseRecord(LeaseKey key, Lease lease) {}

	static synchronized void tickForTests(Object scope, UUID subjectId) {
		pruneInvalid();
		SubjectState state = SUBJECTS.get(
				new SubjectKey(scope, subjectId));
		if (state != null) {
			state.enforce();
		}
	}

	static synchronized boolean isPeacefulForTests(
			Object scope, UUID subjectId) {
		pruneInvalid();
		SubjectState state = SUBJECTS.get(
				new SubjectKey(scope, subjectId));
		return state != null
				&& state.effectiveMode == MobBehaviorMode.PEACEFUL;
	}

	static synchronized void releaseSubjectForTests(
			Object scope, UUID subjectId) {
		SubjectState state = SUBJECTS.get(
				new SubjectKey(scope, subjectId));
		if (state != null) {
			removeState(state);
		}
	}

	static synchronized int activeLeaseCountForTests() {
		pruneInvalid();
		return LEASES.size();
	}

	static synchronized void resetForTests() {
		for (SubjectState state : List.copyOf(SUBJECTS.values())) {
			removeState(state);
		}
		LEASES.clear();
		SUBJECTS.clear();
		OWNERS.clear();
		INTERNAL_TARGET_CHANGES.remove();
	}
}
