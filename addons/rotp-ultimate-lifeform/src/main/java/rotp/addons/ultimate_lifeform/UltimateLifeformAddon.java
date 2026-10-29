package rotp.addons.ultimate_lifeform;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModArmorMaterials;
import rotp.core.init.ModItems;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAbility;
import rotp.addons.ultimate_lifeform.block.UltimateStoneMaskBlock;
import rotp.addons.ultimate_lifeform.block.UltimateStoneMaskBlockEntity;
import rotp.addons.ultimate_lifeform.config.UltimateLifeformConfig;
import rotp.addons.ultimate_lifeform.effect.UltimateAttributeEffect;
import rotp.addons.ultimate_lifeform.effect.UltimateHamonSpreadEffect;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimatePhantomEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateRabbitEntity;
import rotp.addons.ultimate_lifeform.item.LifeEssenceItem;
import rotp.addons.ultimate_lifeform.item.UltimateStoneMaskItem;
import rotp.addons.ultimate_lifeform.network.UltimateDoubleShiftPayload;
import rotp.addons.ultimate_lifeform.network.UltimateHamonEntityChargePayload;
import rotp.addons.ultimate_lifeform.network.UltimateHamonProjectileChargePayload;
import rotp.addons.ultimate_lifeform.network.UltimateSparkBurstPayload;
import rotp.addons.ultimate_lifeform.network.UltimateUserSoundPayload;
import rotp.addons.ultimate_lifeform.network.UltimateWallClimbInputPayload;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformPowerType;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSkill;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(UltimateLifeformAddon.MOD_ID)
public final class UltimateLifeformAddon {
    public static final String MOD_ID = "rotp_ul";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<PlayerPowerType<?>> PLAYER_POWERS =
            DeferredRegister.create(
                    JojoRegistries.PLAYER_POWER_TYPES_REG, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>>
            BLOCK_ENTITY_TYPES = DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);

    public static final Map<
            UltimateLifeformSpec,
            DeferredHolder<
                    AbilityType<?>,
                    AbilityType<UltimateLifeformAbility>>>
            ABILITIES = new EnumMap<>(UltimateLifeformSpec.class);

    static {
        for (UltimateLifeformSpec spec : UltimateLifeformSpec.values()) {
            ABILITIES.put(spec, ability(spec));
        }
    }

    public static final DeferredHolder<Block, UltimateStoneMaskBlock>
            UL_STONE_MASK_NOSTONE = BLOCKS.register(
                    "ul_stone_mask_nostone",
                    () -> new UltimateStoneMaskBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(1.5F)
                                    .sound(SoundType.STONE)
                                    .noOcclusion(),
                            false));
    public static final DeferredHolder<Block, UltimateStoneMaskBlock>
            UL_STONE_MASK_WITHSTONE = BLOCKS.register(
                    "ul_stone_mask_withstone",
                    () -> new UltimateStoneMaskBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(2.0F)
                                    .sound(SoundType.STONE)
                                    .noOcclusion(),
                            true));

    public static final DeferredHolder<
            Item, UltimateStoneMaskItem>
            STONE_MASK_UL_NOSTONE = ITEMS.register(
                    "stone_mask_ul_nostone",
                    () -> new UltimateStoneMaskItem(
                            new Item.Properties()
                                    .durability(ModArmorMaterials.STONE_MASK_DURABILITY)
                                    .rarity(Rarity.RARE),
                            UL_STONE_MASK_NOSTONE.get(),
                            false));
    public static final DeferredHolder<
            Item, UltimateStoneMaskItem>
            STONE_MASK_UL_WITH_STONE = ITEMS.register(
                    "stone_mask_ul_with_stone",
                    () -> new UltimateStoneMaskItem(
                            new Item.Properties()
                                    .durability(ModArmorMaterials.STONE_MASK_DURABILITY)
                                    .rarity(Rarity.EPIC),
                            UL_STONE_MASK_WITHSTONE.get(),
                            true));
    public static final DeferredHolder<Item, Item> SOUL_ESSENCE =
            ITEMS.register(
                    "soul_essence",
                    () -> new Item(
                            new Item.Properties()
                                    .stacksTo(16)));
    public static final DeferredHolder<Item, Item> FIRE_ESSENCE =
            elementalEssence("fire_essence");
    public static final DeferredHolder<Item, Item> EARTH_ESSENCE =
            elementalEssence("earth_essence");
    public static final DeferredHolder<Item, Item> WATER_ESSENCE =
            elementalEssence("water_essence");
    public static final DeferredHolder<Item, LifeEssenceItem> LIFE_ESSENCE =
            ITEMS.register(
                    "life_essence",
                    () -> new LifeEssenceItem(
                            new Item.Properties()
                                    .stacksTo(1)
                                    .fireResistant()));
    public static final DeferredHolder<Item, Item> DEATH_ESSENCE =
            elementalEssence("death_essence");
    public static final DeferredHolder<Item, Item> SUN_ESSENCE =
            elementalEssence("sun_essence");

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<UltimateStoneMaskBlockEntity>>
            UL_STONE_MASK_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "ul_stone_mask",
                    () -> BlockEntityType.Builder.of(
                                    UltimateStoneMaskBlockEntity::new,
                                    UL_STONE_MASK_NOSTONE.get(),
                                    UL_STONE_MASK_WITHSTONE.get())
                            .build(null));

    public static final DeferredHolder<
            MobEffect, UltimateHamonSpreadEffect>
            HAMON_SPREAD = MOB_EFFECTS.register(
                    "hamon_spread",
                    UltimateHamonSpreadEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect>
            ULTIMATE_ARMOR = MOB_EFFECTS.register(
                    "ulifeform_armor",
                    () -> new UltimateAttributeEffect(
                            MobEffectCategory.BENEFICIAL,
                            0x404040,
                            Attributes.ARMOR,
                            id("ultimate_armor"),
                            20.0D));
    public static final DeferredHolder<MobEffect, MobEffect>
            ULTIMATE_FIRE_ARMOR = MOB_EFFECTS.register(
                    "ulifeform_fire_armor",
                    () -> new UltimateAttributeEffect(
                            MobEffectCategory.BENEFICIAL,
                            0x404040,
                            Attributes.ARMOR,
                            id("ultimate_fire_armor"),
                            11.0D));
    public static final DeferredHolder<MobEffect, MobEffect>
            ULTIMATE_HEALTH_BOOST = MOB_EFFECTS.register(
                    "ulifeform_health_boost",
                    () -> new UltimateAttributeEffect(
                            MobEffectCategory.BENEFICIAL,
                            0x404040,
                            Attributes.MAX_HEALTH,
                            id("ultimate_health_boost"),
                            4.0D));

    public static final DeferredHolder<Potion, Potion> LIFE_ESSENCE_POTION =
            POTIONS.register(
                    "life_essence",
                    () -> new Potion(
                            new MobEffectInstance(
                                    MobEffects.REGENERATION,
                                    600,
                                    4),
                            new MobEffectInstance(
                                    MobEffects.ABSORPTION,
                                    600,
                                    4),
                            new MobEffectInstance(
                                    MobEffects.HEALTH_BOOST,
                                    120000,
                                    2)));

    public static final DeferredHolder<
            EntityType<?>, EntityType<UltimateScaleProjectileEntity>>
            SCALE_PROJECTILE = ENTITY_TYPES.register(
                    "scale_projectile",
                    key -> EntityType.Builder
                            .<UltimateScaleProjectileEntity>of(
                                    UltimateScaleProjectileEntity::new,
                                    MobCategory.MISC)
                            .sized(0.6F, 0.4F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<UltimateProjectileEntity>>
            LASER_END = projectile("laser_end");
    public static final DeferredHolder<
            EntityType<?>, EntityType<UltimateProjectileEntity>>
            SQUID_TENTACLE = projectile("squid_tentacle");
    public static final DeferredHolder<
            EntityType<?>, EntityType<UltimateRabbitEntity>>
            ULTIMATE_RABBIT = ENTITY_TYPES.register(
                    "ultimate_rabbit",
                    key -> EntityType.Builder
                            .of(
                                    UltimateRabbitEntity::new,
                                    MobCategory.CREATURE)
                            .noSave()
                            .sized(0.4F, 0.5F)
                            .clientTrackingRange(10)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<UltimatePhantomEntity>>
            ULTIMATE_PHANTOM = ENTITY_TYPES.register(
                    "ultimate_phantom",
                    key -> EntityType.Builder
                            .of(
                                    UltimatePhantomEntity::new,
                                    MobCategory.MONSTER)
                            .noSave()
                            .sized(0.9F, 0.5F)
                            .clientTrackingRange(10)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<UltimateAreaEffectEntity>>
            ULTIMATE_TURQUOISE_BLUE_OVERDRIVE =
            area("ultimate_turquoise_blue_overdrive");
    public static final DeferredHolder<
            EntityType<?>, EntityType<UltimateAreaEffectEntity>>
            ULTIMATE_SENDO_HAMON_OVERDRIVE =
            area("ultimate_sendo_hamon_overdrive");
    public static final DeferredHolder<
            EntityType<?>, EntityType<UltimateAreaEffectEntity>>
            HAMON_BLOCK_CHARGE = area("hamon_block_charge");

    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType> HAMON_AURA_GREEN =
            particle("hamon_aura_green");
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType> HAMON_AURA_PURPLE =
            particle("hamon_aura_purple");
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType> RAINBOW_SENDO =
            particle("rainbow_sendo");
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType> RAINBOW_SPARK =
            particle("rainbow_spark");
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType> HAMON_AURA_RAINBOW =
            particle("hamon_aura_rainbow");
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            HAMON_BREATH_AURA_RAINBOW =
            particle("hamon_breath_aura_rainbow");

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    // 1.16 capability rotp_ul:entity_ultimate_hamon
    public static final DeferredHolder<
            AttachmentType<?>, AttachmentType<UltimateHamonEntityCharge>>
            ENTITY_ULTIMATE_HAMON_CHARGE = ATTACHMENT_TYPES.register(
                    "entity_ultimate_hamon",
                    () -> AttachmentType.serializable(
                                    holder -> new UltimateHamonEntityCharge(
                                            (Entity) holder))
                            .build());

    public static final String[] LEGACY_SOUND_IDS = {
            "awaken",
            "kars_absolute_immortality",
            "kars_attack",
            "kars_hamon_overdrive",
            "kars_hamon_overdrive_charge",
            "kars_scale_shot",
            "kars_unlimited_power",
            "regeneration",
            "stamina_regeneration",
            "ultimate_hamon_barrage",
            "ultimate_hamon_breath",
            "ultimate_hamon_punch",
            "ultimate_lifeform_absorb",
            "ultimate_lifeform_armor",
            "ultimate_lifeform_fire_armor",
            "ultimate_lifeform_guardian_laser_charge",
            "ultimate_lifeform_scale_shot",
            "ultimate_lifeform_summon",
            "ultimate_lifeform_wing_flap"
    };
    public static final Map<String, DeferredHolder<SoundEvent, SoundEvent>>
            SOUNDS = new java.util.LinkedHashMap<>();

    static {
        for (String soundId : LEGACY_SOUND_IDS) {
            SOUNDS.put(
                    soundId,
                    SOUND_EVENTS.register(
                            soundId,
                            () -> SoundEvent.createVariableRangeEvent(
                                    id(soundId))));
        }
    }

    public static final DeferredHolder<
            PlayerPowerType<?>, UltimateLifeformPowerType>
            ULTIMATE_LIFEFORM = PLAYER_POWERS.register(
                    "ultimate_lifeform",
                    key -> new UltimateLifeformPowerType(
                            key, createMoveset()));

    public UltimateLifeformAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ADDON_PLAYER_ANIMATIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_SELECTION_VISUAL_POLICIES_V1)
                // attack-strength reset on perform (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_ACTION_HOOKS_V1)
                // 1.16 action shouts follow the sneak rule
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)
                // the HUD's 1.16 ready-to-fire green of a charged hold
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_HELD_READY_TO_FIRE_V1)
                // 1.16 heldAllowsOtherAction of the held Absorption
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_HELD_ACTION_RULES_PER_POWER_V1)
                // 1.16's continuous player action gate (IPlayerAction moves)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_PLAYER_ACTION_GATE_V1)) {
            throw new IllegalStateException(
                    "RotP Ultimate Lifeform requires ROTP addon ABI 1 "
                            + "with namespaced ability resources and "
                            + "addon player animations, plus ability "
                            + "selection visual policies, entity "
                            + "action hooks, ability shouts, the "
                            + "held ready-to-fire HUD, the per-power "
                            + "held action rules and the player "
                            + "action gate (a core "
                            + "from the same release).");
        }
        ABILITY_TYPES.register(modEventBus);
        PLAYER_POWERS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        POTIONS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(
                UltimateLifeformModEvents::registerAttributes);
        modEventBus.addListener(
                UltimateLifeformModEvents::buildCreativeTab);
        modEventBus.addListener(
                UltimateLifeformModEvents::commonSetup);
        modEventBus.addListener(
                UltimateWallClimbInputPayload::register);
        modEventBus.addListener(
                UltimateDoubleShiftPayload::register);
        modEventBus.addListener(
                UltimateHamonProjectileChargePayload::register);
        modEventBus.addListener(
                UltimateHamonEntityChargePayload::register);
        modEventBus.addListener(
                UltimateSparkBurstPayload::register);
        modEventBus.addListener(
                UltimateUserSoundPayload::register);
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                UltimateLifeformConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<Item, Item> elementalEssence(
            String path) {
        return ITEMS.register(
                path,
                () -> new Item(
                        new Item.Properties()
                                .stacksTo(1)
                                .fireResistant()));
    }

    private static DeferredHolder<
            ParticleType<?>, SimpleParticleType> particle(String path) {
        return PARTICLE_TYPES.register(
                path, () -> new SimpleParticleType(true));
    }

    private static DeferredHolder<
            EntityType<?>, EntityType<UltimateProjectileEntity>>
            projectile(String path) {
        return ENTITY_TYPES.register(
                path,
                key -> EntityType.Builder
                        .<UltimateProjectileEntity>of(
                                UltimateProjectileEntity::new,
                                MobCategory.MISC)
                        .sized(0.35F, 0.35F)
                        .clientTrackingRange(12)
                        .updateInterval(1)
                        .build(key.toString()));
    }

    private static DeferredHolder<
            EntityType<?>, EntityType<UltimateAreaEffectEntity>>
            area(String path) {
        return ENTITY_TYPES.register(
                path,
                key -> EntityType.Builder
                        .<UltimateAreaEffectEntity>of(
                                UltimateAreaEffectEntity::new,
                                MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .clientTrackingRange(10)
                        .updateInterval(2)
                        .build(key.toString()));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<UltimateLifeformAbility>>
            ability(UltimateLifeformSpec spec) {
        return ABILITY_TYPES.register(
                spec.id,
                key -> new AbilityType<>(
                        key,
                        (type, abilityId) ->
                                new UltimateLifeformAbility(
                                        type, abilityId, spec)));
    }

    private static MovesetBuilder createMoveset() {
        MovesetBuilder moveset = new MovesetBuilder();
        for (UltimateLifeformSpec spec : UltimateLifeformSpec.values()) {
            moveset.addAbility(
                    spec.id,
                    ABILITIES.get(spec),
                    UltimateLifeformAddon::initAbility);
            moveset.addSkill(
                    (UltimateLifeformSkill)
                            new UltimateLifeformSkill(
                                    MOD_ID + "_" + spec.id)
                                    .setIsStartingSkill()
                                    .withAbility(spec.id));
        }

        ControlSchemeTemplate controls =
                moveset.makeControlScheme("default");
        InputUseVanillaMapping useSpecial =
                new InputUseVanillaMapping(
                        "jojo_ripples.key.use_special_ability");
        InputUseVanillaMapping switchSpecial =
                new InputUseVanillaMapping(
                        "jojo_ripples.key.ability_hotbar");

        controls.setMovesetGroup(null)
                .bind(
                        UltimateLifeformSpec.HEAVY_PUNCH.id,
                        InputMethod.CLICK,
                        InputKey.LMB)
                .bind(
                        UltimateLifeformSpec.HAMON_OVERDRIVE.id,
                        InputMethod.CLICK,
                        InputKey.LMB.withModifier(
                                InputKey.Modifier.SHIFT))
                .bind(
                        UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT.id,
                        InputMethod.CLICK,
                        InputKey.LMB.withModifier(
                                InputKey.Modifier.CONTROL))
                .bind(
                        UltimateLifeformSpec.ULTIMATE_BARRAGE.id,
                        InputMethod.HOLD,
                        InputKey.RMB)
                .bind(
                        UltimateLifeformSpec
                                .ULTIMATE_HAMON_BARRAGE.id,
                        InputMethod.HOLD,
                        InputKey.RMB.withModifier(
                                InputKey.Modifier.SHIFT))
                .bind(
                        UltimateLifeformSpec.ULTIMATE_HAMON_KICK.id,
                        InputMethod.CLICK,
                        InputKey.RMB.withModifier(
                                InputKey.Modifier.CONTROL))
                .bind(
                        UltimateLifeformSpec.STONE_FORM.id,
                        InputMethod.HOLD,
                        skillKey(UltimateLifeformSpec.STONE_FORM))
                .bind(
                        UltimateLifeformSpec.ENHANCED_SENSES.id,
                        InputMethod.HOLD,
                        skillKey(UltimateLifeformSpec.ENHANCED_SENSES))
                .bind(
                        UltimateLifeformSpec.EVASION.id,
                        InputMethod.HOLD,
                        skillKey(UltimateLifeformSpec.EVASION))
                .bind(
                        UltimateLifeformSpec.ULTIMATE_AGILITY.id,
                        InputMethod.HOLD,
                        skillKey(UltimateLifeformSpec.ULTIMATE_AGILITY))
                .bind(
                        UltimateLifeformSpec.TENTACLES.id,
                        InputMethod.CLICK,
                        InputKey.MMB)
                .bind(
                        UltimateLifeformSpec.HORN_ATTACK.id,
                        InputMethod.CLICK,
                        InputKey.MMB.withModifier(
                                InputKey.Modifier.SHIFT))
                .bind(
                        UltimateLifeformSpec.ULTIMATE_FLIGHT_DASH.id,
                        InputMethod.CLICK,
                        skillKey(UltimateLifeformSpec.ULTIMATE_FLIGHT_DASH))
                .bind(
                        UltimateLifeformSpec.ENDERMAN_TELEPORT.id,
                        InputMethod.CLICK,
                        skillKey(UltimateLifeformSpec.ENDERMAN_TELEPORT))
                .bind(
                        UltimateLifeformSpec.BUNNY_SUMMON.id,
                        InputMethod.HOLD,
                        skillKey(UltimateLifeformSpec.BUNNY_SUMMON))
                .bind(
                        UltimateLifeformSpec.PHANTOM_SUMMON.id,
                        InputMethod.HOLD,
                        skillKey(UltimateLifeformSpec.PHANTOM_SUMMON))
                .bind(
                        UltimateLifeformSpec.NIGHT_VISION.id,
                        InputMethod.CLICK,
                        skillKey(UltimateLifeformSpec.NIGHT_VISION))
                .makeHotbar(0, useSpecial, switchSpecial);
        addHotbar(
                controls,
                0,
                UltimateLifeformSpec.values());
        return controls.finalizeControlScheme();
    }

    public static String skillKeyName(UltimateLifeformSpec spec) {
        return "key." + MOD_ID + "." + spec.id;
    }

    private static InputUseVanillaMapping skillKey(UltimateLifeformSpec spec) {
        return new InputUseVanillaMapping(skillKeyName(spec));
    }

    private static void addHotbar(
            ControlSchemeTemplate controls,
            int hotbar,
            UltimateLifeformSpec... specs) {
        for (UltimateLifeformSpec spec : specs) {
            controls.addToHotbar(
                    spec.id,
                    hotbar,
                    spec.held
                            ? InputMethod.HOLD
                            : InputMethod.CLICK);
        }
    }

    private static void initAbility(Ability ability) {
        ability.usageGroup =
                ability.name().contains("armor")
                                || ability.name().contains("vision")
                                || ability.name().contains("form")
                                || ability.name().contains("flight")
                                || ability.name().contains("climbing")
                        ? AbilityUsageGroup.UTILITY
                        : AbilityUsageGroup.COMBAT;
    }
}
