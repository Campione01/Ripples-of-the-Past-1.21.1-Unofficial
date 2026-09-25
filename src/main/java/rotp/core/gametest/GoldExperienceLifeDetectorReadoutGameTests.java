package rotp.core.gametest;

import java.util.List;
import java.util.OptionalInt;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeDetectorReadout;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeDetectorReadout.ReadoutKind;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeDetectorReadout.ReadoutLine;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entityglow.EntityGlowChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ClientEventHandler.getEntityGEDetectHp / hudRenderEntityGEDetectorData: the Life Detector readout picks the live
 * detected entity closest to the look direction and lists HP, energy, Stand stamina and Resolve (full under the Resolve
 * effect) as whole percents.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceLifeDetectorReadoutGameTests {
	private GoldExperienceLifeDetectorReadoutGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void lookPicksTheDetectedEntity(GameTestHelper helper) {
		EntityGlowChannel channel = EntityGlowChannel.GE_LIFE_DETECTOR;
		Pig left = helper.spawn(EntityType.PIG, 0, 2, 1);
		Pig right = helper.spawn(EntityType.PIG, 4, 2, 1);
		Pig hamonOnly = helper.spawn(EntityType.PIG, 2, 2, 1);
		try {
			channel.apply(left, OptionalInt.of(0xFFE600), 80);
			channel.apply(right, OptionalInt.of(0xFFE600), 80);
			EntityGlowChannel.HAMON_DETECTOR.apply(hamonOnly, OptionalInt.of(0xFFFF00), 80);
			Level level = helper.getLevel();
			Vec3 eye = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 4)));

			helper.assertTrue(channel.mostLookedAt(level, eye, lookAt(eye, left)) == left,
					"Looking at the left detected pig did not pick it");
			helper.assertTrue(channel.mostLookedAt(level, eye, lookAt(eye, right)) == right,
					"Looking at the right detected pig did not pick it");
			Entity towardHamon = channel.mostLookedAt(level, eye, lookAt(eye, hamonOnly));
			helper.assertTrue(towardHamon == left || towardHamon == right,
					"The pick left the Life Detector entries: " + towardHamon);

			left.discard();
			helper.assertTrue(channel.mostLookedAt(level, eye, lookAt(eye, left)) == right,
					"A removed entity was still picked; 1.16 kept only live ones");
			channel.clear(right);
			helper.assertTrue(channel.mostLookedAt(level, eye, lookAt(eye, right)) == null,
					"A cleared entity was still picked");
			helper.succeed();
		}
		finally {
			channel.clear(left);
			channel.clear(right);
			EntityGlowChannel.HAMON_DETECTOR.clear(hamonOnly);
			left.discard();
			right.discard();
			hamonOnly.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void readoutListsHpStaminaAndResolve(GameTestHelper helper) {
		Pig pig = helper.spawn(EntityType.PIG, 1, 2, 1);
		Player user = standUser(helper);
		try {
			pig.setHealth(4);
			List<ReadoutLine> pigLines = GoldExperienceLifeDetectorReadout.readout(pig);
			helper.assertTrue(pigLines.equals(List.of(new ReadoutLine(ReadoutKind.HEALTH, 40))),
					"A pig at 4 of 10 HP must show only HP 40%: " + pigLines);

			StandPower power = StandPower.get(user);
			ResolveCounter counter = power.resolveCounter;
			user.setHealth(15);
			power.setStamina(power.getMaxStamina() * 0.5F);
			counter.setResolveValue(power, counter.getMaxResolveValue(power) * 0.3F, 0);
			List<ReadoutLine> lines = GoldExperienceLifeDetectorReadout.readout(user);
			helper.assertTrue(lines.stream().map(ReadoutLine::kind).toList()
					.equals(List.of(ReadoutKind.HEALTH, ReadoutKind.STAMINA, ReadoutKind.RESOLVE)),
					"A Stand user without a player power must show HP, stamina and Resolve: " + lines);
			int stamina = power.isStaminaInfinite() ? 100 : 50;
			helper.assertTrue(lines.get(0).percent() == 75, "HP 15 of 20 must read 75%: " + lines);
			helper.assertTrue(Math.abs(lines.get(1).percent() - stamina) <= 1,
					"Stamina must read " + stamina + "%: " + lines);
			helper.assertTrue(Math.abs(lines.get(2).percent() - 30) <= 1, "Resolve at 0.3 must read 30%: " + lines);

			user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0));
			counter.setResolveValue(power, counter.getMaxResolveValue(power) * 0.3F, 0);
			lines = GoldExperienceLifeDetectorReadout.readout(user);
			helper.assertTrue(lines.get(lines.size() - 1).equals(new ReadoutLine(ReadoutKind.RESOLVE, 100)),
					"Under the Resolve effect 1.16 showed Resolve 100%: " + lines);
			helper.succeed();
		}
		finally {
			pig.discard();
			user.discard();
		}
	}

	private static Vec3 lookAt(Vec3 eye, Entity target) {
		return target.getBoundingBox().getCenter().subtract(eye).normalize();
	}

	private static Player standUser(GameTestHelper helper) {
		Player user = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
		helper.assertTrue(type != null, "Missing registered Magician's Red");
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant Magician's Red");
		helper.assertTrue(power.usesResolve() && power.usesStamina(), "Magician's Red must use Resolve and stamina");
		return user;
	}
}
