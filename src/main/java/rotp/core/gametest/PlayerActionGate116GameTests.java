package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonProtectionAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferedInputEntry;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Action.checkConditions (:140-143) refused, first and without a message, every action whose
 * canBeUsedDuringPlayerAction was false while its user ran a continuous player action (PlayerUtilCap.continuousAction, set
 * by 13 non-Stand attacks, Hamon Overdrive Beat among them): a non-Stand action (Action's default), and the Stand actions
 * that opted out (Kraft Work's Lock Target and Place Projectile, Stone Free's strings), while every other Stand action
 * went through (StandAction :138-141). The port marks the attacks' actions (EntityActionInstance#isPlayerAction) and asks
 * Ability#canBeUsedDuringPlayerAction (S548 rows 5, 20, 21, D5).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerActionGate116GameTests {
	private static final short BEAT_KEY = 81;
	private static final short PROTECTION_KEY = 82;
	private static final short OPT_OUT_KEY = 83;
	private static final short OPT_IN_KEY = 84;
	private static final short PUNCH_KEY = 85;
	private static final short OVERDRIVE_KEY = 86;
	private static final String OPT_OUT = "player_action_opt_out_probe";
	private static final String OPT_IN = "player_action_opt_in_probe";
	private static final String CONDITION = "jojo.message.action_condition.";

	private PlayerActionGate116GameTests() {}

	/*
	 * A Hamon user with Star Platinum out runs Hamon Overdrive Beat (9 ticks). During it Hamon Protection is refused
	 * without a message and drawn unavailable (before: it toggled), a Stand move of its user that opts out of the gate is
	 * refused the same way (before: it went off), one that does not goes off at once in its user's Stand-power slot, as the
	 * Stand's punch does on the Stand, and Hamon Overdrive, which the beat's own slot holds back, waits in the non-Stand
	 * buffer (owner decision N2) and is drawn unavailable. The user's client's copy of the beat is a player action too.
	 * Once the beat is over, Protection toggles and the opted-out move goes off.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aContinuousPlayerActionRefusesWhat116RefusedDuringItAndLetsStandMovesThrough(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			s.aim(s.cow(1.5D));
			BufferingState beatPress = s.press(s.power, "hamon_beat", InputMethod.CLICK, BEAT_KEY);
			EntityActionInstance beat = s.own().getAction();
			helper.assertTrue(beatPress.isActionSuccess && beat != null && beat.ability == s.power.getAbility("hamon_beat")
					&& beat.getPhase() == ActionPhase.PERFORM && beat.isPlayerAction(),
					"setup: Hamon Overdrive Beat is not running as a player action: started " + beatPress.isActionSuccess
							+ ", the user's action " + beat);

			boolean protectionBefore = HamonProtectionAbility.isProtectionEnabled(s.power);
			AbilityConditionCheck protectionKey = s.resolve(s.power, "hamon_protection");
			BufferingState protection = s.press(s.power, "hamon_protection", InputMethod.CLICK, PROTECTION_KEY);
			helper.assertTrue(!protection.isActionSuccess && !protection.shouldBuffer
					&& HamonProtectionAbility.isProtectionEnabled(s.power) == protectionBefore && s.user.keys().isEmpty()
					&& protectionKey != null && protectionKey.ability.isHeldBackByHeldAction(s.user),
					"1.16 refused Hamon Protection, without a message, during the player action Hamon Overdrive Beat and drew"
							+ " it unavailable: started " + protection.isActionSuccess + ", buffered " + protection.shouldBuffer
							+ ", toggled " + (HamonProtectionAbility.isProtectionEnabled(s.power) != protectionBefore)
							+ ", messages " + s.user.keys() + ", drawn unavailable "
							+ (protectionKey != null && protectionKey.ability.isHeldBackByHeldAction(s.user)));

			AbilityConditionCheck optOutKey = s.resolve(s.standPower, OPT_OUT);
			BufferingState optOut = s.press(s.standPower, OPT_OUT, InputMethod.CLICK, OPT_OUT_KEY);
			helper.assertTrue(!optOut.isActionSuccess && !optOut.shouldBuffer && s.standPowerSlot().getAction() == null
					&& s.user.keys().isEmpty() && optOutKey != null && optOutKey.ability.isHeldBackByHeldAction(s.user),
					"1.16 refused a Stand action that opted out of the player action gate (canBeUsedDuringPlayerAction false,"
							+ " Kraft Work's Lock Target), without a message: started " + optOut.isActionSuccess + ", buffered "
							+ optOut.shouldBuffer + ", the user's Stand-power action " + s.standPowerSlot().getAction()
							+ ", messages " + s.user.keys());

			BufferingState optIn = s.press(s.standPower, OPT_IN, InputMethod.CLICK, OPT_IN_KEY);
			BufferingState punch = s.press(s.standPower, "punch", InputMethod.CLICK, PUNCH_KEY);
			EntityActionInstance optInAction = s.standPowerSlot().getAction();
			EntityActionInstance punchAction = LivingComponentAction.getCurEntityAction(s.stand);
			helper.assertTrue(optIn.isActionSuccess && !optIn.shouldBuffer && optInAction != null
					&& optInAction.ability == s.standPower.getAbility(OPT_IN) && punch.isActionSuccess && punchAction != null
					&& punchAction.ability == s.standPower.getAbility("punch") && s.own().getAction() == beat
					&& !s.standPower.getAbility("punch").isHeldBackByHeldAction(s.user),
					"1.16 let every other Stand action through the player action (StandAction.canBeUsedDuringPlayerAction):"
							+ " the user's Stand move started " + optIn.isActionSuccess + " (" + optInAction + "), the punch "
							+ punch.isActionSuccess + " (" + punchAction + "), the beat still running "
							+ (s.own().getAction() == beat));

			AbilityConditionCheck overdriveKey = s.resolve(s.power, "hamon_overdrive");
			BufferingState overdrive = s.press(s.power, "hamon_overdrive", InputMethod.CLICK, OVERDRIVE_KEY);
			BufferedInputEntry buffered = buffered(s.input().inputBuffer(PowerClass.PLAYER_POWER));
			helper.assertTrue(overdrive.shouldBuffer && !overdrive.isActionSuccess && buffered != null
					&& "hamon_overdrive".equals(buffered.baseAbilityId().nameInMoveset()) && s.own().getAction() == beat
					&& overdriveKey != null && overdriveKey.ability.isHeldBackByHeldAction(s.user),
					"1.16 refused Hamon Overdrive during the player action and drew it unavailable; the beat's own slot holds"
							+ " it back in the input buffer (owner decision N2): buffered " + overdrive.shouldBuffer
							+ ", started " + overdrive.isActionSuccess + ", the buffer " + buffered + ", drawn unavailable "
							+ (overdriveKey != null && overdriveKey.ability.isHeldBackByHeldAction(s.user)));

			EntityActionInstance clientBeat = s.clientCopyOf(beat);
			helper.assertTrue(clientBeat.isPlayerAction(), "the user's client's copy of the beat is no player action");

			for (int tick = 0; tick < 20 && (s.own().getAction() == beat || s.standPowerSlot().getAction() != null); tick++) {
				s.own().tick();
				s.standPowerSlot().tick();
			}
			helper.assertTrue(s.own().getAction() != beat && s.standPowerSlot().getAction() == null,
					"setup: Hamon Overdrive Beat or the user's Stand move did not end");
			BufferingState protectionAfter = s.press(s.power, "hamon_protection", InputMethod.CLICK, PROTECTION_KEY);
			BufferingState optOutAfter = s.press(s.standPower, OPT_OUT, InputMethod.CLICK, OPT_OUT_KEY);
			helper.assertTrue(protectionAfter.isActionSuccess
					&& HamonProtectionAbility.isProtectionEnabled(s.power) != protectionBefore && optOutAfter.isActionSuccess,
					"once the player action is over 1.16 took Hamon Protection and the opted-out Stand action: protection"
							+ " toggled " + (HamonProtectionAbility.isProtectionEnabled(s.power) != protectionBefore)
							+ ", the opted-out move started " + optOutAfter.isActionSuccess);
		}
		helper.succeed();
	}

	/*
	 * 1.16 HamonRebuffOverdrive.canBeUsedDuringPlayerAction let only itself through its own continuous action, while it
	 * could still be cancelled (its windup): its key is drawn usable then and pressed again it cancels the Rebuff, while
	 * Hamon Protection stays refused and drawn unavailable.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void rebuffOverdrivePressedAgainInItsWindupGoesThroughItsOwnPlayerActionAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			BufferingState rebuffPress = s.press(s.power, "rebuff_overdrive", InputMethod.CLICK, BEAT_KEY);
			EntityActionInstance rebuff = s.own().getAction();
			helper.assertTrue(rebuffPress.isActionSuccess && rebuff != null && rebuff.getPhase() == ActionPhase.WINDUP
					&& rebuff.isPlayerAction(), "setup: Rebuff Overdrive is not in its windup as a player action: started "
							+ rebuffPress.isActionSuccess + ", the user's action " + rebuff);
			AbilityConditionCheck rebuffKey = s.resolve(s.power, "rebuff_overdrive");
			AbilityConditionCheck protectionKey = s.resolve(s.power, "hamon_protection");
			helper.assertTrue(rebuffKey != null && rebuffKey.conditionCheck.isPositive()
					&& !rebuffKey.ability.isHeldBackByHeldAction(s.user) && protectionKey != null
					&& protectionKey.ability.isHeldBackByHeldAction(s.user),
					"1.16 drew Rebuff Overdrive usable during its own cancellable windup and Hamon Protection unavailable: the"
							+ " Rebuff key's check " + (rebuffKey != null ? rebuffKey.conditionCheck.isPositive() : null)
							+ ", drawn unavailable " + (rebuffKey != null && rebuffKey.ability.isHeldBackByHeldAction(s.user))
							+ ", Protection drawn unavailable "
							+ (protectionKey != null && protectionKey.ability.isHeldBackByHeldAction(s.user)));
			BufferingState again = s.press(s.power, "rebuff_overdrive", InputMethod.CLICK, OVERDRIVE_KEY);
			helper.assertTrue(again.isActionSuccess && !again.shouldBuffer && rebuff.isOver(),
					"Rebuff Overdrive pressed again in its windup cancels it, as 1.16 let it through its own player action:"
							+ " started " + again.isActionSuccess + ", buffered " + again.shouldBuffer + ", the Rebuff over "
							+ rebuff.isOver());
		}
		helper.succeed();
	}

	@Nullable
	private static BufferedInputEntry buffered(ActionInputBuffer buffer) {
		try {
			Field field = ActionInputBuffer.class.getDeclaredField("buffered");
			field.setAccessible(true);
			return (BufferedInputEntry) field.get(buffer);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("ActionInputBuffer.buffered is not reachable", e);
		}
	}

	private static final class RecordingPlayer extends FakePlayer {
		private final List<Component> actionBar = new ArrayList<>();

		private RecordingPlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			if (actionBar) {
				this.actionBar.add(message);
			}
		}

		private List<String> keys() {
			return actionBar.stream()
					.map(message -> message.getContents() instanceof TranslatableContents contents
							? contents.getKey() : message.getString())
					.filter(key -> key.startsWith(CONDITION))
					.toList();
		}
	}

	/** A Hamon user with Star Platinum out and two test Stand moves it performs itself, looking south; it does not tick. */
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final RecordingPlayer user;
		private final PlayerPower power;
		private final StandPower standPower;
		private final StandType standType;
		private final StandEntity stand;
		private final Vec3 origin;
		private final List<Entity> spawned = new ArrayList<>();

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			user = new RecordingPlayer(helper.getLevel(), "PlayerActionGate");
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			user.getAbilities().instabuild = false;
			user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
			hamon.learnSkill(ModHamonSkills.PROTECTION.get());
			hamon.learnSkill(ModHamonSkills.REBUFF_OVERDRIVE.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			helper.assertTrue(!hamon.isProtectionEnabled(), "setup: Hamon Protection is on");
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(standType != null, "Missing Stand type star_platinum");
			standPower = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(standPower, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			standPower.getMoveset().abilities.put(OPT_OUT, new UserStandMove(ModStandAbilities.PUNCH.get(),
					new AbilityId(PowerClass.STAND, standType.getId(), OPT_OUT), false));
			standPower.getMoveset().abilities.put(OPT_IN, new UserStandMove(ModStandAbilities.PUNCH.get(),
					new AbilityId(PowerClass.STAND, standType.getId(), OPT_IN), true));
			helper.assertTrue(standType.summon(user, standPower), "Could not summon Star Platinum");
			stand = standPower.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Star Platinum is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			standPower.setStamina(standPower.getMaxStamina());
		}

		private Cow cow(double ahead) {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + ahead, 180.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add the cow");
			spawned.add(cow);
			return cow;
		}

		private void aim(Entity target) {
			own().entityAim.setTarget(new ActionTarget(target));
		}

		private LivingComponentAction own() {
			return LivingComponentAction.getComponent(user);
		}

		private LivingComponentAction standPowerSlot() {
			return LivingComponentAction.getComponent(user, PowerClass.STAND);
		}

		private EntityActionInputState input() {
			return user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		}

		// as ClAbilityInputPacket handles a press: the key's resolved ability, its condition check, then the press
		private BufferingState press(Power<?> pressed, String name, InputMethod inputMethod, short key) {
			BufferingState state = BufferingState.clickCanBuffer();
			Ability base = pressed.getAbility(name);
			helper.assertTrue(base != null, "Missing ability " + name);
			AbilityConditionCheck container = resolve(pressed, name);
			if (container != null && AbilityInput.withConditionCheck(container, user, inputMethod)) {
				AbilityInput.keyPress(key, container.ability, user, null, inputMethod, 0, state, base.getAbilityId());
			}
			AbilityInput.keyRelease(key, user);
			return state;
		}

		// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
		private AbilityConditionCheck resolve(Power<?> resolved, String name) {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(resolved, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
			}
			return resolved.updateAvailableMoves().getContextVariationContainer(resolved.getAbility(name));
		}

		// the action as the user's client holds it: decoded from what the server sends it (EntityActionInstance.encode)
		private EntityActionInstance clientCopyOf(EntityActionInstance action) {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			try {
				EntityActionInstance.encode(buf, action);
				EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
				helper.assertTrue(copy != null && copy.getClass() == action.getClass(), "setup: the action decodes as " + copy);
				return copy;
			}
			finally {
				buf.release();
			}
		}

		@Override
		public void close() {
			input().heldKeys.clear();
			own().entityAim.setTarget(ActionTarget.EMPTY);
			own().setAction(null, SyncType.NO_SYNC);
			standPowerSlot().setAction(null, SyncType.NO_SYNC);
			LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
			if (standPower.isSummoned()) {
				standType.forceUnsummon(user, standPower);
			}
			user.removeAllEffects();
			spawned.forEach(Entity::discard);
			user.discard();
		}
	}

	// a Stand move its user performs itself (its Stand-power slot), opting out of the player action gate or not
	private static final class UserStandMove extends EntityActionAbility {
		private final boolean usableDuringPlayerAction;

		UserStandMove(AbilityType<?> abilityType, AbilityId abilityId, boolean usableDuringPlayerAction) {
			super(abilityType, abilityId, EntityActionInstance::new);
			this.usableDuringPlayerAction = usableDuringPlayerAction;
			setDefaultPhaseLength(ActionPhase.PERFORM, 2);
		}

		@Override
		public boolean canBeUsedDuringPlayerAction(EntityActionInstance playerAction) {
			return usableDuringPlayerAction && super.canBeUsedDuringPlayerAction(playerAction);
		}
	}
}
