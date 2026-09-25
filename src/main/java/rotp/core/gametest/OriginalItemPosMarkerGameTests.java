package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItemDataComponents;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.itemtracking.OriginalItemPosComponent;
import rotp.core.subsystems.itemtracking.OriginalItemPositions;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 CrazyDiamondAnchorMarker drew the anchor position of held items, outlining the one the block anchor move uses.
 * The port had OriginalItemPosMarker but never registered it. Marker classes are client-only,
 * so the registration is read from ModMarkers' bytecode.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OriginalItemPosMarkerGameTests {
	private OriginalItemPosMarkerGameTests() {}

	private static final String MARKER = "rotp/core/subsystems/itemtracking/OriginalItemPosMarker";

	@GameTest(template = "empty")
	public static void originalPosMarkerIsRegistered(GameTestHelper helper) {
		Set<String> refs = memberRefs(helper, "rotp/core/client/ModMarkers");
		helper.assertTrue(refs.contains(MARKER + ".<init>(Lnet/minecraft/client/Minecraft;)V"),
				"ModMarkers never registers OriginalItemPosMarker, held anchor positions are not drawn");
		Set<String> markerRefs = memberRefs(helper, MARKER);
		helper.assertTrue(markerRefs.contains("rotp/core/subsystems/itemtracking/OriginalItemPositions.forHeldItems("
				+ "Lrotp/core/powersystem/standpower/StandPower;"
				+ "Lrotp/core/subsystems/itemtracking/OriginalItemPositions$Visitor;"
				+ "[Lnet/minecraft/world/entity/LivingEntity;)V"),
				"OriginalItemPosMarker does not walk the held items through OriginalItemPositions");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void crazyDiamondAnchorItemIsOutlined(GameTestHelper helper) {
		Player cd = standUser(helper, "OrigPosMarker_cd", "crazy_diamond");
		Player sp = standUser(helper, "OrigPosMarker_sp", "star_platinum");
		try {
			BlockPos mainPos = helper.absolutePos(new BlockPos(1, 2, 1));
			BlockPos offPos = helper.absolutePos(new BlockPos(3, 2, 3));
			ItemStack main = anchored(Items.STONE, mainPos, helper.getLevel().dimension());
			ItemStack off = anchored(Items.DIRT, offPos, helper.getLevel().dimension());
			cd.setItemInHand(InteractionHand.MAIN_HAND, main);
			cd.setItemInHand(InteractionHand.OFF_HAND, off);

			// both hands marked, the off hand is the one the anchor move uses
			Map<BlockPos, Boolean> seen = visit(StandPower.get(cd), cd);
			helper.assertTrue(seen.size() == 2 && seen.containsKey(mainPos) && seen.containsKey(offPos),
					"Held anchor positions not all marked: " + seen);
			helper.assertTrue(Boolean.TRUE.equals(seen.get(offPos)), "Off-hand anchor is not outlined: " + seen);
			helper.assertTrue(Boolean.FALSE.equals(seen.get(mainPos)), "Main-hand anchor is outlined too: " + seen);

			// off hand empty: the main-hand item is the one used
			cd.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			seen = visit(StandPower.get(cd), cd);
			helper.assertTrue(seen.size() == 1 && Boolean.TRUE.equals(seen.get(mainPos)),
					"Main-hand anchor is not outlined when the off hand is empty: " + seen);

			// another dimension's position is not drawn here
			cd.setItemInHand(InteractionHand.OFF_HAND, anchored(Items.DIRT, offPos,
					helper.getLevel().dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER));
			seen = visit(StandPower.get(cd), cd);
			helper.assertTrue(seen.size() == 1 && seen.containsKey(mainPos),
					"An anchor from another dimension was marked: " + seen);

			// no outline without Crazy Diamond (grabbed blocks of other Stands still get marked)
			sp.setItemInHand(InteractionHand.OFF_HAND, anchored(Items.DIRT, offPos, helper.getLevel().dimension()));
			seen = visit(StandPower.get(sp), sp);
			helper.assertTrue(seen.size() == 1 && Boolean.FALSE.equals(seen.get(offPos)),
					"A non-Crazy Diamond user got an outlined anchor: " + seen);
			seen = visit(null, cd, null);
			helper.assertTrue(seen.size() == 1 && Boolean.FALSE.equals(seen.get(mainPos)),
					"Outlined an anchor without a Stand power: " + seen);
		} finally {
			cleanup(cd);
			cleanup(sp);
		}
		helper.succeed();
	}

	private static Map<BlockPos, Boolean> visit(StandPower stand, Player... holders) {
		Map<BlockPos, Boolean> seen = new LinkedHashMap<>();
		OriginalItemPositions.forHeldItems(stand, (pos, item, used) -> seen.put(pos, used), holders);
		return seen;
	}

	private static ItemStack anchored(net.minecraft.world.item.Item item, BlockPos pos,
			net.minecraft.resources.ResourceKey<Level> dimension) {
		ItemStack stack = new ItemStack(item);
		stack.set(ModItemDataComponents.ORIGINAL_POS, new OriginalItemPosComponent(pos, dimension));
		return stack;
	}

	private static Player standUser(GameTestHelper helper, String name, String standId) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
				UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
		user.getInventory().clearContent();
		StandPower power = PowerClass.STAND.attachGet(user);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
		helper.assertTrue(type != null, "Missing Stand type " + standId);
		if (power.getPowerType() != type) {
			StandPowerTransitions.insert(power, new StandInstance(type));
		}
		helper.assertTrue(power.getPowerType() == type, "Could not grant " + standId);
		return user;
	}

	private static void cleanup(Player user) {
		user.getInventory().clearContent();
	}

	// owner.name+descriptor of every method ref in the constant pool
	private static Set<String> memberRefs(GameTestHelper helper, String className) {
		String path = className + ".class";
		InputStream raw = OriginalItemPosMarkerGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = OriginalItemPosMarkerGameTests.class.getClassLoader().getResourceAsStream(path);
		}
		helper.assertTrue(raw != null, "Missing class file " + path);
		try (DataInputStream in = new DataInputStream(raw)) {
			helper.assertTrue(in.readInt() == 0xCAFEBABE, path + " is not a class file");
			in.readUnsignedShort();
			in.readUnsignedShort();
			int count = in.readUnsignedShort();
			String[] utf = new String[count];
			int[] a = new int[count];
			int[] b = new int[count];
			int[] tag = new int[count];
			for (int i = 1; i < count; i++) {
				tag[i] = in.readUnsignedByte();
				switch (tag[i]) {
				case 1 -> utf[i] = in.readUTF();
				case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
				case 3, 4 -> in.readInt();
				case 5, 6 -> { in.readLong(); i++; }
				case 9, 10, 11, 12, 17, 18 -> { a[i] = in.readUnsignedShort(); b[i] = in.readUnsignedShort(); }
				case 15 -> { in.readUnsignedByte(); a[i] = in.readUnsignedShort(); }
				default -> throw new IOException("Unknown constant tag " + tag[i]);
				}
			}
			Set<String> refs = new HashSet<>();
			for (int i = 1; i < count; i++) {
				if (tag[i] == 10 || tag[i] == 11) {
					refs.add(utf[a[a[i]]] + "." + utf[a[b[i]]] + utf[b[b[i]]]);
				}
			}
			return refs;
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
			return Set.of();
		}
	}
}
