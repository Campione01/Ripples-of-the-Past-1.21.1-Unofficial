package rotp.core.gametest;

import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.impl.powers.hamon.entity.HamonBubbleEntity;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 LivingEntity.canAttack was a plain true, so a player- or mob-owned DamagingEntity hit invulnerable mobs and
 * Creative players (hurt() then refused the damage) instead of flying through them. Spectators were never hit and a
 * tamed pet's shot still skipped its owner. Each projectile is ticked once by hand.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DamagingEntityTargetFilterGameTests {
	private DamagingEntityTargetFilterGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void playerShotHitsInvulnerableMob(GameTestHelper helper) {
		ServerPlayer owner = player(helper, "HitFilterOwner", new BlockPos(0, 2, 0));
		Pig pig = helper.spawn(EntityType.PIG, new Vec3(2.5D, 2.0D, 3.5D));
		pig.setNoGravity(true);
		pig.setInvulnerable(true);
		HamonBubbleEntity bubble = null;
		try {
			helper.assertFalse(owner.canAttack(pig), "setup: the 1.21 canAttack base should refuse an invulnerable mob");
			bubble = launchAt(helper, new HamonBubbleEntity(owner, helper.getLevel()), pig);
			helper.assertTrue(bubble.isRemoved(), "a player's bubble flew through an invulnerable mob instead of hitting it");
			helper.succeed();
		}
		finally {
			cleanup(bubble, pig, owner);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void playerShotHitsCreativePlayer(GameTestHelper helper) {
		ServerPlayer owner = player(helper, "HitFilterShooter", new BlockPos(0, 2, 0));
		ServerPlayer target = player(helper, "HitFilterCreative", new BlockPos(2, 2, 3));
		HamonBubbleEntity bubble = null;
		try {
			target.setGameMode(GameType.CREATIVE);
			helper.assertFalse(owner.canAttack(target), "setup: the 1.21 canAttack base should refuse a Creative player");
			bubble = launchAt(helper, new HamonBubbleEntity(owner, helper.getLevel()), target);
			helper.assertTrue(bubble.isRemoved(), "a player's bubble flew through a Creative player instead of hitting it");
			helper.succeed();
		}
		finally {
			cleanup(bubble, target, owner);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void spectatorIsNeverHit(GameTestHelper helper) {
		ServerPlayer owner = player(helper, "HitFilterAimer", new BlockPos(0, 2, 0));
		ServerPlayer target = player(helper, "HitFilterSpectator", new BlockPos(2, 2, 3));
		HamonBubbleEntity control = null;
		HamonBubbleEntity bubble = null;
		try {
			// Control: the same shot lands on the target while it is in Survival.
			control = launchAt(helper, new HamonBubbleEntity(owner, helper.getLevel()), target);
			helper.assertTrue(control.isRemoved(), "setup: the control bubble missed the Survival player");
			target.setGameMode(GameType.SPECTATOR);
			bubble = launchAt(helper, new HamonBubbleEntity(owner, helper.getLevel()), target);
			helper.assertFalse(bubble.isRemoved(), "a bubble hit a spectator");
			helper.succeed();
		}
		finally {
			cleanup(control, bubble, target, owner);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tamedShooterStillSkipsItsOwner(GameTestHelper helper) {
		ServerPlayer master = player(helper, "HitFilterMaster", new BlockPos(2, 2, 3));
		ServerPlayer stranger = player(helper, "HitFilterStranger", new BlockPos(5, 2, 3));
		Wolf wolf = helper.spawn(EntityType.WOLF, new Vec3(0.5D, 2.0D, 0.5D));
		wolf.setNoGravity(true);
		HamonBubbleEntity control = null;
		HamonBubbleEntity bubble = null;
		try {
			wolf.setTame(true, false);
			wolf.setOwnerUUID(master.getUUID());
			master.setGameMode(GameType.CREATIVE);
			stranger.setGameMode(GameType.CREATIVE);
			helper.assertTrue(wolf.isOwnedBy(master), "setup: the wolf does not see its owner");
			// A Creative stranger is hit (1.16 base), the Creative owner is not (TamableAnimal.canAttack).
			control = launchAt(helper, new HamonBubbleEntity(wolf, helper.getLevel()), stranger);
			helper.assertTrue(control.isRemoved(), "a pet's bubble flew through a Creative stranger instead of hitting it");
			bubble = launchAt(helper, new HamonBubbleEntity(wolf, helper.getLevel()), master);
			helper.assertFalse(bubble.isRemoved(), "a pet's bubble hit its own owner");
			helper.succeed();
		}
		finally {
			cleanup(control, bubble, wolf, stranger, master);
		}
	}

	private static ServerPlayer player(GameTestHelper helper, String name, BlockPos relPos) {
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(relPos));
		player.setPos(pos.x, pos.y, pos.z);
		player.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the test player " + name);
		PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.HAMON.get());
		return player;
	}

	// Starts the projectile a block short of the target, flying straight into it, and runs one tick.
	private static <T extends ModdedProjectileEntity> T launchAt(GameTestHelper helper, T projectile, Entity target) {
		Vec3 center = target.getBoundingBox().getCenter();
		projectile.setPos(center.x, center.y, center.z - 1.0D);
		projectile.setDeltaMovement(0.0D, 0.0D, 0.8D);
		helper.assertTrue(helper.getLevel().addFreshEntity(projectile), "Could not add the projectile");
		projectile.tickCount = 0;
		projectile.tick();
		return projectile;
	}

	private static void cleanup(Entity... entities) {
		for (Entity entity : entities) {
			if (entity != null && !entity.isRemoved()) {
				if (entity instanceof Player player) {
					player.removeAllEffects();
				}
				entity.discard();
			}
		}
	}
}
