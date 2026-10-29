package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import rotp.addons.d4c.ability.D4CPhase2BAbility;
import rotp.addons.d4c.ability.D4CPhase2BAbility.Mode;
import rotp.addons.d4c.block.D4CPortalBlock;
import rotp.addons.d4c.entity.D4CMarkerEntity;
import rotp.addons.d4c.travel.D4CDimensionTargets;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CPullInParityGameTests {
    private static final String SELECTED = "rotp_d4c.phase2a.selected_dimension";
    private static final AtomicInteger HEIGHT = new AtomicInteger();
    private static final TicketType<UUID> TICKET = TicketType.create(
            "d4c_pull_parity", Comparator.<UUID>naturalOrder());

    private D4CPullInParityGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void committedPullCompletesAfterDeathAtAgeOne(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> committedPullCompletesAfterDeathAtAgeOneBody(h));
    }

    private static void committedPullCompletesAfterDeathAtAgeOneBody(GameTestHelper helper) {
        deathCase(helper, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void committedPullCompletesAfterDeathAtAgeFifteen(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> committedPullCompletesAfterDeathAtAgeFifteenBody(h));
    }

    private static void committedPullCompletesAfterDeathAtAgeFifteenBody(GameTestHelper helper) {
        deathCase(helper, 15);
    }

    private static void deathCase(GameTestHelper helper, int deathAge) {
        Fixture f = new Fixture(helper);
        Cow target = f.cow(2.0D);
        f.startAction();
        boolean[] killed = {false};
        f.watch(marker -> {
            if (!killed[0] && marker.ageTicks() == deathAge) {
                helper.assertTrue(target.hurt(f.level.damageSources().genericKill(), Float.MAX_VALUE)
                        && !target.isAlive(), "The committed pull target did not die.");
                f.level.getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(2))
                        .forEach(Entity::discard);
                killed[0] = true;
            }
            f.assertArmBoundary(marker, target.blockPosition(), D4CDimensionTargets.NETHER);
            if (marker.ageTicks() == 16) {
                helper.assertTrue(killed[0], "The death fixture missed marker age " + deathAge);
                System.out.println("CAL-S07-3 deathAge=" + deathAge + " completedAt=16 feet="
                        + target.blockPosition());
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void sameMarkerCompletesAfterOwnerAndTargetLeaveAtAgeEight(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> sameMarkerCompletesAfterOwnerAndTargetLeaveAtAgeEightBody(h));
    }

    private static void sameMarkerCompletesAfterOwnerAndTargetLeaveAtAgeEightBody(GameTestHelper helper) {
        observeSameMarkerAfterOwnerAndTargetLeave(helper, 16);
    }

    @GameTest(template = "empty", timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void sameMarkerDoesNotRepeatCompletionAfterOwnerAndTargetLeave(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> sameMarkerDoesNotRepeatCompletionAfterOwnerAndTargetLeaveBody(h));
    }

    private static void sameMarkerDoesNotRepeatCompletionAfterOwnerAndTargetLeaveBody(GameTestHelper helper) {
        observeSameMarkerAfterOwnerAndTargetLeave(helper, 20);
    }

    private static void observeSameMarkerAfterOwnerAndTargetLeave(GameTestHelper helper, int finalAge) {
        Fixture f = new Fixture(helper);
        Cow target = f.cow(2.0D);
        helper.assertTrue(!D4CAddon.FLAG.get().canSerialize(), "Pending pull markers must retain noSave.");
        f.startAction();
        BlockPos[] feet = {target.blockPosition()};
        D4CMarkerEntity[] original = {null};
        UUID[] originalUuid = {null};
        boolean[] left = {false};
        f.watch(marker -> {
            if (original[0] == null) {
                original[0] = marker;
                originalUuid[0] = marker.getUUID();
            }
            helper.assertTrue(marker == original[0] && f.marker == original[0]
                    && marker.getUUID().equals(originalUuid[0])
                    && f.level.getEntity(originalUuid[0]) == original[0],
                    "The committed marker was replaced instead of continuing to tick.");
            f.assertArmBoundary(marker, feet[0], D4CDimensionTargets.NETHER);
            if (!left[0] && marker.ageTicks() == 8) {
                feet[0] = target.blockPosition();
                f.level.removePlayerImmediately(f.owner, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                target.discard();
                left[0] = true;
            }
            if (marker.ageTicks() >= 9) {
                helper.assertTrue(left[0]
                        && f.level.getPlayerByUUID(f.owner.getUUID()) == null
                        && f.level.getServer().getPlayerList().getPlayer(f.owner.getUUID()) == null
                        && f.level.getEntity(target.getUUID()) == null,
                        "The owner or target remains available to the original marker.");
            }
            if (marker.ageTicks() == 16) {
                System.out.println("A2-D4C-TESTS sameMarker=" + originalUuid[0]
                        + " ownerTargetLeftAt=8 portalAndOneSoundAt=16 noSave=true");
                if (finalAge > 16) {
                    f.setBlock(feet[0], Blocks.AIR.defaultBlockState());
                }
            }
            if (marker.ageTicks() > 16) {
                helper.assertTrue(f.level.getBlockState(feet[0]).isAir() && f.teleportSounds.size() == 1,
                        "The same completed marker repeated portal placement or teleport sound.");
            }
            if (marker.ageTicks() == finalAge) {
                if (finalAge > 16) {
                    System.out.println("A2-D4C-TESTS sameMarker=" + originalUuid[0]
                            + " placedLatchStayedOneThroughAge=" + finalAge);
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void presentOwnerSelectionIsReadLate(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> presentOwnerSelectionIsReadLateBody(h));
    }

    private static void presentOwnerSelectionIsReadLateBody(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Cow target = f.cow(2.0D);
        helper.assertTrue(f.level.getServer().getLevel(Level.END) != null, "No End fixture level.");
        f.startAction();
        f.watch(marker -> {
            if (marker.ageTicks() == 15) {
                f.owner.getPersistentData().putString(SELECTED, D4CDimensionTargets.END.toString());
            }
            f.assertArmBoundary(marker, target.blockPosition(), D4CDimensionTargets.END);
            if (marker.ageTicks() == 16) {
                System.out.println("CAL-S07-3 presentOwner selection changedAt=15 placed=end at=16");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void vanishedTargetUsesLastObservedExactFeet(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> vanishedTargetUsesLastObservedExactFeetBody(h));
    }

    private static void vanishedTargetUsesLastObservedExactFeetBody(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Cow target = f.cow(2.0D);
        f.startAction();
        BlockPos[] feet = {target.blockPosition()};
        boolean[] moved = {false};
        boolean[] vanished = {false};
        f.watch(marker -> {
            if (!moved[0] && marker.ageTicks() == 5) {
                target.setPos(target.getX(), target.getY(), target.getZ() + 1.0D);
                moved[0] = true;
            } else if (!vanished[0] && marker.ageTicks() == 7) {
                feet[0] = target.blockPosition();
                target.discard();
                vanished[0] = true;
            }
            f.assertArmBoundary(marker, feet[0], D4CDimensionTargets.NETHER);
            if (marker.ageTicks() == 16) {
                System.out.println("CAL-S07-3 vanishedTarget lastFeet=" + feet[0]);
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void pullRayIgnoresStoneAndGlass(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pullRayIgnoresStoneAndGlassBody(h));
    }

    private static void pullRayIgnoresStoneAndGlassBody(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            Cow target = f.cow(3.0D);
            BlockPos wall = BlockPos.containing(f.owner.getEyePosition().add(0, 0, 1.0D));
            for (BlockState block : List.of(Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState())) {
                f.setBlock(wall, block);
                f.assertSelected(target);
                f.clearMarkers();
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void pullRaySelectsNearestMinionAndClone(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pullRaySelectsNearestMinionAndCloneBody(h));
    }

    private static void pullRaySelectsNearestMinionAndCloneBody(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            Cow farther = f.cow(3.0D);
            LivingEntity minion = f.add(D4CAddon.SUMMONED_PLAYER.get().create(f.level), 1.5D);
            f.assertSelected(minion);
            f.clearMarkers();
            minion.discard();
            LivingEntity clone = f.add(D4CAddon.ANNIHILATION_CLONE.get().create(f.level), 1.5D);
            f.assertSelected(clone);
            helper.assertTrue(!farther.getPersistentData().getBoolean(D4CParallelItemService.PULLED_BY_D4C_TAG),
                    "Pull-In selected the farther cow instead of the nearer minion/clone.");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void pullRaySkipsSpectatorButKeepsCreativePlayer(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pullRaySkipsSpectatorButKeepsCreativePlayerBody(h));
    }

    private static void pullRaySkipsSpectatorButKeepsCreativePlayerBody(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            Cow target = f.cow(3.0D);
            ServerPlayer nearer = f.rayPlayer(1.5D, GameType.SPECTATOR);
            Vec3 start = f.owner.getEyePosition();
            Vec3 end = start.add(f.owner.getLookAngle().scale(4.0D));
            var playerHit = nearer.getBoundingBox().clip(start, end);
            var cowHit = target.getBoundingBox().clip(start, end);
            helper.assertTrue(nearer.isSpectator() && playerHit.isPresent() && cowHit.isPresent()
                    && start.distanceToSqr(playerHit.get()) < start.distanceToSqr(cowHit.get()),
                    "The spectator must intersect the real ray before the cow.");
            f.assertSelected(target);
            f.clearMarkers();
            nearer.setGameMode(GameType.CREATIVE);
            helper.assertTrue(!nearer.isSpectator() && nearer.isCreative(),
                    "The same nearer player did not become Creative.");
            f.assertSelected(nearer);
            System.out.println("A1-D4C-SPECTATOR canExecute/execute selected cow behind spectator; Creative control selected player");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void pullRayIncludesOwnStand(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pullRayIncludesOwnStandBody(h));
    }

    private static void pullRayIncludesOwnStandBody(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.cow(3.0D);
            f.summon();
            StandEntity own = f.power.getSummonedStandEntity();
            own.setPos(f.owner.getX(), f.owner.getY() + 0.4D, f.owner.getZ() + 1.5D);
            f.assertSelected(own);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void pullRayUsesCurrentRayInsteadOfSyncedAim(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pullRayUsesCurrentRayInsteadOfSyncedAimBody(h));
    }

    private static void pullRayUsesCurrentRayInsteadOfSyncedAimBody(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            Cow near = f.cow(1.5D);
            Cow far = f.cow(3.0D);
            LivingComponentAction.getComponent(f.owner).entityAim.setTarget(new ActionTarget(far));
            f.assertSelected(near);
            f.clearMarkers();
            Cow offRay = f.cow(2.0D);
            offRay.setPos(offRay.getX() + 1.0D, offRay.getY(), offRay.getZ());
            LivingComponentAction.getComponent(f.owner).entityAim.setTarget(new ActionTarget(offRay));
            f.assertSelected(near);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void pullRayRejectsEndpointAndRawBoxNearMiss(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pullRayRejectsEndpointAndRawBoxNearMissBody(h));
    }

    private static void pullRayRejectsEndpointAndRawBoxNearMissBody(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            Cow endpoint = f.cow(4.0D);
            endpoint.setPos(endpoint.getX(), endpoint.getY(),
                    f.owner.getZ() + 4.0D + endpoint.getBbWidth() / 2.0D);
            helper.assertTrue(!D4CPhase2BService.canExecute(Mode.PULL_IN_TRAVEL, f.owner),
                    "The donor excludes a hit exactly at the four-block endpoint.");
            endpoint.setPos(f.owner.getX() + endpoint.getBbWidth() / 2.0D + 0.02D,
                    endpoint.getY(), f.owner.getZ() + 2.0D);
            helper.assertTrue(!D4CPhase2BService.canExecute(Mode.PULL_IN_TRAVEL, f.owner),
                    "The pull ray inflated the raw target AABB.");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS, batch = "d4cPullParity")
    public static void summonedD4CPullsOrdinaryCowAndHasNoRecovery(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> summonedD4CPullsOrdinaryCowAndHasNoRecoveryBody(h));
    }

    private static void summonedD4CPullsOrdinaryCowAndHasNoRecoveryBody(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Cow target = f.cow(2.0D);
        f.startAction();
        f.watch(marker -> {
            helper.assertTrue(f.markerTag(marker).getUUID("Target").equals(target.getUUID()),
                    "Ordinary cow control pulled D4C itself.");
            if (marker.ageTicks() == 2) {
                D4CPhase2BAbility pull = (D4CPhase2BAbility) f.power.getAbility("pullin_parallel_world_travel");
                var configured = pull.createActionObj();
                pull.initActionFromConfig(configured, f.level, f.owner, f.power.getSummonedStandEntity());
                helper.assertTrue(configured.phasesLength.getFloat(ActionPhase.RECOVERY) == 0,
                        "The configured Pull-In recovery is not exactly zero.");
                var current = LivingComponentAction.getCurEntityAction(f.power.getSummonedStandEntity());
                helper.assertTrue(current == null || current.isOver(),
                        "Pull-In still occupies the Stand in RECOVERY after perform: "
                                + (current == null ? null : current.getPhase()));
                Ability follow = f.power.getAbility("flag_parallel_world_travel");
                f.press(follow, (short) 75);
                var next = LivingComponentAction.getCurEntityAction(f.power.getSummonedStandEntity());
                helper.assertTrue(next != null && next.ability == follow,
                        "The ordinary follow-up remained buffered after Pull-In.");
                System.out.println("CAL-S07-5 13-charge/1-perform/0-recovery; followup admitted at markerAge=2");
                helper.succeed();
            }
        });
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final ServerPlayer owner;
        final UUID ticketId = UUID.randomUUID();
        final ChunkPos chunk;
        final List<Entity> entities = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<Vec3> teleportSounds = new ArrayList<>();
        final Consumer<PlayLevelSoundEvent.AtPosition> soundListener;
        StandPower power;
        D4CMarkerEntity marker;
        boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            BlockPos base = helper.absolutePos(BlockPos.ZERO);
            BlockPos feet = new BlockPos(base.getX(), 100 + HEIGHT.getAndIncrement() * 6, base.getZ());
            chunk = new ChunkPos(feet);
            helper.testInfo.addListener(this);
            // The ray and moved-target case can cross the origin chunk boundary.
            level.getChunkSource().addRegionTicket(TICKET, chunk, 3, ticketId);
            level.getChunkAt(feet);
            GameProfile profile = new GameProfile(UUID.randomUUID(), "d4c-parity");
            owner = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            new SilentListener(level.getServer(), connection, owner,
                    CommonListenerCookie.createInitial(profile, false));
            owner.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 0.0F, 0.0F);
            owner.setNoGravity(true);
            entities.add(owner);
            level.addNewPlayer(owner);
            owner.getPersistentData().putString(SELECTED, D4CDimensionTargets.NETHER.toString());
            helper.assertTrue(level.getServer().getLevel(Level.NETHER) != null, "No Nether fixture level.");
            soundListener = event -> {
                if (event.getLevel() == level && event.getSound() != null
                        && event.getSound().value() == D4CAddon.D4C_TELEPORT.get()
                        && Math.abs(event.getPosition().y - owner.getY()) < 2.0D
                        && event.getPosition().distanceToSqr(owner.position()) < 49.0D) {
                    teleportSounds.add(event.getPosition());
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                    PlayLevelSoundEvent.AtPosition.class, soundListener);
        }

        Cow cow(double distance) {
            Cow cow = add(EntityType.COW.create(level), distance);
            cow.removeFreeWill();
            return cow;
        }

        ServerPlayer rayPlayer(double distance, GameType mode) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "d4c-ray-player");
            ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            new SilentListener(level.getServer(), connection, player,
                    CommonListenerCookie.createInitial(profile, false));
            player.moveTo(owner.getX(), owner.getY() + 0.4D, owner.getZ() + distance, 0.0F, 0.0F);
            player.setNoGravity(true);
            entities.add(player);
            level.addNewPlayer(player);
            player.setGameMode(mode);
            return player;
        }

        <T extends LivingEntity> T add(T entity, double distance) {
            helper.assertTrue(entity != null, "The pull target was not created.");
            entity.setPos(owner.getX(), owner.getY() + 0.4D, owner.getZ() + distance);
            entity.setNoGravity(true);
            entities.add(entity);
            helper.assertTrue(level.addFreshEntity(entity), "The pull target was not added.");
            return entity;
        }

        void summon() {
            power = StandPower.get(owner);
            helper.assertTrue(power != null, "The owner has no Stand attachment.");
            power.setStand(D4CAddon.D4C.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(D4CAddon.D4C.get().summon(owner, power), "D4C was not summoned.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isAddedToLevel(), "The summoned D4C was not added.");
            stand.summonLockTicks = 0;
            entities.add(stand);
        }

        void startAction() {
            summon();
            helper.runAfterDelay(10, () -> {
                Ability ability = power.getAbility("pullin_parallel_world_travel");
                helper.assertTrue(ability instanceof D4CPhase2BAbility, "The real pull ability is missing.");
                var action = ((D4CPhase2BAbility) ability).createActionObj();
                ((D4CPhase2BAbility) ability).initActionFromConfig(action, level, owner, power.getSummonedStandEntity());
                helper.assertTrue(action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE) == 13
                        && action.phasesLength.getFloat(ActionPhase.PERFORM) == 1,
                        "Pull-In charge/perform lengths changed.");
                helper.assertTrue(AbilityInput.withConditionCheck(ability, owner), "Real pull admission failed.");
                press(ability, (short) 74);
            });
        }

        void press(Ability ability, short key) {
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try {
                helper.assertTrue(AbilityInput.keyPress(key, ability, owner, input, InputMethod.HOLD,
                        0.0F, BufferingState.clickCanBuffer(), ability.abilityId) != null,
                        "The real ability input did not create a held action.");
            } finally {
                input.release();
            }
        }

        void watch(Consumer<D4CMarkerEntity> check) {
            int[] observations = {0};
            helper.onEachTick(() -> {
                if (++observations[0] == 70) {
                    StandEntity stand = power == null ? null : power.getSummonedStandEntity();
                    var current = stand == null ? null : LivingComponentAction.getCurEntityAction(stand);
                    System.out.println("D4C-PULL-FIXTURE waiting owner=" + owner.position()
                            + " ownerTicks=" + owner.tickCount + " standTicks=" + (stand == null ? -1 : stand.tickCount)
                            + " phase=" + (current == null ? null : current.getPhase())
                            + " markerAge=" + (marker == null ? -1 : marker.ageTicks()));
                }
                if (marker == null) {
                    marker = markers().stream().findFirst().orElse(null);
                    if (marker != null) entities.add(marker);
                }
                if (marker != null) {
                    helper.assertTrue(!marker.isRemoved(), "A committed PULL marker was discarded before completion.");
                    check.accept(marker);
                }
            });
        }

        void assertArmBoundary(D4CMarkerEntity marker, BlockPos feet, ResourceLocation dimension) {
            setOriginal(feet);
            if (marker.ageTicks() < 16) {
                helper.assertTrue(!(level.getBlockState(feet).getBlock() instanceof D4CPortalBlock),
                        "Pull portal armed before marker tick 16.");
                helper.assertTrue(teleportSounds.isEmpty(), "Pull teleport sound played before marker tick 16.");
            } else if (marker.ageTicks() == 16) {
                helper.assertTrue(level.getBlockState(feet).getBlock() instanceof D4CPortalBlock portal
                        && portal.targetDimension().equals(dimension),
                        "No selected portal at the exact target feet on marker tick 16: " + feet);
                helper.assertTrue(teleportSounds.size() == 1
                        && teleportSounds.getFirst().distanceToSqr(Vec3.atCenterOf(feet)) < 1.0E-8D,
                        "Pull teleport sound did not play once at the exact feet block: " + teleportSounds);
            }
        }

        void assertSelected(LivingEntity expected) {
            helper.assertTrue(D4CPhase2BService.canExecute(Mode.PULL_IN_TRAVEL, owner),
                    "Pull-In admission rejected the donor ray target.");
            helper.assertTrue(D4CPhase2BService.execute(Mode.PULL_IN_TRAVEL, level, owner),
                    "Pull-In execution rejected the donor ray target.");
            List<? extends D4CMarkerEntity> markers = markers();
            entities.addAll(markers);
            helper.assertTrue(markers.size() == 1
                    && markerTag(markers.getFirst()).getUUID("Target").equals(expected.getUUID()),
                    "Pull-In did not capture the nearest current raw ray target: " + expected.getType());
        }

        List<? extends D4CMarkerEntity> markers() {
            return level.getEntities(D4CAddon.FLAG.get(), entity -> entity.mode() == D4CMarkerEntity.Mode.PULL
                    && markerTag(entity).hasUUID("Owner")
                    && markerTag(entity).getUUID("Owner").equals(owner.getUUID()));
        }

        CompoundTag markerTag(D4CMarkerEntity marker) {
            return marker.saveWithoutId(new CompoundTag());
        }

        void clearMarkers() {
            markers().forEach(Entity::discard);
        }

        void setOriginal(BlockPos position) {
            blocks.putIfAbsent(position.immutable(), level.getBlockState(position));
        }

        void setBlock(BlockPos position, BlockState state) {
            setOriginal(position);
            level.setBlockAndUpdate(position, state);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (soundListener != null) NeoForge.EVENT_BUS.unregister(soundListener);
            clearMarkers();
            entities.forEach(Entity::discard);
            blocks.forEach(level::setBlockAndUpdate);
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 3, ticketId);
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class SilentListener extends ServerGamePacketListenerImpl {
        SilentListener(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }
        @Override public void send(Packet<?> packet) {}
        @Override public void send(Packet<?> packet, PacketSendListener listener) {}
    }
}
