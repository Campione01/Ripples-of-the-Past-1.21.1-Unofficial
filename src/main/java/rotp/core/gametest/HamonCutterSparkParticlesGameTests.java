package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonCutterEntity / HamonBubbleCutterEntity tick: on the client every tick,
 * playSparkSound(this, position(), 0.25F) then createHamonSparkParticles(this, position(), 1).
 * The port only restored the sound. The spawn is client-only, so tick()'s bytecode is read as a resource.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonCutterSparkParticlesGameTests {
	private HamonCutterSparkParticlesGameTests() {}

	private static final String IS_CLIENT_SIDE = "net/minecraft/world/level/Level.isClientSide()Z";
	private static final String SPARK_SOUND =
			"rotp/core/client/sound/HamonSparksLoopSound.playSparkSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;F)Z";
	private static final String SPARK_SOUND_LIMITED =
			"rotp/core/client/sound/HamonSparksLoopSound.playSparkSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;FZ)Z";
	private static final String SPARK_PARTICLES =
			"rotp/core/client/particle/CustomParticlesHelper.createHamonSparkParticles(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;I)V";

	@GameTest(template = "empty")
	public static void hamonCutterTickSpawnsSparkParticle(GameTestHelper helper) {
		assertSparkTick(helper, "rotp/core/impl/powers/hamon/entity/HamonCutterEntity");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void bubbleCutterTickSpawnsSparkParticle(GameTestHelper helper) {
		assertSparkTick(helper, "rotp/core/impl/powers/hamon/entity/HamonBubbleCutterEntity");
		helper.succeed();
	}

	// 1.16 HamonBubbleEntity: the same sound + 1 particle, inside the client every-10-ticks branch
	@GameTest(template = "empty")
	public static void hamonBubbleTickSpawnsSparkParticle(GameTestHelper helper) {
		assertSparkTick(helper, "rotp/core/impl/powers/hamon/entity/HamonBubbleEntity", SPARK_SOUND_LIMITED);
		helper.succeed();
	}

	private static void assertSparkTick(GameTestHelper helper, String className) {
		assertSparkTick(helper, className, SPARK_SOUND);
	}

	private static void assertSparkTick(GameTestHelper helper, String className, String soundRef) {
		TickCode tick = readTick(helper, className);
		// if (level().isClientSide()) { ... } compiles to invokevirtual + ifeq past the block
		int branch = tick.find(IS_CLIENT_SIDE, 0xB6, -1);
		helper.assertTrue(branch >= 0 && tick.u1(branch + 3) == 0x99, className + ".tick() has no client-side branch");
		int branchEnd = branch + 3 + (short) ((tick.u1(branch + 4) << 8) | tick.u1(branch + 5));
		int sound = tick.find(soundRef, 0xB8, -1);
		helper.assertTrue(sound > branch && sound < branchEnd, className + ".tick() lost the client spark sound");
		// iconst_1 + invokestatic: exactly one particle, after the sound in the same client branch
		int particles = tick.find(SPARK_PARTICLES, 0xB8, 0x04);
		helper.assertTrue(particles >= 0, className + ".tick() does not spawn 1 Hamon spark particle");
		helper.assertTrue(particles > sound && particles < branchEnd,
				className + ".tick() spawns the particle outside the client branch or before the sound");
	}

	private record TickCode(byte[] code, String[] methodRefs) {
		int u1(int p) {
			return p >= 0 && p < code.length ? code[p] & 0xFF : -1;
		}

		// code offset of an invoke opcode to ref (optionally preceded by the opcode prev), or -1
		int find(String ref, int opcode, int prev) {
			for (int i = 1; i < methodRefs.length; i++) {
				if (!ref.equals(methodRefs[i])) {
					continue;
				}
				for (int p = 1; p + 2 < code.length; p++) {
					if (u1(p) == opcode && ((u1(p + 1) << 8) | u1(p + 2)) == i && (prev < 0 || u1(p - 1) == prev)) {
						return p;
					}
				}
			}
			return -1;
		}
	}

	private static TickCode readTick(GameTestHelper helper, String className) {
		String path = className + ".class";
		InputStream raw = HamonCutterSparkParticlesGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = HamonCutterSparkParticlesGameTests.class.getClassLoader().getResourceAsStream(path);
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
			String[] refs = new String[count];
			for (int i = 1; i < count; i++) {
				if (tag[i] == 10 || tag[i] == 11) {
					refs[i] = utf[a[a[i]]] + "." + utf[a[b[i]]] + utf[b[b[i]]];
				}
			}
			in.readUnsignedShort();
			in.readUnsignedShort();
			in.readUnsignedShort();
			in.skipNBytes(2L * in.readUnsignedShort());
			skipMembers(in);
			int methods = in.readUnsignedShort();
			for (int m = 0; m < methods; m++) {
				in.readUnsignedShort();
				String name = utf[in.readUnsignedShort()];
				String desc = utf[in.readUnsignedShort()];
				int attrs = in.readUnsignedShort();
				for (int k = 0; k < attrs; k++) {
					String attr = utf[in.readUnsignedShort()];
					int len = in.readInt();
					if ("tick".equals(name) && "()V".equals(desc) && "Code".equals(attr)) {
						in.readUnsignedShort();
						in.readUnsignedShort();
						byte[] code = new byte[in.readInt()];
						in.readFully(code);
						return new TickCode(code, refs);
					}
					in.skipNBytes(len);
				}
			}
			helper.fail(className + " has no tick() body");
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
		}
		return new TickCode(new byte[0], new String[0]);
	}

	private static void skipMembers(DataInputStream in) throws IOException {
		int members = in.readUnsignedShort();
		for (int m = 0; m < members; m++) {
			in.skipNBytes(6);
			int attrs = in.readUnsignedShort();
			for (int k = 0; k < attrs; k++) {
				in.skipNBytes(2);
				in.skipNBytes(in.readInt());
			}
		}
	}
}
