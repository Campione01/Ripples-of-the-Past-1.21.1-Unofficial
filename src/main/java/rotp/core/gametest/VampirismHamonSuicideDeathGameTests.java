package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismHamonSuicideAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismHamonSuicideDeathGameTests {
    private VampirismHamonSuicideDeathGameTests() {}

    @GameTest(template = "empty", skyAccess = true, required = true,
            batch = "vampire_hamon_suicide_death", timeoutTicks = 150)
    public static void naturallyLethalSelfHamonDoesNotContinueDeadWindup(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private record Frame(int age, long time, boolean alive, boolean removed, float health, float maxHealth,
            int deathTime, boolean ownAction, boolean actionOver, ActionPhase phase, float phaseTick,
            boolean keyHeld, long generation, float blood) {}

    private static final class Attempt {
        final DamageSource source;
        final LivingIncomingDamageEvent incoming;
        final float healthBefore;
        final float phaseTick;
        Float applied;
        float healthAfter;
        Attempt(DamageSource source, LivingIncomingDamageEvent incoming, float healthBefore, float phaseTick) {
            this.source = source; this.incoming = incoming; this.healthBefore = healthBefore; this.phaseTick = phaseTick;
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 31;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Attempt> attempts = new ArrayList<>();
        private final List<LivingDeathEvent> deathCallbacks = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private StandPower stand;
        private VampirismData data;
        private VampirismHamonSuicideAbility ability;
        private EntityActionInstance action;
        private HeldInputEntry input;
        private ChunkPos chunk;
        private AABB room;
        private Frame before;
        private Frame deadBefore;
        private Frame deadAfter;
        private Attempt pending;
        private Attempt lethal;
        private LivingDeathEvent death;
        private Throwable observerFailure;
        private int lastAge;
        private int preAge = -1;
        private long preTime = -1;
        private int posts;
        private int conversionPosts = -1;
        private int deathAge = -1;
        private long deathGameTime = -1;
        private boolean deathPostObserved;
        private boolean componentActive;
        private boolean pressed;
        private boolean done;
        private boolean closed;

        Fixture(GameTestHelper helper) { this.helper = helper; level = helper.getLevel(); }

        private void setUp() {
            premise(level.getDifficulty() == Difficulty.NORMAL && !level.dimensionType().ultraWarm(), "ordinary world unavailable");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), y = template.getY() + 32;
            room = new AABB(x, y - 1, z, x + 16, y + 4, z + 16);
            premise(room.minY >= level.getMinBuildHeight() && room.maxY < level.getMaxBuildHeight()
                    && level.getEntities((Entity) null, room).isEmpty(), "owned room unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(Math.nextDown(room.maxX), Math.nextDown(room.maxY), Math.nextDown(room.maxZ)))) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "owned room obstructed");
            }
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                putBlock(new BlockPos(x + dx, y + 3, z + dz));
                putBlock(new BlockPos(x + dx, y - 1, z + dz));
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.moveTo(x + 8.5D, y, z + 8.5D, 0, 0);
            premise(user.getHealth() == 20F && user.getMaxHealth() == 20F
                    && user.getAttributeBaseValue(Attributes.MAX_HEALTH) == 20D, "fresh actor is not default20HP");
            premise(level.addFreshEntity(user), "actor was not added");
            lastAge = user.tickCount;
            stand = PowerClass.STAND.attachGet(user);
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, HamonData.pointsAtLevel(10), true, true);
            registerObservers();
            log("human-bootstrap user=" + user.getUUID() + " health=" + user.getHealth() + "/" + user.getMaxHealth()
                    + " ownedCells=" + blocks.size());
        }

        private boolean shadeReady() {
            if (user == null) return false;
            BlockPos sun = BlockPos.containing(user.getX(), Math.round(user.getY(1D)), user.getZ());
            return blocks.keySet().stream().allMatch(pos -> level.getBlockState(pos).equals(Blocks.STONE.defaultBlockState()))
                    && level.isPositionEntityTicking(user.blockPosition()) && !level.canSeeSky(sun);
        }

        private void convert() {
            premise(data == null && posts >= 20 && user.onGround() && shadeReady(), "shade/grounding not ready before conversion");
            HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            int strength = hamon.getHamonStrengthLevel();
            String character = hamon.getCharacterTechniqueName();
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
            premise(strength == 10 && data.isVampireHamonUser() && data.getHamonStrengthLevel() == strength
                    && data.getPrevHamonCharacter().equals(character) && PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).isEmpty()
                    && !data.isVampireAtFullPower() && !data.isBeingCured() && user.getHealth() == 20F,
                    "normal conversion did not preserve the actual Hamon data");
            var found = power.getAbility("vampirism_hamon_suicide");
            premise(found instanceof VampirismHamonSuicideAbility
                    && found.abilityType == VampirismPowerType.VAMPIRE_HAMON_SUICIDE.get(), "wrong registered Suicide");
            ability = (VampirismHamonSuicideAbility) found;
            conversionPosts = posts;
            log("bootstrap user=" + user.getUUID() + " health=" + user.getHealth() + "/" + user.getMaxHealth()
                    + " baseHealth=" + user.getAttributeBaseValue(Attributes.MAX_HEALTH) + " blood=" + blood()
                    + " preservedStrength=" + strength + " character=" + character + " effects=" + user.getActiveEffects()
                    + " gravity=" + !user.isNoGravity() + " ownedCells=" + blocks.size());
        }

        private void putBlock(BlockPos pos) {
            blocks.put(pos, level.getBlockState(pos));
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }

        private void registerObservers() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() != user || done) return;
                premise(!event.isCanceled() && user.tickCount == lastAge + 1, "missing natural actor Pre");
                preAge = user.tickCount; preTime = level.getGameTime();
                requireActor();
            });
            Consumer<EntityTickEvent.Post> startPost = event -> observe(() -> {
                if (event.getEntity() != user || done) return;
                premise(!componentActive && preAge == user.tickCount && preTime == level.getGameTime(), "unpaired component Post");
                before = snapshot();
                componentActive = true;
                pending = null;
                if (pressed && death == null) {
                    premise(before.ownAction && !before.actionOver && before.phase == ActionPhase.WINDUP
                            && before.keyHeld && before.generation == input.generation, "live admitted hold was replaced or released");
                    premise(before.phaseTick < 90F, "no natural WINDUP death before the finite observation boundary");
                }
                if (deathPostObserved) {
                    log("dead-component-admission " + before);
                    premise(!before.alive && !before.removed && before.age > deathAge && before.ownAction && !before.actionOver
                            && before.phase == ActionPhase.WINDUP && before.keyHeld && before.generation == input.generation
                            && heldEntry() == input, "UNOBSERVED: removal/release/replacement precluded the original dead held action");
                }
            });
            Consumer<LivingIncomingDamageEvent> incoming = event -> observe(() -> {
                if (event.getEntity() != user || done) return;
                DamageSource source = event.getSource();
                log("incoming type=" + source.getMsgId() + " direct=" + id(source.getDirectEntity())
                        + " cause=" + id(source.getEntity()) + " amount=" + event.getOriginalAmount()
                        + " canceled=" + event.isCanceled() + " state=" + snapshot());
                premise(pressed && componentActive && pending == null && before != null && before.alive
                        && LivingComponentAction.getCurEntityAction(user) == action && action.getPhase() == ActionPhase.WINDUP
                        && ((int) action.getPhaseTick() + 1) % 10 == 5 && source.is(ModDamageTypes.HAMON)
                        && source.getDirectEntity() == user && source.getEntity() == user
                        && event.getOriginalAmount() > 0 && !event.isCanceled(), "damage is not the uncanceled intrinsic self-Hamon pulse");
                pending = new Attempt(source, event, user.getHealth(), action.getPhaseTick());
                attempts.add(pending);
            });
            Consumer<LivingDamageEvent.Post> damagePost = event -> observe(() -> {
                if (event.getEntity() != user || done) return;
                premise(pending != null && pending.source == event.getSource() && pending.applied == null,
                        "accepted damage Post lacks its owned incoming event");
                pending.applied = event.getNewDamage();
                pending.healthAfter = user.getHealth();
                log("damage-post applied=" + pending.applied + " health=" + pending.healthBefore + "->" + pending.healthAfter
                        + " phaseTick=" + pending.phaseTick);
            });
            Consumer<LivingDeathEvent> onDeath = event -> observe(() -> {
                if (event.getEntity() != user || done) return;
                log("death canceled=" + event.isCanceled() + " type=" + event.getSource().getMsgId() + " state=" + snapshot());
                premise(componentActive && before != null && before.alive && before.age == user.tickCount
                        && before.time == level.getGameTime() && !deathPostObserved && !event.isCanceled()
                        && pending != null && pending.source == event.getSource() && pending.applied != null && pending.applied > 0F
                        && !pending.incoming.isCanceled() && user.getHealth() <= 0F,
                        "death was canceled or not caused by owned natural self-damage");
                if (death == null) {
                    death = event; lethal = pending; deathAge = user.tickCount; deathGameTime = level.getGameTime();
                }
                premise(lethal == pending && lethal.source == event.getSource() && deathAge == user.tickCount
                        && deathGameTime == level.getGameTime() && !deathCallbacks.contains(event),
                        "unrelated or duplicate death callback escaped the single lethal sequence");
                deathCallbacks.add(event);
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() != user || done) return;
                premise(componentActive && before != null && before.age == user.tickCount && before.time == level.getGameTime(),
                        "component work lacks its natural bracket");
                Frame after = snapshot();
                if (pending != null) {
                    premise(!pending.incoming.isCanceled() && pending.applied != null && pending.applied > 0F
                            && pending.healthAfter < pending.healthBefore, "self pulse did not cause actual health loss");
                }
                if (death != null && !deathPostObserved) {
                    premise(!deathCallbacks.isEmpty() && deathCallbacks.stream().allMatch(e -> !e.isCanceled())
                            && !after.alive && after.health <= 0F && user.tickCount == deathAge && level.getGameTime() == deathGameTime,
                            "death did not survive its actual actor Post");
                    deathPostObserved = true;
                    log("death-post callbacks=" + deathCallbacks.size() + " " + after);
                }
                if (deathPostObserved && !before.alive) {
                    EntityActionInstance current = LivingComponentAction.getCurEntityAction(user);
                    premise(!after.removed && (current == null || current == action) && (heldEntry() == null || heldEntry() == input)
                            && pending == null, "UNOBSERVED: unrelated replacement/removal or extra pulse in the dead bracket");
                    deadBefore = before; deadAfter = after; done = true;
                    log("next-dead-component before=" + deadBefore + " after=" + deadAfter);
                }
                else if (pending != null) log("pulse-post before=" + before + " after=" + after);
                lastAge = user.tickCount;
                posts++;
                componentActive = false;
                before = null;
                pending = null;
            });
            Consumer<EntityLeaveLevelEvent> leave = event -> observe(() -> {
                if (event.getEntity() != user || done) return;
                premise(death != null && deathPostObserved && !user.isAlive(), "actor left before natural death was witnessed");
                log("natural-removal containment reason=" + user.getRemovalReason() + " state=" + snapshot());
                premise(false, "UNOBSERVED: natural removal precluded required dead component revalidation");
            });
            listeners.add(pre); listeners.add(startPost); listeners.add(incoming); listeners.add(damagePost);
            listeners.add(onDeath); listeners.add(post); listeners.add(leave);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Post.class, startPost);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, damagePost);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDeathEvent.class, onDeath);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityLeaveLevelEvent.class, leave);
        }

        private void requireActor() {
            AABB box = user.getBoundingBox();
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            BlockPos sun = BlockPos.containing(user.getX(), Math.round(user.getY(1D)), user.getZ());
            try {
                premise(box.minX >= room.minX && box.maxX <= room.maxX && box.minY >= room.minY && box.maxY <= room.maxY
                        && box.minZ >= room.minZ && box.maxZ <= room.maxZ && new ChunkPos(min).equals(chunk) && new ChunkPos(max).equals(chunk)
                        && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max), "actor left the naturally ticking room");
            }
            catch (RuntimeException | Error failure) {
                try {
                    log("room-predicate-failure sample=post-assert-read"
                            + " minX=" + (box.minX >= room.minX) + " maxX=" + (box.maxX <= room.maxX)
                            + " minY=" + (box.minY >= room.minY) + " maxY=" + (box.maxY <= room.maxY)
                            + " minZ=" + (box.minZ >= room.minZ) + " maxZ=" + (box.maxZ <= room.maxZ)
                            + " minChunkMatch=" + new ChunkPos(min).equals(chunk) + " maxChunkMatch=" + new ChunkPos(max).equals(chunk)
                            + " minTicking=" + level.isPositionEntityTicking(min) + " maxTicking=" + level.isPositionEntityTicking(max)
                            + " age=" + user.tickCount + " time=" + level.getGameTime() + " posts=" + posts
                            + " stage=" + (data == null ? "human" : pressed ? "held" : "converted")
                            + " phase=" + (action == null ? "none" : action.getPhase() + "/" + action.getPhaseTick())
                            + " alive=" + user.isAlive() + " removed=" + user.isRemoved() + " health=" + user.getHealth()
                            + " deathPostObserved=" + deathPostObserved + " pos=" + user.position() + " delta=" + user.getDeltaMovement()
                            + " onGround=" + user.onGround() + " noGravity=" + user.isNoGravity() + " box=" + box + " room=" + room
                            + " minCorner=" + min + " maxCorner=" + max + " expectedChunk=" + chunk
                            + " actualChunks=" + new ChunkPos(min) + "/" + new ChunkPos(max));
                }
                catch (RuntimeException | Error diagnosticFailure) { failure.addSuppressed(diagnosticFailure); }
                throw failure;
            }
            if (data != null) premise(!level.canSeeSky(sun), "converted actor lost verified shade");
            premise(!user.isCreative() && !user.isSpectator() && !user.isInvulnerable() && !user.getAbilities().invulnerable
                    && !user.getAbilities().instabuild && !user.isNoGravity() && user.getArmorValue() == 0
                    && user.getAttributeBaseValue(Attributes.MAX_HEALTH) == 20D && user.getMainHandItem().isEmpty()
                    && user.getOffhandItem().isEmpty() && user.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && !user.isUsingItem() && !user.isOnFire() && !user.hasEffect(ModStatusEffects.VAMPIRE_SUN_BURN)
                    && !EntityHamonChargeState.get(user).hasHamonCharge() && stand.getPowerType() == null,
                    "ordinary vulnerability or environmental isolation changed");
            if (data != null) {
                premise(power.getPowerType() == ModPlayerPowers.VAMPIRISM.get() && data.isVampireHamonUser()
                        && data.getHamonStrengthLevel() == 10 && !data.isBeingCured(), "converted power qualification changed");
            }
            else premise(power.getPowerType() == ModPlayerPowers.HAMON.get(), "pre-conversion human power changed");
        }

        private void press() {
            requireActor();
            premise(user.isAlive() && user.getHealth() > 0 && user.onGround() && attempts.isEmpty()
                    && ability.isAbilityAvailable(power) && ability.checkSpecificConditions(power).isPositive()
                    && HamonAbilityHelpers.hamonDamageAmount(user, 4F) > 0 && HamonAbilityHelpers.configHamonDamageMultiplier() > 0,
                    "ordinary live HOLD admission unavailable");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD),
                    "registered HOLD failed its public condition check");
            pressed = true;
            input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD, 0, BufferingState.clickOnly(), ability.getAbilityId());
            premise(input != null && input.action instanceof VampirismHamonSuicideAbility.HamonSuicideInstance shot
                    && shot.ability == ability, "registered HOLD did not install Suicide");
            action = (EntityActionInstance) input.action;
            premise(action == LivingComponentAction.getCurEntityAction(user) && action.getPhase() == ActionPhase.WINDUP
                    && action.getPowerUser() == user && action.getCurPhaseLength() == 100F
                    && input.keyId == KEY && input.powerClass == PowerClass.PLAYER_POWER && input.generation > 0 && heldEntry() == input
                    && AbilityInput.isHeldByKey(user, action), "actual action/key/generation identity differs");
            log("press type=" + ability.abilityType + " moveset=" + ability.getAbilityId() + " key=" + KEY
                    + " generation=" + input.generation + " state=" + snapshot() + " effects=" + user.getActiveEffects());
        }

        private HeldInputEntry heldEntry() {
            var state = user.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT).orElse(null);
            return state != null ? state.heldKeys.get(KEY) : null;
        }

        private Frame snapshot() {
            HeldInputEntry held = heldEntry();
            return new Frame(user.tickCount, level.getGameTime(), user.isAlive(), user.isRemoved(), user.getHealth(), user.getMaxHealth(),
                    user.deathTime, action != null && LivingComponentAction.getCurEntityAction(user) == action,
                    action == null || action.isOver(), action != null ? action.getPhase() : null,
                    action != null ? action.getPhaseTick() : -1F, action != null && held != null && held.action == action,
                    held != null ? held.generation : 0L, blood());
        }

        private float blood() { return data != null ? VampirismState.get(user).blood().current() : 0F; }
        private static UUID id(Entity entity) { return entity != null ? entity.getUUID() : null; }

        private void validate() {
            premise(done && death != null && !deathCallbacks.isEmpty() && deathPostObserved && lethal != null
                    && deathCallbacks.stream().allMatch(e -> !e.isCanceled() && e.getEntity() == user && e.getSource() == lethal.source)
                    && lethal.applied != null && lethal.applied > 0F && lethal.healthAfter <= 0F && !attempts.isEmpty()
                    && attempts.stream().allMatch(a -> !a.incoming.isCanceled() && a.applied != null && a.applied > 0F
                            && a.healthAfter < a.healthBefore), "uncanceled natural lethal self-damage was not established");
            premise(deadBefore != null && deadAfter != null && !deadBefore.alive && !deadAfter.alive
                    && !deadBefore.removed && !deadAfter.removed && deadBefore.ownAction && !deadBefore.actionOver
                    && deadBefore.phase == ActionPhase.WINDUP && deadBefore.keyHeld && deadBefore.generation == input.generation
                    && deadAfter.age > deathAge && deadBefore.age == deadAfter.age && deadBefore.time == deadAfter.time,
                    "no subsequent natural dead actor/component bracket");
            boolean continued = deadAfter.ownAction && !deadAfter.actionOver;
            boolean advanced = deadAfter.phaseTick > deadBefore.phaseTick || deadAfter.phase == ActionPhase.PERFORM;
            log("result serverOwnedHold=true ordinaryDeathScreen=false selfPulses=" + attempts.size()
                    + " continued=" + continued + " advanced=" + advanced + " before=" + deadBefore + " after=" + deadAfter);
            helper.assertTrue(!continued && !advanced,
                    "Suicide alive oracle: same action continued after uncanceled natural death; advanced=" + advanced);
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 130, "finite natural death/continuation watchdog expired");
                if (done) { validate(); close(); helper.succeed(); return; }
                premise(user != null && !user.isRemoved(), "actor disappeared without an observed containment boundary");
                if (data == null) {
                    boolean shaded = shadeReady();
                    if (posts >= 20 && user.onGround() && shaded) convert();
                    else if (helper.getTick() % 10 == 0) log("shade-wait posts=" + posts + " grounded=" + user.onGround() + " roofAndShade=" + shaded);
                }
                else if (!pressed && posts - conversionPosts >= 20) press();
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException | Error error) { observerFailure = error; JojoMod.LOGGER.error("Suicide death premise failed", error); }
        }

        private void premise(boolean valid, String message) { helper.assertTrue(valid, "Suicide premise: " + message); }
        private void log(String message) { JojoMod.LOGGER.info("SUICIDE-DEATH {}", message); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanupStep(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
            if (user != null) {
                if (pressed) cleanupStep(() -> AbilityInput.keyRelease(KEY, user), failures);
                cleanupStep(user::stopUsingItem, failures);
                cleanupStep(() -> user.getInventory().clearContent(), failures);
                cleanupStep(() -> { if (!user.isRemoved()) user.discard(); }, failures);
            }
            for (var entry : blocks.entrySet()) cleanupStep(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            cleanupStep(() -> {
                boolean restored = blocks.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                boolean gone = user == null || user.isRemoved() && level.getEntity(user.getUUID()) == null;
                boolean released = user == null || !pressed || heldEntry() == null;
                log("cleanup blocks=" + blocks.size() + " restored=" + restored + " actorGone=" + gone + " inputReleased=" + released);
                if (!restored || !gone || !released || !listeners.isEmpty()) throw new IllegalStateException("Suicide cleanup verification failed");
            }, failures);
            if (!failures.isEmpty()) {
                IllegalStateException error = new IllegalStateException("Suicide cleanup incomplete");
                failures.forEach(error::addSuppressed);
                throw error;
            }
        }

        private static void cleanupStep(Runnable operation, List<Throwable> failures) {
            try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }

        private void closeAfterFailure(Throwable primary) {
            try { close(); } catch (RuntimeException | Error cleanup) {
                primary.addSuppressed(cleanup);
                JojoMod.LOGGER.error("Suicide cleanup failed; primary retained", cleanup);
            }
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) {
            if (test.getError() != null) closeAfterFailure(test.getError()); else close();
        }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }
}
