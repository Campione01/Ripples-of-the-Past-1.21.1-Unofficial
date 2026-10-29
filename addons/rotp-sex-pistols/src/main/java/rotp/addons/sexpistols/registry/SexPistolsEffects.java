package rotp.addons.sexpistols.registry;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.sexpistols.JoyfulEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SexPistolsEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(
                    Registries.MOB_EFFECT, SexPistolsIds.MOD_ID);

    public static final DeferredHolder<MobEffect, JoyfulEffect> JOYFUL =
            EFFECTS.register(
                    SexPistolsIds.JOYFUL_PATH, JoyfulEffect::new);

    private SexPistolsEffects() {}

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
