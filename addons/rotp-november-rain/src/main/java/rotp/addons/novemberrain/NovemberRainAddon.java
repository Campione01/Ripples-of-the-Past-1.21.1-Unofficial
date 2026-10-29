package rotp.addons.novemberrain;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.novemberrain.ability.LightRainAbility;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(NovemberRainAddon.MOD_ID)
public final class NovemberRainAddon {
    public static final String MOD_ID = "rotp_november_rain";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<AbilityType<?>, AbilityType<LightRainAbility>>
            LIGHT_RAIN = ABILITY_TYPES.register(
                    "light_rain",
                    key -> new AbilityType<>(key, LightRainAbility::new));

    public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>>
            NOVEMBER_RAIN_ENTITY = ENTITY_TYPES.register(
                    "november_rain",
                    key -> EntityType.Builder.<StandEntity>of(StandEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.7F, 2.1F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<StandType, EntityStandType> NOVEMBER_RAIN =
            STAND_TYPES.register("november_rain", NovemberRainAddon::createStand);

    public NovemberRainAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP November Rain requires ROTP addon ABI 1 with namespaced "
                            + "ability resources and stand visual contexts.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        STAND_TYPES.register(modEventBus);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility("light_rain", LIGHT_RAIN)
                .makeControlScheme("hotbar")
                    .bind("light_rain", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("light_rain", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("light_rain"));

        return new EntityStandType(
                new StandStats.Builder()
                        .power(1.7)
                        .speed(9.0)
                        .range(9.0, 9.0)
                        .durability(14.0)
                        .precision(13.6)
                        .build(),
                moveset,
                NOVEMBER_RAIN_ENTITY.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(0.7F, 2.1F);
    }
}
