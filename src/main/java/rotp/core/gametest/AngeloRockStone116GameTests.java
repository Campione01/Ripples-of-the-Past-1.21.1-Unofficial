package rotp.core.gametest;

import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.crazydiamond.AngeloRockEntity;
import rotp.core.impl.stands.crazydiamond.CrazyDAngeloRockPunchEffect;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreTerrainAbility;
import rotp.core.impl.stands.crazydiamond.brokenblocks.BrokenBlocksChunkData;
import rotp.core.init.ModBlockTags;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 CDTurnIntoAngeloRockEffect.tryStartAngeloRock: the rock was made of broken Material.STONE blocks (canUseBlock), at
 * least two of the blocks its target's knockback explosion broke; it took a vertical pair of them whose lower block was
 * within distSqr 3 of the target, else the nearest one with air or an unbroken stone above or below it, that stone broken
 * for the rock (FindBlockEntry, MCUtil.destroyBlock with dropBrokenBlock(user)).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AngeloRockStone116GameTests {
	private static final BlockPos TARGET = new BlockPos(2, 2, 2);
	private static final BlockPos PAIR_LOWER = new BlockPos(3, 2, 3);
	private static final BlockPos PAIR_UPPER = new BlockPos(3, 3, 3);
	private static final BlockPos NEAR_WEST = new BlockPos(1, 2, 2);
	private static final BlockPos NEAR_NORTH = new BlockPos(2, 2, 1);
	private static final BlockPos FAR_NORTH = new BlockPos(2, 2, 0);
	private static final BlockPos ABOVE_NEAR_WEST = new BlockPos(1, 3, 2);

	private AngeloRockStone116GameTests() {}

	// every block the tag lists is a block of the game in the tag, and 1.16's other materials stay out of it
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theAngeloRockStoneTagHoldsEveryBlockItListsAndNoOther116Material(GameTestHelper helper) {
		ResourceLocation file = JojoMod.resLoc("tags/block/angelo_rock_stone.json");
		List<String> wrong = new ArrayList<>();
		int listed = 0;
		try (Reader reader = helper.getLevel().getServer().getResourceManager().getResourceOrThrow(file).openAsReader()) {
			for (JsonElement value : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")) {
				String id = value.getAsString();
				if (id.startsWith("#")) {
					continue;
				}
				listed++;
				ResourceLocation key = ResourceLocation.parse(id);
				Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(key);
				if (block.isEmpty() || !block.get().defaultBlockState().is(ModBlockTags.ANGELO_ROCK_STONE)) {
					wrong.add(id + (block.isEmpty() ? " (no such block)" : " (not in the loaded tag)"));
				}
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not read " + file, e);
		}
		for (Block stone : new Block[] { Blocks.STONE, Blocks.DEEPSLATE, Blocks.NETHERRACK, Blocks.BLACKSTONE }) {
			if (!stone.defaultBlockState().is(ModBlockTags.ANGELO_ROCK_STONE)) {
				wrong.add(BuiltInRegistries.BLOCK.getKey(stone) + " (base stone left out)");
			}
		}
		for (Block other : new Block[] { Blocks.OAK_PLANKS, Blocks.DIRT, Blocks.GRAVEL, Blocks.INFESTED_STONE, Blocks.IRON_BLOCK,
				Blocks.GLASS, Blocks.WHITE_CONCRETE_POWDER, Blocks.BEDROCK, Blocks.END_PORTAL_FRAME, Blocks.ANVIL }) {
			if (other.defaultBlockState().is(ModBlockTags.ANGELO_ROCK_STONE)) {
				wrong.add(BuiltInRegistries.BLOCK.getKey(other) + " (in the tag, not Material.STONE or unbreakable)");
			}
		}
		helper.assertTrue(listed > 200 && wrong.isEmpty(), "The Angelo Rock's stone tag (" + listed + " blocks listed): " + wrong);
		helper.succeed();
	}

	// a vertical pair of broken blocks of each family 1.16 counted as stone makes the rock; broken planks do not
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aRockOfCobblestoneWallsAndOther116StoneFormsAsIn116(GameTestHelper helper) {
		Block[] stones = { Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS,
				Blocks.DEEPSLATE_BRICKS, Blocks.IRON_ORE, Blocks.SANDSTONE, Blocks.BRICKS, Blocks.END_STONE, Blocks.TERRACOTTA,
				Blocks.WHITE_CONCRETE, Blocks.STONE };
		List<String> wrong = new ArrayList<>();
		for (Block stone : stones) {
			try (Scene s = new Scene(helper)) {
				s.broken(PAIR_LOWER, stone.defaultBlockState());
				s.broken(PAIR_UPPER, stone.defaultBlockState());
				ConditionCheck result = s.tryStart();
				AngeloRockEntity rock = s.rock();
				if (!result.isPositive() || rock == null) {
					wrong.add(BuiltInRegistries.BLOCK.getKey(stone) + ": " + (result.isPositive() ? "no rock"
							: "refused with " + result.getWarning()));
				}
			}
		}
		try (Scene s = new Scene(helper)) {
			s.broken(PAIR_LOWER, Blocks.OAK_PLANKS.defaultBlockState());
			s.broken(PAIR_UPPER, Blocks.OAK_PLANKS.defaultBlockState());
			ConditionCheck result = s.tryStart();
			helper.assertTrue(!result.isPositive() && s.rock() == null, "1.16 made no Angelo Rock of broken planks"
					+ " (Material.WOOD): " + (result.isPositive() ? "positive" : result.getWarning()) + ", rock " + s.rock());
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 made the Angelo Rock of every broken Material.STONE block: " + wrong);
		helper.succeed();
	}

	// two broken stones nearer the target than a broken vertical pair within distSqr 3: the rock takes the pair
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theRockTakesABrokenVerticalPairBeforeNearerStonesAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
			s.broken(NEAR_WEST, stone);
			s.broken(NEAR_NORTH, stone);
			s.broken(PAIR_LOWER, stone);
			s.broken(PAIR_UPPER, stone);
			ConditionCheck result = s.tryStart();
			AngeloRockEntity rock = s.rock();
			helper.assertTrue(result.isPositive() && rock != null, "setup: no rock formed: "
					+ (result.isPositive() ? "no rock entity" : result.getWarning()));
			Vec3 expected = Vec3.atBottomCenterOf(s.abs(PAIR_LOWER));
			helper.assertTrue(rock.position().distanceTo(expected) < 1.0E-6, "1.16 put the rock on the broken vertical pair"
					+ " (priority 1) at " + expected + ", not on the nearer single stones: it is at " + rock.position());
			helper.assertTrue(!s.recorded(PAIR_LOWER) && !s.recorded(PAIR_UPPER) && s.recorded(NEAR_WEST) && s.recorded(NEAR_NORTH),
					"1.16 took the pair's two blocks for the rock and left the nearer stones to be restored: remembered pair "
							+ s.recorded(PAIR_LOWER) + "/" + s.recorded(PAIR_UPPER) + ", singles " + s.recorded(NEAR_WEST) + "/"
							+ s.recorded(NEAR_NORTH));
		}
		helper.succeed();
	}

	// no vertical pair: the nearest broken stone under an unbroken stone, which is broken for the rock's upper block
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theRockBreaksTheStoneAboveTheNearestBrokenStoneAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
			s.broken(NEAR_WEST, stone);
			s.broken(FAR_NORTH, stone);
			s.place(ABOVE_NEAR_WEST, Blocks.STONE.defaultBlockState());
			ConditionCheck result = s.tryStart();
			AngeloRockEntity rock = s.rock();
			helper.assertTrue(result.isPositive() && rock != null, "setup: no rock formed: "
					+ (result.isPositive() ? "no rock entity" : result.getWarning()));
			Vec3 expected = Vec3.atBottomCenterOf(s.abs(NEAR_WEST));
			helper.assertTrue(rock.position().distanceTo(expected) < 1.0E-6, "1.16 put the rock on the nearest broken stone at "
					+ expected + ": it is at " + rock.position());
			helper.assertTrue(s.level().getBlockState(s.abs(ABOVE_NEAR_WEST)).isAir() && s.recorded(FAR_NORTH),
					"1.16 broke the stone above the nearest broken stone for the rock's upper block and left the farther broken"
							+ " stone to be restored: above it " + s.level().getBlockState(s.abs(ABOVE_NEAR_WEST))
							+ ", the farther stone remembered " + s.recorded(FAR_NORTH));
		}
		helper.succeed();
	}

	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final FakePlayer user;
		private final StandType type;
		private final Zombie zombie;
		private final List<BlockPos> brokenPos = new ArrayList<>();
		private final List<BlockPos> touched = new ArrayList<>();
		private final CrazyDAngeloRockPunchEffect effect;

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "AngeloRockStone"));
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
			zombie = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(type != null && zombie != null, "setup: no Crazy Diamond or no zombie");
			Vec3 userPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 2, 4)));
			user.moveTo(userPos.x, userPos.y, userPos.z, 180.0F, 0.0F);
			user.setNoGravity(true);
			user.getAbilities().instabuild = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: the Stand user was not added");
			StandPower power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: Crazy Diamond was not granted");
			Vec3 targetPos = Vec3.atBottomCenterOf(abs(TARGET));
			zombie.setNoAi(true);
			zombie.setNoGravity(true);
			zombie.moveTo(targetPos.x, targetPos.y, targetPos.z, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: the zombie was not added");
			zombie.setHealth(8.0F);
			effect = (CrazyDAngeloRockPunchEffect) ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get().create(helper.getLevel());
			effect.withEntity(user);
			effect.withTarget(zombie);
		}

		private BlockPos abs(BlockPos relative) {
			return helper.absolutePos(relative);
		}

		private ServerLevel level() {
			return helper.getLevel();
		}

		private void place(BlockPos relative, BlockState state) {
			touched.add(abs(relative));
			level().setBlockAndUpdate(abs(relative), state);
		}

		// broken as the knockback explosion breaks a block: remembered with its drops, then gone
		private void broken(BlockPos relative, BlockState state) {
			BlockPos pos = abs(relative);
			place(relative, state);
			CrazyDRestoreTerrainAbility.rememberBrokenBlock(level(), pos, state, Optional.empty(), Block.getDrops(state, level(), pos, null));
			level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			brokenPos.add(pos);
		}

		private boolean recorded(BlockPos relative) {
			BrokenBlocksChunkData data = BrokenBlocksChunkData.getExistingData(level(), abs(relative));
			return data != null && data.getBrokenBlockAt(abs(relative)) != null;
		}

		// the effect's one try once the impact is over (CrazyDAngeloRockPunchEffect.tick)
		private ConditionCheck tryStart() {
			KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(zombie);
			helper.assertTrue(!impact.isActive(), "setup: the zombie's knockback impact is active");
			impact.blocksDestroyedByLastExplosion = new ArrayList<>(brokenPos);
			try {
				Method tryStart = CrazyDAngeloRockPunchEffect.class.getDeclaredMethod("tryStartAngeloRock",
						net.minecraft.world.entity.LivingEntity.class, KnockbackCollisionImpact.class);
				tryStart.setAccessible(true);
				return (ConditionCheck) tryStart.invoke(effect, zombie, impact);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("CrazyDAngeloRockPunchEffect.tryStartAngeloRock is not reachable", e);
			}
		}

		private AngeloRockEntity rock() {
			List<AngeloRockEntity> rocks = level().getEntitiesOfClass(AngeloRockEntity.class, area());
			return rocks.isEmpty() ? null : rocks.get(0);
		}

		private AABB area() {
			return new AABB(abs(new BlockPos(-1, 0, -1))).expandTowards(6, 6, 6);
		}

		@Override
		public void close() {
			level().getEntitiesOfClass(AngeloRockEntity.class, area()).forEach(AngeloRockEntity::discard);
			for (BlockPos pos : touched) {
				level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				BrokenBlocksChunkData data = BrokenBlocksChunkData.getExistingData(level(), pos);
				if (data != null) {
					data.removeBrokenBlock(pos);
				}
			}
			level().getEntitiesOfClass(ItemEntity.class, area()).forEach(ItemEntity::discard);
			zombie.discard();
			user.discard();
		}
	}
}
