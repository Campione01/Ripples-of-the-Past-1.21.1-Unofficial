package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.DamagingEntity;
import rotp.core.impl.powers.hamon.abilities.HamonSunlightYellowOverdriveBarrageAbility.SYOverdriveBarrageInstance;
import rotp.core.impl.powers.hamon.entity.HamonBubbleEntity;
import rotp.core.impl.powers.pillarman.abilities.PillarmanSelfDetonationAbility.PillarmanExplosion;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 JojoModUtil.canHarm (and MCUtil.canHarm) ended in attacker.canAttack(target), whose LivingEntity base was a plain
 * true; 1.21's base also refuses a Creative player, an invulnerable entity and every player on Peaceful
 * (canBeSeenAsEnemy). So a non-Stand attacker's ability took such targets in 1.16: a Pillar Man's self-detonation kept
 * them in its explosion (PillarmanSelfDetonation's filter, MCUtil.canHarm), which pushed them, and the Sunlight Yellow
 * Overdrive Barrage immobilized a living target it struck (IMMOBILIZE before the hit; ImmobilizeEffect spared a Creative
 * player itself). The team test before it and the attackers' own rules stay: a mob refuses a Creative or spectator
 * player (1.16 MobEntity.canAttack), also as the owner of a projectile (DamagingEntity.canHitEntity asked
 * owner.canAttack), a tamed animal and a hungry zombie spare their owner, an illager a baby villager, and an attacker
 * class with another rule of its own keeps its answer. A spectator is refused to every attacker: 1.16's gathers and rays
 * left it out before canHarm. The Peaceful rows set the server's difficulty and restore it in the same call, before any
 * tick.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CanHarmAsIn116GameTests {
	private static final String BATCH = "can_harm_116";

	private CanHarmAsIn116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void aPlayersAbilitiesTakeCreativeInvulnerableAndPeacefulTargetsAsIn116(GameTestHelper helper) {
		List<Entity> spawned = new ArrayList<>();
		MinecraftServer server = helper.getLevel().getServer();
		Difficulty difficulty = server.getWorldData().getDifficulty();
		PlayerTeam team = null;
		try {
			ServerPlayer user = player(helper, spawned, "CanHarmUser", new BlockPos(1, 2, 1), GameType.SURVIVAL);
			PowerClass.PLAYER_POWER.attachGet(user).setPowerType(ModPlayerPowers.HAMON.get());

			ServerPlayer creative = player(helper, spawned, "CanHarmCreative", new BlockPos(1, 2, 3), GameType.CREATIVE);
			helper.assertTrue(!user.canAttack(creative), "setup: the 1.21 canAttack base should refuse a Creative player");
			strike(helper, user, creative, true, false, "a Creative player");

			// 1.16's gathers and rays left a spectator out before canHarm
			ServerPlayer spectator = player(helper, spawned, "CanHarmSpectator", new BlockPos(5, 2, 3), GameType.SPECTATOR);
			strike(helper, user, spectator, false, false, "a spectator player");

			Cow cow = cow(helper, spawned, new BlockPos(3, 2, 3));
			cow.setInvulnerable(true);
			helper.assertTrue(!user.canAttack(cow), "setup: the 1.21 canAttack base should refuse an invulnerable cow");
			strike(helper, user, cow, true, true, "an invulnerable cow");

			ServerPlayer survivor = player(helper, spawned, "CanHarmPeaceful", new BlockPos(3, 2, 1), GameType.SURVIVAL);
			server.setDifficulty(Difficulty.PEACEFUL, true);
			try {
				helper.assertTrue(!user.canAttack(survivor), "setup: the 1.21 canAttack base should refuse a player on Peaceful");
				strike(helper, user, survivor, true, true, "a player on Peaceful");
			}
			finally {
				server.setDifficulty(difficulty, true);
			}

			// the team test before the attacker's rule stays
			ServerPlayer mate = player(helper, spawned, "CanHarmMate", new BlockPos(5, 2, 1), GameType.SURVIVAL);
			team = helper.getLevel().getScoreboard().addPlayerTeam("canharm116" + UUID.randomUUID().toString().substring(0, 8));
			team.setAllowFriendlyFire(false);
			helper.getLevel().getScoreboard().addPlayerToTeam(user.getScoreboardName(), team);
			helper.getLevel().getScoreboard().addPlayerToTeam(mate.getScoreboardName(), team);
			strike(helper, user, mate, false, false, "a teammate without friendly fire");
		}
		finally {
			if (team != null) {
				helper.getLevel().getScoreboard().removePlayerTeam(team);
			}
			if (server.getWorldData().getDifficulty() != difficulty) {
				server.setDifficulty(difficulty, true);
			}
			spawned.forEach(Entity::discard);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void canHarmKeepsTheAttackersOwnRulesForACreativeOrInvulnerableTargetAsIn116(GameTestHelper helper) {
		List<Entity> spawned = new ArrayList<>();
		MinecraftServer server = helper.getLevel().getServer();
		Difficulty difficulty = server.getWorldData().getDifficulty();
		try {
			ServerPlayer owner = player(helper, spawned, "CanHarmOwner", new BlockPos(1, 2, 1), GameType.CREATIVE);
			ServerPlayer stranger = player(helper, spawned, "CanHarmStranger", new BlockPos(3, 2, 1), GameType.CREATIVE);
			ServerPlayer spectator = player(helper, spawned, "CanHarmWatcher", new BlockPos(5, 2, 1), GameType.SPECTATOR);
			ServerPlayer survivor = player(helper, spawned, "CanHarmSurvivor", new BlockPos(7, 2, 1), GameType.SURVIVAL);
			Cow cow = cow(helper, spawned, new BlockPos(7, 2, 3));
			cow.setInvulnerable(true);

			Wolf wolf = EntityType.WOLF.create(helper.getLevel());
			helper.assertTrue(wolf != null, "setup: could not create the wolf");
			wolf.setTame(true, false);
			wolf.setOwnerUUID(owner.getUUID());
			add(helper, spawned, wolf, new BlockPos(1, 2, 3));
			helper.assertTrue(!wolf.canAttack(cow), "setup: the 1.21 canAttack base should refuse an invulnerable cow");
			helper.assertTrue(!JojoModUtil.canHarm(wolf, owner) && !JojoModUtil.canHarm(wolf, stranger)
					&& JojoModUtil.canHarm(wolf, cow),
					"1.16 TamableAnimal.canAttack spared its owner and MobEntity.canAttack refused a Creative player on the"
							+ " plain-true base: a tamed wolf may harm its Creative owner " + JojoModUtil.canHarm(wolf, owner)
							+ ", a Creative stranger " + JojoModUtil.canHarm(wolf, stranger) + ", an invulnerable cow "
							+ JojoModUtil.canHarm(wolf, cow));

			// HungryZombieEntity.getOwner drops an owner who is not a vampire
			PowerClass.PLAYER_POWER.attachGet(owner).setPowerType(ModPlayerPowers.VAMPIRISM.get());
			HungryZombieEntity zombie = new HungryZombieEntity(helper.getLevel());
			zombie.setOwner(owner);
			add(helper, spawned, zombie, new BlockPos(3, 2, 3));
			helper.assertTrue(zombie.getOwner() == owner, "setup: the hungry zombie does not see its owner");
			helper.assertTrue(!JojoModUtil.canHarm(zombie, owner) && !JojoModUtil.canHarm(zombie, stranger)
					&& JojoModUtil.canHarm(zombie, cow),
					"1.16 HungryZombieEntity.canAttack spared its owner and MobEntity.canAttack refused a Creative player on the"
							+ " plain-true base: a hungry zombie may harm its Creative owner " + JojoModUtil.canHarm(zombie, owner)
							+ ", a Creative stranger " + JojoModUtil.canHarm(zombie, stranger) + ", an invulnerable cow "
							+ JojoModUtil.canHarm(zombie, cow));

			Vindicator vindicator = EntityType.VINDICATOR.create(helper.getLevel());
			helper.assertTrue(vindicator != null, "setup: could not create the vindicator");
			add(helper, spawned, vindicator, new BlockPos(5, 2, 3));
			Villager baby = EntityType.VILLAGER.create(helper.getLevel());
			Villager adult = EntityType.VILLAGER.create(helper.getLevel());
			helper.assertTrue(baby != null && adult != null, "setup: could not create the villagers");
			baby.setAge(-24000);
			baby.setInvulnerable(true);
			adult.setInvulnerable(true);
			add(helper, spawned, baby, new BlockPos(5, 2, 5));
			add(helper, spawned, adult, new BlockPos(3, 2, 5));
			helper.assertTrue(!JojoModUtil.canHarm(vindicator, baby) && JojoModUtil.canHarm(vindicator, adult),
					"1.16 AbstractIllager.canAttack spared only a baby villager on the plain-true base: a vindicator may harm an"
							+ " invulnerable baby villager " + JojoModUtil.canHarm(vindicator, baby) + ", an invulnerable adult one "
							+ JojoModUtil.canHarm(vindicator, adult));

			// a class with a canAttack rule of its own besides the known ones keeps its answer
			Zombie ruled = new Zombie(EntityType.ZOMBIE, helper.getLevel()) {
				@Override
				public boolean canAttack(LivingEntity target) {
					return super.canAttack(target);
				}
			};
			Zombie plain = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(plain != null, "setup: could not create the zombie");
			helper.assertTrue(!JojoModUtil.canHarm(ruled, cow) && JojoModUtil.canHarm(plain, cow),
					"canHarm keeps the answer of an attacker class with a canAttack rule of its own and gives a plain one 1.16's"
							+ " base: on an invulnerable cow, the ruled zombie " + JojoModUtil.canHarm(ruled, cow)
							+ ", a plain zombie " + JojoModUtil.canHarm(plain, cow));

			helper.assertTrue(!JojoModUtil.canHarm(plain, stranger) && !JojoModUtil.canHarm(plain, spectator),
					"1.16 MobEntity.canAttack refused a player whose abilities made them invulnerable: a zombie may harm a"
							+ " Creative player " + JojoModUtil.canHarm(plain, stranger) + ", a spectator player "
							+ JojoModUtil.canHarm(plain, spectator));

			// on Peaceful only the 1.21 base refuses a Survival player, so the owner rules alone spare the owner
			server.setDifficulty(Difficulty.PEACEFUL, true);
			try {
				owner.setGameMode(GameType.SURVIVAL);
				helper.assertTrue(!plain.canAttack(survivor) && !wolf.canAttack(survivor) && !zombie.canAttack(survivor),
						"setup: the 1.21 canAttack base should refuse a player on Peaceful");
				boolean plainSurvivor = JojoModUtil.canHarm(plain, survivor);
				boolean wolfOwner = JojoModUtil.canHarm(wolf, owner);
				boolean wolfSurvivor = JojoModUtil.canHarm(wolf, survivor);
				boolean zombieOwner = JojoModUtil.canHarm(zombie, owner);
				boolean zombieSurvivor = JojoModUtil.canHarm(zombie, survivor);
				helper.assertTrue(plainSurvivor && !wolfOwner && wolfSurvivor && !zombieOwner && zombieSurvivor,
						"1.16's mobs harmed a Survival player on Peaceful and spared only their owner: a plain zombie may harm"
								+ " a Survival player " + plainSurvivor + "; a tamed wolf its Survival owner " + wolfOwner
								+ ", a Survival stranger " + wolfSurvivor + "; a hungry zombie its Survival owner " + zombieOwner
								+ ", a Survival stranger " + zombieSurvivor);
			}
			finally {
				server.setDifficulty(difficulty, true);
			}
		}
		finally {
			if (server.getWorldData().getDifficulty() != difficulty) {
				server.setDifficulty(difficulty, true);
			}
			spawned.forEach(Entity::discard);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void aMobsProjectileFliesThroughACreativePlayerAsIn116(GameTestHelper helper) {
		List<Entity> spawned = new ArrayList<>();
		try {
			Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(zombie != null, "setup: could not create the zombie");
			add(helper, spawned, zombie, new BlockPos(1, 2, 1));
			ServerPlayer creative = player(helper, spawned, "CanHitCreative", new BlockPos(1, 2, 3), GameType.CREATIVE);
			ServerPlayer survivor = player(helper, spawned, "CanHitSurvivor", new BlockPos(3, 2, 3), GameType.SURVIVAL);
			HamonBubbleEntity mobs = new HamonBubbleEntity(zombie, helper.getLevel());
			HamonBubbleEntity players = new HamonBubbleEntity(survivor, helper.getLevel());
			helper.assertTrue(mobs.getOwner() == zombie && players.getOwner() == survivor,
					"setup: the bubbles do not see their owners");
			helper.assertTrue(canHitEntity(players, creative),
					"setup: a player's bubble should hit a Creative player (1.16's plain-true LivingEntity.canAttack)");
			boolean hitsCreative = canHitEntity(mobs, creative);
			boolean hitsSurvivor = canHitEntity(mobs, survivor);
			helper.assertTrue(!hitsCreative && hitsSurvivor,
					"1.16 DamagingEntity.canHitEntity asked owner.canAttack, and MobEntity.canAttack refused a Creative player: a"
							+ " zombie's bubble may hit a Creative player " + hitsCreative + ", a Survival player " + hitsSurvivor);
		}
		finally {
			spawned.forEach(Entity::discard);
		}
		helper.succeed();
	}

	private static boolean canHitEntity(DamagingEntity projectile, Entity target) {
		try {
			Method method = DamagingEntity.class.getDeclaredMethod("canHitEntity", Entity.class);
			method.setAccessible(true);
			return (boolean) method.invoke(projectile, target);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not ask the projectile's canHitEntity", e);
		}
	}

	// the user's self-detonation explosion's filter over the target, then one hit of its Sunlight Yellow Overdrive Barrage
	private static void strike(GameTestHelper helper, LivingEntity user, LivingEntity target, boolean harmed,
			boolean immobilizable, String what) {
		target.removeEffect(ModStatusEffects.IMMOBILIZE);
		boolean canHarm = JojoModUtil.canHarm(user, target);
		List<Entity> caught = new ArrayList<>(List.of(target));
		try {
			PillarmanExplosion explosion = new PillarmanExplosion(helper.getLevel(), user, null, user.getX(), user.getY(),
					user.getZ(), 4.0F, false, Explosion.BlockInteraction.KEEP);
			Method filter = PillarmanExplosion.class.getDeclaredMethod("filterEntities", List.class);
			filter.setAccessible(true);
			filter.invoke(explosion, caught);
			Method hit = SYOverdriveBarrageInstance.class.getDeclaredMethod("hitEntity", Level.class, LivingEntity.class,
					LivingEntity.class);
			hit.setAccessible(true);
			hit.invoke(null, helper.getLevel(), user, target);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the self-detonation's filter or the barrage's hit", e);
		}
		boolean kept = caught.contains(target);
		boolean immobilized = target.hasEffect(ModStatusEffects.IMMOBILIZE);
		helper.assertTrue(canHarm == harmed && kept == harmed && immobilized == (harmed && immobilizable), (harmed
				? "1.16's canHarm took " + what + " (LivingEntity.canAttack a plain true): the self-detonation kept it"
						+ (immobilizable ? " and the barrage immobilized it" : "")
				: "canHarm keeps refusing " + what + ": the self-detonation drops it and the barrage leaves it")
				+ "; canHarm " + canHarm + ", kept by the explosion " + kept + ", immobilized " + immobilized);
	}

	private static ServerPlayer player(GameTestHelper helper, List<Entity> spawned, String name, BlockPos relPos, GameType mode) {
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(relPos));
		player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		player.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "setup: could not add the player " + name);
		spawned.add(player);
		player.setGameMode(mode);
		return player;
	}

	private static Cow cow(GameTestHelper helper, List<Entity> spawned, BlockPos relPos) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "setup: could not create the cow");
		add(helper, spawned, cow, relPos);
		return cow;
	}

	private static void add(GameTestHelper helper, List<Entity> spawned, Entity entity, BlockPos relPos) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(relPos));
		entity.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		entity.setNoGravity(true);
		if (entity instanceof net.minecraft.world.entity.Mob mob) {
			mob.setNoAi(true);
		}
		helper.assertTrue(helper.getLevel().addFreshEntity(entity), "setup: could not add " + entity);
		spawned.add(entity);
	}
}
