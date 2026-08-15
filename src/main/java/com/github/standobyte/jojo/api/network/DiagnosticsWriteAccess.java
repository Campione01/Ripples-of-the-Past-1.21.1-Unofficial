package com.github.standobyte.jojo.api.network;

import java.util.Arrays;

final class DiagnosticsWriteAccess {
	private static final StackWalker WALKER = StackWalker.getInstance(
			StackWalker.Option.RETAIN_CLASS_REFERENCE);

	private DiagnosticsWriteAccess() {}

	static void requireCaller(
			Class<?> gateway, String... allowedCallerNames) {
		Class<?> caller = WALKER.walk(frames -> frames
				.map(StackWalker.StackFrame::getDeclaringClass)
				.dropWhile(type -> type == DiagnosticsWriteAccess.class
						|| type == gateway)
				.findFirst()
				.orElseThrow(() -> new SecurityException(
						"Missing diagnostics writer caller")));
		boolean allowed = Arrays.stream(allowedCallerNames)
				.anyMatch(caller.getName()::equals);
		if (!allowed) {
			throw new SecurityException(
					"Diagnostics writes are restricted to core network handlers");
		}
	}
}
