package rotp.addons.scarymonsters;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.scarymonsters.ability.ScaryMonstersAbility;
import rotp.addons.scarymonsters.ability.ScaryMonstersAbility.Mode;
import rotp.addons.scarymonsters.ability.ScaryMonstersBarrageAbility;
import rotp.addons.scarymonsters.ability.ScaryMonstersHeavyAbility;
import rotp.addons.scarymonsters.ability.ScaryMonstersHeavyAbility.Kind;
import rotp.addons.scarymonsters.ability.ScaryMonstersPunchAbility;
import rotp.addons.scarymonsters.client.ScaryMonstersClientEvents;
import rotp.addons.scarymonsters.effect.BleedingEffect;
import rotp.addons.scarymonsters.effect.DinosaurFormEffect;
import rotp.addons.scarymonsters.effect.DragonFormEffect;
import rotp.addons.scarymonsters.entity.ScaryDinosaurEntity;
import rotp.addons.scarymonsters.entity.ScaryMonstersStandEntity;
import rotp.addons.scarymonsters.entity.TemplatePickaxeEntity;
import rotp.addons.scarymonsters.item.CoffeeItem;
import rotp.addons.scarymonsters.network.ScaryMonstersStatePayload;
import rotp.addons.scarymonsters.stand.ScaryMonstersStandType;
import rotp.addons.scarymonsters.state.ScaryMonstersState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(ScaryMonstersAddon.MOD_ID)
public final class ScaryMonstersAddon {
    public static final String MOD_ID = "scary_monsters";
    public static final String NETWORK_PROTOCOL_VERSION = "1";
    public static final double EFFECTIVE_RANGE = 4.0D;
    public static final double MAX_RANGE = 4.0D;
    public static final double DRAGON_FORM_DAMAGE_REDUCTION = 0.5D;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<ScaryMonstersState>> STATE =
            ATTACHMENTS.register(
                    "state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new ScaryMonstersState() : null)
                            .build());

    public static final DeferredHolder<MobEffect, DragonFormEffect> DRAGON_FORM =
            EFFECTS.register("dragon_form", DragonFormEffect::new);
    public static final DeferredHolder<MobEffect, DinosaurFormEffect> DINOSAUR_FORM =
            EFFECTS.register("dinosaur_form", DinosaurFormEffect::new);
    public static final DeferredHolder<MobEffect, BleedingEffect> BLEEDING =
            EFFECTS.register("bleeding", BleedingEffect::new);

    public static final ResourceKey<JukeboxSong> DIEGO_SONG = ResourceKey.create(
            Registries.JUKEBOX_SONG, id("diego_music"));
    public static final DeferredItem<Item> COFFEE_FULL = ITEMS.register(
            "coffee_full", () -> new CoffeeItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> COFFEE_EMPTY = ITEMS.register(
            "coffee_empty", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> DIEGO_MUSIC_ITEM = ITEMS.register(
            "diego_music",
            () -> new Item(new Item.Properties()
                    .jukeboxPlayable(DIEGO_SONG)
                    .rarity(Rarity.RARE)
                    .stacksTo(1)));

    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON_VOICE = sound("summon_voice_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_VOICE = sound("heavy_voice_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_VOICE = sound("finisher_voice_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRAGE_VOICE = sound("barrage_voice_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> DYNAMIC_VISION_SOUND = sound("dynamic_vision_activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLASH_SOUND = sound("slash_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent> DINOSAURIFY_VOICE = sound("dinosaurify_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent> COFFEE_SOUND = sound("coffee");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRAGE_SWING = sound("barrage_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAGON_TRANSFORM = sound("jiao");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIEGO_MUSIC = sound("diego_music");
    public static final OstSoundList OST = new OstSoundList(id("diego_bgm"), SOUNDS);

    public static final DeferredHolder<EntityType<?>, EntityType<ScaryMonstersStandEntity>> STAND_ENTITY =
            ENTITY_TYPES.register("example_stand", key -> EntityType.Builder
                    .<ScaryMonstersStandEntity>of(ScaryMonstersStandEntity::new, MobCategory.MISC)
                    .noSave().noSummon().sized(0.7F, 2.1F)
                    .clientTrackingRange(16).updateInterval(2)
                    .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<ScaryDinosaurEntity>> DINOSAUR =
            ENTITY_TYPES.register("dinosaur", key -> EntityType.Builder
                    .<ScaryDinosaurEntity>of(ScaryDinosaurEntity::new, MobCategory.MONSTER)
                    .sized(0.5F, 1.2F).clientTrackingRange(80).updateInterval(1)
                    .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<TemplatePickaxeEntity>> TEMPLATE_PICKAXE =
            ENTITY_TYPES.register("example_pickaxe", key -> EntityType.Builder
                    .<TemplatePickaxeEntity>of(TemplatePickaxeEntity::new, MobCategory.MISC)
                    .noSave().noSummon().sized(1.0F, 1.0F)
                    .clientTrackingRange(32).updateInterval(2)
                    .build(key.toString()));

    public static final DeferredHolder<StandType, ScaryMonstersStandType> SCARY_MONSTERS =
            STAND_TYPES.register("example_stand", ScaryMonstersAddon::createStand);

    private static final Map<String, DeferredHolder<AbilityType<?>, ? extends AbilityType<?>>> ABILITIES =
            abilities();
    // 1.16 registered example_stand_block as a plain StandEntityBlock, so the core treats it as the Stand's guard.
    private static final String BLOCK = "example_stand_block";
    private static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityBlockAbility>> BLOCK_ABILITY =
            ABILITY_TYPES.register(BLOCK, key -> new AbilityType<>(key, StandEntityBlockAbility::new));

    public ScaryMonstersAddon(IEventBus bus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(1)
                // stopsOnHeavyAttack and the auto-guard opt-out (R146 core)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ENTITY_ACTION_HOOKS_V1)
                // StandUserGuard#blockedWhole for the Dynamic Vision counter (R147 core)
                || !RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_USER_GUARD_V1)) {
            throw new IllegalStateException("Scary Monsters requires ROTP addon ABI 1 with entity action hooks"
                    + " (a core from the same release).");
        }
        ABILITY_TYPES.register(bus);
        ENTITY_TYPES.register(bus);
        STAND_TYPES.register(bus);
        EFFECTS.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        ATTACHMENTS.register(bus);
        bus.addListener(ScaryMonstersEvents::registerAttributes);
        bus.addListener(ScaryMonstersAddon::registerPayloads);
        bus.addListener(ScaryMonstersAddon::addCreativeTabItems);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, ScaryMonstersConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) {
            ScaryMonstersClientEvents.registerConfigScreen(modContainer);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 1.16 listed Diego's music disc in the ROTP main tab; the coffee items had no tab. */
    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == rotp.core.init.ModItems.MAIN_TAB.getKey()) {
            event.accept(DIEGO_MUSIC_ITEM.get());
        }
    }
    public static ScaryMonstersState state(LivingEntity entity) { return entity.getData(STATE); }
    public static boolean isUser(LivingEntity entity) {
        var power = rotp.core.powersystem.standpower.StandPower.get(entity);
        return power != null && power.getPowerType() == SCARY_MONSTERS.get();
    }

    private static DeferredHolder<AbilityType<?>, AbilityType<ScaryMonstersAbility>> ability(String id, Mode mode) {
        return ABILITY_TYPES.register(id, key -> new AbilityType<>(key,
                (type, abilityId) -> new ScaryMonstersAbility(type, abilityId, mode)));
    }
    // 1.16 PunchLeftAction and BarrageAction were a StandEntityLightAttack and a StandEntityMeleeBarrage.
    private static DeferredHolder<AbilityType<?>, AbilityType<ScaryMonstersPunchAbility>> punch(String id) {
        return ABILITY_TYPES.register(id, key -> new AbilityType<>(key, ScaryMonstersPunchAbility::new));
    }
    private static DeferredHolder<AbilityType<?>, AbilityType<ScaryMonstersBarrageAbility>> barrage(String id) {
        return ABILITY_TYPES.register(id, key -> new AbilityType<>(key, ScaryMonstersBarrageAbility::new));
    }
    // 1.16 registered these four as StandEntityHeavyAttacks.
    private static DeferredHolder<AbilityType<?>, AbilityType<ScaryMonstersHeavyAbility>> heavy(String id, Kind kind) {
        return ABILITY_TYPES.register(id, key -> new AbilityType<>(key,
                (type, abilityId) -> new ScaryMonstersHeavyAbility(type, abilityId, kind)));
    }
    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ScaryMonstersAddon.id(id)));
    }
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_PROTOCOL_VERSION).playToClient(
                ScaryMonstersStatePayload.TYPE,
                ScaryMonstersStatePayload.STREAM_CODEC,
                ScaryMonstersStatePayload::handle);
    }

    private static ScaryMonstersStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder();
        ABILITIES.forEach((name, type) -> moveset.addAbility(name, (AbilityType<?>) type.get()));
        moveset.addAbility(BLOCK, BLOCK_ABILITY);
        moveset.makeControlScheme("hotbar")
                .bind("example_stand_punch", InputMethod.CLICK, InputKey.LMB)
                .bind("example_stand_barrage", InputMethod.HOLD, InputKey.LMB)
                .bind("example_stand_heavy_punch", InputMethod.CLICK, InputKey.RMB)
                .bind("example_stand_block", InputMethod.HOLD, InputKey.RMB)
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar("example_stand_finisher_punchs_wish_its_tail", 0, InputMethod.CLICK)
                .addToHotbar("slash", 0, InputMethod.CLICK)
                .addToHotbar("dynamic_vision", 0, InputMethod.CLICK)
                .addToHotbar("dynamic_vision_close", 0, InputMethod.CLICK)
                .addToHotbar("dragon", 0, InputMethod.HOLD)
                .addToHotbar("dragon_close", 0, InputMethod.CLICK)
                .addToHotbar("dinosaurify", 0, InputMethod.HOLD)
                .addToHotbar("dinosaurify_revert", 0, InputMethod.HOLD)
                .addToHotbar("block_to_dinosaur", 0, InputMethod.HOLD)
                .addToHotbar("revert_block_to_dinosaur", 0, InputMethod.HOLD)
                .addToHotbar("dinosaur_form", 0, InputMethod.HOLD)
                .addToHotbar("dinosaur_form_close", 0, InputMethod.HOLD)
                .finalizeControlScheme();
        moveset.makeControlScheme("keybinds")
                .bind("example_stand_punch", InputMethod.CLICK, InputKey.LMB)
                .bind("example_stand_barrage", InputMethod.HOLD, InputKey.LMB)
                .bind("example_stand_heavy_punch", InputMethod.CLICK, InputKey.RMB)
                .bind("example_stand_block", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme();
        for (String action : ABILITIES.keySet()) {
            if (!action.equals("example_stand_finisher_punch")) {
                moveset.addSkill(StandUnlockableSkill.startingAbility(action));
            }
        }
        moveset.addSkill(StandUnlockableSkill.startingAbility(BLOCK));
        ScaryMonstersStandType stand = new ScaryMonstersStandType(
                new StandStats.Builder().power(13.0D).speed(13.0D)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(14.0D).precision(8.0D)
                        .randomWeight(2.0D).build(),
                moveset, STAND_ENTITY.get(), id);
        stand.standDimensions(0.7F, 2.1F);
        stand.summonShout(() -> SUMMON_VOICE);
        stand.summonSound(() -> ModSoundEvents.STAND_SUMMON);
        stand.unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON);
        stand.ost(() -> OST);
        return stand;
    }

    private static Map<String, DeferredHolder<AbilityType<?>, ? extends AbilityType<?>>> abilities() {
        Map<String, DeferredHolder<AbilityType<?>, ? extends AbilityType<?>>> map = new LinkedHashMap<>();
        map.put("example_stand_punch", punch("example_stand_punch"));
        map.put("example_stand_barrage", barrage("example_stand_barrage"));
        map.put("example_stand_finisher_punch", heavy("example_stand_finisher_punch", Kind.FINISHER));
        map.put("example_stand_heavy_punch", heavy("example_stand_heavy_punch", Kind.HEAVY));
        map.put("example_stand_finisher_punchs_wish_its_tail", heavy("example_stand_finisher_punchs_wish_its_tail", Kind.TAIL_SWEEP));
        map.put("dynamic_vision", ability("dynamic_vision", Mode.DYNAMIC_VISION));
        map.put("dynamic_vision_close", ability("dynamic_vision_close", Mode.DYNAMIC_VISION_CLOSE));
        map.put("slash", heavy("slash", Kind.SLASH));
        map.put("dinosaurify", ability("dinosaurify", Mode.DINOSAURIFY));
        map.put("dinosaurify_revert", ability("dinosaurify_revert", Mode.DINOSAURIFY_REVERT));
        map.put("dragon", ability("dragon", Mode.DRAGON));
        map.put("dragon_close", ability("dragon_close", Mode.DRAGON_CLOSE));
        map.put("block_to_dinosaur", ability("block_to_dinosaur", Mode.BLOCK_TO_DINOSAUR));
        map.put("revert_block_to_dinosaur", ability("revert_block_to_dinosaur", Mode.REVERT_BLOCK_TO_DINOSAUR));
        map.put("dinosaur_form", ability("dinosaur_form", Mode.DINOSAUR_FORM));
        map.put("dinosaur_form_close", ability("dinosaur_form_close", Mode.DINOSAUR_FORM_CLOSE));
        return java.util.Collections.unmodifiableMap(map);
    }
}
