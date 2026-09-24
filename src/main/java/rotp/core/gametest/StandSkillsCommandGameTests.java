package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * "/jojo_ripples stand_skills unlock" on a skill the target already has fails with a text that takes the skill and
 * the player; it was thrown with the player alone, which printed the raw pattern. Creating the failure formats its
 * text, so this runs in the game (StandSkillsUnlockMessageSmokeTest checks the texts outside it).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandSkillsCommandGameTests {
	private StandSkillsCommandGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void failedUnlockNamesTheSkillAndThePlayer(GameTestHelper helper) {
		String name = "SkillsUnlockFail";
		ServerPlayer user = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
		try {
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the command target");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			StandPower power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			StandTypePersistentData data = power.getCurTypeData();
			String skill = data == null ? null : data.getAllSkills().keySet().stream()
					.filter(key -> key.matches("[A-Za-z0-9_.+-]+")).findFirst().orElse(null);
			helper.assertTrue(skill != null, "Star Platinum has no skill to unlock");

			CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
			CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
					.withEntity(user).withLevel(helper.getLevel()).withPermission(4).withSuppressedOutput();
			String command = JojoMod.MOD_ID + " stand_skills unlock @s " + skill;
			try {
				dispatcher.execute(command, source);
			}
			catch (CommandSyntaxException alreadyUnlocked) {
				// the skill may start unlocked; either way it is unlocked now
			}
			helper.assertTrue(data.isSkillUnlocked(skill), "The first unlock did not unlock " + skill);

			CommandSyntaxException failure = null;
			try {
				dispatcher.execute(command, source);
			}
			catch (CommandSyntaxException error) {
				failure = error;
			}
			helper.assertTrue(failure != null, "Unlocking an unlocked skill must fail");
			helper.assertTrue(failure.getRawMessage() instanceof Component message
					&& message.getContents() instanceof TranslatableContents text
					&& "commands.standskills.unlock.failed.single".equals(text.getKey())
					&& text.getArgs().length == 2
					&& skill.equals(text.getArgs()[0])
					&& text.getArgs()[1] instanceof Component player
					&& player.getString().equals(user.getDisplayName().getString()),
					"The failure must name the skill and then the player: " + failure.getRawMessage());
		}
		finally {
			user.discard();
		}
		helper.succeed();
	}
}
