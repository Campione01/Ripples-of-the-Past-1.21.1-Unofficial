package rotp.core.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonSendoOverdriveAbility;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonSendoOverdrive took its block from the mouse target, a Hamon user's vanilla pick (TargetRequirement.BLOCK,
 * PowerBaseImpl.checkTarget): an entity under the crosshair, or a block beyond the pick's reach (a miss), refused the
 * press without a message; only a pressed block without a collision shape gave way to a collision ray from the eyes as
 * long as the block range, 10 blocks (overrideVanillaMouseTarget, getMaxRangeSqBlockTarget), so grass on the ground
 * gave the ground under it. The scenes set the aim the client sends, its vanilla pick, and check the press as the server
 * and the HUD do (Ability.checkConditions). Own batches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonSendoOverdriveTarget116GameTests {
	private static final String BATCH = "hamon_sendo_target_116_";

	private HamonSendoOverdriveTarget116GameTests() {}

	/*
	 * A cow under the crosshair with a stone wall three blocks behind it, in the 10-block range: 1.16 refused the press
	 * (an entity is no block target), on the server and in the user's own HUD; the stone wall itself is taken.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "entity")
	public static void sendoOverdriveRefusesAnEntityUnderTheCrosshairAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos wall = scene.wallAhead(3);
			Mob cow = scene.mob(EntityType.COW, 1.5D);
			ActionTarget atCow = new ActionTarget(cow);
			scene.assertRefused(atCow, false, "a cow under the crosshair with a stone wall behind it");
			scene.assertRefused(atCow, true, "a cow under the crosshair with a stone wall behind it, in the user's own HUD");
			cow.discard();
			ActionTarget atWall = scene.vanillaPick();
			helper.assertTrue(atWall.getType() == TargetType.BLOCK && wall.equals(atWall.getBlockPos()),
					"Fixture: the vanilla pick is not the wall: " + scene.describe(atWall));
			scene.assertTakes(atWall, wall, "the stone wall under the crosshair");
		}
		helper.succeed();
	}

	/*
	 * A stone wall seven blocks ahead, past the vanilla pick's reach (4.5 blocks in Survival) and within the 10-block
	 * range: the client's pick is a miss, which 1.16 refused; the same wall within the reach is taken.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "reach")
	public static void sendoOverdriveRefusesABlockBeyondThePicksReachAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			scene.wallAhead(7);
			helper.assertTrue(scene.vanillaPick().getType() == TargetType.EMPTY, "Fixture: the vanilla pick meets the wall"
					+ " seven blocks ahead: " + scene.describe(scene.vanillaPick()));
			scene.assertRefused(ActionTarget.EMPTY, false, "a stone wall seven blocks ahead, past the pick's reach");
			BlockPos near = scene.wallAhead(4);
			ActionTarget atNear = scene.vanillaPick();
			helper.assertTrue(atNear.getType() == TargetType.BLOCK && near.equals(atNear.getBlockPos()),
					"Fixture: the vanilla pick is not the wall four blocks ahead: " + scene.describe(atNear));
			scene.assertTakes(atNear, near, "a stone wall four blocks ahead, in the pick's reach");
		}
		helper.succeed();
	}

	/*
	 * Tall grass under the crosshair gives way to the collision ray: to a stone wall behind it within 10 blocks, beyond
	 * the pick's reach too; with nothing there the press is refused.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "grass")
	public static void sendoOverdrivePressedAtGrassTakesTheBlockTheCollisionRayMeetsAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos grass = scene.tallGrassAhead();
			ActionTarget atGrass = scene.vanillaPick();
			helper.assertTrue(atGrass.getType() == TargetType.BLOCK && grass.equals(atGrass.getBlockPos()),
					"Fixture: the vanilla pick is not the tall grass: " + scene.describe(atGrass));
			scene.assertRefused(atGrass, false, "tall grass with nothing behind it in 10 blocks");
			BlockPos wall = scene.wallAhead(7);
			scene.assertTakes(atGrass, wall, "tall grass with a stone wall seven blocks ahead behind it");
		}
		helper.succeed();
	}

	// a player that is its own client's player (LocalPlayer.isLocalPlayer) while local is set
	private static final class ClientUser extends Player {
		boolean local;

		ClientUser(ServerLevel level) {
			super(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "sendo-target-user"));
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean isCreative() {
			return false;
		}

		@Override
		public boolean isLocalPlayer() {
			return local;
		}
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final ServerLevel level;
		final ClientUser user;
		final PlayerPower power;
		final Ability sendo;
		final Vec3 origin;
		final List<Entity> entities = new ArrayList<>();
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		// a Survival Hamon user who learned Sendo Overdrive, full of energy and breath, empty-handed, floating, looking south
		Scene(GameTestHelper helper) {
			this.helper = helper;
			level = helper.getLevel();
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 1)));
			user = new ClientUser(level);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the user");
			entities.add(user);
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.SENDO_OVERDRIVE.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			sendo = power.getAbility("sendo_overdrive");
			helper.assertTrue(sendo instanceof HamonSendoOverdriveAbility, "Fixture: Hamon has no sendo_overdrive");
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
		}

		// a stone wall two blocks high whose near face is distance - 0.5 blocks ahead of the eyes, on the crosshair line
		BlockPos wallAhead(int distance) {
			BlockPos wall = BlockPos.containing(user.getEyePosition()).south(distance);
			block(wall, Blocks.STONE.defaultBlockState());
			block(wall.below(), Blocks.STONE.defaultBlockState());
			return wall;
		}

		// tall grass, its upper half on the crosshair line a block ahead
		BlockPos tallGrassAhead() {
			BlockPos upper = BlockPos.containing(user.getEyePosition()).south();
			block(upper.below(), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
			block(upper, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
			helper.assertTrue(level.getBlockState(upper).getCollisionShape(level, upper).isEmpty(),
					"Fixture: the tall grass has a collision shape");
			return upper;
		}

		// a mob with its box centred on the crosshair line, z blocks ahead of the eyes
		Mob mob(EntityType<? extends Mob> type, double ahead) {
			Mob mob = type.create(level);
			helper.assertTrue(mob != null, "Could not create " + type);
			mob.setNoAi(true);
			mob.setNoGravity(true);
			mob.moveTo(origin.x, user.getEyeY() - mob.getBbHeight() / 2.0D, origin.z + ahead, 180.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(mob), "Could not add " + mob);
			entities.add(mob);
			return mob;
		}

		// the block a client's vanilla pick meets on the crosshair (Entity.pick: outlines, the player's block reach)
		ActionTarget vanillaPick() {
			HitResult pick = user.pick(user.blockInteractionRange(), 1.0F, false);
			return ActionTarget.fromVanilla(pick);
		}

		void assertRefused(ActionTarget aim, boolean ownClient, String scene) {
			ConditionCheck check = check(aim, ownClient);
			helper.assertTrue(!check.isPositive(), "1.16 refused Sendo Overdrive pressed at " + scene + " (its mouse target"
					+ " gave no block); the port's is usable at " + describe(target(aim, ownClient)));
		}

		void assertTakes(ActionTarget aim, BlockPos expected, String scene) {
			ConditionCheck check = check(aim, false);
			ActionTarget target = target(aim, false);
			helper.assertTrue(check.isPositive() && target.getType() == TargetType.BLOCK && expected.equals(target.getBlockPos()),
					"1.16's Sendo Overdrive pressed at " + scene + " took the block at " + expected + "; the port's is "
							+ why(check) + " at " + describe(target));
		}

		private ConditionCheck check(ActionTarget aim, boolean ownClient) {
			setAim(aim, ownClient);
			ConditionCheck check = sendo.checkConditions(power);
			setAim(ActionTarget.EMPTY, false);
			return check;
		}

		private ActionTarget target(ActionTarget aim, boolean ownClient) {
			setAim(aim, ownClient);
			try {
				Method method = HamonSendoOverdriveAbility.class.getDeclaredMethod("getSendoBlockTarget", LivingEntity.class,
						Level.class);
				method.setAccessible(true);
				return (ActionTarget) method.invoke(null, user, level);
			}
			catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				throw new IllegalStateException("Fixture: getSendoBlockTarget failed", e);
			}
			finally {
				setAim(ActionTarget.EMPTY, false);
			}
		}

		// the aim as the server stores it, or as the user's own client sends it
		private void setAim(ActionTarget aim, boolean ownClient) {
			user.local = ownClient;
			LivingComponentAction.getComponent(user).entityAim.setTarget(ownClient ? ActionTarget.EMPTY : aim);
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ownClient ? aim : ActionTarget.EMPTY);
		}

		String describe(ActionTarget target) {
			return switch (target.getType()) {
				case ENTITY -> String.valueOf(target.getEntity());
				case BLOCK -> "block " + target.getBlockPos();
				default -> "nothing";
			};
		}

		private static String why(ConditionCheck check) {
			if (check.isPositive()) {
				return "usable";
			}
			return check.getWarning() == null ? "refused without a message" : "refused ("
					+ (check.getWarning().getContents() instanceof TranslatableContents contents ? contents.getKey()
							: check.getWarning().getString()) + ")";
		}

		void block(BlockPos pos, BlockState state) {
			blocks.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}

		@Override
		public void close() {
			user.local = false;
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			entities.forEach(Entity::discard);
			List<BlockPos> placed = new ArrayList<>(blocks.keySet());
			for (int i = placed.size() - 1; i >= 0; i--) {
				level.setBlock(placed.get(i), blocks.get(placed.get(i)), Block.UPDATE_CLIENTS);
			}
		}
	}
}
