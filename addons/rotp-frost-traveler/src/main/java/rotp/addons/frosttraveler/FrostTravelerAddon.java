package rotp.addons.frosttraveler;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.frosttraveler.ability.CreamStandActionAbility;
import rotp.addons.frosttraveler.ability.CreamStandActionAbility.Operation;
import rotp.addons.frosttraveler.entity.CreamStarterStandEntity;
import rotp.addons.frosttraveler.entity.HealSprayEntity;
import rotp.addons.frosttraveler.entity.SprayBulletEntity;
import rotp.addons.frosttraveler.item.CreamSprayerItem;
import rotp.addons.frosttraveler.item.DisguiseData;
import rotp.addons.frosttraveler.item.MeatMaskItem;
import rotp.addons.frosttraveler.item.SprayerData;
import rotp.addons.frosttraveler.network.AttackStrengthResetPayload;
import rotp.addons.frosttraveler.particle.MeatParticleType;
import rotp.addons.frosttraveler.stand.CreamStarterStandType;
import rotp.addons.frosttraveler.state.CreamStarterState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(FrostTravelerAddon.MOD_ID)
public final class FrostTravelerAddon {
    public static final String MOD_ID = "rotp_zcs";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String GIVE_CS_NAME = "give_cs";
    public static final String CS_HEAL_NAME = "cs_heal";
    public static final String CS_SWITCH_NAME = "cs_switch";
    public static final String CS_ITEM_FILL_NAME = "cs_item_fill";
    public static final String CS_ENTITY_FILL_NAME = "cs_entity_fill";
    public static final String CS_DEFORM_NAME = "cs_deform";
    public static final String CS_DEF_NAME = "cs_def";
    public static final String CS_DSG_ITEM_NAME = "cs_dsg_item";
    public static final String CS_ITEM_NAME = "cs_item";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister.DataComponents
            DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<DataComponentType<SprayerData>>
            SPRAYER_DATA = DATA_COMPONENT_TYPES.registerComponentType(
                    "sprayer_data",
                    builder -> builder
                            .persistent(SprayerData.CODEC)
                            .networkSynchronized(
                                    SprayerData.STREAM_CODEC)
                            .cacheEncoding());
    public static final Supplier<DataComponentType<DisguiseData>>
            DISGUISE_DATA = DATA_COMPONENT_TYPES.registerComponentType(
                    "disguise_data",
                    builder -> builder
                            .persistent(DisguiseData.CODEC)
                            .networkSynchronized(
                                    DisguiseData.STREAM_CODEC)
                            .cacheEncoding());
    public static final Supplier<AttachmentType<CreamStarterState>>
            CREAM_STATE = ATTACHMENT_TYPES.register(
                    "cream_starter_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new CreamStarterState()
                                            : null)
                            .build());

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamStandActionAbility>> GIVE_CS =
            standAction(GIVE_CS_NAME, Operation.GIVE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamStandActionAbility>> CS_HEAL =
            standAction(CS_HEAL_NAME, Operation.HEAL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamStandActionAbility>> CS_SWITCH =
            standAction(CS_SWITCH_NAME, Operation.SWITCH);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamStandActionAbility>> CS_ITEM_FILL =
            standAction(CS_ITEM_FILL_NAME, Operation.ITEM_FILL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamStandActionAbility>> CS_ENTITY_FILL =
            standAction(CS_ENTITY_FILL_NAME, Operation.ENTITY_FILL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamStandActionAbility>> CS_DEFORM =
            standAction(CS_DEFORM_NAME, Operation.DEFORM);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamStandActionAbility>> CS_DEF =
            standAction(CS_DEF_NAME, Operation.DEFENSE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamStandActionAbility>> CS_DSG_ITEM =
            standAction(CS_DSG_ITEM_NAME, Operation.DISGUISE_ITEM);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamStandActionAbility>> CS_ITEM =
            standAction(CS_ITEM_NAME, Operation.ITEM_INJECTION);

    public static final DeferredHolder<
            EntityType<?>, EntityType<CreamStarterStandEntity>>
            CREAM_STARTER_ENTITY = ENTITY_TYPES.register(
                    "cream_starter",
                    key -> EntityType.Builder.<CreamStarterStandEntity>of(
                                    CreamStarterStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<SprayBulletEntity>>
            SPRAY_BULLET_ENTITY = ENTITY_TYPES.register(
                    "spray_bullet",
                    key -> EntityType.Builder.<SprayBulletEntity>of(
                                    SprayBulletEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.0625F, 0.0625F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<HealSprayEntity>>
            HEAL_SPRAY_ENTITY = ENTITY_TYPES.register(
                    "heal_spray",
                    key -> EntityType.Builder.<HealSprayEntity>of(
                                    HealSprayEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.0625F, 0.0625F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<Item, CreamSprayerItem>
            CREAM_STARTER_ITEM = ITEMS.register(
                    "cream_starter",
                    key -> new CreamSprayerItem(
                            new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, MeatMaskItem>
            MEAT_DGS_ITEM = ITEMS.register(
                    "meat_dgs",
                    key -> new MeatMaskItem(
                            new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<
            ParticleType<?>, MeatParticleType> MEAT_PARTICLE =
            PARTICLE_TYPES.register(
                    "meat_particle",
                    MeatParticleType::new);

    public static final DeferredHolder<SoundEvent, SoundEvent> VOID =
            sound("void");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOT_CS =
            sound("hot_cs");
    public static final DeferredHolder<SoundEvent, SoundEvent> CS_SHOT =
            sound("cs_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> CS_SHAKE =
            sound("cs_shake");
    public static final DeferredHolder<SoundEvent, SoundEvent> CS_ABS =
            sound("cs_abs");
    public static final OstSoundList CS_OST =
            new OstSoundList(id("cs_ost"), SOUND_EVENTS);

    public static final DeferredHolder<
            StandType, CreamStarterStandType> CREAM_STARTER =
            STAND_TYPES.register(
                    "cream_starter",
                    FrostTravelerAddon::createStand);

    public FrostTravelerAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP Frost Traveler requires addon ABI 1 with "
                            + "namespaced ability resources and Stand "
                            + "visual contexts.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        DATA_COMPONENT_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(
                FrostTravelerEvents::registerAttributes);
        modEventBus.addListener(AttackStrengthResetPayload::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static CreamStarterState state(Player player) {
        return player.getData(CREAM_STATE);
    }

    public static StandEntity controller(LivingEntity user) {
        StandPower power = user != null ? StandPower.get(user) : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType() == CREAM_STARTER.get()
                && power.getSummonedStandEntity()
                        instanceof StandEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand
                : null;
    }

    public static boolean isActiveUser(LivingEntity user) {
        return controller(user) != null;
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<CreamStandActionAbility>> standAction(
                    String name, Operation operation) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new CreamStandActionAbility(
                                type, id, operation)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String name) {
        return SOUND_EVENTS.register(
                name, SoundEvent::createVariableRangeEvent);
    }

    private static CreamStarterStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(GIVE_CS_NAME, GIVE_CS)
                .addAbility(CS_HEAL_NAME, CS_HEAL)
                .addAbility(CS_SWITCH_NAME, CS_SWITCH)
                .addAbility(CS_ITEM_FILL_NAME, CS_ITEM_FILL)
                .addAbility(CS_ENTITY_FILL_NAME, CS_ENTITY_FILL)
                .addAbility(CS_DEFORM_NAME, CS_DEFORM)
                .addAbility(CS_DEF_NAME, CS_DEF)
                .addAbility(CS_DSG_ITEM_NAME, CS_DSG_ITEM)
                .addAbility(CS_ITEM_NAME, CS_ITEM)
                .makeControlScheme("hotbar")
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            CS_ITEM_FILL_NAME, 0, InputMethod.HOLD)
                    // 1.16 cs_entity_fill shiftVariationOf(CS_ITEM_FILL)
                    .addHotbarSlotVariation(
                            CS_ENTITY_FILL_NAME,
                            CS_ITEM_FILL_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.HOLD)
                    .addToHotbar(
                            CS_SWITCH_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            CS_HEAL_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            CS_DEFORM_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            CS_DSG_ITEM_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            CS_ITEM_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            CS_DEF_NAME, 0, InputMethod.HOLD)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(
                        GIVE_CS_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CS_HEAL_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CS_SWITCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CS_ITEM_FILL_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CS_ENTITY_FILL_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CS_DEFORM_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CS_DEF_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CS_DSG_ITEM_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CS_ITEM_NAME));

        return new CreamStarterStandType(
                new StandStats.Builder()
                        .power(6.0D, 6.0D)
                        .speed(10.0D, 10.0D)
                        .range(9.0D, 9.0D)
                        .durability(16.0D, 16.0D)
                        .precision(2.0D)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                CREAM_STARTER_ENTITY.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(1.0F, 1.0F)
                .summonShout(() -> HOT_CS)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> CS_OST);
    }
}
