package rotp.core.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
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
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityPunch.doHit: the light punch adds its 0.1 finisher meter after every entity punch,
 * also when the hit is blocked or does no damage.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightPunchFinisherGameTests {
	private static final float EPSILON = 1.0E-4F;

	private LightPunchFinisherGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void lightPunchFinisherIgnoresHurtResult(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		String name = "LightPunchFin";
		Player user = FakePlayerFactory.get(level, new GameProfile(
				UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		Cow cow = null;
		Boat boat = null;
		try {
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			user.moveTo(origin.x, origin.y, origin.z);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the punching player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			power.setResolveLevel(1);
			helper.assertTrue(StandEntity.isFinisherMechanicUnlocked(power), "Fixture: the finisher meter is locked");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing Star Platinum entity");
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);

			Ability ability = power.getAbility("punch");
			helper.assertTrue(ability instanceof EntityActionType, "Missing production action punch");
			power.setStamina(power.getMaxStamina());
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level, user, stand, null);
			// sets the performer and power user the damage source reads
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(action instanceof StandEntityPunch, "Star Platinum punch is not a light punch: " + action);

			cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.moveTo(origin.x, origin.y, origin.z + 1.5D, 180, 0);
			cow.setNoAi(true);
			// survives the punch
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
			cow.setHealth(200);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");

			// the hit lands: 0.1
			stand.setFinisherMeter(0);
			hit(action, cow, level, stand);
			helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "Fixture: the light punch did not hurt the cow");
			assertGain(helper, stand, "a light punch that hurts a mob");

			// the hit does no damage (blocked): 1.16 still adds 0.1
			cow.setHealth(cow.getMaxHealth());
			cow.setInvulnerable(true);
			stand.setFinisherMeter(0);
			hit(action, cow, level, stand);
			helper.assertTrue(cow.getHealth() >= cow.getMaxHealth(), "Fixture: the invulnerable cow was hurt");
			assertGain(helper, stand, "a light punch that does no damage to a mob");

			// non-living target that takes no damage
			boat = new Boat(level, origin.x, origin.y, origin.z + 1.5D);
			boat.setInvulnerable(true);
			helper.assertTrue(level.addFreshEntity(boat), "Could not add the boat");
			stand.setFinisherMeter(0);
			hit(action, boat, level, stand);
			helper.assertTrue(boat.getDamage() <= 0 && !boat.isRemoved(), "Fixture: the invulnerable boat was hurt");
			assertGain(helper, stand, "a light punch that does no damage to a non-living entity");
			helper.succeed();
		}
		finally {
			if (cow != null) cow.discard();
			if (boat != null) {
				boat.discard();
				level.getEntitiesOfClass(ItemEntity.class, boat.getBoundingBox().inflate(3)).forEach(Entity::discard);
			}
			if (power != null && standType != null && power.isSummoned()) standType.forceUnsummon(user, power);
			user.discard();
		}
	}

	private static void hit(EntityActionInstance action, Entity target, Level level, StandEntity stand) {
		try {
			Method method = StandEntityPunch.class.getDeclaredMethod("hitEntity", ActionTarget.class, Level.class, StandEntity.class);
			method.setAccessible(true);
			method.invoke(action, new ActionTarget(target), level, stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the light punch hitEntity", e);
		}
	}

	private static void assertGain(GameTestHelper helper, StandEntity stand, String what) {
		float gain = stand.getFinisherMeter();
		helper.assertTrue(Math.abs(gain - 0.1F) < EPSILON,
				"1.16: " + what + " adds 0.1 finisher meter, but it added " + gain);
	}
}
