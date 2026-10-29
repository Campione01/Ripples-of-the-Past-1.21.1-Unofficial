package rotp.addons.kingcrimson.gametest;

import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.ability.KingCrimsonGroundPunchAbility;
import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
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
import rotp.core.subsystems.target.HitResultUtil;

/** Local fixture for the three KC ground-punch regressions, with real input and bounded action ticks. */
final class KingCrimsonGroundGameTestFixture implements AutoCloseable {
    static final short INPUT = 191;
    final GameTestHelper helper;
    final String id;
    final ServerLevel level;
    final Vec3 origin;
    final ModConfigSpec.DoubleValue multiplier = (ModConfigSpec.DoubleValue) JojoModConfig.COMMON_SPEC.getValues()
            .get(List.of("Stand settings", "standPowerMultiplier"));
    final double previousMultiplier;
    final List<Entity> owned = new ArrayList<>();
    final List<Object> listeners = new ArrayList<>();
    final List<Hit> hits = new ArrayList<>();
    final List<Spawn> spawns = new ArrayList<>();
    final Deque<Entity> hurting = new ArrayDeque<>();
    final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    final Set<ChunkPos> chunks = new LinkedHashSet<>();
    final UUID ticketId = UUID.randomUUID();
    ServerPlayer user;
    StandPower power;
    StandType type;
    StandEntity stand;
    EmbeddedChannel channel;
    boolean closed;

    static final class Hit {
        final Entity target;
        final DamageSource source;
        final float amount;
        final Vec3 before;
        final boolean grounded;
        Vec3 after;
        Boolean result;
        Hit(Entity target, DamageSource source, float amount) {
            this.target = target; this.source = source; this.amount = amount;
            before = target.getDeltaMovement();
            grounded = target instanceof LivingEntity living && living.onGround();
        }
    }
    record Spawn(Entity entity, Entity duringHurt, Vec3 initialVelocity) {}

    KingCrimsonGroundGameTestFixture(GameTestHelper helper, String id, double value) {
        this.helper = helper; this.id = id; level = helper.getLevel();
        previousMultiplier = multiplier.get();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        origin = new Vec3(chunk.getMiddleBlockX() + 0.5, helper.absolutePos(BlockPos.ZERO).getY() + 8,
                chunk.getMiddleBlockZ() + 0.5);
        try {
            multiplier.set(value);
            premise(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get() == value, "Live M binding");
            forceChunk(chunk);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 4; z++)
                block(BlockPos.containing(origin).offset(x, -1, z), Blocks.STONE.defaultBlockState());
            listen(EntityJoinLevelEvent.class, event -> {
                Entity entity = event.getEntity();
                if (event.getLevel() == level && !hurting.isEmpty()
                        && (entity instanceof ItemEntity || entity instanceof ExperienceOrb)) {
                    owned.add(entity);
                    spawns.add(new Spawn(entity, hurting.peek(), entity.getDeltaMovement()));
                }
            });
            GameProfile profile = new GameProfile(UUID.randomUUID(), "kc-ground-test");
            user = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            new ServerGamePacketListenerImpl(level.getServer(), connection, user, CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, PacketSendListener listener) {}
            };
            user.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            user.setNoGravity(true); user.noPhysics = true;
            user.moveTo(origin.x, origin.y, origin.z, 0, 0); user.setYHeadRot(0);
            owned.add(user); level.addNewPlayer(user);
            user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); user.setHealth(200);
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            power = PowerClass.STAND.attachGet(user);
            type = KingCrimsonAddon.KING_CRIMSON.get();
            premise(StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
                    "Registered KC grant");
            power.skipProgression(); power.setResolveLevel(4);
            premise(type.summon(user, power), "Registered KC full summon");
            stand = power.getSummonedStandEntity();
            premise(stand != null && !stand.isArmsOnlyMode(), "Real full KC body");
            owned.add(stand); stand.setNoGravity(true); stand.setCanFollowUser(false);
            stand.moveTo(origin.x, origin.y, origin.z, 0, 0); stand.setYHeadRot(0);
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1);
            stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(16);
            power.setStamina(power.getMaxStamina());
            log("READY " + id + " M=" + value + " user=" + user.getId() + " stand=" + stand.getId());
        }
        catch (RuntimeException | Error error) { close(); throw error; }
    }

    <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
        listeners.add(listener);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, type, listener);
    }
    void forceChunk(ChunkPos chunk) {
        if (chunks.add(chunk)) {
            level.getChunkSource().addRegionTicket(KingCrimsonChunkTickets.TICKET, chunk,
                    KingCrimsonChunkTickets.TICKET_RADIUS, ticketId);
            level.getChunk(chunk.x, chunk.z);
        }
    }
    void block(BlockPos pos, BlockState state) {
        blocks.putIfAbsent(pos, level.getBlockState(pos)); level.setBlockAndUpdate(pos, state);
    }
    <T extends Entity> T add(T entity, Vec3 pos) {
        owned.add(entity); forceChunk(new ChunkPos(BlockPos.containing(pos)));
        entity.setNoGravity(true); entity.noPhysics = true; entity.moveTo(pos.x, pos.y, pos.z, 0, 0);
        premise(level.addFreshEntity(entity), "Native target added"); return entity;
    }
    ObservedCow cow(Vec3 position, float health) {
        ObservedCow cow = new ObservedCow(this); cow.setNoAi(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health); cow.setHealth(health);
        return add(cow, position);
    }
    ObservedBoat boat(Vec3 position) { return add(new ObservedBoat(this), position); }

    void aimUserAt(Vec3 point) { user.lookAt(EntityAnchorArgument.Anchor.EYES, point); }
    /**
     * 1.16 StandEntity.precisionRayTrace from the user: the entity's box grown by the Stand's precision misses the user's
     * aim within the user's reach, so a strike that aims again does not take it.
     */
    boolean offUserPrecisionRay(Entity entity) {
        Vec3 eyes = user.getEyePosition();
        Vec3 end = eyes.add(user.getLookAngle().normalize().scale(StandEntityPunchAbility.userAimReach(user, stand)));
        AABB box = HitResultUtil.standPrecisionTargetHitbox(entity.getBoundingBox().inflate(entity.getPickRadius()),
                stand.getPrecision());
        return !box.contains(eyes) && box.clip(eyes, end).isEmpty();
    }

    // whether the user's own look ray, within the user's reach, meets that block
    boolean userLookMeets(BlockPos pos) {
        Vec3 eyes = user.getEyePosition();
        Vec3 end = eyes.add(user.getLookAngle().normalize().scale(StandEntityPunchAbility.userAimReach(user, stand)));
        var hit = level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, user));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    EntityActionInstance cast(Entity target, String baseName, String expectedName) {
        return cast(new ActionTarget(target), baseName, expectedName);
    }
    EntityActionInstance cast(ActionTarget target, String baseName, String expectedName) {
        EntityActionInstance action = press(target, baseName, expectedName);
        int steps = 0;
        while (action.punchedTarget == null && !action.isOver() && steps++ < 100) action._tickAction();
        premise(action.punchedTarget != null, "Native perform reached within100 action ticks");
        if (target.getMainEntity() != null)
            premise(action.punchedTarget.getMainEntity() == target.getMainEntity(), "Exact primary entity admitted");
        log("CAST " + id + " base=" + baseName + " selected=" + expectedName + " actionTicks=" + steps);
        return action;
    }
    EntityActionInstance press(Entity target, String baseName, String expectedName) {
        return press(new ActionTarget(target), baseName, expectedName);
    }
    /** The real registered input with the finisher meter at 0.75, without ticking the installed action. */
    EntityActionInstance press(ActionTarget target, String baseName, String expectedName) {
        stand.setFinisherMeter(0.75F);
        LivingComponentAction.getComponent(user).entityAim.setTarget(target);
        LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
        // 1.16 kept no pressed entity that is not living (StandEntityAction.checkStandTarget) and the strike aimed again
        // with the precision ray from the user's eyes: the user looks at a boat, as a client sending it as its crosshair
        Entity pressed = target.getMainEntity();
        if (pressed != null && !(pressed instanceof LivingEntity)) aimUserAt(pressed.getBoundingBox().getCenter());
        Ability base = power.getAbility(baseName);
        premise(base != null, "Registered input base " + baseName);
        var selected = power.updateAvailableMoves().getContextVariationContainer(base);
        premise(selected != null && selected.ability.name().equals(expectedName)
                && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK), "Actual input admission " + expectedName);
        BufferingState buffer = BufferingState.clickOnly();
        var held = AbilityInput.keyPress(INPUT, selected.ability, user, null, InputMethod.CLICK, 0, buffer, base.getAbilityId());
        EntityActionInstance action = held != null && held.action instanceof EntityActionInstance nativeAction
                ? nativeAction : stand.getCurStandAction();
        premise(buffer.isActionSuccess && action != null && action.ability == selected.ability,
                "Registered input installed its actual action");
        return action;
    }
    List<Hit> hits(Entity entity) { return hits.stream().filter(hit -> hit.target == entity).toList(); }
    Hit begin(Entity entity, DamageSource source, float amount) {
        Hit hit = new Hit(entity, source, amount); hits.add(hit); hurting.push(entity); return hit;
    }
    void end(Hit hit, boolean result) {
        hit.after = hit.target.getDeltaMovement(); hit.result = result; hurting.pop();
        log("HURT " + id + " target=" + hit.target.getId() + " amount=" + hit.amount + " result=" + result
                + " source=" + hit.source.getMsgId() + " before=" + hit.before + " after=" + hit.after);
    }
    void premise(boolean passed, String message) { helper.assertTrue(passed, "KC-GROUND PREMISE " + id + " " + message); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        List<Runnable> cleanup = new ArrayList<>();
        listeners.forEach(listener -> cleanup.add(() -> NeoForge.EVENT_BUS.unregister(listener)));
        if (user != null) cleanup.add(() -> AbilityInput.keyRelease(INPUT, user));
        if (stand != null) cleanup.add(() -> LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC));
        if (power != null && type != null) cleanup.add(() -> { if (power.isSummoned()) type.forceUnsummon(user, power); });
        owned.forEach(entity -> cleanup.add(entity::discard));
        blocks.forEach((pos, state) -> cleanup.add(() -> level.setBlockAndUpdate(pos, state)));
        chunks.forEach(chunk -> cleanup.add(() -> level.getChunkSource().removeRegionTicket(
                KingCrimsonChunkTickets.TICKET, chunk, KingCrimsonChunkTickets.TICKET_RADIUS, ticketId)));
        if (channel != null) cleanup.add(() -> channel.finishAndReleaseAll());
        cleanup.add(() -> multiplier.set(previousMultiplier));
        for (Runnable step : cleanup) {
            try { step.run(); }
            catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        listeners.clear();
        log("CLEANUP " + id + " actorsRemoved=" + owned.stream().allMatch(Entity::isRemoved)
                + " listeners=0 restoredM=" + multiplier.get() + " cleanupFailure=" + (failure != null));
        if (failure != null) throw failure;
    }

    static final class ObservedCow extends Cow {
        final KingCrimsonGroundGameTestFixture scene;
        ObservedCow(KingCrimsonGroundGameTestFixture scene) { super(EntityType.COW, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            Hit hit = scene.begin(this, source, amount); boolean result = super.hurt(source, amount); scene.end(hit, result); return result;
        }
    }
    static final class ObservedBoat extends Boat {
        final KingCrimsonGroundGameTestFixture scene;
        ObservedBoat(KingCrimsonGroundGameTestFixture scene) { super(EntityType.BOAT, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            Hit hit = scene.begin(this, source, amount); boolean result = super.hurt(source, amount); scene.end(hit, result); return result;
        }
    }
    static final class Checks {
        int count;
        final List<String> failures = new ArrayList<>();
        void check(boolean passed, String detail) {
            count++; log((passed ? "PASS " : "FAIL ") + detail); if (!passed) failures.add(detail);
        }
        void finish(GameTestHelper helper, String id, int scenes) {
            log(id + " SUMMARY scenes=" + scenes + " checks=" + count + " failures=" + failures.size() + " details=" + failures);
            helper.assertTrue(failures.isEmpty(), id + " behavioral failures=" + failures.size() + "; see SUMMARY"); helper.succeed();
        }
    }
    static void identity(GameTestHelper helper, Class<?> fixture) {
        try {
            Class<?> strike = KingCrimsonGroundPunchAbility.class.getSuperclass();
            Class<?> strikeAction = null;
            for (Class<?> nested : strike.getDeclaredClasses()) {
                if (nested.getSimpleName().equals("KingCrimsonStrikeAction")) {
                    strikeAction = nested;
                }
            }
            helper.assertTrue(strikeAction != null, "Missing declared native KC strike action");
            for (Class<?> type : new Class<?>[] {StandEntity.class, EntityActionInstance.class, DamageSourceModified.class,
                    strike, strikeAction,
                    KingCrimsonGroundPunchAbility.class, KingCrimsonGroundGameTestFixture.class, fixture}) {
                try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                    helper.assertTrue(input != null, "Missing native class bytes " + type.getName());
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                    String expected = System.getProperty("kc.ground.expected." + type.getName());
                    var source = type.getProtectionDomain().getCodeSource();
                    helper.assertTrue(source != null && (!Boolean.getBoolean("kc.ground.bound") || expected != null)
                            && (expected == null || hash.equalsIgnoreCase(expected)), "Native KC class binding " + type.getName());
                    log("LOADED " + type.getName() + " source=" + source.getLocation() + " sha256=" + hash);
                }
            }
        }
        catch (Exception error) { throw new IllegalStateException("KC native binding failed", error); }
    }
    static boolean near(double a, double b) { return Math.abs(a - b) < 0.0002; }
    static boolean near(Vec3 a, Vec3 b) { return a.distanceTo(b) < 0.0002; }
    static void log(String value) { JojoMod.getLogger().info("KC-GROUND " + value); }
}
