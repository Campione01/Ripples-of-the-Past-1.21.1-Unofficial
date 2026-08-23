package com.github.standobyte.jojo.powersystem.entityaction;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.entityattachment.TickingEntityData;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import net.minecraft.world.entity.LivingEntity;

@ApiStatus.Internal
public class EntityActionInputState implements TickingEntityData {
	private static final AtomicLong CLIENT_INPUT_GENERATION =
			new AtomicLong();

	public final LivingEntity user;
	public final ActionInputBuffer inputBuffer = new ActionInputBuffer();

	public EntityActionInputState(LivingEntity entity) {
		this.user = entity;
		addTicking(entity);
	}

	@Override
	public void tick() {
		inputBuffer.tickInputBuffer(this);
	}

	// TODO (entity action 2) if the player logs out and the action gets saved in NBT, after relog they won't be able to stop the action - fix that
	@ApiStatus.Internal
	public final Int2ObjectMap<HeldInputEntry> heldKeys = new Int2ObjectArrayMap<>();
	private final InputGenerationTracker inputGenerations =
			new InputGenerationTracker();

	@ApiStatus.Internal
	public long nextInputGeneration(short keyId) {
		return user.level().isClientSide()
				? inputGenerations.nextSessionInputGeneration(keyId)
				: inputGenerations.nextInputGeneration(keyId);
	}

	@ApiStatus.Internal
	public boolean acceptNetworkPressGeneration(
			short keyId, long generation) {
		return inputGenerations.acceptNetworkPressGeneration(
				keyId, generation);
	}

	@ApiStatus.Internal
	public boolean adoptInputGeneration(short keyId, long generation) {
		return inputGenerations.adoptInputGeneration(keyId, generation);
	}

	@ApiStatus.Internal
	public void observeInputGeneration(short keyId, long generation) {
		inputGenerations.observeInputGeneration(keyId, generation);
	}

	@ApiStatus.Internal
	public long latestInputGeneration(short keyId) {
		return inputGenerations.latestInputGeneration(keyId);
	}

	@ApiStatus.Internal
	public static final class InputGenerationTracker {
		private final Int2LongMap latestInputGenerations =
				new Int2LongArrayMap();
		private long inputGenerationCounter;

		public long nextInputGeneration(short keyId) {
			if (inputGenerationCounter == Long.MAX_VALUE) {
				throw new IllegalStateException(
						"Ability input generation exhausted");
			}
			long generation = ++inputGenerationCounter;
			latestInputGenerations.put(keyId, generation);
			return generation;
		}

		public long nextSessionInputGeneration(short keyId) {
			long generation = nextClientSessionGeneration(
					inputGenerationCounter);
			observeInputGeneration(keyId, generation);
			return generation;
		}

		public boolean acceptNetworkPressGeneration(
				short keyId, long generation) {
			if (generation <= latestInputGeneration(keyId)) {
				return false;
			}
			observeInputGeneration(keyId, generation);
			return true;
		}

		public boolean adoptInputGeneration(short keyId, long generation) {
			if (generation <= 0L
					|| generation < latestInputGeneration(keyId)) {
				return false;
			}
			observeInputGeneration(keyId, generation);
			return true;
		}

		public void observeInputGeneration(short keyId, long generation) {
			if (generation <= 0L) {
				throw new IllegalArgumentException(
						"Ability input generation must be positive");
			}
			if (generation > latestInputGeneration(keyId)) {
				latestInputGenerations.put(keyId, generation);
			}
			inputGenerationCounter = Math.max(
					inputGenerationCounter, generation);
		}

		public long latestInputGeneration(short keyId) {
			return latestInputGenerations.getOrDefault(keyId, 0L);
		}
	}

	private static long nextClientSessionGeneration(long localFloor) {
		while (true) {
			long current = CLIENT_INPUT_GENERATION.get();
			long floor = Math.max(current, localFloor);
			if (floor == Long.MAX_VALUE) {
				throw new IllegalStateException(
						"Ability input generation exhausted");
			}
			if (CLIENT_INPUT_GENERATION.compareAndSet(
					current, floor + 1L)) {
				return floor + 1L;
			}
		}
	}

	public static class HeldInputEntry {
		public final short keyId;
		public final long generation;
		public final PowerClass<?> powerClass;
		@Nullable public HeldInput action;

		public HeldInputEntry(
				short keyId,
				PowerClass<?> powerClass,
				HeldInput action) {
			this(keyId, 0L, powerClass, action);
		}

		public HeldInputEntry(
				short keyId,
				long generation,
				PowerClass<?> powerClass,
				HeldInput action) {
			this.keyId = keyId;
			this.generation = generation;
			this.powerClass = powerClass;
			this.action = action;
		}
	}

}
