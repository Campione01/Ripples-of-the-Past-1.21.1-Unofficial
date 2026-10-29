package rotp.addons.echoes.init;

import java.util.concurrent.ThreadLocalRandom;

import rotp.addons.echoes.RotpEchoes;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EchoesSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, RotpEchoes.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SUMMON_VOICE_ACT3 = register("summon_voice_act3");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_ATTACK =
            register("heavy_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRAGE_HIT_SOUND =
            register("barrage_hit_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BARRAGE_HIT_SOUND_2 = register("barrage_hit_sound_2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BARRAGE_HIT_SOUND_3 = register("barrage_hit_sound_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_BGM =
            register("skill_bgm");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_USE_SOUND =
            register("skill_use_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SKILL_USE_SOUND_2 = register("skill_use_sound_2");

    private EchoesSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, SoundEvent::createVariableRangeEvent);
    }

    public static SoundEvent randomBarrageHitSound() {
        return switch (ThreadLocalRandom.current().nextInt(3)) {
            case 1 -> BARRAGE_HIT_SOUND_2.get();
            case 2 -> BARRAGE_HIT_SOUND_3.get();
            default -> BARRAGE_HIT_SOUND.get();
        };
    }
}
