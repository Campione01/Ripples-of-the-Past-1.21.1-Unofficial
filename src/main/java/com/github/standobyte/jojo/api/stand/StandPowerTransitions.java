package com.github.standobyte.jojo.api.stand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative Stand ownership changes for addons.
 * <p>
 * Item consumption, inventory delivery, drops, projectiles, and animation
 * staging remain the caller's responsibility.
 */
public final class StandPowerTransitions {
	private static final TransitionContext LEGACY_CONTEXT =
			new TransitionContext(
					JojoMod.resLoc("legacy_stand_transition_api"),
					null);
	private static final Map<ResourceLocation, TransitionVeto> VETOES =
			new LinkedHashMap<>();
	private static final Map<ResourceLocation, MutationVeto>
			MUTATION_VETOES = new LinkedHashMap<>();

	private StandPowerTransitions() {}

	public static Result insert(StandPower power, StandInstance replacement) {
		Objects.requireNonNull(power, "power");
		return insert(
				new StandPowerAccess(power),
				replacement,
				LEGACY_CONTEXT);
	}

	public static Result extract(StandPower power, ResourceLocation expectedCurrent) {
		Objects.requireNonNull(power, "power");
		return extract(new StandPowerAccess(power), expectedCurrent);
	}

	public static Result replace(StandPower power, ResourceLocation expectedCurrent, StandInstance replacement) {
		Objects.requireNonNull(power, "power");
		return replace(
				new StandPowerAccess(power),
				expectedCurrent,
				replacement,
				LEGACY_CONTEXT);
	}

	/**
	 * Inserts a Stand through a contextual, server-thread mutation preflight.
	 */
	public static Result insert(
			StandPower power,
			StandInstance replacement,
			TransitionContext context) {
		Objects.requireNonNull(power, "power");
		return insert(
				new StandPowerAccess(power), replacement, context);
	}

	/**
	 * Replaces the expected Stand through a contextual, server-thread mutation
	 * preflight.
	 */
	public static Result replace(
			StandPower power,
			ResourceLocation expectedCurrent,
			StandInstance replacement,
			TransitionContext context) {
		Objects.requireNonNull(power, "power");
		return replace(
				new StandPowerAccess(power),
				expectedCurrent,
				replacement,
				context);
	}

	/**
	 * Removes the current Stand while preserving all persistent progression.
	 */
	public static Result clear(StandPower power, TransitionContext context) {
		Objects.requireNonNull(power, "power");
		return clear(new StandPowerAccess(power), context);
	}

	/**
	 * Removes and returns the exact current Stand while preserving persistent
	 * progression.
	 */
	public static Result extract(
			StandPower power,
			ResourceLocation expectedCurrent,
			TransitionContext context) {
		Objects.requireNonNull(power, "power");
		return extract(new StandPowerAccess(power), expectedCurrent, context);
	}

	/**
	 * Removes the current Stand and all Stand progression/history owned by the
	 * power.
	 */
	public static Result fullReset(
			StandPower power,
			TransitionContext context) {
		Objects.requireNonNull(power, "power");
		return fullReset(new StandPowerAccess(power), context);
	}

	/**
	 * Registers a deterministic, owner-keyed destructive-transition veto.
	 * Callbacks are preflight-only and must not mutate game state.
	 */
	public static synchronized void registerVeto(
			ResourceLocation owner,
			TransitionVeto veto) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(veto, "veto");
		if (VETOES.putIfAbsent(owner, veto) != null) {
			throw new IllegalStateException(
					"Duplicate Stand power transition veto: " + owner);
		}
	}

	/**
	 * Registers a deterministic, owner-keyed insert/replace veto. Callbacks are
	 * preflight-only and must not mutate game state.
	 */
	public static synchronized void registerMutationVeto(
			ResourceLocation owner,
			MutationVeto veto) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(veto, "veto");
		if (MUTATION_VETOES.putIfAbsent(owner, veto) != null) {
			throw new IllegalStateException(
					"Duplicate Stand power mutation veto: " + owner);
		}
	}

	static Result insert(PowerAccess power, StandInstance replacement) {
		return insert(power, replacement, LEGACY_CONTEXT);
	}

	static Result insert(
			PowerAccess power,
			StandInstance replacement,
			@Nullable TransitionContext context) {
		Objects.requireNonNull(power, "power");
		if (!power.isServerSide()) {
			return Result.failed(Status.NOT_SERVER_SIDE);
		}
		if (!power.isServerThread()) {
			return Result.failed(Status.NOT_SERVER_THREAD);
		}
		if (context == null || !power.isContextValid(context)) {
			return Result.failed(Status.INVALID_CONTEXT);
		}
		if (replacement == null || !replacement.standExists()) {
			return Result.failed(Status.INVALID_REPLACEMENT);
		}
		if (power.getStandInstance().isPresent()) {
			return Result.failed(Status.STAND_ALREADY_PRESENT);
		}

		StandInstance committed = replacement.copy();
		Status vetoStatus = evaluateMutationVetoes(
				new MutationQuery(
						MutationOperation.INSERT,
						context,
						power.getUser(),
						Optional.empty(),
						committed));
		if (vetoStatus != null) {
			return Result.failed(vetoStatus);
		}
		power.setStandInstance(Optional.of(committed));
		return Result.applied(Optional.empty(), Optional.of(committed));
	}

	static Result extract(PowerAccess power, ResourceLocation expectedCurrent) {
		Objects.requireNonNull(power, "power");
		if (!power.isServerSide()) {
			return Result.failed(Status.NOT_SERVER_SIDE);
		}
		if (!power.isServerThread()) {
			return Result.failed(Status.NOT_SERVER_THREAD);
		}
		if (!power.isContextValid(LEGACY_CONTEXT)) {
			return Result.failed(Status.INVALID_CONTEXT);
		}
		Objects.requireNonNull(expectedCurrent, "expectedCurrent");

		Optional<StandInstance> current = power.getStandInstance();
		if (current.isEmpty()) {
			return Result.failed(Status.NO_STAND);
		}
		StandInstance previous = current.get();
		if (!previous.getStandId().equals(expectedCurrent)) {
			return Result.failed(Status.SOURCE_MISMATCH);
		}

		StandInstance previousSnapshot = previous.copy();
		Status vetoStatus = evaluateVetoes(
				new TransitionQuery(
						Operation.EXTRACT,
						LEGACY_CONTEXT,
						power.getUser(),
						previousSnapshot));
		if (vetoStatus != null) {
			return Result.failed(vetoStatus);
		}
		power.setStandInstance(Optional.empty());
		return Result.applied(Optional.of(previousSnapshot), Optional.empty());
	}

	static Result replace(PowerAccess power, ResourceLocation expectedCurrent, StandInstance replacement) {
		return replace(
				power,
				expectedCurrent,
				replacement,
				LEGACY_CONTEXT);
	}

	static Result replace(
			PowerAccess power,
			ResourceLocation expectedCurrent,
			StandInstance replacement,
			@Nullable TransitionContext context) {
		Objects.requireNonNull(power, "power");
		if (!power.isServerSide()) {
			return Result.failed(Status.NOT_SERVER_SIDE);
		}
		if (!power.isServerThread()) {
			return Result.failed(Status.NOT_SERVER_THREAD);
		}
		if (context == null || !power.isContextValid(context)) {
			return Result.failed(Status.INVALID_CONTEXT);
		}
		Objects.requireNonNull(expectedCurrent, "expectedCurrent");
		if (replacement == null || !replacement.standExists()) {
			return Result.failed(Status.INVALID_REPLACEMENT);
		}

		Optional<StandInstance> current = power.getStandInstance();
		if (current.isEmpty()) {
			return Result.failed(Status.NO_STAND);
		}
		StandInstance previous = current.get();
		if (!previous.getStandId().equals(expectedCurrent)) {
			return Result.failed(Status.SOURCE_MISMATCH);
		}

		StandInstance previousSnapshot = previous.copy();
		StandInstance committed = replacement.copy();
		Status vetoStatus = evaluateMutationVetoes(
				new MutationQuery(
						MutationOperation.REPLACE,
						context,
						power.getUser(),
						Optional.of(previousSnapshot),
						committed));
		if (vetoStatus != null) {
			return Result.failed(vetoStatus);
		}
		power.setStandInstance(Optional.of(committed));
		return Result.applied(Optional.of(previousSnapshot), Optional.of(committed));
	}

	static Result clear(PowerAccess power, TransitionContext context) {
		return destructiveTransition(
				power, null, context, Operation.CLEAR);
	}

	static Result extract(
			PowerAccess power,
			ResourceLocation expectedCurrent,
			TransitionContext context) {
		return destructiveTransition(
				power, expectedCurrent, context, Operation.EXTRACT);
	}

	static Result fullReset(PowerAccess power, TransitionContext context) {
		return destructiveTransition(
				power, null, context, Operation.FULL_RESET);
	}

	private static Result destructiveTransition(
			PowerAccess power,
			@Nullable ResourceLocation expectedCurrent,
			@Nullable TransitionContext context,
			Operation operation) {
		Objects.requireNonNull(power, "power");
		Objects.requireNonNull(operation, "operation");
		if (!power.isServerSide()) {
			return Result.failed(Status.NOT_SERVER_SIDE);
		}
		if (!power.isServerThread()) {
			return Result.failed(Status.NOT_SERVER_THREAD);
		}
		if (context == null || !power.isContextValid(context)) {
			return Result.failed(Status.INVALID_CONTEXT);
		}
		if (operation == Operation.EXTRACT && expectedCurrent == null) {
			return Result.failed(Status.INVALID_SOURCE);
		}

		Optional<StandInstance> current = power.getStandInstance();
		if (current.isEmpty()) {
			return Result.failed(Status.NO_STAND);
		}
		StandInstance previousSnapshot = current.get().copy();
		if (expectedCurrent != null
				&& !previousSnapshot.getStandId().equals(expectedCurrent)) {
			return Result.failed(Status.SOURCE_MISMATCH);
		}

		TransitionQuery query = new TransitionQuery(
				operation,
				context,
				power.getUser(),
				previousSnapshot);
		Status vetoStatus = evaluateVetoes(query);
		if (vetoStatus != null) {
			return Result.failed(vetoStatus);
		}

		power.applyDestructiveTransition(operation == Operation.FULL_RESET);
		return Result.applied(
				Optional.of(previousSnapshot), Optional.empty());
	}

	@Nullable
	private static Status evaluateVetoes(TransitionQuery query) {
		List<Map.Entry<ResourceLocation, TransitionVeto>> snapshot;
		synchronized (StandPowerTransitions.class) {
			snapshot = new ArrayList<>(VETOES.entrySet());
		}
		for (Map.Entry<ResourceLocation, TransitionVeto> entry : snapshot) {
			try {
				if (entry.getValue().vetoes(query)) {
					return Status.VETOED;
				}
			}
			catch (RuntimeException exception) {
				JojoMod.getLogger().error(
						"Stand power transition veto {} failed.",
						entry.getKey(),
						exception);
				return Status.PREFLIGHT_FAILED;
			}
		}
		return null;
	}

	@Nullable
	private static Status evaluateMutationVetoes(
			MutationQuery query) {
		List<Map.Entry<ResourceLocation, MutationVeto>> snapshot;
		synchronized (StandPowerTransitions.class) {
			snapshot =
					new ArrayList<>(MUTATION_VETOES.entrySet());
		}
		for (Map.Entry<ResourceLocation, MutationVeto> entry
				: snapshot) {
			try {
				if (entry.getValue().vetoes(query)) {
					return Status.VETOED;
				}
			}
			catch (RuntimeException exception) {
				JojoMod.getLogger().error(
						"Stand power mutation veto {} failed.",
						entry.getKey(),
						exception);
				return Status.PREFLIGHT_FAILED;
			}
		}
		return null;
	}

	public record TransitionContext(
			ResourceLocation source,
			@Nullable Entity actor) {

		public TransitionContext {
			Objects.requireNonNull(source, "source");
		}
	}

	public record TransitionQuery(
			Operation operation,
			TransitionContext context,
			@Nullable LivingEntity target,
			StandInstance current) {

		public TransitionQuery {
			Objects.requireNonNull(operation, "operation");
			Objects.requireNonNull(context, "context");
			current = Objects.requireNonNull(current, "current").copy();
		}

		@Override
		public StandInstance current() {
			return current.copy();
		}
	}

	@FunctionalInterface
	public interface TransitionVeto {
		boolean vetoes(TransitionQuery query);
	}

	public record MutationQuery(
			MutationOperation operation,
			TransitionContext context,
			@Nullable LivingEntity target,
			Optional<StandInstance> current,
			StandInstance replacement) {

		public MutationQuery {
			Objects.requireNonNull(operation, "operation");
			Objects.requireNonNull(context, "context");
			current = copy(current, "current");
			replacement = Objects.requireNonNull(
					replacement, "replacement").copy();
		}

		@Override
		public Optional<StandInstance> current() {
			return copy(current, "current");
		}

		@Override
		public StandInstance replacement() {
			return replacement.copy();
		}

		private static Optional<StandInstance> copy(
				Optional<StandInstance> stand,
				String name) {
			return Objects.requireNonNull(stand, name)
					.map(StandInstance::copy);
		}
	}

	@FunctionalInterface
	public interface MutationVeto {
		boolean vetoes(MutationQuery query);
	}

	public enum Operation {
		CLEAR,
		EXTRACT,
		FULL_RESET
	}

	public enum MutationOperation {
		INSERT,
		REPLACE
	}

	public record Result(
			Status status,
			Optional<StandInstance> previous,
			Optional<StandInstance> current) {

		public Result {
			Objects.requireNonNull(status, "status");
			previous = copy(previous, "previous");
			current = copy(current, "current");
		}

		private static Result applied(Optional<StandInstance> previous, Optional<StandInstance> current) {
			return new Result(Status.APPLIED, previous, current);
		}

		private static Result failed(Status status) {
			return new Result(status, Optional.empty(), Optional.empty());
		}

		private static Optional<StandInstance> copy(Optional<StandInstance> stand, String name) {
			return Objects.requireNonNull(stand, name).map(StandInstance::copy);
		}

		public boolean applied() {
			return status == Status.APPLIED;
		}

		@Override
		public Optional<StandInstance> previous() {
			return copy(previous, "previous");
		}

		@Override
		public Optional<StandInstance> current() {
			return copy(current, "current");
		}
	}

	public enum Status {
		APPLIED,
		NOT_SERVER_SIDE,
		INVALID_REPLACEMENT,
		NO_STAND,
		STAND_ALREADY_PRESENT,
		SOURCE_MISMATCH,
		NOT_SERVER_THREAD,
		INVALID_CONTEXT,
		INVALID_SOURCE,
		VETOED,
		PREFLIGHT_FAILED
	}

	interface PowerAccess {
		boolean isServerSide();
		default boolean isServerThread() {
			return true;
		}
		default boolean isContextValid(TransitionContext context) {
			return true;
		}
		@Nullable
		default LivingEntity getUser() {
			return null;
		}
		Optional<StandInstance> getStandInstance();
		void setStandInstance(Optional<StandInstance> standInstance);
		default void applyDestructiveTransition(boolean fullReset) {
			setStandInstance(Optional.empty());
		}
	}

	private record StandPowerAccess(StandPower power) implements PowerAccess {
		@Override
		public boolean isServerSide() {
			var user = power.getUser();
			return user != null && !user.level().isClientSide();
		}

		@Override
		public boolean isServerThread() {
			LivingEntity user = power.getUser();
			return user != null
					&& user.level() instanceof ServerLevel level
					&& level.getServer().isSameThread();
		}

		@Override
		public boolean isContextValid(TransitionContext context) {
			Entity actor = context.actor();
			return actor == null || actor.level() == power.getUser().level();
		}

		@Override
		public LivingEntity getUser() {
			return power.getUser();
		}

		@Override
		public Optional<StandInstance> getStandInstance() {
			return power.getStandInstance();
		}

		@Override
		public void setStandInstance(Optional<StandInstance> standInstance) {
			power.setStandInstance(standInstance);
		}

		@Override
		public void applyDestructiveTransition(boolean fullReset) {
			power.applyDestructiveTransition(fullReset);
		}
	}

	static synchronized List<ResourceLocation> registeredVetoOwners() {
		return List.copyOf(VETOES.keySet());
	}

	static synchronized List<ResourceLocation>
			registeredMutationVetoOwners() {
		return List.copyOf(MUTATION_VETOES.keySet());
	}

	static synchronized void resetVetoesForTests() {
		VETOES.clear();
		MUTATION_VETOES.clear();
	}
}
