package rotp.addons.tinkers_of_the_past.fluids;

import rotp.addons.tinkers_of_the_past.TinkersOfThePast;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class JojoFluids {
    public static final int STAND_ARROW_ALLOY_TINT = 0xFFDAC352;
    public static final int METEOR_TINT = 0xFF7E6A30;

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.Keys.FLUID_TYPES,
                    TinkersOfThePast.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(
                    Registries.FLUID, TinkersOfThePast.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(TinkersOfThePast.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(TinkersOfThePast.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType>
            MOLTEN_STAND_ARROW_ALLOY_TYPE = FLUID_TYPES.register(
                    "molten_stand_arrow_alloy",
                    () -> moltenType(
                            "block.tjojo.molten_stand_arrow_alloy",
                            15));
    public static final DeferredHolder<FluidType, FluidType>
            MOLTEN_METEOR_TYPE = FLUID_TYPES.register(
                    "molten_meteor",
                    () -> moltenType(
                            "block.tjojo.molten_meteor",
                            10));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source>
            MOLTEN_STAND_ARROW_ALLOY = FLUIDS.register(
                    "molten_stand_arrow_alloy",
                    () -> new BaseFlowingFluid.Source(
                            standArrowAlloyProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing>
            FLOWING_MOLTEN_STAND_ARROW_ALLOY = FLUIDS.register(
                    "flowing_molten_stand_arrow_alloy",
                    () -> new BaseFlowingFluid.Flowing(
                            standArrowAlloyProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source>
            MOLTEN_METEOR = FLUIDS.register(
                    "molten_meteor",
                    () -> new BaseFlowingFluid.Source(
                            meteorProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing>
            FLOWING_MOLTEN_METEOR = FLUIDS.register(
                    "flowing_molten_meteor",
                    () -> new BaseFlowingFluid.Flowing(
                            meteorProperties()));

    public static final DeferredBlock<LiquidBlock>
            MOLTEN_STAND_ARROW_ALLOY_BLOCK = BLOCKS.register(
                    "molten_stand_arrow_alloy",
                    () -> new LiquidBlock(
                            MOLTEN_STAND_ARROW_ALLOY.get(),
                            Blocks.LAVA.properties()
                                    .lightLevel(state -> 15)
                                    .noLootTable()));
    public static final DeferredBlock<LiquidBlock>
            MOLTEN_METEOR_BLOCK = BLOCKS.register(
                    "molten_meteor",
                    () -> new LiquidBlock(
                            MOLTEN_METEOR.get(),
                            Blocks.LAVA.properties()
                                    .lightLevel(state -> 10)
                                    .noLootTable()));

    public static final DeferredItem<BucketItem>
            MOLTEN_STAND_ARROW_ALLOY_BUCKET = ITEMS.register(
                    "molten_stand_arrow_alloy_bucket",
                    () -> new BucketItem(
                            MOLTEN_STAND_ARROW_ALLOY.get(),
                            new Item.Properties()
                                    .craftRemainder(Items.BUCKET)
                                    .stacksTo(1)));
    public static final DeferredItem<BucketItem>
            MOLTEN_METEOR_BUCKET = ITEMS.register(
                    "molten_meteor_bucket",
                    () -> new BucketItem(
                            MOLTEN_METEOR.get(),
                            new Item.Properties()
                                    .craftRemainder(Items.BUCKET)
                                    .stacksTo(1)));

    private JojoFluids() {}

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    private static FluidType moltenType(
            String descriptionId, int lightLevel) {
        return new FluidType(
                FluidType.Properties.create()
                        .descriptionId(descriptionId)
                        .density(2000)
                        .viscosity(10000)
                        .temperature(1000)
                        .lightLevel(lightLevel));
    }

    private static BaseFlowingFluid.Properties
            standArrowAlloyProperties() {
        return new BaseFlowingFluid.Properties(
                        MOLTEN_STAND_ARROW_ALLOY_TYPE,
                        MOLTEN_STAND_ARROW_ALLOY,
                        FLOWING_MOLTEN_STAND_ARROW_ALLOY)
                .bucket(MOLTEN_STAND_ARROW_ALLOY_BUCKET)
                .block(MOLTEN_STAND_ARROW_ALLOY_BLOCK)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(30)
                .explosionResistance(100.0F);
    }

    private static BaseFlowingFluid.Properties meteorProperties() {
        return new BaseFlowingFluid.Properties(
                        MOLTEN_METEOR_TYPE,
                        MOLTEN_METEOR,
                        FLOWING_MOLTEN_METEOR)
                .bucket(MOLTEN_METEOR_BUCKET)
                .block(MOLTEN_METEOR_BLOCK)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(30)
                .explosionResistance(100.0F);
    }

}
