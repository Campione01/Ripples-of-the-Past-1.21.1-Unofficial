package rotp.addons.tournament.init;

import rotp.addons.tournament.AddonMain;
import rotp.addons.tournament.block.FakeSpikesMultiBlock;
import rotp.addons.tournament.block.LightBulbBlock;
import rotp.addons.tournament.block.SpikesMultiBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AddonBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(AddonMain.MOD_ID);

    public static final DeferredBlock<SpikesMultiBlock> SPIKES =
            BLOCKS.register(
                    "spikes",
                    () -> new SpikesMultiBlock(
                            BlockBehaviour.Properties.of()
                                    .requiresCorrectToolForDrops()
                                    .strength(6.0F)
                                    .noOcclusion()));
    public static final DeferredBlock<Block> TILE =
            BLOCKS.register(
                    "tile",
                    () -> new Block(
                            BlockBehaviour.Properties.of()
                                    .requiresCorrectToolForDrops()
                                    .strength(2.0F, 6.0F)));
    public static final DeferredBlock<LightBulbBlock> LIGHT_BULB =
            BLOCKS.register(
                    "light_bulb",
                    () -> new LightBulbBlock(
                            BlockBehaviour.Properties.of()
                                    .noCollission()
                                    .noOcclusion()
                                    .lightLevel(state -> 15)
                                    .instabreak()));
    public static final DeferredBlock<FakeSpikesMultiBlock>
            FAKE_SPIKES = BLOCKS.register(
                    "fake_spikes",
                    () -> new FakeSpikesMultiBlock(
                            BlockBehaviour.Properties.of()
                                    .requiresCorrectToolForDrops()
                                    .strength(6.0F)
                                    .noOcclusion()
                                    .lootFrom(SPIKES)));

    private AddonBlocks() {}
}
