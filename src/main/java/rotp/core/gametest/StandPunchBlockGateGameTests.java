package rotp.core.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModGamerules;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.ServerBlockDestroyTracker;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Stand light punch and barrage obey jojoAbilitiesBreakBlocks and block-protection events, as 1.16 StandEntity.breakBlock did. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandPunchBlockGateGameTests {
	private static final int MAX_HITS = 1000;
	private static final BlockPos CENTRE = new BlockPos(2, 3, 3);

	private StandPunchBlockGateGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void punchRespectsBreakBlocksGamerule(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "punch_rule")) {
			BlockPos centre = f.cluster(CENTRE);
			f.setBreakBlocks(false);
			for (int i = 0; i < MAX_HITS; i++) {
				f.punch(centre);
			}
			helper.assertTrue(f.level().getBlockState(centre).is(Blocks.GLASS),
					"Light punch broke a block with jojoAbilitiesBreakBlocks off");
			helper.assertTrue(ServerBlockDestroyTracker.getBlockDestroyProgress(f.level(), centre) == 0,
					"Light punch cracked the target with jojoAbilitiesBreakBlocks off");
			helper.assertTrue(f.crackedNeighbours(centre) == 0,
					"Light punch cracked neighbours with jojoAbilitiesBreakBlocks off");

			// control: the same punch breaks it once the gamerule is on
			f.setBreakBlocks(true);
			f.hitUntilBroken(centre, "control light punch", () -> f.punch(centre));
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void punchSkipsProtectedNeighbours(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "punch_claim")) {
			f.setBreakBlocks(true);
			BlockPos centre = f.cluster(CENTRE);
			// a claim mod denying everything but the punched block
			Consumer<LivingDestroyBlockEvent> claim = event -> {
				if (event.getEntity() == f.stand && !event.getPos().equals(centre)) {
					event.setCanceled(true);
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingDestroyBlockEvent.class, claim);
			try {
				f.hitUntilBroken(centre, "claimed-cluster light punch", () -> f.punch(centre));
				helper.assertTrue(f.crackedNeighbours(centre) == 0,
						"Light punch cracked blocks a LivingDestroyBlockEvent listener denied");
			}
			finally {
				NeoForge.EVENT_BUS.unregister(claim);
			}

			// control: without the claim the same punch cracks the neighbours
			f.place(centre, Blocks.GLASS.defaultBlockState());
			f.hitUntilBroken(centre, "control light punch", () -> f.punch(centre));
			helper.assertTrue(f.crackedNeighbours(centre) > 0,
					"Control light punch left no cracks, the neighbour loop was not reached");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void barrageRespectsBreakBlocksGamerule(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "barrage_rule")) {
			EntityActionInstance action = f.initAction("barrage");
			helper.assertTrue(action instanceof StandEntityBarrageAbility.StandEntityBarrage,
					"Star Platinum barrage resolved the wrong action: " + action);
			Method hitBlock = barrageHitBlock();
			BlockPos centre = f.cluster(CENTRE);
			f.setBreakBlocks(false);
			for (int i = 0; i < MAX_HITS; i++) {
				invoke(hitBlock, action, f.target(centre), f.level(), f.stand);
			}
			helper.assertTrue(f.level().getBlockState(centre).is(Blocks.GLASS),
					"Barrage broke a block with jojoAbilitiesBreakBlocks off");
			helper.assertTrue(ServerBlockDestroyTracker.getBlockDestroyProgress(f.level(), centre) == 0,
					"Barrage cracked a block with jojoAbilitiesBreakBlocks off");

			// control: the same barrage breaks it once the gamerule is on
			f.setBreakBlocks(true);
			f.hitUntilBroken(centre, "control barrage",
					() -> invoke(hitBlock, action, f.target(centre), f.level(), f.stand));
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void punchPlaysNoBlockSoundOnProtectedBlock(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "punch_sound")) {
			BlockPos centre = f.cluster(CENTRE);
			BlockHitSounds sounds = new BlockHitSounds(helper, centre);
			try {
				// 1.16 breakBlock returned before any block sound on a gated block
				f.setBreakBlocks(false);
				for (int i = 0; i < 20; i++) {
					f.punch(centre);
				}
				helper.assertTrue(sounds.count == 0, "Light punch played " + sounds.count
						+ " block hit sounds with jojoAbilitiesBreakBlocks off");
				f.setBreakBlocks(true);
				Consumer<LivingDestroyBlockEvent> claim = event -> {
					if (event.getEntity() == f.stand) event.setCanceled(true);
				};
				NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingDestroyBlockEvent.class, claim);
				try {
					for (int i = 0; i < 20; i++) {
						f.punch(centre);
					}
				}
				finally {
					NeoForge.EVENT_BUS.unregister(claim);
				}
				helper.assertTrue(sounds.count == 0, "Light punch played " + sounds.count
						+ " block hit sounds on a claim-protected block");

				// control: an allowed but unbreakable block gives the 1.16 hit sound
				f.place(centre, Blocks.BEDROCK.defaultBlockState());
				f.punch(centre);
				helper.assertTrue(sounds.count > 0, "Light punch on bedrock played no block hit sound");
				f.place(centre, Blocks.GLASS.defaultBlockState());
			}
			finally {
				sounds.close();
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void barragePlaysNoBlockSoundOnProtectedBlock(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "barrage_sound")) {
			EntityActionInstance action = f.initAction("barrage");
			helper.assertTrue(action instanceof StandEntityBarrageAbility.StandEntityBarrage,
					"Star Platinum barrage resolved the wrong action: " + action);
			Method hitBlock = barrageHitBlock();
			BlockPos centre = f.cluster(CENTRE);
			BlockHitSounds sounds = new BlockHitSounds(helper, centre);
			try {
				f.setBreakBlocks(false);
				for (int i = 0; i < 20; i++) {
					invoke(hitBlock, action, f.target(centre), f.level(), f.stand);
				}
				helper.assertTrue(sounds.count == 0, "Barrage played " + sounds.count
						+ " block hit sounds with jojoAbilitiesBreakBlocks off");

				// control: an allowed block keeps the barrage hit sound (also proves the phase tick is even)
				f.setBreakBlocks(true);
				invoke(hitBlock, action, f.target(centre), f.level(), f.stand);
				helper.assertTrue(sounds.count > 0, "Control barrage played no block hit sound");
			}
			finally {
				sounds.close();
			}
		}
		helper.succeed();
	}

	/** Counts block-category sounds played at the centre of one block. */
	private static final class BlockHitSounds {
		int count;
		final Consumer<PlayLevelSoundEvent.AtPosition> listener;

		BlockHitSounds(GameTestHelper helper, BlockPos pos) {
			Vec3 centre = Vec3.atCenterOf(pos);
			listener = event -> {
				if (event.getLevel() == helper.getLevel() && event.getSound() != null
						&& event.getSource() == SoundSource.BLOCKS
						&& event.getPosition().distanceToSqr(centre) < 0.01) {
					count++;
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayLevelSoundEvent.AtPosition.class, listener);
		}

		void close() {
			NeoForge.EVENT_BUS.unregister(listener);
		}
	}

	private static Method barrageHitBlock() {
		try {
			Method method = StandEntityBarrageAbility.StandEntityBarrage.class.getDeclaredMethod(
					"hitBlock", ActionTarget.class, Level.class, StandEntity.class);
			method.setAccessible(true);
			return method;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Barrage hitBlock is missing", e);
		}
	}

	private static void invoke(Method method, Object instance, Object... args) {
		try {
			method.invoke(instance, args);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Barrage hitBlock failed", e);
		}
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final StandType standType;
		private final StandPower power;
		private final StandEntity stand;
		private final boolean breakBlocks;
		private final List<BlockPos> placed = new ArrayList<>();

		private Fixture(GameTestHelper helper, String name) {
			this.helper = helper;
			String profileName = "StandBlockGate_" + name;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
					UUID.nameUUIDFromBytes(profileName.getBytes(StandardCharsets.US_ASCII)), profileName));
			Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 2, 0)));
			user.moveTo(origin.x, origin.y, origin.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add block-gate player");
			breakBlocks = helper.getLevel().getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
			power = PowerClass.STAND.attachGet(user);
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing Star Platinum entity");
			stand.moveTo(origin.x, origin.y, origin.z);
		}

		private void setBreakBlocks(boolean value) {
			level().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(value, level().getServer());
		}

		/** 3x3x3 stone around a glass centre; returns the absolute centre. */
		private BlockPos cluster(BlockPos relCentre) {
			BlockPos centre = helper.absolutePos(relCentre);
			for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
				place(pos.immutable(), Blocks.STONE.defaultBlockState());
			}
			place(centre, Blocks.GLASS.defaultBlockState());
			return centre;
		}

		private void place(BlockPos pos, BlockState state) {
			level().setBlock(pos, state, Block.UPDATE_CLIENTS);
			if (!placed.contains(pos)) placed.add(pos);
		}

		private ActionTarget target(BlockPos pos) {
			return new ActionTarget(pos, Direction.UP);
		}

		private void punch(BlockPos pos) {
			StandEntityPunchAbility.StandEntityPunch.hitBlockTarget(target(pos), level(), stand, false);
		}

		private void hitUntilBroken(BlockPos pos, String what, Runnable hit) {
			for (int i = 0; i < MAX_HITS && !level().getBlockState(pos).isAir(); i++) {
				hit.run();
			}
			helper.assertTrue(level().getBlockState(pos).isAir(), what + " did not break " + level().getBlockState(pos));
		}

		private int crackedNeighbours(BlockPos centre) {
			int cracked = 0;
			for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
				if (!pos.equals(centre) && ServerBlockDestroyTracker.getBlockDestroyProgress(level(), pos) > 0) {
					cracked++;
				}
			}
			return cracked;
		}

		private EntityActionInstance initAction(String abilityName) {
			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "Missing production action " + abilityName);
			power.setStamina(power.getMaxStamina());
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level(), user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			return action;
		}

		private ServerLevel level() {
			return helper.getLevel();
		}

		@Override
		public void close() {
			for (BlockPos pos : placed) {
				BlockState state = level().getBlockState(pos);
				if (!state.isAir()) {
					// drop leftover cracks before clearing
					ServerBlockDestroyTracker.addBlockDestroyProgress(level(), null, pos, state, -1.0F);
				}
				level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			level().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(breakBlocks, level().getServer());
			if (power.isSummoned()) standType.forceUnsummon(user, power);
			user.discard();
		}
	}
}
