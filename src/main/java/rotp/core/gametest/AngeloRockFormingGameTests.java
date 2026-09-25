package rotp.core.gametest;

import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands.crazydiamond.AngeloRockEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Angelo rock forming phase: the victim is immobilized, pinned standing at the rock without hurt or walk
 * animation, a player victim is teleported on its own client and loses every effect before entering the rock.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AngeloRockFormingGameTests {
	private AngeloRockFormingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void formingAngeloRockFreezesMobVictim(GameTestHelper helper) {
		Vec3 rockPos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
		Pig pig = new Pig(EntityType.PIG, helper.getLevel());
		pig.setNoGravity(true);
		pig.moveTo(rockPos.x + 0.3D, rockPos.y, rockPos.z + 0.2D);
		helper.assertTrue(helper.getLevel().addFreshEntity(pig), "Could not add pig");
		AngeloRockEntity rock = null;
		try {
			pig.hurtTime = 10;
			pig.deathTime = 5;
			pig.walkAnimation.setSpeed(1.0F);
			pig.walkAnimation.update(1.0F, 1.0F);
			pig.setPose(Pose.CROUCHING);
			rock = AngeloRockEntity.turnIntoRock(helper.getLevel(), pig, rockPos, 0.0F);
			helper.assertTrue(rock != null, "Angelo rock was not created");
			rock.tick();

			helper.assertTrue(pig.hasEffect(ModStatusEffects.IMMOBILIZE), "Forming rock did not immobilize the victim");
			helper.assertTrue(pig.hurtTime == 0 && pig.deathTime == 0,
					"Forming rock kept hurtTime " + pig.hurtTime + " / deathTime " + pig.deathTime);
			helper.assertTrue(pig.walkAnimation.speed(0.0F) == 0.0F && pig.walkAnimation.speed() == 0.0F,
					"Forming rock kept the walk animation");
			helper.assertTrue(pig.getPose() == Pose.STANDING, "Forming rock victim pose is " + pig.getPose());
			assertAtRock(helper, pig, rock);

			for (int i = 0; i < 60 && !rock.isFullyFormed(); i++) {
				rock.tick();
			}
			helper.assertTrue(rock.isFullyFormed(), "Angelo rock never finished forming");
			helper.assertTrue(pig.isRemoved(), "Mob victim was not sealed into the rock");
			helper.succeed();
		}
		finally {
			if (rock != null) {
				rock.discard();
			}
			pig.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void formingAngeloRockTeleportsPlayerAndClearsEffects(GameTestHelper helper) {
		Vec3 rockPos = helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D));
		TeleportRecordingPlayer player = new TeleportRecordingPlayer(helper.getLevel(), "RockForming");
		player.setGameMode(GameType.SURVIVAL);
		player.setNoGravity(true);
		player.setPos(rockPos.x + 0.3D, rockPos.y, rockPos.z - 0.2D);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add test player");
		AngeloRockEntity rock = null;
		try {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400));
			rock = AngeloRockEntity.turnIntoRock(helper.getLevel(), player, rockPos, 0.0F);
			helper.assertTrue(rock != null, "Angelo rock was not created");
			rock.tick();
			helper.assertTrue(player.teleports == 1,
					"Displaced player victim got " + player.teleports + " teleports, expected 1 so its own client is moved");
			helper.assertTrue(player.hasEffect(ModStatusEffects.IMMOBILIZE), "Forming rock did not immobilize the player");
			assertAtRock(helper, player, rock);
			rock.tick();
			helper.assertTrue(player.teleports == 1, "Player already at the rock was teleported again");

			for (int i = 0; i < 60 && !rock.isFullyFormed(); i++) {
				rock.tick();
			}
			helper.assertTrue(rock.isFullyFormed(), "Angelo rock never finished forming");
			helper.assertTrue(LivingComponentPossession.isPossessingSomeone(player), "Player victim did not enter the rock");
			helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED) && !player.hasEffect(ModStatusEffects.IMMOBILIZE),
					"Player entered the rock with effects still active (1.16 removeAllEffects)");
			helper.succeed();
		}
		finally {
			if (rock != null) {
				rock.discard();
			}
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void formingAngeloRockSendsTargetIdToClients(GameTestHelper helper) {
		Vec3 rockPos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 3.5D));
		Pig pig = new Pig(EntityType.PIG, helper.getLevel());
		pig.setNoGravity(true);
		pig.moveTo(rockPos.x, rockPos.y, rockPos.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(pig), "Could not add pig");
		AngeloRockEntity rock = null;
		try {
			rock = AngeloRockEntity.turnIntoRock(helper.getLevel(), pig, rockPos, 0.0F);
			helper.assertTrue(rock != null, "Angelo rock was not created");
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			rock.writeSpawnData(buf);
			buf.readInt();
			helper.assertTrue(buf.readInt() == 0, "Test rock unexpectedly has blocks");
			int targetId = buf.readInt();
			helper.assertTrue(targetId == pig.getId(),
					"Spawn data carries target id " + targetId + ", expected " + pig.getId() + " for the client-side freeze");

			RegistryFriendlyByteBuf roundTrip = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			rock.writeSpawnData(roundTrip);
			AngeloRockEntity copy = new AngeloRockEntity(ModEntityTypes.ANGELO_ROCK.get(), helper.getLevel());
			copy.readSpawnData(roundTrip);
			helper.assertTrue(roundTrip.readableBytes() == 0, "Angelo rock spawn data read does not match its write");
			helper.succeed();
		}
		finally {
			if (rock != null) {
				rock.discard();
			}
			pig.discard();
		}
	}

	private static void assertAtRock(GameTestHelper helper, LivingEntity target, AngeloRockEntity rock) {
		helper.assertTrue(target.position().distanceToSqr(rock.position()) < 1.0E-6D,
				"Victim at " + target.position() + " is not held at the rock " + rock.position());
		helper.assertTrue(Math.abs(target.xo - rock.getX()) < 1.0E-6D && Math.abs(target.xOld - rock.getX()) < 1.0E-6D
				&& Math.abs(target.zOld - rock.getZ()) < 1.0E-6D,
				"Victim old position was not reset, so it renders sliding into the rock");
	}

	/** FakePlayer drops teleport packets; this counts the teleports the server sends. */
	private static final class TeleportRecordingPlayer extends FakePlayer {
		int teleports;

		TeleportRecordingPlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public void teleportTo(double x, double y, double z) {
			teleports++;
			super.teleportTo(x, y, z);
		}
	}
}
