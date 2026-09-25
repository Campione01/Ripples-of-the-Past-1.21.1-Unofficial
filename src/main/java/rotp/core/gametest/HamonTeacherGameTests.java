package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.entity.HamonMasterEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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

	private static void place(GameTestHelper helper, net.minecraft.world.entity.LivingEntity entity) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(POS));
		entity.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Could not add " + entity.getType());
	}
}
