package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * StandEntity.autoGuardsOnHit: 1.16 Scary Monsters' GeoExampleStandEntity replaced actuallyHurt without the core's
 * BLOCK_STAND_ENTITY line, so its idle Stand took a frontal hit unguarded; a block its user held still blocked.
 * The Stands here are built as EntityStandType.summon builds them, once plain and once with the hook overridden.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAutoGuardOptOutGameTests {
	private StandAutoGuardOptOutGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void optedOutStandIsNotAutoGuarded(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			// control: the same Stand built the same way still auto-guards
			fixture.place(new StandEntity(fixture.standEntityType, helper.getLevel()));
			fixture.hitFrom(0, 2);
			helper.assertTrue(fixture.stand.getCurStandAction() != null && fixture.stand.isAutoGuarding()
					&& fixture.stand.isCurrentAttackBlocked(), "The plain Stand did not auto-guard the frontal hit");

			fixture.place(new NoAutoGuardStand(fixture.standEntityType, helper.getLevel()));
			boolean hurt = fixture.hitFrom(0, 2);
			helper.assertTrue(hurt, "The frontal hit did not reach the opted-out Stand");
			helper.assertTrue(fixture.stand.getCurStandAction() == null && !fixture.stand.isStandBlocking(),
					"An opted-out Stand started a guard on the hit: " + fixture.stand.getCurStandAction());
			helper.assertTrue(!fixture.stand.isCurrentAttackBlocked(), "An opted-out Stand blocked a hit it did not guard");
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void optedOutStandStillBlocksWithHeldGuard(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			StandEntity stand = fixture.place(new NoAutoGuardStand(fixture.standEntityType, helper.getLevel()));
			Ability guard = fixture.power.getAbility("guard");
			helper.assertTrue(guard instanceof EntityActionType, "Star Platinum's guard is missing");
			EntityActionType guardType = (EntityActionType) guard;
			EntityActionInstance held = guardType.createActionObj();
			guardType.initActionFromConfig(held, helper.getLevel(), fixture.user, stand);
			held.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0F);
			held.phasesLength.put(ActionPhase.WINDUP, 0F);
			held.phasesLength.put(ActionPhase.PERFORM, 40F);
			held.setStartingPhase();
			LivingComponentAction.getComponent(stand).setAction(held, fixture.user, SyncType.NO_SYNC);
			helper.assertTrue(stand.isStandBlocking(), "The held guard is not blocking");

			fixture.hitFrom(0, 2);
			helper.assertTrue(stand.getCurStandAction() == held && !stand.isAutoGuarding(),
					"The held guard was replaced on the hit");
			helper.assertTrue(stand.isCurrentAttackBlocked(), "A held guard must still block on an opted-out Stand");
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	// What an add-on Stand entity like Scary Monsters' does.
	private static final class NoAutoGuardStand extends StandEntity {
		NoAutoGuardStand(EntityType<? extends StandEntity> type, Level level) {
			super(type, level);
		}

		@Override
		protected boolean autoGuardsOnHit() {
			return false;
		}
	}

	private static final class Fixture {
		final GameTestHelper helper;
		final Player user;
		final StandType type;
		final StandPower power;
		final EntityType<? extends StandEntity> standEntityType;
		final Vec3 pos;
		StandEntity stand;
		Zombie attacker;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			// the factory FakePlayer is invulnerable, and so would its Stand be
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			user.getAbilities().invulnerable = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			StandEntity summoned = power.getSummonedStandEntity();
			helper.assertTrue(summoned != null, "The summoned Stand is missing");
			standEntityType = entityTypeOf(summoned);
			type.forceUnsummon(user, power);
			power.setStamina(power.getMaxStamina());
		}

		// As EntityStandType.summon: stand type, the user's power, then the level. Idle, facing south (+Z).
		StandEntity place(StandEntity entity) {
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			entity.withStandType(type);
			entity.refreshDimensions();
			face(entity);
			power.setSummonedStand(entity);
			helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Could not add the Stand");
			face(entity);
			LivingComponentAction.getComponent(entity).setAction(null, user, SyncType.NO_SYNC);
			helper.assertTrue(entity.getCurStandAction() == null && !entity.isManuallyControlled(),
					"The Stand is not idle");
			stand = entity;
			return entity;
		}

		private void face(StandEntity entity) {
			entity.moveTo(pos.x, pos.y, pos.z, 0, 0);
			entity.setYHeadRot(0);
			entity.yRotO = 0;
			entity.yHeadRotO = 0;
		}

		boolean hitFrom(double dx, double dz) {
			if (attacker != null) {
				attacker.discard();
			}
			attacker = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(attacker != null, "Could not create the attacker");
			attacker.moveTo(stand.getX() + dx, stand.getY(), stand.getZ() + dz, 0, 0);
			stand.invulnerableTime = 0;
			user.invulnerableTime = 0;
			return stand.hurt(DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, attacker), 2.0F);
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

		@SuppressWarnings("unchecked")
		private static EntityType<? extends StandEntity> entityTypeOf(StandEntity stand) {
			return (EntityType<? extends StandEntity>) stand.getType();
		}
	}
}
