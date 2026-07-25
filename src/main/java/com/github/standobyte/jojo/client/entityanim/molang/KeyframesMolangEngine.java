package com.github.standobyte.jojo.client.entityanim.molang;

import team.unnamed.mocha.MochaEngine;

/*
 * Integrates Mocha v3.0.1 by Unnamed Team.
 * Copyright (c) 2021-2025 Unnamed Team. Licensed under the MIT License.
 * Fixed upstream revision:
 * https://github.com/unnamed/mocha/commit/eac679c71a1c4211ccf5ec2ff9a31b25c52e7509
 * Modification notice (2026-07-26): wired the shared interpreter to this
 * project's no-Javassist engine and animation-query namespace.
 */
public class KeyframesMolangEngine {
	private static MochaEngine<?> mochaInstance;
	
	public static void init() {
		if (mochaInstance == null) {
			mochaInstance = MochaEngineWithoutJavassist.createStandard();
			mochaInstance.scope().set(AnimMolangQuery.NAMESPACE, AnimMolangQuery.instance);
		}
	}
	
	public static MochaEngine<?> get() {
		return mochaInstance;
	}
}
