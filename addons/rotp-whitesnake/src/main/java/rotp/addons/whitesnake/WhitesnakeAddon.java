package rotp.addons.whitesnake;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.whitesnake.ability.WhitesnakeBlindnessAbility;
import rotp.addons.whitesnake.ability.WhitesnakeDisguiseAbility;
import rotp.addons.whitesnake.ability.WhitesnakeMeltYourHeartAbility;
import rotp.addons.whitesnake.ability.WhitesnakePunchAbility;
import rotp.addons.whitesnake.ability.WhitesnakeRemoveMemoryDiscAbility;
import rotp.addons.whitesnake.ability.WhitesnakeRemoveStandDiscAbility;
import rotp.addons.whitesnake.ability.WhitesnakeThrowDiscAbility;
import rotp.addons.whitesnake.blindness.BlindnessState;
import rotp.addons.whitesnake.block.MeltHeartBlock;
import rotp.addons.whitesnake.blockentity.MeltHeartBlockEntity;
import rotp.addons.whitesnake.disc.MemoryDiscData;
import rotp.addons.whitesnake.disc.MemoryDiscItem;
import rotp.addons.whitesnake.disc.StandDiscAuthorityData;
import rotp.addons.whitesnake.entity.WhitesnakeEntity;
import rotp.addons.whitesnake.entity.projectile.DiscProjectile;
import rotp.addons.whitesnake.entity.projectile.MeltHeartProjectile;
import rotp.addons.whitesnake.effect.MeltHeartEffect;
import rotp.addons.whitesnake.memory.MemoryLossState;
import rotp.addons.whitesnake.network.DisguiseSelectionPayload;
import rotp.addons.whitesnake.standdisc.StandDiscLossState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

@Mod(WhitesnakeAddon.MOD_ID)
public final class WhitesnakeAddon {
    public static final String MOD_ID = "rotp_whitesnake";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>>
            BLOCK_ENTITY_TYPES = DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister.DataComponents
            DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<DataComponentType<MemoryDiscData>>
            MEMORY_OWNER =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "memory_owner",
                    builder -> builder
                            .persistent(MemoryDiscData.CODEC)
                            .networkSynchronized(
                                    MemoryDiscData.STREAM_CODEC)
                            .cacheEncoding());
    public static final Supplier<
            DataComponentType<StandDiscAuthorityData>>
            STAND_DISC_AUTHORITY =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "stand_disc_authority",
                    builder -> builder
                            .persistent(
                                    StandDiscAuthorityData.CODEC)
                            .networkSynchronized(
                                    StandDiscAuthorityData.STREAM_CODEC)
                            .cacheEncoding());
    public static final Supplier<AttachmentType<MemoryLossState>>
            MEMORY_LOSS_STATE = ATTACHMENT_TYPES.register(
                    "memory_loss_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                            instanceof LivingEntity
                                            ? new MemoryLossState()
                                            : null)
                            .build());
    public static final Supplier<AttachmentType<StandDiscLossState>>
            STAND_DISC_LOSS_STATE = ATTACHMENT_TYPES.register(
                    "stand_disc_loss_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                            instanceof LivingEntity
                                            ? new StandDiscLossState()
                                            : null)
                            .build());
    public static final Supplier<AttachmentType<BlindnessState>>
            BLINDNESS_STATE = ATTACHMENT_TYPES.register(
                    "blindness_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                            instanceof LivingEntity
                                            ? new BlindnessState()
                                            : null)
                            .build());

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WhitesnakeThrowDiscAbility>>
            WHITESNAKE_THROW_DISC = ABILITY_TYPES.register(
                    "whitesnake_throw_disc",
                    key -> new AbilityType<>(
                            key, WhitesnakeThrowDiscAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WhitesnakeRemoveStandDiscAbility>>
            WHITESNAKE_REMOVE_STAND_DISC = ABILITY_TYPES.register(
                    "whitesnake_remove_stand_disc",
                    key -> new AbilityType<>(
                            key,
                            WhitesnakeRemoveStandDiscAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WhitesnakeRemoveMemoryDiscAbility>>
            REMOVING_THE_MEMORY_DISK = ABILITY_TYPES.register(
                    "removing_the_memory_disk",
                    key -> new AbilityType<>(
                            key,
                            WhitesnakeRemoveMemoryDiscAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WhitesnakeBlindnessAbility>>
            BLINDNESS = ABILITY_TYPES.register(
                    "blindness",
                    key -> new AbilityType<>(
                            key, WhitesnakeBlindnessAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WhitesnakeMeltYourHeartAbility>>
            MELT_YOUR_HEART = ABILITY_TYPES.register(
                    "melt_your_heart",
                    key -> new AbilityType<>(
                            key,
                            WhitesnakeMeltYourHeartAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WhitesnakeDisguiseAbility>>
            DISGUISE = ABILITY_TYPES.register(
                    "disguise",
                    key -> new AbilityType<>(
                            key, WhitesnakeDisguiseAbility::new));

    public static final DeferredBlock<MeltHeartBlock>
            MELT_HEART_BLOCK = BLOCKS.register(
                    "melt_heart_block",
                    () -> new MeltHeartBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                                    .strength(0.1F)
                                    .noOcclusion()
                                    .noLootTable()
                                    .randomTicks()
                                    .sound(SoundType.SLIME_BLOCK)
                                    .pushReaction(PushReaction.DESTROY)));
    public static final DeferredHolder<
            BlockEntityType<?>, BlockEntityType<MeltHeartBlockEntity>>
            MELT_HEART_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "melt_heart_block",
                    () -> BlockEntityType.Builder.of(
                                    MeltHeartBlockEntity::new,
                                    MELT_HEART_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<
            EntityType<?>, EntityType<WhitesnakeEntity>>
            WHITESNAKE_ENTITY = ENTITY_TYPES.register(
                    "whitesnake",
                    key -> EntityType.Builder.<WhitesnakeEntity>of(
                                    WhitesnakeEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.65F, 1.95F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<DiscProjectile>>
            DISC_ENTITY = ENTITY_TYPES.register(
                    "disc",
                    key -> EntityType.Builder.<DiscProjectile>of(
                                    DiscProjectile::new, MobCategory.MISC)
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(10)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<MeltHeartProjectile>>
            MELT_HEART_PROJECTILE = ENTITY_TYPES.register(
                    "melt_heart_projectile",
                    key -> EntityType.Builder.<MeltHeartProjectile>of(
                                    MeltHeartProjectile::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(64)
                            .updateInterval(10)
                            .build(key.toString()));

    public static final DeferredHolder<Item, MemoryDiscItem>
            MEMORY_DISC_ITEM = ITEMS.register(
                    "memory_disc_item",
                    key -> new MemoryDiscItem(
                            new Item.Properties()
                                    .stacksTo(1)
                                    .rarity(Rarity.RARE)));
    public static final DeferredHolder<MobEffect, MeltHeartEffect>
            MELT_HEART_EFFECT = MOB_EFFECTS.register(
                    "melt_heart_effect", MeltHeartEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            PUCCI_WHITESNAKE = registerSound("pucci_whitesnake");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WHITESNAKE_SUMMON = registerSound("whitesnake_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WHITESNAKE_UNSUMMON = registerSound("whitesnake_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WHITESNAKE_PUNCH = registerSound("whitesnake_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WHITESNAKE_PUNCH_HEAVY =
                    registerSound("whitesnake_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WHITESNAKE_BARRAGE =
                    registerSound("whitesnake_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WHITESNAKE_USHYA = registerSound("whitesnake_ushya");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PUCCI_PUNCH = registerSound("pucci_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WHITESNAKE_PUNCH_COMBO =
                    registerSound("whitesnake_punch_combo");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WHITESNAKE_REMOVE_DISC =
                    registerSound("whitesnake_remove_disc");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PUCCI_REMOVE_DISK = registerSound("pucci_remove_disk");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WHITESNAKE_MELT_HEART =
                    registerSound("whitesnake_melt_heart");
    public static final OstSoundList WHITESNAKE_OST =
            new OstSoundList(id("whitesnake_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType> WHITESNAKE =
            STAND_TYPES.register("whitesnake", WhitesnakeAddon::createStand);

    public WhitesnakeAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_POWER_TRANSITIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_FIRST_PERSON_POST_ARM_LAYERS_V1)
                // 1.16 action shouts keep the sneak rule
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)
                // canFireReleasedHold (R146 core held-action rechecks)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ENTITY_ACTION_HOOKS_V1)) {
            throw new IllegalStateException(
                    "RotP Whitesnake requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and stand visual "
                            + "contexts, public Stand power transitions, "
                            + "bounded first-person post-arm layers, and "
                            + "entity action hooks.");
        }

        DATA_COMPONENT_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        ABILITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(DisguiseSelectionPayload::register);
        modEventBus.addListener(WhitesnakeAddon::addCreativeTabItems);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, WhitesnakeConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 1.16 listed the memory disc in the ROTP main tab. */
    private static void addCreativeTabItems(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == rotp.core.init.ModItems.MAIN_TAB.getKey()) {
            event.accept(MEMORY_DISC_ITEM.get());
        }
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    public static MemoryLossState memoryState(LivingEntity entity) {
        return entity.getData(MEMORY_LOSS_STATE);
    }

    public static StandDiscLossState standDiscState(
            LivingEntity entity) {
        return entity.getData(STAND_DISC_LOSS_STATE);
    }

    public static BlindnessState blindnessState(LivingEntity entity) {
        return entity.getData(BLINDNESS_STATE);
    }

    public static boolean hasWhitesnakePower(LivingEntity user) {
        StandPower power = user != null ? StandPower.get(user) : null;
        return power != null
                && power.hasPower()
                && power.getPowerType() == WHITESNAKE.get();
    }

    public static boolean isActiveWhitesnakeUser(
            LivingEntity user, String requiredAbility) {
        if (user == null || !user.isAlive() || user.isRemoved()) {
            return false;
        }
        StandPower power = StandPower.get(user);
        if (power == null
                || !power.hasPower()
                || power.getPowerType() != WHITESNAKE.get()
                || !power.isSummoned()
                || requiredAbility != null
                        && !power.isAbilityUnlocked(requiredAbility)) {
            return false;
        }
        StandEntity stand = power.getSummonedStandEntity();
        return stand instanceof WhitesnakeEntity
                && stand.isAlive()
                && !stand.isRemoved()
                && stand.level() == user.level();
    }

    // 1.16 whitesnake_punch .shout(PUCCI_PUNCH) from the user
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WhitesnakePunchAbility>>
            WHITESNAKE_PUNCH_TYPE = ABILITY_TYPES.register(
                    "whitesnake_punch",
                    key -> new AbilityType<>(
                            key, WhitesnakePunchAbility::new));

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility("punch", WHITESNAKE_PUNCH_TYPE, punch ->
                        punch.punchImpactSound(WHITESNAKE_PUNCH))
                .addAbility("barrage", ModStandAbilities.BARRAGE, barrage ->
                        barrage.barrageHitSound(WHITESNAKE_PUNCH)
                                .barrageCrySound(WHITESNAKE_USHYA)
                                .barrageCryInArmsOnly())
                .addAbility(
                        "heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> punch
                                .heavyPunchImpactSound(
                                        WHITESNAKE_PUNCH_HEAVY)
                                .heavyPunchCrySound(
                                        WHITESNAKE_PUNCH_COMBO))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        "finisher_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    WHITESNAKE_PUNCH_HEAVY);
                            punch.heavyPunchCrySound(
                                    WHITESNAKE_PUNCH_COMBO);
                            punch.initIsFinisher("heavy_punch");
                        })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility(
                        "whitesnake_throw_disc",
                        WHITESNAKE_THROW_DISC,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        "whitesnake_remove_stand_disc",
                        WHITESNAKE_REMOVE_STAND_DISC,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        "removing_the_memory_disk",
                        REMOVING_THE_MEMORY_DISK,
                        ability -> ability.resolveLevelToUnlock(0))
                .addAbility(
                        "blindness",
                        BLINDNESS,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        "melt_your_heart",
                        MELT_YOUR_HEART,
                        ability -> ability.resolveLevelToUnlock(0))
                .addAbility(
                        "disguise",
                        DISGUISE,
                        ability -> ability.resolveLevelToUnlock(3))
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            "heavy_punch",
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            "whitesnake_throw_disc",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "whitesnake_remove_stand_disc",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "removing_the_memory_disk",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "blindness",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "melt_your_heart",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "disguise",
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            "heavy_punch",
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("barrage"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                         "finisher_punch"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "whitesnake_throw_disc",
                                200)
                        .prerequisiteSkill("barrage"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "whitesnake_remove_stand_disc",
                                300)
                        .prerequisiteSkill(
                                "whitesnake_throw_disc"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "removing_the_memory_disk"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "blindness"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "melt_your_heart"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "disguise"))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(10.0)
                        .speed(12.0)
                        .range(20.0, 20.0)
                        .durability(14.0)
                        .precision(14.0)
                        .randomWeight(1.5D)
                        .build(),
                moveset,
                WHITESNAKE_ENTITY.get(),
                id)
                .standControlType(StandControlType.LONG_DISTANCE_OPERATION)
                .manualControl(true)
                .distanceStrengthDecay(false)
                .standDimensions(0.65F, 1.95F)
                .summonShout(() -> PUCCI_WHITESNAKE)
                .summonSound(() -> WHITESNAKE_SUMMON)
                .unsummonSound(() -> WHITESNAKE_UNSUMMON)
                .ost(() -> WHITESNAKE_OST);
    }
}
