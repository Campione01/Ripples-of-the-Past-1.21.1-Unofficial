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
public final class CMoonPunchCounterGameTests {
    private CMoonPunchCounterGameTests() {}

    @GameTest(template = "empty", batch = "cmPunchCounter", timeoutTicks = 2000)
    public static void registeredGuardCounterPunchUsesDetachedHalfPulses(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonPunchCounterGameTests.class);
        PunchTestSupport.run(helper, PunchTestSupport.Facet.COUNTER);
    }

    @GameTest(template = "empty", batch = "cmPunchTeamOwnership", timeoutTicks = 100)
    public static void temporaryTeamCollisionPreservesForeignOwnership(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonPunchCounterGameTests.class);
        PunchTestSupport.runTeamOwnership(helper);
    }

    @AfterBatch(batch = "cmPunchCounter")
    public static void release(ServerLevel level) { PunchTestSupport.release(); }
}
