package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityAutoBlockAction;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.actuallyHurt: a hit from the front on an idle Stand that is not manually controlled put it into
 * BLOCK_STAND_ENTITY for 5 ticks, whether or not the Stand had a block of its own, and the hit was blocked.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAutoGuardGameTests {
	private StandAutoGuardGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standWithoutUnlockedGuardUsesGenericGuard(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			fixture.lockEveryGuard();
			fixture.hitFrom(0, 2);
			EntityActionInstance action = fixture.stand.getCurStandAction();
			helper.assertTrue(action != null && action.ability == StandEntityAutoBlockAction.get(),
					"A Stand with no unlocked guard did not take the generic guard: " + action);
			helper.assertTrue(fixture.stand.isStandBlocking() && fixture.stand.isAutoGuarding(),
					"The generic guard did not count as the Stand's auto-guard");
			helper.assertTrue(fixture.stand.isCurrentAttackBlocked(), "The generic guard did not block the hit");
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standKeepsItsOwnUnlockedGuard(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			Ability ownGuard = fixture.power.getAbility("guard");
			fixture.power.getCurTypeData()._lockedAbilities.remove("guard");
			helper.assertTrue(ownGuard != null && fixture.power.isAbilityUnlocked("guard"),
					"Star Platinum's guard is missing or locked");
			fixture.hitFrom(0, 2);
			EntityActionInstance action = fixture.stand.getCurStandAction();
			helper.assertTrue(action != null && action.ability == ownGuard,
					"An unlocked guard of the Stand's own must stay the auto-guard: " + action);
			helper.assertTrue(fixture.stand.isStandBlocking() && fixture.stand.isCurrentAttackBlocked(),
					"The Stand's own auto-guard did not block the hit");
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	// 1.16 BLOCK_STAND_ENTITY.getOffsetFromUser returned null: the auto-guard changed the pose, not the Stand's place.
	// The 0.3 front offset belongs to the guard the user holds (StandEntityBlock.standOffsetFromUser(0, 0.3)).
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void autoGuardKeepsTheStandAtItsIdleOffset(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		boolean scheduled = false;
		try {
			Ability ownGuard = fixture.power.getAbility("guard");
			fixture.power.getCurTypeData()._lockedAbilities.remove("guard");
			helper.assertTrue(ownGuard instanceof EntityActionType && fixture.power.isAbilityUnlocked("guard"),
					"Star Platinum's guard is missing or locked");
			StandEntity stand = fixture.stand;
			Vec3 idleOffset = stand.offsetFromUser.idleOffset;
			helper.assertTrue(stand.offsetFromUser.isIdle(), "The idle Stand is not at its idle offset");

			fixture.hitFrom(0, 2);
			EntityActionInstance auto = stand.getCurStandAction();
			helper.assertTrue(auto != null && auto.ability == ownGuard && stand.isAutoGuarding()
					&& stand.isCurrentAttackBlocked(), "The Stand's own guard did not auto-guard the hit: " + auto);
			helper.assertTrue(stand.offsetFromUser.isIdle(),
					"The auto-guard moved the Stand off its idle offset " + idleOffset + " to "
							+ stand.offsetFromUser.getRelativeOffset() + " on " + stand.offsetFromUser.getRotations());
			helper.assertTrue(auto.getSyncedStandOffset() == null,
					"The auto-guard sends clients a Stand offset: " + auto.getSyncedStandOffset());
			helper.assertTrue(auto.userWalkSpeed == 0.3F, "The auto-guard lost the 0.3 walk speed: " + auto.userWalkSpeed);

			scheduled = true;
			helper.runAfterDelay(2, () -> {
				try {
					helper.assertTrue(stand.getCurStandAction() == auto && stand.isAutoGuarding(),
							"The 5-tick auto-guard ended early: " + stand.getCurStandAction());
					// the user faces +Z: the idle spot is 0.75 behind, the held guard's spot is 0.3 in front
					double front = stand.getZ() - fixture.user.getZ();
					helper.assertTrue(front < -0.5, "The auto-guarding Stand did not stay behind its user: " + front);

					// control: the same guard held by the user still steps in front and tells clients so
					EntityActionType guardType = (EntityActionType) ownGuard;
					EntityActionInstance held = guardType.createActionObj();
					guardType.initActionFromConfig(held, helper.getLevel(), fixture.user, stand);
					held.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0F);
					held.phasesLength.put(ActionPhase.WINDUP, 0F);
					held.phasesLength.put(ActionPhase.PERFORM, 40F);
					held.setStartingPhase();
					LivingComponentAction.getComponent(stand).setAction(held, fixture.user, SyncType.NO_SYNC);
					Vec3 heldOffset = new Vec3(0, stand.Y_OFFSET, 0.3);
					helper.assertTrue(stand.getCurStandAction() == held && !stand.isAutoGuarding(),
							"The held guard did not take over from the auto-guard");
					helper.assertTrue(heldOffset.equals(stand.offsetFromUser.getRelativeOffset())
							&& stand.offsetFromUser.getRotations() == StandOffsetFromUser.Rotations.HEAD,
							"A held guard must put the Stand 0.3 in front: " + stand.offsetFromUser.getRelativeOffset());
					helper.assertTrue(heldOffset.equals(held.getSyncedStandOffset()),
							"A held guard must send its offset to clients: " + held.getSyncedStandOffset());
				}
				finally {
					fixture.close();
				}
				helper.succeed();
			});
		}
		finally {
			if (!scheduled) {
				fixture.close();
			}
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void hitFromBehindIsNotGuarded(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			fixture.lockEveryGuard();
			fixture.hitFrom(0, -2);
			helper.assertTrue(fixture.stand.getCurStandAction() == null && !fixture.stand.isCurrentAttackBlocked(),
					"A hit from behind must not start a guard");
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	private static final class Fixture {
		final GameTestHelper helper;
		final Player user;
		final StandType type;
		final StandPower power;
		final StandEntity stand;
		Zombie attacker;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			// the factory FakePlayer is invulnerable, and so would its Stand be
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			user.getAbilities().invulnerable = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			power.setStamina(power.getMaxStamina());
			// idle, facing south (+Z)
			stand.moveTo(pos.x, pos.y, pos.z, 0, 0);
			stand.setYHeadRot(0);
			stand.yRotO = 0;
			stand.yHeadRotO = 0;
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == null && !stand.isManuallyControlled(),
					"The Stand is not idle");
		}

		void lockEveryGuard() {
			for (Ability ability : power.getMoveset().abilities.values()) {
				if (ability instanceof EntityActionType actionType && JojoModUtil.isStandGuardAbility(actionType)) {
					power.getCurTypeData()._lockedAbilities.add(ability.name());
				}
			}
			helper.assertTrue(!power.isAbilityUnlocked("guard"), "Star Platinum's guard is still unlocked");
		}

		void hitFrom(double dx, double dz) {
			attacker = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(attacker != null, "Could not create the attacker");
			attacker.moveTo(stand.getX() + dx, stand.getY(), stand.getZ() + dz, 0, 0);
			stand.invulnerableTime = 0;
			stand.hurt(DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, attacker), 2.0F);
		}

		void close() {
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			if (attacker != null) {
				attacker.discard();
			}
			user.discard();
		}
	}
}
