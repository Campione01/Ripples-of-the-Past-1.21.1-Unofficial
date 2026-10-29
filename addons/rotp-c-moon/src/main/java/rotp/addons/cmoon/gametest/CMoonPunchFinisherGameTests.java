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
public final class CMoonPunchFinisherGameTests {
    private CMoonPunchFinisherGameTests() {}

    @GameTest(template = "empty", batch = "cmPunchFinisher", timeoutTicks = 1600)
    public static void entityAttemptAddsFinisherEvenWithoutReturnedHurt(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonPunchFinisherGameTests.class);
        PunchTestSupport.run(helper, PunchTestSupport.Facet.FINISHER);
    }

    @AfterBatch(batch = "cmPunchFinisher")
    public static void release(ServerLevel level) { PunchTestSupport.release(); }
}
