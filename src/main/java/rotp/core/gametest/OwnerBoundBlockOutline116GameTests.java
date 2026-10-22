package rotp.core.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.DamagingEntity;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.impl.powers.pillarman.PillarmanHornEntity;
import rotp.core.impl.powers.pillarman.PillarmanRibEntity;
import rotp.core.impl.powers.pillarman.PillarmanVeinEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 OwnerBoundProjectileEntity.rayTrace, which Space Ripper Stingy Eyes and the Pillar Man vein, rib and horn took:
 * JojoModUtil.rayTraceMultipleEntities, a block only when no entity was on the ray, met by its outline (BlockMode.OUTLINE),
 * so a torch, grass or a flower stopped the ray; DamagingEntity.onHitBlock then broke it (hardness 0, within each one's
 * getMaxHardnessBreakable), and afterBlockHit did not retract a projectile that broke its block. Each scene puts the
 * projectile's ray along a block's centre line, a wall torch on it and stone behind. Own batches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnerBoundBlockOutline116GameTests {
	private static final String BATCH = "owner_bound_block_outline_";

	private OwnerBoundBlockOutline116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "eyes")
	public static void stingyEyesStopAtATorchAndBreakItAsIn116(GameTestHelper helper) {
		stopsAtATorchAndBreaksIt(helper, "Space Ripper Stingy Eyes",
				owner -> new SpaceRipperStingyEyesEntity(helper.getLevel(), owner, true));
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "vein")
	public static void pillarManVeinStopsAtATorchAndBreaksItAsIn116(GameTestHelper helper) {
		stopsAtATorchAndBreaksIt(helper, "the Pillar Man vein",
				owner -> new PillarmanVeinEntity(helper.getLevel(), owner, 0.0F, 0.0F, 0.0D, 0.0D, 0.0D));
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "rib")
	public static void pillarManRibStopsAtATorchAndBreaksItAsIn116(GameTestHelper helper) {
		stopsAtATorchAndBreaksIt(helper, "the Pillar Man rib", owner -> new PillarmanRibEntity(owner, helper.getLevel()));
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "horn")
	public static void pillarManHornStopsAtATorchAndBreaksItAsIn116(GameTestHelper helper) {
		stopsAtATorchAndBreaksIt(helper, "the Pillar Man horn", owner -> new PillarmanHornEntity(owner, helper.getLevel()));
	}

	private static void stopsAtATorchAndBreaksIt(GameTestHelper helper, String name,
			Function<Player, DamagingEntity> make) {
		ServerLevel level = helper.getLevel();
		Player owner = GameTestPlayers.makeServerMockPlayer(helper, GameType.CREATIVE);
		owner.getAbilities().instabuild = true;
		owner.setNoGravity(true);
		Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		face(owner, start);
		helper.assertTrue(level.addFreshEntity(owner), "Could not add the owner");
		BlockPos torch = null;
		BlockPos stone = null;
		BlockState torchWas = null;
		BlockState stoneWas = null;
		try {
			DamagingEntity projectile = make.apply(owner);
			// the ray starts where the owner's body puts it: move the owner so that it starts at a block's centre
			Vec3 origin = origin(projectile);
			face(owner, start.add(Vec3.atCenterOf(BlockPos.containing(origin)).subtract(origin)));
			origin = origin(projectile);
			helper.assertTrue(origin.distanceTo(Vec3.atCenterOf(BlockPos.containing(origin))) < 1.0E-4D,
					"Fixture: the ray of " + name + " does not start at a block's centre: " + origin);
			torch = BlockPos.containing(origin).south(2);
			stone = torch.south(2);
			torchWas = level.getBlockState(torch);
			stoneWas = level.getBlockState(stone);
			level.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH),
					Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			projectile.setPos(origin.add(0.0D, 0.0D, 5.5D));
			projectile.setDeltaMovement(Vec3.ZERO);

			HitResult[] hits = (HitResult[]) invoke(helper, projectile, "rayTrace");
			BlockPos met = null;
			for (HitResult hit : hits) {
				if (hit.getType() == HitResult.Type.BLOCK) {
					met = ((BlockHitResult) hit).getBlockPos();
					break;
				}
			}
			helper.assertTrue(torch.equals(met), "1.16's ray of " + name + " met a torch by its outline; the port's met "
					+ (met != null ? "block " + met : "no block") + ", the torch at " + torch + ", stone at " + stone);

			invoke(helper, projectile, "checkHit");
			helper.assertTrue(level.getBlockState(torch).isAir() && level.getBlockState(stone).is(Blocks.STONE),
					"1.16's " + name + " broke the torch it met (hardness 0) and left the stone behind it; now the torch's"
							+ " cell holds " + level.getBlockState(torch) + ", the stone's " + level.getBlockState(stone));
			helper.assertTrue(!projectile.saveWithoutId(new CompoundTag()).getBoolean("IsRetracting"), "1.16's " + name
					+ " did not retract after breaking the torch (afterBlockHit); the port's retracts");
		}
		finally {
			if (stone != null) {
				level.setBlock(torch, torchWas, Block.UPDATE_CLIENTS);
				level.setBlock(stone, stoneWas, Block.UPDATE_ALL);
			}
			owner.discard();
		}
		helper.succeed();
	}

	// where the projectile's ray starts (1.16 getOriginPoint)
	private static Vec3 origin(DamagingEntity projectile) {
		return projectile instanceof OwnerBoundProjectileEntity bound ? bound.getOriginPoint(1.0F)
				: ((SpaceRipperStingyEyesEntity) projectile).getOriginPoint(1.0F);
	}

	// the owner at the given feet position, looking south, its head and body too
	private static void face(Player owner, Vec3 feet) {
		owner.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
		owner.setYHeadRot(0.0F);
		owner.setYBodyRot(0.0F);
		owner.yBodyRotO = 0.0F;
		owner.yHeadRotO = 0.0F;
	}

	private static Object invoke(GameTestHelper helper, DamagingEntity projectile, String name) {
		try {
			Method method = DamagingEntity.class.getDeclaredMethod(name);
			method.setAccessible(true);
			return method.invoke(projectile);
		}
		catch (NoSuchMethodException | IllegalAccessException e) {
			helper.fail("Fixture: DamagingEntity has no " + name);
			throw new IllegalStateException(e);
		}
		catch (InvocationTargetException e) {
			throw new IllegalStateException("Fixture: " + name + " failed", e.getCause());
		}
	}
}
