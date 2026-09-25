package rotp.core.gametest;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.zombie.ZombieData;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.playerpower.PlayerPower;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 /jojoenergy worked on any non-stand power through its energy and max energy, add-on powers included. The
 * port knew only Hamon, Pillar Man, Vampirism and Zombie; other powers now answer through the PlayerPowerData
 * command energy hooks, and the core powers keep their own branches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JojoEnergyCommandGameTests {
	private JojoEnergyCommandGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void jojoEnergyReachesPowerDataHooks(GameTestHelper helper) throws ReflectiveOperationException {
		ServerPlayer user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
				UUID.nameUUIDFromBytes("JojoEnergyHooks".getBytes(StandardCharsets.US_ASCII)), "JojoEnergyHooks"));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the command target");
		try {
			CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
					.withEntity(user).withLevel(helper.getLevel()).withPermission(4);
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);

			// A core power without hooks keeps its own branch.
			power.setPowerType(ModPlayerPowers.ZOMBIE.get());
			ZombieData zombie = PlayerPower.getPowerData(user, ModPlayerPowers.ZOMBIE).orElseThrow();
			float zombieMax = zombie.getMaxEnergy(user);
			helper.assertTrue(zombieMax > 0.0F, "Zombie max energy is " + zombieMax);
			run(helper, source, "jojoenergy set @s 0.5 ratio");
			helper.assertTrue(Math.abs(zombie.getEnergy() - zombieMax * 0.5F) < 0.01F,
					"/jojoenergy set @s 0.5 ratio left Zombie energy at " + zombie.getEnergy() + " of " + zombieMax);

			HookedData hooked = new HookedData();
			hooked.onInit(power);
			install(power, ModPlayerPowers.ZOMBIE.get().getId(), hooked);
			helper.assertTrue(power.getCurTypeData() == hooked, "Could not install the hooked power data");

			run(helper, source, "jojoenergy set @s 0.5 ratio");
			helper.assertTrue(Math.abs(hooked.energy - HookedData.MAX * 0.5F) < 0.001F,
					"/jojoenergy set @s 0.5 ratio set the hooked energy to " + hooked.energy);
			run(helper, source, JojoMod.MOD_ID + " power_energy set @s 150");
			helper.assertTrue(Math.abs(hooked.energy - 150.0F) < 0.001F,
					"/" + JojoMod.MOD_ID + " power_energy set @s 150 set the hooked energy to " + hooked.energy);
			int reported = run(helper, source, "jojoenergy get @s");
			helper.assertTrue(reported == 150, "/jojoenergy get @s reported " + reported);
		}
		finally {
			user.discard();
		}
		helper.succeed();
	}

	@SuppressWarnings("unchecked")
	private static void install(PlayerPower power, ResourceLocation id, PowerData data) throws ReflectiveOperationException {
		Field field = Power.class.getDeclaredField("powerData");
		field.setAccessible(true);
		((Map<ResourceLocation, Either<PowerData, CompoundTag>>) field.get(power)).put(id, Either.left(data));
	}

	private static int run(GameTestHelper helper, CommandSourceStack source, String command) {
		try {
			return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
		}
		catch (CommandSyntaxException error) {
			throw new AssertionError("/" + command + " failed: " + error.getMessage(), error);
		}
	}

	// Energy only through the command hooks, as add-on powers such as Ultimate Lifeform have it.
	private static final class HookedData extends ZombieData {
		private static final float MAX = 400.0F;
		private float energy;

		@Override
		public float getCommandMaxEnergy(LivingEntity user) {
			return MAX;
		}

		@Override
		public float getCommandEnergy() {
			return energy;
		}

		@Override
		public void setCommandEnergy(LivingEntity user, float amount) {
			energy = Mth.clamp(amount, 0.0F, MAX);
		}
	}
}
