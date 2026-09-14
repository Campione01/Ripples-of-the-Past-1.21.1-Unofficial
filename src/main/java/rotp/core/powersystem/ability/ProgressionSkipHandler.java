package rotp.core.powersystem.ability;

import rotp.core.powersystem.standpower.StandPower;

public interface ProgressionSkipHandler {
	void onProgressionSkipped(StandPower power);
}
