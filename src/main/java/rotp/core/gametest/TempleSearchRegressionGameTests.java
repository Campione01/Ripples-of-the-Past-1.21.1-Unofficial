package rotp.core.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("rotp_owner_decisions")
@PrefixGameTestTemplate(false)
public final class TempleSearchRegressionGameTests {
    private TempleSearchRegressionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pillarSearchDefersTerrainFootprint(GameTestHelper helper) {
        TempleBiomeGateGameTests.pillarmanTempleTestsBiomeBeforeFootprint(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hamonSearchDefersTerrainFootprint(GameTestHelper helper) {
        TempleBiomeGateGameTests.hamonTempleTestsBiomeBeforeFootprint(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pillarGenerationRetainsDonorAnchor(GameTestHelper helper) {
        TempleAnchorGameTests.pillarmanTempleAnchorsOnFootprintMinimum(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hamonGenerationRetainsDonorAnchor(GameTestHelper helper) {
        TempleAnchorGameTests.hamonTempleAnchorsOnFlooredFootprintMinimum(helper);
    }
}
