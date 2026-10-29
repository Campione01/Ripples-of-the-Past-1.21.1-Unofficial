package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

/** Natural-tick actors owned only by the two ground-admission regressions. */
final class KingCrimsonGroundAdmissionFixture implements AutoCloseable {
    static final short ATTACK = 201, GUARD = 202, CONTROL = 203;
    final GameTestHelper helper;
    final String id;
    final ServerLevel level;
    final Vec3 origin;
    final ModConfigSpec.DoubleValue multiplier = (ModConfigSpec.DoubleValue) JojoModConfig.COMMON_SPEC.getValues()
            .get(List.of("Stand settings", "standPowerMultiplier"));
    final double oldMultiplier;
    final List<Actor> actors = new ArrayList<>();
    final List<Entity> owned = new ArrayList<>();
    final List<EmbeddedChannel> channels = new ArrayList<>();
    final List<Object> listeners = new ArrayList<>();
    final KingCrimsonChunkTickets chunks;
    final List<Hit> hits = new ArrayList<>();
    boolean closed;
    record Actor(ServerPlayer user, StandPower power, StandType type, StandEntity stand) {}
    static final class Hit {
        final Entity target;
        final DamageSource source;
        final float amount;
        Vec3 after;
        boolean result;
        Hit(Entity target, DamageSource source, float amount) { this.target = target; this.source = source; this.amount = amount; }
    }
    KingCrimsonGroundAdmissionFixture(GameTestHelper helper, String id, double value) {
        this.helper = helper; this.id = id; level = helper.getLevel(); chunks = new KingCrimsonChunkTickets(level);
        oldMultiplier = multiplier.get();
        // Own cell coordinates above structure cleanup bounds, never a shared chunk centre.
        origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 24, 1)));
        try {
            multiplier.set(value);
            premise(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get() == value, "Live M binding");
        }
        catch (RuntimeException | Error error) { multiplier.set(oldMultiplier); throw error; }
    }
    void force(Vec3 position) { chunks.force(position); }
    Actor actor(Vec3 userPosition, Vec3 bodyPosition, float yaw, double raw, double speed) {
        force(userPosition); force(bodyPosition);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "kc-admission");
        ServerPlayer user = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection); channels.add(channel);
        new ServerGamePacketListenerImpl(level.getServer(), connection, user, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        user.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        user.setNoGravity(true); user.noPhysics = true;
        user.moveTo(userPosition.x, userPosition.y, userPosition.z, yaw, 0); user.setYHeadRot(yaw);
        owned.add(user); level.addNewPlayer(user);
        user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); user.setHealth(200);
        user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
        StandPower power = PowerClass.STAND.attachGet(user);
        StandType type = KingCrimsonAddon.KING_CRIMSON.get();
        premise(StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
                "Registered KC actor grant");
        power.skipProgression(); power.setResolveLevel(4);
        premise(type.summon(user, power), "Registered KC summon");
        StandEntity stand = power.getSummonedStandEntity();
        premise(stand != null && stand.getUser() == user, "Actual body and owner");
        Actor actor = new Actor(user, power, type, stand); actors.add(actor); owned.add(stand);
        stand.setNoGravity(true); stand.noPhysics = true; stand.setCanFollowUser(false);
        stand.moveTo(bodyPosition.x, bodyPosition.y, bodyPosition.z, yaw, 0); stand.setYHeadRot(yaw);
        stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
        user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(raw);
        user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);
        user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
        StandEntity.updateStandStatAttributes(stand, user);
        stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        power.setStamina(power.getMaxStamina()); stand.updateStrengthMultipliers();
        return actor;
    }
    EntityActionInstance press(Actor actor, Entity target, String baseName, String expected, InputMethod method, short key) {
        if (target != null) {
            LivingComponentAction.getComponent(actor.user).entityAim.setTarget(new ActionTarget(target));
            LivingComponentAction.getComponent(actor.stand).entityAim.setTarget(new ActionTarget(target));
            // 1.16 kept no pressed entity that is not living (StandEntityAction.checkStandTarget) and the strike aimed
            // again with the precision ray from the user's eyes: the user looks at a boat
            if (!(target instanceof LivingEntity)) {
                actor.user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            }
        }
        Ability base = actor.power.getAbility(baseName);
        premise(base != null, "Registered base " + baseName);
        var selected = actor.power.updateAvailableMoves().getContextVariationContainer(base);
        premise(selected != null && selected.ability.name().equals(expected)
                && AbilityInput.withConditionCheck(selected, actor.user, method), "Real input admission " + expected);
        BufferingState buffer = BufferingState.clickOnly();
        var input = AbilityInput.keyPress(key, selected.ability, actor.user, null, method, 0, buffer, base.getAbilityId());
        EntityActionInstance action = input != null && input.action instanceof EntityActionInstance value
                ? value : actor.stand.getCurStandAction();
        premise(buffer.isActionSuccess, "Actual input succeeded " + expected);
        log("PRESS " + id + " selected=" + expected + " body=" + actor.stand.getId() + " raw=" + actor.stand.getAttackDamage()
                + " bodyAge=" + actor.stand.tickCount + " origin=" + origin);
        return action;
    }
    <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
        listeners.add(listener); NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, type, listener);
    }
    <T extends Entity> T add(T entity, Vec3 position) {
        force(position); owned.add(entity); entity.setNoGravity(true); entity.noPhysics = true;
        entity.moveTo(position.x, position.y, position.z, 0, 0);
        premise(level.addFreshEntity(entity), "Actual target added"); return entity;
    }
    ObservedBoat boat(Vec3 position) { return add(new ObservedBoat(this), position); }
    ObservedCow cow(Vec3 position) {
        ObservedCow cow = new ObservedCow(this); cow.setNoAi(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); cow.setHealth(200); return add(cow, position);
    }
    List<Hit> hits(Entity target) { return hits.stream().filter(hit -> hit.target == target).toList(); }
    Hit begin(Entity target, DamageSource source, float amount) { Hit hit = new Hit(target, source, amount); hits.add(hit); return hit; }
    void end(Hit hit, boolean result) {
        hit.after = hit.target.getDeltaMovement(); hit.result = result;
        log("HURT " + id + " target=" + hit.target.getId() + " amount=" + hit.amount + " result=" + result);
    }
    void premise(boolean value, String message) { helper.assertTrue(value, "KC-R1 PREMISE " + id + " " + message); }
    static void log(String message) { KingCrimsonGroundGameTestFixture.log(message); }
    static final class ObservedBoat extends Boat {
        final KingCrimsonGroundAdmissionFixture scene;
        ObservedBoat(KingCrimsonGroundAdmissionFixture scene) { super(EntityType.BOAT, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            Hit hit = scene.begin(this, source, amount); boolean result = super.hurt(source, amount); scene.end(hit, result); return result;
        }
    }
    static final class ObservedCow extends Cow {
        final KingCrimsonGroundAdmissionFixture scene;
        final List<Vec3> pushes = new ArrayList<>();
        ObservedCow(KingCrimsonGroundAdmissionFixture scene) { super(EntityType.COW, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            Hit hit = scene.begin(this, source, amount); boolean result = super.hurt(source, amount); scene.end(hit, result); return result;
        }
        @Override public void push(double x, double y, double z) {
            pushes.add(new Vec3(x, y, z)); super.push(x, y, z);
        }
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        List<Runnable> cleanup = new ArrayList<>();
        listeners.forEach(listener -> cleanup.add(() -> NeoForge.EVENT_BUS.unregister(listener)));
        for (Actor actor : actors) {
            for (short key : new short[] {ATTACK, GUARD, CONTROL}) cleanup.add(() -> AbilityInput.keyRelease(key, actor.user));
            cleanup.add(() -> LivingComponentAction.getComponent(actor.stand).setAction(null, SyncType.NO_SYNC));
            cleanup.add(() -> { if (actor.user.hasData(ModDataAttachmentTypes.CONTROLLER))
                actor.user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling(); });
            cleanup.add(() -> { if (actor.power.isSummoned()) actor.type.forceUnsummon(actor.user, actor.power); });
        }
        owned.forEach(entity -> cleanup.add(entity::discard));
        channels.forEach(channel -> cleanup.add(() -> channel.finishAndReleaseAll()));
        cleanup.add(chunks::release);
        cleanup.add(() -> multiplier.set(oldMultiplier));
        RuntimeException failure = null;
        for (Runnable step : cleanup) {
            try { step.run(); }
            catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        listeners.clear();
        log("CLEANUP " + id + " actorsRemoved=" + owned.stream().allMatch(Entity::isRemoved)
                + " listeners=0 restoredM=" + multiplier.get() + " cleanupFailure=" + (failure != null));
        if (failure != null) throw failure;
    }
}
