package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Optional;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.ClHamonInteractAskTeacherPacket;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.entity.HamonMasterEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerData;
import rotp.core.powersystem.playerpower.PlayerPowerType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 HamonUtil.canGetPower(HAMON) / givePower(HAMON): a non-Zeppeli Hamon teacher (for example a Hamon Master)
 * gives Hamon to a learner with no power, but a learner whose power is not replaceable with Hamon (for example
 * Zombie) is refused. See rotp.core.impl.powers.hamon.HamonUtil.startLearningHamon.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonTeacherGameTests {
	private static final BlockPos POS = new BlockPos(2, 2, 2);

	private HamonTeacherGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void powerlessLearnerGetsHamonFromTeacher(GameTestHelper helper) {
		Player learner = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		HamonMasterEntity teacher = ModEntityTypes.HAMON_MASTER.get().create(helper.getLevel());
		helper.assertTrue(teacher != null, "Could not create a Hamon Master");
		try {
			place(helper, learner);
			place(helper, teacher);
			teacher.addMasterHamon();
			HamonData teacherHamon = PlayerPower.getPowerData(teacher, ModPlayerPowers.HAMON).orElseThrow();
			PlayerPower learnerPower = PowerClass.PLAYER_POWER.attachGet(learner);
			HamonUtil.startLearningHamon(helper.getLevel(), learner, learnerPower, teacher, teacherHamon);
			helper.assertTrue(learnerPower.getPowerType() == ModPlayerPowers.HAMON.get(),
					"1.16 a powerless learner is given Hamon by a non-Zeppeli teacher: got " + learnerPower.getPowerType());
			helper.assertTrue(PlayerPower.getPowerData(learner, ModPlayerPowers.HAMON).isPresent(),
					"The learner must have Hamon power data after learning");
			helper.succeed();
		} finally {
			learner.discard();
			teacher.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void zombieLearnerIsRefusedHamon(GameTestHelper helper) {
		Player learner = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		HamonMasterEntity teacher = ModEntityTypes.HAMON_MASTER.get().create(helper.getLevel());
		helper.assertTrue(teacher != null, "Could not create a Hamon Master");
		try {
			place(helper, learner);
			place(helper, teacher);
			teacher.addMasterHamon();
			HamonData teacherHamon = PlayerPower.getPowerData(teacher, ModPlayerPowers.HAMON).orElseThrow();
			PlayerPower learnerPower = PowerClass.PLAYER_POWER.attachGet(learner);
			learnerPower.setPowerType(ModPlayerPowers.ZOMBIE.get());
			HamonUtil.startLearningHamon(helper.getLevel(), learner, learnerPower, teacher, teacherHamon);
			helper.assertTrue(learnerPower.getPowerType() == ModPlayerPowers.ZOMBIE.get(),
					"1.16 a Zombie learner keeps its power instead of getting Hamon: got " + learnerPower.getPowerType());
			helper.assertTrue(PlayerPower.getPowerData(learner, ModPlayerPowers.HAMON).isEmpty(),
					"A refused learner must not have Hamon power data");
			helper.succeed();
		} finally {
			learner.discard();
			teacher.discard();
		}
	}

	/**
	 * 1.16 InputHandler (:459-467) sent ClHamonInteractAskTeacherPacket whenever canGetPower(HAMON) held, and a player
	 * teacher can only be asked through that key: the learner is queued with the teacher (HamonData.addNewPlayerLearner).
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void keyAskQueuesPowerlessLearnerWithPlayerTeacher(GameTestHelper helper) {
		askPlayerTeacherByKey(helper, null, true);
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void keyAskQueuesLearnerWhosePowerHamonMayReplace(GameTestHelper helper) {
		askPlayerTeacherByKey(helper, new ReplaceableByHamonPowerType(), true);
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void keyAskRefusesLearnerWhosePowerHamonMayNotReplace(GameTestHelper helper) {
		askPlayerTeacherByKey(helper, ModPlayerPowers.ZOMBIE.get(), false);
	}

	private static void askPlayerTeacherByKey(GameTestHelper helper, PlayerPowerType<?> learnerType, boolean expectQueued) {
		Player learner = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player teacher = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		PlayerPower learnerPower = null;
		try {
			place(helper, learner);
			place(helper, teacher);
			PowerClass.PLAYER_POWER.attachGet(teacher).setPowerType(ModPlayerPowers.HAMON.get());
			HamonData teacherHamon = PlayerPower.getPowerData(teacher, ModPlayerPowers.HAMON).orElseThrow();
			learnerPower = PowerClass.PLAYER_POWER.attachGet(learner);
			setCurrentType(learnerPower, learnerType);
			helper.assertTrue(learnerPower.canGetPower(ModPlayerPowers.HAMON.get()) == expectQueued,
					"test premise: canGetPower(HAMON) of " + describe(learnerType) + " is not " + expectQueued);

			IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
					new Class<?>[] { IPayloadContext.class }, (proxy, method, args) -> {
						if (method.getName().equals("player")) {
							return learner;
						}
						throw new UnsupportedOperationException(method.getName());
					});
			new ClHamonInteractAskTeacherPacket.Handler(JojoMod.resLoc("cl_hamon_interact_ask_teacher"))
					.handle(new ClHamonInteractAskTeacherPacket(teacher.getId()), context);

			helper.assertTrue(teacherHamon.playerWantsToLearn(learner) == expectQueued,
					"1.16 the Hamon-window key asks a teacher exactly when canGetPower(HAMON): a learner with "
							+ describe(learnerType) + " must " + (expectQueued ? "" : "not ")
							+ "be queued with the player teacher, queued=" + teacherHamon.playerWantsToLearn(learner));
			helper.assertTrue(PlayerPower.getPowerData(learner, ModPlayerPowers.HAMON).isEmpty(),
					"asking a player teacher must not give Hamon by itself");
			helper.succeed();
		} finally {
			if (learnerPower != null) {
				setCurrentType(learnerPower, null);
			}
			learner.discard();
			teacher.discard();
		}
	}

	private static String describe(PlayerPowerType<?> type) {
		return type == null ? "no power" : "the power " + type.getId();
	}

	// The core registers no power that Hamon may replace (the Speedwagon power is an add-on), so the unregistered
	// test type is put in place directly, without the grant callbacks that need a registered type.
	private static void setCurrentType(PlayerPower power, PlayerPowerType<?> type) {
		boolean testType = type instanceof ReplaceableByHamonPowerType
				|| power.getPowerType() instanceof ReplaceableByHamonPowerType;
		if (!testType) {
			power.setPowerType(type);
			return;
		}
		try {
			Field current = PlayerPower.class.getDeclaredField("curPowerType");
			current.setAccessible(true);
			current.set(power, Optional.ofNullable(type));
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Could not set the test power type", error);
		}
	}

	private static final class ReplaceableByHamonPowerType extends PlayerPowerType<PlayerPowerData> {
		private ReplaceableByHamonPowerType() {
			super(ResourceLocation.fromNamespaceAndPath("rotp_test", "replaceable_by_hamon"), new MovesetBuilder());
		}

		@Override
		public PlayerPowerData newDataInstance() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isReplaceableWith(PlayerPowerType<?> newType) {
			return newType == ModPlayerPowers.HAMON.get();
		}
	}

	private static void place(GameTestHelper helper, net.minecraft.world.entity.LivingEntity entity) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(POS));
		entity.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Could not add " + entity.getType());
	}
}
