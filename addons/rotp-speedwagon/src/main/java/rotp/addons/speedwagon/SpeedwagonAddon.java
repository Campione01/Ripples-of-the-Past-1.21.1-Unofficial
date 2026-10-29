package rotp.addons.speedwagon;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.BiFunction;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItems;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import rotp.core.powersystem.playerpower.PlayerPowerType;

import rotp.addons.speedwagon.ability.HammerStrikeAbility;
import rotp.addons.speedwagon.ability.HotAbsAbility;
import rotp.addons.speedwagon.ability.RunAbility;
import rotp.addons.speedwagon.ability.SummonHenchmenAbility;
import rotp.addons.speedwagon.ability.ThrowHatAbility;
import rotp.addons.speedwagon.client.SpeedwagonClientEvents;
import rotp.addons.speedwagon.entity.Henchman2Entity;
import rotp.addons.speedwagon.entity.HenchmanEntity;
import rotp.addons.speedwagon.entity.InstructorEntity;
import rotp.addons.speedwagon.power.SpeedwagonPowerType;
import rotp.addons.speedwagon.power.SpeedwagonSkill;
import rotp.addons.speedwagon.worldgen.InstructorsHouseStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SpeedwagonAddon.MOD_ID)
public final class SpeedwagonAddon {
    public static final String MOD_ID = "rotp_sp";
    public static final String ATTACK1 = "attack1";
    public static final String ATTACK2 = "attack2";
    public static final String SKILL1 = "skill1";
    public static final String SKILL2 = "skill2";
    public static final String SKILL3 = "skill3";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<PlayerPowerType<?>> PLAYER_POWERS =
            DeferredRegister.create(
                    JojoRegistries.PLAYER_POWER_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(
                    BuiltInRegistries.STRUCTURE_TYPE, MOD_ID);
    public static final DeferredRegister<StructurePieceType>
            STRUCTURE_PIECES = DeferredRegister.create(
                    BuiltInRegistries.STRUCTURE_PIECE, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ThrowHatAbility>>
            THROW_HAT = ability(ATTACK1, ThrowHatAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HammerStrikeAbility>>
            HAMMER_STRIKE =
                    ability(ATTACK2, HammerStrikeAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SummonHenchmenAbility>>
            SUMMON_HENCHMEN =
                    ability(SKILL1, SummonHenchmenAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RunAbility>>
            RUN = ability(SKILL2, RunAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HotAbsAbility>>
            HOT_ABS = ability(SKILL3, HotAbsAbility::new);

    public static final DeferredHolder<
            EntityType<?>, EntityType<HenchmanEntity>>
            HENCHMAN = ENTITY_TYPES.register(
                    "henchman",
                    key -> EntityType.Builder
                            .<HenchmanEntity>of(
                                    HenchmanEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<Henchman2Entity>>
            HENCHMAN2 = ENTITY_TYPES.register(
                    "henchman2",
                    key -> EntityType.Builder
                            .<Henchman2Entity>of(
                                    Henchman2Entity::new,
                                    MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<InstructorEntity>>
            INSTRUCTOR = ENTITY_TYPES.register(
                    "instructor",
                    key -> EntityType.Builder
                            .<InstructorEntity>of(
                                    InstructorEntity::new,
                                    MobCategory.MISC)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build(key.toString()));

    public static final DeferredHolder<Item, SpawnEggItem>
            HENCHMAN_SPAWN_EGG = ITEMS.register(
                    "henchman_spawn_egg",
                    () -> new SpawnEggItem(
                            HENCHMAN.get(),
                            0xC4A160,
                            0xBF4147,
                            new Item.Properties()));
    public static final DeferredHolder<Item, SpawnEggItem>
            HENCHMAN2_SPAWN_EGG = ITEMS.register(
                    "henchman2_spawn_egg",
                    () -> new SpawnEggItem(
                            HENCHMAN2.get(),
                            0x6D8A7D,
                            0x54343B,
                            new Item.Properties()));
    public static final DeferredHolder<Item, SpawnEggItem>
            INSTRUCTOR_SPAWN_EGG = ITEMS.register(
                    "instructor_spawn_egg",
                    () -> new SpawnEggItem(
                            INSTRUCTOR.get(),
                            0x2A2833,
                            0x4F4C70,
                            new Item.Properties()));

    public static final DeferredHolder<
            StructureType<?>, StructureType<InstructorsHouseStructure>>
            INSTRUCTORS_HOUSE = STRUCTURE_TYPES.register(
                    "instructors_house",
                    () -> () -> InstructorsHouseStructure.CODEC);
    public static final DeferredHolder<
            StructurePieceType,
            StructurePieceType.StructureTemplateType>
            INSTRUCTORS_HOUSE_PIECE = STRUCTURE_PIECES.register(
                    "instructors_house_piece",
                    () -> InstructorsHouseStructure.Piece::new);

    public static final DeferredHolder<
            PlayerPowerType<?>, SpeedwagonPowerType>
            SPEEDWAGON = PLAYER_POWERS.register(
                    "speedwagon",
                    key -> new SpeedwagonPowerType(
                            key, createMoveset()));

    public SpeedwagonAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)) {
            throw new IllegalStateException(
                    "RotP Speedwagon requires ROTP addon ABI 1 "
                            + "with namespaced ability resources.");
        }
        ABILITY_TYPES.register(modEventBus);
        PLAYER_POWERS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECES.register(modEventBus);
        modEventBus.addListener(
                SpeedwagonModEvents::registerAttributes);
        modEventBus.addListener(
                SpeedwagonModEvents::buildCreativeTab);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, SpeedwagonConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) {
            SpeedwagonClientEvents.registerConfigScreen(modContainer);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static MovesetBuilder createMoveset() {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(
                        ATTACK1, THROW_HAT,
                        SpeedwagonAddon::combat)
                .addAbility(
                        ATTACK2, HAMMER_STRIKE,
                        SpeedwagonAddon::combat)
                .addAbility(
                        SKILL1, SUMMON_HENCHMEN,
                        SpeedwagonAddon::utility)
                .addAbility(
                        SKILL2, RUN,
                        SpeedwagonAddon::utility)
                .addAbility(
                        SKILL3, HOT_ABS,
                        SpeedwagonAddon::utility)
                .addSkill(startingSkill(ATTACK1))
                .addSkill(startingSkill(ATTACK2))
                .addSkill(startingSkill(SKILL1))
                .addSkill(startingSkill(SKILL2))
                .addSkill(startingSkill(SKILL3));

        ControlSchemeTemplate controls =
                moveset.makeControlScheme("default");
        controls.makeMovesetGroup(
                        "moveset_default_group",
                        new InputUseVanillaMapping(
                                "jojo_ripples.key.non_stand_mode"))
                .bind(ATTACK1, InputMethod.CLICK, InputKey.LMB)
                .bind(ATTACK2, InputMethod.CLICK, InputKey.RMB)
                .bind(SKILL1, InputMethod.CLICK, InputKey.MMB)
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar(ATTACK1, 0, InputMethod.CLICK)
                .addToHotbar(ATTACK2, 0, InputMethod.CLICK)
                .addToHotbar(SKILL1, 0, InputMethod.CLICK)
                .addToHotbar(SKILL2, 0, InputMethod.HOLD)
                .addToHotbar(SKILL3, 0, InputMethod.HOLD);
        return controls.finalizeControlScheme();
    }

    private static SpeedwagonSkill startingSkill(
            String abilityName) {
        return (SpeedwagonSkill) new SpeedwagonSkill(
                MOD_ID + "_" + abilityName)
                .setIsStartingSkill()
                .withAbility(abilityName);
    }

    private static void combat(Ability ability) {
        ability.usageGroup =
                rotp.core.powersystem.ability
                        .AbilityUsageGroup.COMBAT;
    }

    private static void utility(Ability ability) {
        ability.usageGroup =
                rotp.core.powersystem.ability
                        .AbilityUsageGroup.UTILITY;
    }

    private static <T extends Ability>
            DeferredHolder<AbilityType<?>, AbilityType<T>> ability(
                    String path,
                    BiFunction<AbilityType<T>, AbilityId, T> factory) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(key, factory));
    }
}
