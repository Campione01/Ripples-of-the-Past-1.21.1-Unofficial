package rotp.addons.catchtherainbow;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.timestop.TimeStopBehaviorPolicies;
import rotp.core.api.timestop.TimeStopBehaviorPolicy;
import rotp.core.api.timestop.TimeStopProgressionPolicy;
import rotp.core.api.timestop.TimeStopStartupCostContext;
import rotp.core.api.timestop.TimeStopStartupCostDecision;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.core.JojoRegistries;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.catchtherainbow.ability.FallingRainBladesAbility;
import rotp.addons.catchtherainbow.ability.RainBladeAbility;
import rotp.addons.catchtherainbow.ability.RainBlinkAbility;
import rotp.addons.catchtherainbow.ability.RainMergeAbility;
import rotp.addons.catchtherainbow.ability.RainRedemptionAbility;
import rotp.addons.catchtherainbow.ability.RainResumeAbility;
import rotp.addons.catchtherainbow.ability.RainStopAbility;
import rotp.addons.catchtherainbow.ability.TempHealthAbility;
import rotp.addons.catchtherainbow.effect.RainMergeEffect;
import rotp.addons.catchtherainbow.effect.RainRedemptionEffect;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.entity.RainBladeEntity;
import rotp.addons.catchtherainbow.item.CatchTheRainbowMaskItem;
import rotp.addons.catchtherainbow.item.CatchTheRainbowRevolverItem;
import rotp.addons.catchtherainbow.item.UmbrellaItem;
import rotp.addons.catchtherainbow.stand.CatchTheRainbowStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CatchTheRainbowAddon.MOD_ID)
public final class CatchTheRainbowAddon {
    public static final String MOD_ID = "rotp_ctr";

    public static final String RAIN_BLADE_NAME =
            "catch_the_rainbow_rain_blade";
    public static final String RAIN_BLADES_NAME =
            "catch_the_rainbow_rain_blades";
    public static final String RAIN_STOP_NAME =
            "catch_the_rainbow_rain_stop";
    public static final String RAIN_RESUME_NAME =
            "catch_the_rainbow_rain_resume";
    public static final String RAIN_BLINK_NAME =
            "catch_the_rainbow_rain_blink";
    public static final String RAIN_MERGE_NAME =
            "catch_the_rainbow_rain_merge";
    public static final String TEMP_HEALTH_NAME =
            "catch_the_rainbow_temp_health";
    public static final String RAIN_REDEMPTION_NAME =
            "catch_the_rainbow_rain_redemption";

    public static final double POWER = 9.0D;
    public static final double SPEED = 9.0D;
    public static final double EFFECTIVE_RANGE = 13.0D;
    public static final double MAX_RANGE = 13.0D;
    public static final double DURABILITY = 13.0D;
    public static final double PRECISION = 7.0D;
    // 1.16 StandEntityType(CatchTheRainbowEntity::new, 0.0f, 0.0f): a point
    // at the user's feet that area hits rarely reach. A 0.6x1.8 body was
    // hit by them and auto-guarded.
    public static final float STAND_WIDTH = 0.0F;
    public static final float STAND_HEIGHT = 0.0F;
    // Render-only box: the body is drawn and culled as it was at 0.6x1.8.
    public static final float RENDER_WIDTH = 0.6F;
    public static final float RENDER_HEIGHT = 1.8F;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RainBladeAbility>>
            RAIN_BLADE = ability(
                    RAIN_BLADE_NAME, RainBladeAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<FallingRainBladesAbility>>
            RAIN_BLADES = ability(
                    RAIN_BLADES_NAME,
                    FallingRainBladesAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RainStopAbility>>
            RAIN_STOP = ability(
                    RAIN_STOP_NAME, RainStopAbility::new);
    /*
     * The old shift variation had its own registry ID. The current
     * TimeStopAbility resumes through the same input, while this holder
     * retains that legacy registry surface for data-pack compatibility.
     */
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RainResumeAbility>>
            RAIN_RESUME = ability(
                    RAIN_RESUME_NAME, RainResumeAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RainBlinkAbility>>
            RAIN_BLINK = ability(
                    RAIN_BLINK_NAME, RainBlinkAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RainMergeAbility>>
            RAIN_MERGE = ability(
                    RAIN_MERGE_NAME, RainMergeAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TempHealthAbility>>
            TEMP_HEALTH = ability(
                    TEMP_HEALTH_NAME, TempHealthAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RainRedemptionAbility>>
            RAIN_REDEMPTION = ability(
                    RAIN_REDEMPTION_NAME,
                    RainRedemptionAbility::new);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial>
            MASK_MATERIAL = ARMOR_MATERIALS.register(
                    "catch_the_rainbow_mask_item",
                    () -> new ArmorMaterial(
                            Map.of(ArmorItem.Type.HELMET, 1),
                            0,
                            SoundEvents.ARMOR_EQUIP_GENERIC,
                            () -> Ingredient.EMPTY,
                            List.of(new ArmorMaterial.Layer(
                                    id("catch_the_rainbow_mask_item"))),
                            0.0F,
                            0.0F));

    public static final DeferredHolder<
            EntityType<?>, EntityType<CatchTheRainbowEntity>>
            CATCH_THE_RAINBOW_ENTITY = ENTITY_TYPES.register(
                    "catch_the_rainbow",
                    key -> EntityType.Builder
                            .<CatchTheRainbowEntity>of(
                                    CatchTheRainbowEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<RainBladeEntity>>
            RAIN_BLADE_ENTITY_1 = rainBladeType(
                    "catch_the_rainbow_rain_blade1");
    public static final DeferredHolder<
            EntityType<?>, EntityType<RainBladeEntity>>
            RAIN_BLADE_ENTITY_2 = rainBladeType(
                    "catch_the_rainbow_rain_blade2");
    public static final DeferredHolder<
            EntityType<?>, EntityType<RainBladeEntity>>
            RAIN_BLADE_ENTITY_3 = rainBladeType(
                    "catch_the_rainbow_rain_blade3");

    public static final DeferredHolder<Item, CatchTheRainbowMaskItem>
            MASK_ITEM = ITEMS.register(
                    "catch_the_rainbow_mask_item",
                    key -> new CatchTheRainbowMaskItem(
                            new Item.Properties().durability(
                                    ArmorItem.Type.HELMET
                                            .getDurability(3))));
    public static final DeferredHolder<Item, UmbrellaItem>
            UMBRELLA = ITEMS.register(
                    "umbrella",
                    key -> new UmbrellaItem(
                            new Item.Properties().durability(444)));
    public static final DeferredHolder<
            Item, CatchTheRainbowRevolverItem>
            REVOLVER = ITEMS.register(
                    "revolver",
                    key -> new CatchTheRainbowRevolverItem(
                            new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<MobEffect, RainMergeEffect>
            RAIN_MERGE_EFFECT = MOB_EFFECTS.register(
                    "rain_merge_effect", RainMergeEffect::new);
    public static final DeferredHolder<
            MobEffect, RainRedemptionEffect>
            RAIN_REDEMPTION_EFFECT = MOB_EFFECTS.register(
                    "rain_redemption_effect",
                    RainRedemptionEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SUMMON_SOUND = sound("catch_the_rainbow_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            UNSUMMON_SOUND = sound("catch_the_rainbow_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MERGE_SOUND = sound("catch_the_rainbow_rain_merge");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RAIN_BLADE_SOUND = sound(
                    "catch_the_rainbow_throwing_blade");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RAIN_BLINK_SOUND = sound(
                    "catch_the_rainbow_rain_blink");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RAIN_HEAL_SOUND = sound(
                    "catch_the_rainbow_rain_heal");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RAIN_STEP_SOUND = sound(
                    "catch_the_rainbow_rain_step");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            UMBRELLA_RAIN_SOUND = sound(
                    "umbrella_falling_rain_drops");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REVOLVER_SHOT = sound("revolver_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REVOLVER_NO_AMMO = sound("revolver_no_ammo");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REVOLVER_RELOAD = sound("revolver_reload");
    public static final OstSoundList CATCH_THE_RAINBOW_OST =
            new OstSoundList(
                    id("catch_the_rainbow_ost"),
                    SOUND_EVENTS);

    // 1.16 Rain Stop: a TheWorldTimeStop with staminaCost(250) and
    // staminaCostTick(7.5), and otherwise the TimeStop.Builder defaults -
    // humans 100 ticks, high-blood vampires and Pillar Men 180,
    // high-saturation zombies 120, learning 0.1 a tick, no decay, cooldown
    // 3 a tick. The core prices time stops on The World's 225 / 9, so the
    // start is scaled here and the per-tick part in CatchTheRainbowEvents.
    public static final float RAIN_STOP_STAMINA_COST = 250.0F;
    public static final float RAIN_STOP_STAMINA_COST_TICK = 7.5F;
    private static final TimeStopProgressionPolicy RAIN_STOP_PROGRESSION =
            new TimeStopProgressionPolicy(100, 180, 120, 0.1F, 0.0F, 3.0F);
    private static final TimeStopBehaviorPolicy RAIN_STOP_BEHAVIOR =
            new TimeStopBehaviorPolicy() {
                @Override
                public TimeStopStartupCostDecision startupCost(
                        TimeStopStartupCostContext context) {
                    return TimeStopStartupCostDecision.override(
                            context.defaultCost()
                                    * RAIN_STOP_STAMINA_COST
                                    / TimeStopLearning.BASE_STAMINA_COST);
                }

                @Override
                public TimeStopProgressionPolicy progression() {
                    return RAIN_STOP_PROGRESSION;
                }
            };

    public static final DeferredHolder<
            StandType, CatchTheRainbowStandType>
            CATCH_THE_RAINBOW = STAND_TYPES.register(
                    "catch_the_rainbow",
                    CatchTheRainbowAddon::createStand);

    // 1.16 InitStands: addAttackerResolveMultTier(10), tier 15, so a
    // Stand user hitting Catch the Rainbow's user gains 11x Resolve.
    public static final int ATTACKER_RESOLVE_MULT_TIER = 10;

    public CatchTheRainbowAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_LIFECYCLE_V1)
                // Resolve 4 charge, attacker Resolve tier (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_ABILITY_TUNING_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_ATTACKER_RESOLVE_TIERS_V1)
                // its moves tick through the summon lock and a Stand's
                // stun after their press (R770 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_SUMMON_LOCK_HOOK_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_STAND_STUN_HOOK_V1)
                // its moves run in the user's Stand-power slot, as
                // 1.16's StandActions were the user's power's (R770 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_ACTION_PORT_SLOT_V1)) {
            throw new IllegalStateException(
                    "RotP Catch the Rainbow requires addon ABI 1 with "
                            + "namespaced ability resources, Stand "
                            + "visual contexts, time-stop lifecycle "
                            + "events, time-stop ability tuning, "
                            + "attacker Resolve tiers, the action "
                            + "summon lock hook, the action Stand "
                            + "stun hook, the Stand-power action slot "
                            + "and the Stand action port slot (a core "
                            + "from the same release).");
        }

        ABILITY_TYPES.register(modEventBus);
        ARMOR_MATERIALS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        TimeStopBehaviorPolicies.register(
                id("time_stop_behavior"),
                id("catch_the_rainbow"),
                RAIN_STOP_BEHAVIOR);
        ResolveCounter.addAttackerResolveMultTier(
                id("catch_the_rainbow"), ATTACKER_RESOLVE_MULT_TIER);
        modEventBus.addListener(
                CatchTheRainbowEvents::registerAttributes);
        modEventBus.addListener(
                CatchTheRainbowAddon::addCreativeTabItems);
    }

    /** 1.16 listed the umbrella and the revolver in the ROTP main tab; the mask item had no tab. */
    private static void addCreativeTabItems(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == rotp.core.init.ModItems.MAIN_TAB.getKey()) {
            event.accept(UMBRELLA.get());
            event.accept(REVOLVER.get());
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static CatchTheRainbowEntity controller(
            LivingEntity user) {
        StandPower power = user != null ? StandPower.get(user) : null;
        return power != null
                && power.getPowerType() == CATCH_THE_RAINBOW.get()
                && power.getSummonedStandEntity()
                        instanceof CatchTheRainbowEntity controller
                && controller.isAlive()
                && !controller.isRemoved()
                ? controller
                : null;
    }

    // 1.16 built every Catch the Rainbow action with autoSummonStand():
    // an unsummoned user may press one, and the press summons the Stand
    // first, so its checks read the fresh Stand's defaults (all off).
    public static boolean summonsOnPress(LivingEntity user) {
        StandPower power = user != null ? StandPower.get(user) : null;
        return power != null
                && power.getPowerType() == CATCH_THE_RAINBOW.get()
                && power.getSummonedStandEntity() == null;
    }

    public static boolean isCreative(LivingEntity entity) {
        return entity instanceof Player player
                && player.getAbilities().instabuild;
    }

    private static <A extends rotp.core
            .powersystem.ability.Ability>
            DeferredHolder<AbilityType<?>, AbilityType<A>> ability(
                    String name,
                    BiFunction<AbilityType<A>,
                            rotp.core
                                    .powersystem.ability.AbilityId,
                            A> factory) {
        return ABILITY_TYPES.register(
                name, key -> new AbilityType<>(key, factory));
    }

    private static DeferredHolder<
            EntityType<?>, EntityType<RainBladeEntity>>
            rainBladeType(String name) {
        return ENTITY_TYPES.register(
                name,
                key -> EntityType.Builder
                        .<RainBladeEntity>of(
                                RainBladeEntity::new,
                                MobCategory.MISC)
                        .noSave()
                        .noSummon()
                        .sized(0.5F, 0.5F)
                        .clientTrackingRange(64)
                        .updateInterval(1)
                        .build(key.toString()));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static CatchTheRainbowStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(RAIN_BLADE_NAME, RAIN_BLADE)
                .addAbility(RAIN_BLADES_NAME, RAIN_BLADES)
                .addAbility(RAIN_STOP_NAME, RAIN_STOP)
                .addAbility(RAIN_BLINK_NAME, RAIN_BLINK)
                .addAbility(RAIN_MERGE_NAME, RAIN_MERGE)
                .addAbility(TEMP_HEALTH_NAME, TEMP_HEALTH)
                .addAbility(
                        RAIN_REDEMPTION_NAME, RAIN_REDEMPTION)
                .makeControlScheme("hotbar")
                    .bind(
                            RAIN_BLADE_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            RAIN_BLADES_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            RAIN_STOP_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            RAIN_BLINK_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            RAIN_MERGE_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            TEMP_HEALTH_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            RAIN_REDEMPTION_NAME,
                            0,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            RAIN_BLADE_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(
                        RAIN_BLADE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        RAIN_BLADES_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        RAIN_STOP_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        RAIN_BLINK_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        RAIN_MERGE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        TEMP_HEALTH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        RAIN_REDEMPTION_NAME));

        return new CatchTheRainbowStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                CATCH_THE_RAINBOW_ENTITY.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonSound(() -> SUMMON_SOUND)
                .unsummonSound(() -> UNSUMMON_SOUND);
    }
}
