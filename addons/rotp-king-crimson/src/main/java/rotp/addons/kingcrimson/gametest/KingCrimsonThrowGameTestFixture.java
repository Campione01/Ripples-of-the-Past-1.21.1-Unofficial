package rotp.addons.kingcrimson.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.ability.KingCrimsonProjectileThrowAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonTripleProjectileThrowAbility;
import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/** Isolated, naturally ticking actors for the three throw regressions. */
final class KingCrimsonThrowGameTestFixture implements AutoCloseable {
    static final String SINGLE = "kingcrimson_projectile_throw";
    static final String TRIPLE = "kingcrimson_projectile_throw_tripple";
    static final short FIRST = 231, SECOND = 232;
    enum Mode { FINITE, CREATIVE, STAMINA_OFF, RESOLVE, NO_COOLDOWN }
    record Press(boolean conditions, boolean admitted, boolean buffered, EntityActionInstance action) {}
    record Spawn(Projectile entity, Entity owner, Vec3 position, Vec3 eye, Vec3 look,
            Vec3 velocity, float stamina, int handCount, ItemStack item) {}

    final GameTestHelper helper;
    final String id;
    final ServerLevel level;
    final Vec3 origin;
    final ModConfigSpec.BooleanValue staminaSetting = (ModConfigSpec.BooleanValue)
            JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings", "standStamina"));
    final boolean oldStamina = staminaSetting.get();
    final int oldCooldown = KingCrimsonConfig.PROJECTILE_THROW_COOLDOWN.get();
    final String oldAllowed = KingCrimsonConfig.ALLOWED_PROJECTILES.get();
    final List<Entity> owned = new ArrayList<>();
    final List<EmbeddedChannel> channels = new ArrayList<>();
    final List<Object> listeners = new ArrayList<>();
    final KingCrimsonChunkTickets chunks;
    final List<Spawn> spawns = new ArrayList<>();
    ServerPlayer user;
    StandPower power;
    StandEntity stand;
    Ability ability;
    EntityActionInstance action;
    Runnable duringWindup = () -> {};
    Runnable beforePerform = () -> {};
    boolean windupChanged, performing, performed, closed;
    float inputStamina, preStamina, postStamina, preGain;
    int inputUserAge, inputStandAge, preCooldown, postCooldown, preUserAge, postUserAge;
    int postStandAge, preAmmo, postAmmo;
    RuntimeException eventFailure;

    KingCrimsonThrowGameTestFixture(GameTestHelper helper, String id, Mode mode, float stamina, int cooldown) {
        this.helper = helper; this.id = id; level = helper.getLevel(); chunks = new KingCrimsonChunkTickets(level);
        origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 24, 1)));
        try {
            staminaSetting.set(mode != Mode.STAMINA_OFF);
            KingCrimsonConfig.PROJECTILE_THROW_COOLDOWN.set(cooldown);
            premise(JojoModConfig.getCommonConfigInstance(false).standStamina.get() == (mode != Mode.STAMINA_OFF), "Live stamina setting");
            force(origin); force(origin.add(-3, 0, -2));
            GameProfile profile = new GameProfile(UUID.randomUUID(), "kc-throw");
            user = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection); channels.add(channel);
            new ServerGamePacketListenerImpl(level.getServer(), connection, user, CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, PacketSendListener listener) {}
            };
            user.gameMode.changeGameModeForPlayer(mode == Mode.CREATIVE ? GameType.CREATIVE : GameType.SURVIVAL);
            user.setNoGravity(true); user.noPhysics = true;
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            owned.add(user); level.addNewPlayer(user);
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = mode == Mode.NO_COOLDOWN;
            power = PowerClass.STAND.attachGet(user);
            premise(StandPowerTransitions.insert(power, new StandInstance(KingCrimsonAddon.KING_CRIMSON.get())).status()
                    == StandPowerTransitions.Status.APPLIED, "Registered KC grant");
            power.skipProgression(); power.setResolveLevel(3);
            premise(KingCrimsonAddon.KING_CRIMSON.get().summon(user, power), "Registered KC summon");
            stand = power.getSummonedStandEntity(); premise(stand != null && stand.getUser() == user, "Real Stand owner");
            owned.add(stand); stand.setNoGravity(true); stand.noPhysics = true; stand.setCanFollowUser(false);
            stand.moveTo(origin.x - 3, origin.y, origin.z - 2, 137, 31); stand.setYHeadRot(137);
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(28);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            StandEntity.updateStandStatAttributes(stand, user);
            if (mode == Mode.RESOLVE) user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 1200, 0));
            else user.removeEffect(ModStatusEffects.RESOLVE);
            premise((ResolveModeEffect.getResolveEffectLvl(user) >= 0) == (mode == Mode.RESOLVE), "Resolve effect independent of unlock level3");
            premise(power.isStaminaInfinite() == (mode == Mode.CREATIVE || mode == Mode.STAMINA_OFF), "Finite/infinite resource premise");
            power.setStamina(stamina); stand.updateStrengthMultipliers();
            listen(EntityJoinLevelEvent.class, event -> {
                if (event.getLevel() == level && event.getEntity() instanceof Projectile projectile && projectile.getOwner() == user) {
                    owned.add(projectile);
                    ItemStack item = projectile instanceof ThrowableItemProjectile thrown ? thrown.getItem().copy() : ItemStack.EMPTY;
                    spawns.add(new Spawn(projectile, projectile.getOwner(), projectile.position(), user.getEyePosition(),
                            user.getLookAngle(), projectile.getDeltaMovement(), power.getStamina(), user.getMainHandItem().getCount(), item));
                    log("SPAWN " + id + " type=" + projectile.getType() + " pos=" + projectile.position()
                            + " eye=" + user.getEyePosition() + " look=" + user.getLookAngle() + " stamina=" + power.getStamina());
                }
            });
            listen(EntityTickEvent.Pre.class, event -> {
                if (event.getEntity() != stand || action == null || performed || eventFailure != null) return;
                try {
                    if (!windupChanged && action.getPhase() == ActionPhase.WINDUP && stand.tickCount > inputStandAge) {
                        duringWindup.run(); windupChanged = true;
                    }
                    if (action.getPhase() == ActionPhase.PERFORM && action.getPhaseTick() < 1) {
                        beforePerform.run(); performing = true;
                        preStamina = power.getStamina(); preGain = power.getStaminaTickGain();
                        preCooldown = power.getAbilityCooldown(ability.name()); preUserAge = user.tickCount;
                        preAmmo = user.getMainHandItem().getCount();
                    }
                } catch (RuntimeException error) { eventFailure = error; }
            });
            listen(EntityTickEvent.Post.class, event -> {
                if (event.getEntity() != stand || !performing || performed || eventFailure != null) return;
                postStamina = power.getStamina(); postCooldown = power.getAbilityCooldown(ability.name());
                postUserAge = user.tickCount; postStandAge = stand.tickCount; postAmmo = user.getMainHandItem().getCount();
                performed = true;
                log("PERFORM " + id + " ability=" + action.ability.getAbilityId() + " stamina=" + preStamina + "->" + postStamina + " gain=" + preGain
                        + " cooldown=" + preCooldown + "->" + postCooldown + " userTicks=" + (postUserAge - inputUserAge)
                        + " standTicks=" + (postStandAge - inputStandAge) + " ammo=" + preAmmo + "->" + postAmmo
                        + " phase=" + action.getPhase() + " spawns=" + spawns.size());
            });
        } catch (RuntimeException | Error error) { close(); throw error; }
    }

    Press press(String name, short key) {
        return press(name, key, false);
    }
    Press press(String name, short key, boolean canBuffer) {
        ability = power.getAbility(name); premise(ability != null, "Registered ability " + name);
        var selected = power.updateAvailableMoves().getContextVariationContainer(ability);
        premise(selected != null && selected.ability == ability, "Unchanged registered hotbar variant " + name);
        boolean conditions = AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK);
        BufferingState buffer = canBuffer ? BufferingState.clickCanBuffer() : BufferingState.clickOnly();
        inputStamina = power.getStamina(); inputUserAge = user.tickCount; inputStandAge = stand.tickCount;
        if (conditions) AbilityInput.keyPress(key, ability, user, null, InputMethod.CLICK, 0, buffer, ability.getAbilityId());
        EntityActionInstance admitted = buffer.isActionSuccess ? stand.getCurStandAction() : null;
        if (admitted != null) {
            action = admitted; identity(helper, action.getClass());
            premise(action.ability == ability, "Actual admitted action identity");
        }
        log("INPUT " + id + " ability=" + name + " conditions=" + conditions + " success=" + buffer.isActionSuccess
                + " buffered=" + buffer.shouldBuffer + " stamina=" + inputStamina + " regen=" + power.getStaminaTickGain()
                + " resolve=" + ResolveModeEffect.getResolveEffectLvl(user) + " cooldown=" + power.getAbilityCooldown(name)
                + " phase=" + (admitted == null ? "none" : admitted.getPhase()));
        return new Press(conditions, buffer.isActionSuccess, buffer.shouldBuffer, admitted);
    }

    void aim(float yaw, float pitch) { user.setYRot(yaw); user.setYHeadRot(yaw); user.setXRot(pitch); }
    void hand(ItemStack stack) { user.setItemInHand(InteractionHand.MAIN_HAND, stack); }
    void offhand(ItemStack stack) { user.setItemInHand(InteractionHand.OFF_HAND, stack); }
    void force(Vec3 position) { chunks.force(position); }
    <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
        listeners.add(listener); NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, type, listener);
    }
    void premise(boolean value, String message) { helper.assertTrue(value, "KC-THROW PREMISE " + id + " " + message); }
    void checkPayment(Checks checks, boolean debit) {
        premise(performed && postStandAge > inputStandAge && preUserAge == postUserAge, "Natural actor perform with no interleaved user regen");
        premise(Float.isFinite(preGain) && preGain >= 0 && preGain < 20, "Bounded measured regeneration");
        float expected = !debit || power.isStaminaInfinite() ? preStamina : Math.max(0, preStamina - 120);
        checks.check(near(postStamina, expected), id + " debit expected=" + expected + " actual=" + postStamina);
    }
    void checkSpawns(Checks checks, int expected, Class<? extends Projectile> type) {
        checks.check(spawns.size() == expected, id + " emitted=" + spawns.size() + " expected=" + expected);
        for (int i = 0; i < spawns.size(); i++) {
            Spawn spawn = spawns.get(i);
            Vec3 offset = expected <= 1 || i == 1 ? Vec3.ZERO : i == 0 ? new Vec3(-0.2, 0, -0.2) : new Vec3(0.2, 0, 0.2);
            Vec3 error = spawn.velocity.scale(1.0 / 3).subtract(spawn.look.normalize());
            checks.check(type.isInstance(spawn.entity) && spawn.owner == user && spawn.entity.isAddedToLevel(), id + " actual type/owner/level " + i);
            checks.check(near(spawn.position, spawn.eye.add(offset)) && spawn.eye.distanceTo(stand.position()) > 2,
                    id + " live player eye / world-axis offset " + i);
            checks.check(Math.abs(error.x) <= 0.017228 && Math.abs(error.y) <= 0.017228 && Math.abs(error.z) <= 0.017228,
                    id + " live look / vanilla speed3 inaccuracy1 envelope " + i);
            checks.check(near(spawn.stamina, preStamina), id + " spawn precedes payment " + i);
        }
    }
    static boolean near(double a, double b) { return Math.abs(a - b) < 0.0001; }
    static boolean near(Vec3 a, Vec3 b) { return a.distanceToSqr(b) < 0.00000001; }
    static void log(String text) { System.out.println("KC-THROW " + text); }
    static void identity(GameTestHelper helper, Class<?> test) {
        for (Class<?> type : new Class<?>[] {StandPower.class, StandEntity.class, AbilityInput.class, ActionInputBuffer.class, EntityActionInstance.class,
                StandAbilityStamina.class, KingCrimsonProjectileThrowAbility.class, KingCrimsonTripleProjectileThrowAbility.class,
                KingCrimsonThrowGameTestFixture.class, test}) {
            try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                helper.assertTrue(input != null, "Native class resource " + type.getName());
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                String expected = System.getProperty("kc.throw.expected." + type.getName());
                var source = type.getProtectionDomain().getCodeSource();
                helper.assertTrue(source != null && (!Boolean.getBoolean("kc.throw.bound") || expected != null)
                        && (expected == null || expected.equalsIgnoreCase(hash)), "Native class binding " + type.getName());
                log("LOADED " + type.getName() + " source=" + source.getLocation() + " sha256=" + hash);
            } catch (Exception error) { throw new IllegalStateException("Native throw identity", error); }
        }
    }
    static final class Checks {
        int count;
        final List<String> failures = new ArrayList<>();
        void check(boolean value, String text) { count++; log((value ? "PASS " : "FAIL ") + text); if (!value) failures.add(text); }
        void finish(GameTestHelper helper, String id, int scenes) {
            log(id + " SUMMARY scenes=" + scenes + " checks=" + count + " failures=" + failures.size() + " details=" + failures);
            helper.assertTrue(failures.isEmpty(), id + " behavioral failures=" + failures.size() + "; see SUMMARY"); helper.succeed();
        }
    }
    abstract static class Suite implements GameTestListener {
        final GameTestHelper helper;
        final Checks checks = new Checks();
        final int scenes;
        final String id;
        KingCrimsonThrowGameTestFixture scene;
        int index, ticks;
        boolean done, closed;
        Suite(GameTestHelper helper, String id, int scenes) { this.helper = helper; this.id = id; this.scenes = scenes; }
        abstract void prepare(int index);
        abstract boolean verify();
        abstract void release();
        void start() { helper.testInfo.addListener(this); helper.onEachTick(this::tick); }
        void tick() {
            if (closed) return;
            try {
                if (scene != null && scene.eventFailure != null) throw scene.eventFailure;
                if (scene != null && (done || verify())) { scene.close(); scene = null; done = false; }
                if (scene == null) {
                    if (index == scenes) { close(); checks.finish(helper, id, scenes); return; }
                    prepare(index++); ticks = 0;
                }
                helper.assertTrue(++ticks < 240, "Natural throw scene deadline " + scene.id);
            } catch (RuntimeException | Error error) { close(); throw error; }
        }
        void close() { if (closed) return; closed = true; try { if (scene != null) scene.close(); } finally { release(); } }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        List<Runnable> cleanup = new ArrayList<>();
        listeners.forEach(listener -> cleanup.add(() -> NeoForge.EVENT_BUS.unregister(listener)));
        if (user != null) {
            cleanup.add(() -> AbilityInput.keyRelease(FIRST, user)); cleanup.add(() -> AbilityInput.keyRelease(SECOND, user));
            cleanup.add(() -> user.getInventory().clearContent());
            cleanup.add(() -> { if (user.hasData(ModDataAttachmentTypes.CONTROLLER)) user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling(); });
        }
        if (stand != null) cleanup.add(() -> LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC));
        if (power != null && user != null) cleanup.add(() -> { if (power.isSummoned()) KingCrimsonAddon.KING_CRIMSON.get().forceUnsummon(user, power); });
        owned.forEach(entity -> cleanup.add(entity::discard));
        channels.forEach(channel -> cleanup.add(() -> channel.finishAndReleaseAll()));
        cleanup.add(chunks::release);
        cleanup.add(() -> staminaSetting.set(oldStamina));
        cleanup.add(() -> KingCrimsonConfig.PROJECTILE_THROW_COOLDOWN.set(oldCooldown));
        cleanup.add(() -> KingCrimsonConfig.ALLOWED_PROJECTILES.set(oldAllowed));
        RuntimeException failure = null;
        for (Runnable step : cleanup) {
            try { step.run(); } catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        listeners.clear();
        log("CLEANUP " + id + " actorsRemoved=" + owned.stream().allMatch(Entity::isRemoved) + " listeners=0"
                + " restoredStamina=" + staminaSetting.get() + " restoredCooldown=" + KingCrimsonConfig.projectileCooldownTicks()
                + " cleanupFailure=" + (failure != null));
        if (failure != null) throw failure;
    }
}
