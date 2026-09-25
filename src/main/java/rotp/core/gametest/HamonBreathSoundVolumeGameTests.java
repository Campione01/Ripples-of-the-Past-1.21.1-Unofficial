package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import rotp.core.client.sound.sounds.StoppableSoundVolume;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.abilities.HamonBreathAbility;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonEnergySound.tick set the breath sound volume to energy / max energy (NonStandPower.getMaxEnergy, at least 1)
 * every tick while breathing, so it swelled with the energy bar; after release it dropped by 0.1 per tick from that volume.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonBreathSoundVolumeGameTests {
	private static final float EPS = 0.001F;
	// Client classes: read as bytes only, never loaded on the test server
	private static final String SOUNDS_HELPER = "rotp/core/client/sound/ClientsideSoundsHelper";
	private static final String SOUND_INSTANCE = "rotp/core/client/sound/sounds/EntityStoppableSoundInstance";
	private static final String TRACKER = Type.getInternalName(StoppableSoundVolume.class);
	private static final String BREATH = Type.getInternalName(HamonBreathAbility.BreathInstance.class);

	private HamonBreathSoundVolumeGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void breathSoundFollowsEnergyBar(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Hamon breath sound test player");
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.setBreathStability(hamon.getMaxBreathStability());
			float max = hamon.getMaxEnergy();
			helper.assertTrue(max > 1.0F, "the Hamon test player has no breath stability: max energy=" + max);

			Ability found = power.getAbility("hamon_breath");
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered hamon_breath");
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			helper.assertTrue(action instanceof HamonBreathAbility.BreathInstance,
					"hamon_breath made no BreathInstance: " + action);
			component.setAction(action, user, SyncType.NO_SYNC);
			HamonBreathAbility.BreathInstance breath = (HamonBreathAbility.BreathInstance) action;

			hamon.setEnergy(max);
			near(helper, breath.breathSoundVolume(), 1.0F, "a full energy bar must give the full breath volume");
			hamon.setEnergy(max * 0.25F);
			near(helper, breath.breathSoundVolume(), 0.25F, "a quarter energy bar must give a quarter of the breath volume");
			hamon.setEnergy(0.0F);
			near(helper, breath.breathSoundVolume(), 0.0F, "an empty energy bar must make the breath silent");
			near(helper, HamonBreathAbility.breathSoundVolume(null), 1.0F, "no Hamon data keeps 1.16's full volume");
			near(helper, HamonBreathAbility.BREATH_SOUND_FADE_STEP, 0.1F, "1.16 faded the breath sound by 0.1 per tick");

			// The breath sound's volume, set up as the breath wires it (pinned by breathSoundWiring)
			DoubleSupplier factor = () -> breath.breathSoundVolume();
			StoppableSoundVolume sound = new StoppableSoundVolume(1.0F, 0, HamonBreathAbility.BREATH_SOUND_FADE_STEP, factor);
			hamon.setEnergy(max);
			helper.assertTrue(sound.tick(false), "the breath sound stopped while breathing");
			near(helper, sound.volume(), 1.0F, "the running breath sound must be at full volume with a full bar");
			hamon.setEnergy(max * 0.4F);
			helper.assertTrue(sound.tick(false), "the breath sound stopped while breathing");
			near(helper, sound.volume(), 0.4F, "the running breath sound must follow the energy bar each tick");
			// 1.16: -0.1 per tick from the released volume, so 4 ticks from 0.4 (not a fixed 15-tick fade)
			assertSteppedFade(helper, sound, 0.4F, 4);

			hamon.setEnergy(max);
			sound = new StoppableSoundVolume(1.0F, 0, HamonBreathAbility.BREATH_SOUND_FADE_STEP, factor);
			sound.tick(false);
			assertSteppedFade(helper, sound, 1.0F, 10);

			sound = new StoppableSoundVolume(1.0F, 0, HamonBreathAbility.BREATH_SOUND_FADE_STEP, factor);
			helper.assertFalse(sound.tick(true), "a breath sound released before its first tick must stop at once");
			near(helper, sound.volume(), 0.0F, "a breath sound released before its first tick must be silent");

			// Unscaled sounds keep their own behaviour.
			near(helper, StoppableSoundVolume.running(0.8F, null), 0.8F, "an unscaled looping sound kept its base volume");
			near(helper, StoppableSoundVolume.running(0.8F, () -> 0.5), 0.4F, "the volume factor did not scale the base volume");
			near(helper, StoppableSoundVolume.running(0.8F, () -> 2.0), 0.8F, "the volume factor must be clamped to 1");
			StoppableSoundVolume plain = new StoppableSoundVolume(0.8F, 0, 0.0F, null);
			helper.assertTrue(plain.tick(false), "an unscaled sound stopped while running");
			helper.assertFalse(plain.tick(true), "a sound without a fade-out must stop on its first stopping tick");
			StoppableSoundVolume timed = new StoppableSoundVolume(0.8F, 15, 0.0F, null);
			timed.tick(false);
			helper.assertTrue(timed.tick(true), "a sound with fade-out ticks stopped at once");
			near(helper, timed.volume(), 0.8F, "a timed fade-out must start from the running volume");
			helper.succeed();
		}
		finally {
			component.setAction(null, SyncType.NO_SYNC);
			user.discard();
		}
	}

	/**
	 * The client-only glue, read from the class files: the breath starts its sound with the energy factor and the
	 * 0.1 step, the helper and the sound instance pass both on, and every sound tick takes its volume from the tracker.
	 */
	@GameTest(template = "empty")
	public static void breathSoundWiring(GameTestHelper helper) {
		Type soundEvent = Type.getType(SoundEvent.class);
		Type doubleSupplier = Type.getType(DoubleSupplier.class);
		String playDesc = Type.getMethodDescriptor(Type.VOID_TYPE, soundEvent, Type.getType(LivingEntity.class),
				Type.getType(EntityActionInstance.class), Type.getType(ActionPhase.class), Type.FLOAT_TYPE, Type.FLOAT_TYPE,
				Type.INT_TYPE, Type.FLOAT_TYPE, doubleSupplier);
		String instanceDesc = Type.getMethodDescriptor(Type.VOID_TYPE, soundEvent, Type.getType(SoundSource.class),
				Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.BOOLEAN_TYPE, Type.getType(Entity.class), Type.LONG_TYPE,
				Type.getType(BooleanSupplier.class), Type.INT_TYPE, Type.FLOAT_TYPE, doubleSupplier);

		// BreathInstance.onSetPhase: playLoopingActionSound(..., BREATH_SOUND_FADE_STEP, () -> breathSoundVolume())
		ClassNode breathClass = classNode(BREATH);
		MethodNode onSetPhase = method(breathClass, "onSetPhase", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ActionPhase.class)));
		MethodInsnNode play = onlyCall(onSetPhase, SOUNDS_HELPER, "playLoopingActionSound", playDesc);
		AbstractInsnNode arg = prevReal(play);
		if (!(arg instanceof InvokeDynamicInsnNode indy) || !"getAsDouble".equals(indy.name)) {
			throw fail("the breath sound gets no volume factor lambda: " + describe(arg));
		}
		Handle impl = (Handle) indy.bsmArgs[1];
		boolean readsEnergy = BREATH.equals(impl.getOwner()) && ("breathSoundVolume".equals(impl.getName())
				|| hasCall(method(breathClass, impl.getName(), impl.getDesc()), BREATH, "breathSoundVolume", "()F"));
		helper.assertTrue(readsEnergy, "the breath sound's volume factor does not read breathSoundVolume(): " + impl);
		arg = prevReal(indy);
		if (arg instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD) {
			arg = prevReal(arg);
		}
		helper.assertTrue(arg instanceof LdcInsnNode ldc && ldc.cst instanceof Float step
				&& step == HamonBreathAbility.BREATH_SOUND_FADE_STEP,
				"the breath sound is not started with the 1.16 fade step: " + describe(arg));

		// ClientsideSoundsHelper passes fadeOutTicks, fadeOutStep and volumeFactor (slots 6, 7, 8) to the instance
		MethodNode helperPlay = method(classNode(SOUNDS_HELPER), "playLoopingActionSound", playDesc);
		MethodInsnNode newInstance = onlyCall(helperPlay, SOUND_INSTANCE, "<init>", instanceDesc);
		expectLoads(helper, newInstance, "the sound helper drops the fade step or factor",
				Opcodes.ALOAD, 8, Opcodes.FLOAD, 7, Opcodes.ILOAD, 6);

		// The instance builds its tracker from volume, fadeOutTicks, fadeOutStep, volumeFactor (slots 3, 10, 11, 12)
		ClassNode instanceClass = classNode(SOUND_INSTANCE);
		MethodInsnNode newTracker = onlyCall(method(instanceClass, "<init>", instanceDesc), TRACKER, "<init>",
				Type.getMethodDescriptor(Type.VOID_TYPE, Type.FLOAT_TYPE, Type.INT_TYPE, Type.FLOAT_TYPE, doubleSupplier));
		expectLoads(helper, newTracker, "the sound instance drops a volume argument",
				Opcodes.ALOAD, 12, Opcodes.FLOAD, 11, Opcodes.ILOAD, 10, Opcodes.FLOAD, 3);

		// tick: keepPlaying = tracker.tick(stopWhen.getAsBoolean()); volume = tracker.volume(); if (!keepPlaying) stop
		MethodNode tick = method(instanceClass, "tick", "()V");
		MethodInsnNode trackerTick = onlyCall(tick, TRACKER, "tick", "(Z)Z");
		AbstractInsnNode stopping = prevReal(trackerTick);
		helper.assertTrue(stopping instanceof MethodInsnNode call && "getAsBoolean".equals(call.name),
				"the sound tick does not pass its stop condition to the tracker: " + describe(stopping));
		AbstractInsnNode store = nextReal(trackerTick);
		if (!(store instanceof VarInsnNode keep) || keep.getOpcode() != Opcodes.ISTORE) {
			throw fail("the sound tick ignores the tracker's result: " + describe(store));
		}
		boolean stopsWhenDone = false;
		for (AbstractInsnNode insn = tick.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof VarInsnNode load && load.getOpcode() == Opcodes.ILOAD && load.var == keep.var) {
				AbstractInsnNode jump = nextReal(load);
				stopsWhenDone |= jump != null && jump.getOpcode() == Opcodes.IFNE;
			}
		}
		helper.assertTrue(stopsWhenDone, "the sound tick does not stop when the tracker is done");
		AbstractInsnNode put = nextReal(onlyCall(tick, TRACKER, "volume", "()F"));
		helper.assertTrue(put instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
				&& "volume".equals(field.name) && "F".equals(field.desc),
				"the sound tick does not take its volume from the tracker: " + describe(put));
		helper.succeed();
	}

	// Release the sound and check 1.16's fixed 0.1 steps down to silence after the given number of ticks.
	private static void assertSteppedFade(GameTestHelper helper, StoppableSoundVolume sound, float from, int silentAfter) {
		for (int i = 1; i < silentAfter; i++) {
			helper.assertTrue(sound.tick(true), "the breath sound stopped " + i + " ticks into its fade from " + from);
			near(helper, sound.volume(), from - 0.1F * i, "the breath sound must drop 0.1 per tick after release (tick " + i + ")");
		}
		boolean running = sound.tick(true);
		helper.assertTrue(sound.volume() < EPS, "the breath sound from " + from + " must be silent after " + silentAfter
				+ " ticks, got " + sound.volume());
		// 1.16 float steps can leave a tiny rest for one more tick
		helper.assertTrue(!running || !sound.tick(true), "the breath sound from " + from + " did not stop once silent");
	}

	private static ClassNode classNode(String internalName) {
		InputStream found = HamonBreathSoundVolumeGameTests.class.getResourceAsStream("/" + internalName + ".class");
		if (found == null) {
			found = HamonBreathSoundVolumeGameTests.class.getClassLoader().getResourceAsStream(internalName + ".class");
		}
		try (InputStream in = found) {
			if (in == null) {
				throw fail("no class file for " + internalName);
			}
			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return node;
		}
		catch (IOException e) {
			throw fail("could not read " + internalName + ": " + e);
		}
	}

	private static MethodNode method(ClassNode owner, String name, String desc) {
		for (MethodNode method : owner.methods) {
			if (method.name.equals(name) && method.desc.equals(desc)) {
				return method;
			}
		}
		throw fail("no method " + owner.name + "." + name + desc);
	}

	private static MethodInsnNode onlyCall(MethodNode method, String owner, String name, String desc) {
		MethodInsnNode found = null;
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) {
				if (found != null) {
					throw fail(method.name + " calls " + owner + "." + name + desc + " more than once");
				}
				found = call;
			}
		}
		if (found == null) {
			throw fail(method.name + " does not call " + owner + "." + name + desc);
		}
		return found;
	}

	private static boolean hasCall(MethodNode method, String owner, String name, String desc) {
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) {
				return true;
			}
		}
		return false;
	}

	// The last arguments before a call, as (opcode, slot) pairs from the last one backwards
	private static void expectLoads(GameTestHelper helper, AbstractInsnNode call, String message, int... opcodeSlots) {
		AbstractInsnNode insn = call;
		for (int i = 0; i < opcodeSlots.length; i += 2) {
			insn = prevReal(insn);
			helper.assertTrue(insn instanceof VarInsnNode load && load.getOpcode() == opcodeSlots[i] && load.var == opcodeSlots[i + 1],
					message + ": expected opcode " + opcodeSlots[i] + " slot " + opcodeSlots[i + 1] + ", got " + describe(insn));
		}
	}

	private static AbstractInsnNode prevReal(AbstractInsnNode insn) {
		do {
			insn = insn.getPrevious();
		}
		while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
		do {
			insn = insn.getNext();
		}
		while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	private static String describe(AbstractInsnNode insn) {
		if (insn == null) {
			return "nothing";
		}
		if (insn instanceof VarInsnNode load) {
			return "opcode " + load.getOpcode() + " slot " + load.var;
		}
		if (insn instanceof LdcInsnNode ldc) {
			return "ldc " + ldc.cst;
		}
		if (insn instanceof MethodInsnNode call) {
			return call.owner + "." + call.name + call.desc;
		}
		if (insn instanceof FieldInsnNode field) {
			return "field " + field.name + " opcode " + field.getOpcode();
		}
		return "opcode " + insn.getOpcode();
	}

	private static GameTestAssertException fail(String message) {
		return new GameTestAssertException(message);
	}

	private static void near(GameTestHelper helper, float actual, float expected, String message) {
		helper.assertTrue(Math.abs(actual - expected) < EPS, message + ": expected " + expected + ", got " + actual);
	}
}
