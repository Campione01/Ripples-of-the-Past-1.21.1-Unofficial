package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The user's own client runs an ability's conditions for its HUD (AvailableAbilities) and for its own check of a press
 * (InputHandler), where the client's copy of the user holds no aim: the client sends its aim to the server
 * (ClAimTargetPacket) and only a Stand's aim comes back (TrAimTargetPacket). 1.16's client checks read the mouse target
 * (ActionsOverlayGui.actionAvailability, PowerBaseImpl.checkRequirements), so an action that needs a target showed
 * available at one and its press went off on the client too. A scene's user stands in for that client's copy: its
 * isLocalPlayer() is true (LocalPlayer's), it holds no aim, and the aim its client sends is in
 * LivingComponentAction.CLIENT_PLAYER_AIM, which ClientsideAim.playerAim is (ClientsideAim itself is client-only and does
 * not load on the GameTest server, so that link is read from its class file); the same user with the aim stored and
 * isLocalPlayer() false is the server's. The client's aim is put back after each scene. Own batches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnClientAim116GameTests {
	private static final String BATCH = "own_client_aim_116_";

	private OwnClientAim116GameTests() {}

	/*
	 * LivingComponentAction.getAimTarget: the server's copy of a user reads the aim its client sent, the user's own client
	 * the aim it sends, resolved in the user's level; a mob that never aimed has none.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "helper")
	public static void theUsersOwnClientReadsTheAimItSends(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, ModPlayerPowers.HAMON.get())) {
			Mob cow = scene.mob(EntityType.COW, 2.0D);
			BlockPos stone = scene.block(scene.feet.south(3), Blocks.STONE.defaultBlockState());
			ActionTarget sent = new ActionTarget(stone, Direction.NORTH);
			LivingComponentAction.getComponent(scene.user).entityAim.setTarget(sent);
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(new ActionTarget(cow));
			ActionTarget server = LivingComponentAction.getAimTarget(scene.user);
			helper.assertTrue(server.getType() == TargetType.BLOCK && stone.equals(server.getBlockPos()), "The server's copy of"
					+ " the user reads the aim its client sent (" + stone + "), not " + describe(server));
			scene.user.local = true;
			LivingComponentAction.getComponent(scene.user).entityAim.setTarget(ActionTarget.EMPTY);
			ActionTarget client = LivingComponentAction.getAimTarget(scene.user);
			helper.assertTrue(client.getType() == TargetType.ENTITY && client.getEntity() == cow, "The user's own client reads"
					+ " the aim it sends (the cow), which its copy of the user never gets back; it read " + describe(client));
			helper.assertTrue(LivingComponentAction.getAimTarget(cow).getType() == TargetType.EMPTY, "A cow that never aimed"
					+ " has an aim: " + describe(LivingComponentAction.getAimTarget(cow)));
		}
		String file = "rotp/core/client/input/ClientsideAim.class";
		try (InputStream in = OwnClientAim116GameTests.class.getClassLoader().getResourceAsStream(file)) {
			helper.assertTrue(in != null, "Missing class file " + file);
			String pool = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
			helper.assertTrue(pool.contains("CLIENT_PLAYER_AIM"), "ClientsideAim.playerAim, the aim the client updates and"
					+ " sends, is not LivingComponentAction.CLIENT_PLAYER_AIM, which the user's own client reads");
		}
		catch (IOException e) {
			helper.fail("Cannot read " + file + ": " + e);
		}
		helper.succeed();
	}

	/*
	 * Hamon actions whose check needs the aimed target (1.16: Plant Infusion TargetRequirement.BLOCK, Organism Infusion
	 * ANY, Hypnosis, Shock and Overdrive an entity, Life Magnetism leaves aimed at unless held, Wall Climbing a wall's
	 * side): on the user's own client each takes the aim the client sends, as the server does. Sendo Overdrive asked for
	 * no target (TargetRequirement.NONE; HamonSendoOverdriveTarget116GameTests).
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "hamon")
	public static void hamonChecksOnTheUsersOwnClientSeeTheAimItSendsAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, ModPlayerPowers.HAMON.get())) {
			HamonData hamon = PlayerPower.getPowerData(scene.user, ModPlayerPowers.HAMON).orElseThrow();
			for (var skill : List.of(ModHamonSkills.THROWABLES_INFUSION, ModHamonSkills.PLANT_BLOCK_INFUSION,
					ModHamonSkills.ANIMAL_INFUSION, ModHamonSkills.DETECTOR, ModHamonSkills.HYPNOSIS, ModHamonSkills.HAMON_SHOCK,
					ModHamonSkills.WALL_CLIMBING, ModHamonSkills.LIFE_MAGNETISM, ModHamonSkills.OVERDRIVE)) {
				hamon.learnSkill(skill.get());
			}
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			Mob wolf = scene.mob(EntityType.WOLF, 2.0D);
			BlockPos grass = scene.feet.south(2).east(2);
			scene.block(grass.below(), Blocks.GRASS_BLOCK.defaultBlockState());
			scene.block(grass, Blocks.SHORT_GRASS.defaultBlockState());
			BlockPos leaves = scene.block(scene.feet.west(2), Blocks.OAK_LEAVES.defaultBlockState());
			BlockPos wall = scene.block(scene.feet.north(), Blocks.STONE.defaultBlockState());
			scene.block(wall.above(), Blocks.STONE.defaultBlockState());

			scene.assertTakesTheAim("plant_infusion", new ActionTarget(grass, Direction.UP));
			scene.assertTakesTheAim("organism_infusion", new ActionTarget(wolf));
			scene.assertTakesTheAim("hypnosis", new ActionTarget(wolf));
			scene.assertTakesTheAim("hamon_shock", new ActionTarget(wolf));
			scene.assertTakesTheAim("hamon_overdrive", new ActionTarget(wolf));
			scene.assertTakesTheAim("life_magnetism", new ActionTarget(leaves, Direction.EAST));
			scene.assertTakesTheAim("wall_climbing", new ActionTarget(wall, Direction.SOUTH));
		}
		helper.succeed();
	}

	// Pillar Man Absorption and Hide in Entity (1.16 TargetRequirement.ENTITY, 8 blocks)
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "pillarman")
	public static void pillarManChecksOnTheUsersOwnClientSeeTheAimItSendsAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, ModPlayerPowers.PILLAR_MAN.get())) {
			PillarmanData data = PlayerPower.getPowerData(scene.user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			data.setEvolutionStage(2, scene.user);
			data.setMode(PillarmanMode.NONE, scene.user);
			data.setEnergy(scene.user, 100.0F);
			Mob cow = scene.mob(EntityType.COW, 1.5D);
			scene.assertTakesTheAim("pillarman_absorption", new ActionTarget(cow));
			scene.assertTakesTheAim("pillarman_hide_in_entity", new ActionTarget(cow));
		}
		helper.succeed();
	}

	// Vampirism Blood Drain and Blood Gift (1.16 TargetRequirement.ENTITY; the gift a weak player without a power)
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "vampirism")
	public static void vampirismChecksOnTheUsersOwnClientSeeTheAimItSendsAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, ModPlayerPowers.VAMPIRISM.get())) {
			VampirismData data = PlayerPower.getPowerData(scene.user, ModPlayerPowers.VAMPIRISM).orElseThrow();
			data.setVampireFullPower(true, scene.user);
			VampirismState.get(scene.user).blood().setCurrent(500.0F);
			data.setBloodLevel(VampirismState.get(scene.user).blood().current());
			Mob cow = scene.mob(EntityType.COW, 1.5D);
			Player weak = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			weak.setNoGravity(true);
			weak.moveTo(scene.origin.x + 1.0D, scene.origin.y, scene.origin.z + 1.5D, 180.0F, 0.0F);
			helper.assertTrue(scene.level.addFreshEntity(weak), "Could not add the weak player");
			scene.entities.add(weak);
			PowerClass.PLAYER_POWER.attachGet(weak);
			weak.setHealth(6.0F);
			scene.assertTakesTheAim("vampirism_blood_drain", new ActionTarget(cow));
			scene.assertTakesTheAim("vampirism_blood_gift", new ActionTarget(weak));
		}
		helper.succeed();
	}

	// the zombie's Devour (1.16 TargetRequirement.ENTITY)
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "zombie")
	public static void zombieDevourOnTheUsersOwnClientSeesTheAimItSendsAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, ModPlayerPowers.ZOMBIE.get())) {
			Mob cow = scene.mob(EntityType.COW, 1.5D);
			scene.assertTakesTheAim("zombie_devour", new ActionTarget(cow));
		}
		helper.succeed();
	}

	private static String describe(ActionTarget target) {
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
		if (check.getWarning() == null) {
			return "refused without a message";
		}
		return "refused (" + (check.getWarning().getContents() instanceof TranslatableContents contents ? contents.getKey()
				: check.getWarning().getString()) + ")";
	}

	// a player that is its own client's player (LocalPlayer.isLocalPlayer) while local is set
	private static final class ClientUser extends Player {
		boolean local;

		ClientUser(ServerLevel level) {
			super(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "own-client-aim-user"));
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
		final BlockPos feet;
		final Vec3 origin;
		final List<Entity> entities = new ArrayList<>();
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		// a Survival user with the power, floating, looking south, empty-handed
		Scene(GameTestHelper helper, PlayerPowerType<?> type) {
			this.helper = helper;
			level = helper.getLevel();
			feet = helper.absolutePos(new BlockPos(2, 3, 2));
			origin = Vec3.atBottomCenterOf(feet);
			user = new ClientUser(level);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the user");
			entities.add(user);
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(type);
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
		}

		Mob mob(EntityType<? extends Mob> type, double ahead) {
			Mob mob = type.create(level);
			helper.assertTrue(mob != null, "Could not create " + type);
			mob.setNoAi(true);
			mob.setNoGravity(true);
			mob.moveTo(origin.x, origin.y, origin.z + ahead, 180.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(mob), "Could not add " + mob);
			entities.add(mob);
			return mob;
		}

		BlockPos block(BlockPos pos, BlockState state) {
			blocks.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			return pos;
		}

		/*
		 * The ability's check (Ability.checkConditions, what the HUD and the client's own press check ask): on the server
		 * with the aim its client sent, usable (premise), and refused without one (premise: the check needs it); on the
		 * user's own client, which holds no aim of its own, with the aim it sends, usable as on the server.
		 */
		void assertTakesTheAim(String name, ActionTarget aim) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability != null, "Fixture: " + power.getPowerType() + " has no " + name);
			LivingComponentAction component = LivingComponentAction.getComponent(user);
			user.local = false;
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
			component.entityAim.setTarget(aim);
			ConditionCheck server = ability.checkConditions(power);
			helper.assertTrue(server.isPositive(), "Fixture: on the server " + name + " at " + describe(aim) + " is "
					+ why(server));
			component.entityAim.setTarget(ActionTarget.EMPTY);
			ConditionCheck none = ability.checkConditions(power);
			helper.assertTrue(!none.isPositive(), "Fixture: " + name + " is usable on the server with no aim, so its check"
					+ " needs none");
			user.local = true;
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(aim);
			ConditionCheck client = ability.checkConditions(power);
			user.local = false;
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
			helper.assertTrue(client.isPositive(), "1.16's client checked " + name + " at its mouse target, " + describe(aim)
					+ ", and showed it usable as the server took it; the port's own client finds it " + why(client)
					+ ", reading no aim of its copy of the user");
		}

		@Override
		public void close() {
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
