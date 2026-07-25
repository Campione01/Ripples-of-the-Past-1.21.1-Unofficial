package com.github.standobyte.jojo.client.entityanim.action;

import java.util.Comparator;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import it.unimi.dsi.fastutil.floats.Float2DoubleArrayMap;
import it.unimi.dsi.fastutil.floats.Float2DoubleMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectArrayMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectMap;

public class AnimObjTimeline<V> {
	private Float2ObjectMap<V> timeline = new Float2ObjectArrayMap<>();
	
	public void add(float keyframeTimeInSeconds, V value) {
		timeline.put(keyframeTimeInSeconds, value);
	}
	
	@ApiStatus.Internal
	public void sort() {
		if (!timeline.isEmpty()) {
			timeline = timeline.float2ObjectEntrySet().stream()
					.sorted(Comparator.comparingDouble(Float2ObjectMap.Entry::getFloatKey))
					.collect(Float2ObjectArrayMap::new, 
							(map, entry) -> map.put(entry.getFloatKey(), entry.getValue()), 
							(map1, map2) -> map1.putAll(map2));
		}
	}
	
	@Nullable
	public V getCurValue(float timeInSeconds) {
		V latestValue = null;
		for (Float2ObjectMap.Entry<V> entry : timeline.float2ObjectEntrySet()) {
			if (entry.getFloatKey() <= timeInSeconds) {
				latestValue = entry.getValue();
			}
			else break;
		}
		return latestValue;
	}
	
	public Iterable<Float2ObjectMap.Entry<V>> getEntries() {
		return timeline.float2ObjectEntrySet();
	}
	
	
	public static class Double {
		Float2DoubleMap timeline = new Float2DoubleArrayMap();
		
		public void add(float keyframeTimeInSeconds, double value) {
			timeline.put(keyframeTimeInSeconds, value);
		}
		
		@ApiStatus.Internal
		public void sort() {
			if (!timeline.isEmpty()) {
				timeline = timeline.float2DoubleEntrySet().stream()
						.sorted(Comparator.comparingDouble(Float2DoubleMap.Entry::getFloatKey))
						.collect(Float2DoubleArrayMap::new, 
								(map, entry) -> map.put(entry.getFloatKey(), entry.getDoubleValue()), 
								(map1, map2) -> map1.putAll(map2));
			}
		}
		
		@Nullable
		public double getCurValue(float timeInSeconds) {
			double latestValue = 0;
			for (Float2DoubleMap.Entry entry : timeline.float2DoubleEntrySet()) {
				if (entry.getFloatKey() <= timeInSeconds) {
					latestValue = entry.getDoubleValue();
				}
				else break;
			}
			return latestValue;
		}
	}
}
