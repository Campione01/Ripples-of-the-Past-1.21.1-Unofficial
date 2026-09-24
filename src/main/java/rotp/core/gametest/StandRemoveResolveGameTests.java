package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItems;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 /stand clear ran power.clear() (ResolveCounter.onClearStandType: the Resolve value, boosts and records) and
 * power.fullStandClear() (clearLevels: the Resolve levels, and the learning of every Stand). /stand give and
 * /stand random with replace ran power.clear() first, which keeps the levels. The stand remover item's REMOVE
 * mode was power.clear() too.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandRemoveResolveGameTests {
	private static final float EPS = 1.0E-3F;

	private StandRemoveResolveGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standRemoveClearsResolveAndItsLevels(GameTestHelper helper) {
		Fixture f = new Fixture(helper, "StandRemoveResolve");
		try {
			f.fillResolve();
			f.run("stand remove @s");
			helper.assertTrue(!f.power.hasPower(), "/jojo_ripples stand remove left the Stand");
			f.assertResolveCleared("/jojo_ripples stand remove");
			f.run("stand give @s jojo_ripples:star_platinum");
			helper.assertTrue(f.power.getResolveLevel() == f.levelOfANewStand(),
					"1.16 /stand clear also cleared the Resolve levels: level " + f.power.getResolveLevel());
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standGiveReplaceClearsResolveButKeepsLevels(GameTestHelper helper) {
		Fixture f = new Fixture(helper, "StandGiveReplaceResolve");
		try {
			f.fillResolve();
			f.run("stand give @s jojo_ripples:magicians_red true");
			helper.assertTrue(f.power.hasPower() && f.power.getPowerType().getId().equals(JojoMod.resLoc("magicians_red")),
					"/jojo_ripples stand give ... true did not give Magician's Red");
			f.assertResolveCleared("/jojo_ripples stand give ... true");
			f.run("stand give @s jojo_ripples:star_platinum true");
			helper.assertTrue(f.power.getResolveLevel() == Math.max(Fixture.LEVEL, f.levelOfANewStand()),
					"1.16 power.clear() kept Star Platinum's Resolve level: level " + f.power.getResolveLevel());
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standRemoverItemStillClearsResolveOnly(GameTestHelper helper) {
		Fixture f = new Fixture(helper, "StandRemoverItemResolve");
		try {
			f.fillResolve();
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STAND_REMOVER_ONE_TIME.get()));
			helper.assertTrue(ModItems.STAND_REMOVER_ONE_TIME.get().use(helper.getLevel(), f.user, InteractionHand.MAIN_HAND)
					.getResult().consumesAction(), "The stand remover did not remove the Stand");
			helper.assertTrue(!f.power.hasPower(), "The stand remover left the Stand");
			f.assertResolveCleared("The stand remover");
			f.run("stand give @s jojo_ripples:star_platinum");
			helper.assertTrue(f.power.getResolveLevel() == Math.max(Fixture.LEVEL, f.levelOfANewStand()),
					"The stand remover must keep the Resolve level: level " + f.power.getResolveLevel());
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	private static final class Fixture {
		static final int LEVEL = 2;
		final GameTestHelper helper;
		final ServerPlayer user;
		final StandPower power;
		final CommandSourceStack source;

		Fixture(GameTestHelper helper, String name) {
			this.helper = helper;
			user = FakePlayerFactory.get(helper.getLevel(),
					new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the command target");
			power = PowerClass.STAND.attachGet(user);
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			source = helper.getLevel().getServer().createCommandSourceStack()
					.withEntity(user).withLevel(helper.getLevel()).withPermission(4).withSuppressedOutput();
		}

		// a Resolve level, a value in its no-decay ticks, an attack boost and a record, as a fight leaves them
		void fillResolve() {
			power.setResolveLevel(LEVEL);
			helper.assertTrue(power.getResolveLevel() == LEVEL, "Could not set the Resolve level");
			CompoundTag nbt = power.resolveCounter.writeNBT();
			nbt.putFloat("Resolve", 400.0F);
			nbt.putInt("ResolveTicks", 300);
			nbt.putFloat("BoostAttack", 2.0F);
			ListTag records = new ListTag();
			records.add(FloatTag.valueOf(900.0F));
			nbt.put("ResolveRecord", records);
			nbt.putFloat("MaxAchieved", 900.0F);
			power.resolveCounter.readNBT(nbt);
			ResolveCounter counter = power.resolveCounter;
			helper.assertTrue(Math.abs(counter.getResolveValue() - 400.0F) < EPS && counter.boostAttack == 2.0F
					&& counter.maxAchievedValue == 900.0F, "Could not fill the Resolve");
		}

		void assertResolveCleared(String what) {
			ResolveCounter counter = power.resolveCounter;
			helper.assertTrue(counter.getResolveValue() == 0.0F && counter.noResolveDecayTicks == 0,
					what + " kept the Resolve value: " + counter.getResolveValue());
			helper.assertTrue(counter.boostAttack == 1.0F, what + " kept the attack boost: " + counter.boostAttack);
			helper.assertTrue(counter.maxAchievedValue == 0.0F
					&& counter.writeNBT().getList("ResolveRecord", Tag.TAG_FLOAT).isEmpty(),
					what + " kept the Resolve records (max " + counter.maxAchievedValue + ")");
		}

		// skipStandProgression gives a new Stand its highest level
		int levelOfANewStand() {
			return JojoModConfig.getCommonConfigInstance(false).skipStandProgression.get() ? power.getMaxResolveLevel() : 0;
		}

		void run(String command) {
			try {
				helper.getLevel().getServer().getCommands().getDispatcher().execute(JojoMod.MOD_ID + " " + command, source);
			}
			catch (CommandSyntaxException error) {
				throw new AssertionError("/" + JojoMod.MOD_ID + " " + command + " failed: " + error.getMessage(), error);
			}
		}

		void close() {
			if (power.isSummoned() && power.getPowerType() != null) {
				power.getPowerType().forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
