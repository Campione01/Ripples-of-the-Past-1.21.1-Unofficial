package rotp.core.gametest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.IntPredicate;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import rotp.core.client.ui.hud_power.FinisherRing;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ActionsOverlayGui:1738-1740: the finisher fill was white (overlay.png u=114) at every value, green (u=132)
 * only when the heavy punch would be the finisher variation. The port's full ring textures are baked green,
 * so a full meter without the heavy finisher must draw the white ring whole. Texture pixels are read from the PNGs.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandFinisherRingGameTests {

	private StandFinisherRingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void fullFinisherRingStaysWhiteWithoutHeavyFinisher(GameTestHelper helper) {
		ResourceLocation plain = FinisherRing.fullRing(1, false);
		check(helper, plain.equals(FinisherRing.fillRing(0)), "a full meter must draw the ring the fill runs on, got " + plain);
		int[] plainPx = rgba(plain);
		int[] fillPx = rgba(FinisherRing.fillRing(0));
		int[] heavyPx = rgba(FinisherRing.fullRing(1, true));
		check(helper, opaque(plainPx) > 0 && allOpaque(plainPx, StandFinisherRingGameTests::white),
				"a full meter without the heavy finisher must draw a white ring, got " + plain);
		check(helper, opaque(fillPx) > 0 && allOpaque(fillPx, StandFinisherRingGameTests::white),
				"the fill below a full meter must run on a white ring");
		check(helper, sameMask(plainPx, heavyPx), "the white full ring must cover the green full ring's shape");
		int tint = FinisherRing.tint(false);
		check(helper, (tint & 0xFFFFFF) == 0xFFFFFF && (tint >>> 24) > 0, "the plain tint must be translucent white, got " + Integer.toHexString(tint));
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void fullFinisherRingGreenForHeavyFinisher(GameTestHelper helper) {
		ResourceLocation heavy = FinisherRing.fullRing(1, true);
		int[] heavyPx = rgba(heavy);
		check(helper, opaque(heavyPx) > 0 && allOpaque(heavyPx, StandFinisherRingGameTests::green),
				"a full meter with the heavy finisher must draw a green ring, got " + heavy);
		int tint = FinisherRing.tint(true);
		check(helper, green(tint) && (tint >>> 24) > 0, "the heavy finisher tint must be green, got " + Integer.toHexString(tint));
		helper.succeed();
	}

	private static void check(GameTestHelper helper, boolean ok, String message) {
		helper.assertTrue(ok, message);
	}

	private static boolean white(int argb) {
		return (argb >> 16 & 0xFF) >= 240 && (argb >> 8 & 0xFF) >= 240 && (argb & 0xFF) >= 240;
	}

	private static boolean green(int argb) {
		return (argb >> 16 & 0xFF) <= 40 && (argb >> 8 & 0xFF) >= 200 && (argb & 0xFF) <= 80;
	}

	private static int opaque(int[] px) {
		int n = 0;
		for (int p : px) if (p >>> 24 != 0) n++;
		return n;
	}

	private static boolean allOpaque(int[] px, IntPredicate test) {
		for (int p : px) if (p >>> 24 != 0 && !test.test(p)) return false;
		return true;
	}

	private static boolean sameMask(int[] a, int[] b) {
		if (a.length != b.length) return false;
		for (int i = 0; i < a.length; i++) if ((a[i] >>> 24 != 0) != (b[i] >>> 24 != 0)) return false;
		return true;
	}

	// ARGB pixels of an 8-bit RGBA, non-interlaced PNG on the mod's classpath
	private static int[] rgba(ResourceLocation texture) {
		String path = "assets/" + texture.getNamespace() + "/" + texture.getPath();
		InputStream raw = StandFinisherRingGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = StandFinisherRingGameTests.class.getClassLoader().getResourceAsStream(path);
		}
		if (raw == null) {
			throw new GameTestAssertException("missing texture " + path);
		}
		try (InputStream in0 = raw) {
			byte[] file = in0.readAllBytes();
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(file, 8, file.length - 8));
			ByteArrayOutputStream idat = new ByteArrayOutputStream();
			int w = 0;
			int h = 0;
			while (true) {
				int len = in.readInt();
				String type = new String(in.readNBytes(4), StandardCharsets.US_ASCII);
				byte[] data = in.readNBytes(len);
				in.readInt();
				if (type.equals("IHDR")) {
					ByteBuffer ihdr = ByteBuffer.wrap(data);
					w = ihdr.getInt();
					h = ihdr.getInt();
					if (data[8] != 8 || data[9] != 6 || data[12] != 0) {
						throw new GameTestAssertException("unexpected PNG format in " + path);
					}
				}
				else if (type.equals("IDAT")) {
					idat.write(data);
				}
				else if (type.equals("IEND")) {
					break;
				}
			}
			int stride = w * 4;
			byte[] rows = new byte[(stride + 1) * h];
			Inflater inflater = new Inflater();
			inflater.setInput(idat.toByteArray());
			int n = 0;
			while (n < rows.length && !inflater.finished()) {
				int k = inflater.inflate(rows, n, rows.length - n);
				if (k == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
				n += k;
			}
			inflater.end();
			if (n != rows.length) {
				throw new GameTestAssertException("truncated PNG " + path);
			}
			int[] out = new int[w * h];
			byte[] prev = new byte[stride];
			byte[] cur = new byte[stride];
			for (int y = 0; y < h; y++) {
				int row = y * (stride + 1);
				int filter = rows[row] & 0xFF;
				for (int i = 0; i < stride; i++) {
					int x = rows[row + 1 + i] & 0xFF;
					int a = i >= 4 ? cur[i - 4] & 0xFF : 0;
					int b = prev[i] & 0xFF;
					int c = i >= 4 ? prev[i - 4] & 0xFF : 0;
					int v = switch (filter) {
						case 0 -> x;
						case 1 -> x + a;
						case 2 -> x + b;
						case 3 -> x + ((a + b) >> 1);
						case 4 -> x + paeth(a, b, c);
						default -> throw new GameTestAssertException("bad PNG filter " + filter + " in " + path);
					};
					cur[i] = (byte) v;
				}
				for (int px = 0; px < w; px++) {
					int o = px * 4;
					out[y * w + px] = (cur[o + 3] & 0xFF) << 24 | (cur[o] & 0xFF) << 16 | (cur[o + 1] & 0xFF) << 8 | (cur[o + 2] & 0xFF);
				}
				byte[] swap = prev;
				prev = cur;
				cur = swap;
			}
			return out;
		}
		catch (IOException | DataFormatException e) {
			throw new GameTestAssertException("could not read " + path + ": " + e);
		}
	}

	private static int paeth(int a, int b, int c) {
		int p = a + b - c;
		int pa = Math.abs(p - a);
		int pb = Math.abs(p - b);
		int pc = Math.abs(p - c);
		return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
	}
}
