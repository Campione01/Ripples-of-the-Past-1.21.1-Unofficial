package rotp.core.gametest;

import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.ClHamonPickTechniquePacket;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonTechnique;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonTechniqueTabGui.isLocked: no character technique can be picked until both Hamon Strength and
 * Hamon Control reach the first technique slot level. The menu Pick packet must refuse a pick below it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonTechniqueLockGameTests {
	private HamonTechniqueLockGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void pickPacketWaitsForFirstTechniqueLevel(GameTestHelper helper) {
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HamonTechniqueLock"));
		player.setGameMode(GameType.SURVIVAL);
		player.setNoGravity(true);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add Hamon player");
		try {
			PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
			HamonTechnique zeppeli = ModHamonSkills.CHARACTER_ZEPPELI.get();
			int unlock = HamonData.techniqueSkillRequirement(0);
			helper.assertTrue(unlock == 20, "First technique slot level changed: " + unlock);

			setLevels(helper, hamon, 0, 0);
			assertRejected(helper, player, hamon, zeppeli, "Level 0/0");
			setLevels(helper, hamon, unlock, unlock - 1);
			assertRejected(helper, player, hamon, zeppeli, "Control one level short");
			setLevels(helper, hamon, unlock - 1, unlock);
			assertRejected(helper, player, hamon, zeppeli, "Strength one level short");

			setLevels(helper, hamon, unlock, unlock);
			helper.assertFalse(HamonTechnique.techniquesLocked(hamon), "Both stats at level " + unlock + " still lock techniques");
			helper.assertTrue(ClHamonPickTechniquePacket.pickFromClient(player, zeppeli.getRegistryKey()),
					"Pick packet refused at level " + unlock + "/" + unlock);
			HamonTechnique picked = hamon.getCharacterTechnique();
			helper.assertTrue(picked != null && picked.getRegistryKey().equals(zeppeli.getRegistryKey()),
					"Pick packet did not set Zeppeli: " + (picked != null ? picked.getRegistryKey() : null));
			helper.assertFalse(HamonTechnique.techniquesLocked(hamon), "A picked technique must keep the tab unlocked");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	private static void setLevels(GameTestHelper helper, HamonData hamon, int strength, int control) {
		hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, HamonData.pointsAtLevel(strength), true, true);
		hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(control), true, true);
		helper.assertTrue(hamon.getHamonStrengthLevel() == strength && hamon.getHamonControlLevel() == control,
				"Stat levels not applied: expected " + strength + "/" + control + ", actual "
						+ hamon.getHamonStrengthLevel() + "/" + hamon.getHamonControlLevel());
	}

	private static void assertRejected(GameTestHelper helper, ServerPlayer player, HamonData hamon,
			HamonTechnique technique, String label) {
		helper.assertTrue(HamonTechnique.techniquesLocked(hamon), label + ": techniques not locked");
		helper.assertFalse(ClHamonPickTechniquePacket.pickFromClient(player, technique.getRegistryKey()),
				label + ": locked pick packet was accepted");
		helper.assertTrue(hamon.getCharacterTechnique() == null, label + ": locked pick packet set a technique");
	}
}
