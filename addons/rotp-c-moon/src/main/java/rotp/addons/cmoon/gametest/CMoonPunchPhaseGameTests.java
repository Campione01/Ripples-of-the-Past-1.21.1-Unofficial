package rotp.addons.cmoon.gametest;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;

@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonPunchPhaseGameTests {
    private CMoonPunchPhaseGameTests() {}

    @GameTest(template = "empty", batch = "cmPunchPhase", timeoutTicks = 1200)
    public static void laterPunchPhasesSampleLiveGettersAndThenFreeze(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonPunchPhaseGameTests.class);
        PunchTestSupport.run(helper, PunchTestSupport.Facet.PHASE);
    }

    @AfterBatch(batch = "cmPunchPhase")
    public static void release(ServerLevel level) { PunchTestSupport.release(); }
}
