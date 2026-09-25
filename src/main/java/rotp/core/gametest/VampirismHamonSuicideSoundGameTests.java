package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.DoubleSupplier;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
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
import rotp.core.impl.powers.hamon.abilities.HamonBreathAbility;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.abilities.VampirismHamonSuicideAbility;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 VampirismHamonSuicide.startedHolding played HamonEnergySound: while held its volume was the power's
 * energy / max energy (blood / max blood for a vampire), and after the hold it dropped by 0.1 per tick.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismHamonSuicideSoundGameTests {
	private static final float EPS = 0.001F;
	// Client class: read as bytes only, never loaded on the test server
	private static final String SOUNDS_HELPER = "rotp/core/client/sound/ClientsideSoundsHelper";
	private static final String SUICIDE = Type.getInternalName(VampirismHamonSuicideAbility.HamonSuicideInstance.class);

	private VampirismHamonSuicideSoundGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void suicideSoundFollowsBloodBar(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the vampire Hamon suicide sound test player");
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
			data.setVampireHamonUser(true, null);
			float max = data.getMaxBlood(user);
			helper.assertTrue(max > 1.0F, "the vampire test player has no blood bar: max blood=" + max);

			Ability found = power.getAbility("vampirism_hamon_suicide");
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered vampirism_hamon_suicide");
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			helper.assertTrue(action instanceof VampirismHamonSuicideAbility.HamonSuicideInstance,
					"vampirism_hamon_suicide made no HamonSuicideInstance: " + action);
			component.setAction(action, user, SyncType.NO_SYNC);
			VampirismHamonSuicideAbility.HamonSuicideInstance suicide = (VampirismHamonSuicideAbility.HamonSuicideInstance) action;

			data.setBloodLevel(max);
			near(helper, suicide.bloodSoundVolume(), 1.0F, "a full blood bar must give the full concentration volume");
			data.setBloodLevel(max * 0.5F);
			near(helper, suicide.bloodSoundVolume(), 0.5F, "half the blood bar must give half the concentration volume");
			data.setBloodLevel(0.0F);
			near(helper, suicide.bloodSoundVolume(), 0.0F, "an empty blood bar must make the concentration silent");
			data.setBloodLevel(max * 2.0F);
			near(helper, suicide.bloodSoundVolume(), 1.0F, "blood over the bar must be clamped to the full volume");
			near(helper, VampirismHamonSuicideAbility.bloodSoundVolume(null, user), 1.0F, "no vampirism data keeps 1.16's full volume");

			// The loop as the action wires it (pinned by suicideSoundWiring): half blood, then released
			DoubleSupplier factor = () -> suicide.bloodSoundVolume();
			StoppableSoundVolume sound = new StoppableSoundVolume(1.0F, 0, HamonBreathAbility.BREATH_SOUND_FADE_STEP, factor);
			data.setBloodLevel(max * 0.5F);
			helper.assertTrue(sound.tick(false), "the concentration sound stopped while held");
			near(helper, sound.volume(), 0.5F, "the held concentration sound must follow the blood bar");
			for (int i = 1; i < 5; i++) {
				helper.assertTrue(sound.tick(true), "the concentration sound stopped " + i + " ticks after release");
				near(helper, sound.volume(), 0.5F - 0.1F * i, "the concentration sound must drop 0.1 per tick after release");
			}
			boolean running = sound.tick(true);
			helper.assertTrue(sound.volume() < EPS, "the concentration sound from 0.5 was not silent after 5 ticks: " + sound.volume());
			helper.assertTrue(!running || !sound.tick(true), "the concentration sound did not stop once silent");
			helper.succeed();
		}
		finally {
			component.setAction(null, SyncType.NO_SYNC);
			user.discard();
		}
	}

	/** The client-only call, read from the class file: blood volume factor, no fixed fade ticks, the 0.1 fade step. */
	@GameTest(template = "empty")
	public static void suicideSoundWiring(GameTestHelper helper) {
		String playDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(SoundEvent.class), Type.getType(LivingEntity.class),
				Type.getType(EntityActionInstance.class), Type.getType(ActionPhase.class), Type.FLOAT_TYPE, Type.FLOAT_TYPE,
				Type.INT_TYPE, Type.FLOAT_TYPE, Type.getType(DoubleSupplier.class));
		ClassNode suicideClass = classNode(SUICIDE);
		MethodNode onSetPhase = method(suicideClass, "onSetPhase", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ActionPhase.class)));
		MethodInsnNode play = null;
		for (AbstractInsnNode insn = onSetPhase.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode call && SOUNDS_HELPER.equals(call.owner) && "playLoopingActionSound".equals(call.name)) {
				helper.assertTrue(play == null && playDesc.equals(call.desc),
						"the Hamon suicide sound is not started once through the fade-step overload: " + call.desc);
				play = call;
			}
		}
		if (play == null) {
			throw fail("the Hamon suicide does not start its concentration sound");
		}
		AbstractInsnNode arg = prevReal(play);
		if (!(arg instanceof InvokeDynamicInsnNode indy) || !"getAsDouble".equals(indy.name)) {
			throw fail("the Hamon suicide sound gets no volume factor lambda: " + describe(arg));
		}
		Handle impl = (Handle) indy.bsmArgs[1];
		boolean readsBlood = SUICIDE.equals(impl.getOwner()) && ("bloodSoundVolume".equals(impl.getName())
				|| hasCall(method(suicideClass, impl.getName(), impl.getDesc()), SUICIDE, "bloodSoundVolume", "()F"));
		helper.assertTrue(readsBlood, "the Hamon suicide sound's volume factor does not read bloodSoundVolume(): " + impl);
		arg = prevReal(indy);
		if (arg instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD) {
			arg = prevReal(arg);
		}
		helper.assertTrue(arg instanceof LdcInsnNode ldc && ldc.cst instanceof Float step
				&& step == HamonBreathAbility.BREATH_SOUND_FADE_STEP,
				"the Hamon suicide sound is not started with the 1.16 fade step: " + describe(arg));
		arg = prevReal(arg);
		helper.assertTrue(arg != null && arg.getOpcode() == Opcodes.ICONST_0,
				"the Hamon suicide sound must fade by step, not over fixed ticks: " + describe(arg));
		helper.succeed();
	}

	private static ClassNode classNode(String internalName) {
		InputStream found = VampirismHamonSuicideSoundGameTests.class.getResourceAsStream("/" + internalName + ".class");
		if (found == null) {
			found = VampirismHamonSuicideSoundGameTests.class.getClassLoader().getResourceAsStream(internalName + ".class");
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

	private static boolean hasCall(MethodNode method, String owner, String name, String desc) {
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) {
				return true;
			}
		}
		return false;
	}

	private static AbstractInsnNode prevReal(AbstractInsnNode insn) {
		do {
			insn = insn.getPrevious();
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
		return "opcode " + insn.getOpcode();
	}

	private static GameTestAssertException fail(String message) {
		return new GameTestAssertException(message);
	}

	private static void near(GameTestHelper helper, float actual, float expected, String message) {
		helper.assertTrue(Math.abs(actual - expected) < EPS, message + ": expected " + expected + ", got " + actual);
	}
}
