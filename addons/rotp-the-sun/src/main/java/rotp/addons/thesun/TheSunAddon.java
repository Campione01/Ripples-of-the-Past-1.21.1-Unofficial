package rotp.addons.thesun;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.thesun.entity.TheSunEntity;
import rotp.addons.thesun.stand.TheSunStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(TheSunAddon.MOD_ID)
public final class TheSunAddon {
    public static final String MOD_ID = "rotp_the_sun";
    public static final String LEGACY_NAMESPACE = "rotp_nextalbum";
    public static final String THE_SUN_PATH = "the_sun";

    public static final double POWER = 16.0D;
    public static final double SPEED = 10.0D;
    public static final double EFFECTIVE_RANGE = 500.0D;
    public static final double MAX_RANGE = 500.0D;
    public static final double DURABILITY = 20.0D;
    public static final double PRECISION = 0.0D;
    public static final float ENTITY_WIDTH = 1.5F;
    public static final float ENTITY_HEIGHT = 1.5F;
    public static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    Registries.ENTITY_TYPE, LEGACY_NAMESPACE);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG,
                    LEGACY_NAMESPACE);

    public static final DeferredHolder<
            EntityType<?>, EntityType<TheSunEntity>>
            THE_SUN_ENTITY = ENTITY_TYPES.register(
                    THE_SUN_PATH,
                    key -> EntityType.Builder
                            .<TheSunEntity>of(
                                    TheSunEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(ENTITY_WIDTH, ENTITY_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<StandType, TheSunStandType>
            THE_SUN = STAND_TYPES.register(
                    THE_SUN_PATH, TheSunAddon::createStand);

    public TheSunAddon(IEventBus modEventBus) {
        requireCoreApi();
        ENTITY_TYPES.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(TheSunModEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                LEGACY_NAMESPACE, path);
    }

    private static TheSunStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder();
        return new TheSunStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .randomWeight(0.0D)
                        .build(),
                moveset,
                THE_SUN_ENTITY.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(ENTITY_WIDTH, ENTITY_HEIGHT);
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_RANDOM_WEIGHTS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_CLIENT_SKY_PRESENTATION_PROVIDERS_V1)) {
            throw new IllegalStateException(
                    "RotP The Sun requires ROTP addon ABI 1 "
                            + "with Stand visual contexts, random "
                            + "weights, and client sky presentation "
                            + "providers.");
        }
    }
}
