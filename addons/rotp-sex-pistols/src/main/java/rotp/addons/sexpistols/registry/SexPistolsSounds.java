package rotp.addons.sexpistols.registry;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import rotp.addons.sexpistols.SexPistolsIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SexPistolsSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(
                    Registries.SOUND_EVENT, SexPistolsIds.MOD_ID);

    // Donor 025 SexPistolsSoundUtil.sayStandReload repeats the line after
    // 100 ticks, for stand reloads and all three special shots alike.
    public static final int MISTA_STAND_RELOAD_DELAY = 100;

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SEX_PISTOLS_SUMMON = sound("sex_pistols_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SEX_PISTOLS_UNSUMMON = sound("sex_pistols_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SEX_PISTOLS_RICOCHET = sound("sex_pistols_ricochet");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SEX_PISTOLS_RICOCHET_YARI =
                    sound("sex_pistols_ricochet_yari");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SEX_PISTOLS_RICOCHET_YORODOMO =
                    sound("sex_pistols_ricochet_yorodomo");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SEX_PISTOLS_RICOCHET_HIT =
                    sound("sex_pistols_ricochet_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SEX_PISTOLS_RICOCHET_HIT_YAHO =
                    sound("sex_pistols_ricochet_hit_yaho");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SEX_PISTOLS_RICOCHET_HIT_YE =
                    sound("sex_pistols_ricochet_hit_ye");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SEX_PISTOLS_HUNGRY = sound("sex_pistols_hungry");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MISTA_SEX_PISTOLS = sound("mista_sex_pistols");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MISTA_PISTOLS = sound("mista_pistols");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MISTA_RECALL = sound("mista_recall");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MISTA_RECALL_2 = sound("mista_recall_2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MISTA_LOADED_FIRE = sound("mista_loaded_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MISTA_LOADED_FIRE_KURAI =
                    sound("mista_loaded_fire_kurai");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MISTA_LOADED_FIRE_SOKODA =
                    sound("mista_loaded_fire_sokoda");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MISTA_LOADED_FIRE_YIK =
                    sound("mista_loaded_fire_yik");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MISTA_STAND_RELOAD = sound("mista_stand_reload");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REVOLVER_OPEN = sound("revolver_open");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REVOLVER_CLOSE = sound("revolver_close");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REVOLVER_SHELLS_OUT =
                    sound("revolver_shellsout");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REVOLVER_SPEEDLOADER =
                    sound("revolver_speedloader");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            FIRE = sound("fire");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DRY_FIRE = sound("dry_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHELL_CASTING_1 = sound("shell_casting_1");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHELL_CASTING_2 = sound("shell_casting_2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHELL_CASTING_3 = sound("shell_casting_3");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHELL_CASTING_4 = sound("shell_casting_4");

    private static final List<DeferredHolder<SoundEvent, SoundEvent>>
            RICOCHET_VARIANTS = List.of(
                    SEX_PISTOLS_RICOCHET,
                    SEX_PISTOLS_RICOCHET_YARI,
                    SEX_PISTOLS_RICOCHET_YORODOMO);
    private static final List<DeferredHolder<SoundEvent, SoundEvent>>
            RICOCHET_HIT_VARIANTS = List.of(
                    SEX_PISTOLS_RICOCHET_HIT,
                    SEX_PISTOLS_RICOCHET_HIT_YAHO,
                    SEX_PISTOLS_RICOCHET_HIT_YE);
    private static final List<DeferredHolder<SoundEvent, SoundEvent>>
            MISTA_SUMMON_VARIANTS = List.of(
                    MISTA_SEX_PISTOLS,
                    MISTA_PISTOLS);
    private static final List<DeferredHolder<SoundEvent, SoundEvent>>
            MISTA_RECALL_VARIANTS = List.of(
                    MISTA_RECALL,
                    MISTA_RECALL_2);
    private static final List<DeferredHolder<SoundEvent, SoundEvent>>
            MISTA_LOADED_FIRE_VARIANTS = List.of(
                    MISTA_LOADED_FIRE,
                    MISTA_LOADED_FIRE_KURAI,
                    MISTA_LOADED_FIRE_SOKODA,
                    MISTA_LOADED_FIRE_YIK);

    private SexPistolsSounds() {}

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    public static SoundEvent randomRicochet(RandomSource random) {
        return pick(RICOCHET_VARIANTS, random).get();
    }

    public static SoundEvent randomRicochetHit(RandomSource random) {
        return pick(RICOCHET_HIT_VARIANTS, random).get();
    }

    public static DeferredHolder<SoundEvent, SoundEvent>
            randomMistaSummon() {
        return pick(MISTA_SUMMON_VARIANTS);
    }

    public static SoundEvent randomMistaRecall(RandomSource random) {
        return pick(MISTA_RECALL_VARIANTS, random).get();
    }

    public static SoundEvent randomMistaLoadedFire(
            RandomSource random) {
        return pick(MISTA_LOADED_FIRE_VARIANTS, random).get();
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> pick(
            List<DeferredHolder<SoundEvent, SoundEvent>> variants,
            RandomSource random) {
        return variants.get(random.nextInt(variants.size()));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> pick(
            List<DeferredHolder<SoundEvent, SoundEvent>> variants) {
        return variants.get(
                ThreadLocalRandom.current().nextInt(variants.size()));
    }
}
