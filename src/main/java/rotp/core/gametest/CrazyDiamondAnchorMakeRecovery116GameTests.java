package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.crazydiamond.CrazyDAnchorMakeAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModGamerules;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 CrazyDiamondBlockCheckpointMake: the block anchor make recovered for the heavy attack's recovery at no finisher
 * value (getStandRecoveryTicks: StandStatFormulas.getHeavyAttackRecovery(attackSpeed, 0), 14 to 16 ticks for Crazy
 * Diamond) and that recovery was free (isFreeRecovery), so another action pressed in it started at once; the action was
 * not chainable, so its own press was refused in every phase.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrazyDiamondAnchorMakeRecovery116GameTests {
	private static final short KEY = 31;

	private CrazyDiamondAnchorMakeRecovery116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theAnchorMakeRecoversForTheHeavyAttacksRecoveryAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			int recovery = StandStatFormulas.getHeavyAttackRecovery(s.stand.getAttackSpeed(), 0);
			helper.assertTrue(recovery >= 14 && recovery <= 16, "setup: Crazy Diamond's heavy recovery at speed "
					+ s.stand.getAttackSpeed() + " is " + recovery);
			EntityActionInstance action = s.pressAtPlank();
			helper.assertTrue(action.phasesLength.getFloat(ActionPhase.RECOVERY) == recovery,
					"1.16 gave the anchor make the heavy attack's recovery, getHeavyAttackRecovery(" + s.stand.getAttackSpeed()
							+ ", 0) = " + recovery + " ticks: " + action.phasesLength);
			int recoveryTick = -1;
			int endTick = -1;
			for (int tick = 1; tick <= 60 && endTick < 0; tick++) {
				s.standAction.tick();
				if (recoveryTick < 0 && s.standAction.getAction() == action && action.getPhase() == ActionPhase.RECOVERY) {
					recoveryTick = tick;
				}
				if (s.standAction.getAction() != action) {
					endTick = tick;
				}
			}
			helper.assertTrue(s.level().getBlockState(s.plank).isAir(), "setup: the anchor make did not take the plank");
			helper.assertTrue(recoveryTick > 0 && endTick - recoveryTick == recovery,
					"1.16 ended the anchor make " + recovery + " ticks into its recovery: recovery from tick " + recoveryTick
							+ ", over on tick " + endTick);
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aPressInTheAnchorMakesRecoveryTakesOverAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			EntityActionInstance action = s.pressAtPlank();
			for (int tick = 1; tick <= 30 && action.getPhase() != ActionPhase.RECOVERY; tick++) {
				s.standAction.tick();
			}
			helper.assertTrue(s.standAction.getAction() == action && action.getPhase() == ActionPhase.RECOVERY,
					"setup: the anchor make is not in its recovery: " + s.standAction.getAction() + " " + action.getPhase());

			// another plank, so that only the running action can refuse the press
			s.aimAtNewPlank();
			AbilityConditionCheck resolved = s.resolve("block_anchor_make");
			helper.assertTrue(resolved != null && !resolved.conditionCheck.isPositive() && resolved.conditionCheck.getWarning() == null,
					"1.16 refused the anchor make pressed again in its recovery silently: "
							+ (resolved == null ? "nothing" : resolved.conditionCheck.isPositive() ? "usable" : "refused with "
									+ resolved.conditionCheck.getWarning()));
			BufferingState own = s.press("block_anchor_make");
			helper.assertTrue(s.standAction.getAction() == action && !own.shouldBuffer && !own.isActionSuccess,
					"1.16 refused the anchor make pressed again in its recovery (not chainable), unqueued: buffered "
							+ own.shouldBuffer + ", started " + own.isActionSuccess + ", the Stand's action " + s.standAction.getAction());

			BufferingState punch = s.press("punch");
			EntityActionInstance next = s.standAction.getAction();
			helper.assertTrue(punch.isActionSuccess && !punch.shouldBuffer && next != null && next != action
					&& next.ability == s.power.getAbility("punch"),
					"1.16 started a punch pressed in the anchor make's free recovery at once (isFreeRecovery): started "
							+ punch.isActionSuccess + ", buffered " + punch.shouldBuffer + ", the Stand's action " + next);
			helper.succeed();
		}
	}

	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final FakePlayer user;
		private final StandType type;
		private final BlockPos plank;
		private final boolean breakBlocks;
		private StandPower power;
		private StandEntity stand;
		private LivingComponentAction standAction;

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CDAnchorRecovery"));
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
			plank = helper.absolutePos(new BlockPos(2, 3, 4));
			breakBlocks = helper.getLevel().getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
			try {
				helper.assertTrue(type != null, "Missing Stand type crazy_diamond");
				helper.getLevel().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(true, helper.getLevel().getServer());
				Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
				user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
				user.setNoGravity(true);
				user.getAbilities().instabuild = false;
				helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: the Stand user was not added");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
						== StandPowerTransitions.Status.APPLIED, "setup: Crazy Diamond was not granted");
				power.setResolveLevel(4);
				power.getCurTypeData()._lockedAbilities.remove("block_anchor_make");
				helper.assertTrue(type.summon(user, power), "setup: Crazy Diamond was not summoned");
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "setup: the summoned Stand is missing");
				stand.summonLockTicks = 0;
				stand.setNoGravity(true);
				// out of the user's look at the plank
				stand.moveTo(origin.x - 1.5, origin.y, origin.z - 1.5, 0.0F, 0.0F);
				standAction = LivingComponentAction.getComponent(stand);
				power.setStamina(power.getMaxStamina());
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		private ServerLevel level() {
			return helper.getLevel();
		}

		// pressed at an oak plank 2 blocks ahead of its user, as the client sends that look
		private EntityActionInstance pressAtPlank() {
			aimAtNewPlank();
			BufferingState pressed = press("block_anchor_make");
			EntityActionInstance action = standAction.getAction();
			helper.assertTrue(pressed.isActionSuccess && action instanceof CrazyDAnchorMakeAbility.AnchorMake,
					"setup: the anchor make pressed at a plank did not start: started " + pressed.isActionSuccess
							+ ", the Stand's action " + action);
			return action;
		}

		private void aimAtNewPlank() {
			helper.getLevel().setBlockAndUpdate(plank, Blocks.OAK_PLANKS.defaultBlockState());
			user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(plank));
			user.setYHeadRot(user.getYRot());
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(plank, Direction.NORTH));
		}

		// as ClAbilityInputPacket handles a click: the key's resolved ability, its condition check, then the press
		private BufferingState press(String name) {
			power.setStamina(power.getMaxStamina());
			BufferingState state = BufferingState.clickCanBuffer();
			Ability base = power.getAbility(name);
			AbilityConditionCheck container = resolve(name);
			if (container != null && AbilityInput.withConditionCheck(container, user, InputMethod.CLICK)) {
				AbilityInput.keyPress(KEY, container.ability, user, null, InputMethod.CLICK, 0, state, base.getAbilityId());
				AbilityInput.keyRelease(KEY, user);
			}
			return state;
		}

		// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
		private AbilityConditionCheck resolve(String name) {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(power, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
			}
			return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
		}

		@Override
		public void close() {
			if (stand != null) {
				standAction.setAction(null, user, SyncType.NO_SYNC);
			}
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.clear();
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			helper.getLevel().setBlockAndUpdate(plank, Blocks.AIR.defaultBlockState());
			helper.getLevel().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(breakBlocks, helper.getLevel().getServer());
			user.getInventory().clearContent();
			user.discard();
		}
	}
}
