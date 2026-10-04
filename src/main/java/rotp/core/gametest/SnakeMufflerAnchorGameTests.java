package rotp.core.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.SnakeMufflerEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SnakeMufflerAnchorGameTests {
    private static final double EPSILON = 1.0E-5D;

    private SnakeMufflerAnchorGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "snake_muffler_ground_anchor", timeoutTicks = 100)
    public static void realPassiveKeepsCapturedDefenderCellDuringNaturalJump(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        helper.testInfo.addListener(fixture);
        try {
            fixture.setUp();
            helper.runAfterDelay(1, fixture::poll);
        }
        catch (RuntimeException | Error error) {
            fixture.close();
            throw error;
        }
    }

    @GameTest(template = "empty", batch = "snake_muffler_anchor_data", timeoutTicks = 20)
    public static void anchorDataPreservesFullCoordinatesAndClearsLegacyState(GameTestHelper helper) {
        // Detached entities exercise persistence and the stock metadata codec, not world-height gameplay.
        SnakeMufflerEntity source = new SnakeMufflerEntity(ModEntityTypes.SNAKE_MUFFLER.get(), helper.getLevel());
        SnakeMufflerEntity savedCopy = new SnakeMufflerEntity(ModEntityTypes.SNAKE_MUFFLER.get(), helper.getLevel());
        SnakeMufflerEntity metadataCopy = new SnakeMufflerEntity(ModEntityTypes.SNAKE_MUFFLER.get(), helper.getLevel());
        try {
            assertSavedAnchor(helper, source, null);
            for (BlockPos anchor : List.of(new BlockPos(20000001, 5000, -20000003),
                    new BlockPos(-20000005, -5000, 20000007))) {
                source.attachToBlockPos(anchor);
                CompoundTag saved = source.saveWithoutId(new CompoundTag());
                assertSavedAnchor(helper, source, anchor);
                savedCopy.load(saved);
                assertSavedAnchor(helper, savedCopy, anchor);
                transferAnchorMetadata(helper, source, metadataCopy);
                assertSavedAnchor(helper, metadataCopy, anchor);
                JojoMod.LOGGER.info("SNAKE-ANCHOR-DATA full-coordinate NBT/packet anchor={}", anchor);
            }

            CompoundTag legacy = source.saveWithoutId(new CompoundTag());
            legacy.remove("AttachedBlock");
            source.load(legacy);
            savedCopy.load(legacy);
            assertSavedAnchor(helper, source, null);
            assertSavedAnchor(helper, savedCopy, null);
            transferAnchorMetadata(helper, source, metadataCopy);
            assertSavedAnchor(helper, metadataCopy, null);

            BlockPos rearmed = new BlockPos(17, 5000, -19);
            for (int malformed = 0; malformed < 4; malformed++) {
                source.attachToBlockPos(rearmed);
                savedCopy.attachToBlockPos(rearmed);
                transferAnchorMetadata(helper, source, metadataCopy);
                assertSavedAnchor(helper, metadataCopy, rearmed);
                CompoundTag invalid = source.saveWithoutId(new CompoundTag());
                if (malformed == 0) invalid.putIntArray("AttachedBlock", new int[0]);
                else if (malformed == 1) invalid.putIntArray("AttachedBlock", new int[] { 1, 2 });
                else if (malformed == 2) invalid.putIntArray("AttachedBlock", new int[] { 1, 2, 3, 4 });
                else invalid.putString("AttachedBlock", "not-an-int-array");
                source.load(invalid);
                savedCopy.load(invalid);
                assertSavedAnchor(helper, source, null);
                assertSavedAnchor(helper, savedCopy, null);
                transferAnchorMetadata(helper, source, metadataCopy);
                assertSavedAnchor(helper, metadataCopy, null);
            }
            JojoMod.LOGGER.info("SNAKE-ANCHOR-DATA result fullCoordinates=2 legacyCleared=true malformedCleared=4 worldAdded=0");
            helper.succeed();
        }
        finally {
            source.discard();
            savedCopy.discard();
            metadataCopy.discard();
        }
    }

    private static void assertSavedAnchor(GameTestHelper helper, SnakeMufflerEntity entity, BlockPos expected) {
        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        if (expected == null) {
            helper.assertTrue(!saved.contains("AttachedBlock"), "Absent/invalid Snake anchor retained stale persistence state");
        }
        else {
            helper.assertTrue(Arrays.equals(saved.getIntArray("AttachedBlock"),
                    new int[] { expected.getX(), expected.getY(), expected.getZ() }), "Snake anchor coordinates changed in data round trip");
        }
    }

    private static void transferAnchorMetadata(GameTestHelper helper, SnakeMufflerEntity source, SnakeMufflerEntity receiver) {
        var dirty = source.getEntityData().packDirty();
        helper.assertTrue(dirty != null && !dirty.isEmpty(), "Snake anchor change produced no metadata update");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer, new ClientboundSetEntityDataPacket(source.getId(), dirty));
            ClientboundSetEntityDataPacket decoded = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.id() == source.getId() && buffer.readableBytes() == 0,
                    "Snake stock metadata packet did not round trip completely");
            receiver.getEntityData().assignValues(decoded.packedItems());
        }
        finally {
            buffer.release();
        }
    }

    private record Frame(long time, int age, Vec3 owner, Vec3 root, Vec3 tip, Vec3 delta,
            Vec3 ownerDelta, boolean hurtMarked, float fallDistance, boolean removed) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> floor = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<SnakeMufflerEntity> spawned = new ArrayList<>();
        private final List<ItemEntity> drops = new ArrayList<>();
        private final List<Frame> frames = new ArrayList<>();
        private Player defender;
        private Player attacker;
        private HamonData hamon;
        private SnakeMufflerEntity snake;
        private AABB room;
        private BlockPos capturedCell;
        private Vec3 attackPosition;
        private Vec3 previousOwnerPost;
        private Frame before;
        private LivingIncomingDamageEvent incoming;
        private RuntimeException observerFailure;
        private int defenderTicks;
        private int attackerTicks;
        private int groundedTicks;
        private int attackOwnerTick;
        private int upwardOwnerSteps;
        private int impactCount;
        private boolean attacking;
        private boolean attacked;
        private boolean closed;
        private float healthBefore;
        private float energyBefore;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(x + 3, y - 1, z + 3);
            BlockPos max = new BlockPos(x + 13, y + 8, z + 13);
            helper.assertTrue(min.getY() >= level.getMinBuildHeight() && max.getY() < level.getMaxBuildHeight(),
                    "Snake fixture exceeds build height");
            room = AABB.encapsulatingFullBlocks(min, max);
            helper.assertTrue(level.getEntities((Entity) null, room).isEmpty(), "Snake fixture contains another entity");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Snake fixture is obstructed");
            }
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x + 4, y - 1, z + 4),
                    new BlockPos(x + 12, y - 1, z + 12))) {
                floor.put(pos.immutable(), level.getBlockState(pos));
                helper.assertTrue(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "Could not place Snake floor");
            }
            defender = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            defender.moveTo(x + 8.5D, y + 0.125D, z + 6.5D, 0, 0);
            defender.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SATIPOROJA_SCARF.get()));
            attacker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            attacker.moveTo(x + 8.5D, y + 0.125D, z + 8.5D, 180, 0);
            attacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(level.addFreshEntity(defender) && level.addFreshEntity(attacker), "Could not add Snake players");
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(defender);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(defender, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.SATIPOROJA_SCARF.get());
            hamon.learnSkill(ModHamonSkills.SNAKE_MUFFLER.get());
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            registerObservers();
            log("setup defender=" + defender.getUUID() + " attacker=" + attacker.getUUID()
                    + " gravity=true floorCells=" + floor.size());
        }

        private void registerObservers() {
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() != defender) return;
                helper.assertTrue(attacking && incoming == null && event.getSource().getEntity() == attacker
                                && event.getSource().getDirectEntity() == attacker && event.getOriginalAmount() > 0,
                        "Snake admission did not receive the real direct melee hit");
                incoming = event;
                log("incoming amount=" + event.getOriginalAmount() + " canceled=" + event.isCanceled()
                        + " grounded=" + defender.onGround());
            });
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level) return;
                if (event.getEntity() instanceof ItemEntity drop && room.contains(drop.position())) drops.add(drop);
                if (!(event.getEntity() instanceof SnakeMufflerEntity created) || created.getOwner() != defender) return;
                spawned.add(created);
                observe(() -> {
                    helper.assertTrue(attacking && spawned.size() == 1 && !event.isCanceled(), "Unexpected Snake passive spawn");
                    snake = created;
                    CompoundTag nbt = snake.saveWithoutId(new CompoundTag());
                    helper.assertTrue(snake.getType() == ModEntityTypes.SNAKE_MUFFLER.get() && snake.tickCount == 0
                                    && snake.ticksLifespan() == 10 && nbt.hasUUID("TargetEntity")
                                    && nbt.getUUID("TargetEntity").equals(attacker.getUUID()),
                            "Snake join owner, attacker, type or age changed");
                    helper.assertTrue(defender.onGround() && defender.blockPosition().equals(capturedCell)
                                    && near(snake.position(), defender.getEyePosition(1).add(0, -0.3D, 0)),
                            "Snake constructor or grounded capture premise changed");
                    log("join tip=" + snake.position() + " capturedDefenderCell=" + capturedCell
                            + " attackerCell=" + attacker.blockPosition() + " delta=" + snake.getDeltaMovement());
                });
            };
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() != snake) return;
                helper.assertTrue(!event.isCanceled() && before == null && snake.getOwner() == defender
                                && level.isPositionEntityTicking(snake.blockPosition()), "Snake natural tick/owner invalid");
                before = frame();
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (event.getProjectile() != snake) return;
                impactCount++;
                log("impact age=" + snake.tickCount + " type=" + event.getRayTraceResult().getType()
                        + " point=" + event.getRayTraceResult().getLocation() + " canceled=" + event.isCanceled());
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == attacker) attackerTicks++;
                if (event.getEntity() == defender) {
                    defenderTicks++;
                    if (!attacked) groundedTicks = defender.onGround() && attacker.onGround() ? groundedTicks + 1 : 0;
                    if (attacked && previousOwnerPost != null && defender.getY() > previousOwnerPost.y + EPSILON) {
                        upwardOwnerSteps++;
                        log("owner-jump step=" + upwardOwnerSteps + " from=" + previousOwnerPost + " to=" + defender.position());
                    }
                    if (attacked) {
                        float expectedCooldown = (80.0F - (defenderTicks - attackOwnerTick)) / 80.0F;
                        helper.assertTrue(Math.abs(defender.getCooldowns().getCooldownPercent(ModItems.SATIPOROJA_SCARF.get(), 0)
                                        - expectedCooldown) < EPSILON, "Snake cooldown does not follow the natural 80-tick interval");
                    }
                    previousOwnerPost = defender.position();
                }
                if (event.getEntity() != snake) return;
                Frame after = frame();
                helper.assertTrue(before != null && before.age == after.age && before.time == after.time,
                        "Missing natural Snake Pre/Post pair");
                helper.assertTrue(!after.removed && snake.ticksLifespan() == 10
                                && near(after.root, defender.getEyePosition(1).add(0, -0.3D, 0)),
                        "Snake lifecycle or moving root changed before anchor observation");
                if (after.age < 5) {
                    Vec3 difference = attacker.position().subtract(defender.position());
                    Vec3 horizontal = new Vec3(difference.x, 0, difference.z);
                    helper.assertTrue(attacker.isAlive() && horizontal.lengthSqr() < 25, "Jump target left the near-target branch");
                    if (horizontal.lengthSqr() > 0.04D) horizontal = horizontal.scale(0.4D / horizontal.length());
                    Vec3 expectedJump = horizontal.add(0, (attacker.getBbHeight() + 0.5D) / 10.0D, 0);
                    helper.assertTrue(near(after.ownerDelta, expectedJump) && after.hurtMarked && after.fallDistance == 0.0F,
                            "Natural passive jump delta, motion sync or fall reset changed");
                }
                frames.add(after);
                log("step pre=" + before + " post=" + after);
                before = null;
            });
            listeners.add(damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private Frame frame() {
            return new Frame(level.getGameTime(), snake.tickCount, defender.position(), snake.getOriginPoint(1),
                    snake.position(), snake.getDeltaMovement(), defender.getDeltaMovement(), defender.hurtMarked,
                    defender.fallDistance, snake.isRemoved());
        }

        private void attack() {
            helper.assertTrue(defender.onGround() && attacker.onGround() && groundedTicks >= 2
                            && !defender.isNoGravity() && !attacker.isNoGravity() && !defender.isCreative()
                            && !defender.getAbilities().instabuild && !defender.isInvulnerable()
                            && defender.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SATIPOROJA_SCARF.get())
                            && !defender.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get())
                            && hamon.isSkillLearned(ModHamonSkills.SATIPOROJA_SCARF.get())
                            && hamon.isSkillLearned(ModHamonSkills.SNAKE_MUFFLER.get())
                            && hamon.getEnergy() >= 500 && attacker.getAttackStrengthScale(0.5F) > 0.99F,
                    "Snake grounded Survival passive admission is not ready");
            helper.assertTrue(level.isPositionEntityTicking(defender.blockPosition())
                            && level.isPositionEntityTicking(attacker.blockPosition()), "Snake players are not ticking");
            float efficiency = hamon.getActionEfficiency(500, false, ModHamonSkills.SNAKE_MUFFLER.get(), defender);
            helper.assertTrue(efficiency > 0, "Snake passive has no efficiency");
            capturedCell = defender.blockPosition().immutable();
            helper.assertTrue(floor.containsKey(capturedCell.below()) && !capturedCell.equals(attacker.blockPosition()),
                    "Captured Snake cell is not the defender's supported feet cell");
            attackPosition = defender.position();
            previousOwnerPost = attackPosition;
            healthBefore = defender.getHealth();
            energyBefore = hamon.getEnergy();
            attackOwnerTick = defenderTicks;
            attacking = true;
            try { attacker.attack(defender); }
            finally { attacking = false; }
            attacked = true;
            helper.assertTrue(incoming != null && incoming.isCanceled() && snake != null && spawned.size() == 1,
                    "Real melee did not trigger/cancel through Snake passive");
            helper.assertTrue(efficiency == 1.0F || efficiency >= incoming.getOriginalAmount() / defender.getMaxHealth(),
                    "Snake passive efficiency premise failed");
            float expectedDebit = 500.0F * 0.6F;
            float actualDebit = energyBefore - hamon.getEnergy();
            boolean cooldown = defender.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get());
            int glowDuration = attacker.hasEffect(MobEffects.GLOWING) ? attacker.getEffect(MobEffects.GLOWING).getDuration() : -1;
            log("resource-controls nominalCost=500.0 scarfMultiplier=0.6 expectedDebit=" + expectedDebit
                    + " actualDebit=" + actualDebit + " energy=" + energyBefore + "->" + hamon.getEnergy()
                    + " health=" + healthBefore + "->" + defender.getHealth() + " cooldown=" + cooldown
                    + " cooldownFraction=" + defender.getCooldowns().getCooldownPercent(ModItems.SATIPOROJA_SCARF.get(), 0)
                    + " glowDuration=" + glowDuration + " capturedCell=" + capturedCell
                    + " currentCell=" + defender.blockPosition() + " canceled=" + incoming.isCanceled());
            helper.assertTrue(defender.getHealth() == healthBefore && Math.abs(actualDebit - expectedDebit) < 0.001F
                            && cooldown && glowDuration == 200 && capturedCell.equals(defender.blockPosition()),
                    "Snake cancellation/resource/target controls failed");
            log("attack canceled=true energy=" + energyBefore + "->" + hamon.getEnergy() + " efficiency=" + efficiency
                    + " feet=" + capturedCell + " anchor=" + Vec3.atCenterOf(capturedCell));
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Snake watchdog: ground=" + groundedTicks + " ownerTicks=" + defenderTicks
                        + " snakeFrames=" + frames.size() + " jumpSteps=" + upwardOwnerSteps);
                helper.assertTrue(defender.isAlive() && attacker.isAlive() && room.contains(defender.position())
                                && room.contains(attacker.position()), "Snake player escaped or died");
                if (!attacked && groundedTicks >= 2 && defenderTicks >= 25 && attackerTicks >= 25) attack();
                if (attacked && frames.size() >= 2 && upwardOwnerSteps >= 2) {
                    validate();
                    close();
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        private void validate() {
            helper.assertTrue(frames.get(0).age == 1 && frames.get(1).age == 2 && impactCount == 0
                            && upwardOwnerSteps >= 2 && defender.getY() > attackPosition.y + EPSILON
                            && defender.getHealth() == healthBefore && incoming.isCanceled(), "Snake jump/empty-ray controls failed");
            Vec3 anchor = Vec3.atCenterOf(capturedCell);
            double error = frames.stream().mapToDouble(frame -> frame.tip.distanceTo(anchor)).max().orElseThrow();
            helper.assertTrue(frames.stream().anyMatch(frame -> frame.root.distanceTo(anchor) > 0.5D),
                    "Snake root never separated from the fixed cell center");
            log("result controls=true frames=" + frames.size() + " upwardSteps=" + upwardOwnerSteps
                    + " captured=" + capturedCell + " maxTipAnchorError=" + error);
            helper.assertTrue(error < EPSILON, "Snake tip did not retain captured defender-cell center; error=" + error);
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Snake anchor observer failure", error);
            }
        }

        private static boolean near(Vec3 a, Vec3 b) { return a.distanceToSqr(b) < EPSILON * EPSILON; }
        private void log(String message) { JojoMod.LOGGER.info("SNAKE-ANCHOR {}", message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                for (SnakeMufflerEntity created : spawned) if (!created.isRemoved()) created.discard();
                for (ItemEntity drop : drops) if (!drop.isRemoved()) drop.discard();
                if (defender != null) { defender.stopUsingItem(); defender.discard(); }
                if (attacker != null) { attacker.stopUsingItem(); attacker.discard(); }
            }
            finally {
                for (var entry : floor.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                boolean restored = floor.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                log("cleanup listeners=0 restored=" + restored + " floorCells=" + floor.size() + " drops=" + drops.size());
                if (!restored) throw new IllegalStateException("Snake floor was not exactly restored");
            }
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
