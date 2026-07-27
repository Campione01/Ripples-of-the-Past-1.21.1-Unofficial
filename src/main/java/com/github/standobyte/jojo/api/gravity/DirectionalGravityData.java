package com.github.standobyte.jojo.api.gravity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

@ApiStatus.Internal
public final class DirectionalGravityData {
	private final Map<ResourceLocation, Binding> bindings = new HashMap<>();
	private Direction appliedDirection = Direction.DOWN;

	boolean bind(ResourceLocation sourceId, int priority,
			DirectionalGravitySource source) {
		Binding replacement = new Binding(priority, source);
		Binding previous = bindings.put(sourceId, replacement);
		return !replacement.equals(previous);
	}

	boolean unbind(ResourceLocation sourceId,
			DirectionalGravitySource source) {
		Binding binding = bindings.get(sourceId);
		if (binding == null || binding.source() != source) {
			return false;
		}
		bindings.remove(sourceId);
		return true;
	}

	boolean contains(ResourceLocation sourceId,
			DirectionalGravitySource source) {
		Binding binding = bindings.get(sourceId);
		return binding != null && binding.source() == source;
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
		ResourceLocation winningId = null;
		Binding winner = null;
		Direction winningDirection = Direction.DOWN;
		for (Map.Entry<ResourceLocation, Binding> entry
				: bindings.entrySet()) {
			Direction direction = Objects.requireNonNullElse(
					entry.getValue().source().gravityDirection(entity),
					Direction.DOWN);
			if (direction == Direction.DOWN) {
				continue;
			}
			if (winner == null
					|| entry.getValue().priority() > winner.priority()
					|| entry.getValue().priority() == winner.priority()
							&& entry.getKey().compareTo(winningId) < 0) {
				winningId = entry.getKey();
				winner = entry.getValue();
				winningDirection = direction;
			}
		}
		return winningDirection;
	}

	private record Binding(int priority, DirectionalGravitySource source) {
		private Binding {
			Objects.requireNonNull(source);
		}
	}
}
