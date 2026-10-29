package rotp.addons.stonefree.ability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import rotp.addons.stonefree.state.StoneFreeState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopState;

@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreeBarrierGameTests {
    static final short REMOVE_KEY = 1;
    static final short GRAPPLE_KEY = 2;
    // 1.16 SFUBarrierEntity.onSyncedDataUpdated: alertedTicks = 40.
    private static final int DONOR_ALERT_TICKS = 40;
    private static final int TIME_STOP_ID = -7_640_301;
    // The game test server runs ticks as fast as it can, so a wait for chunk state that counts ticks alone is a
    // race against the chunk workers. A wait therefore ends only when both its tick budget and its wall clock
    // budget are used up; each waiting tick sleeps a millisecond, which a pending unpark permit cannot cut short.
    static final int CHUNK_WAIT_TICKS = 4000;
    static final long CHUNK_WAIT_NANOS = 60_000_000_000L;
    // A wait of CHUNK_WAIT_NANOS holds at most one tick per sleeping millisecond; the rest is the test body.
    static final int CHUNK_WAIT_TEST_TICKS = 62_000;

    private StoneFreeBarrierGameTests() {}

    static boolean chunkWaitSpent(int ticksLeft, long startNanos) {
        return ticksLeft <= 0 && System.nanoTime() - startNanos >= CHUNK_WAIT_NANOS;
    }

    static String chunkWaitBudget(long startNanos) {
        return CHUNK_WAIT_TICKS + " ticks and " + (CHUNK_WAIT_NANOS / 1_000_000_000L) + " s (waited "
                + (System.nanoTime() - startNanos) / 1_000_000L + " ms)";
    }

    static void yieldToChunkWorkers() {
        try {
            Thread.sleep(1L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static void assertTicksEntities(GameTestHelper helper, Fixture fixture) {
        BlockPos ownerAt = fixture.owner.blockPosition();
        boolean ownerTicks = helper.getLevel().isPositionEntityTicking(ownerAt);
        BlockPos standAt = fixture.stand == null ? null : fixture.stand.blockPosition();
        boolean standTicks = standAt == null || helper.getLevel().isPositionEntityTicking(standAt);
        helper.assertTrue(ownerTicks && standTicks,
                "The fixture's chunk does not tick entities, so nothing placed there acts: owner at " + ownerAt
                        + " ticks " + ownerTicks + ", Stand at " + standAt + " ticks " + standTicks
                        + ", test origin " + helper.absolutePos(BlockPos.ZERO));
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 80)
    public static void barrierNetworkPolicyKeepsDirtyAttachmentAndHealthUpdates(GameTestHelper helper) {
        EntityType<StoneFreeStringEntity> type = StoneFreeAddon.SFU_BARRIER.get();
        helper.assertTrue(type.updateInterval() == Integer.MAX_VALUE && !type.trackDeltas(),
                "Registered barrier lost the donor local-follow network policy");
        for (EntityType<StoneFreeStringEntity> ordinary : List.of(
                StoneFreeAddon.SF_STRING.get(), StoneFreeAddon.SFU_STRING.get(),
                StoneFreeAddon.SFU_STRING_SWEEP.get(), StoneFreeAddon.SFU_STRING_WHIP.get(),
                StoneFreeAddon.SF_EXTENDED_PUNCH.get(), StoneFreeAddon.SFU_STRING_SPIKE.get(),
                StoneFreeAddon.SF_GRAPPLING_STRING.get(), StoneFreeAddon.SFU_GRAPPLING_STRING.get())) {
            helper.assertTrue(ordinary.updateInterval() == 1 && ordinary.trackDeltas(),
                    "Barrier networking change leaked into an ordinary string type");
        }
        Fixture fixture = fixture(helper, true);
        later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            place(helper, fixture, fixture.first);
            later(helper, fixture, 6, () -> {
                List<StoneFreeStringEntity> placed = barriers(helper, fixture);
                helper.assertTrue(placed.size() == 1, "Network fixture did not create one public loose barrier");
                StoneFreeStringEntity barrier = placed.getFirst();
                List<Packet<?>> packets = new ArrayList<>();
                ServerEntity tracker = new ServerEntity(helper.getLevel(), barrier,
                        type.updateInterval(), type.trackDeltas(), packets::add);
                StoneFreeStringEntity recipient = type.create(helper.getLevel());
                helper.assertTrue(recipient != null, "Missing registered barrier data recipient");
                var initialData = barrier.getEntityData().getNonDefaultValues();
                helper.assertTrue(initialData != null, "Public barrier has no initial synchronized data");
                recipient.getEntityData().assignValues(initialData);
                tracker.sendChanges();
                packets.clear();
                for (int tick = 0; tick < 3; tick++) {
                    barrier.setPos(barrier.position().add(0.0D, 0.3D, 0.0D));
                    barrier.setDeltaMovement(0.0D, 0.3D, 0.0D);
                    tracker.sendChanges();
                }
                helper.assertTrue(packets.stream().noneMatch(packet ->
                                packet instanceof ClientboundMoveEntityPacket
                                        || packet instanceof ClientboundTeleportEntityPacket
                                        || packet instanceof ClientboundSetEntityMotionPacket),
                        "Ordinary local-follow motion emitted a second server motion stream");
                packets.clear();
                helper.assertTrue(barrier.attachBarrier(fixture.second), "Public attachment refused the loose fixture");
                tracker.sendChanges();
                applyBarrierMetadata(helper, packets, barrier, recipient);
                CompoundTag attached = recipient.saveWithoutId(new CompoundTag());
                helper.assertTrue(recipient.isBarrierAttached()
                                && attached.getLong("BarrierTerminal") == fixture.second.asLong(),
                        "MAX movement interval delayed dirty attached state or terminal metadata");

                packets.clear();
                Cow entrant = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(entrant != null, "Missing metadata contact fixture");
                fixture.entrant = entrant;
                entrant.setNoAi(true);
                entrant.setNoGravity(true);
                Vec3 middle = Vec3.atCenterOf(fixture.first).lerp(Vec3.atCenterOf(fixture.second), 0.5D);
                entrant.setPos(middle.subtract(0.0D, entrant.getBbHeight() * 0.5D, 0.0D));
                helper.assertTrue(helper.getLevel().addFreshEntity(entrant), "Could not add metadata contact fixture");
                PowerClass.STAND.attachGet(entrant).setStand(StoneFreeAddon.STONE_FREE.get());
                // A Stone Free user carries integrated_stand from its first tick; this one has not ticked yet.
                entrant.addEffect(integratedStand());
                barrier.tick();
                helper.assertTrue(barrier.barrierHealth() == 2 && barrier.isBarrierAlerted(),
                        "Production contact did not dirty health and alert metadata");
                tracker.sendChanges();
                applyBarrierMetadata(helper, packets, barrier, recipient);
                helper.assertTrue(recipient.barrierHealth() == 2 && recipient.isBarrierAlerted(),
                        "MAX movement interval delayed dirty health or alert metadata");
                helper.assertTrue(packets.stream().noneMatch(ClientboundSetEntityMotionPacket.class::isInstance),
                        "Dirty barrier metadata also emitted an ordinary velocity update");
                LogUtils.getLogger().info("R656 SF registered network policy: barrier MAX/false, ordinary8 1/true; "
                        + "production ServerEntity suppressed routine motion and delivered attach/health/alert immediately");
                succeed(helper, fixture);
            });
        });
    }

    private static void applyBarrierMetadata(GameTestHelper helper, List<Packet<?>> packets,
            StoneFreeStringEntity source, StoneFreeStringEntity recipient) {
        int applied = 0;
        for (Packet<?> packet : packets) {
            if (packet instanceof ClientboundSetEntityDataPacket data && data.id() == source.getId()) {
                recipient.getEntityData().assignValues(data.packedItems());
                applied++;
            }
        }
        helper.assertTrue(applied > 0, "Production ServerEntity did not emit dirty barrier metadata this call");
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void stringOriginsFollowDonorKindOffsetsAndUserPose(GameTestHelper helper) {
        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SFOriginOwner"));
        Fixture fixture = fixture(helper, true, owner);
        later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            place(helper, fixture, fixture.first);
            later(helper, fixture, 6, () -> {
                List<StoneFreeStringEntity> placed = barriers(helper, fixture);
                helper.assertTrue(placed.size() == 1, "Origin control did not create one public loose barrier");
                StoneFreeStringEntity barrier = placed.getFirst();
                List<String> mismatches = new ArrayList<>();
                int checks = 0;
                double standingEyeHeight = owner.getEyeY() - owner.getY();
                fixture.stand.setPos(fixture.home.add(1.0D, 0.5D, -1.0D));
                for (Pose pose : new Pose[] {Pose.STANDING, Pose.CROUCHING}) {
                    owner.setPose(pose);
                    helper.assertTrue(pose != Pose.CROUCHING
                                    || owner.getEyeY() - owner.getY() < standingEyeHeight - 0.1D,
                            "Crouching fixture did not change the real player's eye height");
                    for (float[] yaw : new float[][] {{70.0F, -35.0F, 25.0F}, {-145.0F, 120.0F, -80.0F}}) {
                        for (float pitch : new float[] {-90.0F, 0.0F, 90.0F}) {
                            for (LivingEntity shooter : new LivingEntity[] {owner, fixture.stand}) {
                                shooter.setYRot(yaw[0]);
                                shooter.yRotO = yaw[0] - 20.0F;
                                shooter.yBodyRotO = yaw[1];
                                shooter.yBodyRot = yaw[2];
                                shooter.setXRot(pitch);
                                shooter.xRotO = -pitch;
                            }
                            for (StoneFreeStringEntity.Kind kind : StoneFreeStringEntity.Kind.values()) {
                                if (kind == StoneFreeStringEntity.Kind.USER_BARRIER) continue;
                                StoneFreeStringEntity string = new StoneFreeStringEntity(kind, fixture.stand, helper.getLevel());
                                LivingEntity shooter = kind.isUserString() ? owner : fixture.stand;
                                string.setShootingPosOf(shooter);
                                String label = kind + "/" + pose + "/pitch=" + pitch + "/lookYaw=" + yaw[0];
                                recordOriginMismatch(mismatches, label + "/launch", string.position(),
                                        donorStringOrigin(kind, shooter, 1.0F, yaw[0]));
                                checks++;
                                for (float partial : new float[] {0.0F, 0.5F, 1.0F}) {
                                    float bodyYaw = yaw[1] + (yaw[2] - yaw[1]) * partial;
                                    recordOriginMismatch(mismatches, label + "/origin/partial=" + partial,
                                            string.getOriginPoint(partial), donorStringOrigin(kind, shooter, partial, bodyYaw));
                                    checks++;
                                }
                                helper.assertTrue(string.getOwner() == fixture.stand,
                                        "Origin lookup changed controller ownership for " + kind);
                            }
                            barrier.tick();
                            assertOpenEndpoint(helper, fixture, barrier, fixture.first);
                            checks++;
                        }
                    }
                }
                LogUtils.getLogger().info("R651 SF production origin checks={} mismatches={} examples={}",
                        checks, mismatches.size(), mismatches.subList(0, Math.min(8, mismatches.size())));
                helper.assertTrue(mismatches.isEmpty(), "Donor origin mismatch count=" + mismatches.size()
                        + (mismatches.isEmpty() ? "" : ": " + mismatches.getFirst()));
                succeed(helper, fixture);
            });
        });
    }

    private static void recordOriginMismatch(List<String> mismatches, String label, Vec3 actual, Vec3 expected) {
        // The independent oracle uses Math trig; production uses Minecraft's float lookup table.
        if (actual.distanceToSqr(expected) > 1.0E-7D) {
            mismatches.add(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static Vec3 donorStringOrigin(StoneFreeStringEntity.Kind kind, LivingEntity shooter,
            float partial, float yaw) {
        double pitch = Math.toRadians(shooter.getXRot());
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);
        Vec3 local = switch (kind) {
            case USER_STRING -> new Vec3(-0.22D, -0.3D - 0.47D * cosPitch - 0.25D * sinPitch,
                    0.25D * cosPitch - 0.47D * sinPitch);
            case STAND_STRING -> new Vec3(-0.3D, -0.3D + 0.1D * cosPitch - 0.4D * sinPitch,
                    0.4D * cosPitch + 0.1D * sinPitch);
            case USER_SPIKE, USER_SWEEP, USER_GRAPPLE -> new Vec3(-0.22D, -0.75D, 0.25D);
            case USER_WHIP -> new Vec3(-0.3D, -0.75D, 0.25D);
            case STAND_GRAPPLE -> new Vec3(-0.3D, -0.2D, 0.4D);
            case EXTENDED_PUNCH -> new Vec3(-0.3D, -0.2D, 0.25D);
            case USER_BARRIER -> throw new IllegalArgumentException("Barrier has a separate world-space endpoint");
        };
        double sinYaw = Math.sin(Math.toRadians(yaw));
        double cosYaw = Math.cos(Math.toRadians(yaw));
        return shooter.getEyePosition(partial).add(local.x * cosYaw - local.z * sinYaw,
                local.y, local.z * cosYaw + local.x * sinYaw);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void openBarrierFollowsDonorOffsetWhileClosedEndpointsStayFixed(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            place(helper, fixture, fixture.first);
            later(helper, fixture, 6, () -> {
                List<StoneFreeStringEntity> firstCast = barriers(helper, fixture);
                helper.assertTrue(firstCast.size() == 1, "First public cast did not create one loose barrier");
                StoneFreeStringEntity first = firstCast.getFirst();
                assertOpenEndpoint(helper, fixture, first, fixture.first);
                place(helper, fixture, fixture.first);
                later(helper, fixture, 6, () -> {
                    List<StoneFreeStringEntity> repeated = barriers(helper, fixture);
                    helper.assertTrue(repeated.size() == 1 && repeated.getFirst() == first
                                    && first.isAlive() && !first.isBarrierAttached()
                                    && StoneFreeState.get(fixture.owner).placedStrings() == 0
                                    && StoneFreeState.get(fixture.owner).openBarrier()
                                            .filter(first.getUUID()::equals).isPresent(),
                            "Repeated current origin attached/invested a zero-length segment or replaced the open tail");
                    assertOpenEndpoint(helper, fixture, first, fixture.first);
                    fixture.owner.setPos(fixture.home.add(-0.5D, 0.5D, 0.5D));
                    fixture.owner.setYRot(93.0F);
                    fixture.owner.setXRot(-27.0F);
                    later(helper, fixture, 2, () -> {
                        assertOpenEndpoint(helper, fixture, first, fixture.first);
                        place(helper, fixture, fixture.second);
                        later(helper, fixture, 6, () -> {
                            fixture.retained = barriers(helper, fixture);
                            helper.assertTrue(fixture.retained.size() == 2 && first.isBarrierAttached()
                                            && StoneFreeState.get(fixture.owner).placedStrings() == 1,
                                    "Second distinct public cast did not attach the first barrier and create a loose tail");
                            StoneFreeStringEntity tail = fixture.retained.stream()
                                    .filter(barrier -> !barrier.isBarrierAttached()).findFirst().orElseThrow();
                            fixture.owner.setPos(fixture.home.add(0.25D, 0.75D, -0.5D));
                            fixture.owner.setYRot(-121.0F);
                            fixture.owner.setXRot(38.0F);
                            later(helper, fixture, 2, () -> {
                                assertOpenEndpoint(helper, fixture, tail, fixture.second);
                                assertClosedEndpoints(helper, fixture, first);
                                recall(helper, fixture, () -> {
                                    assertOpenEndpoint(helper, fixture, tail, fixture.second);
                                    assertClosedEndpoints(helper, fixture, first);
                                    helper.assertTrue(first.isAlive() && tail.isAlive()
                                                    && first.getOwner() == fixture.owner
                                                    && tail.getOwner() == fixture.owner,
                                            "Recall removed or rebound the geometry fixtures");
                                    succeed(helper, fixture);
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    private static void assertOpenEndpoint(GameTestHelper helper, Fixture fixture,
            StoneFreeStringEntity barrier, BlockPos origin) {
        Vec3 expected = fixture.owner.getEyePosition().add(0.15D, -1.4D, 0.0D);
        helper.assertTrue(!barrier.isBarrierAttached()
                        && barrier.getCurrentBarrierUser() == fixture.owner
                        && barrier.position().distanceToSqr(expected) < 1.0E-10D
                        && barrier.getOriginPoint(1.0F).distanceToSqr(Vec3.atCenterOf(origin)) < 1.0E-10D,
                "Loose barrier lost donor eye offset or fixed origin: expected=" + expected
                        + ", actual=" + barrier.position());
    }

    private static void assertClosedEndpoints(GameTestHelper helper, Fixture fixture,
            StoneFreeStringEntity barrier) {
        helper.assertTrue(barrier.isBarrierAttached()
                        && barrier.position().distanceToSqr(Vec3.atCenterOf(fixture.second)) < 1.0E-10D
                        && barrier.getOriginPoint(1.0F).distanceToSqr(Vec3.atCenterOf(fixture.first)) < 1.0E-10D,
                "Owner movement or recall moved a closed barrier endpoint");
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 220)
    public static void placedBarriersSurviveNormalRecallAndExplicitRemoval(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        placePair(helper, fixture, () -> {
            helper.assertTrue(!fixture.stand.isArmsOnlyMode(), "Full summon fixture became arms-only");
            recall(helper, fixture, () -> watchRecalledBarriers(helper, fixture, 100, () -> {
                var ability = fixture.power.getAbility(StoneFreeAddon.USER_REMOVE_BARRIER_NAME);
                helper.assertTrue(ability instanceof StoneFreeStringAbility
                                && ability.checkConditions(fixture.power).isPositive(),
                        "Registered removal refused the recalled barriers");
                BufferingState buffering = BufferingState.clickOnly();
                AbilityInput.keyPress(REMOVE_KEY, ability, fixture.owner, null,
                        InputMethod.HOLD, 0.0F, buffering, ability.abilityId);
                helper.assertTrue(buffering.isActionSuccess, "Public barrier removal did not start");
                later(helper, fixture, 12, () -> {
                    helper.assertTrue(barriers(helper, fixture).isEmpty()
                                    && StoneFreeState.get(fixture.owner).placedStrings() == 0
                                    && StoneFreeState.get(fixture.owner).openBarrier().isEmpty(),
                            "Explicit removal left a barrier or its invested/open accounting");
                    succeed(helper, fixture);
                });
            }));
        });
    }

    // 1.16 Remove Barriers was holdType (InitStands:175) and a barrier went on a tick that saw the hold
    // (SFUBarrierEntity:145). The 1.16 client sent the end of a hold no sooner than the client tick after its click
    // (InputHandler:336 and 741-760), so every click was held over a tick and took every barrier. Here the release
    // may reach the server before the action's first tick.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 220)
    public static void briefRemovalClickCollectsEveryBarrierAndEnds(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        placePair(helper, fixture, () -> {
            var ability = fixture.power.getAbility(StoneFreeAddon.USER_REMOVE_BARRIER_NAME);
            helper.assertTrue(ability instanceof StoneFreeStringAbility
                            && ability.checkConditions(fixture.power).isPositive(),
                    "Registered removal refused the placed barriers");
            BufferingState click = BufferingState.clickOnly();
            AbilityInput.keyPress(REMOVE_KEY, ability, fixture.owner, null,
                    InputMethod.CLICK, 0.0F, click, ability.abilityId);
            helper.assertTrue(click.isActionSuccess, "Removal click did not start");
            AbilityInput.keyRelease(REMOVE_KEY, fixture.owner);
            later(helper, fixture, 6, () -> {
                helper.assertTrue(barriers(helper, fixture).isEmpty()
                                && StoneFreeState.get(fixture.owner).placedStrings() == 0
                                && StoneFreeState.get(fixture.owner).openBarrier().isEmpty(),
                        "A removal click released before the action's first tick left "
                                + barriers(helper, fixture).size() + " barriers, "
                                + StoneFreeState.get(fixture.owner).placedStrings() + " invested strings and open "
                                + StoneFreeState.get(fixture.owner).openBarrier());
                // the removal, a 1.16 StandAction, runs in its user's Stand-power slot
                LivingEntity controller = StoneFreeServices.controller(fixture.owner);
                for (EntityActionInstance action : new EntityActionInstance[] {
                        LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND),
                        LivingComponentAction.getCurEntityAction(fixture.owner),
                        controller != null ? LivingComponentAction.getCurEntityAction(controller) : null}) {
                    helper.assertTrue(action == null || action.isOver() || action.ability != ability,
                            "The removal click was still running after it collected: phase "
                                    + (action != null ? action.getPhase() : null));
                }
                succeed(helper, fixture);
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 220)
    public static void unsummonedPlacementDoesNotRebindOrRebirthAfterRecall(GameTestHelper helper) {
        Fixture fixture = fixture(helper, false);
        placePair(helper, fixture, () -> {
            StoneFreeStandEntity current = StoneFreeServices.controller(fixture.owner);
            helper.assertTrue(current == null || current.isArmsOnlyMode(),
                    "Completed user barrier unexpectedly left a full manifestation");
            if (current == null) {
                helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
                        "Completed user barrier retained a removed manifestation link");
            }
            StoneFreeAddon.STONE_FREE.get().onUserSummonCommand(fixture.owner, fixture.power);
            fixture.stand = StoneFreeServices.controller(fixture.owner);
            helper.assertTrue(fixture.stand != null && !fixture.stand.isArmsOnlyMode()
                            && (current == null || fixture.stand == current),
                    "Normal R did not summon full mode or promote the current arms manifestation");
            later(helper, fixture, 5, () -> recall(helper, fixture,
                    () -> watchRecalledBarriers(helper, fixture, 100, () -> succeed(helper, fixture))));
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 220)
    public static void legacyControllerOwnedBarrierLoadsAsUserOwnedWithoutSummoning(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        placePair(helper, fixture, () -> {
            StoneFreeStringEntity original = attachedBarrier(helper, fixture);
            original.setOwner(fixture.stand);
            CompoundTag legacy = original.saveWithoutId(new CompoundTag());
            helper.assertTrue(legacy.hasUUID("Owner")
                            && legacy.getUUID("Owner").equals(fixture.stand.getUUID())
                            && legacy.hasUUID("StoneFreeUser")
                            && legacy.getUUID("StoneFreeUser").equals(fixture.owner.getUUID()),
                    "Fixture did not produce the existing controller-owner/user-UUID save shape");
            original.discard();
            recall(helper, fixture, () -> {
                StoneFreeStringEntity loaded = StoneFreeAddon.SFU_BARRIER.get().create(helper.getLevel());
                helper.assertTrue(loaded != null, "Missing registered saved barrier type");
                loaded.load(legacy);
                helper.assertTrue(helper.getLevel().addFreshEntity(loaded), "Could not load the saved barrier");
                fixture.loaded = loaded;
                fixture.retained = barriers(helper, fixture);
                later(helper, fixture, 1, () -> {
                    CompoundTag rebound = loaded.saveWithoutId(new CompoundTag());
                    helper.assertTrue(loaded.getOwner() == fixture.owner
                                    && loaded.isBarrierAttached()
                                    && rebound.getUUID("Owner").equals(fixture.owner.getUUID())
                                    && rebound.getLong("BarrierOrigin") == legacy.getLong("BarrierOrigin")
                                    && rebound.getLong("BarrierTerminal") == legacy.getLong("BarrierTerminal")
                                    && rebound.getInt("BarrierHealth") == legacy.getInt("BarrierHealth"),
                            "Legacy barrier lost its user ownership, endpoints, attachment, or health");
                    watchRecalledBarriers(helper, fixture, 100, () -> succeed(helper, fixture));
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void barrierExcludesOwnerAndOwnStandButDetectsForeignContact(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        placePair(helper, fixture, () -> {
            StoneFreeStringEntity barrier = attachedBarrier(helper, fixture);
            Vec3 middle = middle(fixture);
            fixture.owner.setPos(middle.subtract(0, fixture.owner.getBbHeight() * 0.5D, 0));
            fixture.stand.setPos(middle.subtract(0, fixture.stand.getBbHeight() * 0.5D, 0));
            barrier.tick();
            helper.assertTrue(barrier.getOwner() == fixture.owner
                            && barrier.barrierHealth() == 3 && !barrier.isBarrierAlerted(),
                    "Owner or current own Stand triggered the placed barrier");
            fixture.owner.setPos(fixture.home);
            fixture.stand.setPos(fixture.owner.position());
            // 1.16 SFUBarrierEntity.isStand: a Stand power alone is not a Stand body.
            Cow entrant = toucher(helper, fixture, EntityType.COW, middle, 0.0D);
            PowerClass.STAND.attachGet(entrant).setStand(StoneFreeAddon.STONE_FREE.get());
            entrant.removeEffect(ModStatusEffects.INTEGRATED_STAND);
            entrant.setDeltaMovement(0.3D, 0.0D, 0.2D);
            barrier.tick();
            helper.assertTrue(barrier.isBarrierAlerted(), "Foreign contact did not alert the placed barrier");
            helper.assertTrue(barrier.barrierHealth() == 3 && !barrier.wasRipped(),
                    "A toucher that is neither a Stand nor integrated with one wore the barrier down: health="
                            + barrier.barrierHealth());
            helper.assertTrue(entrant.getDeltaMovement().lengthSqr() == 0.0D,
                    "Contact did not stop the toucher: motion=" + entrant.getDeltaMovement());
            succeed(helper, fixture);
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void barrierContactIsTheStringsRayAgainstEntityBoxes(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        placePair(helper, fixture, () -> {
            StoneFreeStringEntity barrier = attachedBarrier(helper, fixture);
            Vec3 middle = middle(fixture);
            // The string runs through the edge of this 1.4 wide box; the box centre stays 0.65 away from it.
            PolarBear grazer = toucher(helper, fixture, EntityType.POLAR_BEAR, middle, 0.65D);
            grazer.setDeltaMovement(0.3D, 0.0D, 0.2D);
            barrier.tick();
            helper.assertTrue(barrier.isBarrierAlerted() && barrier.barrierHealth() == 3,
                    "A box the string passes through did not alert the barrier: box=" + grazer.getBoundingBox()
                            + ", string at x=" + middle.x + " z=" + middle.z + ", health=" + barrier.barrierHealth());
            helper.assertTrue(grazer.getDeltaMovement().lengthSqr() == 0.0D,
                    "Contact did not stop the toucher: motion=" + grazer.getDeltaMovement());
            tick(barrier, DONOR_ALERT_TICKS);
            helper.assertTrue(!barrier.isBarrierAlerted() && barrier.isAlive(),
                    "The alert of an ordinary contact outlived its 40 ticks or removed the barrier");
            // One block away the box ends 0.3 short of the string, beyond the string's own half width.
            grazer.setPos(grazer.position().add(0.35D, 0.0D, 0.0D));
            grazer.setDeltaMovement(0.3D, 0.0D, 0.2D);
            barrier.tick();
            helper.assertTrue(!barrier.isBarrierAlerted() && grazer.getDeltaMovement().lengthSqr() > 0.0D,
                    "A box clear of the string still counted as contact: box=" + grazer.getBoundingBox());
            succeed(helper, fixture);
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void barrierAlertPausesContactAndOnlyStandsWearItDown(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        placePair(helper, fixture, () -> {
            StoneFreeStringEntity barrier = attachedBarrier(helper, fixture);
            Vec3 middle = middle(fixture);
            Cow integrated = toucher(helper, fixture, EntityType.COW, middle, 0.0D);
            integrated.addEffect(integratedStand());
            barrier.tick();
            helper.assertTrue(barrier.barrierHealth() == 2 && barrier.isBarrierAlerted(),
                    "A toucher with integrated_stand did not alert the barrier and take one of its three points:"
                            + " health=" + barrier.barrierHealth() + ", alerted=" + barrier.isBarrierAlerted());
            integrated.setDeltaMovement(0.2D, 0.0D, 0.0D);
            for (int alertTick = 1; alertTick < DONOR_ALERT_TICKS; alertTick++) {
                barrier.tick();
                helper.assertTrue(barrier.barrierHealth() == 2 && barrier.isBarrierAlerted(),
                        "Alert tick " + alertTick + " of 40 ended the alert or took a point: health="
                                + barrier.barrierHealth() + ", alerted=" + barrier.isBarrierAlerted());
            }
            helper.assertTrue(integrated.getDeltaMovement().x == 0.2D,
                    "The barrier kept handling contact while its alert counted: motion="
                            + integrated.getDeltaMovement());
            barrier.tick();
            helper.assertTrue(!barrier.isBarrierAlerted() && barrier.barrierHealth() == 2,
                    "The alert did not end on its 40th tick");
            barrier.tick();
            helper.assertTrue(barrier.barrierHealth() == 1 && barrier.isBarrierAlerted()
                            && integrated.getDeltaMovement().lengthSqr() == 0.0D,
                    "The toucher still in the string did not take the next point once the alert had ended: health="
                            + barrier.barrierHealth() + ", motion=" + integrated.getDeltaMovement());
            tick(barrier, DONOR_ALERT_TICKS);
            integrated.discard();
            StoneFreeStandEntity rivalStand = rivalStand(helper, fixture);
            rivalStand.setPos(middle.subtract(0.0D, rivalStand.getBbHeight() * 0.5D, 0.0D));
            barrier.tick();
            helper.assertTrue(barrier.barrierHealth() == 0 && barrier.wasRipped() && barrier.isAlive(),
                    "Another user's Stand did not take the last point and rip the barrier: health="
                            + barrier.barrierHealth() + ", ripped=" + barrier.wasRipped()
                            + ", alive=" + barrier.isAlive());
            succeed(helper, fixture);
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void rippedBarrierHurtsUserOnceAndBreaksWhenAlertEnds(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        placePair(helper, fixture, () -> {
            StoneFreeStringEntity barrier = attachedBarrier(helper, fixture);
            // A Stone Free user that has ticked: it has both the power and integrated_stand.
            Cow ripper = toucher(helper, fixture, EntityType.COW, middle(fixture), 0.0D);
            PowerClass.STAND.attachGet(ripper).setStand(StoneFreeAddon.STONE_FREE.get());
            ripper.addEffect(integratedStand());
            Vec3 inside = ripper.position();
            Vec3 outside = fixture.home.add(0.0D, 0.0D, 2.0D);
            for (int left = 2; left > 0; left--) {
                ripper.setPos(inside);
                barrier.tick();
                helper.assertTrue(barrier.barrierHealth() == left && !barrier.wasRipped(),
                        "Contact " + (3 - left) + " of 3 left health " + barrier.barrierHealth());
                ripper.setPos(outside);
                tick(barrier, DONOR_ALERT_TICKS);
                helper.assertTrue(!barrier.isBarrierAlerted() && barrier.isAlive(),
                        "An unripped barrier did not come out of its alert after 40 ticks");
            }
            // Fresh invulnerability frames with a larger last hit: a plain 0.12 hit is refused now.
            helper.assertTrue(fixture.owner.hurt(fixture.owner.damageSources().generic(), 1.0F)
                            && fixture.owner.invulnerableTime == 20,
                    "Could not put the barrier's user into invulnerability frames");
            float health = fixture.owner.getHealth();
            ripper.setPos(inside);
            barrier.tick();
            helper.assertTrue(barrier.wasRipped() && barrier.barrierHealth() == 0 && barrier.isBarrierAlerted(),
                    "The third Stand contact did not rip the barrier");
            helper.assertTrue(barrier.isAlive() && fixture.owner.getHealth() == health
                            && StoneFreeState.get(fixture.owner).placedStrings() == 1,
                    "The rip acted before its alert began to count: alive=" + barrier.isAlive()
                            + ", user health " + health + " -> " + fixture.owner.getHealth());
            barrier.tick();
            helper.assertTrue(Math.abs(fixture.owner.getHealth() - (health - 0.12F)) < 1.0E-4F
                            && fixture.owner.invulnerableTime == 20,
                    "The rip did not hurt the user 0.12 through invulnerability frames on its first alert tick:"
                            + " user health " + health + " -> " + fixture.owner.getHealth()
                            + ", invulnerableTime=" + fixture.owner.invulnerableTime);
            for (int alertTick = 2; alertTick < DONOR_ALERT_TICKS; alertTick++) {
                barrier.tick();
                helper.assertTrue(barrier.isAlive()
                                && Math.abs(fixture.owner.getHealth() - (health - 0.12F)) < 1.0E-4F,
                        "Alert tick " + alertTick + " of 40 removed the ripped barrier or hurt the user again:"
                                + " alive=" + barrier.isAlive() + ", user health=" + fixture.owner.getHealth());
            }
            barrier.tick();
            helper.assertTrue(!barrier.isAlive() && StoneFreeState.get(fixture.owner).placedStrings() == 0,
                    "The ripped barrier did not break and release its invested segment when its alert ended");
            succeed(helper, fixture);
        });
    }

    // Its own batch: a time stop freezes every test cell of its chunk.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100,
            batch = "rotp_sf_time_stop")
    public static void barrierRaisesItsAlertInStoppedTimeAndCountsItOnlyAfterTimeResumes(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        placePair(helper, fixture, () -> {
            StoneFreeStringEntity barrier = attachedBarrier(helper, fixture);
            Vec3 middle = middle(fixture);
            Vec3 onTheString = middle.subtract(0.0D, fixture.stand.getBbHeight() * 0.5D, 0.0D);
            // Premise of the own-Stand check below: the string's ray does meet the user's own Stand.
            fixture.stand.setPos(onTheString);
            fixture.stand.setDeltaMovement(0.3D, 0.0D, 0.2D);
            barrier.tick();
            helper.assertTrue(fixture.stand.getDeltaMovement().lengthSqr() == 0.0D && !barrier.isBarrierAlerted(),
                    "The string's ray did not meet the user's own Stand, or the Stand tripped it: Stand motion="
                            + fixture.stand.getDeltaMovement() + ", alerted=" + barrier.isBarrierAlerted());
            fixture.stand.setPos(fixture.owner.position());

            TimeStopState time = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
            try {
                helper.assertTrue(time.tryPutInstance(new TimeStopState.Instance(TIME_STOP_ID, 200, 200,
                                new ChunkPos(barrier.blockPosition()), 1, -1, "sf_barrier_test"))
                                && time.isTimeStopped(barrier) && time.shouldFreeze(fixture.owner),
                        "Could not stop time around the barrier and its user");
                helper.assertTrue(!time.shouldFreeze(barrier),
                        "A placed barrier is frozen with its user in stopped time: the server does not tick it, so"
                                + " whoever moves in the stopped time crosses it unnoticed");
                fixture.stand.setPos(onTheString);
                stoppedTick(time, barrier);
                helper.assertTrue(!barrier.isBarrierAlerted(),
                        "The user's own Stand tripped the barrier in stopped time");
                fixture.stand.setPos(fixture.owner.position());

                // A Stand body: in normal time its contact would also take one of the three points.
                Cow intruder = toucher(helper, fixture, EntityType.COW, middle, 0.0D);
                intruder.addEffect(integratedStand());
                intruder.setDeltaMovement(0.3D, 0.0D, 0.2D);
                stoppedTick(time, barrier);
                helper.assertTrue(alertTicks(barrier) == DONOR_ALERT_TICKS && barrier.barrierHealth() == 3,
                        "A Stand body on the string in stopped time did not just start the 40 tick alert: alert"
                                + " ticks " + alertTicks(barrier) + ", health " + barrier.barrierHealth());
                helper.assertTrue(intruder.getDeltaMovement().lengthSqr() > 0.0D,
                        "The stopped-time alert also stopped its toucher; 1.16 only raised the alert there");
                for (int tick = 0; tick < DONOR_ALERT_TICKS + 5; tick++) {
                    stoppedTick(time, barrier);
                }
                helper.assertTrue(alertTicks(barrier) == DONOR_ALERT_TICKS && barrier.barrierHealth() == 3,
                        "The alert counted down or the barrier wore down while time was stopped: alert ticks "
                                + alertTicks(barrier) + ", health " + barrier.barrierHealth());

                time.removeInstance(TIME_STOP_ID);
                intruder.setPos(fixture.home.add(0.0D, 0.0D, 2.0D));
                barrier.tick();
                helper.assertTrue(alertTicks(barrier) == DONOR_ALERT_TICKS - 1,
                        "The alert did not begin to count when time resumed: alert ticks " + alertTicks(barrier));
                tick(barrier, DONOR_ALERT_TICKS - 1);
                helper.assertTrue(!barrier.isBarrierAlerted() && barrier.isAlive() && barrier.barrierHealth() == 3,
                        "The alert raised in stopped time did not end 40 ticks after time resumed: alert ticks "
                                + alertTicks(barrier) + ", alive=" + barrier.isAlive());
            }
            finally {
                time.removeInstance(TIME_STOP_ID);
            }
            succeed(helper, fixture);
        });
    }

    // What ServerLevelTimeStopTickMixin does with an entity on each tick of stopped time.
    private static void stoppedTick(TimeStopState time, StoneFreeStringEntity barrier) {
        if (!time.interruptTickEarly(barrier)) {
            barrier.tick();
        }
    }

    private static int alertTicks(StoneFreeStringEntity barrier) {
        return barrier.saveWithoutId(new CompoundTag()).getInt("AlertTicks");
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void barriersOfAUserWhoLostStoneFreeHurtThatUserOnceEach(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        placePair(helper, fixture, () -> {
            List<StoneFreeStringEntity> barriers = barriers(helper, fixture);
            helper.assertTrue(barriers.size() == 2, "The fixture did not leave an attached barrier and an open tail");
            // Fresh invulnerability frames with a larger last hit: a plain 0.12 hit is refused now.
            helper.assertTrue(fixture.owner.hurt(fixture.owner.damageSources().generic(), 1.0F)
                            && fixture.owner.invulnerableTime == 20,
                    "Could not put the barriers' user into invulnerability frames");
            float health = fixture.owner.getHealth();
            fixture.power.setStand(null);
            helper.assertTrue(!StoneFreeServices.isStoneFreeUser(fixture.owner), "Could not take Stone Free away");
            barriers.forEach(StoneFreeStringEntity::tick);
            helper.assertTrue(barriers.stream().noneMatch(Entity::isAlive),
                    "A barrier outlived its user's Stone Free");
            helper.assertTrue(Math.abs(fixture.owner.getHealth() - (health - 0.24F)) < 1.0E-4F,
                    "The two barriers of a user who lost Stone Free took " + (health - fixture.owner.getHealth())
                            + " health instead of 0.12 each through invulnerability frames: " + health + " -> "
                            + fixture.owner.getHealth());
            succeed(helper, fixture);
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void barrierBlockContactLeavesAnchorsAlone(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        // A hardness 0 anchor, and one that answers every projectile hit with a redstone pulse.
        helper.getLevel().setBlockAndUpdate(fixture.first, Blocks.SLIME_BLOCK.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(fixture.second, Blocks.TARGET.defaultBlockState());
        placePair(helper, fixture, () -> later(helper, fixture, 5, () -> {
            BlockState first = helper.getLevel().getBlockState(fixture.first);
            BlockState second = helper.getLevel().getBlockState(fixture.second);
            helper.assertTrue(first.is(Blocks.SLIME_BLOCK),
                    "The barrier broke the soft block it is anchored to: now " + first);
            helper.assertTrue(second.is(Blocks.TARGET) && second.getValue(BlockStateProperties.POWER) == 0,
                    "The barrier struck its anchor block like a projectile: now " + second);
            succeed(helper, fixture);
        }));
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void barrierVisualUserFollowsReplacementPlayer(GameTestHelper helper) {
        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SFVisualOwner"));
        Fixture fixture = fixture(helper, true, owner);
        placePair(helper, fixture, () -> {
            StoneFreeStringEntity barrier = attachedBarrier(helper, fixture);
            UUID barrierId = barrier.getUUID();
            int oldId = owner.getId();
            helper.assertTrue(barrier.getOwner() == owner && barrier.getCurrentBarrierUser() == owner,
                    "Real registered barrier did not cache its original player owner");
            FakePlayer replacement = replacePlayer(helper, fixture, owner.getUUID(), false);
            helper.assertTrue(replacement != owner && replacement.getId() != oldId
                            && replacement.getUUID().equals(owner.getUUID())
                            && barrier.isAlive() && barrier.getUUID().equals(barrierId)
                            && barrier.getOwner() == owner && owner.isRemoved(),
                    "Player replacement did not preserve a distinct stale cache on the same barrier");
            LivingEntity resolved = barrier.getCurrentBarrierUser();
            LogUtils.getLogger().info("R639 SF replacement barrier={} oldId={} newId={} resolvedId={} staleRemoved={}",
                    barrierId, oldId, replacement.getId(), resolved == null ? -1 : resolved.getId(), owner.isRemoved());
            helper.assertTrue(resolved == replacement,
                    "Barrier visual owner retained the removed player instead of the current same-UUID player");
            helper.assertTrue(barrier.getOwner() == owner,
                    "Visual resolution mutated authoritative projectile ownership");
            replacement.setHealth(0.0F);
            helper.assertTrue(barrier.getCurrentBarrierUser() == null, "Dead current player remained a visual owner");
            replacement.setHealth(100.0F);
            helper.getLevel().removePlayerImmediately(replacement, Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(barrier.getCurrentBarrierUser() == null,
                    "Absent current player fell back to a removed cached owner");
            succeed(helper, fixture);
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void barrierVisualUserRejectsReusedEntityId(GameTestHelper helper) {
        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SFReuseOwner"));
        Fixture fixture = fixture(helper, true, owner);
        placePair(helper, fixture, () -> {
            StoneFreeStringEntity barrier = attachedBarrier(helper, fixture);
            helper.assertTrue(barrier.getOwner() == owner, "Real barrier did not retain its original owner");
            FakePlayer unrelated = replacePlayer(helper, fixture, UUID.randomUUID(), true);
            helper.assertTrue(unrelated.getId() == owner.getId()
                            && !unrelated.getUUID().equals(owner.getUUID())
                            && barrier.isAlive() && barrier.getOwner() == owner
                            && helper.getLevel().getPlayerByUUID(owner.getUUID()) == null,
                    "Fixture did not reuse only the numeric ID while keeping the synced UUID absent");
            LivingEntity resolved = barrier.getCurrentBarrierUser();
            LogUtils.getLogger().info("R639 SF id reuse barrier={} id={} owner={} unrelated={} resolved={}",
                    barrier.getUUID(), owner.getId(), owner.getUUID(), unrelated.getUUID(),
                    resolved == null ? null : resolved.getUUID());
            helper.assertTrue(resolved == null,
                    "Barrier accepted a removed owner or an unrelated player reusing its entity ID");
            helper.assertTrue(barrier.getOwner() == owner, "Visual lookup rebound gameplay ownership");
            succeed(helper, fixture);
        });
    }

    private static FakePlayer replacePlayer(GameTestHelper helper, Fixture fixture, UUID uuid, boolean reuseId) {
        ServerPlayer original = (ServerPlayer) fixture.owner;
        int originalId = original.getId();
        helper.getLevel().removePlayerImmediately(original, Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(original.isRemoved() && helper.getLevel().getEntity(originalId) == null,
                "Original player was not removed from the actual server entity map");
        // A new instance is required: FakePlayerFactory would reuse the profile's cached object.
        FakePlayer replacement = new FakePlayer(helper.getLevel(), new GameProfile(uuid, "SFNewOwner"));
        fixture.replacementPlayers.add(replacement);
        if (reuseId) replacement.setId(originalId);
        replacement.setPos(fixture.home);
        replacement.setNoGravity(true);
        replacement.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        replacement.setHealth(100.0F);
        helper.getLevel().addNewPlayer(replacement);
        PowerClass.STAND.attachGet(replacement).setStand(StoneFreeAddon.STONE_FREE.get());
        helper.assertTrue(helper.getLevel().getEntity(replacement.getId()) == replacement
                        && helper.getLevel().getPlayerByUUID(uuid) == replacement,
                "Replacement was not the actual current player in the level");
        return replacement;
    }

    private static Vec3 middle(Fixture fixture) {
        return Vec3.atCenterOf(fixture.first).lerp(Vec3.atCenterOf(fixture.second), 0.5D);
    }

    // A motionless mob centred on the string's height, moved sideways from the string by the given distance.
    private static <T extends Mob> T toucher(GameTestHelper helper, Fixture fixture, EntityType<T> type,
            Vec3 middle, double sideways) {
        T mob = type.create(helper.getLevel());
        helper.assertTrue(mob != null, "Missing contact fixture " + type);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPos(middle.add(sideways, -mob.getBbHeight() * 0.5D, 0.0D));
        helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not spawn contact fixture " + type);
        fixture.extras.add(mob);
        return mob;
    }

    private static MobEffectInstance integratedStand() {
        return new MobEffectInstance(ModStatusEffects.INTEGRATED_STAND, 2000, 0, false, false, false);
    }

    private static void tick(StoneFreeStringEntity barrier, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            barrier.tick();
        }
    }

    private static StoneFreeStandEntity rivalStand(GameTestHelper helper, Fixture fixture) {
        Cow rival = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(rival != null, "Missing rival Stand user fixture");
        rival.setNoAi(true);
        rival.setNoGravity(true);
        rival.setPos(fixture.home.add(0.0D, 0.0D, 2.0D));
        helper.assertTrue(helper.getLevel().addFreshEntity(rival), "Could not add the rival Stand user");
        fixture.rival = rival;
        StandPower power = PowerClass.STAND.attachGet(rival);
        power.setStand(StoneFreeAddon.STONE_FREE.get());
        power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(StoneFreeAddon.STONE_FREE.get().summon(rival, power),
                "Could not summon the rival's Stand");
        StoneFreeStandEntity stand = StoneFreeServices.controller(rival);
        helper.assertTrue(stand != null && !stand.isArmsOnlyMode(), "Rival summon produced no full Stand");
        return stand;
    }

    static Fixture fixture(GameTestHelper helper, boolean summon) {
        Cow owner = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(owner != null, "Missing Stone Free barrier owner fixture");
        owner.setNoAi(true);
        return fixture(helper, summon, owner);
    }

    static Fixture fixture(GameTestHelper helper, boolean summon, LivingEntity owner) {
        // Keep actors and both endpoints inside the empty template's forced chunk.
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int columnX = (origin.getX() & ~15) + Mth.clamp(origin.getX() & 15, 2, 13);
        int columnZ = (origin.getZ() & ~15) + Mth.clamp(origin.getZ() & 15, 2, 13);
        return fixture(helper, summon, owner, new BlockPos(columnX, origin.getY(), columnZ));
    }

    /** The owner stands one block east of the column and two above its foot. */
    static Fixture fixture(GameTestHelper helper, boolean summon, LivingEntity owner, BlockPos column) {
        owner.setNoGravity(true);
        int columnX = column.getX();
        int columnZ = column.getZ();
        BlockPos origin = column;
        Vec3 home = new Vec3(columnX + 1.5D, origin.getY() + 2.0D, columnZ + 0.5D);
        owner.setPos(home);
        owner.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        owner.setHealth(100.0F);
        if (owner instanceof ServerPlayer player) {
            helper.getLevel().addNewPlayer(player);
            helper.assertTrue(helper.getLevel().getEntity(player.getId()) == player,
                    "Could not add the barrier player owner");
        }
        else {
            helper.assertTrue(helper.getLevel().addFreshEntity(owner), "Could not add the barrier owner");
        }
        StandPower power = PowerClass.STAND.attachGet(owner);
        power.setStand(StoneFreeAddon.STONE_FREE.get());
        power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
        power.skipProgression();
        power.setResolveLevel(4);
        power.setStamina(power.getMaxStamina());
        Fixture fixture = new Fixture(owner, power,
                new BlockPos(columnX, origin.getY() + 4, columnZ),
                new BlockPos(columnX, origin.getY() + 7, columnZ), home, helper);
        helper.getLevel().setBlockAndUpdate(fixture.first, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(fixture.second, Blocks.STONE.defaultBlockState());
        if (summon) {
            helper.assertTrue(StoneFreeAddon.STONE_FREE.get().summon(owner, power),
                    "Could not normally summon Stone Free");
            fixture.stand = StoneFreeServices.controller(owner);
            helper.assertTrue(fixture.stand != null, "Normal summon produced no controller");
        }
        return fixture;
    }

    static void placePair(GameTestHelper helper, Fixture fixture, Runnable assertions) {
        int delay = fixture.stand != null ? fixture.stand.summonLockTicks + 1 : 1;
        later(helper, fixture, delay, () -> {
            place(helper, fixture, fixture.first);
            fixture.stand = StoneFreeServices.controller(fixture.owner);
            helper.assertTrue(fixture.stand != null, "Registered placement did not supply its controller");
            later(helper, fixture, fixture.stand.summonLockTicks + 6, () -> {
                helper.assertTrue(barriers(helper, fixture).size() == 1,
                        "First public cast did not create exactly one open barrier");
                place(helper, fixture, fixture.second);
                later(helper, fixture, 6, () -> {
                    List<StoneFreeStringEntity> barriers = barriers(helper, fixture);
                    helper.assertTrue(barriers.size() == 2
                                    && barriers.stream().allMatch(barrier -> barrier.getOwner() == fixture.owner)
                                    && barriers.stream().allMatch(barrier -> barrier.getCurrentBarrierUser() == fixture.owner)
                                    && StoneFreeState.get(fixture.owner).placedStrings() == 1
                                    && StoneFreeState.get(fixture.owner).openBarrier().isPresent(),
                            "Second registered placement lost user ownership or segment accounting");
                    fixture.retained = barriers;
                    assertions.run();
                });
            });
        });
    }

    private static void place(GameTestHelper helper, Fixture fixture, BlockPos endpoint) {
        boolean ownerTicking = helper.getLevel().isPositionEntityTicking(fixture.owner.blockPosition());
        boolean targetTicking = helper.getLevel().isPositionEntityTicking(endpoint);
        helper.assertTrue(ownerTicking && targetTicking,
                "Barrier fixture is outside entity-ticking chunks: owner=" + fixture.owner.getUUID()
                        + "@" + fixture.owner.blockPosition() + "/" + ownerTicking
                        + ", endpoint=" + endpoint + "/" + targetTicking);
        // This is the public aim input consumed by the registered action's normal snapshot.
        LivingComponentAction.getComponent(fixture.owner).entityAim.setTarget(
                new ActionTarget(endpoint, Direction.UP));
        var ability = fixture.power.getAbility(StoneFreeAddon.USER_BARRIER_NAME);
        helper.assertTrue(ability instanceof StoneFreeStringAbility
                        && ability.checkConditions(fixture.power).isPositive(),
                "Registered user-barrier conditions refused the fixture");
        BufferingState buffering = BufferingState.clickOnly();
        ability.onKeyPress(helper.getLevel(), fixture.owner, null, InputMethod.CLICK, 0.0F, buffering);
        helper.assertTrue(buffering.isActionSuccess, "Public phased user-barrier cast failed");
    }

    private static void recall(GameTestHelper helper, Fixture fixture, Runnable assertions) {
        StoneFreeAddon.STONE_FREE.get().onUserSummonCommand(fixture.owner, fixture.power);
        later(helper, fixture, 25, () -> {
            assertNoController(helper, fixture);
            assertions.run();
        });
    }

    private static void watchRecalledBarriers(GameTestHelper helper, Fixture fixture, int ticks, Runnable assertions) {
        assertNoController(helper, fixture);
        for (StoneFreeStringEntity barrier : fixture.retained) {
            helper.assertTrue(barrier.isAlive() && barrier.getOwner() == fixture.owner,
                    "Recall removed or rebound a retained user barrier");
        }
        if (fixture.loaded != null) {
            helper.assertTrue(fixture.loaded.isAlive() && fixture.loaded.getOwner() == fixture.owner,
                    "Loaded legacy barrier did not remain user-owned");
        }
        if (ticks == 0) {
            assertions.run();
        }
        else {
            later(helper, fixture, 1, () -> watchRecalledBarriers(helper, fixture, ticks - 1, assertions));
        }
    }

    private static void assertNoController(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
                "Barrier automatically re-created the linked manifestation");
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof StoneFreeStandEntity stand && stand.getUser() == fixture.owner) {
                helper.assertTrue(!stand.isAlive(), "Barrier automatically re-created a world controller");
            }
        }
    }

    private static StoneFreeStringEntity attachedBarrier(GameTestHelper helper, Fixture fixture) {
        return barriers(helper, fixture).stream().filter(StoneFreeStringEntity::isBarrierAttached)
                .findFirst().orElseThrow(() -> new AssertionError("Missing production attached barrier"));
    }

    static List<StoneFreeStringEntity> barriers(GameTestHelper helper, Fixture fixture) {
        List<StoneFreeStringEntity> result = new ArrayList<>();
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof StoneFreeStringEntity barrier && barrier.isAlive()
                    && barrier.isBarrierFor(fixture.owner.getUUID())) {
                result.add(barrier);
            }
        }
        return result;
    }

    static void later(GameTestHelper helper, Fixture fixture, int ticks, Runnable assertions) {
        helper.runAfterDelay(ticks, () -> {
            try {
                assertions.run();
            } catch (Throwable error) {
                cleanup(helper, fixture);
                throw error;
            }
        });
    }

    static void succeed(GameTestHelper helper, Fixture fixture) {
        try {
            helper.succeed();
        } finally {
            cleanup(helper, fixture);
        }
    }

    private static void cleanup(GameTestHelper helper, Fixture fixture) {
        AbilityInput.keyRelease(REMOVE_KEY, fixture.owner);
        AbilityInput.keyRelease(GRAPPLE_KEY, fixture.owner);
        LivingComponentAction.getComponent(fixture.owner).entityAim.setTarget(ActionTarget.EMPTY);
        StoneFreeServices.discardBarriers(fixture.owner);
        StoneFreeServices.discardUserGrapples(fixture.owner);
        if (fixture.power.hasPower()) {
            fixture.power.getPowerType().forceUnsummon(fixture.owner, fixture.power);
        }
        if (fixture.entrant != null) {
            fixture.entrant.discard();
        }
        for (Entity extra : fixture.extras) {
            extra.discard();
        }
        if (fixture.rival != null) {
            StandPower rivalPower = StandPower.get(fixture.rival);
            if (rivalPower != null && rivalPower.hasPower()) {
                rivalPower.getPowerType().forceUnsummon(fixture.rival, rivalPower);
            }
            fixture.rival.discard();
        }
        fixture.replacedBlocks.forEach(helper.getLevel()::setBlockAndUpdate);
        for (ServerPlayer replacement : fixture.replacementPlayers) {
            helper.getLevel().removePlayerImmediately(replacement, Entity.RemovalReason.DISCARDED);
        }
        if (fixture.owner instanceof ServerPlayer player) {
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        else {
            fixture.owner.discard();
        }
        helper.getLevel().setBlockAndUpdate(fixture.first, fixture.originalFirst);
        helper.getLevel().setBlockAndUpdate(fixture.second, fixture.originalSecond);
        fixture.afterwards.forEach(Runnable::run);
        fixture.afterwards.clear();
    }

    static final class Fixture {
        final LivingEntity owner;
        final StandPower power;
        final BlockPos first;
        final BlockPos second;
        final Vec3 home;
        final BlockState originalFirst;
        final BlockState originalSecond;
        StoneFreeStandEntity stand;
        StoneFreeStringEntity loaded;
        Cow entrant;
        LivingEntity rival;
        List<StoneFreeStringEntity> retained = List.of();
        final List<ServerPlayer> replacementPlayers = new ArrayList<>();
        final List<Entity> extras = new ArrayList<>();
        final Map<BlockPos, BlockState> replacedBlocks = new LinkedHashMap<>();
        // Server-wide settings and chunk tickets a test changed, undone last.
        final List<Runnable> afterwards = new ArrayList<>();

        // Keeps the first state seen at a position, so cleanup puts the level back as it was.
        void setBlock(GameTestHelper helper, BlockPos pos, BlockState state) {
            replacedBlocks.putIfAbsent(pos.immutable(), helper.getLevel().getBlockState(pos));
            helper.getLevel().setBlockAndUpdate(pos, state);
        }

        Fixture(LivingEntity owner, StandPower power, BlockPos first, BlockPos second, Vec3 home, GameTestHelper helper) {
            this.owner = owner;
            this.power = power;
            this.first = first;
            this.second = second;
            this.home = home;
            this.originalFirst = helper.getLevel().getBlockState(first);
            this.originalSecond = helper.getLevel().getBlockState(second);
        }
    }
}
