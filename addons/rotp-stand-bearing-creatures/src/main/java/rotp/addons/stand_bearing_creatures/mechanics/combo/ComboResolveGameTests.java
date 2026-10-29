package rotp.addons.stand_bearing_creatures.mechanics.combo;

import java.util.List;
import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;

@GameTestHolder(MobsWithPowersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ComboResolveGameTests {
    private ComboResolveGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void missingResolveUsesRandomActionFallback(GameTestHelper helper) {
        PowerComboData world = ComboManager.stand(
                ResourceLocation.fromNamespaceAndPath("jojo_ripples", "the_world"));
        helper.assertTrue(world != null, "The World's combo resource was not loaded");
        helper.assertTrue(!world.combosByResolve().containsKey(4),
                "The World's test resource no longer has a missing Resolve 4 entry");
        helper.assertTrue(world.availableCombos(4).isEmpty(),
                "Missing Resolve enabled staged combos instead of random-action fallback");
        helper.assertTrue(world.availableCombos(2).equals(world.combosByResolve().get(2)),
                "A configured Resolve level lost its exact combo selection");

        PowerComboData unrestricted = new PowerComboData(Map.of(), null, null,
                Map.of("second", List.of(), "first", List.of()));
        helper.assertTrue(unrestricted.availableCombos(4).equals(List.of("first", "second")),
                "An absent Resolve table no longer exposes all combos");
        PowerComboData disabled = new PowerComboData(Map.of(2, List.of()), null, null,
                unrestricted.combos());
        helper.assertTrue(disabled.availableCombos(2).isEmpty(),
                "An explicitly empty Resolve entry enabled combos");
        helper.succeed();
    }
}
