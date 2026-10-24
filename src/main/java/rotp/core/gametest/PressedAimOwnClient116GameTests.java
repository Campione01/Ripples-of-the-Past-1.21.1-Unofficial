package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16's client ran a press on its own power (PowerBaseImpl.onClickAction) with the mouse target it sent, and the server
 * with the same target: both copies of the action took it. The port's EntityActionInstance.captureActionTargetFromAim
 * takes the aim of the entity it is given; an action that falls back to its user's aim (an add-on Stand move such as
 * Cream's Chomp or Bad Company's command, or a Hamon move) reads, on the user's own client, the aim that client sends
 * (LivingComponentAction.getAimTarget), since its copy of the user holds none. A scene's user stands in for that client's
 * copy while its isLocalPlayer() is true and the aim it sends is in LivingComponentAction.CLIENT_PLAYER_AIM, and for the
 * server's with the aim stored. Own batch.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PressedAimOwnClient116GameTests {
	private static final String BATCH = "pressed_aim_own_client_116";
	private static final String PROBE = "pressed_aim_own_client_probe";

	private PressedAimOwnClient116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void anActionThatFallsBackToItsUsersAimTakesTheAimTheOwnClientSendsAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
		ClientUser user = new ClientUser(level);
		List<Entity> entities = new ArrayList<>();
		StandPower standPower = null;
		StandType standType = null;
		try {
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the user");
			entities.add(user);
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			standPower = PowerClass.STAND.attachGet(user);
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(standType != null && StandPowerTransitions.insert(standPower, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			standPower.getMoveset().abilities.put(PROBE, new UsersAimProbe(ModStandAbilities.PUNCH.get(),
					new AbilityId(PowerClass.STAND, standType.getId(), PROBE)));
			helper.assertTrue(standType.summon(user, standPower), "Could not summon Star Platinum");
			StandEntity stand = standPower.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing the Star Platinum entity");
			entities.add(stand);
			Mob cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + 2.0D, 180.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
			entities.add(cow);
			ActionTarget atCow = new ActionTarget(cow);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);

			// a Stand action falling back to its user's aim, as an add-on's (its performer the Stand)
			UsersAimProbeAction standAction = (UsersAimProbeAction) ((EntityActionType) standPower.getAbility(PROBE))
					.initActionOnAbilityUse(level, user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(standAction, user, SyncType.NO_SYNC);
			// the server's copy: the aim the client sent is stored
			LivingComponentAction.getComponent(user).entityAim.setTarget(atCow);
			ActionTarget serverStand = standAction.takeUsersAim().resolveEntityId(level);
			helper.assertTrue(serverStand.getEntity() == cow, "Fixture: the server's copy of the Stand action did not take the cow"
					+ " its user's client sent: " + describe(serverStand));
			// the user's own client's copy: no stored aim, the aim it sends
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(atCow);
			user.local = true;
			ActionTarget ownClientStand = standAction.takeUsersAim().resolveEntityId(level);
			user.local = false;
			helper.assertTrue(ownClientStand.getEntity() == cow, "1.16's client ran the press with the mouse target it sent and"
					+ " its copy of the Stand action took the cow; the port's own client's copy, falling back to its user's aim,"
					+ " took " + describe(ownClientStand));

			// a Hamon action that takes its user's aim at its start (Overdrive Beat)
			Ability beat = power.getAbility("hamon_beat");
			helper.assertTrue(beat instanceof EntityActionType, "Fixture: Hamon has no hamon_beat action");
			EntityActionInstance beatAction = ((EntityActionType) beat).initActionOnAbilityUse(level, user, user, null);
			LivingComponentAction.getComponent(user).setAction(beatAction, user, SyncType.NO_SYNC);
			user.local = true;
			beatAction.onActionSet(null);
			user.local = false;
			ActionTarget ownClientBeat = snapshot(beatAction);
			ownClientBeat = ownClientBeat != null ? ownClientBeat.resolveEntityId(level) : null;
			helper.assertTrue(ownClientBeat != null && ownClientBeat.getEntity() == cow, "1.16's client took the mouse target"
					+ " it sent for Overdrive Beat; the port's own client's copy took " + describe(ownClientBeat));
		}
		finally {
			user.local = false;
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(user).setAction(null, SyncType.NO_SYNC);
			StandEntity summoned = standPower != null ? standPower.getSummonedStandEntity() : null;
			if (summoned != null) {
				LivingComponentAction.getComponent(summoned).setAction(null, SyncType.NO_SYNC);
			}
			if (standPower != null && standType != null && standPower.isSummoned()) {
				standType.forceUnsummon(user, standPower);
			}
			entities.forEach(Entity::discard);
		}
		helper.succeed();
	}

	@Nullable
	private static ActionTarget snapshot(EntityActionInstance action) {
		try {
			Field field = EntityActionInstance.class.getDeclaredField("actionTargetSnapshot");
			field.setAccessible(true);
			return (ActionTarget) field.get(action);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("EntityActionInstance.actionTargetSnapshot is not reachable", e);
		}
	}

	private static String describe(@Nullable ActionTarget target) {
		if (target == null) {
			return "nothing";
		}
		return switch (target.getType()) {
			case ENTITY -> String.valueOf(target.getEntity());
			case BLOCK -> "block " + target.getBlockPos();
			default -> "nothing";
		};
	}

	// a player that is its own client's player (LocalPlayer.isLocalPlayer) while local is set
	private static final class ClientUser extends Player {
		boolean local;

		ClientUser(ServerLevel level) {
			super(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "pressed-aim-own-client-user"));
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

	// a Stand move whose press falls back to its user's aim, as an add-on's (Cream's Chomp, Bad Company's command)
	private static final class UsersAimProbe extends StandEntityAbility {
		UsersAimProbe(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId, UsersAimProbeAction::new);
		}
	}

	private static final class UsersAimProbeAction extends EntityActionInstance {
		UsersAimProbeAction(EntityActionType ability) {
			super(ability);
		}

		ActionTarget takeUsersAim() {
			return captureActionTargetFromAim(getPowerUser());
		}
	}
}
