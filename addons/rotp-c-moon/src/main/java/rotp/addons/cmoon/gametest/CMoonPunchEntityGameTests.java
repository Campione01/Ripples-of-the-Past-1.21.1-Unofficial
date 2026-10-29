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
public final class CMoonPunchEntityGameTests {
    private CMoonPunchEntityGameTests() {}

    @GameTest(template = "empty", batch = "cmPunchEntity", timeoutTicks = 1000)
    public static void registeredPunchHitsPickableEntitiesWithRawOne(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonPunchEntityGameTests.class);
        PunchTestSupport.run(helper, PunchTestSupport.Facet.ENTITY);
    }

    @AfterBatch(batch = "cmPunchEntity")
    public static void release(ServerLevel level) { PunchTestSupport.release(); }
}
