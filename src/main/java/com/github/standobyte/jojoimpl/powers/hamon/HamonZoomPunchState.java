package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.LivingEntity;

public final class HamonZoomPunchState {
	private static final long STALE_TICKS = 3L;
	private static final Map<LivingEntity, ZoomPunchUse> ACTIVE_ZOOM_PUNCHES =
			Collections.synchronizedMap(new WeakHashMap<>());

	private HamonZoomPunchState() {}

	public static void add(LivingEntity entity) {
		long gameTime = entity.level().getGameTime();
		ACTIVE_ZOOM_PUNCHES.compute(entity, (__, state) -> {
			ZoomPunchUse use = state != null ? state : new ZoomPunchUse();
			use.count++;
			use.lastSeenTick = gameTime;
			return use;
		});
	}

	public static void touch(LivingEntity entity) {
		long gameTime = entity.level().getGameTime();
		ACTIVE_ZOOM_PUNCHES.computeIfPresent(entity, (__, state) -> {
			state.lastSeenTick = gameTime;
			return state;
		});
	}

	public static void remove(LivingEntity entity) {
		ACTIVE_ZOOM_PUNCHES.computeIfPresent(entity, (__, state) -> {
			state.count--;
			return state.count > 0 ? state : null;
		});
	}

	public static boolean isUsingZoomPunch(LivingEntity entity) {
		cleanupStale(entity);
		ZoomPunchUse state = ACTIVE_ZOOM_PUNCHES.get(entity);
		return state != null && state.count > 0;
	}

	private static void cleanupStale(LivingEntity entity) {
		ZoomPunchUse state = ACTIVE_ZOOM_PUNCHES.get(entity);
		if (state != null && entity.level().getGameTime() - state.lastSeenTick > STALE_TICKS) {
			ACTIVE_ZOOM_PUNCHES.remove(entity);
		}
	}

	private static final class ZoomPunchUse {
		private int count;
		private long lastSeenTick;
	}
}
