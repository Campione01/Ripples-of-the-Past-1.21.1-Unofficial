package rotp.addons.tinkers_of_the_past.shared;

import rotp.addons.tinkers_of_the_past.TinkersOfThePast;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JojoMaterials {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(TinkersOfThePast.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(TinkersOfThePast.MOD_ID);

    public static final DeferredBlock<Block> STAND_ARROW_ALLOY_BLOCK =
            BLOCKS.register(
                    "stand_arrow_alloy_block",
                    JojoMaterials::metalBlock);
    public static final DeferredBlock<Block> METEOR_BLOCK =
            BLOCKS.register("meteor_block", JojoMaterials::metalBlock);

    public static final DeferredItem<TooltipBlockItem>
            STAND_ARROW_ALLOY_BLOCK_ITEM = ITEMS.register(
                    "stand_arrow_alloy_block",
                    () -> new TooltipBlockItem(
                            STAND_ARROW_ALLOY_BLOCK.get(),
                            new Item.Properties()));
    public static final DeferredItem<Item> STAND_ARROW_ALLOY_NUGGET =
            ITEMS.registerSimpleItem("stand_arrow_alloy_nugget");
    public static final DeferredItem<TooltipBlockItem> METEOR_BLOCK_ITEM =
            ITEMS.register(
                    "meteor_block",
                    () -> new TooltipBlockItem(
                            METEOR_BLOCK.get(),
                            new Item.Properties()));
    public static final DeferredItem<Item> METEOR_INGOT =
            ITEMS.registerSimpleItem("meteor_ingot");
    public static final DeferredItem<Item> METEOR_NUGGET =
            ITEMS.registerSimpleItem("meteor_nugget");

    private JojoMaterials() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    private static Block metalBlock() {
        return new Block(
                BlockBehaviour.Properties.of()
                        .strength(5.0F, 6.0F)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops());
    }
}
