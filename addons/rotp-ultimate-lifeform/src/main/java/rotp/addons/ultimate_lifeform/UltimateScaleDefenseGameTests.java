package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAbility;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.pillarman.abilities.PillarmanBladeBarrageAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateScaleDefenseGameTests {
    private UltimateScaleDefenseGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_scale_side_defense", timeoutTicks = 120)
    public static void registeredScaleOutsideFrontConeIsBlockedByHeldBlades(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_scale_front_defense", timeoutTicks = 120)
    public static void registeredFrontScaleIsBlockedThenDamagesAfterBladeRelease(GameTestHelper helper) {
        start(helper, true);
    }

    private static void start(GameTestHelper helper, boolean front) {
        Fixture fixture = new Fixture(helper, front);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short CASTER_KEY = 35, DEFENDER_KEY = 36;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean front;
        private final Set<Entity> owned = new LinkedHashSet<>();
        private final Map<UUID, UltimateScaleProjectileEntity> emitted = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> roof = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player caster, defender;
        private PlayerPower casterPower, defenderPower;
        private UltimateLifeformData casterData;
        private PillarmanData defenderData;
        private Ability casterAbility, defenderAbility;
        private EntityActionInstance casterAction, defenderAction;
        private UltimateScaleProjectileEntity scale;
        private ChunkPos chunk;
        private AABB room;
        private int casterPosts, defenderPosts, grantCasterPost, grantDefenderPost, defenderHeldPosts;
        private int releasedDefenderPost, incomingCount, damagePosts, attempts, impacts;
        private long casterGeneration, defenderGeneration;
        private boolean granted, casterPressed, defenderPressed, casterReleased, defenderReleased, tested, closed;
        private Throwable observerFailure;
        private DamageSource activeSource;
        private LivingIncomingDamageEvent incoming;
        private float applied;

        Fixture(GameTestHelper helper, boolean front) {
            this.helper = helper; this.level = helper.getLevel(); this.front = front;
        }
        private void premise(boolean ok, String message) { helper.assertTrue(ok, "SCALE-DEFENSE-PREMISE " + message); }
        private void oracle(boolean ok, String message) { helper.assertTrue(ok, "SCALE-DEFENSE-ORACLE " + message); }
        private void log(String message) { JojoMod.LOGGER.info("SCALE-DEFENSE {} {}", front ? "FRONT" : "SIDE", message); }

        private Player player(String name, Vec3 pos, float pitch) {
            Player player = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), name)) {
                @Override public boolean isCreative() { return false; }
                @Override public boolean isSpectator() { return false; }
            };
            owned.add(player);
            GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
            player.setNoGravity(true);
            player.moveTo(pos.x, pos.y, pos.z, 0, pitch);
            player.setYHeadRot(0); player.yBodyRot = 0;
            return player;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(template);
            Vec3 center = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 32D, chunk.getMinBlockZ() + 8.5D);
            room = new AABB(center.add(-5, -1, -7), center.add(5, 16, 4));
            premise(room.minY >= level.getMinBuildHeight() && room.maxY < level.getMaxBuildHeight(), "room build bounds");
            premise(level.getEntities((Entity) null, room).isEmpty(), "room contains foreign entities");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(room.maxX, room.maxY, room.maxZ))) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "room is not empty air: " + pos);
            }
            BlockPos top = BlockPos.containing(center).above(4);
            for (BlockPos pos : BlockPos.betweenClosed(top.offset(-1, 0, -1), top.offset(1, 0, 1))) {
                roof.put(pos.immutable(), level.getBlockState(pos));
                premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "owned roof placement failed");
            }
            caster = player("ScaleDefenseCaster", center.add(0, 0, -4), -90F);
            defender = player("ScaleDefenseTarget", center, front ? 90F : 0F);
            premise(level.addFreshEntity(caster) && level.addFreshEntity(defender), "actor join failed");
            casterPower = PowerClass.PLAYER_POWER.attachGet(caster);
            defenderPower = PowerClass.PLAYER_POWER.attachGet(defender);
            premise(!casterPower.hasPower() && !defenderPower.hasPower()
                    && !PowerClass.STAND.attachGet(caster).hasPower() && !PowerClass.STAND.attachGet(defender).hasPower(),
                    "actors are not fresh power-free/no-Stand controls");
            observeEvents();
            log("setup caster=" + caster.getUUID() + " defender=" + defender.getUUID() + " chunk=" + chunk
                    + " casterPos=" + caster.position() + " defenderPos=" + defender.position() + " roofCells=" + roof.size());
        }

        private void observeEvents() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                Entity entity = event.getEntity();
                if (event.getLevel() != level || entity.getType() != UltimateLifeformAddon.SCALE_PROJECTILE.get()
                        || !(entity instanceof Projectile projectile) || projectile.getOwner() != caster) return;
                owned.add(entity);
                premise(entity instanceof UltimateScaleProjectileEntity, "registered emission is not the actual scale class");
                UltimateScaleProjectileEntity born = (UltimateScaleProjectileEntity) entity;
                emitted.put(born.getUUID(), born);
                premise(!event.isCanceled() && emitted.size() == 1 && casterPressed && !casterReleased
                        && casterAction == LivingComponentAction.getCurEntityAction(caster)
                        && casterAction.ability == casterAbility && casterAction.getPhase() == ActionPhase.PERFORM
                        && AbilityInput.isHeldByKey(caster, casterAction) && casterData.getHeldTicks() == 5,
                        "emission lacks first natural registered held-tick5 identity");
                CompoundTag tag = new CompoundTag(); born.saveWithoutId(tag);
                premise(!tag.getBoolean("Hamon") && born.getProjectileDamage() == 5F && born.getLifeTicks() == 360,
                        "ordinary uncharged production emission differs");
                scale = born;
                log("emitted registry=rotp_ul:scale_projectile uuid=" + born.getUUID() + " class=" + born.getClass().getName()
                        + " owner=" + caster.getUUID() + " inputGeneration=" + casterGeneration
                        + " phase=" + casterAction.getPhase() + " heldTicks=" + casterData.getHeldTicks()
                        + " position=" + born.position() + " velocity=" + born.getDeltaMovement()
                        + " coreModded=" + ((Entity) born instanceof ModdedProjectileEntity) + " tag=" + tag);
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == caster || event.getEntity() == defender) ready(event.getEntity().getBoundingBox());
                if (emitted.get(event.getEntity().getUUID()) == event.getEntity()) {
                    premise(!event.isCanceled(), "emitted scale natural tick was canceled");
                    ready(event.getEntity().getBoundingBox().expandTowards(event.getEntity().getDeltaMovement()).inflate(1));
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == caster) casterPosts++;
                if (event.getEntity() == defender) {
                    defenderPosts++;
                    if (defenderPressed && !defenderReleased && defenderAction == LivingComponentAction.getCurEntityAction(defender)
                            && defenderAction.getPhase() == ActionPhase.PERFORM && AbilityInput.isHeldByKey(defender, defenderAction)) defenderHeldPosts++;
                }
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (emitted.get(event.getProjectile().getUUID()) != event.getProjectile()) return;
                impacts++;
                premise(false, "unexpected natural scale impact confounds declared hurt transport: " + event.getRayTraceResult());
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() != caster && event.getEntity() != defender) return;
                premise(event.getEntity() == defender && activeSource != null && event.getSource() == activeSource,
                        "foreign/natural damage outside the exact transport window");
                incoming = event; incomingCount++;
            });
            Consumer<LivingDamageEvent.Post> appliedEvent = event -> observe(() -> {
                if (event.getEntity() != caster && event.getEntity() != defender) return;
                premise(event.getEntity() == defender && activeSource != null && event.getSource() == activeSource, "unpaired applied damage");
                applied = event.getNewDamage(); damagePosts++;
            });
            add(join, EntityJoinLevelEvent.class, true);
            add(pre, EntityTickEvent.Pre.class, true);
            add(post, EntityTickEvent.Post.class, false);
            add(impact, ProjectileImpactEvent.class, true);
            add(damage, LivingIncomingDamageEvent.class, true);
            add(appliedEvent, LivingDamageEvent.Post.class, false);
        }

        private <T extends net.neoforged.bus.api.Event> void add(Consumer<T> listener, Class<T> type, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, canceled, type, listener);
        }
        private void ready(AABB box) {
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ), max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            premise(room.minX <= box.minX && room.maxX >= box.maxX && room.minY <= box.minY && room.maxY >= box.maxY
                    && room.minZ <= box.minZ && room.maxZ >= box.maxZ && new ChunkPos(min).equals(chunk) && new ChunkPos(max).equals(chunk)
                    && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max), "actor/scale query left owned ready chunk: " + box);
        }

        private void grant() {
            premise(casterPower.trySetPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())
                    && defenderPower.trySetPowerType(ModPlayerPowers.PILLAR_MAN.get()), "power grants failed");
            casterData = casterPower.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
            // a new Ultimate Lifeform starts with no energy (1.16); this fixture starts full
            casterData.setCommandEnergy(caster, UltimateLifeformData.MAX_ENERGY);
            defenderData = PlayerPower.getPowerData(defender, ModPlayerPowers.PILLAR_MAN).orElseThrow();
            defenderData.setEvolutionStage(2, defender); defenderData.setMode(PillarmanMode.LIGHT, defender); defenderData.setEnergy(defender, 300F);
            caster.setHealth(caster.getMaxHealth()); defender.setHealth(defender.getMaxHealth());
            casterPower.updateAvailableMoves(); defenderPower.updateAvailableMoves();
            casterAbility = casterPower.getAbility(UltimateLifeformSpec.SCALE_SHOT.id);
            defenderAbility = defenderPower.getAbility("pillarman_blade_barrage");
            premise(casterAbility instanceof UltimateLifeformAbility ulf && ulf.spec() == UltimateLifeformSpec.SCALE_SHOT
                    && defenderAbility instanceof PillarmanBladeBarrageAbility
                    && defenderAbility.abilityType == PillarmanPowerType.PILLAR_MAN_BLADE_BARRAGE.get(), "actual registered abilities absent");
            LivingComponentAction.getComponent(caster).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(defender).entityAim.setTarget(ActionTarget.EMPTY);
            grantCasterPost = casterPosts; grantDefenderPost = defenderPosts; granted = true;
        }

        private EntityActionInstance press(Player player, PlayerPower power, Ability ability, short key, boolean ulf) {
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), player, InputMethod.HOLD),
                    "registered HOLD admission failed: " + ability.getAbilityId());
            if (ulf) casterPressed = true; else defenderPressed = true;
            FriendlyByteBuf extra = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var input = AbilityInput.keyPress(key, ability, player, extra, InputMethod.HOLD, 0, BufferingState.clickOnly(), ability.getAbilityId());
                premise(input != null && input.action instanceof EntityActionInstance, "registered HOLD has no entity action");
                EntityActionInstance action = (EntityActionInstance) input.action;
                premise(action == LivingComponentAction.getCurEntityAction(player) && action.ability == ability
                        && action.getPowerUser() == player && input.generation > 0 && AbilityInput.isHeldByKey(player, action),
                        "actual held instance/generation identity differs");
                if (ulf) casterGeneration = input.generation; else defenderGeneration = input.generation;
                log("input role=" + (ulf ? "caster" : "defender") + " generation=" + input.generation + " phase=" + action.getPhase());
                return action;
            }
            finally { extra.release(); }
        }

        private void releaseCaster() {
            long actual = AbilityInput.keyReleaseAndGetGeneration(CASTER_KEY, caster);
            premise(actual == casterGeneration && casterAction.isOver() && !AbilityInput.isHeldByKey(caster, casterAction), "caster release failed");
            casterReleased = true;
        }
        private void releaseDefender() {
            long actual = AbilityInput.keyReleaseAndGetGeneration(DEFENDER_KEY, defender);
            oracle(actual == defenderGeneration && defenderAction.isOver() && !AbilityInput.isHeldByKey(defender, defenderAction), "blade release failed");
            defenderReleased = true; releasedDefenderPost = defenderPosts;
        }

        private void probe(boolean held) {
            premise(scale != null && emitted.size() == 1 && emitted.get(scale.getUUID()) == scale && !scale.isRemoved()
                    && level.getEntity(scale.getUUID()) == scale && scale.getOwner() == caster && caster.isAlive() && defender.isAlive()
                    && !defender.isInvulnerable() && !defender.isCreative() && !defender.isOnFire()
                    && defender.invulnerableTime == 0 && defender.getAbsorptionAmount() == 0 && defender.getArmorValue() == 0
                    && impacts == 0 && casterData.getHamonCharge() == 0 && !PowerClass.STAND.attachGet(defender).hasPower(),
                    "live emitted identity/ordinary hurt prerequisites changed");
            premise(!level.canSeeSky(BlockPos.containing(defender.getEyePosition()))
                    && roof.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)), "defender shade changed");
            ready(scale.getBoundingBox().expandTowards(scale.getDeltaMovement()).inflate(1));
            List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, room);
            premise(projectiles.size() == 1 && projectiles.get(0) == scale, "neighbor projectile could supply a different defense");
            premise(held ? defenderHeldPosts >= 2 && defenderAction == LivingComponentAction.getCurEntityAction(defender)
                    && defenderAction.getPhase() == ActionPhase.PERFORM && AbilityInput.isHeldByKey(defender, defenderAction)
                    : defenderReleased && defenderAction.isOver() && LivingComponentAction.getCurEntityAction(defender) == null,
                    "defender lacks requested natural hold state");
            CompoundTag tag = new CompoundTag(); scale.saveWithoutId(tag);
            premise(!tag.getBoolean("Hamon") && scale.getProjectileDamage() == 5F && scale.getDeltaMovement().lengthSqr() > 1,
                    "actual ordinary scale damage/motion changed");
            double dot = defender.getLookAngle().dot(scale.getDeltaMovement().reverse().normalize());
            premise(front ? dot > 0.9D : Math.abs(dot) < 0.2D, "actual motion does not discriminate the48.75-degree cone: " + dot);
            DamageSource source = scale.damageSources().thrown(scale, scale.getOwner());
            premise(source.getDirectEntity() == scale && source.getEntity() == caster && source.is(DamageTypes.THROWN),
                    "canonical actual scale attributed source differs");
            activeSource = source; incoming = null; incomingCount = 0; damagePosts = 0; applied = 0;
            try {
                float before = defender.getHealth();
                boolean accepted = defender.hurt(source, scale.getProjectileDamage());
                float after = defender.getHealth(); attempts++;
                throwObserverFailure();
                log("probe=" + attempts + " held=" + held + " scale=" + scale.getUUID() + " cause=" + caster.getUUID()
                        + " age=" + scale.getElapsedLifeTicks() + " pos=" + scale.position() + " velocity=" + scale.getDeltaMovement()
                        + " defenderLook=" + defender.getLookAngle() + " dot=" + dot + " coreModded=" + ((Entity) scale instanceof ModdedProjectileEntity)
                        + " incoming=" + incomingCount + " canceled=" + (incoming == null ? null : incoming.isCanceled())
                        + " accepted=" + accepted + " HP=" + before + "->" + after + " applied=" + applied + " damagePosts=" + damagePosts);
                premise(incomingCount == 1 && incoming != null && incoming.getSource() == source
                        && incoming.getSource().getDirectEntity() == scale && incoming.getSource().getEntity() == caster
                        && incoming.getOriginalAmount() == 5F, "actual emitted-source Incoming proof absent");
                if (held) oracle(incoming.isCanceled() && !accepted && before == after && damagePosts == 0,
                        "donor-evadable emitted scale bypassed held blades at dot=" + dot);
                else oracle(!incoming.isCanceled() && accepted && after < before && damagePosts == 1 && applied > 0
                        && Math.abs(before - after - applied) < 1.0E-4F, "released blade control did not apply real scale damage");
            }
            finally { activeSource = null; }
        }

        private void poll() {
            if (closed) return;
            try {
                throwObserverFailure();
                premise(helper.getTick() < 100, "finite shade/input/emission watchdog");
                if (!granted && casterPosts >= 2 && defenderPosts >= 2
                        && !level.canSeeSky(BlockPos.containing(defender.getEyePosition()))) grant();
                else if (granted && !defenderPressed && casterPosts >= grantCasterPost + 3 && defenderPosts >= grantDefenderPost + 3) {
                    defenderAction = press(defender, defenderPower, defenderAbility, DEFENDER_KEY, false);
                }
                else if (defenderPressed && !casterPressed && defenderHeldPosts >= 2) {
                    premise(casterData.getHamonCharge() == 0 && casterData.getEnergy() > 1000, "fresh uncharged ULF resource admission changed");
                    casterAction = press(caster, casterPower, casterAbility, CASTER_KEY, true);
                }
                else if (scale != null && !tested) {
                    releaseCaster();
                    probe(true); tested = true;
                    if (!front) { finish(); return; }
                    releaseDefender();
                }
                else if (tested && defenderReleased && defenderPosts >= releasedDefenderPost + 2) {
                    probe(false); finish(); return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private void finish() { log("RESULT qualified=true actualEmissions=" + emitted.size() + " probes=" + attempts + " transport=explicitHurt native=false"); close(); helper.succeed(); }
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
            cleanup(() -> { if (casterPressed && caster != null) AbilityInput.keyRelease(CASTER_KEY, caster); }, failures);
            cleanup(() -> { if (defenderPressed && defender != null) AbilityInput.keyRelease(DEFENDER_KEY, defender); }, failures);
            for (Player player : new Player[] {caster, defender}) cleanup(() -> {
                if (player != null && player.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                        .map(input -> !input.heldKeys.isEmpty()).orElse(false)) throw new IllegalStateException("owned held keys remain");
            }, failures);
            cleanup(() -> { if (casterPower != null) casterPower.setPowerType(null); }, failures);
            cleanup(() -> { if (defenderPower != null) defenderPower.setPowerType(null); }, failures);
            for (Entity entity : owned) cleanup(() -> { if (!entity.isRemoved()) entity.discard(); }, failures);
            for (var entry : roof.entrySet()) cleanup(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            cleanup(() -> {
                boolean restored = roof.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())
                        && level.getBlockEntity(entry.getKey()) == null);
                boolean removed = owned.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null);
                log("cleanup cells=" + roof.size() + " exactRestore=" + restored + " ownedRemoved=" + removed + " listeners=0");
                if (!restored || !removed) throw new IllegalStateException("scale defense cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("scale defense cleanup failed");
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
