package com.github.standobyte.jojo.subsystems.entityglow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;

/**
 * Slice 5a framework extension — entity glow channel API.
 *
 * <p>Minimal client-side glow tinting hook used by Gold Experience life_detector
 * to mark living entities visible-through-walls in the user's color for a finite
 * duration. Future consumers may add additional channels (heat / cold / arrow-trail).</p>
 *
 * <p>Slice 5a delivers the channel descriptor + per-entity attach API. Behavior depth
 * (client-side scan loop, color resolution from PowerHud, per-channel timeout) is
 * consumed by Slice 5b GE family follow-up.</p>
 */
public final class EntityGlowChannel {

	public static final EntityGlowChannel STAND_VIEW_OBSTRUCTION = new EntityGlowChannel("stand_view_obstruction");
	public static final EntityGlowChannel GE_LIFE_DETECTOR = new EntityGlowChannel("ge_life_detector");
	public static final EntityGlowChannel HAMON_DETECTOR = new EntityGlowChannel("hamon_detector");
	public static final EntityGlowChannel PILLARMAN_ENHANCED_SENSES = new EntityGlowChannel("pillarman_enhanced_senses");
	private static final List<EntityGlowChannel> CHANNELS = List.of(
			STAND_VIEW_OBSTRUCTION, GE_LIFE_DETECTOR, HAMON_DETECTOR, PILLARMAN_ENHANCED_SENSES);

	private final String channelId;
	private final Map<Entity, GlowState> glowState = new WeakHashMap<>();

	private EntityGlowChannel(String channelId) {
		this.channelId = channelId;
	}

	public String channelId() {
		return channelId;
	}

	/**
	 * @return current channel-specific glow color for {@code target}, if any.
	 *         Slice 5a default: empty. Slice 5b populates.
	 */
	public Optional<OptionalInt> currentGlow(Entity target) {
		GlowState state = glowState.get(target);
		if (state == null) {
			return Optional.empty();
		}
		if (target.tickCount > state.expiresAtTick()) {
			clear(target);
			return Optional.empty();
		}
		return Optional.of(state.color());
	}

	public static Optional<OptionalInt> currentGlowAny(Entity target) {
		for (EntityGlowChannel channel : CHANNELS) {
			Optional<OptionalInt> glow = channel.currentGlow(target);
			if (glow.isPresent()) {
				return glow;
			}
		}
		return Optional.empty();
	}

	public static boolean anyActive() {
		for (EntityGlowChannel channel : CHANNELS) {
			if (channel.hasActive()) {
				return true;
			}
		}
		return false;
	}

	public boolean hasActive() {
		for (Entity target : new ArrayList<>(glowState.keySet())) {
			if (target != null && currentGlow(target).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Tag {@code target} with the given color for {@code durationTicks}.
	 * Slice 5a no-op; Slice 5b implements client-side state.
	 */
	public void apply(Entity target, OptionalInt color, int durationTicks) {
		if (target == null || durationTicks <= 0) {
			return;
		}
		GlowState previous = glowState.get(target);
		boolean previousGlowing = previous != null ? previous.previousGlowing() : target.isCurrentlyGlowing();
		glowState.put(target, new GlowState(color, target.tickCount + durationTicks, previousGlowing));
		target.setGlowingTag(true);
	}

	/**
	 * Clear any active tint on {@code target}.
	 */
	public void clear(Entity target) {
		if (target != null) {
			GlowState state = glowState.remove(target);
			if (state != null) {
				restoreVanillaGlow(target, state);
			}
		}
	}

	public void clearAll() {
		for (Map.Entry<Entity, GlowState> entry : new ArrayList<>(glowState.entrySet())) {
			Entity target = entry.getKey();
			if (target != null) {
				restoreVanillaGlow(target, entry.getValue());
			}
		}
		glowState.clear();
	}

	public void forEachActive(BiConsumer<Entity, OptionalInt> consumer) {
		for (Map.Entry<Entity, GlowState> entry : new ArrayList<>(glowState.entrySet())) {
			Entity target = entry.getKey();
			if (target == null) {
				continue;
			}
			currentGlow(target).ifPresent(color -> consumer.accept(target, color));
		}
	}

	private static void restoreVanillaGlow(Entity target, GlowState state) {
		if (!state.previousGlowing() && target instanceof LivingEntity living && living.hasEffect(MobEffects.GLOWING)) {
			return;
		}
		target.setGlowingTag(state.previousGlowing());
	}

	private static record GlowState(OptionalInt color, int expiresAtTick, boolean previousGlowing) {}
}
