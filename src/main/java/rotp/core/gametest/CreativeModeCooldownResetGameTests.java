package rotp.core.gametest;

import java.util.UUID;
import java.util.function.Supplier;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerData;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GameplayEventHandler.onGameModeChange: switching to Creative reset the ability cooldowns of both powers and
 * removed Immobilize, Stun and Hamon Shock. Other game mode changes kept them.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeModeCooldownResetGameTests {
	private static final String ABILITY = "creative_reset_probe";
	private static final int COOLDOWN = 200;

	private CreativeModeCooldownResetGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void creativeClearsStandAndHamonCooldownsAndStun(GameTestHelper helper) {
		ServerPlayer player = player(helper, "CreativeResetHamon");
		try {
			StandPower stand = PowerClass.STAND.attachGet(player);
			StandType starPlatinum = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(starPlatinum != null, "Missing Star Platinum");
			helper.assertTrue(StandPowerTransitions.insert(stand, new StandInstance(starPlatinum)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			stand.setAbilityCooldown(ABILITY, COOLDOWN);

			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
			hamon.setAbilityCooldown(ABILITY, COOLDOWN);

			player.addEffect(new MobEffectInstance(ModStatusEffects.IMMOBILIZE, COOLDOWN));
			player.addEffect(new MobEffectInstance(ModStatusEffects.STUN, COOLDOWN));
			player.addEffect(new MobEffectInstance(ModStatusEffects.HAMON_SHOCK, COOLDOWN));
			assertEffects(helper, player, true, "Survival setup");

			// not Creative: nothing is reset
			player.setGameMode(GameType.ADVENTURE);
			helper.assertTrue(stand.getAbilityCooldown(ABILITY) > 0, "Adventure cleared the Stand cooldown");
			helper.assertTrue(hamon.getAbilityCooldown(ABILITY) > 0, "Adventure cleared the Hamon cooldown");
			assertEffects(helper, player, true, "After Adventure");

			player.setGameMode(GameType.CREATIVE);
			helper.assertTrue(stand.getAbilityCooldown(ABILITY) == 0,
					"Creative kept the Stand cooldown: " + stand.getAbilityCooldown(ABILITY));
			helper.assertTrue(hamon.getAbilityCooldown(ABILITY) == 0,
					"Creative kept the Hamon cooldown: " + hamon.getAbilityCooldown(ABILITY));
			assertEffects(helper, player, false, "After Creative");
		}
		finally {
			player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void creativeClearsVampirismAndPillarmanCooldowns(GameTestHelper helper) {
		ServerPlayer vampire = player(helper, "CreativeResetVampire");
		try {
			VampirismData data = givePower(helper, vampire, ModPlayerPowers.VAMPIRISM);
			data.setAbilityCooldown(ABILITY, COOLDOWN);
			vampire.setGameMode(GameType.CREATIVE);
			helper.assertTrue(data.getAbilityCooldown(ABILITY) == 0,
					"Creative kept the Vampirism cooldown: " + data.getAbilityCooldown(ABILITY));
		}
		finally {
			vampire.discard();
		}
		ServerPlayer pillarman = player(helper, "CreativeResetPillarman");
		try {
			PillarmanData data = givePower(helper, pillarman, ModPlayerPowers.PILLAR_MAN);
			data.setAbilityCooldown(ABILITY, COOLDOWN);
			pillarman.setGameMode(GameType.CREATIVE);
			helper.assertTrue(data.getAbilityCooldown(ABILITY) == 0,
					"Creative kept the Pillar Man cooldown: " + data.getAbilityCooldown(ABILITY));
		}
		finally {
			pillarman.discard();
		}
		helper.succeed();
	}

	private static <T extends PlayerPowerType<D>, D extends PlayerPowerData> D givePower(
			GameTestHelper helper, ServerPlayer player, Supplier<T> type) {
		PowerClass.PLAYER_POWER.attachGet(player).setPowerType(type.get());
		D data = PlayerPower.getPowerData(player, type).orElse(null);
		helper.assertTrue(data != null, "Could not give " + type.get());
		return data;
	}

	private static ServerPlayer player(GameTestHelper helper, String name) {
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		player.setGameMode(GameType.SURVIVAL);
		player.setNoGravity(true);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add " + name);
		return player;
	}

	private static void assertEffects(GameTestHelper helper, ServerPlayer player, boolean present, String when) {
		assertEffect(helper, player, ModStatusEffects.IMMOBILIZE, "Immobilize", present, when);
		assertEffect(helper, player, ModStatusEffects.STUN, "Stun", present, when);
		assertEffect(helper, player, ModStatusEffects.HAMON_SHOCK, "Hamon Shock", present, when);
	}

	private static void assertEffect(GameTestHelper helper, ServerPlayer player, Holder<MobEffect> effect, String name,
			boolean present, String when) {
		helper.assertTrue(player.hasEffect(effect) == present,
				when + ": " + name + (present ? " is missing" : " was not removed"));
	}
}
