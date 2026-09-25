package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;
import rotp.core.network.c2s.ClLeavesGliderColorPacket;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Glider leaf tint (1.16 "Color", RGB): the server fixes the constant vanilla tints once and carries them
// in spawn data and NBT; biome-tinted leaves take the first client's BlockColors report, as 1.16 did
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LeavesGliderFoliageColorGameTests {
	private static final int BIRCH_RGB = 0x80A755;
	private static final int SPRUCE_RGB = 0x619961;

	private LeavesGliderFoliageColorGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void vanillaLeafTintsReachClientSpawnData(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		// 1.21 returns these as ARGB (negative ints)
		helper.assertTrue((FoliageColor.getBirchColor() & 0xFFFFFF) == BIRCH_RGB
				&& (FoliageColor.getEvergreenColor() & 0xFFFFFF) == SPRUCE_RGB,
				"Precondition: vanilla birch/spruce leaf tints changed");
		assertSpawnTint(helper, pos, Blocks.BIRCH_LEAVES, BIRCH_RGB, "birch");
		assertSpawnTint(helper, pos, Blocks.SPRUCE_LEAVES, SPRUCE_RGB, "spruce");
		// The foliage colormap is client-only (0 = black on a dedicated server): no server biome tint
		assertSpawnTint(helper, pos, Blocks.OAK_LEAVES, -1, "oak (client biome tint)");
		assertSpawnTint(helper, pos, Blocks.MANGROVE_LEAVES, -1, "mangrove (client biome tint)");
		assertSpawnTint(helper, pos, Blocks.CHERRY_LEAVES, -1, "cherry (untinted)");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void tintIsFixedOnceAndSaved(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		LeavesGliderEntity glider = glider(helper, pos, Blocks.SPRUCE_LEAVES);
		glider.resolveFoliageColor();
		helper.assertTrue(glider.getFoliageColor() == SPRUCE_RGB,
				"Spruce glider tint " + glider.getFoliageColor() + ", expected " + SPRUCE_RGB);
		// A later resolve (new tracker, save) must not recompute the tint
		glider.setLeavesBlock(Blocks.BIRCH_LEAVES.defaultBlockState());
		LeavesGliderEntity sent = spawnCopy(helper, glider);
		helper.assertTrue(glider.getFoliageColor() == SPRUCE_RGB && sent.getFoliageColor() == SPRUCE_RGB,
				"Glider tint was recomputed after it was fixed: " + glider.getFoliageColor() + ", sent " + sent.getFoliageColor());
		CompoundTag saved = glider.saveWithoutId(new CompoundTag());
		helper.assertTrue(saved.contains("Color") && saved.getInt("Color") == SPRUCE_RGB,
				"Glider NBT does not carry the fixed tint as \"Color\": " + saved);
		LeavesGliderEntity reloaded = new LeavesGliderEntity(helper.getLevel());
		reloaded.load(saved);
		helper.assertTrue(reloaded.getFoliageColor() == SPRUCE_RGB,
				"Reloaded glider tint " + reloaded.getFoliageColor() + " is not the saved " + SPRUCE_RGB);
		// Saved before any tracker saw it: the save itself fixes the tint
		CompoundTag unsent = glider(helper, pos, Blocks.BIRCH_LEAVES).saveWithoutId(new CompoundTag());
		helper.assertTrue(unsent.contains("Color") && unsent.getInt("Color") == BIRCH_RGB,
				"Unsent birch glider NBT lacks its tint: " + unsent);
		// Untinted and biome-tinted leaves save no server tint
		for (Block leaves : new Block[] { Blocks.CHERRY_LEAVES, Blocks.OAK_LEAVES }) {
			CompoundTag tag = glider(helper, pos, leaves).saveWithoutId(new CompoundTag());
			helper.assertTrue(!tag.contains("Color"), leaves + " glider saved a server tint: " + tag);
		}
		// Client BlockColors values are ARGB: kept as RGB, only -1 means unset
		LeavesGliderEntity client = new LeavesGliderEntity(helper.getLevel());
		client.setFoliageColor(0xFF3A5F0B);
		helper.assertTrue(client.getFoliageColor() == 0x3A5F0B,
				"ARGB tint 0xFF3A5F0B stored as " + client.getFoliageColor() + ", expected RGB " + 0x3A5F0B);
		client.setFoliageColor(-1);
		helper.assertTrue(client.getFoliageColor() == -1, "Unset tint -1 stored as " + client.getFoliageColor());
		helper.succeed();
	}

	// 1.16 ClLeavesGliderColorPacket: the first client report fixes a biome tint on the server for all later viewers
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void clientTintReportIsKeptOnce(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		LeavesGliderEntity oak = added(helper, glider(helper, pos, Blocks.OAK_LEAVES));
		helper.assertTrue(oak.getFoliageColor() == -1, "Precondition: oak glider has a server tint " + oak.getFoliageColor());
		helper.assertTrue(report(helper, oak.getId(), 0xFF3A5F0B), "First client tint report for an oak glider was refused");
		helper.assertTrue(oak.getFoliageColor() == 0x3A5F0B,
				"Reported ARGB tint 0xFF3A5F0B kept as " + oak.getFoliageColor() + ", expected RGB " + 0x3A5F0B);
		// A second client (other biome, other position) must not repaint it
		helper.assertTrue(!report(helper, oak.getId(), 0x112233) && oak.getFoliageColor() == 0x3A5F0B,
				"A second client report changed the kept tint to " + oak.getFoliageColor());
		LeavesGliderEntity sent = spawnCopy(helper, oak);
		helper.assertTrue(sent.getFoliageColor() == 0x3A5F0B,
				"Later tracker got spawn tint " + sent.getFoliageColor() + " instead of the reported " + 0x3A5F0B);
		CompoundTag saved = oak.saveWithoutId(new CompoundTag());
		helper.assertTrue(saved.contains("Color") && saved.getInt("Color") == 0x3A5F0B,
				"Reported tint is not saved as \"Color\": " + saved);
		// A server-fixed tint wins over any report
		LeavesGliderEntity spruce = added(helper, glider(helper, pos, Blocks.SPRUCE_LEAVES));
		spruce.resolveFoliageColor();
		helper.assertTrue(!report(helper, spruce.getId(), 0x112233) && spruce.getFoliageColor() == SPRUCE_RGB,
				"Client report overrode the spruce server tint: " + spruce.getFoliageColor());
		// BlockColors -1 (no colour registered) is white, a real tint, not "unset"
		LeavesGliderEntity cherry = added(helper, glider(helper, pos, Blocks.CHERRY_LEAVES));
		helper.assertTrue(report(helper, cherry.getId(), -1) && cherry.getFoliageColor() == 0xFFFFFF,
				"Untinted leaves report -1 kept as " + cherry.getFoliageColor() + ", expected white " + 0xFFFFFF);
		helper.assertTrue(!report(helper, Integer.MIN_VALUE, 0x112233), "Report for a missing entity was accepted");
		oak.discard();
		spruce.discard();
		cherry.discard();
		helper.succeed();
	}

	private static LeavesGliderEntity added(GameTestHelper helper, LeavesGliderEntity glider) {
		helper.assertTrue(helper.getLevel().addFreshEntity(glider), "Glider was not added to the test level");
		return glider;
	}

	// Server side of ClLeavesGliderColorPacket
	private static boolean report(GameTestHelper helper, int entityId, int color) {
		return ClLeavesGliderColorPacket.apply(helper.getLevel(), new ClLeavesGliderColorPacket(entityId, color));
	}

	private static LeavesGliderEntity glider(GameTestHelper helper, BlockPos pos, Block leaves) {
		LeavesGliderEntity glider = new LeavesGliderEntity(helper.getLevel());
		glider.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
		glider.setLeavesBlock(leaves.defaultBlockState());
		return glider;
	}

	// Server write, client-side read into a fresh glider
	private static LeavesGliderEntity spawnCopy(GameTestHelper helper, LeavesGliderEntity glider) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		glider.writeSpawnData(buf);
		LeavesGliderEntity copy = new LeavesGliderEntity(helper.getLevel());
		copy.readSpawnData(buf);
		return copy;
	}

	private static void assertSpawnTint(GameTestHelper helper, BlockPos pos, Block leaves, int expected, String name) {
		LeavesGliderEntity copy = spawnCopy(helper, glider(helper, pos, leaves));
		helper.assertTrue(copy.getLeavesBlock().is(leaves), name + " glider lost its leaves block in spawn data");
		helper.assertTrue(copy.getFoliageColor() == expected,
				name + " glider spawn tint " + copy.getFoliageColor() + ", expected " + expected);
	}
}
