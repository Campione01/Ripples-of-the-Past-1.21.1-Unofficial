package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonBasicPunchGameTests{
    private KingCrimsonBasicPunchGameTests(){}
    @GameTest(template="empty",timeoutTicks=3000,batch="kc_basic_punch")
    public static void ordinaryCounterPunchUsesDetachedDonorKnockback(GameTestHelper helper){
        KingCrimsonBasicMeleeFixture.identity(helper,KingCrimsonBasicPunchGameTests.class);
        var suite=new KingCrimsonBasicMeleeFixture(helper,false);
        try{suite.start();}catch(RuntimeException|Error error){suite.close();throw error;}
    }
}
