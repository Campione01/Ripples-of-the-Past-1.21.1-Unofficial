package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.LightBeamEntity;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 LightBeamEntity.rayTrace is JojoModUtil.rayTrace(this, length, e -> e != getOwner()): the ray starts at the
 * beam's own eye height (0.85 of its 0.125 box), takes the nearest pickable entity by its bounding box plus pick
 * radius with no extra inflation, and falls back to the block outline shape.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AjaStoneBeamGeometryGameTests {
	private static final double BEAM_EYE_HEIGHT = 0.125D * 0.85D;

	private AjaStoneBeamGeometryGameTests() {}

	@GameTest(template = "empty", batch = "aja_geometry_size", timeoutTicks = 20)
	public static void beamKeepsItsDonorSize(GameTestHelper helper) {
		EntityType<LightBeamEntity> type = ModEntityTypes.AJA_STONE_BEAM.get();
		helper.assertTrue(type.getWidth() == 0.125F && type.getHeight() == 0.125F,
				"Aja beam is " + type.getWidth() + " x " + type.getHeight() + ", 1.16 registers 0.125 x 0.125");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "aja_geometry_eye_hit", timeoutTicks = 60)
	public static void beamHitsTargetCrossedOnlyByItsEyeRay(GameTestHelper helper) {
		run(helper, Scenario.EYE_RAY_HIT);
	}

	@GameTest(template = "empty", batch = "aja_geometry_above", timeoutTicks = 60)
	public static void beamMissesTargetJustAboveItsEyeRay(GameTestHelper helper) {
		run(helper, Scenario.ABOVE_EYE_RAY);
	}

	@GameTest(template = "empty", batch = "aja_geometry_below", timeoutTicks = 60)
	public static void beamMissesTargetEndingBelowItsEyeRay(GameTestHelper helper) {
		run(helper, Scenario.BELOW_EYE_RAY);
	}

	@GameTest(template = "empty", batch = "aja_geometry_graze", timeoutTicks = 60)
	public static void beamMissesTargetGrazedBySideInflation(GameTestHelper helper) {
		run(helper, Scenario.SIDE_GRAZE);
	}

	@GameTest(template = "empty", batch = "aja_geometry_outline", timeoutTicks = 60)
	public static void beamStopsAtBlockWithOutlineButNoCollision(GameTestHelper helper) {
		run(helper, Scenario.OUTLINE_BLOCK);
	}

	@GameTest(template = "empty", batch = "aja_geometry_dying", timeoutTicks = 60)
	public static void beamIsStoppedByDyingTarget(GameTestHelper helper) {
		run(helper, Scenario.DYING_TARGET);
	}

	private enum Scenario { EYE_RAY_HIT, ABOVE_EYE_RAY, BELOW_EYE_RAY, SIDE_GRAZE, OUTLINE_BLOCK, DYING_TARGET }

	private record Impact(HitResult.Type type, Entity entity, BlockPos block, Direction face) {
		@Override
		public String toString() {
			return type + (entity != null ? " " + entity.getType().toShortString() : "") + (block != null ? " " + block + " " + face : "");
		}
	}

	private static void run(GameTestHelper helper, Scenario scenario) {
		ServerLevel level = helper.getLevel();
		BlockPos template = helper.absolutePos(BlockPos.ZERO);
		ChunkPos chunk = new ChunkPos(template);
		int x = chunk.getMinBlockX();
		int z = chunk.getMinBlockZ();
		int y = template.getY() + 70;
		BlockPos backstop = new BlockPos(x + 8, y + 1, z + 12);
		BlockPos cobweb = new BlockPos(x + 8, y + 1, z + 6);
		Map<BlockPos, BlockState> original = new LinkedHashMap<>();
		List<Impact> impacts = new ArrayList<>();
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Husk target = scenario == Scenario.OUTLINE_BLOCK ? null : EntityType.HUSK.create(level);
		Consumer<ProjectileImpactEvent> listener = event -> {
			if (event.getProjectile() instanceof LightBeamEntity beam && beam.getOwner() == user) {
				HitResult hit = event.getRayTraceResult();
				impacts.add(new Impact(hit.getType(), hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null,
						hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos().immutable() : null,
						hit instanceof BlockHitResult blockHit ? blockHit.getDirection() : null));
			}
		};
		Runnable cleanup = () -> {
			NeoForge.EVENT_BUS.unregister(listener);
			level.getEntitiesOfClass(LightBeamEntity.class, user.getBoundingBox().inflate(4.0D)).forEach(Entity::discard);
			if (target != null) {
				target.discard();
			}
			user.getInventory().clearContent();
			user.discard();
			original.forEach(level::setBlockAndUpdate);
		};
		try {
			helper.assertTrue(y + 4 < level.getMaxBuildHeight(), "Aja geometry fixture is above the build height");
			for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x + 7, y, z + 2), new BlockPos(x + 9, y + 3, z + 12))) {
				helper.assertTrue(level.isEmptyBlock(pos), "Aja geometry fixture is obstructed at " + pos);
				original.put(pos.immutable(), level.getBlockState(pos));
			}
			level.setBlockAndUpdate(backstop, Blocks.STONE.defaultBlockState());
			// keeps the fire the beam lights in front of what it hits
			level.setBlockAndUpdate(backstop.north().below(), Blocks.STONE.defaultBlockState());
			if (scenario == Scenario.OUTLINE_BLOCK) {
				level.setBlockAndUpdate(cobweb, Blocks.COBWEB.defaultBlockState());
				level.setBlockAndUpdate(cobweb.north().below(), Blocks.STONE.defaultBlockState());
			}

			user.setNoGravity(true);
			// the beam leaves 0.3 below the eyes: half way up the block row of the backstop
			user.moveTo(x + 8.5D, y + 1.5D + 0.3D - user.getEyeHeight(), z + 2.5D, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.yBodyRot = 0.0F;
			helper.assertTrue(level.addFreshEntity(user) && level.isPositionEntityTicking(user.blockPosition()),
					"Could not add the ticking Aja user");
			double beamY = user.getEyeY() - 0.3D;
			if (target != null) {
				double height = target.getBbHeight();
				double feet = switch (scenario) {
					case EYE_RAY_HIT -> beamY + 0.05D;
					case ABOVE_EYE_RAY -> beamY + BEAM_EYE_HEIGHT + 0.04D;
					case BELOW_EYE_RAY -> beamY + 0.05D - height;
					default -> beamY - 1.0D;
				};
				// a 0.6 wide target 0.5 to the side leaves 0.2 between its box and the ray
				double side = scenario == Scenario.SIDE_GRAZE ? 0.5D : 0.0D;
				target.setNoAi(true);
				target.setNoGravity(true);
				target.setPersistenceRequired();
				target.moveTo(x + 8.5D + side, feet, z + 6.5D, 0.0F, 0.0F);
				helper.assertTrue(level.addFreshEntity(target), "Could not add the Aja target");
			}
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			helper.assertTrue(hamon.learnSkill(ModHamonSkills.AJA_STONE_KEEPER.get()), "Could not grant Aja Stone Keeper");
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AJA_STONE.get()));
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, listener);
		}
		catch (RuntimeException | Error error) {
			cleanup.run();
			throw error;
		}

		helper.runAfterDelay(3, () -> {
			try {
				if (scenario == Scenario.DYING_TARGET) {
					target.setHealth(0.0F);
					helper.assertTrue(!target.isAlive() && !target.isRemoved(), "The Aja target is not dying");
				}
				// the Hamon-charged use shoots at once
				helper.assertTrue(user.getMainHandItem().use(level, user, InteractionHand.MAIN_HAND).getResult().consumesAction()
						&& !user.isUsingItem(), "The charged Aja stone did not shoot");
			}
			catch (RuntimeException | Error error) {
				cleanup.run();
				throw error;
			}
		});
		helper.runAfterDelay(7, () -> {
			try {
				JojoMod.LOGGER.info("AJA-BEAM-GEOMETRY {} impacts={}", scenario, impacts);
				helper.assertTrue(impacts.size() == 1, scenario + ": the beam made " + impacts.size() + " impacts " + impacts);
				Impact impact = impacts.get(0);
				switch (scenario) {
					case EYE_RAY_HIT -> helper.assertTrue(impact.entity == target,
							"A target crossed by the beam's eye ray was missed: " + impact);
					case DYING_TARGET -> helper.assertTrue(impact.entity == target,
							"A dying target did not stop the beam: " + impact);
					case OUTLINE_BLOCK -> helper.assertTrue(cobweb.equals(impact.block) && impact.face == Direction.NORTH
							&& level.getBlockState(cobweb.north()).is(Blocks.FIRE),
							"The beam did not stop at the cobweb outline and light its near side: " + impact
							+ " fire=" + level.getBlockState(cobweb.north()));
					default -> helper.assertTrue(backstop.equals(impact.block) && impact.face == Direction.NORTH,
							scenario + ": the beam should pass the target and reach the wall behind it: " + impact);
				}
			}
			finally {
				cleanup.run();
			}
			helper.succeed();
		});
	}
}
