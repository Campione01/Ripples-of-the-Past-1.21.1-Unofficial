package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import rotp.addons.d4c.entity.D4CAnnihilationCloneEntity;
import rotp.addons.d4c.entity.D4CMarkerEntity;
import rotp.addons.d4c.entity.D4CMinionEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.SummonedStand;

/**
 * A server player with a summoned D4C for game tests that press its moves through AbilityInput and tick the running
 * action by hand, recording the action-bar messages the player is sent.
 */
final class D4CGameTestScene implements AutoCloseable {
    static final String ANNIHILATION_TARGET = "rotp_d4c.phase2b.annihilation_target";

    final GameTestHelper helper;
    final ServerPlayer owner;
    // a player with a connection, for actions that send custom payloads to their user
    private final D4CRecordingServerPlayer connected;
    final StandPower power;
    final StandEntity stand;
    final List<Entity> spawned = new ArrayList<>();
    String fixtureFailure = "";
    private short key = -1;

    D4CGameTestScene(GameTestHelper helper, String name) {
        this(helper, name, false);
    }

    D4CGameTestScene(GameTestHelper helper, String name, boolean withConnection) {
        this.helper = helper;
        ServerLevel level = helper.getLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(4.5D, 4.0D, 1.5D));
        if (withConnection) {
            connected = new D4CRecordingServerPlayer(helper, name, pos);
            owner = connected.player;
        }
        else {
            connected = null;
            owner = new RecordingPlayer(level, new GameProfile(UUID.randomUUID(), name));
            owner.getAbilities().instabuild = false;
        }
        owner.setNoGravity(true);
        owner.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        owner.setYHeadRot(0.0F);
        if (!withConnection) {
            level.addNewPlayer(owner);
        }
        power = PowerClass.STAND.attachGet(owner);
        power.setStand(D4CAddon.D4C.get());
        power.skipProgression();
        helper.assertTrue(D4CAddon.D4C.get().summon(owner, power), "fixture: D4C could not be summoned");
        stand = power.getSummonedStandEntity();
        helper.assertTrue(stand != null, "fixture: no summoned D4C");
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
        power.tick(); // moves are cached per tick
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(!power.isStaminaInfinite(), "fixture: the stamina bar is infinite");
    }

    Cow cow(Vec3 offset) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "fixture: no cow");
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(owner.position().add(offset));
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "fixture: the cow could not join the level");
        spawned.add(cow);
        return cow;
    }

    void minion(Vec3 offset) {
        D4CMinionEntity minion = D4CAddon.SUMMONED_PLAYER.get().create(helper.getLevel());
        helper.assertTrue(minion != null, "fixture: no minion");
        minion.setNoAi(true);
        minion.setNoGravity(true);
        minion.moveTo(owner.position().add(offset));
        minion.setOwner(owner);
        helper.assertTrue(helper.getLevel().addFreshEntity(minion), "fixture: the minion could not join");
        spawned.add(minion);
    }

    <T extends Entity> List<T> around(Class<T> type) {
        return helper.getLevel().getEntitiesOfClass(type, new AABB(owner.blockPosition()).inflate(16.0D));
    }

    EntityActionType actionType(String name) {
        helper.assertTrue(power.getAbility(name) instanceof EntityActionType, "D4C has no action " + name);
        return (EntityActionType) power.getAbility(name);
    }

    // The action set on D4C as a press sets it, without the input path
    EntityActionInstance start(String name) {
        EntityActionType actionType = actionType(name);
        EntityActionInstance action = actionType.createActionObj();
        actionType.initActionFromConfig(action, helper.getLevel(), owner, stand);
        action.setStartingPhase();
        LivingComponentAction.getComponent(stand).setAction(action, owner, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action, "fixture: " + name + " did not become D4C's action");
        return action;
    }

    // A real click; whether it set a new action on D4C
    boolean click(String name, short keyId) {
        power.tick();
        Ability ability = power.getAbility(name);
        EntityActionInstance before = stand.getCurStandAction();
        BufferingState press = BufferingState.clickOnly();
        AbilityInput.keyPress(keyId, ability, owner, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK,
                0, press, ability.getAbilityId());
        AbilityInput.keyRelease(keyId, owner);
        EntityActionInstance after = stand.getCurStandAction();
        return after != null && after != before && after.ability == ability;
    }

    // A real held key press, then the action ticked until it holds; null when it did not get there
    EntityActionInstance pressAndHold(String name, short keyId) {
        power.tick();
        Ability ability = power.getAbility(name);
        if (!(ability instanceof EntityActionAbility entityAbility)) {
            fixtureFailure = name + " is not an action";
            return null;
        }
        if (!AbilityInput.withConditionCheck(ability, owner)) {
            fixtureFailure = name + ": the press was refused " + keys();
            return null;
        }
        key = keyId;
        BufferingState press = BufferingState.clickOnly();
        AbilityInput.keyPress(keyId, ability, owner, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.HOLD,
                0, press, ability.getAbilityId());
        EntityActionInstance action = stand.getCurStandAction();
        if (!press.isActionSuccess || action == null || action.ability != ability) {
            fixtureFailure = name + ": the press did not start it " + keys();
            return null;
        }
        for (int tick = 0; tick < 20 && !entityAbility.isActionHeld(action) && !action.isOver(); tick++) {
            action._tickAction();
        }
        // one held tick that passes the recheck
        tick();
        if (!held(action)) {
            fixtureFailure = name + " was not held after the press (phase " + action.getPhase() + ", messages "
                    + keys() + ")";
            return null;
        }
        clearMessages();
        return action;
    }

    boolean held(EntityActionInstance action) {
        return stand.getCurStandAction() == action && !action.isOver()
                && ((EntityActionAbility) action.ability).isActionHeld(action);
    }

    // One server tick of the running action, its key still down
    void tick() {
        EntityActionInstance action = stand.getCurStandAction();
        if (action != null && !action.isOver()) {
            action._tickAction();
        }
    }

    @Override
    public void close() {
        if (key >= 0) {
            AbilityInput.keyRelease(key, owner);
        }
        LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
        spawned.forEach(Entity::discard);
        around(D4CMarkerEntity.class).forEach(Entity::discard);
        around(D4CAnnihilationCloneEntity.class).forEach(Entity::discard);
        owner.getPersistentData().remove(ANNIHILATION_TARGET);
        if (power.isSummoned()) {
            D4CAddon.D4C.get().forceUnsummon(owner, power);
        }
        if (connected != null) {
            connected.close();
        }
        else {
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
        }
    }

    // the key of the first argument of each translatable message, "" where it has none or a plain one
    List<String> firstArgumentKeys() {
        List<String> found = new ArrayList<>();
        for (Component message : actionBarMessages()) {
            String key = "";
            if (message.getContents() instanceof TranslatableContents translatable
                    && translatable.getArgs().length > 0
                    && translatable.getArgs()[0] instanceof Component argument
                    && argument.getContents() instanceof TranslatableContents inner) {
                key = inner.getKey();
            }
            found.add(key);
        }
        return found;
    }

    private List<Component> actionBarMessages() {
        return connected != null
                ? connected.actionBarMessages()
                : ((RecordingPlayer) owner).messages;
    }

    List<String> keys() {
        List<String> found = new ArrayList<>();
        List<Component> messages = actionBarMessages();
        for (Component message : messages) {
            found.add(message.getContents() instanceof TranslatableContents translatable
                    ? translatable.getKey()
                    : message.getString());
        }
        return found;
    }

    /**
     * Whether a client copy's power has the scene's D4C out, as TrSetStandEntityPacket tells the user's client, without
     * handing the server's D4C to the copy.
     */
    void showStandOnClientCopy(StandPower clientPower, boolean out) {
        clientPower.setSummonedStand(!out ? null : new SummonedStand.BlankSummonedStand() {
            @Override
            public StandEntity getStandEntity() {
                return stand;
            }
        });
    }

    // what the server sent its player's client (a scene with a connection)
    <T extends CustomPacketPayload> List<T> payloads(Class<T> type) {
        helper.assertTrue(connected != null, "fixture: the scene has no connection");
        return connected.payloads(type);
    }

    void clearMessages() {
        if (connected != null) {
            connected.clear();
        }
        else {
            ((RecordingPlayer) owner).messages.clear();
        }
    }

    // Keeps the action-bar messages a FakePlayer would drop.
    private static final class RecordingPlayer extends FakePlayer {
        final List<Component> messages = new ArrayList<>();

        RecordingPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message);
        }
    }
}
