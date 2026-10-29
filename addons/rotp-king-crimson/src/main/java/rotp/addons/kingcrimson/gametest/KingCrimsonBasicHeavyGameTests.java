package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonBasicHeavyGameTests{
    private KingCrimsonBasicHeavyGameTests(){}
    @GameTest(template="empty",timeoutTicks=3000,batch="kc_basic_heavy")
    public static void ordinaryHeavyUsesDetachedDonorKnockback(GameTestHelper helper){
        KingCrimsonBasicMeleeFixture.identity(helper,KingCrimsonBasicHeavyGameTests.class);
        var suite=new KingCrimsonBasicMeleeFixture(helper,true);
        try{suite.start();}catch(RuntimeException|Error error){suite.close();throw error;}
    }
}
