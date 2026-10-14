package rotp.core.gametest;

import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 TheWorldTSHeavyAttack.punchEntity was a HeavyPunchInstance with impactSound THE_WORLD_PUNCH_HEAVY, played by
 * StandEntity.attackTarget after the hit and only when it landed (or the target was not living), as the ordinary
 * heavy punch.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheWorldTSPunchImpactSoundGameTests {

	private TheWorldTSPunchImpactSoundGameTests() {}

	@GameTest(template = "empty")
	public static void timeStopPunchSoundsItsImpactOnlyOnAHitThatLands(GameTestHelper helper) {
		helper.assertTrue(impactSounds(helper, true, new Vec3(1.5, 2, 1.5)) == 0,
				"The time-stop punch played its heavy impact sound on a hit that did not land; 1.16 played none");
		int landed = impactSounds(helper, false, new Vec3(4.5, 2, 1.5));
		helper.assertTrue(landed == 1, "fixture: the time-stop punch that landed played " + landed + " heavy impact sounds");
		helper.succeed();
	}

	private static int impactSounds(GameTestHelper helper, boolean invulnerableTarget, Vec3 relative) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Cow cow = EntityType.COW.create(helper.getLevel());
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
		StandPower power = PowerClass.STAND.attachGet(user);
		int[] sounds = {0};
		Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
			if (cow != null && event.getLevel() == helper.getLevel() && event.getSound() != null
					&& event.getSound().value() == ModSoundEvents.THE_WORLD_PUNCH_HEAVY.get()
					&& event.getPosition().distanceTo(cow.getBoundingBox().getCenter()) < 2) {
				sounds[0]++;
			}
		};
		try {
			Vec3 pos = helper.absoluteVec(relative);
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "fixture: the user was not added");
			helper.assertTrue(cow != null, "fixture: no cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
			cow.setHealth(200);
			cow.setInvulnerable(invulnerableTarget);
			cow.moveTo(pos.x, pos.y, pos.z + 1.7, 180, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "fixture: the cow was not added");
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "fixture: The World not granted");
			power.setResolveLevel(4);
			helper.assertTrue(type.summon(user, power), "fixture: The World not summoned");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "fixture: no The World entity");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.moveTo(pos.x, pos.y, pos.z + 0.5, 0, 0);
			power.setStamina(power.getMaxStamina());
			TheWorldTSPunch punch = new TheWorldTSPunch((EntityActionType) power.getAbility("ts_punch")) {
				@Override
				protected ActionTarget getPunchTarget(StandEntity performer) {
					return new ActionTarget(cow);
				}
			};
			LivingComponentAction component = LivingComponentAction.getComponent(stand);
			component.setAction(punch, user, SyncType.NO_SYNC);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, listener);
			punch.actionPerformStart();
			helper.assertTrue(punch.punchedTarget != null && punch.punchedTarget.getMainEntity() == cow,
					"fixture: the time-stop punch did not strike the cow: " + punch.punchedTarget);
			helper.assertTrue(invulnerableTarget == (cow.getHealth() == 200),
					"fixture: the cow's health " + cow.getHealth() + " does not match invulnerable=" + invulnerableTarget);
			component.setAction(null, user, SyncType.NO_SYNC);
			return sounds[0];
		}
		finally {
			NeoForge.EVENT_BUS.unregister(listener);
			if (type != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			if (cow != null) cow.discard();
			user.discard();
		}
	}
}
