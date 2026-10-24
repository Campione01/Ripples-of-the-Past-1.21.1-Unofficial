package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonSendoOverdriveAbility;
import rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonSendoOverdrive kept Action's TargetRequirement.NONE (neither it, HamonAction nor NonStandAction overrides
 * getTargetRequirement), so PowerBaseImpl.checkTarget let a press at a mob, at the air or at a plant through: the hold
 * started, with Jonathan's line, wherever its user looked, and overrideVanillaMouseTarget only rewrote a check target
 * nothing read. stoppedHolding sent the wave only when the mouse target at the release, the raw pick the client went on
 * sending, was a block (a plant itself), and only then spent the energy of a release before the full charge; a release at
 * the full charge that passed its checks still performed wherever it was aimed (stopHeldAction(true)): the hand swung and
 * NonStandAction.onPerform spent the energy. The press kept its other checks: a free main hand and the energy. Each swing
 * showed on the user's own screen too, whose client ran stopHeldAction and stoppedHolding itself. The scenes set the aim
 * the client sends, its vanilla pick, check the press as the server and the HUD do (Ability.checkConditions) and press
 * through the server's input path; the user's connection counts the swings sent to its own client. Own batches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonSendoOverdriveTarget116GameTests {
	private static final String BATCH = "hamon_sendo_target_116_";
	private static final short KEY = 116;

	private HamonSendoOverdriveTarget116GameTests() {}

	/*
	 * A cow under the crosshair with a stone wall three blocks behind it: 1.16 accepted the press, on the server and in the
	 * user's own HUD (a stick in the main hand, or no energy or breath left, still refused it); the hold started with
	 * Jonathan's line and went on at the cow, and let go of there before its full charge it sent no wave and spent
	 * nothing. Held to its full charge and let go of at the cow: no wave either, but the hand swung and the energy was
	 * spent; with a stick taken into the main hand just before that release, its check failed and nothing happened.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "entity")
	public static void sendoOverdrivePressedAtAMobHoldsAndSendsNoWaveAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			scene.wallAhead(3);
			ActionTarget atCow = new ActionTarget(scene.mob(EntityType.COW, 1.5D));
			String where = "a cow under the crosshair with a stone wall behind it";
			scene.assertUsable(atCow, false, where);
			scene.assertUsable(atCow, true, where + ", in the user's own HUD");
			scene.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			scene.assertRefusedWith(atCow, "hand", where + ", a stick in the main hand");
			scene.user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			scene.hamon.setEnergy(0.0F);
			scene.hamon.setBreathStability(0.0F);
			scene.assertRefusedWith(atCow, "no_energy_hamon", where + ", no energy or breath left");
			scene.refill();
			EntityActionInstance held = scene.press(atCow, where);
			scene.tick(10);
			scene.assertHeldWithItsLine(held, where);
			scene.assertReleasedWithoutWave(held, false, where + ", let go of before the full charge");
		}
		try (Scene scene = new Scene(helper)) {
			scene.wallAhead(3);
			ActionTarget atCow = new ActionTarget(scene.mob(EntityType.COW, 1.5D));
			String where = "a cow under the crosshair with a stone wall behind it";
			EntityActionInstance held = scene.press(atCow, where);
			scene.tick(31);
			scene.assertHeldWithItsLine(held, where + " for its full charge");
			scene.assertReleasedWithoutWave(held, true, where + ", let go of at its full charge");
		}
		try (Scene scene = new Scene(helper)) {
			ActionTarget atCow = new ActionTarget(scene.mob(EntityType.COW, 1.5D));
			String where = "a cow under the crosshair";
			EntityActionInstance held = scene.press(atCow, where);
			scene.tick(31);
			scene.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			scene.assertReleasedWithoutWave(held, false, where + ", let go of at its full charge with a stick just taken"
					+ " into the main hand (stopHeldAction(true)'s check failed: no perform)");
		}
		helper.succeed();
	}

	/*
	 * A stone wall seven blocks ahead, past the vanilla pick's reach (4.5 blocks in Survival): the client's pick is a miss,
	 * which 1.16 accepted; held to the full charge and let go of at the air, no wave, the hand swung and the energy was
	 * spent. Pressed at the air and turned to a wall in the pick's reach before the release: the wave goes from that wall
	 * as stoppedHolding put it.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "air")
	public static void sendoOverdrivePressedAtTheAirHoldsAndSendsItsWaveOnlyAtABlockAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			scene.wallAhead(7);
			helper.assertTrue(scene.vanillaPick().getType() == TargetType.EMPTY, "Fixture: the vanilla pick meets the wall"
					+ " seven blocks ahead: " + scene.describe(scene.vanillaPick()));
			String where = "the air, a stone wall seven blocks ahead past the pick's reach";
			scene.assertUsable(ActionTarget.EMPTY, false, where);
			EntityActionInstance held = scene.press(ActionTarget.EMPTY, where);
			scene.tick(31);
			scene.assertHeldWithItsLine(held, where);
			scene.assertReleasedWithoutWave(held, true, where + ", let go of at its full charge");
		}
		try (Scene scene = new Scene(helper)) {
			scene.wallAhead(7);
			String where = "the air, then turned to a stone wall four blocks ahead";
			EntityActionInstance held = scene.press(ActionTarget.EMPTY, where);
			scene.tick(10);
			BlockPos near = scene.wallAhead(4);
			ActionTarget atNear = scene.vanillaPick();
			helper.assertTrue(atNear.getType() == TargetType.BLOCK && near.equals(atNear.getBlockPos())
					&& atNear.getFace() == Direction.NORTH, "Fixture: the vanilla pick is not the north face of the wall four"
							+ " blocks ahead: " + scene.describe(atNear) + " " + atNear.getFace());
			scene.setAim(atNear, false);
			scene.assertHeldWithItsLine(held, where);
			scene.assertWaveFrom(scene.release(held), atNear, where + ", let go of before the full charge");
		}
		helper.succeed();
	}

	/*
	 * Sendo Overdrive no key holds (a buffered click, an action set by code): it fires by itself at its full charge, and
	 * with no block under the crosshair that is the same perform as a key let go of at the full charge: 900 energy spent
	 * and the main hand swung, once on the user's own screen, with no wave; a stick taken into the main hand before the
	 * full charge ends the hold, and nothing is spent and nothing swings.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "unheld")
	public static void sendoOverdriveNoKeyHoldsFiresAtItsFullChargeWithoutABlockAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			scene.assertUnheldFullCharge(ActionTarget.EMPTY, false, "the air");
		}
		try (Scene scene = new Scene(helper)) {
			scene.wallAhead(3);
			scene.assertUnheldFullCharge(new ActionTarget(scene.mob(EntityType.COW, 1.5D)), false,
					"a cow under the crosshair with a stone wall behind it");
		}
		try (Scene scene = new Scene(helper)) {
			scene.assertUnheldFullCharge(ActionTarget.EMPTY, true, "the air, with a stick taken into the main hand");
		}
		helper.succeed();
	}

	/*
	 * Tall grass under the crosshair with nothing behind it in 10 blocks: overrideVanillaMouseTarget's collision ray met
	 * nothing, which TargetRequirement.NONE accepted; the hold started and, let go of at the grass, sent its wave from the
	 * grass itself.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "grass")
	public static void sendoOverdrivePressedAtGrassWithNothingBehindItHoldsAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos grass = scene.tallGrassAhead();
			ActionTarget atGrass = scene.vanillaPick();
			helper.assertTrue(atGrass.getType() == TargetType.BLOCK && grass.equals(atGrass.getBlockPos()),
					"Fixture: the vanilla pick is not the tall grass: " + scene.describe(atGrass));
			String where = "tall grass with nothing behind it in 10 blocks";
			scene.assertUsable(atGrass, false, where);
			EntityActionInstance held = scene.press(atGrass, where);
			scene.tick(10);
			scene.assertHeldWithItsLine(held, where);
			scene.assertWaveFrom(scene.release(held), atGrass, where + ", let go of before the full charge");
		}
		helper.succeed();
	}

	/*
	 * Held at tall grass with a stone wall seven blocks behind it, then released: 1.16's press and held checks took any
	 * target, and stoppedHolding sent the wave from the mouse target as it was, the grass itself: centred on its block, on
	 * the axis of the face the crosshair met; also when the hold ended on a failed check (an item taken into the main
	 * hand: the wave still went). At short grass on a stone floor, looked down at: the wave stands on the grass block, a
	 * ring on the grass's top face, not in the floor under it.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "release")
	public static void sendoOverdriveReleasedAtGrassSendsItsWaveFromTheGrassAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos grass = scene.tallGrassAhead();
			scene.wallAhead(7);
			ActionTarget atGrass = scene.vanillaPick();
			helper.assertTrue(atGrass.getType() == TargetType.BLOCK && grass.equals(atGrass.getBlockPos())
					&& atGrass.getFace() == Direction.NORTH, "Fixture: the vanilla pick is not the tall grass's north face: "
							+ scene.describe(atGrass) + " " + atGrass.getFace());
			scene.assertUsable(atGrass, false, "tall grass with a stone wall seven blocks ahead behind it");
			scene.assertWaveAt(atGrass, false, "released at tall grass with a stone wall seven blocks behind it");
			scene.assertWaveAt(atGrass, true, "held at tall grass with a stone wall seven blocks behind it until an item was"
					+ " taken into the main hand");
		}
		try (Scene scene = new Scene(helper)) {
			BlockPos grass = BlockPos.containing(scene.user.getEyePosition()).south().below(2);
			scene.block(grass.below(), Blocks.STONE.defaultBlockState());
			scene.block(grass, Blocks.SHORT_GRASS.defaultBlockState());
			scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atBottomCenterOf(grass).add(0.0D, 0.75D, 0.0D));
			ActionTarget atGrass = scene.vanillaPick();
			helper.assertTrue(atGrass.getType() == TargetType.BLOCK && grass.equals(atGrass.getBlockPos())
					&& atGrass.getFace() == Direction.UP, "Fixture: the vanilla pick is not the short grass's top face: "
							+ scene.describe(atGrass) + " " + atGrass.getFace());
			scene.assertUsable(atGrass, false, "short grass on a stone floor");
			scene.assertWaveAt(atGrass, false, "released at short grass on a stone floor");
		}
		helper.succeed();
	}

	/*
	 * A player that is its own client's player (LocalPlayer.isLocalPlayer) while local is set; its connection counts the
	 * main-hand swings the server sends it of itself, which its own client draws (ClientPacketListener.handleAnimate).
	 */
	private static final class ClientUser extends FakePlayer {
		boolean local;
		int ownMainHandSwings;

		ClientUser(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "sendo-target-user"));
			this.connection = new SwingConnection(level, this);
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

	private static final class SwingConnection extends ServerGamePacketListenerImpl {
		@Nullable private final ClientUser user;

		SwingConnection(ServerLevel level, ClientUser user) {
			super(level.getServer(), openConnection(), user, CommonListenerCookie.createInitial(user.getGameProfile(), false));
			this.user = user;
		}

		// as GameTestHelper.makeMockServerPlayerInLevel: a live channel, so channel lookups do not fail
		private static Connection openConnection() {
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			return connection;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
			if (user == null) return;
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(sub -> send(sub, null));
			}
			else if (packet instanceof ClientboundAnimatePacket swing && swing.getId() == user.getId()
					&& swing.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND) {
				user.ownMainHandSwings++;
			}
		}
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final ServerLevel level;
		final ClientUser user;
		final PlayerPower power;
		final HamonData hamon;
		final Ability sendo;
		final Vec3 origin;
		final AABB area;
		final List<HamonSendoOverdriveEntity> wavesBefore;
		final List<Entity> entities = new ArrayList<>();
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		final List<SoundEvent> said = new ArrayList<>();
		final Consumer<PlayLevelSoundEvent.AtPosition> voiceLines;
		int swingsBeforeRelease;

		// a Survival Hamon user of Jonathan's technique who learned Sendo Overdrive, full of energy and breath,
		// empty-handed, floating, looking south
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
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.SENDO_OVERDRIVE.get());
			hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, 1000, true, true);
			hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, 2000, true, true);
			helper.assertTrue(hamon.pickHamonTechnique(user, ModHamonSkills.CHARACTER_JONATHAN.get()),
					"Fixture: could not pick Jonathan's technique");
			refill();
			sendo = power.getAbility("sendo_overdrive");
			helper.assertTrue(sendo instanceof HamonSendoOverdriveAbility, "Fixture: Hamon has no sendo_overdrive");
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
			area = new AABB(user.blockPosition()).inflate(12.0D);
			wavesBefore = level.getEntitiesOfClass(HamonSendoOverdriveEntity.class, area);
			voiceLines = event -> {
				Holder<SoundEvent> sound = event.getSound();
				if (event.getLevel() == level && sound != null && event.getPosition().distanceToSqr(user.position()) < 0.01D) {
					said.add(sound.value());
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayLevelSoundEvent.AtPosition.class, voiceLines);
		}

		void refill() {
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
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

		void assertUsable(ActionTarget aim, boolean ownClient, String scene) {
			ConditionCheck check = check(aim, ownClient);
			helper.assertTrue(check.isPositive(), "1.16's Sendo Overdrive asked for no target (TargetRequirement.NONE) and"
					+ " could be pressed at " + scene + "; the port's is " + why(check) + " at " + describe(aim));
		}

		void assertRefusedWith(ActionTarget aim, String condition, String scene) {
			ConditionCheck check = check(aim, false);
			String key = "jojo.message.action_condition." + condition;
			helper.assertTrue(!check.isPositive() && check.getWarning() != null
					&& check.getWarning().getContents() instanceof TranslatableContents contents && key.equals(contents.getKey()),
					"1.16 refused Sendo Overdrive pressed at " + scene + " with " + key + "; the port's is " + why(check));
		}

		private ConditionCheck check(ActionTarget aim, boolean ownClient) {
			setAim(aim, ownClient);
			ConditionCheck check = sendo.checkConditions(power);
			setAim(ActionTarget.EMPTY, false);
			return check;
		}

		/*
		 * As ClAbilityInputPacket handles the press of Sendo Overdrive's CLICK slot: the aim the client sent stays the
		 * user's aim, the slot's condition check (with its message when refused), then the press; its key stays held.
		 */
		EntityActionInstance press(ActionTarget aim, String scene) {
			setAim(aim, false);
			AbilityConditionCheck slot = resolve();
			boolean admitted = AbilityInput.withConditionCheck(slot, user, InputMethod.CLICK);
			helper.assertTrue(admitted, "1.16 held Sendo Overdrive pressed at " + scene + " (TargetRequirement.NONE); the"
					+ " port's press is " + why(slot.conditionCheck) + " at " + describe(aim));
			HeldInputEntry entry = AbilityInput.keyPress(KEY, slot.ability, user, null, InputMethod.CLICK, 0.0F,
					BufferingState.clickCanBuffer(), sendo.getAbilityId());
			EntityActionInstance action = LivingComponentAction.getComponent(user).getAction();
			helper.assertTrue(entry != null && entry.action == action && action instanceof HamonSendoOverdriveAbility.SendoOverdriveInstance
					&& action.ability == sendo && action.getPhase() == ActionPhase.WINDUP, "Fixture: the press of Sendo Overdrive"
							+ " at " + scene + " started " + action + (action != null ? " in " + action.getPhase() : ""));
			return action;
		}

		// the power resolves its slots once a tick (Power.updateAvailableMoves), and the test does not tick it
		private AbilityConditionCheck resolve() {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(power, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
			}
			AbilityConditionCheck slot = power.updateAvailableMoves().getContextVariationContainer(sendo);
			helper.assertTrue(slot != null && slot.ability == sendo, "Fixture: the HUD resolves no Sendo Overdrive slot: "
					+ (slot != null ? slot.ability : null));
			return slot;
		}

		void assertHeldWithItsLine(EntityActionInstance held, String scene) {
			helper.assertTrue(LivingComponentAction.getComponent(user).getAction() == held && held.getPhase() == ActionPhase.WINDUP
					&& !held.isOver(), "1.16 held Sendo Overdrive pressed at " + scene + " until its release; the port's is "
							+ held.getPhase() + (held.isOver() ? ", over" : "") + ", the user's action "
							+ LivingComponentAction.getComponent(user).getAction());
			SoundEvent line = ModSoundEvents.JONATHAN_SENDO_OVERDRIVE.get();
			int count = (int) said.stream().filter(sound -> sound == line).count();
			helper.assertTrue(count == 1, "1.16 said Jonathan's Sendo Overdrive line once on the press at " + scene
					+ "; the port's said it " + count + " times (heard " + said + ")");
		}

		// lets go of the key and ticks once; the waves sent since the scene began
		List<HamonSendoOverdriveEntity> release(EntityActionInstance held) {
			swingsBeforeRelease = user.ownMainHandSwings;
			AbilityInput.keyRelease(KEY, user);
			tick(1);
			List<HamonSendoOverdriveEntity> sent = new ArrayList<>();
			for (HamonSendoOverdriveEntity wave : level.getEntitiesOfClass(HamonSendoOverdriveEntity.class, area)) {
				if (!wavesBefore.contains(wave)) {
					sent.add(wave);
				}
			}
			return sent;
		}

		/*
		 * Let go of with no block under the crosshair: 1.16 stoppedHolding sent no wave. Before the full charge
		 * (stopHeldAction(false), no perform) nothing was spent and the hand did not swing; at the full charge, with
		 * its checks passed, performAction spent the 900 energy and swingHand swung the main hand, once on the user's own
		 * screen too (its client ran its own stopHeldAction(true)).
		 */
		void assertReleasedWithoutWave(EntityActionInstance held, boolean performs, String scene) {
			float energy = hamon.getEnergy();
			float breath = hamon.getBreathStability();
			helper.assertTrue(!user.swinging, "Fixture: the user swings before Sendo Overdrive pressed at " + scene
					+ " is let go of");
			List<HamonSendoOverdriveEntity> sent = release(held);
			helper.assertTrue(sent.isEmpty(), "1.16's Sendo Overdrive pressed at " + scene + " sent no wave (stoppedHolding"
					+ " found no block); the port's sent " + sent.size() + " at " + sent.stream().map(wave ->
							wave.getTargetedBlockPos() + " (" + wave.getTargetedFace() + ")").toList());
			helper.assertTrue(held.isOver() || LivingComponentAction.getComponent(user).getAction() != held,
					"Sendo Overdrive pressed at " + scene + " is still running: " + held.getPhase());
			int ownSwings = user.ownMainHandSwings - swingsBeforeRelease;
			if (performs) {
				helper.assertTrue(hamon.getEnergy() == energy - 900.0F && hamon.getBreathStability() == breath
						&& user.swinging && user.swingingArm == InteractionHand.MAIN_HAND && ownSwings == 1, "1.16's Sendo"
								+ " Overdrive pressed at " + scene + " still performed: its 900 energy spent and the main hand"
								+ " swung, on its user's own screen too; the port's energy went from " + energy + " to "
								+ hamon.getEnergy() + ", its breath stability from " + breath + " to "
								+ hamon.getBreathStability() + ", the user " + (user.swinging ? "swung its " + user.swingingArm
										: "did not swing") + ", its own client was sent " + ownSwings + " main-hand swings");
			}
			else {
				helper.assertTrue(hamon.getEnergy() == energy && hamon.getBreathStability() == breath && !user.swinging
						&& ownSwings == 0, "1.16's Sendo Overdrive pressed at " + scene + " did not perform: nothing spent,"
								+ " no swing; the port's energy went from " + energy + " to " + hamon.getEnergy() + ", its breath"
								+ " stability from " + breath + " to " + hamon.getBreathStability() + ", the user "
								+ (user.swinging ? "swung its " + user.swingingArm : "did not swing") + ", its own client was"
								+ " sent " + ownSwings + " main-hand swings");
			}
		}

		/*
		 * Sendo Overdrive started at the aim with no key holding it, as a buffered click or code does: charging up to its
		 * full charge, where it fires by itself; with a stick taken into the main hand on the way, the failed held check
		 * ends it first.
		 */
		void assertUnheldFullCharge(ActionTarget aim, boolean stick, String scene) {
			setAim(aim, false);
			LivingComponentAction component = LivingComponentAction.getComponent(user);
			EntityActionInstance action = ((EntityActionType) sendo).initActionOnAbilityUse(level, user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			float energy = hamon.getEnergy();
			float breath = hamon.getBreathStability();
			tick(stick ? 10 : 30);
			if (stick) {
				user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
				tick(20);
			}
			else {
				helper.assertTrue(component.getAction() == action && action.getPhase() == ActionPhase.WINDUP,
						"Fixture: Sendo Overdrive no key holds, at " + scene + ", is not charging a tick short of its full charge: "
								+ action.getPhase());
			}
			helper.assertTrue(hamon.getEnergy() == energy && !user.swinging && user.ownMainHandSwings == 0,
					"Sendo Overdrive no key holds, at " + scene + ", spent energy or swung before its full charge");
			tick(1);
			List<HamonSendoOverdriveEntity> sent = new ArrayList<>(level.getEntitiesOfClass(HamonSendoOverdriveEntity.class, area));
			sent.removeAll(wavesBefore);
			int ownSwings = user.ownMainHandSwings;
			helper.assertTrue(sent.isEmpty() && (action.isOver() || component.getAction() != action), "1.16's Sendo Overdrive no"
					+ " key holds, at " + scene + ", sent no wave (stoppedHolding found no block) and was over; the port's sent "
					+ sent.size() + ", phase " + action.getPhase());
			if (stick) {
				helper.assertTrue(hamon.getEnergy() == energy && hamon.getBreathStability() == breath && !user.swinging
						&& ownSwings == 0, "1.16's Sendo Overdrive no key holds, at " + scene + ", did not perform: nothing"
								+ " spent, no swing; the port's energy went from " + energy + " to " + hamon.getEnergy() + ", the"
								+ " user " + (user.swinging ? "swung its " + user.swingingArm : "did not swing") + ", its own client"
								+ " was sent " + ownSwings + " main-hand swings");
			}
			else {
				helper.assertTrue(hamon.getEnergy() == energy - 900.0F && hamon.getBreathStability() == breath && user.swinging
						&& user.swingingArm == InteractionHand.MAIN_HAND && ownSwings == 1, "1.16's Sendo Overdrive no key holds,"
								+ " at " + scene + ", fired at its full charge like a released one: its 900 energy spent and the"
								+ " main hand swung once on its user's own screen; the port's energy went from " + energy + " to "
								+ hamon.getEnergy() + ", the user " + (user.swinging ? "swung its " + user.swingingArm
										: "did not swing") + ", its own client was sent " + ownSwings + " main-hand swings");
			}
			user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}

		/*
		 * Sendo Overdrive held by a key at the aim to its full charge and released, or held until an item is taken into
		 * the main hand (a failed held check ends the hold); its wave must stand where 1.16 put it.
		 */
		void assertWaveAt(ActionTarget aim, boolean failedCheck, String scene) {
			refill();
			setAim(aim, false);
			LivingComponentAction component = LivingComponentAction.getComponent(user);
			EntityActionInstance action = ((EntityActionType) sendo).initActionOnAbilityUse(level, user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.put(KEY,
					new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			List<HamonSendoOverdriveEntity> before = level.getEntitiesOfClass(HamonSendoOverdriveEntity.class, area);
			List<HamonSendoOverdriveEntity> sent = new ArrayList<>();
			try {
				tick(failedCheck ? 10 : 31);
				helper.assertTrue(component.getAction() == action && action.getPhase() == ActionPhase.WINDUP, "Fixture: Sendo"
						+ " Overdrive " + scene + " is not charging: " + action.getPhase());
				swingsBeforeRelease = user.ownMainHandSwings;
				if (failedCheck) {
					user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
					tick(1);
				}
				else {
					AbilityInput.keyRelease(KEY, user);
					tick(1);
				}
				for (HamonSendoOverdriveEntity wave : level.getEntitiesOfClass(HamonSendoOverdriveEntity.class, area)) {
					if (!before.contains(wave)) {
						sent.add(wave);
					}
				}
				assertWaveFrom(sent, aim, scene);
			}
			finally {
				user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
				component.setAction(null, SyncType.NO_SYNC);
				sent.forEach(Entity::discard);
				setAim(ActionTarget.EMPTY, false);
			}
		}

		/*
		 * One wave where 1.16's stoppedHolding put it: centred on the aimed block (its centre less half the wave's height),
		 * on the axis of the face the crosshair met, with that block and face as its target. stoppedHolding then swung the
		 * main hand on both sides, so the user's own screen showed one swing since the release.
		 */
		void assertWaveFrom(List<HamonSendoOverdriveEntity> sent, ActionTarget aim, String scene) {
			HamonSendoOverdriveEntity wave = sent.size() == 1 ? sent.get(0) : null;
			BlockPos pos = aim.getBlockPos();
			Direction face = aim.getFace();
			Vec3 expected = Vec3.atCenterOf(pos).subtract(0.0D, wave != null ? wave.getBbHeight() * 0.5D : 0.0D, 0.0D);
			helper.assertTrue(wave != null && pos.equals(wave.getTargetedBlockPos()) && face == wave.getTargetedFace()
					&& face.getAxis() == waveAxis(wave) && wave.position().distanceTo(expected) < 1.0E-6D, "1.16's Sendo"
							+ " Overdrive " + scene + " sent one wave from the block the crosshair met, " + pos + " (" + face
							+ "), at " + expected + " on the " + face.getAxis() + " axis; the port's "
							+ (wave == null ? "sent " + sent.size() : "targets " + wave.getTargetedBlockPos() + " ("
									+ wave.getTargetedFace() + ") at " + wave.position() + " on the " + waveAxis(wave) + " axis"));
			int ownSwings = user.ownMainHandSwings - swingsBeforeRelease;
			helper.assertTrue(user.swinging && user.swingingArm == InteractionHand.MAIN_HAND && ownSwings == 1, "1.16's Sendo"
					+ " Overdrive " + scene + " swung the main hand with its wave, once on its user's own screen too; the port's"
					+ " user " + (user.swinging ? "swung its " + user.swingingArm : "did not swing") + ", its own client was"
					+ " sent " + ownSwings + " main-hand swings");
		}

		// the user's action slot, without entity physics or passive Hamon regeneration
		void tick(int count) {
			LivingComponentAction component = LivingComponentAction.getComponent(user);
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
			}
		}

		private static Direction.Axis waveAxis(HamonSendoOverdriveEntity wave) {
			try {
				Field field = HamonSendoOverdriveEntity.class.getDeclaredField("axis");
				field.setAccessible(true);
				return (Direction.Axis) field.get(wave);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("HamonSendoOverdriveEntity.axis is not reachable", e);
			}
		}

		// the aim as the server stores it, or as the user's own client sends it
		void setAim(ActionTarget aim, boolean ownClient) {
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
			NeoForge.EVENT_BUS.unregister(voiceLines);
			user.local = false;
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			LivingComponentAction.getComponent(user).setAction(null, SyncType.NO_SYNC);
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			for (HamonSendoOverdriveEntity wave : level.getEntitiesOfClass(HamonSendoOverdriveEntity.class, area)) {
				if (!wavesBefore.contains(wave)) {
					wave.discard();
				}
			}
			entities.forEach(Entity::discard);
			List<BlockPos> placed = new ArrayList<>(blocks.keySet());
			for (int i = placed.size() - 1; i >= 0; i--) {
				level.setBlock(placed.get(i), blocks.get(placed.get(i)), Block.UPDATE_CLIENTS);
			}
		}
	}
}
