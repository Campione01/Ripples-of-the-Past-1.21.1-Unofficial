package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEyejabEffectGameTests {
    private KingCrimsonEyejabEffectGameTests() {}
    @GameTest(template = "empty", timeoutTicks = 500, batch = "kc_eyejab_effect")
    public static void eyejabAppliesDonorBlindnessAfterActualHit(GameTestHelper helper) {
        KingCrimsonStrikeEffectFixture.identity(helper, KingCrimsonEyejabEffectGameTests.class);
        var suite = new KingCrimsonStrikeEffectFixture(helper, true);
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }
}
