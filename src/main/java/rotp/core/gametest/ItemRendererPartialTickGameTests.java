package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ClackersISTER and TommyGunISTER animate with the render partial tick (ClientUtil.getPartialTick()).
 * The port fed DeltaTracker.getGameTimeDeltaTicks() (ticks elapsed in the last frame) instead, so the
 * clackers swing stepped on whole ticks and the FIRE_1 muzzle flash never showed at high FPS.
 * The renderers are client classes, so their bytecode is read as a resource instead of being loaded.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemRendererPartialTickGameTests {
	private ItemRendererPartialTickGameTests() {}

	private static final String CLIENT_UTIL_PARTIAL_TICK = "rotp/core/client/util/functions/ClientUtil.partialTick()F";

	@GameTest(template = "empty")
	public static void clackersRendererUsesRenderPartialTick(GameTestHelper helper) {
		assertUsesPartialTick(helper, "rotp/core/client/itemrender/ClackersItemRenderer");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void tommyGunRendererUsesRenderPartialTick(GameTestHelper helper) {
		assertUsesPartialTick(helper, "rotp/core/client/itemrender/TommyGunItemRenderer");
		helper.succeed();
	}

	private static void assertUsesPartialTick(GameTestHelper helper, String className) {
		Set<String> calls = methodRefs(helper, className);
		helper.assertTrue(calls.contains(CLIENT_UTIL_PARTIAL_TICK),
				className + " does not read the render partial tick (ClientUtil.partialTick())");
		for (String call : calls) {
			helper.assertFalse(call.contains(".getGameTimeDeltaTicks("),
					className + " still uses the frame tick delta: " + call);
		}
	}

	// owner.name+descriptor of every method ref in the class constant pool
	private static Set<String> methodRefs(GameTestHelper helper, String className) {
		String path = className + ".class";
		InputStream raw = ItemRendererPartialTickGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = ItemRendererPartialTickGameTests.class.getClassLoader().getResourceAsStream(path);
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
					String owner = utf[a[a[i]]];
					int nameAndType = b[i];
					refs.add(owner + "." + utf[a[nameAndType]] + utf[b[nameAndType]]);
				}
			}
			return refs;
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
			return Set.of();
		}
	}
}
