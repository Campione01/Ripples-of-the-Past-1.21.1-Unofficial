package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonImpaleEffectGameTests {
    private KingCrimsonImpaleEffectGameTests() {}
    @GameTest(template = "empty", timeoutTicks = 500, batch = "kc_impale_effect")
    public static void impaleAppliesDonorSlownessAfterActualHit(GameTestHelper helper) {
        KingCrimsonStrikeEffectFixture.identity(helper, KingCrimsonImpaleEffectGameTests.class);
        var suite = new KingCrimsonStrikeEffectFixture(helper, KingCrimsonStrikeEffectFixture.Strike.IMPALE);
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }
}
