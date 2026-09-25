package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonCharge;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.entity.HamonMasterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonUtil.cancelDamageFromBlock (LivingAttackEvent): cactus and sweet berry bush damage costs a Hamon user
 * dmg * 0.5 energy per touching block instead of health; too little energy drains it and the hit lands; a Hamon
 * Master takes none; a Hamon charge takes the hit off its ticks instead.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonBlockDamageGameTests {
	private static final BlockPos USER_POS = new BlockPos(2, 2, 2);

	private HamonBlockDamageGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void hamonEnergyStopsCactusAndBerryBushDamage(GameTestHelper helper) {
		Player user = hamonUser(helper);
		try {
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.setEnergy(10.0F);
			float health = user.getHealth();
			user.hurt(helper.getLevel().damageSources().cactus(), 1.0F);
			helper.assertTrue(user.getHealth() == health, "1.16 Hamon kept cactus damage off: health " + health + " -> " + user.getHealth());
			assertClose(helper, hamon.getEnergy(), 9.5F, "Cactus damage 1 costs 0.5 Hamon energy");
			user.hurt(helper.getLevel().damageSources().sweetBerryBush(), 1.0F);
			helper.assertTrue(user.getHealth() == health, "1.16 Hamon kept sweet berry bush damage off: health " + health + " -> " + user.getHealth());
			assertClose(helper, hamon.getEnergy(), 9.0F, "Sweet berry bush damage 1 costs 0.5 Hamon energy");
			hamon.setEnergy(0.2F);
			user.hurt(helper.getLevel().damageSources().cactus(), 1.0F);
			helper.assertTrue(user.getHealth() < health, "With too little energy the cactus hit lands");
			assertClose(helper, hamon.getEnergy(), 0.0F, "Too little energy is drained by the cactus hit");
			helper.succeed();
		} finally {
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void cactusCostsEnergyForEachTouchingBlock(GameTestHelper helper) {
		helper.setBlock(USER_POS.below(), Blocks.SAND);
		helper.setBlock(USER_POS, Blocks.CACTUS);
		helper.setBlock(USER_POS.above(), Blocks.CACTUS);
		Player user = hamonUser(helper);
		try {
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.setEnergy(10.0F);
			float health = user.getHealth();
			user.hurt(helper.getLevel().damageSources().cactus(), 1.0F);
			helper.assertTrue(user.getHealth() == health, "1.16 Hamon kept cactus damage off: health " + health + " -> " + user.getHealth());
			assertClose(helper, hamon.getEnergy(), 9.0F, "1.16 paid 0.5 energy for each of the 2 cactus blocks in the hitbox");
			helper.succeed();
		} finally {
			user.discard();
			helper.setBlock(USER_POS.above(), Blocks.AIR);
			helper.setBlock(USER_POS, Blocks.AIR);
			helper.setBlock(USER_POS.below(), Blocks.AIR);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void hamonMasterTakesNoCactusDamage(GameTestHelper helper) {
		HamonMasterEntity master = ModEntityTypes.HAMON_MASTER.get().create(helper.getLevel());
		helper.assertTrue(master != null, "Could not create a Hamon Master");
		try {
			place(helper, master);
			master.addMasterHamon();
			HamonData hamon = PlayerPower.getPowerData(master, ModPlayerPowers.HAMON).orElseThrow();
			hamon.setEnergy(0.0F);
			float health = master.getHealth();
			master.hurt(helper.getLevel().damageSources().cactus(), 1.0F);
			helper.assertTrue(master.getHealth() == health, "1.16 kept all block damage off a Hamon Master: health " + health + " -> " + master.getHealth());
			helper.succeed();
		} finally {
			master.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void hamonChargeStopsBerryBushDamage(GameTestHelper helper) {
		Pig pig = EntityType.PIG.create(helper.getLevel());
		helper.assertTrue(pig != null, "Could not create a pig");
		try {
			place(helper, pig);
			EntityHamonChargeState chargeState = EntityHamonChargeState.get(pig);
			chargeState.setHamonCharge(1.0F, 100, null, 0.0F);
			float health = pig.getHealth();
			DamageSource berryBush = helper.getLevel().damageSources().sweetBerryBush();
			pig.hurt(berryBush, 3.0F);
			HamonCharge charge = chargeState.getHamonCharge();
			helper.assertTrue(pig.getHealth() == health, "1.16 a Hamon charge kept block damage off: health " + health + " -> " + pig.getHealth());
			helper.assertTrue(charge != null && charge.getTicks() == 97, "Damage 3 takes 3 ticks off the charge: "
					+ (charge == null ? "no charge" : charge.getTicks()));
			helper.succeed();
		} finally {
			pig.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void cactusHitLeavesNoChargeStateOnPlainMob(GameTestHelper helper) {
		Pig pig = EntityType.PIG.create(helper.getLevel());
		helper.assertTrue(pig != null, "Could not create a pig");
		try {
			place(helper, pig);
			helper.assertTrue(!pig.hasData(ModDataAttachmentTypes.HAMON_CHARGE), "A fresh pig has no Hamon charge state");
			float health = pig.getHealth();
			pig.hurt(helper.getLevel().damageSources().cactus(), 1.0F);
			helper.assertTrue(pig.getHealth() < health, "Without Hamon or a charge the cactus hit lands");
			// 1.16 read an always-present capability; the port must not attach a ticking, saved state here
			helper.assertTrue(!pig.hasData(ModDataAttachmentTypes.HAMON_CHARGE),
					"A cactus hit must not attach a Hamon charge state to a plain mob");
			helper.succeed();
		} finally {
			pig.discard();
		}
	}

	private static Player hamonUser(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.getAbilities().invulnerable = false;
		place(helper, user);
		PowerClass.PLAYER_POWER.attachGet(user).setPowerType(ModPlayerPowers.HAMON.get());
		helper.assertTrue(PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).isPresent(), "Could not give the user Hamon");
		return user;
	}

	private static void place(GameTestHelper helper, LivingEntity entity) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(USER_POS));
		entity.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Could not add " + entity.getType());
	}

	private static void assertClose(GameTestHelper helper, float actual, float expected, String message) {
		helper.assertTrue(Math.abs(actual - expected) < 1.0E-4F, message + ": expected " + expected + ", got " + actual);
	}
}
