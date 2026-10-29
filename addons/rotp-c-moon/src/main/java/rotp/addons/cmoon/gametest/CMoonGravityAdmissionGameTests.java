package rotp.addons.cmoon.gametest;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.GravityCounterAbility;
import rotp.addons.cmoon.ability.CenterOfGravityAbility;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.condition.AvailableAbilities;

@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonGravityAdmissionGameTests {
    private CMoonGravityAdmissionGameTests() {}

    @GameTest(template = "empty", batch = "cmCounterLiteralFloor", timeoutTicks = 500)
    public static void counterLiteralFloorUsesFiniteGetterAndRechecksWarmInput(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonAddon.class, GravityCounterAbility.class,
                GravityCounterAbility.CounterAction.class, CMoonStandEntity.class, StandPower.class,
                StandEntity.class, AbilityInput.class, EntityActionInstance.class, GravityTestSupport.class,
                CMoonGravityAdmissionGameTests.class, JojoModConfig.class, StandPowerTransitions.class,
                StandAbilityStamina.class, ResolveCounter.class, ResolveModeEffect.class, Power.class,
                AvailableAbilities.class);
        var suite = new GravityTestSupport.FloorSuite(helper, false);
        helper.onEachTick(suite::tick);
    }

    @AfterBatch(batch = "cmCounterLiteralFloor")
    public static void releaseCounterFloor(ServerLevel level) {
        if (GravityTestSupport.active != null) GravityTestSupport.active.close();
    }

    @GameTest(template = "empty", batch = "cmCenterLiteralFloor", timeoutTicks = 400)
    public static void centerLiteralFloorUsesPostResolveUnlockAndRechecksWarmInput(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonAddon.class, CenterOfGravityAbility.class,
                CenterOfGravityAbility.CenterAction.class, CMoonStandEntity.class, StandPower.class,
                StandEntity.class, AbilityInput.class, EntityActionInstance.class, GravityTestSupport.class,
                CMoonGravityAdmissionGameTests.class, JojoModConfig.class, StandPowerTransitions.class,
                StandAbilityStamina.class, ResolveCounter.class, ResolveModeEffect.class, Power.class,
                AvailableAbilities.class);
        var suite = new GravityTestSupport.FloorSuite(helper, true);
        helper.onEachTick(suite::tick);
    }

    @AfterBatch(batch = "cmCenterLiteralFloor")
    public static void releaseCenterFloor(ServerLevel level) {
        if (GravityTestSupport.active != null) GravityTestSupport.active.close();
    }
}
