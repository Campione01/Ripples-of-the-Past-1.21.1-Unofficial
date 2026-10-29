package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonChopEffectGameTests {
    private KingCrimsonChopEffectGameTests() {}
    @GameTest(template = "empty", timeoutTicks = 500, batch = "kc_chop_effect")
    public static void chopAppliesDonorSlownessAfterActualHit(GameTestHelper helper) {
        KingCrimsonStrikeEffectFixture.identity(helper, KingCrimsonChopEffectGameTests.class);
        var suite = new KingCrimsonStrikeEffectFixture(helper, false);
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }
}
