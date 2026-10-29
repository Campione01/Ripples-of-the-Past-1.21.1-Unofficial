package rotp.addons.ultimate_lifeform.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

@EventBusSubscriber(modid = UltimateLifeformAddon.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class UltimateLifeformKeyMappings {
    private UltimateLifeformKeyMappings() {}

    // Optional shortcuts with no default key: every skill is on the skill bar, the 1.16 add-on had no keys of its
    // own, and a default here would sit on a core or vanilla key (X, C, R, F).
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        register(event, UltimateLifeformSpec.STONE_FORM);
        register(event, UltimateLifeformSpec.ENHANCED_SENSES);
        register(event, UltimateLifeformSpec.EVASION);
        register(event, UltimateLifeformSpec.ULTIMATE_AGILITY);
        register(event, UltimateLifeformSpec.ULTIMATE_FLIGHT_DASH);
        register(event, UltimateLifeformSpec.ENDERMAN_TELEPORT);
        register(event, UltimateLifeformSpec.BUNNY_SUMMON);
        register(event, UltimateLifeformSpec.PHANTOM_SUMMON);
        register(event, UltimateLifeformSpec.NIGHT_VISION);
    }

    private static void register(RegisterKeyMappingsEvent event, UltimateLifeformSpec spec) {
        event.register(new KeyMapping(UltimateLifeformAddon.skillKeyName(spec),
                KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, "key.categories.rotp_ul"));
    }
}
