package com.github.standobyte.jojo.api.gravity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

@ApiStatus.Internal
public final class DirectionalGravityData {
	private final Map<ResourceLocation, Binding> bindings = new HashMap<>();
	private List<Candidate> snapshot = List.of();
	private Direction appliedDirection = Direction.DOWN;

	boolean bind(ResourceLocation sourceId, int priority,
			DirectionalGravitySource source) {
		Binding replacement = new Binding(priority, source);
		Binding previous = bindings.put(sourceId, replacement);
		publishSnapshot();
		return !replacement.sameDefinition(previous);
	}

	Direction bindAndResolve(Entity entity, ResourceLocation sourceId,
			int priority, DirectionalGravitySource source) {
		Binding replacement = new Binding(priority, source);
		Binding previous = bindings.put(sourceId, replacement);
		publishSnapshot();
		try {
			Direction replacementDirection =
					resolveStrict(entity, replacement);
			return resolve(
					entity, replacement, replacementDirection);
		}
		catch (RuntimeException | Error failure) {
			if (bindings.get(sourceId) == replacement) {
				if (previous != null) {
					bindings.put(sourceId, previous);
				}
				else {
					bindings.remove(sourceId);
				}
				publishSnapshot();
			}
			throw failure;
		}
	}

	boolean unbind(ResourceLocation sourceId,
			DirectionalGravitySource source) {
		Binding binding = bindings.get(sourceId);
		if (binding == null || binding.source() != source) {
			return false;
		}
		bindings.remove(sourceId);
		publishSnapshot();
		return true;
	}

	boolean contains(ResourceLocation sourceId,
			DirectionalGravitySource source) {
		Binding binding = bindings.get(sourceId);
		return binding != null && binding.source() == source;
	}

	boolean reactivate(ResourceLocation sourceId,
			DirectionalGravitySource source) {
		Binding binding = bindings.get(sourceId);
		if (binding == null || binding.source() != source) {
			return false;
		}
		binding.reactivate();
		return true;
	}

	Direction appliedDirection() {
		return appliedDirection;
	}

	boolean updateAppliedDirection(Direction direction) {
		if (appliedDirection == direction) {
			return false;
		}
		appliedDirection = direction;
		return true;
	}

	Direction resolve(Entity entity) {
		return resolve(entity, null, Direction.DOWN);
	}

	private Direction resolve(Entity entity,
			Binding preResolvedBinding,
			Direction preResolvedDirection) {
		ResourceLocation winningId = null;
		Binding winner = null;
		Direction winningDirection = Direction.DOWN;
		for (Candidate candidate : snapshot) {
			ResourceLocation sourceId = candidate.sourceId();
			Binding binding = candidate.binding();
			if (bindings.get(sourceId) != binding
					|| binding.quarantined()) {
				continue;
			}
			Direction direction;
			if (binding == preResolvedBinding) {
				direction = preResolvedDirection;
			}
			else {
				try {
					direction = resolveStrict(entity, binding);
				}
				catch (RuntimeException failure) {
					if (bindings.get(sourceId) == binding
							&& binding.quarantine()) {
						logRuntimeFailure(
								sourceId, entity, binding, failure);
					}
					continue;
				}
			}
			if (bindings.get(sourceId) != binding) {
				continue;
			}
			binding.recordSuccess();
			if (direction == Direction.DOWN) {
				continue;
			}
			if (winner == null
					|| binding.priority() > winner.priority()
					|| binding.priority() == winner.priority()
							&& sourceId.compareTo(winningId) < 0) {
				winningId = sourceId;
				winner = binding;
				winningDirection = direction;
			}
		}
		return winningDirection;
	}

	private void publishSnapshot() {
		snapshot = bindings.entrySet().stream()
				.map(entry -> new Candidate(
						entry.getKey(), entry.getValue()))
				.toList();
	}

	private static Direction resolveStrict(
			Entity entity, Binding binding) {
		return Objects.requireNonNullElse(
				binding.source().gravityDirection(entity),
				Direction.DOWN);
	}

	private static void logRuntimeFailure(
			ResourceLocation sourceId,
			Entity entity,
			Binding binding,
			RuntimeException failure) {
		if (!binding.markFailureLogged()) {
			return;
		}
		JojoMod.getLogger().error(
				"Directional gravity source {} failed for {}; "
						+ "its binding is quarantined until "
						+ "directionChanged or rebind.",
				sourceId,
				entity != null
						? entity.getStringUUID()
						: "<unknown entity>",
				failure);
	}

	private record Candidate(
			ResourceLocation sourceId, Binding binding) {}

	private static final class Binding {
		private final int priority;
		private final DirectionalGravitySource source;
		private boolean quarantined;
		private boolean failureLogged;

		private Binding(int priority,
				DirectionalGravitySource source) {
			this.priority = priority;
			this.source = Objects.requireNonNull(source);
		}

		private int priority() {
			return priority;
		}

		private DirectionalGravitySource source() {
			return source;
		}

		private boolean quarantined() {
			return quarantined;
		}

		private boolean quarantine() {
			if (quarantined) {
				return false;
			}
			quarantined = true;
			return true;
		}

		private void reactivate() {
			quarantined = false;
		}

		private boolean markFailureLogged() {
			if (failureLogged) {
				return false;
			}
			failureLogged = true;
			return true;
		}

		private void recordSuccess() {
			failureLogged = false;
		}

		private boolean sameDefinition(Binding other) {
			return other != null
					&& priority == other.priority
					&& source.equals(other.source);
		}
	}
}
