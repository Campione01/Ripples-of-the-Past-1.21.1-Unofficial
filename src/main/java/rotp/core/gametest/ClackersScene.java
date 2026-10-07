package rotp.core.gametest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * Scene shared by the Clackers tests: owned blocks, mock Hamon throwers that use the registered item, and one
 * frame before and after every natural tick of each Clackers in the room.
 */
final class ClackersScene implements GameTestListener {
    static final double EPS = 1.0E-7D;
    // 1.16.5 AbstractArrow: float literals promoted to double vector arithmetic.
    static final double DRAG = (double) 0.99F;
    static final double GRAVITY = (double) 0.05F;

    record Frame(long time, int age, Vec3 pos, Vec3 delta, float xRot, float yRot, boolean grounded,
            boolean removed, @Nullable Entity.RemovalReason reason, boolean impulse, CompoundTag tag) {
        int life() {
            return tag.getShort("life");
        }

        boolean boomerang() {
            return tag.getBoolean("BoomerangHit");
        }
    }

    record Impact(HitResult hit, @Nullable Entity entity, @Nullable AABB entityBox, @Nullable Vec3 entityEye) {}

    record Step(Frame pre, Frame post, List<Impact> impacts) {
        @Nullable
        Impact entityImpact() {
            return impacts.stream().filter(impact -> impact.entity() != null).findFirst().orElse(null);
        }

        @Nullable
        BlockHitResult blockImpact() {
            return impacts.stream().filter(impact -> impact.hit() instanceof BlockHitResult)
                    .map(impact -> (BlockHitResult) impact.hit()).findFirst().orElse(null);
        }
    }

    static final class Track {
        final ClackersEntity shot;
        final List<Step> steps = new ArrayList<>();
        private Frame pending;
        private List<Impact> impacts = new ArrayList<>();

        private Track(ClackersEntity shot) {
            this.shot = shot;
        }

        @Nullable
        Step contact() {
            return steps.stream().filter(step -> step.entityImpact() != null).findFirst().orElse(null);
        }

        @Nullable
        Step landing() {
            return steps.stream().filter(step -> !step.pre().grounded() && step.post().grounded()).findFirst().orElse(null);
        }

        /** Completed ticks after the landing tick, or -1 while airborne. */
        int groundTicks() {
            Step landing = landing();
            return landing == null ? -1 : steps.size() - 1 - steps.indexOf(landing);
        }
    }

    private record Stage(String what, BooleanSupplier body) {}

    final GameTestHelper helper;
    final ServerLevel level;
    final AABB room;
    final List<ClackersEntity> shots = new ArrayList<>();
    private final String name;
    private final int deadline;
    private final int x;
    private final int y;
    private final int z;
    private final Map<ClackersEntity, Track> tracks = new IdentityHashMap<>();
    private final Map<Player, int[]> playerTicks = new IdentityHashMap<>();
    private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
    private final List<Object> listeners = new ArrayList<>();
    private final List<Entity> owned = new ArrayList<>();
    private final List<Runnable> powerResets = new ArrayList<>();
    private final List<Stage> stages = new ArrayList<>();
    private int stage;
    private Throwable observerFailure;
    private boolean closed;

    private ClackersScene(GameTestHelper helper, String name, int deadline) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.name = name;
        this.deadline = deadline;
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(origin);
        this.x = chunk.getMinBlockX();
        this.y = origin.getY() + 32;
        this.z = chunk.getMinBlockZ();
        this.room = new AABB(x + 2, y - 4, z + 1, x + 14, y + 6, z + 15);
    }

    /** Builds the scene, lets the script queue its stages and runs them one helper tick at a time. */
    static void start(GameTestHelper helper, String name, int deadline, Consumer<ClackersScene> script) {
        ClackersScene scene = new ClackersScene(helper, name, deadline);
        helper.testInfo.addListener(scene);
        try {
            scene.prepare();
            script.accept(scene);
            helper.runAfterDelay(1, scene::poll);
        }
        catch (RuntimeException | Error error) {
            scene.close();
            throw error;
        }
    }

    private void prepare() {
        check(room.maxY < level.getMaxBuildHeight(), "room exceeds the build height");
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                BlockPos.containing(room.maxX - 1.0D, room.maxY - 1.0D, room.maxZ - 1.0D))) {
            check(level.isEmptyBlock(pos) && level.isPositionEntityTicking(pos), "room cell is not empty and ticking: " + pos);
        }
        check(level.getEntities((Entity) null, room).isEmpty(), "foreign entity in the room");
        listen(EntityJoinLevelEvent.class, event -> {
            Entity entity = event.getEntity();
            if (event.getLevel() != level || !room.contains(entity.position())) return;
            if (entity instanceof ClackersEntity shot && !tracks.containsKey(shot)) {
                shots.add(shot);
                tracks.put(shot, new Track(shot));
                owned.add(shot);
            }
            else if (entity instanceof ItemEntity) {
                owned.add(entity);
            }
        });
        listen(EntityTickEvent.Pre.class, event -> {
            Track track = event.getEntity() instanceof ClackersEntity shot ? tracks.get(shot) : null;
            if (track != null) {
                expect(track.pending == null, "unpaired Clackers tick");
                track.pending = frame(track.shot);
                track.impacts = new ArrayList<>();
            }
        });
        listen(EntityTickEvent.Post.class, event -> {
            int[] ticks = event.getEntity() instanceof Player player ? playerTicks.get(player) : null;
            if (ticks != null) ticks[0]++;
            Track track = event.getEntity() instanceof ClackersEntity shot ? tracks.get(shot) : null;
            if (track != null && track.pending != null) {
                track.steps.add(new Step(track.pending, frame(track.shot), List.copyOf(track.impacts)));
                track.pending = null;
            }
        });
        listen(ProjectileImpactEvent.class, event -> {
            Track track = event.getProjectile() instanceof ClackersEntity shot ? tracks.get(shot) : null;
            if (track == null || track.pending == null) return;
            HitResult hit = event.getRayTraceResult();
            Entity target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
            track.impacts.add(new Impact(hit, target, target == null ? null : target.getBoundingBox(),
                    target == null ? null : target.getEyePosition()));
        });
    }

    private <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
        Consumer<T> guarded = event -> {
            if (closed || observerFailure != null) return;
            try {
                listener.accept(event);
            }
            catch (RuntimeException | Error error) {
                observerFailure = error;
            }
        };
        listeners.add(guarded);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, guarded);
    }

    private void expect(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(name + ": " + message);
    }

    void check(boolean condition, String message) {
        helper.assertTrue(condition, "CLACKERS " + name + ": " + message);
    }

    void near(Vec3 actual, Vec3 expected, String what) {
        check(actual.distanceTo(expected) < EPS, what + ": expected " + expected + " but was " + actual);
    }

    void log(String message) {
        JojoMod.LOGGER.info("CLACKERS-SCENE {} {}", name, message);
    }

    BlockPos cell(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    Vec3 point(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    void place(BlockPos pos, BlockState state) {
        originals.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        check(level.setBlockAndUpdate(pos, state), "block placement failed at " + pos);
    }

    void stone(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (BlockPos pos : BlockPos.betweenClosed(cell(x1, y1, z1), cell(x2, y2, z2))) {
            place(pos, Blocks.STONE.defaultBlockState());
        }
    }

    /** A floating player with Hamon, Clacker Volley and one Clackers in the main hand. */
    Player thrower(GameType mode, Vec3 feet, float yaw) {
        Player player = bystander(mode, feet, yaw);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CLACKERS.get()));
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        power.setPowerType(ModPlayerPowers.HAMON.get());
        powerResets.add(() -> power.setPowerType(null));
        HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
        hamon.learnSkill(ModHamonSkills.CLACKER_VOLLEY.get());
        hamon.setBreathStability(hamon.getMaxBreathStability());
        hamon.setEnergy(hamon.getMaxEnergy());
        check(hamon.isSkillLearned(ModHamonSkills.CLACKER_VOLLEY.get()) && hamon.getEnergy() > 200.0F, "thrower lacks the skill or energy");
        return player;
    }

    Player bystander(GameType mode, Vec3 feet, float yaw) {
        Player player = GameTestPlayers.makeServerMockPlayer(helper, mode);
        mode.updatePlayerAbilities(player.getAbilities());
        player.setNoGravity(true);
        player.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        owned.add(player);
        playerTicks.put(player, new int[1]);
        check(level.addFreshEntity(player), "could not add a player");
        return player;
    }

    Cow cow(Vec3 feet, boolean invulnerable) {
        Cow cow = EntityType.COW.create(level);
        check(cow != null, "could not create the target");
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setInvulnerable(invulnerable);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        cow.setHealth(100.0F);
        cow.setPos(feet);
        owned.add(cow);
        check(level.addFreshEntity(cow), "could not add the target");
        return cow;
    }

    /** Adds any other entity; the scene removes it again when it closes. */
    <T extends Entity> T add(T entity) {
        owned.add(entity);
        check(level.addFreshEntity(entity), "could not add " + entity);
        return entity;
    }

    /** Queues the registered use, twenty natural use ticks and the item's own release for every player. */
    void throwClackers(Player... players) {
        await("the throwers ticking", () -> {
            for (Player player : players) if (playerTicks.get(player)[0] < 3) return false;
            return true;
        });
        then(() -> {
            for (Player player : players) {
                check(player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction()
                        && player.isUsingItem(), "the registered Clackers use was refused");
            }
        });
        await("twenty natural use ticks", () -> {
            for (Player player : players) if (player.getTicksUsingItem() < 20) return false;
            return true;
        });
        then(() -> {
            for (Player player : players) {
                int before = shots.size();
                check(player.getTicksUsingItem() == 20, "not exactly twenty use ticks: " + player.getTicksUsingItem());
                player.releaseUsingItem();
                check(shots.size() == before + 1 && shots.getLast().getOwner() == player && !player.isUsingItem(),
                        "the release did not throw one owned Clackers");
                log("throw owner=" + player.getUUID() + " shot=" + shots.getLast().getUUID() + " velocity=" + shots.getLast().getDeltaMovement());
            }
        });
    }

    /** The public summon command: a Clackers that never had a thrower. */
    ClackersEntity summon(Vec3 pos, String nbt) {
        int before = shots.size();
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
        // plain decimals: the command parser has no exponent form, and test plots can lie millions of blocks out
        String command = "summon " + ModEntityTypes.CLACKERS.getId() + " " + BigDecimal.valueOf(pos.x).toPlainString() + " "
                + BigDecimal.valueOf(pos.y).toPlainString() + " " + BigDecimal.valueOf(pos.z).toPlainString() + " " + nbt;
        level.getServer().getCommands().performPrefixedCommand(source, command);
        check(shots.size() == before + 1, "the command did not add one Clackers: " + command);
        return shots.getLast();
    }

    /** Saves the entity as a chunk would, removes it and loads the saved data into a fresh entity. */
    ClackersEntity reload(ClackersEntity shot) {
        CompoundTag saved = new CompoundTag();
        check(shot.save(saved), "the Clackers could not be saved");
        shot.discard();
        int before = shots.size();
        Entity loaded = EntityType.loadEntityRecursive(saved, level, entity -> entity);
        check(loaded instanceof ClackersEntity && loaded != shot && level.addFreshEntity(loaded)
                && shots.size() == before + 1 && shots.getLast() == loaded, "the saved Clackers did not load back");
        return (ClackersEntity) loaded;
    }

    Frame frame(ClackersEntity shot) {
        return new Frame(level.getGameTime(), shot.tickCount, shot.position(), shot.getDeltaMovement(), shot.getXRot(),
                shot.getYRot(), shot.isInGround(), shot.isRemoved(), shot.getRemovalReason(), shot.hasImpulse,
                shot.saveWithoutId(new CompoundTag()));
    }

    Track track(ClackersEntity shot) {
        Track track = tracks.get(shot);
        check(track != null, "untracked Clackers");
        return track;
    }

    @Nullable
    Track track(int index) {
        return index < shots.size() ? tracks.get(shots.get(index)) : null;
    }

    @Nullable
    Step contact(int index) {
        Track track = track(index);
        return track == null ? null : track.contact();
    }

    @Nullable
    Step landing(int index) {
        Track track = track(index);
        return track == null ? null : track.landing();
    }

    int groundTicks(int index) {
        Track track = track(index);
        return track == null ? -1 : track.groundTicks();
    }

    int steps(int index) {
        Track track = track(index);
        return track == null ? 0 : track.steps.size();
    }

    /** One tick of free flight applied to a velocity: drag, then gravity. */
    static Vec3 afterFlightTick(Vec3 movement) {
        return movement.scale(DRAG).add(0.0D, -GRAVITY, 0.0D);
    }

    /** 1.16.5 ProjectileEntity.lerpRotation. */
    static float turn(float from, float to) {
        while (to - from < -180.0F) from -= 360.0F;
        while (to - from >= 180.0F) from += 360.0F;
        return Mth.lerp(0.2F, from, to);
    }

    void await(String what, BooleanSupplier condition) {
        stages.add(new Stage(what, condition));
    }

    void then(Runnable action) {
        stages.add(new Stage("an action", () -> {
            action.run();
            return true;
        }));
    }

    private void poll() {
        if (closed) return;
        try {
            rethrowObserverFailure();
            while (stage < stages.size() && stages.get(stage).body().getAsBoolean()) {
                stage++;
                rethrowObserverFailure();
            }
            if (stage == stages.size()) {
                close();
                boolean restored = originals.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                boolean gone = owned.stream().allMatch(Entity::isRemoved);
                log("done stages=" + stages.size() + " shots=" + shots.size() + " restored=" + restored + " ownedGone=" + gone);
                helper.assertTrue(restored && gone, "CLACKERS " + name + ": cleanup incomplete");
                helper.succeed();
                return;
            }
            check(helper.getTick() < deadline, "watchdog while waiting for " + stages.get(stage).what());
            helper.runAfterDelay(1, this::poll);
        }
        catch (RuntimeException | Error error) {
            close();
            throw error;
        }
    }

    private void rethrowObserverFailure() {
        if (observerFailure instanceof RuntimeException error) throw error;
        if (observerFailure instanceof Error error) throw error;
    }

    private void close() {
        if (closed) return;
        closed = true;
        for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
        listeners.clear();
        for (Player player : playerTicks.keySet()) {
            player.stopUsingItem();
            player.getInventory().clearContent();
        }
        powerResets.forEach(Runnable::run);
        for (Entity entity : owned) if (!entity.isRemoved()) entity.discard();
        originals.forEach(level::setBlockAndUpdate);
    }

    @Override
    public void testStructureLoaded(GameTestInfo test) {}

    @Override
    public void testPassed(GameTestInfo test, GameTestRunner runner) {
        close();
    }

    @Override
    public void testFailed(GameTestInfo test, GameTestRunner runner) {
        close();
    }

    @Override
    public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
        close();
    }
}
