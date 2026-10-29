package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.abilities.HamonRebuffOverdriveAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A client's copy of a synced action is of its power type's base-moveset instance of the ability
 * (EntityActionType#encodeAbility sends it without its user), not of the user's own one that the client's moveset and
 * HUD hold. 1.16's client asked the same checks of its copy as the server of its own, so the port's client must give a
 * rule that tells the action's own ability from the others the server's answer: each row builds the action once of the
 * user's own ability, as the server holds it, and once of the base-moveset one, as the client holds it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClientCopyOfSyncedAction116GameTests {
	private static final short KEY = 31;

	private ClientCopyOfSyncedAction116GameTests() {}

	/*
	 * 1.16 StandEntityAction.checkTaskCancelling skipped the power's held action (getHeldAction() == this), and its HUD drew
	 * that action's slot from its own checkRequirements, so a held charge's slot was not greyed for being held. The port's
	 * Ability#isHeldBackByHeldAction leaves the held action's own ability to its own check; a charge lets no other ability
	 * through, so a slot taken for another would be drawn greyed.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theClientDrawsAHeldChargesOwnSlotAsTheServerDoesAsIn116(GameTestHelper helper) {
		List<Entity> spawned = new ArrayList<>();
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		FakePlayer user = addUser(helper, spawned, "ClientCopyCharge");
		StandPower power = PowerClass.STAND.attachGet(user);
		try {
			summon(helper, user, power, type, 4);
			StandEntity stand = power.getSummonedStandEntity();
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
			String name = "heavy_charged";
			Ability own = power.getAbility(name);
			Ability base = power.getPowerType().getBaseMoveset().getAbility(name);
			assertCopy(helper, name, own, base);
			for (Ability of : new Ability[] { own, base }) {
				String side = of == own ? "the server" : "its user's client";
				power.setStamina(power.getMaxStamina());
				EntityActionInstance charge = ((EntityActionAbility) of).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
				standAction.setAction(null, user, SyncType.NO_SYNC);
				standAction.setAction(charge, user, SyncType.NO_SYNC);
				user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.put(KEY,
						new HeldInputEntry(KEY, PowerClass.STAND, charge));
				helper.assertTrue(standAction.getAction() == charge && charge.getPhase() == ActionPhase.BUTTON_CHARGE
						&& AbilityInput.getHeldAction(user, PowerClass.STAND) == charge && !charge.heldAllowsOtherAbilities(),
						"setup: the charged heavy punch as " + side + " holds it is not the held charge: " + charge.getPhase()
								+ ", the held action " + AbilityInput.getHeldAction(user, PowerClass.STAND));
				AbilityConditionCheck slot = resolve(power, name);
				helper.assertTrue(slot != null && slot.ability == own && slot.conditionCheck.isPositive()
						&& slot.drawnCondition(user).isPositive() && !own.isHeldBackByHeldAction(user),
						"1.16 drew the slot of the charged heavy punch its key holds by its own check, usable, also on its user's"
								+ " client, whose copy is of the base-moveset ability; as " + side + " holds it: the slot "
								+ (slot != null ? slot.ability.name() + (slot.conditionCheck.isPositive() ? " usable" : " refused")
										+ ", drawn " + (slot.drawnCondition(user).isPositive() ? "usable" : "greyed") : "missing")
								+ ", held back " + own.isHeldBackByHeldAction(user));
				user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.remove(KEY);
				standAction.setAction(null, user, SyncType.NO_SYNC);
			}
			helper.succeed();
		}
		finally {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (type != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			spawned.forEach(Entity::discard);
		}
	}

	/*
	 * 1.16 HamonRebuffOverdrive.canBeUsedDuringPlayerAction: Rebuff Overdrive could be used during its own continuous action
	 * while that could still be cancelled (its windup), so its HUD slot stayed usable there; any other player action held
	 * it back.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void rebuffOverdriveStaysUsableDuringItsOwnWindupOnItsUsersClientAsIn116(GameTestHelper helper) {
		List<Entity> spawned = new ArrayList<>();
		try {
			FakePlayer user = addUser(helper, spawned, "ClientCopyRebuff");
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			helper.assertTrue(power.getPowerType() == ModPlayerPowers.HAMON.get(), "setup: could not give Hamon");
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			String name = "rebuff_overdrive";
			Ability own = power.getAbility(name);
			Ability base = power.getPowerType().getBaseMoveset().getAbility(name);
			assertCopy(helper, name, own, base);
			LivingComponentAction actions = LivingComponentAction.getComponent(user);
			for (Ability of : new Ability[] { own, base }) {
				String side = of == own ? "the server" : "its user's client";
				EntityActionInstance rebuff = ((EntityActionAbility) of).initActionOnAbilityUse(helper.getLevel(), user, user, null);
				actions.setAction(null, user, SyncType.NO_SYNC);
				actions.setAction(rebuff, user, SyncType.NO_SYNC);
				helper.assertTrue(rebuff instanceof HamonRebuffOverdriveAbility.HamonRebuffOverdrive overdrive && overdrive.canCancel()
						&& Ability.getRunningPlayerAction(user) == rebuff,
						"setup: Rebuff Overdrive as " + side + " holds it is not the user's player action in its windup: "
								+ rebuff.getPhase() + ", the player action " + Ability.getRunningPlayerAction(user));
				helper.assertTrue(own.canBeUsedDuringPlayerAction(rebuff) && !own.isHeldBackByHeldAction(user),
						"1.16 let Rebuff Overdrive be used during its own windup and drew it usable, also on its user's client,"
								+ " whose copy is of the base-moveset ability; as " + side + " holds it: usable during it "
								+ own.canBeUsedDuringPlayerAction(rebuff) + ", drawn held back " + own.isHeldBackByHeldAction(user));
				actions.setAction(null, user, SyncType.NO_SYNC);
			}
			helper.succeed();
		}
		finally {
			spawned.forEach(Entity::discard);
		}
	}

	private static void assertCopy(GameTestHelper helper, String name, Ability own, Ability base) {
		helper.assertTrue(own instanceof EntityActionAbility && base instanceof EntityActionAbility && base != own
						&& base.getAbilityId().equals(own.getAbilityId()),
				"setup: the power type's base-moveset " + name + " is not another instance of the user's own: " + base);
	}

	private static FakePlayer addUser(GameTestHelper helper, List<Entity> spawned, String name) {
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
		spawned.add(user);
		return user;
	}

	private static void summon(GameTestHelper helper, FakePlayer user, StandPower power, StandType type, int resolveLevel) {
		helper.assertTrue(type != null, "setup: no Stand type");
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "setup: could not grant the Stand");
		power.setResolveLevel(resolveLevel);
		helper.assertTrue(type.summon(user, power) && power.getSummonedStandEntity() != null, "setup: could not summon the Stand");
		// the summon lock skips the action tick, and StandEntity.tick, which counts it down, does not run here
		power.getSummonedStandEntity().summonLockTicks = 0;
	}

	// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
	private static AbilityConditionCheck resolve(Power<?> power, String name) {
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
}
