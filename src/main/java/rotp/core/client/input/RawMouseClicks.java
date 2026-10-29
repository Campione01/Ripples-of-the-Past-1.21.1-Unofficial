package rotp.core.client.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The raw presses of the mouse buttons in the current client tick, as 1.16's InputHandler took both buttons' clicks at
 * the start of a tick (:343-349), and the presses replayed for a vanilla attack or use click that came with no raw press
 * of its button, kept until their key mapping is up. Free of client classes so that a GameTest can ask it.
 */
public final class RawMouseClicks<K> {
	private final Set<K> pressedThisTick = new HashSet<>();
	private final Map<K, BooleanSupplier> replayed = new HashMap<>();

	public void rawPress(K button) {
		pressedThisTick.add(button);
	}

	/** Whether the button had a raw press since the last client tick ended: 1.16's LMB+RMB pose took both in one tick. */
	public boolean pressedThisTick(K button) {
		return pressedThisTick.contains(button);
	}

	/**
	 * Whether a vanilla click of the button that no held press of it answers is to be replayed as a press: not after a raw
	 * press of it went through since the last tick, whose decision stands (a raw press and release in one tick leave no
	 * held press, and a replay would press the ability again and keep a press no raw release lets go).
	 */
	public boolean replaysVanillaClick(K button) {
		return !pressedThisTick.contains(button);
	}

	/** A replayed press, to be let go once its key mapping is up (no raw release of the button will come for it). */
	public void replayed(K button, BooleanSupplier mappingDown) {
		replayed.put(button, mappingDown);
	}

	/** At the end of a client tick: the replayed presses whose key mapping is up, to release; the tick's raw presses go. */
	public List<K> endTick() {
		pressedThisTick.clear();
		List<K> release = new ArrayList<>();
		var iter = replayed.entrySet().iterator();
		while (iter.hasNext()) {
			var entry = iter.next();
			if (!entry.getValue().getAsBoolean()) {
				release.add(entry.getKey());
				iter.remove();
			}
		}
		return release;
	}

	/** A raw release of the button lets its replayed press go with it. */
	public void rawRelease(K button) {
		replayed.remove(button);
	}

	public void clear() {
		pressedThisTick.clear();
		replayed.clear();
	}
}
