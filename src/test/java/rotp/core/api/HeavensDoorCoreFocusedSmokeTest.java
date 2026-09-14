package rotp.core.api;

import rotp.core.api.control.PlayerOperationPoliciesSmokeTest;
import rotp.core.api.leap.LeapAccessPoliciesSmokeTest;
import rotp.core.api.rps.RpsCheatRegistrationsSmokeTest;
import rotp.core.impl.npc.rps.RpsCheatStateSmokeTest;

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
