package com.github.standobyte.jojo.subsystems.rollback;

record RollbackUsage(
		int entities,
		int blockMutations,
		int containerSlots,
		long serializedBytes,
		long captureNanos) {

	static final RollbackUsage ZERO = new RollbackUsage(0, 0, 0, 0L, 0L);

	RollbackUsage {
		if (entities < 0
				|| blockMutations < 0
				|| containerSlots < 0
				|| serializedBytes < 0L
				|| captureNanos < 0L) {
			throw new IllegalArgumentException("rollback usage cannot be negative");
		}
	}

	RollbackUsage plus(RollbackUsage delta) {
		return new RollbackUsage(
				saturatedAdd(entities, delta.entities),
				saturatedAdd(blockMutations, delta.blockMutations),
				saturatedAdd(containerSlots, delta.containerSlots),
				saturatedAdd(serializedBytes, delta.serializedBytes),
				saturatedAdd(captureNanos, delta.captureNanos));
	}

	RollbackUsage nextTick() {
		return new RollbackUsage(
				entities, blockMutations, containerSlots, serializedBytes, 0L);
	}

	private static int saturatedAdd(int left, int right) {
		try {
			return Math.addExact(left, right);
		}
		catch (ArithmeticException overflow) {
			return Integer.MAX_VALUE;
		}
	}

	private static long saturatedAdd(long left, long right) {
		try {
			return Math.addExact(left, right);
		}
		catch (ArithmeticException overflow) {
			return Long.MAX_VALUE;
		}
	}
}
