package rotp.core.gametest;

import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * PlayerList.respawn removes the dead player first and gives the new player the dead one's
 * network id. A Stand summoned for the removed player must not pass to whoever takes that id:
 * 1.16 kept the Stand bound to its user object, so it removed itself with that user.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandUserIdentityGameTests {
	private StandUserIdentityGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standOfRemovedUserIsNotAdoptedThroughItsId(
			GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player removedUser = FakePlayerFactory.get(level, new GameProfile(
				UUID.fromString("6ad5bb00-a65e-43fc-9815-9b5073dace05"),
				"StandIdentityRemoved"));
		Player presentUser = FakePlayerFactory.get(level, new GameProfile(
				UUID.fromString("6ad5bb00-a65e-43fc-9815-9b5073dace06"),
				"StandIdentityPresent"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(
				JojoMod.resLoc("star_platinum"));
		StandPower removedPower = null;
		StandPower presentPower = null;
		ArmorStand successor = null;
		try {
			helper.assertTrue(standType != null,
					"Missing Star Platinum Stand type");
			Vec3 pos = Vec3.atBottomCenterOf(
					helper.absolutePos(new BlockPos(2, 2, 2)));
			removedUser.moveTo(pos.x, pos.y, pos.z);
			presentUser.moveTo(pos.x + 2.0D, pos.y, pos.z);
			helper.assertTrue(level.addFreshEntity(removedUser)
							&& level.addFreshEntity(presentUser),
					"Could not add Stand identity test players");
			removedPower = grantStand(helper, removedUser, standType);
			presentPower = grantStand(helper, presentUser, standType);

			int reusedId = removedUser.getId();
			removedUser.discard();
			helper.assertTrue(standType.summon(removedUser, removedPower),
					"Could not summon the removed user's Stand");
			StandEntity orphan = removedPower.getSummonedStandEntity();
			helper.assertTrue(orphan != null && orphan.isAddedToLevel(),
					"The removed user's Stand was not added to the level");

			successor = new ArmorStand(level, pos.x, pos.y, pos.z);
			successor.setId(reusedId);
			helper.assertTrue(level.addFreshEntity(successor)
							&& level.getEntity(reusedId) == successor,
					"Could not add the entity that reuses the removed user's id");
			helper.assertTrue(orphan.getUser() != successor,
					"The Stand passed to the entity that reuses its user's id");
			orphan.tick();
			helper.assertTrue(orphan.isRemoved(),
					"The Stand outlived its removed user");

			helper.assertTrue(standType.summon(presentUser, presentPower),
					"Could not summon the present user's Stand");
			StandEntity kept = presentPower.getSummonedStandEntity();
			helper.assertTrue(kept != null, "The present user's Stand is missing");
			kept.tick();
			helper.assertTrue(!kept.isRemoved() && kept.getUser() == presentUser,
					"A Stand whose user is present lost its user");
			helper.succeed();
		}
		finally {
			if (removedPower != null && removedPower.isSummoned()) {
				standType.forceUnsummon(removedUser, removedPower);
			}
			if (presentPower != null && presentPower.isSummoned()) {
				standType.forceUnsummon(presentUser, presentPower);
			}
			if (successor != null) {
				successor.discard();
			}
			removedUser.discard();
			presentUser.discard();
		}
	}

	private static StandPower grantStand(
			GameTestHelper helper, Player user, StandType standType) {
		StandPower power = PowerClass.STAND.attachGet(user);
		StandPowerTransitions.Result inserted = StandPowerTransitions.insert(
				power, new StandInstance(standType));
		helper.assertTrue(
				inserted.status() == StandPowerTransitions.Status.APPLIED,
				"Could not grant Star Platinum: " + inserted.status());
		return power;
	}
}
