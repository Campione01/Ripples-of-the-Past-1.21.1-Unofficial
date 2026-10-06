package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.pillarman.PillarmanRibEntity;
import rotp.core.impl.powers.pillarman.abilities.PillarmanRibsBladesAbility;
import rotp.core.impl.powers.zombie.ZombieData;
import rotp.core.impl.powers.zombie.ZombiePowerType;
import rotp.core.impl.powers.zombie.abilities.ZombieDisguiseAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZombieDisguiseHeldConditionGameTests {
    private ZombieDisguiseHeldConditionGameTests() {}

    @GameTest(template = "empty", batch = "zombie_disguise_real_rib_stun", timeoutTicks = 160)
    public static void registeredRibStunStopsRetainedDisguiseHold(GameTestHelper helper) { start(helper, Lane.STUN); }

    @GameTest(template = "empty", batch = "zombie_disguise_real_weapon_death", timeoutTicks = 160)
    public static void actualWeaponDeathStopsOriginalRetainedDisguiseHold(GameTestHelper helper) { start(helper, Lane.DEATH); }

    @GameTest(template = "empty", batch = "zombie_disguise_nonstun_hit", timeoutTicks = 160)
    public static void survivingNonstunWeaponHitKeepsSameDisguiseHold(GameTestHelper helper) { start(helper, Lane.DAMAGE_ONLY); }

    private enum Lane { STUN, DEATH, DAMAGE_ONLY }
    private record Frame(int age, long time, boolean alive, boolean removed, float health, float maxHealth,
            boolean stunned, int stunDuration, boolean currentOwned, boolean over, ActionPhase phase,
            float counter, boolean retainedKey, long generation, boolean disguise) {}
    private static final class Hit {
        final LivingIncomingDamageEvent incoming;
        final float healthBefore;
        Float applied, healthAfter;
        Hit(LivingIncomingDamageEvent incoming, float health) { this.incoming = incoming; this.healthBefore = health; }
        boolean accepted() { return !incoming.isCanceled() && applied != null && applied > 0 && healthAfter < healthBefore; }
    }
    private static final class Rib {
        final PillarmanRibEntity entity;
        final List<ProjectileImpactEvent> impacts = new ArrayList<>();
        Rib(PillarmanRibEntity entity) { this.entity = entity; }
    }
    private static void start(GameTestHelper helper, Lane lane) {
        Fixture fixture = new Fixture(helper, lane); helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short HOLD_KEY = 64, RIB_KEY = 65;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Lane lane;
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final Set<Entity> owned = new LinkedHashSet<>();
        private final Map<UUID, Rib> ribs = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Hit> hits = new ArrayList<>();
        private final List<LivingDeathEvent> deaths = new ArrayList<>();
        private Player user, attacker;
        private PlayerPower power, attackerPower;
        private ZombieData data;
        private PillarmanData pillar;
        private ZombieDisguiseAbility ability;
        private Ability ribAbility;
        private EntityActionInstance action, ribAction, volleyAction;
        private HeldInputEntry input, ribInput;
        private Frame before, producedBefore;
        private DamageSource deathSource;
        private AABB room;
        private ChunkPos chunk;
        private double floorY;
        private int userPosts, attackerPosts, grantPosts, holdRows, preAge, lastAge, swings, conditionRows, settleRows;
        private long preTime;
        private boolean granted, pressed, produced, launchRequested, componentOpen, swingOpen, checked, done, closed;
        private Throwable observerFailure;

        Fixture(GameTestHelper helper, Lane lane) { this.helper = helper; this.level = helper.getLevel(); this.lane = lane; }
        private void premise(boolean ok, String text) { helper.assertTrue(ok, "ZHOLD-PREMISE " + text); }
        private void oracle(boolean ok, String text) { helper.assertTrue(ok, "ZHOLD-ORACLE " + text); }
        private void log(String text) { JojoMod.LOGGER.info("ZHOLD {} {}", lane, text); }

        private void setUp() {
            premise(level.getDifficulty() == Difficulty.NORMAL, "ordinary Normal difficulty");
            BlockPos origin = helper.absolutePos(BlockPos.ZERO); chunk = new ChunkPos(origin);
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), y = origin.getY() + 32;
            floorY = y;
            room = new AABB(x + 3, y - 1, z + 2, x + 14, y + 4, z + 13);
            premise(room.minY >= level.getMinBuildHeight() && room.maxY < level.getMaxBuildHeight()
                    && level.getEntities((Entity) null, room).isEmpty(), "empty bounded room");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(Math.nextDown(room.maxX), Math.nextDown(room.maxY), Math.nextDown(room.maxZ)))) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "room not empty air");
            }
            for (int ix = 4; ix <= 12; ix++) for (int iz = 3; iz <= 11; iz++) {
                put(new BlockPos(x + ix, y - 1, z + iz)); put(new BlockPos(x + ix, y + 3, z + iz));
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL); owned.add(user);
            attacker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL); owned.add(attacker);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities()); GameType.SURVIVAL.updatePlayerAbilities(attacker.getAbilities());
            user.moveTo(x + 8.5, y + 0.2, z + 7.35, 180, 0);
            attacker.moveTo(x + 8.5, y + 0.2, z + (lane == Lane.STUN ? 4.10 : 6.05), 0, 0);
            user.setYHeadRot(180); user.yBodyRot = 180; attacker.setYHeadRot(0); attacker.yBodyRot = 0;
            premise(user.getHealth() == 20F && user.getAttributeBaseValue(Attributes.MAX_HEALTH) == 20D
                    && attacker.getHealth() == 20F && level.addFreshEntity(user) && level.addFreshEntity(attacker), "fresh damageable20HP actors");
            if (lane != Lane.STUN) attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_AXE));
            power = PowerClass.PLAYER_POWER.attachGet(user); attackerPower = PowerClass.PLAYER_POWER.attachGet(attacker);
            premise(!power.hasPower() && !attackerPower.hasPower() && !PowerClass.STAND.attachGet(user).hasPower()
                    && !PowerClass.STAND.attachGet(attacker).hasPower(), "fresh actors have powers");
            lastAge = user.tickCount; observeEvents();
            log("setup user=" + user.getUUID() + " attacker=" + attacker.getUUID() + " positions=" + user.position() + "/" + attacker.position()
                    + " ordinaryGravity=true cells=" + blocks.size());
        }
        private void put(BlockPos pos) { blocks.put(pos.immutable(), level.getBlockState(pos)); premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "owned support/roof"); }
        private void ready(Entity actor) {
            AABB box = actor.getBoundingBox(); BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ), max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            premise(box.minX >= room.minX && box.maxX <= room.maxX && box.minY >= room.minY && box.maxY <= room.maxY
                    && box.minZ >= room.minZ && box.maxZ <= room.maxZ && new ChunkPos(min).equals(chunk) && new ChunkPos(max).equals(chunk)
                    && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max), "owned actor left ready room");
        }
        private void actorState() {
            ready(user); ready(attacker);
            premise(user.experienceLevel == 0 && user.totalExperience == 0 && user.experienceProgress == 0F,
                    "scoped fresh zero-XP victim");
            premise(!user.isRemoved() && !attacker.isRemoved() && attacker.isAlive()
                    && !user.isCreative() && !user.isSpectator() && !user.isInvulnerable() && !user.isNoGravity()
                    && !attacker.isCreative() && !attacker.isSpectator() && !attacker.isNoGravity() && !user.isOnFire() && !attacker.isOnFire()
                    && user.getMainHandItem().isEmpty() && user.getOffhandItem().isEmpty() && !user.isBlocking()
                    && !PowerClass.STAND.attachGet(user).hasPower() && !PowerClass.STAND.attachGet(attacker).hasPower(), "ordinary actor state");
            premise(lane == Lane.DEATH && produced || user.isAlive(), "unexpected death outside owned lethal route");
            if (granted) premise(PlayerPower.getPowerData(user, ModPlayerPowers.ZOMBIE).orElse(null) == data
                    && data.getEnergy() >= 0 && data.getEnergy() <= data.getMaxEnergy(user)
                    && !level.canSeeSky(BlockPos.containing(user.getEyePosition())), "Zombie/resource/shade identity");
            if (!produced) premise(!ModStatusEffects.isStunned(user), "status arrived without owned producer");
        }
        private HeldInputEntry held() { return user.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT).map(state -> state.heldKeys.get(HOLD_KEY)).orElse(null); }
        private Frame frame() {
            EntityActionInstance current = LivingComponentAction.getCurEntityAction(user); HeldInputEntry retained = held();
            premise(current == null || current == action, "another action replaced held action");
            premise(retained == null || retained == input, "another generation replaced held key");
            MobEffectInstance stun = user.getEffect(ModStatusEffects.STUN);
            return new Frame(user.tickCount, level.getGameTime(), user.isAlive(), user.isRemoved(), user.getHealth(), user.getMaxHealth(),
                    ModStatusEffects.isStunned(user), stun == null ? 0 : stun.getDuration(), current == action && current != null,
                    action != null && action.isOver(), action == null ? null : action.getPhase(), action == null ? Float.NaN : action.getPhaseTick(),
                    retained == input && retained != null, retained == null ? 0 : retained.generation, data != null && data.isDisguiseEnabled());
        }
        private void observeEvents() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == user) {
                    premise(!event.isCanceled() && user.tickCount == lastAge + 1, "consecutive uncanceled user Pre");
                    preAge = user.tickCount; preTime = level.getGameTime(); actorState();
                }
                if (event.getEntity() == attacker) premise(!event.isCanceled(), "attacker natural Pre canceled");
            });
            Consumer<EntityTickEvent.Post> opening = event -> observe(() -> {
                if (event.getEntity() != user) return;
                premise(!componentOpen && preAge == user.tickCount && preTime == level.getGameTime(), "actual component opening bracket");
                componentOpen = true; if (pressed) before = frame();
            });
            Consumer<EntityTickEvent.Post> closing = event -> observe(() -> {
                if (event.getEntity() == attacker) attackerPosts++;
                if (event.getEntity() == user) {
                    premise(componentOpen && preAge == user.tickCount && preTime == level.getGameTime(), "actual component closing bracket");
                    if (pressed) { holdRows++; inspectBracket(before, frame()); }
                    userPosts++; lastAge = user.tickCount; componentOpen = false;
                }
                if (lane == Lane.STUN && event.getEntity() instanceof PillarmanRibEntity rib && ribs.containsKey(rib.getUUID())) qualifyStun(rib);
            });
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level) return;
                if (event.getEntity() instanceof PillarmanRibEntity rib && rib.getOwner() == attacker) {
                    owned.add(rib);
                    observe(() -> {
                        premise(ribs.put(rib.getUUID(), new Rib(rib)) == null && ribs.size() <= 8, "unique owned volley");
                        EntityActionInstance emitter = LivingComponentAction.getCurEntityAction(attacker);
                        premise(lane == Lane.STUN && launchRequested && !event.isCanceled() && emitter != null
                                && emitter instanceof PillarmanRibsBladesAbility.RibsBladesInstance && emitter.ability == ribAbility
                                && emitter.getPowerUser() == attacker && emitter.getPhase() == ActionPhase.PERFORM
                                && (volleyAction == null || volleyAction == emitter) && rib.getType() == ModEntityTypes.PILLAR_MAN_RIBS.get()
                                && rib.tickCount == 0 && rib.ticksLifespan() == 21, "actual registered Ribs CLICK emission");
                        volleyAction = emitter; log("rib-emission uuid=" + rib.getUUID() + " owner=" + attacker.getUUID());
                    });
                }
                if (event.getEntity() instanceof ExperienceOrb && lane == Lane.DEATH && swingOpen && deathSource != null
                        && user.getBoundingBox().inflate(1).contains(event.getEntity().position())) owned.add(event.getEntity());
            };
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                Rib rib = ribs.get(event.getProjectile().getUUID()); if (rib == null) return;
                rib.impacts.add(event);
                if (!(event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() == user)) {
                    log("other-rib-contact uuid=" + rib.entity.getUUID() + " kind=" + event.getRayTraceResult().getType()
                            + " target=" + (event.getRayTraceResult() instanceof EntityHitResult hit ? hit.getEntity().getUUID() : "none"));
                }
                premise(!(event.getRayTraceResult() instanceof EntityHitResult hit) || hit.getEntity() == user,
                        "foreign actor in owned rib contact");
            });
            Consumer<LivingIncomingDamageEvent> incoming = event -> observe(() -> {
                if (event.getEntity() == attacker) premise(false, "unexpected attacker damage");
                if (event.getEntity() != user) return;
                DamageSource source = event.getSource();
                premise(pressed && source.getEntity() == attacker && (produced || held() == input) && input.generation > 0
                        && (lane == Lane.STUN ? source.getDirectEntity() instanceof PillarmanRibEntity rib && ribs.containsKey(rib.getUUID())
                                && source.is(ModDamageTypes.MOD_PROJECTILE) : swingOpen && source.getDirectEntity() == attacker), "unowned/unadmitted incoming source");
                hits.add(new Hit(event, user.getHealth()));
            });
            Consumer<LivingDamageEvent.Post> damage = event -> observe(() -> {
                if (event.getEntity() != user) return;
                Hit hit = hits.stream().filter(candidate -> candidate.incoming.getSource() == event.getSource() && candidate.applied == null).findFirst().orElseThrow();
                hit.applied = event.getNewDamage(); hit.healthAfter = user.getHealth();
                log("damage applied=" + hit.applied + " HP=" + hit.healthBefore + "->" + hit.healthAfter + " source=" + event.getSource().getMsgId());
                if (lane == Lane.DAMAGE_ONLY && hit.accepted()) {
                    premise(user.isAlive() && !ModStatusEffects.isStunned(user), "surviving nonstun control did not survive"); produced = true;
                }
            });
            Consumer<LivingDeathEvent> death = event -> observe(() -> {
                if (event.getEntity() != user) return;
                premise(lane == Lane.DEATH && swingOpen && event.getSource().getEntity() == attacker
                        && event.getSource().getDirectEntity() == attacker && user.getHealth() == 0
                        && held() == input && (deathSource == null || deathSource == event.getSource()), "actual owned weapon death/source/retained key");
                deathSource = event.getSource(); deaths.add(event); produced = true;
                log("death callback=" + deaths.size() + " canceled=" + event.isCanceled() + " original=" + user.getUUID() + " heldGeneration=" + input.generation);
            });
            Consumer<LivingDropsEvent> drops = event -> {
                if (closed || event.getEntity() != user) return;
                owned.addAll(event.getDrops());
                observe(() -> premise(lane == Lane.DEATH && swingOpen && deathSource == event.getSource()
                        && !deaths.isEmpty(), "owned victim drops lack synchronous lethal weapon source"));
            };
            add(pre, EntityTickEvent.Pre.class, EventPriority.LOWEST, true);
            add(opening, EntityTickEvent.Post.class, EventPriority.HIGHEST, false);
            add(closing, EntityTickEvent.Post.class, EventPriority.LOWEST, false);
            add(join, EntityJoinLevelEvent.class, EventPriority.LOWEST, true);
            add(impact, ProjectileImpactEvent.class, EventPriority.LOWEST, true);
            add(incoming, LivingIncomingDamageEvent.class, EventPriority.LOWEST, true);
            add(damage, LivingDamageEvent.Post.class, EventPriority.LOWEST, false);
            add(death, LivingDeathEvent.class, EventPriority.LOWEST, true);
            add(drops, LivingDropsEvent.class, EventPriority.LOWEST, true);
        }
        private void inspectBracket(Frame first, Frame after) {
            premise(first != null && first.age == after.age && first.time == after.time, "paired real component frames");
            log("row=" + holdRows + " before=" + first + " after=" + after);
            if (!produced) {
                premise(first.alive && !first.stunned && first.currentOwned && first.retainedKey && first.phase == ActionPhase.WINDUP
                        && !first.over && !first.disguise && first.generation == input.generation, "healthy retained WINDUP before condition");
                premise(holdRows < 55, "condition not produced during bounded WINDUP");
                return;
            }
            if (!checked) {
                premise(producedBefore != null && producedBefore.alive && producedBefore.currentOwned && producedBefore.retainedKey
                        && producedBefore.phase == ActionPhase.WINDUP && !producedBefore.over, "producer lacked precondition live hold");
                premise(hits.stream().anyMatch(Hit::accepted), "no accepted positive applied damage/HP loss");
                premise(first.retainedKey && first.generation == input.generation, "no retained HOLD at next original component bracket");
                if (lane == Lane.STUN) premise(first.alive && first.stunned && first.stunDuration > 0, "no actual STUN at next component");
                if (lane == Lane.DEATH) premise(!first.alive && !first.removed && !deaths.isEmpty()
                        && deaths.stream().noneMatch(LivingDeathEvent::isCanceled) && user.getHealth() == 0, "no uncanceled original dead-component bracket");
                if (lane == Lane.DAMAGE_ONLY) {
                    premise(first.alive && !first.stunned, "nonstun survivor condition changed");
                    oracle(after.currentOwned && !after.over && after.phase == ActionPhase.WINDUP && after.counter > first.counter
                            && after.retainedKey && !after.disguise, "ordinary surviving damage canceled the eligible hold");
                }
                else oracle(after.over && !after.currentOwned && (after.phase == null || after.counter <= first.counter),
                        "failed live/dead condition did not stop original hold on natural component tick");
                checked = true; conditionRows++;
            }
            else if (lane == Lane.DAMAGE_ONLY) {
                premise(user.isAlive() && !ModStatusEffects.isStunned(user), "surviving control stopped being ordinary");
                if (action.isOver()) {
                    oracle(data.isDisguiseEnabled(), "eligible damaged hold ended without its established natural result");
                    if (LivingComponentAction.getCurEntityAction(user) == null) done = true;
                }
                else oracle(after.currentOwned && !after.over && after.retainedKey, "surviving nonstun hold was interrupted");
                oracle(holdRows < 75, "eligible damaged hold did not reach its bounded natural result");
            }
            else {
                oracle(action.isOver() && LivingComponentAction.getCurEntityAction(user) != action, "aborted original hold resumed");
                if (lane == Lane.STUN) oracle(!data.isDisguiseEnabled(), "aborted stun hold later toggled disguise");
                if (++settleRows >= 2) done = true;
            }
        }
        private void qualifyStun(PillarmanRibEntity rib) {
            if (produced) return;
            Rib tracked = ribs.get(rib.getUUID());
            boolean hit = hits.stream().anyMatch(candidate -> candidate.accepted() && candidate.incoming.getSource().getDirectEntity() == rib);
            if (!hit) return;
            premise(ribs.size() == 8 && volleyAction == ribAction
                    && tracked.impacts.stream().anyMatch(event -> !event.isCanceled() && event.getRayTraceResult() instanceof EntityHitResult result
                    && result.getEntity() == user) && rib.isAttachedToAnEntity() && rib.saveWithoutId(new CompoundTag()).getInt("AttachedEntity") == user.getId()
                    && user.isAlive() && user.hasEffect(ModStatusEffects.STUN) && user.getEffect(ModStatusEffects.STUN).getDuration() > 0,
                    "accepted natural rib hit did not produce attachment/STUN");
            produced = true; log("STUN-PRODUCED naturalRib=" + rib.getUUID() + " target=" + user.getUUID() + " duration=" + user.getEffect(ModStatusEffects.STUN).getDuration());
        }
        private void grant() {
            log("victim-XP level=" + user.experienceLevel + " total=" + user.totalExperience + " progress=" + user.experienceProgress);
            premise(user.onGround() && attacker.onGround()
                    && !level.canSeeSky(BlockPos.containing(user.getEyePosition()))
                    && !level.canSeeSky(BlockPos.containing(attacker.getEyePosition())), "natural grounded shade before power grant");
            premise(power.trySetPowerType(ModPlayerPowers.ZOMBIE.get()), "real Zombie grant");
            data = PlayerPower.getPowerData(user, ModPlayerPowers.ZOMBIE).orElseThrow();
            var found = power.getAbility("zombie_disguise");
            premise(found instanceof ZombieDisguiseAbility && found.abilityType == ZombiePowerType.ZOMBIE_DISGUISE.get()
                    && !data.isDisguiseEnabled() && data.getEnergy() > 0, "actual Disguise/default bootstrap");
            ability = (ZombieDisguiseAbility) found; LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            if (lane == Lane.STUN) {
                premise(attackerPower.trySetPowerType(ModPlayerPowers.PILLAR_MAN.get()), "real Pillar grant");
                pillar = PlayerPower.getPowerData(attacker, ModPlayerPowers.PILLAR_MAN).orElseThrow();
                pillar.setEvolutionStage(2, attacker); pillar.setMode(PillarmanMode.NONE, attacker); pillar.setEnergy(attacker, 300F);
                ribAbility = attackerPower.getAbility("pillarman_ribs_blades");
                premise(ribAbility instanceof PillarmanRibsBladesAbility && ribAbility.abilityType == PillarmanPowerType.PILLAR_MAN_RIBS_BLADES.get(), "actual Ribs registry");
                LivingComponentAction.getComponent(attacker).entityAim.setTarget(ActionTarget.EMPTY);
            }
            granted = true; grantPosts = userPosts;
            log("grant Zombie HP/max=" + user.getHealth() + "/" + user.getMaxHealth() + " armor=" + user.getArmorValue()
                    + " energy=" + data.getEnergy() + " attackerDamage=" + attacker.getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        private void pressHold() {
            actorState(); premise(user.isAlive() && !ModStatusEffects.isStunned(user) && !data.isDisguiseEnabled()
                    && held() == null && LivingComponentAction.getCurEntityAction(user) == null, "fresh live HOLD admission");
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD), "actual HOLD condition admission");
            pressed = true; input = AbilityInput.keyPress(HOLD_KEY, ability, user, null, InputMethod.HOLD, 0F, BufferingState.clickOnly(), ability.getAbilityId());
            premise(input != null && input.action instanceof ZombieDisguiseAbility.DisguiseInstance, "actual registered Disguise instance");
            action = (EntityActionInstance) input.action;
            premise(action == LivingComponentAction.getCurEntityAction(user) && action.getPowerUser() == user && held() == input && input.generation > 0
                    && action.getPhase() == ActionPhase.WINDUP && action.getCurPhaseLength() == 60F, "real zero-skip WINDUP input identity");
        }
        private void pressRibs() {
            premise(pillar.getEvolutionStage() == 2 && pillar.getMode() == PillarmanMode.NONE && !pillar.isStoneFormEnabled()
                    && pillar.getEnergy() > 60F && attacker.getMainHandItem().isEmpty(), "ordinary admitted Ribs attacker");
            AvailableAbilities available = new AvailableAbilities(); available.update(attackerPower, attackerPower.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ribAbility), attacker, InputMethod.CLICK), "real Ribs CLICK admission");
            producedBefore = frame(); launchRequested = true;
            ribInput = AbilityInput.keyPress(RIB_KEY, ribAbility, attacker, null, InputMethod.CLICK, 0F, BufferingState.clickOnly(), ribAbility.getAbilityId());
            premise(ribInput != null && ribInput.action instanceof PillarmanRibsBladesAbility.RibsBladesInstance, "actual Ribs action input"); ribAction = (EntityActionInstance) ribInput.action;
        }
        private void swing() {
            attacker.moveTo(user.getX(), floorY, user.getZ() - 1.3D, 0, 0);
            attacker.setYHeadRot(0); attacker.yBodyRot = 0; ready(attacker);
            premise(attacker.getMainHandItem().is(Items.NETHERITE_AXE) && attacker.getAttackStrengthScale(0.5F) >= 1F
                    && attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 9D && attacker.distanceToSqr(user) < 4D
                    && user.isAlive() && action.getPhase() == ActionPhase.WINDUP && action.getPhaseTick() < 55
                    && held() == input && !user.isBlocking(), "genuine charged in-range weapon swing");
            producedBefore = frame(); float hp = user.getHealth(); int count = hits.size(); swings++; swingOpen = true;
            try { attacker.attack(user); } finally { swingOpen = false; }
            premise(hits.size() > count && hits.subList(count, hits.size()).stream().anyMatch(Hit::accepted) && user.getHealth() < hp,
                    "weapon did not cause genuine accepted damage/HP loss");
            log("weapon-swing=" + swings + " HP=" + hp + "->" + user.getHealth() + " naturalRecharge=" + attacker.getAttackStrengthScale(0.5F));
        }
        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 140, "finite setup/condition/component watchdog");
                if (!granted && userPosts >= 2 && attackerPosts >= 2 && user.onGround() && attacker.onGround()
                        && !level.canSeeSky(BlockPos.containing(user.getEyePosition()))
                        && !level.canSeeSky(BlockPos.containing(attacker.getEyePosition()))) grant();
                else if (granted && !pressed && userPosts >= grantPosts + 3) pressHold();
                else if (pressed && !produced && holdRows >= 3) {
                    if (lane == Lane.STUN && !launchRequested) pressRibs();
                    if (lane != Lane.STUN && attacker.getAttackStrengthScale(0.5F) >= 1F) {
                        premise(lane != Lane.DEATH || swings < 3, "finite charged-axe route did not kill during WINDUP; unqualified death");
                        swing();
                    }
                }
                if (ribInput != null && ribs.size() == 8 && AbilityInput.isHeldByKey(attacker, ribAction)) {
                    premise(volleyAction == ribAction && AbilityInput.keyReleaseAndGetGeneration(RIB_KEY, attacker) == ribInput.generation, "owned volley/generation release");
                }
                if (done) { log("RESULT qualified=true conditionRows=" + conditionRows + " naturalPostStop=true native=false"); close(); helper.succeed(); return; }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }
        private <T extends net.neoforged.bus.api.Event> void add(Consumer<T> listener, Class<T> type, EventPriority priority, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(priority, canceled, type, listener);
        }
        private void observe(Runnable operation) { if (!closed && !done && observerFailure == null) try { operation.run(); } catch (RuntimeException | Error error) { observerFailure = error; } }
        private void cleanup(Runnable operation, List<Throwable> failures) { try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); } }
        @Override public void close() {
            if (closed) return; closed = true; List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures); listeners.clear();
            cleanup(() -> { if (pressed && user != null) AbilityInput.keyRelease(HOLD_KEY, user); }, failures);
            cleanup(() -> { if (launchRequested && attacker != null) AbilityInput.keyRelease(RIB_KEY, attacker); }, failures);
            for (Player actor : new Player[] { user, attacker }) if (actor != null) cleanup(() -> {
                if (actor.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT).map(state -> !state.heldKeys.isEmpty()).orElse(false)) throw new IllegalStateException("owned key remains");
            }, failures);
            for (Player actor : new Player[] { user, attacker }) if (actor != null)
                cleanup(() -> actor.getInventory().clearContent(), failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            cleanup(() -> { if (attackerPower != null) attackerPower.setPowerType(null); }, failures);
            for (Entity actor : owned) cleanup(() -> { if (!actor.isRemoved()) actor.discard(); }, failures);
            for (var entry : blocks.entrySet()) cleanup(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            cleanup(() -> {
                boolean restored = blocks.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()) && level.getBlockEntity(entry.getKey()) == null);
                boolean gone = owned.stream().allMatch(actor -> actor.isRemoved() && level.getEntity(actor.getUUID()) == null);
                log("cleanup cells=" + blocks.size() + " exactRestore=" + restored + " ownedGone=" + gone + " listeners=0");
                if (!restored || !gone) throw new IllegalStateException("held-condition scene cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("held-condition cleanup failed"); failures.forEach(error::addSuppressed); throw error; }
        }
        private void closeAfterFailure(Throwable error) { try { close(); } catch (RuntimeException | Error cleanup) { error.addSuppressed(cleanup); } }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { if (test.getError() != null) closeAfterFailure(test.getError()); else close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close(); }
    }
}
