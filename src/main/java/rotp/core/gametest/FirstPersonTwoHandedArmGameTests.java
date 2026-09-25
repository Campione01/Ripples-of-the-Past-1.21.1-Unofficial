package rotp.core.gametest;

import java.util.UUID;
import java.util.function.Predicate;

import rotp.core.core.JojoMod;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.client.particle.custom.FirstPersonHamonAura.TwoHandedOffHand;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ClientEventHandler.onRenderHand: with both hands free and Overdrive Barrage, S.Y.O. Barrage, Wall Climbing,
 * Erratic Blaze King or Divine Sandstorm selected, or while wall climbing, first person also draws the off hand.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FirstPersonTwoHandedArmGameTests {
	private static final Predicate<String> NOTHING_SELECTED = name -> false;

	private FirstPersonTwoHandedArmGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void twoHandedMovesMatchThe116List(GameTestHelper helper) {
		try (Fixture f = Fixture.hamon(helper, "TwoHandedHamonIds")) {
			for (String name : new String[] { "overdrive_barrage", "sunlight_yellow_overdrive_barrage", "wall_climbing" }) {
				helper.assertTrue(TwoHandedOffHand.isTwoHandedAbility(f.id(name)), "1.16 drew both hands for " + name);
			}
			for (String name : new String[] { "sunlight_yellow_overdrive", "zoom_punch" }) {
				helper.assertTrue(!TwoHandedOffHand.isTwoHandedAbility(f.id(name)), name + " is a one-handed move");
			}
		}
		try (Fixture f = Fixture.pillarman(helper, "TwoHandedPillarmanIds")) {
			for (String name : new String[] { "pillarman_erratic_blaze_king", "pillarman_divine_sandstorm" }) {
				helper.assertTrue(TwoHandedOffHand.isTwoHandedAbility(f.id(name)), "1.16 drew both hands for " + name);
			}
			helper.assertTrue(!TwoHandedOffHand.isTwoHandedAbility(f.id("pillarman_heavy_punch")),
					"Heavy Punch is a one-handed move");
		}
		helper.assertTrue(!TwoHandedOffHand.isTwoHandedAbility(null), "a missing ability id matched");
		helper.assertTrue(!TwoHandedOffHand.isTwoHandedAbility(
				new AbilityId(PowerClass.STAND, JojoMod.resLoc("hamon"), "overdrive_barrage")),
				"a stand ability with a Hamon move name matched");
		helper.assertTrue(!TwoHandedOffHand.isTwoHandedAbility(
				new AbilityId(PowerClass.PLAYER_POWER, JojoMod.resLoc("pillarman"), "wall_climbing")),
				"Hamon move name matched under the Pillar Man power");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void offHandDrawnForSelectedMoveWithFreeHandsOrWallClimb(GameTestHelper helper) {
		try (Fixture f = Fixture.hamon(helper, "TwoHandedHamonArm")) {
			helper.assertTrue(!TwoHandedOffHand.shouldRender(f.user, NOTHING_SELECTED),
					"off hand drawn with nothing selected");
			helper.assertTrue(TwoHandedOffHand.shouldRender(f.user, "overdrive_barrage"::equals),
					"Overdrive Barrage selected with free hands did not draw the off hand");
			helper.assertTrue(TwoHandedOffHand.shouldRender(f.user, "wall_climbing"::equals),
					"Wall Climbing selected with free hands did not draw the off hand");
			helper.assertTrue(!TwoHandedOffHand.shouldRender(f.user, "sunlight_yellow_overdrive"::equals),
					"one-handed S.Y.O. drew the off hand");
			helper.assertTrue(!TwoHandedOffHand.shouldRender(f.user, "pillarman_divine_sandstorm"::equals),
					"a Pillar Man move name counted for a Hamon user");

			// 1.16 MCUtil.areHandsFree: gloves count as a free hand, a sword does not.
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GLOVES.get()));
			helper.assertTrue(TwoHandedOffHand.shouldRender(f.user, "overdrive_barrage"::equals),
					"gloves in the main hand hid the off hand");
			f.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
			helper.assertTrue(!TwoHandedOffHand.shouldRender(f.user, "overdrive_barrage"::equals),
					"off hand drawn over a held sword");

			// 1.16: wall climbing draws the off hand whatever is selected.
			f.hamon.trSetWallClimbing(true, true, 1.0F, false, 0.0F);
			helper.assertTrue(TwoHandedOffHand.shouldRender(f.user, NOTHING_SELECTED),
					"wall climbing did not draw the off hand");
			f.hamon.trSetWallClimbing(false, false, 0.0F, false, 0.0F);
			helper.assertTrue(!TwoHandedOffHand.shouldRender(f.user, NOTHING_SELECTED),
					"off hand still drawn after wall climbing stopped");

			// The running barrage counts as selected.
			f.user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			f.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			EntityActionInstance action = f.ability("overdrive_barrage")
					.initActionOnAbilityUse(helper.getLevel(), f.user, f.user, null);
			f.component.setAction(action, f.user, SyncType.NO_SYNC);
			helper.assertTrue(TwoHandedOffHand.shouldRender(f.user, NOTHING_SELECTED),
					"running Overdrive Barrage did not draw the off hand");
		}
		try (Fixture f = Fixture.pillarman(helper, "TwoHandedPillarmanArm")) {
			helper.assertTrue(TwoHandedOffHand.shouldRender(f.user, "pillarman_erratic_blaze_king"::equals),
					"Erratic Blaze King selected did not draw the off hand");
			helper.assertTrue(!TwoHandedOffHand.shouldRender(f.user, "overdrive_barrage"::equals),
					"a Hamon move name counted for a Pillar Man user");
		}
		helper.succeed();
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final LivingComponentAction component;
		private HamonData hamon;

		private Fixture(GameTestHelper helper, String name) {
			this.helper = helper;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.setPos(origin.x, origin.y, origin.z);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the test player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			component = LivingComponentAction.getComponent(user);
		}

		private static Fixture hamon(GameTestHelper helper, String name) {
			Fixture f = new Fixture(helper, name);
			f.power.setPowerType(ModPlayerPowers.HAMON.get());
			f.hamon = PlayerPower.getPowerData(f.user, ModPlayerPowers.HAMON).orElseThrow();
			f.hamon.learnSkill(ModHamonSkills.OVERDRIVE_BARRAGE.get());
			f.hamon.setBreathStability(f.hamon.getMaxBreathStability());
			f.hamon.setEnergy(f.hamon.getMaxEnergy());
			return f;
		}

		private static Fixture pillarman(GameTestHelper helper, String name) {
			Fixture f = new Fixture(helper, name);
			f.power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			return f;
		}

		private AbilityId id(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found != null, "Missing registered " + name);
			return found.getAbilityId();
		}

		private EntityActionAbility ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			return (EntityActionAbility) found;
		}

		@Override
		public void close() {
			if (hamon != null) {
				hamon.trSetWallClimbing(false, false, 0.0F, false, 0.0F);
			}
			component.setAction(null, SyncType.NO_SYNC);
			user.discard();
		}
	}
}
