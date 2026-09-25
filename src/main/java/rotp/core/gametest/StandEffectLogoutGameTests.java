package rotp.core.gametest;

import java.util.List;
import java.util.UUID;

import rotp.core.core.EventHandler;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.effect.StandEffectsTarget;
import rotp.core.powersystem.standpower.effect.UserStandEffects;
import rotp.core.impl.stands.crazydiamond.DriedBloodDropsEffect;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 GameplayEventHandler.onPlayerLogout -> StandEffectsTracker.onStandUserLogout.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandEffectLogoutGameTests {

	private StandEffectLogoutGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void logoutEventReachesUserStandEffects(GameTestHelper helper) {
		FakePlayer user = fake(helper, "LogoutUser");
		StandPower power = PowerClass.STAND.attachGet(user);
		UserStandEffects original = power.userStandEffects;
		RecordingEffects recording = new RecordingEffects(power);
		boolean published = helper.getLevel().getServer().isPublished();
		try {
			power.userStandEffects = recording;
			EventHandler.onStandUserLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(user));
			helper.assertTrue(recording.calls == 1 && recording.lastPublished == published,
					"Logout event must run the Stand effects logout pass once with the server's published state; calls="
							+ recording.calls + ", published=" + recording.lastPublished + "/" + published);
		}
		finally {
			power.userStandEffects = original;
			cleanup(user);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void logoutKeepsEffectsOnUnpublishedServer(GameTestHelper helper) {
		FakePlayer user = fake(helper, "LogoutSolo");
		FakePlayer target = fake(helper, "LogoutSoloTarget");
		try {
			StandPower power = PowerClass.STAND.attachGet(user);
			CountingEffect effect = addEffect(power, target, true);
			boolean published = helper.getLevel().getServer().isPublished();
			EventHandler.onStandUserLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(user));
			boolean kept = power.userStandEffects.getById(effect.getId()) == effect;
			helper.assertTrue(kept == !published && effect.stops == (published ? 1 : 0),
					"Only LAN/dedicated servers drop Stand effects on logout; published=" + published
							+ ", kept=" + kept + ", stops=" + effect.stops);
		}
		finally {
			cleanup(user, target);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void logoutStopsOnlyFlaggedEffects(GameTestHelper helper) {
		FakePlayer user = fake(helper, "LogoutMulti");
		FakePlayer target = fake(helper, "LogoutMultiTarget");
		try {
			StandPower power = PowerClass.STAND.attachGet(user);
			CountingEffect removable = addEffect(power, target, true);
			CountingEffect retained = addEffect(power, target, false);
			power.userStandEffects.removeLogoutEffects(false);
			helper.assertTrue(removable.stops == 0 && power.userStandEffects.getById(removable.getId()) == removable,
					"Single-player logout must keep Stand effects saved");
			power.userStandEffects.removeLogoutEffects(true);
			helper.assertTrue(removable.stops == 1 && power.userStandEffects.getById(removable.getId()) == null
					&& StandEffectsTarget.getEffectsReadOnly(target).noneMatch(e -> e == removable),
					"Logout must stop and remove a removeOnUserLogout effect once; stops=" + removable.stops);
			helper.assertTrue(retained.stops == 0 && power.userStandEffects.getById(retained.getId()) == retained
					&& StandEffectsTarget.getEffectsReadOnly(target).anyMatch(e -> e == retained),
					"Logout removed an effect that opts out of logout removal");
		}
		finally {
			cleanup(user, target);
		}
		helper.succeed();
	}

	private static FakePlayer fake(GameTestHelper helper, String name) {
		return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
	}

	private static CountingEffect addEffect(StandPower power, ServerPlayer target, boolean removeOnLogout) {
		CountingEffect effect = new CountingEffect(removeOnLogout);
		effect.withTarget(target);
		power.userStandEffects.addEffect(effect);
		return effect;
	}

	private static void cleanup(FakePlayer... players) {
		for (FakePlayer player : players) {
			StandPower power = StandPower.get(player);
			if (power != null) {
				for (StandEffectInstance effect : List.copyOf(power.userStandEffects.getEffects())) {
					power.userStandEffects.removeEffect(effect);
				}
			}
			player.discard();
		}
	}

	private static final class RecordingEffects extends UserStandEffects {
		int calls;
		boolean lastPublished;

		RecordingEffects(StandPower power) {
			super(power);
		}

		@Override
		public void removeLogoutEffects(boolean publishedServer) {
			calls++;
			lastPublished = publishedServer;
		}
	}

	private static final class CountingEffect extends DriedBloodDropsEffect {
		int stops;

		CountingEffect(boolean removeOnLogout) {
			super(ModStandAbilities.EFFECT_CD_BLOOD_DROPS.get());
			removeOnUserLogout = removeOnLogout;
		}

		@Override protected void stop() { stops++; super.stop(); }
	}
}
