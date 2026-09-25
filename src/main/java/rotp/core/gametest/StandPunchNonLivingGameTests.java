package rotp.core.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.attackTarget / StandEntityPunch.doAttack: a Stand light or heavy punch hurt any entity,
 * not only living ones (boats, minecarts, item frames, end crystals).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandPunchNonLivingGameTests {
	private StandPunchNonLivingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void lightPunchHurtsBoat(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "light")) {
			EntityActionInstance action = f.initAction("punch");
			helper.assertTrue(action instanceof StandEntityPunch, "Star Platinum punch is not a light punch: " + action);
			Boat boat = f.boat();
			invoke(method(StandEntityPunch.class, "hitEntity", ActionTarget.class, Level.class, StandEntity.class),
					action, new ActionTarget(boat), f.level(), f.stand);
			f.assertHurt(boat, "light punch");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void heavyPunchHurtsBoat(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "heavy")) {
			EntityActionInstance action = f.initAction("heavy_punch");
			helper.assertTrue(action instanceof StandEntityHeavyPunch, "Star Platinum heavy_punch is not a heavy punch: " + action);
			Boat boat = f.boat();
			DamageSource source = action.makePunchDamageSource();
			invoke(method(StandEntityHeavyPunch.class, "hitEntity", ActionTarget.class, Level.class, StandEntity.class,
					DamageSource.class, float.class, float.class),
					action, new ActionTarget(boat), f.level(), f.stand, source, 1.0F, 0.0F);
			f.assertHurt(boat, "heavy punch");
		}
		helper.succeed();
	}

	private static Method method(Class<?> owner, String name, Class<?>... params) {
		try {
			Method method = owner.getDeclaredMethod(name, params);
			method.setAccessible(true);
			return method;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(owner.getSimpleName() + "." + name + " is missing", e);
		}
	}

	private static void invoke(Method method, Object instance, Object... args) {
		try {
			method.invoke(instance, args);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(method.getName() + " failed", e);
		}
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final StandType standType;
		private final StandPower power;
		private final StandEntity stand;
		private final Vec3 origin;
		private Boat boat;

		private Fixture(GameTestHelper helper, String name) {
			this.helper = helper;
			String profileName = "StandNonLiving_" + name;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
					UUID.nameUUIDFromBytes(profileName.getBytes(StandardCharsets.US_ASCII)), profileName));
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user.moveTo(origin.x, origin.y, origin.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the punching player");
			power = PowerClass.STAND.attachGet(user);
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing Star Platinum entity");
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
		}

		private EntityActionInstance initAction(String abilityName) {
			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "Missing production action " + abilityName);
			power.setStamina(power.getMaxStamina());
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level(), user, stand, null);
			// sets the performer and power user the damage source reads
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			return action;
		}

		private Boat boat() {
			boat = new Boat(level(), origin.x, origin.y, origin.z + 1.5D);
			helper.assertTrue(level().addFreshEntity(boat), "Could not add the boat");
			return boat;
		}

		private void assertHurt(Boat boat, String what) {
			helper.assertTrue(boat.getDamage() > 0 || boat.isRemoved(),
					"1.16: a Stand " + what + " hurts a non-living entity, but the boat took no damage");
		}

		private ServerLevel level() {
			return helper.getLevel();
		}

		@Override
		public void close() {
			if (boat != null) {
				boat.discard();
				// a broken boat drops its item
				level().getEntitiesOfClass(ItemEntity.class, boat.getBoundingBox().inflate(3)).forEach(Entity::discard);
			}
			if (power.isSummoned()) standType.forceUnsummon(user, power);
			user.discard();
		}
	}
}
