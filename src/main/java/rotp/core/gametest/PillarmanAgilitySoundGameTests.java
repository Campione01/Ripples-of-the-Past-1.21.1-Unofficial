package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.pillarman.abilities.PillarmanUnnaturalAgilityAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanAgilitySoundGameTests {
    private PillarmanAgilitySoundGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "agility_sound_modded", timeoutTicks = 100)
    public static void moddedAgilityDefenseIsSilentAndReleasedHitStillDamages(GameTestHelper helper) {
        start(helper, Case.MODDED);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "agility_sound_non_evadable", timeoutTicks = 100)
    public static void deniedNonEvadableProjectileDamagesWithoutEvasionSound(GameTestHelper helper) {
        start(helper, Case.NON_EVADE);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "agility_sound_vanilla", timeoutTicks = 100)
    public static void vanillaArrowDefenseStillRequestsOneEvasionSound(GameTestHelper helper) {
        start(helper, Case.VANILLA);
    }

    private enum Case { MODDED, NON_EVADE, VANILLA }
    private enum Source { TOMMY, SANDSTORM, ARROW }

    private static void start(GameTestHelper helper, Case testCase) {
        Fixture fixture = new Fixture(helper, testCase);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 33;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Case testCase;
        private final Map<BlockPos, BlockState> roof = new LinkedHashMap<>();
        private final Set<Entity> owned = new LinkedHashSet<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player user, shooter;
        private PlayerPower power;
        private PillarmanData data;
        private PillarmanUnnaturalAgilityAbility ability;
        private EntityActionInstance action;
        private long generation;
        private int userPosts, shooterPosts, grantPost, heldPosts, releasePost;
        private boolean granted, pressed, released, testedHeld, closed;
        private Throwable observerFailure;
        private DamageSource activeSource;
        private LivingIncomingDamageEvent incoming;
        private int incomingCount, damagePosts, attempts;
        private float applied;
        private final List<PlayLevelSoundEvent> soundRequests = new ArrayList<>();

        Fixture(GameTestHelper helper, Case testCase) {
            this.helper = helper; this.level = helper.getLevel(); this.testCase = testCase;
        }
        private void premise(boolean ok, String message) { helper.assertTrue(ok, "AGILITY-SOUND-PREMISE " + message); }
        private void oracle(boolean ok, String message) { helper.assertTrue(ok, "AGILITY-SOUND-ORACLE " + message); }
        private void log(String message) { JojoMod.LOGGER.info("AGILITY-SOUND {} {}", testCase, message); }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            Vec3 center = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 32.0D, chunk.getMinBlockZ() + 8.5D);
            AABB room = new AABB(center.add(-4, -1, -5), center.add(4, 6, 4));
            premise(room.minY >= level.getMinBuildHeight() && room.maxY < level.getMaxBuildHeight(), "room build bounds");
            premise(level.getEntities((Entity) null, room).isEmpty(), "room contains a foreign actor");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(room.maxX, room.maxY, room.maxZ))) premise(level.isEmptyBlock(pos), "room not air: " + pos);
            BlockPos top = BlockPos.containing(center).above(4);
            for (BlockPos pos : BlockPos.betweenClosed(top.offset(-1, 0, -1), top.offset(1, 0, 1))) {
                roof.put(pos.immutable(), level.getBlockState(pos));
                premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "roof placement failed");
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            shooter = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            owned.add(user); owned.add(shooter);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            GameType.SURVIVAL.updatePlayerAbilities(shooter.getAbilities());
            user.setNoGravity(true); shooter.setNoGravity(true);
            user.moveTo(center.x, center.y, center.z, 0, 0);
            user.setYHeadRot(0); user.yBodyRot = 0;
            shooter.moveTo(center.x, center.y, center.z - 4, 0, 0);
            premise(level.addFreshEntity(user) && level.addFreshEntity(shooter), "owned actor join failed");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            premise(!power.hasPower() && !PowerClass.STAND.attachGet(user).hasPower(), "fresh owner has another power");
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == shooter) shooterPosts++;
                if (event.getEntity() != user) return;
                userPosts++;
                if (pressed && !released && LivingComponentAction.getCurEntityAction(user) == action
                        && action.getPhase() == ActionPhase.PERFORM && AbilityInput.isHeldByKey(user, action)) heldPosts++;
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() != user && event.getEntity() != shooter) return;
                premise(event.getEntity() == user && activeSource != null && event.getSource() == activeSource,
                        "unowned or out-of-probe damage");
                incoming = event; incomingCount++;
            });
            Consumer<LivingDamageEvent.Post> afterDamage = event -> observe(() -> {
                if (event.getEntity() != user && event.getEntity() != shooter) return;
                premise(event.getEntity() == user && activeSource != null && event.getSource() == activeSource,
                        "unpaired applied damage");
                applied = event.getNewDamage(); damagePosts++;
            });
            Consumer<PlayLevelSoundEvent> sound = event -> observe(() -> {
                if (activeSource == null || event.getLevel() != level) return;
                Entity direct = activeSource.getDirectEntity();
                boolean ownedEntity = event instanceof PlayLevelSoundEvent.AtEntity at && at.getEntity() == direct;
                boolean ownedPosition = event instanceof PlayLevelSoundEvent.AtPosition at
                        && direct != null && at.getPosition().distanceTo(direct.position()) < 1.0E-5D
                        && event.getSound() != null && event.getSound().value() == ModSoundEvents.PILLAR_MAN_EVASION.get();
                if (!ownedEntity && !ownedPosition) return;
                soundRequests.add(event);
                log("sound-request target=" + user.getUUID() + " direct=" + direct.getUUID()
                        + " subclass=" + event.getClass().getSimpleName() + " at=" + direct.position()
                        + " sound=" + event.getSound() + " category=" + event.getSource()
                        + " volume=" + event.getOriginalVolume() + " pitch=" + event.getOriginalPitch()
                        + " canceledAtObservation=" + event.isCanceled());
                premise(event.getSound() != null && event.getSound().value() == ModSoundEvents.PILLAR_MAN_EVASION.get(),
                        "owned direct-entity sound was not evasion at HIGHEST observation");
            });
            Consumer<PlayLevelSoundEvent.AtEntity> entitySound = sound::accept;
            Consumer<PlayLevelSoundEvent.AtPosition> positionSound = sound::accept;
            listeners.add(entitySound); listeners.add(positionSound);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, PlayLevelSoundEvent.AtEntity.class, entitySound);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, PlayLevelSoundEvent.AtPosition.class, positionSound);
            listeners.add(post); NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            listeners.add(damage); NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            listeners.add(afterDamage); NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, afterDamage);
            log("setup user=" + user.getUUID() + " shooter=" + shooter.getUUID() + " chunk=" + chunk + " shadeCells=" + roof.size());
        }

        private void grant() {
            premise(power.trySetPowerType(ModPlayerPowers.PILLAR_MAN.get()), "Pillar grant failed");
            data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
            data.setEvolutionStage(2, user); data.setMode(PillarmanMode.NONE, user); data.setEnergy(user, 300F);
            user.setHealth(user.getMaxHealth());
            var found = power.getAbility("pillarman_unnatural_agility");
            premise(found instanceof PillarmanUnnaturalAgilityAbility
                    && found.abilityType == PillarmanPowerType.PILLAR_MAN_UNNATURAL_AGILITY.get(), "registered agility absent");
            ability = (PillarmanUnnaturalAgilityAbility) found;
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            grantPost = userPosts; granted = true;
        }

        private void press() {
            requireActors();
            premise(!data.isStoneFormEnabled() && data.getEvolutionStage() == 2 && data.getMode() == PillarmanMode.NONE
                    && user.getMainHandItem().isEmpty() && data.getEnergy() > 10, "NONE input profile changed");
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD),
                    "registered HOLD admission failed");
            pressed = true;
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD, 0, BufferingState.clickOnly(), ability.getAbilityId());
            premise(input != null && input.action instanceof PillarmanUnnaturalAgilityAbility.UnnaturalAgilityInstance,
                    "registered HOLD did not install agility instance");
            action = (EntityActionInstance) input.action; generation = input.generation;
            premise(action == LivingComponentAction.getCurEntityAction(user) && action.ability == ability && generation > 0,
                    "registered action/generation identity differs");
            log("input generation=" + generation + " phase=" + action.getPhase() + " HP=" + user.getHealth() + " energy=" + data.getEnergy());
        }

        private void requireActors() {
            premise(user.isAlive() && shooter.isAlive() && !user.isRemoved() && !shooter.isRemoved()
                    && !user.isCreative() && !user.isInvulnerable() && !user.isOnFire()
                    && level.isPositionEntityTicking(user.blockPosition()) && level.isPositionEntityTicking(shooter.blockPosition())
                    && !level.canSeeSky(BlockPos.containing(user.getEyePosition()))
                    && roof.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE))
                    && user.getLookAngle().distanceTo(new Vec3(0, 0, 1)) < 1.0E-5D,
                    "actor/shade/facing readiness changed");
        }

        private Projectile projectile(Source kind) {
            Entity entity = switch (kind) {
                case TOMMY -> ModEntityTypes.TOMMY_GUN_BULLET.get().create(level);
                case SANDSTORM -> ModEntityTypes.PILLAR_MAN_DIVINE_SANDSTORM.get().create(level);
                case ARROW -> EntityType.ARROW.create(level);
            };
            if (entity != null) owned.add(entity);
            premise(entity instanceof Projectile, "registered source factory failed");
            return (Projectile) entity;
        }

        private void probe(Source kind, Vec3 sourceOffset, boolean held, boolean blocked, int expectedSounds) {
            requireActors();
            premise(user.invulnerableTime == 0 && user.getAbsorptionAmount() == 0 && user.getArmorValue() == 0,
                    "probe would be hidden by iFrames/absorption/armor");
            premise(level.getEntitiesOfClass(Projectile.class, user.getBoundingBox().inflate(8)).isEmpty(), "neighbor projectile could mask guard");
            premise(held ? action == LivingComponentAction.getCurEntityAction(user) && action.getPhase() == ActionPhase.PERFORM
                    && AbilityInput.isHeldByKey(user, action) && heldPosts >= 2
                    : released && action.isOver() && LivingComponentAction.getCurEntityAction(user) == null,
                    "probe lacks actual requested hold state");
            Projectile projectile = projectile(kind);
            projectile.setOwner(shooter);
            Vec3 position = user.getEyePosition().add(sourceOffset);
            projectile.setPos(position.x, position.y, position.z);
            projectile.setDeltaMovement(sourceOffset.reverse().normalize());
            Throwable primary = null;
            try {
                premise(level.addFreshEntity(projectile) && projectile.getOwner() == shooter, "registered source join/owner differs");
                List<Projectile> candidates = level.getEntitiesOfClass(Projectile.class, user.getBoundingBox().inflate(8));
                premise(candidates.size() == 1 && candidates.get(0) == projectile && !projectile.onGround(), "source isolation/air context differs");
                if (projectile instanceof ModdedProjectileEntity modded) {
                    premise(!modded.standDamage()
                            && modded.canBeEvaded(user) == (kind != Source.SANDSTORM), "real registered flags differ");
                }
                else premise(kind == Source.ARROW, "unexpected vanilla category");
                double facing = user.getLookAngle().dot(projectile.getDeltaMovement().reverse().normalize());
                premise(Math.abs(facing - sourceOffset.normalize().z) < 1.0E-5D, "declared side/front/rear direction differs");
                activeSource = kind == Source.ARROW ? level.damageSources().arrow((AbstractArrow) projectile, shooter)
                        : new DamageSource(DamageUtil.type(level, ModDamageTypes.MOD_PROJECTILE), projectile, shooter);
                incoming = null; incomingCount = 0; damagePosts = 0; applied = 0; soundRequests.clear();
                premise(!activeSource.is(DamageTypeTags.IS_EXPLOSION), "source is unexpectedly explosive");
                float beforeHealth = user.getHealth();
                boolean accepted = user.hurt(activeSource, 2F);
                float afterHealth = user.getHealth();
                attempts++;
                throwObserverFailure();
                log("probe=" + attempts + " kind=" + kind + " held=" + held + " dot=" + facing
                        + " direct=" + projectile.getUUID() + " cause=" + shooter.getUUID() + " incoming=" + incomingCount
                        + " canceled=" + (incoming == null ? null : incoming.isCanceled()) + " accepted=" + accepted
                        + " HP=" + beforeHealth + "->" + afterHealth + " applied=" + applied + " damagePosts=" + damagePosts + " soundRequests=" + soundRequests.size());
                premise(incomingCount == 1 && incoming != null && incoming.getOriginalAmount() == 2F
                        && incoming.getSource().getDirectEntity() == projectile && incoming.getSource().getEntity() == shooter,
                        "actual Incoming/source proof absent");
                if (blocked) oracle(incoming.isCanceled() && !accepted && beforeHealth == afterHealth && damagePosts == 0,
                        "held agility failed to cancel " + kind + " dot=" + facing);
                else oracle(!incoming.isCanceled() && accepted && afterHealth < beforeHealth && damagePosts == 1 && applied > 0
                        && Math.abs((beforeHealth - afterHealth) - applied) < 1.0E-4F,
                        "nonblocked control did not apply real damage: " + kind + " held=" + held + " dot=" + facing);
                for (PlayLevelSoundEvent sound : soundRequests) {
                    premise(sound instanceof PlayLevelSoundEvent.AtEntity at && at.getEntity() == projectile
                                    && sound.getLevel() == level && !sound.isCanceled()
                                    && sound.getSound() != null && sound.getSound().value() == ModSoundEvents.PILLAR_MAN_EVASION.get()
                                    && sound.getSource() == projectile.getSoundSource()
                                    && sound.getOriginalVolume() == 1F && sound.getOriginalPitch() == 1F
                                    && sound.getNewVolume() == 1F && sound.getNewPitch() == 1F,
                            "recorded request is not a qualified AtEntity evasion/source/category/volume/pitch witness");
                }
                oracle(soundRequests.size() == expectedSounds,
                        "evasion server request count differs for " + kind + " held=" + held
                                + " expected=" + expectedSounds + " actual=" + soundRequests.size());
            }
            catch (RuntimeException | Error error) { primary = error; throw error; }
            finally {
                activeSource = null;
                try { if (!projectile.isRemoved()) projectile.discard(); }
                catch (RuntimeException | Error cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
            }
        }

        private void release() {
            long actual = AbilityInput.keyReleaseAndGetGeneration(KEY, user);
            oracle(actual == generation && action.isOver() && !AbilityInput.isHeldByKey(user, action), "real hold release failed");
            released = true; releasePost = userPosts;
        }

        private void poll() {
            if (closed) return;
            try {
                throwObserverFailure();
                premise(helper.getTick() < 80, "finite readiness/hold watchdog");
                if (!granted && userPosts >= 2 && shooterPosts >= 2
                        && !level.canSeeSky(BlockPos.containing(user.getEyePosition()))) grant();
                else if (granted && !pressed && userPosts >= grantPost + 3) press();
                else if (pressed && !testedHeld && heldPosts >= 2) {
                    testedHeld = true;
                    switch (testCase) {
                        case MODDED -> {
                            probe(Source.TOMMY, new Vec3(2, 0, 0), true, true, 0);
                            release();
                        }
                        case NON_EVADE -> { probe(Source.SANDSTORM, new Vec3(0, 0, 2), true, false, 0); finish(); return; }
                        case VANILLA -> {
                            probe(Source.ARROW, new Vec3(0, 0, 2), true, true, 1);
                            finish(); return;
                        }
                    }
                }
                else if (released && userPosts >= releasePost + 2) {
                    probe(Source.TOMMY, new Vec3(2, 0, 0), false, false, 0); finish(); return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private void finish() { log("RESULT qualified=true attempts=" + attempts + " heldPosts=" + heldPosts + " native=false"); close(); helper.succeed(); }
        private void throwObserverFailure() {
            if (observerFailure instanceof RuntimeException error) throw error;
            if (observerFailure instanceof Error error) throw error;
        }
        private void observe(Runnable operation) {
            if (closed || observerFailure != null) return;
            try { operation.run(); } catch (RuntimeException | Error error) { observerFailure = error; }
        }
        private void cleanup(Runnable operation, List<Throwable> failures) {
            try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
            cleanup(() -> { if (pressed && user != null) AbilityInput.keyRelease(KEY, user); }, failures);
            cleanup(() -> { if (user != null && user.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                    .map(input -> !input.heldKeys.isEmpty()).orElse(false)) throw new IllegalStateException("held keys remain"); }, failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            for (Entity entity : owned) cleanup(() -> { if (!entity.isRemoved()) entity.discard(); }, failures);
            for (var entry : roof.entrySet()) cleanup(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            cleanup(() -> {
                boolean restored = roof.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())
                        && level.getBlockEntity(entry.getKey()) == null);
                boolean removed = owned.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null);
                log("cleanup cells=" + roof.size() + " exactRestore=" + restored + " ownedRemoved=" + removed + " listeners=0");
                if (!restored || !removed) throw new IllegalStateException("owned fixture cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("agility sound cleanup failed");
                failures.forEach(error::addSuppressed); throw error; }
        }
        private void closeAfterFailure(Throwable error) { try { close(); } catch (RuntimeException | Error cleanup) { error.addSuppressed(cleanup); } }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { if (test.getError() != null) closeAfterFailure(test.getError()); else close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }
}
