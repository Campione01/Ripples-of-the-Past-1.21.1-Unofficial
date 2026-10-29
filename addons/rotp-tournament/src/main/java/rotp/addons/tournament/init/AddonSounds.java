package rotp.addons.tournament.init;

import rotp.addons.tournament.AddonMain;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AddonSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(
                    Registries.SOUND_EVENT, AddonMain.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            ASSGORE_RECORD = SOUNDS.register(
                    "assgore_record",
                    SoundEvent::createVariableRangeEvent);

    private AddonSounds() {}
}
