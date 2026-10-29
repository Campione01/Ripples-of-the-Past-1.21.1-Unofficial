package rotp.addons.hermitpurple.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.addons.hermitpurple.entity.HermitVineProjectileEntity;
import rotp.core.JojoModConfig;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;

@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitVineAttackGameTests {
    private static final Gson JSON = new Gson();
    private static final double EPS = 1.0E-4D;
    private static final float WEAK_HIT = 1.0F;

    private HermitVineAttackGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void normalClickExtendsThenRetracts(GameTestHelper helper) {
        begin(helper, Case.STATIONARY);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void normalClickFollowsOwnerTranslationAndTurn(GameTestHelper helper) {
        begin(helper, Case.OWNER_MOTION);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void bindingClickWithoutTargetExtendsThenRetracts(GameTestHelper helper) {
        begin(helper, Case.BINDING_STATIONARY);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void bindingClickWithoutTargetFollowsOwnerTranslationAndTurn(GameTestHelper helper) {
        begin(helper, Case.BINDING_OWNER_MOTION);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void normalClickRetractsAfterDamagingAnEntity(GameTestHelper helper) {
        begin(helper, Case.ENTITY_HIT);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void normalClickIsNotRemovedByABlockInItsPath(GameTestHelper helper) {
        begin(helper, Case.BLOCK_PASS);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void framelessSavedLashKeepsLegacyFlight(GameTestHelper helper) {
        begin(helper, Case.LEGACY_LOAD);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void leftHandedClickLeavesTheUsersLeftSide(GameTestHelper helper) {
        begin(helper, Case.LEFT_HAND);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void normalClickKnocksItsTargetAlongTheUsersFacing(GameTestHelper helper) {
        begin(helper, Case.KNOCKBACK);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void bindingClickNeitherKnocksBackNorChargesTheFinisherOnABlockingTarget(GameTestHelper helper) {
        begin(helper, Case.BIND_BLOCKED);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void hitRayStartsAtTheUserNotAtTheController(GameTestHelper helper) {
        begin(helper, Case.RAY_ORIGIN);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void bindingClickAttachesOneVineAndHurtsItsTargetOnce(GameTestHelper helper) {
        begin(helper, Case.BIND_ATTACH);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void normalClickInsideAWeakerHitsCooldownDealsOnlyTheExcess(GameTestHelper helper) {
        begin(helper, Case.COOLDOWN_EXCESS);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void bindingClickPlaysTheThrowSoundAsItsWindupStarts(GameTestHelper helper) {
        begin(helper, Case.SHIFT_SOUND);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 160)
    public static void normalClickPlaysTheThrowSoundAsItsPerformStarts(GameTestHelper helper) {
        begin(helper, Case.ATTACK_SOUND);
    }

    private enum Case {
        STATIONARY("stationary", 12.0D, false, false),
        OWNER_MOTION("owner_motion", 22.0D, false, true),
        BINDING_STATIONARY("binding_stationary", 32.0D, true, false),
        BINDING_OWNER_MOTION("binding_owner_motion", 42.0D, true, true),
        ENTITY_HIT("entity_hit", 52.0D, false, false),
        BLOCK_PASS("block_pass", 62.0D, false, false),
        LEGACY_LOAD("legacy_load", 72.0D, false, false),
        LEFT_HAND("left_hand", 132.0D, false, false),
        KNOCKBACK("knockback", 142.0D, false, false),
        BIND_BLOCKED("bind_blocked", 152.0D, true, false),
        RAY_ORIGIN("ray_origin", 162.0D, false, false),
        BIND_ATTACH("bind_attach", 172.0D, true, false),
        COOLDOWN_EXCESS("cooldown_excess", 182.0D, false, false),
        SHIFT_SOUND("shift_sound", 192.0D, true, false),
        ATTACK_SOUND("attack_sound", 202.0D, false, false);

        final String label;
        final double height;
        final boolean binding;
        final boolean moveOwner;

        Case(String label, double height, boolean binding, boolean moveOwner) {
            this.label = label;
            this.height = height;
            this.binding = binding;
            this.moveOwner = moveOwner;
        }

        boolean readsSounds() {
            return this == SHIFT_SOUND || this == ATTACK_SOUND;
        }
    }

    private static void begin(GameTestHelper helper, Case kind) {
        Fixture fixture = new Fixture(helper, kind);
        helper.testInfo.addListener(fixture);
        fixture.guarded(fixture::prepare);
    }

    private record Actor(UUID uuid, Vec3 position, Vec3 eye,
                         float yaw, float pitch, float bodyYaw, float headYaw) {
        static Actor of(LivingEntity entity) {
            return new Actor(entity.getUUID(), entity.position(), entity.getEyePosition(),
                    entity.getYRot(), entity.getXRot(), entity.yBodyRot, entity.getYHeadRot());
        }
    }

    private record Sample(long worldTime, UUID uuid, int id, int age, int lifespan,
                          double speedFactor, boolean alive, boolean removed,
                          boolean binding, boolean attached, Vec3 tip, Vec3 velocity,
                          Actor player, Actor controller, Vec3 relativePlayer,
                          Vec3 relativeController, String removalReason) {}

    private record Hit(UUID source, float amount, long worldTime) {}

    private record ThrowSound(long worldTime, ActionPhase phase, float phaseTick) {}

    // StandUtil.playStandEntitySound fires no server event: it only sends a packet to the players tracking the
    // Stand. The fake user reads the packets sent to it.
    private static final class SentPackets extends ServerGamePacketListenerImpl {
        final Fixture fixture;

        SentPackets(Fixture fixture, FakePlayer player) {
            super(player.getServer(), player.connection.getConnection(), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.fixture = fixture;
        }

        @Override
        public void send(Packet<?> packet) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(this::send);
            }
            else {
                fixture.sent(packet);
            }
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            send(packet);
        }
    }

    private static final class Trace {
        final HermitVineProjectileEntity vine;
        final List<Sample> live = new ArrayList<>();
        Sample removal;
        Vec3 legacyMotion;

        Trace(HermitVineProjectileEntity vine) { this.vine = vine; }
    }

    private static final class Fixture implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final Case kind;
        final boolean moveOwner;
        final String name;
        final Map<UUID, Trace> traces = new LinkedHashMap<>();
        final Map<UUID, Trace> reloaded = new LinkedHashMap<>();
        final Consumer<EntityJoinLevelEvent> joinListener = this::joined;
        final Consumer<EntityTickEvent.Post> tickListener = this::ticked;
        final Consumer<EntityLeaveLevelEvent> leaveListener = this::left;
        final Consumer<LivingKnockBackEvent> knockListener = this::knocked;
        final Consumer<LivingDamageEvent.Post> damageListener = this::damaged;
        final List<Vec3> pushes = new ArrayList<>();
        final List<Hit> hits = new ArrayList<>();
        final List<Integer> cooldown = new ArrayList<>();
        final List<ThrowSound> throwSounds = new ArrayList<>();
        final Set<Integer> spawnsSent = new HashSet<>();
        SentPackets sentPackets;
        FakePlayer player;
        StandPower power;
        HermitPurpleStandEntity stand;
        LivingEntity target;
        AABB targetBox;
        Chicken decoy;
        float targetHealth;
        float finisherBefore;
        BlockPos lampPos;
        int lampLitTicks;
        RuntimeException observationFailure;
        boolean watching;
        boolean closed;
        boolean translated;
        boolean turned;
        boolean pitched;
        boolean reloadDone;
        boolean fixtureHit;
        long weakHitAt = -1L;
        long firedAt;

        Fixture(GameTestHelper helper, Case kind) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.kind = kind;
            this.moveOwner = kind.moveOwner;
            this.name = kind.label;
        }

        void prepare() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            int chunkX = origin.getX() & ~15;
            int chunkZ = origin.getZ() & ~15;
            double y = origin.getY() + kind.height;
            AABB corridor = new AABB(chunkX + 1, y - 1, chunkZ + 1,
                    chunkX + 15, y + 8, chunkZ + 15);
            helper.assertTrue(level.getBlockStates(corridor).allMatch(state ->
                            state.isAir() && state.getFluidState().isEmpty()),
                    "Lash fixture needs an existing empty, dry corridor");
            helper.assertTrue(level.getEntities((Entity) null, corridor,
                            entity -> !entity.isSpectator()).isEmpty(),
                    "Lash fixture corridor contains another actor");
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPLash" + kind.ordinal()));
            player.setPos(chunkX + 7.5D, y, chunkZ + 3.5D);
            player.setNoGravity(true);
            if (kind == Case.LEFT_HAND) player.setMainArm(HumanoidArm.LEFT);
            facePlayer(0.0F);
            if (kind.readsSounds()) sentPackets = new SentPackets(this, player);
            level.addNewPlayer(player);
            // the user's own chunk counts as sent, so the user tracks the Stand summoned into it
            if (sentPackets != null) sentPackets.chunkSender.dropChunk(player, player.chunkPosition());
            helper.assertTrue(level.isPositionEntityTicking(player.blockPosition()),
                    "Lash owner is outside an entity-ticking chunk");
            power = PowerClass.STAND.attachGet(player);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power),
                    "Normal Hermit summon failed");
            stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "Normal summon produced no Hermit controller");
            helper.assertTrue(sentPackets == null || spawnsSent.contains(stand.getId()),
                    "Fixture: the fake user does not track its Stand, so the Stand's sounds cannot be read");
            later(stand.summonLockTicks + 2, this::click);
        }

        void click() {
            helper.assertTrue(stand.isAlive() && stand.getUser() == player
                            && stand.getAttachedBindingLash() == null,
                    "Normal CLICK fixture lost its controller or acquired a binding lash");
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
            placeObstacle();
            var ability = power.getAbility(kind.binding
                    ? HermitPurpleAddon.HP_VINE_SHIFT_NAME : HermitPurpleAddon.HP_VINE_ATTACK_NAME);
            helper.assertTrue(ability instanceof HermitVineAttackAbility
                            && ability.checkConditions(power).isPositive(),
                    "Registered lash refused the fixture");
            watching = true;
            NeoForge.EVENT_BUS.addListener(joinListener);
            NeoForge.EVENT_BUS.addListener(tickListener);
            NeoForge.EVENT_BUS.addListener(leaveListener);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingKnockBackEvent.class, knockListener);
            NeoForge.EVENT_BUS.addListener(damageListener);
            firedAt = level.getGameTime();
            finisherBefore = stand.getFinisherMeter();
            BufferingState buffering = BufferingState.clickOnly();
            ability.onKeyPress(level, player, null, InputMethod.CLICK, 0.0F, buffering);
            helper.assertTrue(buffering.isActionSuccess, "Registered server CLICK was not accepted");
            LogUtils.getLogger().info("R650_HP_LASH_INPUT case={} world={} player={} controller={}",
                    name, firedAt, player.getUUID(), stand.getUUID());
            later(1, this::monitor);
        }

        // Donor HPVineEntity.RIGHT_OFFSET / LEFT_OFFSET: the vine's owner is the user, so both lashes leave
        // the user's eyes shifted to the main-arm side and turned by the user's yaw.
        Vec3 donorBase(Actor user) {
            Vec3 offset = new Vec3(player.getMainArm() == HumanoidArm.RIGHT ? -0.3D : 0.3D, -0.2D, 0.0D);
            return user.eye().add(offset.yRot(-user.yaw() * Mth.DEG_TO_RAD));
        }

        void placeObstacle() {
            Vec3 muzzle = donorBase(Actor.of(player));
            if (kind == Case.ENTITY_HIT) {
                addTarget(EntityType.IRON_GOLEM.create(level), muzzle, 180.0F);
            }
            if (kind == Case.KNOCKBACK || kind == Case.BIND_BLOCKED || kind == Case.BIND_ATTACH
                    || kind == Case.COOLDOWN_EXCESS) {
                // An iron golem resists all knockback. The villager looks away, so a raised shield stops nothing.
                Villager villager = EntityType.VILLAGER.create(level);
                addTarget(villager, muzzle, 0.0F);
                if (kind == Case.BIND_BLOCKED) {
                    villager.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                    villager.startUsingItem(InteractionHand.OFF_HAND);
                    helper.assertTrue(JojoModUtil.isTargetBlocking(villager), "Lash target did not raise its shield");
                }
            }
            if (kind == Case.RAY_ORIGIN) {
                placeDecoy(muzzle);
            }
            if (kind == Case.BLOCK_PASS) {
                lampPos = BlockPos.containing(muzzle.x, muzzle.y, muzzle.z + 2.5D);
                level.setBlockAndUpdate(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState());
                BlockState lamp = level.getBlockState(lampPos);
                helper.assertTrue(lamp.is(Blocks.REDSTONE_LAMP) && !lamp.getValue(RedstoneLampBlock.LIT),
                        "Unlit lamp obstacle was not placed");
            }
        }

        // A mob between the controller and the lash line: only a ray drawn from the controller crosses it.
        void placeDecoy(Vec3 muzzle) {
            Vec3 forward = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
            Vec3 offset = new Vec3(player.getMainArm() == HumanoidArm.RIGHT ? -0.3D : 0.3D, -0.2D, 0.0D);
            Vec3 controllerOrigin = stand.getEyePosition().add(offset.yRot(-stand.yBodyRot * Mth.DEG_TO_RAD));
            Vec3 tip = muzzle.add(forward.scale(1.6D));
            Vec3 centre = controllerOrigin.lerp(tip, 0.15D);
            decoy = EntityType.CHICKEN.create(level);
            helper.assertTrue(decoy != null, "Decoy could not be created");
            decoy.moveTo(centre.x, centre.y - decoy.getBbHeight() / 2.0D, centre.z, 0.0F, 0.0F);
            decoy.setNoAi(true);
            decoy.setNoGravity(true);
            decoy.setPersistenceRequired();
            helper.assertTrue(level.addFreshEntity(decoy), "Decoy could not be added");
            AABB box = decoy.getBoundingBox();
            helper.assertTrue(box.clip(controllerOrigin, tip).isPresent(),
                    "Decoy is not on a ray from the controller: controller=" + controllerOrigin + " box=" + box);
            AABB clearance = box.inflate(0.3D);
            helper.assertTrue(!clearance.contains(muzzle)
                            && clearance.clip(muzzle, muzzle.add(forward.scale(5.0D))).isEmpty(),
                    "Decoy stands in the user's own line of fire: base=" + muzzle + " box=" + box);
        }

        boolean decoyStruck() {
            return decoy != null && (!decoy.isAlive() || decoy.getHealth() < decoy.getMaxHealth());
        }

        void addTarget(Mob mob, Vec3 muzzle, float yaw) {
            helper.assertTrue(mob != null, "Lash target could not be created");
            mob.moveTo(muzzle.x, player.getY(), muzzle.z + 3.0D, yaw, 0.0F);
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setPersistenceRequired();
            helper.assertTrue(level.addFreshEntity(mob), "Lash target could not be added");
            target = mob;
            targetBox = mob.getBoundingBox();
            targetHealth = mob.getHealth();
        }

        void joined(EntityJoinLevelEvent event) {
            if (!watching || closed || event.getLevel() != level
                    || !(event.getEntity() instanceof HermitVineProjectileEntity vine)
                    || vine.getType() != HermitPurpleAddon.HP_VINE_ENTITY_TYPE.get()
                    || reloaded.containsKey(vine.getUUID())
                    || vine.getOwner() != stand) return;
            observe(() -> {
                if (traces.containsKey(vine.getUUID())) throw new IllegalStateException("Duplicate lash join");
                Trace trace = new Trace(vine);
                traces.put(vine.getUUID(), trace);
                Sample sample = snapshot(vine);
                if (sample.age() != 0) throw new IllegalStateException("Spawn snapshot was not age zero");
                trace.live.add(sample);
                log("spawn", sample);
            });
        }

        void ticked(EntityTickEvent.Post event) {
            if (!watching || closed) return;
            Trace found = traces.get(event.getEntity().getUUID());
            if (found == null) found = reloaded.get(event.getEntity().getUUID());
            Trace trace = found;
            if (trace == null || event.getEntity() != trace.vine || trace.vine.isRemoved()) return;
            observe(() -> {
                Sample sample = snapshot(trace.vine);
                Sample previous = trace.live.getLast();
                if (sample.age() != previous.age() + 1 || sample.worldTime() < previous.worldTime()) {
                    throw new IllegalStateException("Missing or duplicate natural post-tick sample");
                }
                trace.live.add(sample);
                log("post_tick", sample);
            });
        }

        // Every LivingEntity.knockback call on the target, after the core has scaled the one made by the damage.
        void knocked(LivingKnockBackEvent event) {
            if (!watching || closed || fixtureHit || event.getEntity() != target || !(event.getStrength() > 0.0F)) return;
            Vec3 ratio = new Vec3(event.getRatioX(), 0.0D, event.getRatioZ());
            pushes.add(ratio.normalize().scale(-event.getStrength()));
        }

        void sent(Packet<?> packet) {
            if (packet instanceof ClientboundAddEntityPacket add) spawnsSent.add(add.getId());
            if (stand != null && packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof StandEntitySoundPacket sound && sound.entityId() == stand.getId()
                    && sound.sound().value() == HermitPurpleAddon.HP_THROW.get()) {
                EntityActionInstance action = stand.getCurStandAction();
                throwSounds.add(new ThrowSound(level.getGameTime(), action != null ? action.getPhase() : null,
                        action != null ? action.getPhaseTick() : -1.0F));
            }
        }

        // Every hit that took health off the target, whatever dealt it.
        void damaged(LivingDamageEvent.Post event) {
            if (!watching || closed || fixtureHit || event.getEntity() != target) return;
            Entity direct = event.getSource().getDirectEntity();
            hits.add(new Hit(direct != null ? direct.getUUID() : null, event.getNewDamage(), level.getGameTime()));
        }

        void left(EntityLeaveLevelEvent event) {
            if (!watching || closed || event.getLevel() != level) return;
            Trace found = traces.get(event.getEntity().getUUID());
            if (found == null) found = reloaded.get(event.getEntity().getUUID());
            Trace trace = found;
            if (trace == null || event.getEntity() != trace.vine) return;
            observe(() -> {
                if (trace.removal != null) throw new IllegalStateException("Duplicate lash removal");
                trace.removal = snapshot(trace.vine);
                log("removed", trace.removal);
            });
        }

        Sample snapshot(HermitVineProjectileEntity vine) {
            Actor user = Actor.of(player);
            Actor controller = Actor.of(stand);
            return new Sample(level.getGameTime(), vine.getUUID(), vine.getId(), vine.tickCount,
                    vine.ticksLifespan(), vine.getSpeedFactor(), vine.isAlive(), vine.isRemoved(),
                    vine.isBinding(), vine.getEntityAttachedTo() != null, vine.position(),
                    vine.getDeltaMovement(), user, controller,
                    vine.position().subtract(user.position()), vine.position().subtract(controller.position()),
                    String.valueOf(vine.getRemovalReason()));
        }

        void monitor() {
            if (observationFailure != null) throw observationFailure;
            helper.assertTrue(traces.size() <= 2, "A registered CLICK spawned more than two lashes");
            helper.assertTrue(player.isAlive() && !player.isRemoved() && stand.isAlive()
                            && level.isPositionEntityTicking(player.blockPosition()),
                    "Owner/controller/chunk lifecycle invalidated the trajectory fixture");
            if (lampPos != null && level.getBlockState(lampPos).getValue(RedstoneLampBlock.LIT)) lampLitTicks++;
            if (kind == Case.COOLDOWN_EXCESS) weakerHitFirst();
            boolean originalsDone = traces.size() == 2
                    && traces.values().stream().allMatch(trace -> trace.removal != null);
            boolean copiesDone = kind != Case.LEGACY_LOAD || reloaded.size() == 2
                    && reloaded.values().stream().allMatch(trace -> trace.removal != null);
            if (originalsDone && copiesDone) {
                finish();
                return;
            }
            helper.assertTrue(level.getGameTime() - firedAt <= 60,
                    "Registered lash spawn/natural completion exceeded the bounded observation window");
            int minimumAge = traces.size() != 2 ? 0
                    : traces.values().stream().mapToInt(trace -> trace.live.getLast().age()).min().orElse(0);
            if (kind == Case.LEGACY_LOAD && !reloadDone && minimumAge >= 3) {
                reloadWithoutFrame();
            }
            if (moveOwner && traces.size() == 2) {
                if (!translated && minimumAge >= 2) {
                    Vec3 before = player.position();
                    player.move(MoverType.SELF, new Vec3(1.0D, 0.0D, 0.0D));
                    helper.assertTrue(player.position().distanceTo(before.add(1.0D, 0.0D, 0.0D)) < EPS,
                            "Owner-only translation was blocked");
                    translated = true;
                    LogUtils.getLogger().info("R650_HP_LASH_OWNER case={} event=translated world={} state={}",
                            name, level.getGameTime(), JSON.toJson(Actor.of(player)));
                }
                else if (translated && !turned && minimumAge >= 3) {
                    facePlayer(90.0F);
                    turned = true;
                    LogUtils.getLogger().info("R650_HP_LASH_OWNER case={} event=turned world={} state={}",
                            name, level.getGameTime(), JSON.toJson(Actor.of(player)));
                }
                else if (turned && !pitched && minimumAge >= 4) {
                    player.setXRot(-90.0F);
                    pitched = true;
                    LogUtils.getLogger().info("R650_HP_LASH_OWNER case={} event=pitched world={} state={}",
                            name, level.getGameTime(), JSON.toJson(Actor.of(player)));
                }
            }
            later(1, this::monitor);
        }

        // A weaker hit as the lashes spawn leaves the target inside its hurt cooldown when they arrive.
        void weakerHitFirst() {
            if (weakHitAt < 0L) {
                if (traces.size() != 2) return;
                fixtureHit = true;
                boolean hurt = target.hurt(level.damageSources().generic(), WEAK_HIT);
                fixtureHit = false;
                helper.assertTrue(hurt && target.invulnerableTime == 20 && target.lastHurt == WEAK_HIT,
                        "Fixture: the weaker hit did not start the target's hurt cooldown");
                weakHitAt = level.getGameTime();
            }
            cooldown.add(target.invulnerableTime);
        }

        // The lash type is registered noSave, so Entity.load is the only route a frameless tag can take.
        void reloadWithoutFrame() {
            reloadDone = true;
            for (Trace trace : List.copyOf(traces.values())) {
                HermitVineProjectileEntity vine = trace.vine;
                helper.assertTrue(vine.isAlive(), "Lash ended before the frameless reload");
                helper.assertTrue(!vine.save(new CompoundTag()) && !vine.saveAsPassenger(new CompoundTag()),
                        "A live lash is serialisable into a chunk save");
                CompoundTag tag = vine.saveWithoutId(new CompoundTag());
                helper.assertTrue(tag.getBoolean("OrdinaryFrameInitialized"),
                        "A fired lash did not save an initialised frame");
                for (String key : List.copyOf(tag.getAllKeys())) {
                    if (key.startsWith("Ordinary")) tag.remove(key);
                }
                Vec3 motion = trace.live.getFirst().velocity();
                ListTag legacyMotion = new ListTag();
                legacyMotion.add(DoubleTag.valueOf(motion.x));
                legacyMotion.add(DoubleTag.valueOf(motion.y));
                legacyMotion.add(DoubleTag.valueOf(motion.z));
                tag.put("Motion", legacyMotion);
                tag.putUUID("UUID", UUID.randomUUID());
                Vec3 savedTip = vine.position();
                int savedAge = vine.tickCount;
                vine.discard();
                HermitVineProjectileEntity copy = HermitPurpleAddon.HP_VINE_ENTITY_TYPE.get().create(level);
                helper.assertTrue(copy != null, "Lash type refused to create the reloaded entity");
                copy.load(tag);
                helper.assertTrue(copy.tickCount == savedAge && copy.position().distanceTo(savedTip) < EPS
                                && copy.getOwner() == stand,
                        "Frameless reload lost its age, position or controller");
                Trace reloadedTrace = new Trace(copy);
                reloadedTrace.legacyMotion = motion;
                reloaded.put(copy.getUUID(), reloadedTrace);
                helper.assertTrue(level.addFreshEntity(copy), "Reloaded lash was not admitted to the level");
                Sample sample = snapshot(copy);
                reloadedTrace.live.add(sample);
                log("reloaded", sample);
            }
        }

        void finish() {
            List<String> fixtureErrors = new ArrayList<>();
            List<String> donorErrors = new ArrayList<>();
            for (Trace trace : traces.values()) {
                Sample spawn = trace.live.getFirst();
                if (spawn.lifespan() <= 1 || spawn.speedFactor() <= 0.0D
                        || spawn.velocity().lengthSqr() < EPS * EPS) fixtureErrors.add("Invalid spawn motion parameters");
                if (kind != Case.LEGACY_LOAD && trace.removal.age() != trace.live.getLast().age() + 1
                        || !trace.removal.removed()) fixtureErrors.add("Incomplete natural removal boundary");
                boolean attaches = kind == Case.BIND_ATTACH;
                for (Sample sample : trace.live) {
                    if (sample.binding() != kind.binding || !sample.alive()
                            || !attaches && (sample.attached() || sample.lifespan() != spawn.lifespan())
                            || sample.speedFactor() != spawn.speedFactor()
                            || !level.isPositionEntityTicking(BlockPos.containing(sample.tip()))) {
                        fixtureErrors.add("Contact/parameter/chunk confound at " + sample.uuid() + "/" + sample.age());
                    }
                }
                spawnBase(trace, donorErrors);
                switch (kind) {
                    case ENTITY_HIT, KNOCKBACK, COOLDOWN_EXCESS -> {
                        if (trace.removal.age() < spawn.lifespan()) retractionAfterHit(trace, donorErrors);
                        else donorTrajectory(trace, donorErrors);
                    }
                    case BIND_ATTACH -> {
                        if (trace.live.stream().anyMatch(Sample::attached)) attachedBind(trace, donorErrors);
                        else {
                            if (trace.removal.age() < spawn.lifespan()) {
                                donorErrors.add(spawn.uuid() + ": the vine that did not attach was removed at age "
                                        + trace.removal.age());
                            }
                            donorTrajectory(trace, donorErrors);
                        }
                    }
                    case LEGACY_LOAD -> { }
                    case BLOCK_PASS -> {
                        if (trace.removal.age() < spawn.lifespan()) {
                            donorErrors.add(spawn.uuid() + ": removed at age " + trace.removal.age()
                                    + " with a block in its path");
                        }
                        donorTrajectory(trace, donorErrors);
                    }
                    default -> {
                        if (trace.removal.age() < spawn.lifespan() && !decoyStruck()) {
                            fixtureErrors.add("Early removal/contact before ordinary lifespan");
                        }
                        donorTrajectory(trace, donorErrors);
                    }
                }
            }
            spread(donorErrors);
            if (target != null && (!target.isAlive() || target.getHealth() >= targetHealth)) {
                fixtureErrors.add("No lash damaged the target in its path");
            }
            if (target != null) oneHitPerClick(fixtureErrors, donorErrors);
            if (kind == Case.ENTITY_HIT || kind == Case.KNOCKBACK || kind == Case.COOLDOWN_EXCESS) {
                long early = traces.values().stream().filter(trace ->
                        trace.removal.age() < trace.live.getFirst().lifespan()).count();
                if (early != 1) {
                    donorErrors.add(early + " lashes retracted early after one click on one target;"
                            + " the donor's damaging vine retracts and the other finishes its swing");
                }
            }
            if (kind == Case.BIND_ATTACH) {
                long attached = traces.values().stream().filter(trace ->
                        trace.live.stream().anyMatch(Sample::attached)).count();
                if (attached != 1) {
                    donorErrors.add(attached + " binding vines attached to the target, the donor attaches one");
                }
            }
            if (decoyStruck()) {
                donorErrors.add("the hit ray left the controller: it struck a mob beside the user, health "
                        + decoy.getHealth() + "/" + decoy.getMaxHealth());
            }
            if (kind == Case.KNOCKBACK || kind == Case.COOLDOWN_EXCESS) knockbackAlongFacing(donorErrors);
            if (kind == Case.COOLDOWN_EXCESS) excessOverWeakerHit(fixtureErrors, donorErrors);
            if (kind.readsSounds()) throwSoundTiming(donorErrors);
            if (kind == Case.BIND_BLOCKED) blockedBinding(fixtureErrors, donorErrors);
            if (kind == Case.BLOCK_PASS) {
                LogUtils.getLogger().info("R650_HP_LASH_LAMP case={} litTicks={}", name, lampLitTicks);
                if (lampLitTicks == 0) fixtureErrors.add("No lash crossed the lamp obstacle");
            }
            if (kind == Case.LEGACY_LOAD) {
                for (Trace trace : reloaded.values()) legacyFlight(trace, donorErrors);
            }
            if (moveOwner && (!translated || !turned || !pitched)) fixtureErrors.add("Owner motion controls did not execute");
            LogUtils.getLogger().info("R650_HP_LASH_COMPLETE case={} vines={} fixtureErrors={} donorErrors={}",
                    name, traces.size(), JSON.toJson(fixtureErrors), JSON.toJson(donorErrors));
            helper.assertTrue(fixtureErrors.isEmpty(), "Invalid Hermit trajectory fixture; count="
                    + fixtureErrors.size() + "; first=" + (fixtureErrors.isEmpty() ? "none" : fixtureErrors.getFirst()));
            helper.assertTrue(donorErrors.isEmpty(), "Lash differs from donor owner-bound trajectory; count="
                    + donorErrors.size() + "; first=" + (donorErrors.isEmpty() ? "none" : donorErrors.getFirst()));
            LogUtils.getLogger().info("R650_HP_LASH_PASS {}", name);
            close();
            helper.succeed();
        }

        void donorTrajectory(Trace trace, List<String> errors) {
            Sample spawn = trace.live.getFirst();
            // Donor distance changes precede flag changes; only the fired direction is calibrated at spawn.
            float speed = 8.0F / spawn.lifespan();
            double maximum = speed * speed * spawn.lifespan() / (speed + speed);
            int turnAge = (int) (maximum / speed);
            float distance = 0.0F;
            boolean retracting = false;
            Vec3 initialDirection = spawn.velocity().normalize();
            double initialYaw = Math.toDegrees(Math.atan2(-initialDirection.x, initialDirection.z));
            double initialPitch = Math.toDegrees(Math.atan2(-initialDirection.y, initialDirection.horizontalDistance()));
            double previousRadial = 0.0D;
            boolean observedRetraction = false;
            for (Sample sample : trace.live) {
                if (sample.age() == 0) continue;
                distance = (float) (distance + (retracting ? -speed : speed) * spawn.speedFactor());
                if (sample.age() >= turnAge) retracting = true;
                Vec3 direction = Vec3.directionFromRotation(
                        (float) (initialPitch + sample.player().pitch() - spawn.player().pitch()),
                        (float) (initialYaw + sample.player().yaw() - spawn.player().yaw()));
                Vec3 base = donorBase(sample.player());
                Vec3 expected = base.add(direction.scale(Math.max(0.0F, distance)));
                double error = sample.tip().distanceTo(expected);
                double radial = sample.tip().subtract(base).dot(direction);
                if (sample.age() > turnAge && radial < previousRadial - EPS) observedRetraction = true;
                if (distance <= 0.0F || error > 0.002D) {
                    errors.add(sample.uuid() + "/age=" + sample.age() + "/tipError=" + error
                            + "/donorDistance=" + distance + "/observedRadial=" + radial);
                }
                previousRadial = radial;
            }
            if (!moveOwner && !observedRetraction) errors.add(trace.vine.getUUID() + ": no second-half retraction");
        }

        void spawnBase(Trace trace, List<String> errors) {
            Sample spawn = trace.live.getFirst();
            Vec3 expected = donorBase(spawn.player());
            if (spawn.tip().distanceTo(expected) > 1.0E-3D) {
                errors.add(spawn.uuid() + ": spawn=" + spawn.tip() + " donorBase=" + expected
                        + " controller=" + spawn.controller().position());
            }
        }

        // Donor HPVineAttack.standPerform: one vine along the user's look and one turned by
        // MathUtil.xRotYRotOffsets(angle, 10), whose two angles always satisfy sin^2 + sin^2 = 1/101.
        // The ordinary lash draws its angle below 1.5 pi, which never gives a left turn with a downward one.
        void spread(List<String> errors) {
            int straight = 0;
            int turned = 0;
            for (Trace trace : traces.values()) {
                Sample spawn = trace.live.getFirst();
                Vec3 direction = spawn.velocity().normalize();
                double yaw = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-direction.x, direction.z))
                        - spawn.player().yaw());
                double pitch = Math.toDegrees(Math.atan2(-direction.y, direction.horizontalDistance()))
                        - spawn.player().pitch();
                double measure = Math.pow(Math.sin(Math.toRadians(yaw)), 2.0D)
                        + Math.pow(Math.sin(Math.toRadians(pitch)), 2.0D);
                if (measure < 1.0E-7D) {
                    straight++;
                }
                else if (Math.abs(measure - 1.0D / 101.0D) < 1.0E-4D
                        && (kind.binding || !(yaw < -0.01D && pitch > 0.01D))) {
                    turned++;
                }
                else {
                    errors.add(spawn.uuid() + ": yawOffset=" + yaw + " pitchOffset=" + pitch
                            + " is neither the user's look nor the donor spread");
                }
            }
            if (straight != 1 || turned != 1) {
                errors.add("straight lashes=" + straight + ", spread lashes=" + turned + ", donor fires one of each");
            }
        }

        // Donor DamageUtil.knockback(target, 0.15F, yRot) with knockbackMultiplier() 0: each ordinary hit pushes
        // 0.15 along the user's facing and the damage itself pushes nothing.
        void knockbackAlongFacing(List<String> errors) {
            Vec3 facing = Vec3.directionFromRotation(0.0F, player.getYRot());
            LogUtils.getLogger().info("R764_HP_LASH_KNOCKBACK case={} pushes={} finisher={}->{}",
                    name, pushes, finisherBefore, stand.getFinisherMeter());
            if (pushes.size() != 1) {
                errors.add("one click pushed its target " + pushes.size() + " times, the donor's one damaging vine once");
            }
            for (Vec3 push : pushes) {
                if (push.distanceTo(facing.scale(0.15D)) > 1.0E-3D) {
                    errors.add("target push " + push + " is not 0.15 along the user's facing " + facing);
                }
            }
            float gained = stand.getFinisherMeter() - finisherBefore;
            if (Math.abs(gained - HermitPurpleSpec.VINE_FINISHER_GAIN) > 1.0E-4F) {
                errors.add("one click moved the finisher meter by " + gained + ", one damaging vine gives "
                        + HermitPurpleSpec.VINE_FINISHER_GAIN);
            }
        }

        // Donor OwnerBoundProjectileEntity.hurtTarget with shouldHurtThroughInvulTicks() false: a plain hurt. The two
        // vines of one click carry the same damage, so the target's hurt cooldown refuses the second one for the
        // 10 ticks a vine lives.
        void oneHitPerClick(List<String> fixtureErrors, List<String> errors) {
            for (Trace trace : traces.values()) {
                List<Integer> ages = crossingAges(trace);
                LogUtils.getLogger().info("R764_HP_LASH_RAY case={} vine={} targetInRayAtAges={}",
                        name, trace.vine.getUUID(), ages);
                if (ages.isEmpty()) fixtureErrors.add(trace.vine.getUUID() + ": the target was never in this lash's hit ray");
            }
            float one = oneLashDamage();
            float lost = targetHealth - target.getHealth();
            LogUtils.getLogger().info("R764_HP_LASH_HITS case={} hits={} healthLost={} oneLash={}", name, hits, lost, one);
            if (hits.size() != 1) {
                errors.add("one click hurt its target " + hits.size() + " times " + hits
                        + ", the donor lands one of its two vines");
            }
            if (Math.abs(lost - one) > 1.0E-3F) {
                errors.add("the target lost " + lost + " health to one click, one vine deals " + one);
            }
        }

        // Donor InitStands: hp_vine_attack standSound(Phase.PERFORM, VINE_TRHOW), hp_vine_shift
        // standSound(Phase.WINDUP, VINE_TRHOW). 1.16 played a phase's sounds as the task entered that phase.
        void throwSoundTiming(List<String> errors) {
            ActionPhase phase = kind.binding ? ActionPhase.WINDUP : ActionPhase.PERFORM;
            long time = kind.binding ? firedAt : traces.values().iterator().next().live.getFirst().worldTime();
            LogUtils.getLogger().info("R764_HP_LASH_SOUND case={} firedAt={} throwSounds={}", name, firedAt, throwSounds);
            if (throwSounds.size() != 1) {
                errors.add("hp_throw was sent " + throwSounds.size() + " times " + throwSounds
                        + ", the donor plays it once, as " + phase + " starts");
            }
            for (ThrowSound sound : throwSounds) {
                if (sound.phase() != phase || sound.worldTime() != time) {
                    errors.add("hp_throw was sent in " + sound + ", the donor plays it as " + phase
                            + " starts (world time " + time + ")");
                }
            }
        }

        float oneLashDamage() {
            return (kind.binding ? HermitPurpleSpec.VINE_BIND_DAMAGE : HermitPurpleSpec.VINE_ATTACK_DAMAGE)
                    * JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get().floatValue()
                    * traces.values().iterator().next().vine.getDamageFactor();
        }

        // 1.16 LivingEntity.hurt inside the hurt cooldown: a bigger hit takes its excess over the last one, becomes
        // the last hit and leaves the cooldown running.
        void excessOverWeakerHit(List<String> fixtureErrors, List<String> errors) {
            float one = oneLashDamage();
            LogUtils.getLogger().info("R764_HP_LASH_COOLDOWN case={} weakHitAt={} cooldown={} lastHurt={}",
                    name, weakHitAt, cooldown, target.lastHurt);
            if (hits.size() == 1) {
                int index = (int) (hits.getFirst().worldTime() - weakHitAt);
                if (index < 1 || index >= cooldown.size() || cooldown.get(index) <= 10) {
                    fixtureErrors.add("The lash did not arrive inside the weaker hit's cooldown");
                }
                if (Math.abs(hits.getFirst().amount() - (one - WEAK_HIT)) > 1.0E-3F) {
                    errors.add("inside the cooldown of a " + WEAK_HIT + " hit the vine took " + hits.getFirst().amount()
                            + ", the donor takes the excess " + (one - WEAK_HIT));
                }
            }
            if (Math.abs(target.lastHurt - one) > 1.0E-3F) {
                errors.add("the target's last hit reads " + target.lastHurt + " after the vine, the donor keeps " + one);
            }
            for (int index = 1; index < cooldown.size(); index++) {
                if (cooldown.get(index) != Math.max(cooldown.get(index - 1) - 1, 0)) {
                    errors.add("the vine restarted the target's hurt cooldown: " + cooldown);
                    break;
                }
            }
        }

        // The ticks in which the lash's hit ray (user origin to the tip's next position) crossed the target.
        List<Integer> crossingAges(Trace trace) {
            AABB reach = targetBox.inflate(target.getPickRadius() + trace.vine.getBbWidth() / 2.0D);
            List<Integer> ages = new ArrayList<>();
            for (int index = 0; index + 1 < trace.live.size(); index++) {
                Sample before = trace.live.get(index);
                Sample after = trace.live.get(index + 1);
                Vec3 start = donorBase(after.player());
                Vec3 end = before.tip().add(before.velocity());
                if (reach.contains(start) || reach.clip(start, end).isPresent()) ages.add(after.age());
            }
            return ages;
        }

        // Donor HPVineEntity: the binding vine that hurt an unblocking target attaches to it, stays on it and
        // lives 5 ticks longer.
        void attachedBind(Trace trace, List<String> errors) {
            Sample spawn = trace.live.getFirst();
            Vec3 centre = new Vec3(target.getX(), target.getY(0.5D), target.getZ());
            boolean attached = false;
            for (Sample sample : trace.live) {
                if (attached && !sample.attached()) errors.add(sample.uuid() + "/age=" + sample.age() + ": let go of its target");
                attached |= sample.attached();
                if (sample.attached() && (sample.tip().distanceTo(centre) > EPS || sample.lifespan() != spawn.lifespan() + 5)) {
                    errors.add(sample.uuid() + "/age=" + sample.age() + ": attached tip=" + sample.tip()
                            + " lifespan=" + sample.lifespan() + ", donor keeps it at " + centre + " for "
                            + (spawn.lifespan() + 5) + " ticks");
                }
            }
            if (trace.removal.age() != spawn.lifespan() + 6) {
                errors.add(spawn.uuid() + ": attached vine was removed at age " + trace.removal.age()
                        + ", donor removes it after " + (spawn.lifespan() + 5) + " ticks");
            }
        }

        // Donor HPVineEntity.afterEntityHit: the binding branch has neither the knockback nor the finisher gain.
        void blockedBinding(List<String> fixtureErrors, List<String> errors) {
            if (!JojoModUtil.isTargetBlocking(target)) fixtureErrors.add("Lash target lowered its shield");
            Vec3 motion = target.getDeltaMovement();
            LogUtils.getLogger().info("R764_HP_LASH_KNOCKBACK case={} pushes={} motion={} finisher={}->{}",
                    name, pushes, motion, finisherBefore, stand.getFinisherMeter());
            if (!pushes.isEmpty() || motion.lengthSqr() > 1.0E-12D) {
                errors.add("binding lash knocked the blocking target back: pushes=" + pushes
                        + " motion=" + motion);
            }
            if (stand.getFinisherMeter() != finisherBefore) {
                errors.add("binding lash moved the finisher meter from " + finisherBefore
                        + " to " + stand.getFinisherMeter());
            }
        }

        // Donor HPVineEntity.afterEntityHit: a damaging ordinary lash starts retracting and is pulled back in.
        void retractionAfterHit(Trace trace, List<String> errors) {
            Sample spawn = trace.live.getFirst();
            double step = 8.0D / spawn.lifespan() * spawn.speedFactor();
            Vec3 direction = spawn.velocity().normalize();
            double previous = 0.0D;
            boolean retracting = false;
            for (Sample sample : trace.live) {
                if (sample.age() == 0) continue;
                double radial = sample.tip().subtract(spawn.tip()).dot(direction);
                double change = radial - previous;
                if (change < 0.0D) retracting = true;
                if (Math.abs(Math.abs(change) - step) > 0.002D || retracting && change > 0.0D) {
                    errors.add(sample.uuid() + "/age=" + sample.age() + "/radialChange=" + change
                            + "/donorStep=" + step);
                }
                previous = radial;
            }
            if (previous > step + 0.002D) {
                errors.add(spawn.uuid() + ": removed at age " + trace.removal.age()
                        + " while still extended " + previous + " blocks");
            }
        }

        void legacyFlight(Trace trace, List<String> errors) {
            Sample loaded = trace.live.getFirst();
            Sample previous = loaded;
            for (Sample sample : trace.live) {
                if (sample == loaded) continue;
                double error = sample.tip().distanceTo(previous.tip().add(trace.legacyMotion));
                double fromEye = sample.tip().distanceTo(sample.player().eye());
                if (error > EPS || fromEye < 1.5D) {
                    errors.add(sample.uuid() + "/age=" + sample.age() + "/legacyFlightError=" + error
                            + "/distanceFromEye=" + fromEye);
                }
                previous = sample;
            }
            if (trace.live.size() < 2 || previous.age() < loaded.lifespan()) {
                errors.add(loaded.uuid() + ": frameless lash ended at age " + previous.age()
                        + " before its lifespan " + loaded.lifespan());
            }
        }

        void facePlayer(float yaw) {
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            player.setYBodyRot(yaw);
            player.setXRot(0.0F);
        }

        void log(String event, Sample sample) {
            LogUtils.getLogger().info("R650_HP_LASH_TRACE case={} event={} state={}", name, event, JSON.toJson(sample));
        }

        void observe(Runnable observation) {
            try {
                observation.run();
            }
            catch (RuntimeException failure) {
                if (observationFailure == null) observationFailure = failure;
            }
        }

        void later(int ticks, Runnable task) {
            helper.runAfterDelay(ticks, () -> guarded(task));
        }

        void guarded(Runnable task) {
            if (closed || helper.testInfo.isDone()) return;
            try {
                task.run();
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            watching = false;
            NeoForge.EVENT_BUS.unregister(joinListener);
            NeoForge.EVENT_BUS.unregister(tickListener);
            NeoForge.EVENT_BUS.unregister(leaveListener);
            NeoForge.EVENT_BUS.unregister(knockListener);
            NeoForge.EVENT_BUS.unregister(damageListener);
            try {
                if (power != null && power.hasPower()) power.getPowerType().forceUnsummon(player, power);
            }
            finally {
                for (Trace trace : traces.values()) if (!trace.vine.isRemoved()) trace.vine.discard();
                for (Trace trace : reloaded.values()) if (!trace.vine.isRemoved()) trace.vine.discard();
                if (target != null && !target.isRemoved()) target.discard();
                if (decoy != null && !decoy.isRemoved()) decoy.discard();
                if (lampPos != null) level.setBlockAndUpdate(lampPos, Blocks.AIR.defaultBlockState());
                if (player != null && !player.isRemoved()) {
                    level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                }
                LogUtils.getLogger().info("R650_HP_LASH_CLEANUP case={} listenersReleased=true", name);
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
