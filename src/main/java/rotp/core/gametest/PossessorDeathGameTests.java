package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands.crazydiamond.AngeloRockEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 beforeLivingDeath: a player who dies while possessing (Pillar Man hide, Angelo rock) leaves the target and gets
 * the pre-possession game mode back before respawn copies it; a canceled death keeps the possession.
 * FakePlayer.die is a no-op, so the tests fire the same CommonHooks.onLivingDeath call that die() makes.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PossessorDeathGameTests {
	private PossessorDeathGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void dyingHiderLeavesHostAndGetsGameModeBack(GameTestHelper helper) {
		RecordingPlayer hider = createPlayer(helper, "DeadHider", GameType.ADVENTURE);
		Pig host = createHost(helper);
		try {
			LivingComponentPossession.setPossessionTarget(hider, host, LivingComponentPossession.PILLARMAN_HIDE_IN_ENTITY);
			helper.assertTrue(hider.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, "Hiding player is not a spectator");
			helper.assertTrue(possessors(host).contains(possession(hider)), "Host does not list the hiding player");

			hider.setHealth(0.0F);
			boolean canceled = CommonHooks.onLivingDeath(hider, hider.damageSources().genericKill());

			helper.assertFalse(canceled, "Plain death of a hiding player was canceled");
			helper.assertTrue(!LivingComponentPossession.isPossessingSomeone(hider), "Possession outlived the possessor's death");
			helper.assertTrue(hider.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
					"Dead possessor kept " + hider.gameMode.getGameModeForPlayer() + ", respawn would copy it (expected ADVENTURE)");
			helper.assertTrue(!possessors(host).contains(possession(hider)), "Host still lists the dead possessor");
			helper.assertTrue(hider.hurtSources.isEmpty(), "Leaving the host on death hurt the player again");
			helper.succeed();
		}
		finally {
			host.discard();
			hider.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void canceledDeathKeepsPossession(GameTestHelper helper) {
		RecordingPlayer hider = createPlayer(helper, "SavedHider", GameType.SURVIVAL);
		Pig host = createHost(helper);
		// stands in for cheat death: a NORMAL listener cancels and heals
		Consumer<LivingDeathEvent> cancelDeath = event -> {
			if (event.getEntity() == hider) {
				event.setCanceled(true);
				hider.setHealth(hider.getMaxHealth());
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, LivingDeathEvent.class, cancelDeath);
		try {
			LivingComponentPossession.setPossessionTarget(hider, host, LivingComponentPossession.PILLARMAN_HIDE_IN_ENTITY);
			hider.setHealth(0.0F);
			boolean canceled = CommonHooks.onLivingDeath(hider, hider.damageSources().genericKill());

			helper.assertTrue(canceled, "The canceling death listener did not run");
			helper.assertTrue(LivingComponentPossession.getEntityPossessedBy(hider) == host, "A canceled death ended the possession");
			helper.assertTrue(hider.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, "A canceled death left spectator mode");
			helper.assertTrue(possessors(host).contains(possession(hider)), "A canceled death removed the player from the host");
			helper.succeed();
		}
		finally {
			NeoForge.EVENT_BUS.unregister(cancelDeath);
			host.discard();
			hider.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void dyingRockPossessorIsNotKilledAgain(GameTestHelper helper) {
		RecordingPlayer sealed = createPlayer(helper, "DeadInRock", GameType.SURVIVAL);
		AngeloRockEntity rock = new AngeloRockEntity(ModEntityTypes.ANGELO_ROCK.get(), helper.getLevel());
		Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
		rock.setPos(pos.x, pos.y, pos.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(rock), "Could not add Angelo rock");
		try {
			LivingComponentPossession.setPossessionTarget(sealed, rock, "angelo_rock");
			sealed.setHealth(0.0F);
			boolean canceled = CommonHooks.onLivingDeath(sealed, sealed.damageSources().genericKill());

			helper.assertFalse(canceled, "Plain death of a sealed player was canceled");
			helper.assertTrue(!LivingComponentPossession.isPossessingSomeone(sealed), "Rock possession outlived the player's death");
			helper.assertTrue(sealed.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
					"Dead rock possessor kept " + sealed.gameMode.getGameModeForPlayer());
			helper.assertTrue(sealed.hurtSources.isEmpty(),
					"Dying in the rock triggered " + sealed.hurtSources.size() + " extra rock kill(s)");
			helper.succeed();
		}
		finally {
			rock.discard();
			sealed.discard();
		}
	}

	private static Set<LivingComponentPossession> possessors(Entity target) {
		Set<LivingComponentPossession> set = LivingComponentPossession.getEntitiesPossessing(target);
		return set != null ? set : Set.of();
	}

	private static LivingComponentPossession possession(RecordingPlayer player) {
		return player.getData(ModDataAttachmentTypes.ENTITY_POSSESSION.get());
	}

	private static Pig createHost(GameTestHelper helper) {
		Pig host = helper.spawn(EntityType.PIG, new Vec3(1.5D, 2.0D, 1.5D));
		host.setNoAi(true);
		return host;
	}

	private static RecordingPlayer createPlayer(GameTestHelper helper, String name, GameType gameType) {
		RecordingPlayer player = new RecordingPlayer(helper.getLevel(), name);
		player.setGameMode(gameType);
		player.setNoGravity(true);
		player.setPos(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add test player");
		return player;
	}

	/** FakePlayer ignores damage; this records each hit. */
	private static final class RecordingPlayer extends FakePlayer {
		final List<DamageSource> hurtSources = new ArrayList<>();

		RecordingPlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public boolean hurt(DamageSource source, float amount) {
			hurtSources.add(source);
			return true;
		}
	}
}
