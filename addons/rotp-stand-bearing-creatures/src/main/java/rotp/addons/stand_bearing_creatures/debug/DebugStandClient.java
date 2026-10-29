package rotp.addons.stand_bearing_creatures.debug;

import java.util.OptionalInt;
import java.util.Set;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;

import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.util.RainbowTextUtil;

@EventBusSubscriber(
        modid = MobsWithPowersAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class DebugStandClient {
    private static final Set<String> DEBUG_ACTIONS = Set.of(
            "give_random_stand",
            "give_random_power",
            "remove_stand",
            "remove_power",
            "no_ai",
            "use_random_action",
            "toggle_summon",
            "aggro_one_to_two");

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        AbilitySelectionVisualPolicies.register(
                MobsWithPowersAddon.id("debug_rainbow"),
                query -> {
                    var key = query.ability().abilityType.registryKey;
                    if (key == null
                            || !key.getNamespace().equals(
                                    MobsWithPowersAddon.MOD_ID)
                            || !DEBUG_ACTIONS.contains(key.getPath())) {
                        return OptionalInt.empty();
                    }
                    if (key.getPath().equals("no_ai")
                            && DebugStandAbilities.aimedLiving(
                                    query.power())
                                    instanceof Mob mob
                            && mob.isNoAi()) {
                        return OptionalInt.of(0xFF00FF00);
                    }
                    return OptionalInt.of(
                            RainbowTextUtil.rainbowArgb());
                });
    }

    private DebugStandClient() {}
}
