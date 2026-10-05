package rotp.core.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonSendoOverdriveAbility;
import rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
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
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SendoOverdriveOverlapGameTests {
    private SendoOverdriveOverlapGameTests() {}

    @GameTest(template = "empty", skyAccess = true, required = true, batch = "sendo_single_source", timeoutTicks = 120)
    public static void singleRegisteredSendoReleaseDamagesThroughItsFirstWave(GameTestHelper helper) {
        start(helper, 1);
    }

    @GameTest(template = "empty", skyAccess = true, required = true, batch = "sendo_distinct_sources", timeoutTicks = 120)
    public static void distinctRegisteredSendoSourcesRespectTargetExclusion(GameTestHelper helper) {
        start(helper, 2);
    }

    private static void start(GameTestHelper helper, int casterCount) {
        Fixture fixture = new Fixture(helper, casterCount);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Caster {
        Player user;
        PlayerPower power;
        StandPower stand;
        HamonData hamon;
        HamonSendoOverdriveAbility ability;
        EntityActionInstance action;
        Source source;
        Vec3 start;
        short key;
        int posts;
        int pressPosts;
        float inputEnergy;
        float releaseEnergy;
        float releasePhaseTick;
        EnergyWindow postEnergy;
        EnergyWindow inputEnergyWindow;
        int emittedSources;
        int payments;
        float paidEnergy;
        boolean pressed;
        boolean released;
    }

    private record EnergyWindow(long time, float energy, float decay, int energyTicks, int emissions) {}

    private static final class Source {
        final HamonSendoOverdriveEntity entity;
        final Caster caster;
        float radius;
        float damage;
        float points;
        int wavesToAdd;
        int completedTicks;
        Sample sample;
        Source(HamonSendoOverdriveEntity entity, Caster caster) { this.entity = entity; this.caster = caster; }
    }

    private record Attempt(float amount, UUID direct, UUID cause, Vec3 sourcePosition,
            int targetPosts, LivingIncomingDamageEvent event) {}
    private record Before(int age, long time, int targetPosts, float health, double points,
            int gavePoints, List<Integer> waves, int wavesAdded, Vec3 origin, AABB fullBox, AABB firstHurtBox) {}
    private record Sample(UUID source, UUID caster, Before before, int targetPosts, float health,
            double points, int gavePoints, List<Integer> waves, int wavesAdded,
            boolean alive, List<Attempt> attempts) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final double EPS = 2.0E-5D;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final int casterCount;
        private final List<Caster> casters = new ArrayList<>();
        private final Map<UUID, Source> owned = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Sample> samples = new ArrayList<>();
        private final List<Attempt> attempts = new ArrayList<>();
        private IronGolem target;
        private BlockPos anchor;
        private AABB blockBox;
        private AABB room;
        private ChunkPos chunk;
        private Source active;
        private Before before;
        private Throwable observerFailure;
        private int targetPosts;
        private boolean samplingDone;
        private boolean closed;

        Fixture(GameTestHelper helper, int casterCount) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.casterCount = casterCount;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            room = new AABB(x + 4, y - 1, z + 3, x + 13, y + 6, z + 12);
            premise(room.minY >= level.getMinBuildHeight() && room.maxY < level.getMaxBuildHeight()
                    && level.getEntities((Entity) null, room).isEmpty(), "room unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(room.maxX, room.maxY, room.maxZ))) {
                premise(level.isEmptyBlock(pos), "room is obstructed");
            }
            anchor = new BlockPos(x + 8, y + 2, z + 8);
            blockBox = new AABB(anchor);
            blocks.put(anchor, level.getBlockState(anchor));
            level.setBlockAndUpdate(anchor, Blocks.STONE.defaultBlockState());

            target = EntityType.IRON_GOLEM.create(level);
            premise(target != null, "could not create default Iron Golem");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.moveTo(x + 8.5D, y + 2D, z + 9.8D, 180, 0);
            premise(level.addFreshEntity(target) && target.getMaxHealth() == 100F && target.getHealth() == 100F
                    && target.getArmorValue() == 0 && !target.isInvulnerable()
                    && !target.getBoundingBox().intersects(blockBox), "target defaults or physical separation changed");
            for (int i = 0; i < casterCount; i++) {
                Caster caster = new Caster();
                casters.add(caster);
                caster.key = (short) (26 + i);
                caster.user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
                GameType.SURVIVAL.updatePlayerAbilities(caster.user.getAbilities());
                caster.user.setNoGravity(true);
                caster.start = new Vec3(x + (i == 0 ? 6.75D : 10.25D), y + 1D, z + 5D);
                caster.user.moveTo(caster.start.x, caster.start.y, caster.start.z, 0, 0);
                Vec3 aim = new Vec3(x + 8.5D, y + 2.5D, z + 8D).subtract(caster.user.getEyePosition(1F));
                float yaw = (float) -Math.toDegrees(Math.atan2(aim.x, aim.z));
                float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
                caster.user.setYRot(yaw);
                caster.user.setXRot(pitch);
                caster.user.setYHeadRot(yaw);
                caster.user.yBodyRot = yaw;
                caster.user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                caster.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                premise(level.addFreshEntity(caster.user), "could not add caster");
                caster.power = PowerClass.PLAYER_POWER.attachGet(caster.user);
                caster.power.setPowerType(ModPlayerPowers.HAMON.get());
                caster.stand = PowerClass.STAND.attachGet(caster.user);
                caster.hamon = PlayerPower.getPowerData(caster.user, ModPlayerPowers.HAMON).orElseThrow();
                caster.hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
                caster.hamon.learnSkill(ModHamonSkills.SENDO_OVERDRIVE.get());
                caster.hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(10), true, true);
                caster.hamon.setBreathStability(caster.hamon.getMaxBreathStability());
                caster.hamon.setEnergy(caster.hamon.getMaxEnergy());
                LivingComponentAction.getComponent(caster.user).entityAim.setTarget(ActionTarget.EMPTY);
                var ability = caster.power.getAbility("sendo_overdrive");
                premise(ability instanceof HamonSendoOverdriveAbility && ability.abilityType == HamonPowerType.HAMON_SENDO_OVERDRIVE.get(),
                        "wrong registered Sendo ability");
                caster.ability = (HamonSendoOverdriveAbility) ability;
                log("caster uuid=" + caster.user.getUUID() + " feet=" + caster.start + " yaw=" + yaw + " pitch=" + pitch);
            }
            registerObservers();
            log("setup anchor=" + anchor + " face=NORTH target=" + target.getUUID() + " targetBox=" + target.getBoundingBox()
                    + " blockBox=" + blockBox + " zGap=" + (target.getBoundingBox().minZ - blockBox.maxZ)
                    + " health=" + target.getHealth() + " armor=" + target.getArmorValue()
                    + " targetNoAI=true actorsNoGravity=true room=" + room);
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level || !(event.getEntity() instanceof HamonSendoOverdriveEntity created)) return;
                CompoundTag nbt = created.saveWithoutId(new CompoundTag());
                if (!nbt.hasUUID("Owner")) return;
                UUID ownerId = nbt.getUUID("Owner");
                Caster caster = casters.stream().filter(c -> c.user != null && c.user.getUUID().equals(ownerId)).findFirst().orElse(null);
                if (caster == null) return;
                Source source = new Source(created, caster);
                owned.put(created.getUUID(), source);
                caster.emittedSources++;
                // Ownership is retained before fallible admission checks.
                observe(() -> {
                    EnergyWindow energy = caster.inputEnergyWindow != null ? caster.inputEnergyWindow : caster.postEnergy;
                    premise(energy != null && energy.time == level.getGameTime(), "emission escaped its measured energy window");
                    premise(caster.pressed && caster.released && caster.source == null && !event.isCanceled()
                            && created.getType() == ModEntityTypes.SENDO_HAMON_OVERDRIVE.get(), "unexpected owned emission");
                    caster.source = source;
                    EntityActionInstance action = LivingComponentAction.getCurEntityAction(caster.user);
                    premise(action == caster.action && action instanceof HamonSendoOverdriveAbility.SendoOverdriveInstance
                            && action.ability == caster.ability && action.getPhase() == ActionPhase.PERFORM,
                            "join lacks registered release/PERFORM action");
                    source.radius = nbt.getFloat("Radius");
                    source.damage = nbt.getFloat("Damage");
                    source.points = nbt.getFloat("Points");
                    source.wavesToAdd = nbt.getInt("WavesToAdd");
                    Vec3 center = Vec3.atCenterOf(anchor);
                    premise(created.tickCount == 0 && source.radius > 0 && Float.isFinite(source.radius)
                            && source.damage > 0 && Float.isFinite(source.damage) && source.points > 0
                            && source.wavesToAdd >= 2 && nbt.getInt("LifeSpan") == source.wavesToAdd * 4 + 15
                            && nbt.getInt("WavesAdded") == 0 && nbt.getIntArray("Waves").length == 0
                            && nbt.getInt("GavePoints") == 0 && nbt.getInt("Axis") == Direction.Axis.Z.ordinal()
                            && anchor.equals(created.getTargetedBlockPos()) && created.getTargetedFace() == Direction.NORTH
                            && created.position().distanceTo(center.add(0, -created.getBbHeight() * 0.5D, 0)) < EPS
                            && created.getBoundingBox().getCenter().distanceTo(center) < EPS
                            && created.getDeltaMovement().lengthSqr() == 0D, "invalid source metadata or block-centered origin");
                    log("join source=" + created.getUUID() + " owner=" + ownerId + " position=" + created.position()
                            + " box=" + created.getBoundingBox() + " radius=" + source.radius + " damage=" + source.damage
                            + " points=" + source.points + " waves=" + source.wavesToAdd + " angle=" + created.sparksAngle
                            + " releasePhase=" + caster.releasePhaseTick + " energyInput=" + caster.inputEnergy
                            + " energyRelease=" + caster.releaseEnergy + " energyJoin=" + caster.hamon.getEnergy());
                });
            };
            Consumer<EntityTickEvent.Post> startCasterPost = event -> observe(() -> {
                for (Caster caster : casters) if (event.getEntity() == caster.user) {
                    premise(caster.postEnergy == null && caster.inputEnergyWindow == null, "nested caster energy window");
                    int energyTicks = caster.hamon.serializeNBT(level.registryAccess()).getInt("EnergyTicks");
                    float decay = energyTicks > 0 || !JojoModConfig.getCommonConfigInstance(false).hamonEnergyTicksDown.get()
                            ? 0F : HamonData.ENERGY_TICK_DOWN_AMOUNT;
                    caster.postEnergy = new EnergyWindow(level.getGameTime(), caster.hamon.getEnergy(), decay,
                            energyTicks, caster.emittedSources);
                }
            });
            Consumer<EntityTickEvent.Pre> pre = event -> {
                Source source = owned.get(event.getEntity().getUUID());
                if (source == null || source.entity != event.getEntity() || samplingDone) return;
                observe(() -> {
                    requireActors();
                    requirePaidEmission(source.caster);
                    premise(active == null && !event.isCanceled() && !source.entity.isRemoved()
                            && source.entity.tickCount == source.completedTicks + 1, "missing consecutive natural source Pre");
                    if (!samples.isEmpty()) {
                        premise(targetPosts - samples.getFirst().targetPosts < 4,
                                "first-source windows did not overlap within four target Posts");
                    }
                    premise(source.entity.tickCount <= 7, "later pulse arrived before the selected first-wave window completed");
                    CompoundTag nbt = source.entity.saveWithoutId(new CompoundTag());
                    AABB fullBox = source.entity.getBoundingBox();
                    Vec3 center = fullBox.getCenter();
                    AABB hurtBox = new AABB(center, center).inflate(source.radius / 15D, source.radius / 15D, 1D);
                    active = source;
                    before = new Before(source.entity.tickCount, level.getGameTime(), targetPosts, target.getHealth(), training(source.caster),
                            nbt.getInt("GavePoints"), waveTicks(nbt), nbt.getInt("WavesAdded"), source.entity.position(), fullBox, hurtBox);
                    attempts.clear();
                    premise(inside(fullBox) && ticking(fullBox) && center.distanceTo(Vec3.atCenterOf(anchor)) < EPS
                            && Float.floatToIntBits(nbt.getFloat("Radius")) == Float.floatToIntBits(source.radius)
                            && Float.floatToIntBits(nbt.getFloat("Damage")) == Float.floatToIntBits(source.damage)
                            && Float.floatToIntBits(nbt.getFloat("Points")) == Float.floatToIntBits(source.points)
                            && nbt.getInt("WavesToAdd") == source.wavesToAdd
                            && nbt.getInt("Axis") == Direction.Axis.Z.ordinal()
                            && anchor.equals(source.entity.getTargetedBlockPos()) && source.entity.getTargetedFace() == Direction.NORTH,
                            "source moved, changed plane or left the ticking room");
                    if (before.age == 4) {
                        List<UUID> candidates = level.getEntitiesOfClass(LivingEntity.class, hurtBox,
                                entity -> entity.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity)
                                        && entity != source.caster.user).stream().map(Entity::getUUID).toList();
                        float eligibleAmount = HamonAbilityHelpers.hamonDamageAmount(target, source.damage)
                                * source.caster.hamon.getHamonDamageMultiplier() * HamonAbilityHelpers.configHamonDamageMultiplier();
                        log("birth-pre source=" + source.entity.getUUID() + " snapshot=" + before + " targetBox=" + target.getBoundingBox()
                                + " candidates=" + candidates + " eligibleAmount=" + eligibleAmount
                                + " targetBlockOverlap=" + target.getBoundingBox().intersects(blockBox));
                        premise(before.waves.isEmpty() && before.wavesAdded == 0 && before.gavePoints == 0
                                && hurtBox.intersects(target.getBoundingBox()) && candidates.equals(List.of(target.getUUID()))
                                && inside(hurtBox) && ticking(hurtBox) && eligibleAmount > 0 && Float.isFinite(eligibleAmount),
                                "first inner-wave birth/candidate was not independently admitted");
                    }
                });
            };
            Consumer<LivingIncomingDamageEvent> damage = event -> {
                if (event.getEntity() != target || samplingDone) return;
                observe(() -> {
                    DamageSource source = event.getSource();
                    premise(active != null && before != null && before.age == 4 && attempts.isEmpty()
                            && active.entity.tickCount == before.age && level.getGameTime() == before.time
                            && source.is(ModDamageTypes.HAMON) && source.getDirectEntity() == active.entity
                            && source.getEntity() == active.caster.user && event.getOriginalAmount() > 0 && !event.isCanceled()
                            && before.firstHurtBox.intersects(target.getBoundingBox()),
                            "incoming event is outside the owned first inner-wave contact");
                    attempts.add(new Attempt(event.getOriginalAmount(), active.entity.getUUID(), active.caster.user.getUUID(),
                            source.getSourcePosition(), targetPosts, event));
                    log("incoming source=" + active.entity.getUUID() + " cause=" + active.caster.user.getUUID()
                            + " targetPosts=" + targetPosts + " amount=" + event.getOriginalAmount()
                            + " sourcePosition=" + source.getSourcePosition() + " origin=" + before.origin);
                });
            };
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                for (Caster caster : casters) if (event.getEntity() == caster.user) {
                    premise(caster.postEnergy != null, "missing caster energy Post bracket");
                    finishEnergyWindow(caster, caster.postEnergy, "POST");
                    caster.postEnergy = null;
                    caster.posts++;
                }
                if (event.getEntity() == target) targetPosts++;
                Source source = owned.get(event.getEntity().getUUID());
                if (source == null || source.entity != event.getEntity() || samplingDone) return;
                premise(active == source && before != null && before.age == source.entity.tickCount
                        && before.time == level.getGameTime() && before.targetPosts == targetPosts,
                        "source Post lacks its natural Pre/target-tick interval");
                source.completedTicks++;
                CompoundTag nbt = source.entity.saveWithoutId(new CompoundTag());
                premise(!source.entity.isRemoved() && source.entity.position().distanceTo(before.origin) < EPS,
                        "source did not persist at its authored origin");
                if (before.age == 4) {
                    premise(source.sample == null && waveTicks(nbt).equals(List.of(1)) && nbt.getInt("WavesAdded") == 1,
                            "first inner wave did not actually tick to age one");
                    Sample sample = new Sample(source.entity.getUUID(), source.caster.user.getUUID(), before, targetPosts,
                            target.getHealth(), training(source.caster), nbt.getInt("GavePoints"), waveTicks(nbt), nbt.getInt("WavesAdded"),
                            !source.entity.isRemoved(), List.copyOf(attempts));
                    source.sample = sample;
                    samples.add(sample);
                    log("birth-post " + sample);
                }
                else {
                    premise(attempts.isEmpty() && Math.abs(target.getHealth() - before.health) < EPS
                            && Math.abs(training(source.caster) - before.points) < EPS,
                            "damage/training occurred outside the selected first-wave birth");
                }
                active = null;
                before = null;
                if (samples.size() == casterCount && owned.size() == casterCount) {
                    samplingDone = true;
                    // Freeze the complete first-wave window only after both natural Posts.
                    for (Source ownedSource : owned.values()) if (!ownedSource.entity.isRemoved()) ownedSource.entity.discard();
                }
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(startCasterPost);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Post.class, startCasterPost);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void requireActors() {
            premise(target.isAlive() && !target.isInvulnerable() && target.getMaxHealth() == 100F && target.getArmorValue() == 0
                    && target.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !EntityHamonChargeState.get(target).hasHamonCharge()
                    && !target.getBoundingBox().intersects(blockBox) && inside(target.getBoundingBox()) && ticking(target.getBoundingBox())
                    && level.getBlockState(anchor).is(Blocks.STONE), "target or solid-block separation changed");
            for (Caster caster : casters) {
                premise(caster.user.isAlive() && !caster.user.isCreative() && !caster.user.isSpectator()
                        && !caster.user.getAbilities().instabuild && !caster.user.getAbilities().invulnerable
                        && caster.user.position().distanceToSqr(caster.start) < 1.0E-10D
                        && caster.user.getMainHandItem().isEmpty() && caster.user.getOffhandItem().isEmpty()
                        && caster.user.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                        && !caster.user.isShiftKeyDown() && !caster.user.isUsingItem()
                        && caster.power.getPowerType() == ModPlayerPowers.HAMON.get() && caster.stand.getPowerType() == null
                        && caster.hamon.isSkillLearned(ModHamonSkills.SENDO_OVERDRIVE.get())
                        && !caster.hamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get())
                        && !caster.hamon.isSkillLearned(ModHamonSkills.NATURAL_TALENT.get())
                        && caster.hamon.getHamonControlLevel() == 10 && caster.hamon.getBloodstreamEfficiency(caster.user) > 0
                        && inside(caster.user.getBoundingBox()) && ticking(caster.user.getBoundingBox()), "caster lost normal admission");
            }
        }

        private BlockHitResult aim(Caster caster) {
            Vec3 eye = caster.user.getEyePosition(1F);
            HitResult hit = level.clip(new ClipContext(eye, eye.add(caster.user.getLookAngle().scale(10D)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster.user));
            premise(hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                    && block.getBlockPos().equals(anchor) && block.getDirection() == Direction.NORTH,
                    "real aim ray does not select the owned NORTH collider face");
            return (BlockHitResult) hit;
        }

        private void press(Caster caster) {
            BlockHitResult hit = aim(caster);
            premise(caster.hamon.getEnergy() > 900F && !caster.hamon.isAbilityOnCooldown("sendo_overdrive")
                    && HamonAbilityHelpers.configHamonDamageMultiplier() > 0F
                    && JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() > 0D,
                    "Hamon energy or configured effects unavailable");
            AvailableAbilities available = new AvailableAbilities();
            available.update(caster.power, caster.power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(caster.ability), caster.user, InputMethod.CLICK),
                    "registered Sendo CLICK was not admitted");
            caster.inputEnergy = caster.hamon.getEnergy();
            caster.pressPosts = caster.posts;
            caster.pressed = true;
            EnergyWindow energy = beginInputEnergy(caster);
            var held = AbilityInput.keyPress(caster.key, caster.ability, caster.user, null, InputMethod.CLICK, 0,
                    BufferingState.clickOnly(), caster.ability.getAbilityId());
            finishInputEnergy(caster, energy, "CLICK");
            premise(held != null && held.action instanceof HamonSendoOverdriveAbility.SendoOverdriveInstance action
                    && action.ability == caster.ability && action == LivingComponentAction.getCurEntityAction(caster.user)
                    && action.getPhase() == ActionPhase.WINDUP && action.phasesLength.getFloat(ActionPhase.WINDUP) == 30F,
                    "CLICK did not install the registered held windup");
            caster.action = (EntityActionInstance) held.action;
            premise(AbilityInput.isHeldByKey(caster.user, caster.action), "CLICK key does not own the held action");
            log("press owner=" + caster.user.getUUID() + " ability=" + caster.ability.getAbilityId()
                    + " input=CLICK posts=" + caster.posts + " energy=" + caster.inputEnergy
                    + " bloodstream=" + caster.hamon.getBloodstreamEfficiency(caster.user) + " aim=" + hit.getLocation());
        }

        private void releaseEarly(Caster caster) {
            BlockHitResult hit = aim(caster);
            premise(caster.action == LivingComponentAction.getCurEntityAction(caster.user)
                    && caster.action.getPhase() == ActionPhase.WINDUP && AbilityInput.isHeldByKey(caster.user, caster.action)
                    && caster.posts - caster.pressPosts >= 6 && caster.action.getPhaseTick() > 0F && caster.action.getPhaseTick() < 30F,
                    "release is not an actual early held CLICK release");
            caster.releaseEnergy = caster.hamon.getEnergy();
            caster.releasePhaseTick = caster.action.getPhaseTick();
            release(caster);
            premise(!AbilityInput.isHeldByKey(caster.user, caster.action) && caster.action.getPhase() == ActionPhase.PERFORM,
                    "public key release did not reach Sendo PERFORM");
            log("release owner=" + caster.user.getUUID() + " heldPosts=" + (caster.posts - caster.pressPosts)
                    + " phaseTick=" + caster.releasePhaseTick + " energy=" + caster.releaseEnergy + " aim=" + hit.getLocation());
        }

        private void release(Caster caster) {
            if (caster.user != null && caster.pressed && !caster.released) {
                EnergyWindow energy = beginInputEnergy(caster);
                caster.released = true;
                AbilityInput.keyRelease(caster.key, caster.user);
                finishInputEnergy(caster, energy, "RELEASE");
            }
        }

        private EnergyWindow beginInputEnergy(Caster caster) {
            if (closed) return null;
            premise(caster.inputEnergyWindow == null, "nested public-input energy window");
            // An input nested in a natural Post is already measured by that outer bracket.
            if (caster.postEnergy != null) return null;
            caster.inputEnergyWindow = new EnergyWindow(level.getGameTime(), caster.hamon.getEnergy(), 0F, -1, caster.emittedSources);
            return caster.inputEnergyWindow;
        }

        private void finishInputEnergy(Caster caster, EnergyWindow energy, String input) {
            if (energy == null) return;
            premise(caster.inputEnergyWindow == energy && caster.postEnergy == null, "public-input energy bracket changed");
            finishEnergyWindow(caster, energy, input);
            caster.inputEnergyWindow = null;
        }

        private void finishEnergyWindow(Caster caster, EnergyWindow energy, String window) {
            float after = caster.hamon.getEnergy();
            float debit = energy.energy - after - energy.decay;
            int emitted = caster.emittedSources - energy.emissions;
            if (caster.pressed || Math.abs(debit) >= EPS || !Float.isFinite(debit)) {
                log("energy owner=" + caster.user.getUUID() + " window=" + window + " time=" + energy.time
                        + " before=" + energy.energy + " after=" + after + " EnergyTicks=" + energy.energyTicks
                        + " passive=" + energy.decay + " actionDebit=" + debit + " emitted=" + emitted
                        + " previousPayments=" + caster.payments + " previousPaid=" + caster.paidEnergy);
            }
            premise(energy.time == level.getGameTime() && Float.isFinite(energy.energy) && Float.isFinite(after)
                    && energy.energy >= energy.decay && (emitted == 0 || emitted == 1), "invalid measured energy interval");
            boolean paid = Math.abs(debit - 900F) < EPS;
            premise(Math.abs(debit) < EPS || paid && caster.pressed && caster.payments == 0
                    && (caster.source == null || emitted == 1), "energy delta is not passive decay plus one900 payment");
            if (paid) {
                caster.payments++;
                caster.paidEnergy += debit;
            }
            premise(emitted == 0 || caster.payments == 1 && Math.abs(caster.paidEnergy - 900F) < EPS,
                    "owned emission lacks its actual900 payment");
        }

        private void requirePaidEmission(Caster caster) {
            premise(caster.emittedSources == 1 && caster.payments == 1 && Math.abs(caster.paidEnergy - 900F) < EPS
                    && caster.postEnergy == null && caster.inputEnergyWindow == null,
                    "source lacks one completed observed900 debit");
        }

        private void validate() {
            premise(samplingDone && active == null && samples.size() == casterCount && owned.size() == casterCount
                    && casters.stream().allMatch(c -> c.source != null && c.source.sample != null), "first-wave window incomplete");
            for (Caster caster : casters) requirePaidEmission(caster);
            Sample first = samples.getFirst();
            premise(samples.stream().allMatch(sample -> sample.alive && sample.before.age == 4
                    && sample.targetPosts - first.targetPosts >= 0 && sample.targetPosts - first.targetPosts < 4),
                    "distinct-source window exceeds four natural target Posts");
            for (Sample sample : samples) {
                double healthLoss = sample.before.health - sample.health;
                double pointDelta = sample.points - sample.before.points;
                if (!sample.attempts.isEmpty()) {
                    Attempt hit = sample.attempts.getFirst();
                    helper.assertTrue(sample.attempts.size() == 1 && hit.amount > 0 && !hit.event.isCanceled()
                            && sample.source.equals(hit.direct) && sample.caster.equals(hit.cause)
                            && hit.targetPosts == sample.before.targetPosts && hit.targetPosts == sample.targetPosts
                            && hit.sourcePosition != null && hit.sourcePosition.distanceTo(sample.before.origin) < EPS
                            && healthLoss > 0 && pointDelta > 0 && sample.before.gavePoints == 0 && sample.gavePoints == 1,
                            "Sendo accepted-hit control lacks actual damage, source or training");
                }
                else {
                    helper.assertTrue(Math.abs(healthLoss) < EPS && Math.abs(pointDelta) < EPS
                            && sample.before.gavePoints == 0 && sample.gavePoints == 0,
                            "Sendo excluded source changed health or training without an incoming hit");
                }
                log("result source=" + sample.source + " caster=" + sample.caster + " targetPosts=" + sample.targetPosts
                        + " attempts=" + sample.attempts.size() + " healthLoss=" + healthLoss + " pointDelta=" + pointDelta
                        + " gavePoints=" + sample.gavePoints + " birthAndCandidatePremises=valid");
            }
            helper.assertTrue(first.attempts.size() == 1, "Sendo first-source positive control did not land");
            int totalAttempts = samples.stream().mapToInt(sample -> sample.attempts.size()).sum();
            helper.assertTrue(totalAttempts == 1 && samples.stream().skip(1).allMatch(sample -> sample.attempts.isEmpty()),
                    "Sendo target exclusion oracle: distinct sources landed " + totalAttempts + " hits inside four target Posts");
        }

        private boolean inside(AABB box) {
            return box.minX >= room.minX && box.maxX <= room.maxX && box.minY >= room.minY && box.maxY <= room.maxY
                    && box.minZ >= room.minZ && box.maxZ <= room.maxZ;
        }

        private boolean ticking(AABB box) {
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            return new ChunkPos(min).equals(chunk) && new ChunkPos(max).equals(chunk)
                    && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max);
        }

        private static List<Integer> waveTicks(CompoundTag nbt) {
            return Arrays.stream(nbt.getIntArray("Waves")).boxed().toList();
        }

        private double training(Caster caster) {
            CompoundTag nbt = caster.hamon.serializeNBT(level.registryAccess());
            return nbt.getInt("StrengthPoints") + (double) nbt.getFloat("PointsIncFrac");
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 90, "watchdog expired before first-wave collection");
                requireActors();
                if (targetPosts >= 20 && casters.stream().allMatch(c -> c.posts >= 20 && !c.pressed)) {
                    for (Caster caster : casters) press(caster);
                }
                if (casters.stream().allMatch(c -> c.pressed && !c.released && c.posts - c.pressPosts >= 6)) {
                    for (Caster caster : casters) releaseEarly(caster);
                }
                if (samplingDone) {
                    validate();
                    close();
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException | Error error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Sendo overlap premise failed: casters=" + casterCount, error);
            }
        }

        private void premise(boolean valid, String message) { helper.assertTrue(valid, "Sendo premise: " + message); }
        private void log(String message) { JojoMod.LOGGER.info("SENDO-OVERLAP casters={} {}", casterCount, message); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanupStep(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
            for (Caster caster : casters) cleanupStep(() -> release(caster), failures);
            for (Source source : owned.values()) cleanupStep(() -> { if (!source.entity.isRemoved()) source.entity.discard(); }, failures);
            if (target != null) cleanupStep(target::discard, failures);
            for (Caster caster : casters) {
                if (caster.user == null) continue;
                cleanupStep(caster.user::stopUsingItem, failures);
                cleanupStep(() -> caster.user.getInventory().clearContent(), failures);
                cleanupStep(caster.user::discard, failures);
            }
            for (var entry : blocks.entrySet()) cleanupStep(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            cleanupStep(() -> {
                boolean restored = blocks.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                boolean gone = owned.entrySet().stream().allMatch(entry -> entry.getValue().entity.isRemoved()
                        && level.getEntity(entry.getKey()) == null)
                        && (target == null || target.isRemoved() && level.getEntity(target.getUUID()) == null)
                        && casters.stream().allMatch(c -> c.user == null || c.user.isRemoved() && level.getEntity(c.user.getUUID()) == null);
                log("cleanup listeners=" + listeners.size() + " sources=" + owned.keySet() + " blockCount=" + blocks.size()
                        + " exactRestore=" + restored + " entitiesGone=" + gone);
                if (!restored || !gone || !listeners.isEmpty()) throw new IllegalStateException("Sendo cleanup verification failed");
            }, failures);
            if (!failures.isEmpty()) {
                IllegalStateException error = new IllegalStateException("Sendo cleanup did not complete");
                failures.forEach(error::addSuppressed);
                throw error;
            }
        }

        private void cleanupStep(Runnable operation, List<Throwable> failures) {
            try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }

        private void closeAfterFailure(Throwable primary) {
            try { close(); }
            catch (RuntimeException | Error cleanup) {
                primary.addSuppressed(cleanup);
                JojoMod.LOGGER.error("Sendo cleanup failed; original failure retained", cleanup);
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
