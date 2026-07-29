package com.github.standobyte.jojo.api;

import com.github.standobyte.jojo.api.control.PlayerOperationPoliciesSmokeTest;
import com.github.standobyte.jojo.api.leap.LeapAccessPoliciesSmokeTest;
import com.github.standobyte.jojo.api.rps.RpsCheatRegistrationsSmokeTest;
import com.github.standobyte.jojoimpl.npc.rps.RpsCheatStateSmokeTest;

public final class HeavensDoorCoreFocusedSmokeTest {
	private HeavensDoorCoreFocusedSmokeTest() {}

	public static void main(String[] args) {
		PlayerOperationPoliciesSmokeTest.run();
		LeapAccessPoliciesSmokeTest.run();
		RpsCheatRegistrationsSmokeTest.run();
		RpsCheatStateSmokeTest.run();
		HeavensDoorCoreBoundarySmokeTest.run();
		System.out.println(
				"Heaven's Door core focused smoke tests passed.");
	}
}
