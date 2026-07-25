package com.github.standobyte.jojo.client.entityanim.action;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.floats.Float2ObjectMap;

public class AnimInstructionTimelines {
	@Nullable public AnimObjTimeline<AnimActionPhase> phases;
	@Nullable public Map<String, AnimObjTimeline<String>> stringVals = new HashMap<>();
	@Nullable public Map<String, AnimObjTimeline.Double> numericVals = new HashMap<>();
	
	
	@Nullable
	public String getStringTimelineVal(String key, float animTime) {
		if (stringVals == null) {
			return null;
		}
		AnimObjTimeline<String> timeline = stringVals.get(key);
		if (timeline == null) {
			return null;
		}
		return timeline.getCurValue(animTime);
	}
	
	public double getNumericTimelineVal(String key, float animTime) {
		if (numericVals == null) {
			return 0;
		}
		AnimObjTimeline.Double timeline = numericVals.get(key);
		if (timeline == null) {
			return 0;
		}
		return timeline.getCurValue(animTime);
	}
	
	
	public void onFinishedParsing() {
		if (stringVals != null) {
			Iterator<Map.Entry<String, AnimObjTimeline<String>>> iter = stringVals.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<String, AnimObjTimeline<String>> entry = iter.next();
				timelineToNumeric(entry.getValue()).ifPresent(numericTimeline -> {
					if (numericVals == null) {
						numericVals = new HashMap<>();
					}
					numericVals.put(entry.getKey(), numericTimeline);
					iter.remove();
				});
			}
		}

		if (phases != null) phases.sort();
		if (stringVals != null) stringVals.values().forEach(AnimObjTimeline::sort);
		if (numericVals != null) numericVals.values().forEach(AnimObjTimeline.Double::sort);
	}
	
	private static Optional<AnimObjTimeline.Double> timelineToNumeric(AnimObjTimeline<String> stringTimeline) {
		AnimObjTimeline.Double timeline = new AnimObjTimeline.Double();
		for (Float2ObjectMap.Entry<String> entry : stringTimeline.getEntries()) {
			try {
				double numericVal = Double.parseDouble(entry.getValue());
				timeline.add(entry.getFloatKey(), numericVal);
			}
			catch (NumberFormatException notNumeric) {
				return Optional.empty();
			}
		}
		return Optional.of(timeline);
	}

}
