package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.client.LeavesGliderRenderer.Tint;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;
import rotp.core.network.c2s.ClLeavesGliderColorPacket;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 LeavesGliderRenderer draws the glider's own tint (server tint or the first client's report).
// Drawing never looks up or keeps a tint of its own, so all viewers and re-tracks show one colour.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LeavesGliderDrawTintGameTests {
	private static final int REPORTED_ARGB = 0xFF3A5F0B;

	private LeavesGliderDrawTintGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void drawnTintIsTheGlidersOwn(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		LeavesGliderEntity oak = new LeavesGliderEntity(helper.getLevel());
		oak.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
		oak.setLeavesBlock(Blocks.OAK_LEAVES.defaultBlockState());
		helper.assertTrue(helper.getLevel().addFreshEntity(oak), "Glider was not added to the test level");
		helper.assertTrue(oak.getFoliageColor() == -1, "Precondition: oak glider has a server tint " + oak.getFoliageColor());

		// Unset tint draws white (1.16 ClientUtil.rgb(-1)) and drawing keeps no local tint
		int unset = Tint.argb(oak);
		helper.assertTrue(unset == 0xFFFFFFFF, "Unset glider tint drawn as " + Integer.toHexString(unset) + ", expected white");
		helper.assertTrue(oak.getFoliageColor() == -1,
				"Drawing kept a local tint " + oak.getFoliageColor() + " that the server never received");

		// The first client's report is what every viewer draws
		helper.assertTrue(ClLeavesGliderColorPacket.apply(helper.getLevel(), new ClLeavesGliderColorPacket(oak.getId(), REPORTED_ARGB)),
				"Client tint report for an oak glider was refused");
		int drawn = Tint.argb(oak);
		helper.assertTrue(drawn == REPORTED_ARGB,
				"Reported glider tint drawn as " + Integer.toHexString(drawn) + ", expected " + Integer.toHexString(REPORTED_ARGB));

		// A later tracker (spawn data) and a reload (NBT "Color") draw the same colour
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		oak.writeSpawnData(buf);
		LeavesGliderEntity tracker = new LeavesGliderEntity(helper.getLevel());
		tracker.readSpawnData(buf);
		int trackerDrawn = Tint.argb(tracker);
		helper.assertTrue(trackerDrawn == REPORTED_ARGB,
				"Later tracker draws " + Integer.toHexString(trackerDrawn) + ", not the reported " + Integer.toHexString(REPORTED_ARGB));
		LeavesGliderEntity reloaded = new LeavesGliderEntity(helper.getLevel());
		reloaded.load(oak.saveWithoutId(new CompoundTag()));
		int reloadedDrawn = Tint.argb(reloaded);
		helper.assertTrue(reloadedDrawn == REPORTED_ARGB,
				"Reloaded glider draws " + Integer.toHexString(reloadedDrawn) + ", not the reported " + Integer.toHexString(REPORTED_ARGB));
		oak.discard();
		helper.succeed();
	}
}
