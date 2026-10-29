package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.magiciansred.MagiciansRedKickAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 MagiciansRedKick.standTickWindup: two ticks before the kick the Stand slid toward its task target's eyes (a block's
 * centre), by their distance from its own eyes less its width, at most 3 blocks, or 3 blocks along its look with no
 * target, and stopped a tick later; the task turned it to its target every tick (StandEntityTask.rotateStand).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagiciansRedKickSlide116GameTests {
	// Each scene stands this far west of its test cell, outside the grid, which grows east and south from its first
	// cell: in the grid the slide ended within a block of the next row's cell, where that test's end removed the Stand
	// before its kick ended. Each test has a lane of its own there, wider than the grid, so that no two scenes meet.
	private static final int AWAY_BLOCKS = 256;
	private static final int LANE_BLOCKS = 64;
	// StructureGridSpawner puts the next row 6 blocks past a row of one-block cells
	private static final int NEXT_ROW_BLOCKS = 7;
	// along the look from the user: the Stand winds up 2 blocks ahead and slides at most 3 more
	private static final double SLIDE_REACH = 5.0D;

	private MagiciansRedKickSlide116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 120)
	public static void theKickSlidesToItsTargetsEyesLessTheStandsWidthAsIn116(GameTestHelper helper) {
		Scene s = new Scene(helper, 0);
		Zombie zombie = s.zombie(2.5);
		s.user.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
		s.user.setYHeadRot(s.user.getYRot());
		ActionTarget aim = new ActionTarget(zombie);
		LivingComponentAction.getComponent(s.user).entityAim.setTargetFromClient(aim, helper.getLevel());
		LivingComponentAction.getComponent(s.stand).entityAim.setTargetFromClient(aim, helper.getLevel());
		double[] closest = { Double.MAX_VALUE };
		double[] farthestAhead = { -Double.MAX_VALUE };
		double[] widestYaw = { 0 };
		s.run(tick -> {
			Vec3 eyes = s.stand.getEyePosition();
			closest[0] = Math.min(closest[0], eyes.distanceTo(zombie.getEyePosition()));
			farthestAhead[0] = Math.max(farthestAhead[0], eyes.z - zombie.getEyePosition().z);
			if (s.kick.getPhase() == ActionPhase.RECOVERY) {
				widestYaw[0] = Math.max(widestYaw[0], Math.abs(Mth.wrapDegrees(s.stand.getYRot() - s.user.getYRot())));
			}
		}, () -> {
			double width = s.stand.getBbWidth();
			helper.assertTrue(farthestAhead[0] < 0 && closest[0] >= width - 0.05 && closest[0] <= width + 0.15,
					"1.16 slid Magician's Red to its kick's target, a zombie 2.5 blocks ahead, its eyes stopping its width ("
							+ width + ") short of the zombie's eyes: its eyes came within " + closest[0] + " of them and "
							+ farthestAhead[0] + " past them");
			helper.assertTrue(widestYaw[0] <= 10, "1.16's Magician's Red kept facing its kick's target through the recovery,"
					+ " within 10 degrees of its user's yaw: it turned " + widestYaw[0] + " degrees off it");
		});
	}

	@GameTest(template = "empty", timeoutTicks = 120)
	public static void theKickAtNothingSlidesThreeBlocksAlongTheLookAsIn116(GameTestHelper helper) {
		kickAtNothing(helper, new Scene(helper, 1), tick -> {}, () -> {});
	}

	// The user 4.5 blocks short of its chunk's south edge: the Stand winds up in that chunk and slides into the next,
	// which ticks entities only as the scene holds it.
	@GameTest(template = "empty", timeoutTicks = 120)
	public static void theKickAtNothingSlidesThreeBlocksAcrossAChunkBorderAsIn116(GameTestHelper helper) {
		BlockPos cell = cell(helper, 2);
		ChunkPos chunk = new ChunkPos(cell);
		Scene s = new Scene(helper,
				new Vec3(chunk.getMiddleBlockX() + 0.5D, cell.getY() + 2, chunk.getMaxBlockZ() + 1 - 4.5D));
		kickAtNothing(helper, s, tick -> {}, () -> helper.assertTrue(
				!s.stand.chunkPosition().equals(s.user.chunkPosition()),
				"setup: the slide ended in its user's chunk " + s.user.chunkPosition() + ", at " + s.stand.position()));
	}

	// The next row's test stands 7 blocks south of this one. As a test ends, GameTestInfo.succeed removes every entity
	// but players within a block of its one-block structure; a test that starts later than this one (the chunks around
	// it were ready later) can end while the slide's end lies there. The stand-in does that to the scene's entities on
	// every tick.
	@GameTest(template = "empty", timeoutTicks = 120)
	public static void theKickAtNothingSlidesThreeBlocksBesideTheNextRowsTestAsIn116(GameTestHelper helper) {
		Scene s = new Scene(helper, 3);
		AABB nextRowsEnd = helper.getBounds().move(0, 0, NEXT_ROW_BLOCKS).inflate(1.0D);
		kickAtNothing(helper, s, tick -> helper.getLevel()
				.getEntitiesOfClass(Entity.class, nextRowsEnd, entity -> !(entity instanceof Player) && s.owns(entity))
				.forEach(entity -> entity.remove(Entity.RemovalReason.DISCARDED)), () -> {});
	}

	private static void kickAtNothing(GameTestHelper helper, Scene s, Watch beside, Runnable premise) {
		Vec3[] last = { null };
		double[] longestStep = { 0 };
		Vec3[] stepDirection = { Vec3.ZERO };
		s.run(tick -> {
			beside.tick(tick);
			Vec3 pos = s.stand.position();
			if (last[0] != null && s.kick.getPhase() != ActionPhase.RECOVERY) {
				Vec3 step = pos.subtract(last[0]);
				if (step.length() > longestStep[0]) {
					longestStep[0] = step.length();
					stepDirection[0] = step.normalize();
				}
			}
			last[0] = pos;
		}, () -> {
			premise.run();
			Vec3 look = s.stand.getLookAngle();
			helper.assertTrue(Math.abs(longestStep[0] - 3.0) <= 0.05 && stepDirection[0].dot(new Vec3(0, 0, 1)) > 0.99,
					"1.16 slid Magician's Red 3 blocks along its look (south) in its kick at nothing: its longest step in one"
							+ " tick was " + longestStep[0] + " toward " + stepDirection[0] + " (look " + look + ")");
		});
	}

	private interface Watch {
		void tick(int tick);
	}

	// where a lane's scene stands as if its test cell were there
	private static BlockPos cell(GameTestHelper helper, int lane) {
		return helper.absolutePos(BlockPos.ZERO).west(away(lane));
	}

	private static int away(int lane) {
		return AWAY_BLOCKS + LANE_BLOCKS * lane;
	}

	private static final class Scene {
		private final GameTestHelper helper;
		private final GameTestChunkTickets tickets;
		private final Player user;
		private final StandType type;
		private final StandPower power;
		private final StandEntity stand;
		private final Vec3 origin;
		private final List<Zombie> zombies = new ArrayList<>();
		@Nullable private EntityActionInstance kick;
		private int ticks;
		private boolean done;

		private Scene(GameTestHelper helper, int lane) {
			this(helper, Vec3.atBottomCenterOf(cell(helper, lane).offset(1, 2, 1)));
		}

		private Scene(GameTestHelper helper, Vec3 origin) {
			this.helper = helper;
			this.origin = origin;
			tickets = new GameTestChunkTickets(helper);
			// before anyone joins, so that each is added where it ticks
			hold(origin.add(-1.0D, 0.0D, -1.0D), origin.add(1.0D, 0.0D, SLIDE_REACH + 1.0D));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setYHeadRot(0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: the Stand user was not added");
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: Magician's Red was not granted");
			power.setResolveLevel(4);
			helper.assertTrue(type.summon(user, power), "setup: Magician's Red was not summoned");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "setup: the summoned Stand is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setNoGravity(true);
			power.setStamina(power.getMaxStamina());
		}

		private Zombie zombie(double ahead) {
			Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(zombie != null, "setup: no zombie");
			zombie.setNoAi(true);
			// no player stands within 128 blocks of a scene, past which a monster despawns
			zombie.setPersistenceRequired();
			zombie.setNoGravity(true);
			zombie.noPhysics = true;
			zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			zombie.setHealth(500);
			zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			zombie.moveTo(origin.x, origin.y, origin.z + ahead, 180, 0);
			hold(zombie.position(), zombie.position());
			helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: the zombie was not added");
			zombies.add(zombie);
			return zombie;
		}

		private void hold(Vec3 corner, Vec3 otherCorner) {
			tickets.hold(corner, otherCorner);
		}

		private boolean owns(Entity entity) {
			return entity == user || entity == stand || zombies.contains(entity);
		}

		// the kick starts on the Stand on the 3rd tick as its press sets it; the watch sees every tick until it ends
		private void run(Watch watch, Runnable check) {
			helper.onEachTick(() -> {
				if (done) return;
				try {
					ticks++;
					if (ticks == 3) {
						EntityActionType ability = (EntityActionType) power.getAbility("kick");
						helper.assertTrue(ability instanceof MagiciansRedKickAbility, "setup: Magician's Red has no kick");
						kick = ability.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
						kick.setStartedByClick(true);
						LivingComponentAction.getComponent(stand).setAction(kick, user, SyncType.NO_SYNC);
						helper.assertTrue(stand.getCurStandAction() == kick, "setup: the kick did not start on the Stand");
						return;
					}
					if (kick == null) return;
					if (stand.getCurStandAction() != kick) {
						done = true;
						check.run();
						close();
						helper.succeed();
						return;
					}
					watch.tick(ticks);
					if (ticks >= 100) {
						helper.fail("setup: the kick did not end in 97 ticks: the Stand is at " + stand.position() + " in chunk "
								+ stand.chunkPosition() + (stand.isRemoved() ? ", removed (" + stand.getRemovalReason() + ")" : "")
								+ ", which ticks entities: " + helper.getLevel().isPositionEntityTicking(stand.blockPosition()));
					}
				}
				catch (RuntimeException | Error e) {
					done = true;
					close();
					throw e;
				}
			});
		}

		private void close() {
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			zombies.forEach(Zombie::discard);
			user.discard();
		}
	}
}
