package com.github.standobyte.jojo.powersystem.entityaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction.GenerationSequence;

public final class ActionGenerationSequenceSmokeTest {
	private ActionGenerationSequenceSmokeTest() {}

	public static void run() {
		testCloneAndEntityIdReuseDoNotRewind();
		testGenerationWrapFailsClosed();
		testCloneSourceContract();
	}

	private static void testCloneAndEntityIdReuseDoNotRewind() {
		GenerationSequence sequence = new GenerationSequence();
		long originalEntity = sequence.nextAfter(0L);
		long reusedEntityId = sequence.nextAfter(0L);
		check(reusedEntityId > originalEntity,
				"a replacement entity reusing an ID must receive a newer generation");
		long clonedPlayer = sequence.nextAfter(reusedEntityId);
		check(clonedPlayer > reusedEntityId,
				"a same-level player clone must advance the inherited generation floor");
		check(sequence.nextAfter(1L) > clonedPlayer,
				"a stale local counter must not rewind the server sequence");
	}

	private static void testGenerationWrapFailsClosed() {
		GenerationSequence exhausted = new GenerationSequence(Long.MAX_VALUE);
		expectThrows(IllegalStateException.class,
				() -> exhausted.nextAfter(0L),
				"generation wrap must fail instead of becoming non-positive");
		expectThrows(IllegalArgumentException.class,
				() -> new GenerationSequence().nextAfter(-1L),
				"negative generation floors must fail closed");
	}

	private static void testCloneSourceContract() {
		String source = read(
				"src/main/java/com/github/standobyte/jojo/powersystem/entityaction/LivingComponentAction.java");
		check(source.contains("replacement.actionGenerationCounter = Math.max(")
				&& source.contains("replacement.advanceActionGeneration(null)"),
				"player clone handling must carry and advance the generation floor");
	}

	private static String read(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static <T extends Throwable> void expectThrows(
			Class<T> type, Runnable action, String message) {
		try {
			action.run();
		}
		catch (Throwable error) {
			if (type.isInstance(error)) {
				return;
			}
			throw new AssertionError(message, error);
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
