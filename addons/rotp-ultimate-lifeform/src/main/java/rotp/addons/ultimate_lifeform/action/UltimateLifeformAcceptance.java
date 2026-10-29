package rotp.addons.ultimate_lifeform.action;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.AfterimageEntity;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;
import rotp.core.impl.powers.pillarman.PillarmanDivineSandstormEntity;
import rotp.core.impl.powers.pillarman.PillarmanHornEntity;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanVeinEntity;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimatePhantomEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateRabbitEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Read-only production receipts for automated Ultimate Lifeform acceptance.
 * Callers must drive abilities through the normal AbilityInput path and compare
 * monotonic receipt deltas; this class deliberately has no reset or trigger API.
 */
public final class UltimateLifeformAcceptance {
    private static final int MAX_TRACKED_USERS = 64;
    private static final String SELECTED_DONOR_TREE =
            "040_RotP-UltimateLifeform-1.0";
    private static final String NORMALIZED_DONOR_JAVA_TREE_SHA256 =
            "07080ddd0350c8539b5ad853b70c0cc3a61fa6dd1f19226b36e37f3add7869ac";
    private static final String CANONICAL_DONOR_JAR_SHA256 =
            "671f002096136da9a342cec1dc3031d2e8a6f47ccdecaa028075f7625d5b3c17";
    private static final String EXPECTED_ABILITY_ORDER_SHA256 =
            "20a988232416715d412ededf60509533e72c36e9a08680ba5ccddb1768032f15";
    private static final List<String> REQUIRED_RENDER_PROFILES =
            List.of("V0", "I0", "I1", "SR", "ISR");
    private static final List<AbilityDescriptor> ABILITIES =
            buildDescriptors();
    private static final List<String> REGISTERED_HOTBAR_ORDER =
            ABILITIES.stream()
                    .map(AbilityDescriptor::actionId)
                    .toList();
    private static final AbilityTreeIdentity ABILITY_TREE_IDENTITY =
            buildAbilityTreeIdentity();
    private static final Map<UltimateLifeformSpec, AbilityDescriptor>
            DESCRIPTORS_BY_SPEC = descriptorsBySpec();
    private static final Map<UUID, UserTrace> SERVER_TRACES =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<UUID, UserTrace> eldest) {
                    return size() > MAX_TRACKED_USERS;
                }
            };

    private UltimateLifeformAcceptance() {}

    public static List<AbilityDescriptor> abilities() {
        return ABILITIES;
    }

    public static List<String> registeredHotbarOrder() {
        return REGISTERED_HOTBAR_ORDER;
    }

    public static AbilityTreeIdentity abilityTreeIdentity() {
        return ABILITY_TREE_IDENTITY;
    }

    public static List<String> requiredRenderProfiles() {
        return REQUIRED_RENDER_PROFILES;
    }

    public static String abilityOrderSha256(List<String> actionIds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    String.join("\n", actionIds)
                            .getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable for Ultimate ability identity.",
                    error);
        }
    }

    public static AbilityDescriptor descriptor(
            UltimateLifeformSpec spec) {
        return DESCRIPTORS_BY_SPEC.get(spec);
    }

    @Nullable
    public static AbilityDescriptor descriptor(String actionId) {
        UltimateLifeformSpec spec =
                UltimateLifeformSpec.byId(actionId);
        return spec != null ? descriptor(spec) : null;
    }

    public static synchronized ServerSnapshot snapshot(
            LivingEntity user) {
        UltimateLifeformData data = data(user);
        UserTrace trace = SERVER_TRACES.get(user.getUUID());
        List<ActionObservation> actions = new ArrayList<>(
                UltimateLifeformSpec.values().length);
        for (UltimateLifeformSpec spec
                : UltimateLifeformSpec.values()) {
            actions.add(observation(
                    spec,
                    trace != null
                            ? trace.actions.get(spec)
                            : null));
        }
        return new ServerSnapshot(
                user.getUUID(),
                user.level().getGameTime(),
                List.copyOf(actions),
                state(data),
                cleanup(user, data));
    }

    public static synchronized ActionObservation observation(
            LivingEntity user, UltimateLifeformSpec spec) {
        UserTrace trace = SERVER_TRACES.get(user.getUUID());
        return observation(
                spec,
                trace != null ? trace.actions.get(spec) : null);
    }

    static synchronized void recordAccepted(
            LivingEntity user,
            UltimateLifeformData data,
            UltimateLifeformSpec spec) {
        MutableObservation receipt = receipt(user, spec);
        receipt.acceptedCount++;
        receipt.lastAcceptedGameTime = user.level().getGameTime();
        receipt.lastEnergyAfterAccept = data.getEnergy();
        receipt.lastHamonAfterAccept = data.getHamonCharge();
        receipt.lastCooldownAfterAccept =
                data.getAbilityCooldownTicks(spec.id);
    }

    static synchronized void recordExecution(
            LivingEntity user, UltimateLifeformSpec spec) {
        MutableObservation receipt = receipt(user, spec);
        receipt.executionCount++;
        receipt.lastExecutionGameTime = user.level().getGameTime();
    }

    static synchronized void recordImmediateResult(
            LivingEntity user, UltimateLifeformSpec spec) {
        recordResult(user, spec, "perform", -1);
    }

    static synchronized void recordHeldResultIfDue(
            LivingEntity user,
            UltimateLifeformSpec spec,
            int heldTicks) {
        String phase = heldResultPhase(user, spec, heldTicks);
        if (phase != null) {
            recordResult(user, spec, phase, heldTicks);
        }
    }

    static synchronized void recordRelease(
            LivingEntity user,
            UltimateLifeformSpec spec,
            int heldTicks) {
        MutableObservation receipt = receipt(user, spec);
        receipt.releaseCount++;
        receipt.lastReleaseGameTime = user.level().getGameTime();
        receipt.lastReleaseHeldTicks = heldTicks;
    }

    private static void recordResult(
            LivingEntity user,
            UltimateLifeformSpec spec,
            String phase,
            int heldTick) {
        MutableObservation receipt = receipt(user, spec);
        receipt.resultCount++;
        receipt.lastResultGameTime = user.level().getGameTime();
        receipt.lastResultPhase = phase;
        receipt.lastResultHeldTick = heldTick;
    }

    @Nullable
    private static String heldResultPhase(
            LivingEntity user,
            UltimateLifeformSpec spec,
            int ticks) {
        return switch (spec) {
            case ENHANCED_SENSES ->
                    ticks < 160 || ticks % 20 == 0 ? "scan" : null;
            case EVASION, ULTIMATE_AGILITY -> "guard_window";
            case HEAVY_PUNCH, HAMON_OVERDRIVE_BEAT ->
                    ticks == 5 ? "impact" : null;
            case BLADE_DASH_ATTACK -> "swept_dash_impact";
            case BLADE_SLASH -> ticks == 8 ? "blade_impact" : null;
            case ERRATIC_BLAZE_KING ->
                    ticks == 10 ? "ten_veins" : null;
            case BLADE_BARRAGE -> "projectile_guard_window";
            case DIVINE_SANDSTORM, ATMOSPHERIC_RIFT ->
                    ticks % 2 == 0 ? "emission" : null;
            case GUARDIAN_LASER -> "laser_ray";
            case ULTIMATE_HAMON_OVERDRIVE ->
                    ticks == 5 ? "impact" : null;
            case HAMON_SHOCK -> ticks == 9 ? "shock" : null;
            case ULTIMATE_BARRAGE -> "impact";
            case ULTIMATE_HAMON_BARRAGE -> "hamon_drain";
            case ULTIMATE_HAMON_KICK -> "swept_kick_impact";
            case SCALE_SHOT ->
                    ticks % 5 == 0 ? "projectile" : null;
            case ABSORPTION -> "absorption_pulse";
            case ABSOLUTE_IMMORTALITY ->
                    ticks % 5 == 0 ? "pulse" : null;
            case HAMON_BREATH -> "hamon_charge";
            case UNLIMITED_POWER -> "energy_charge";
            default -> null;
        };
    }

    private static MutableObservation receipt(
            LivingEntity user, UltimateLifeformSpec spec) {
        UserTrace trace = SERVER_TRACES.computeIfAbsent(
                user.getUUID(), ignored -> new UserTrace());
        return trace.actions.get(spec);
    }

    private static ActionObservation observation(
            UltimateLifeformSpec spec,
            @Nullable MutableObservation receipt) {
        if (receipt == null) {
            return new ActionObservation(
                    descriptor(spec),
                    0L,
                    0L,
                    0L,
                    0L,
                    -1L,
                    -1L,
                    -1L,
                    -1L,
                    "not_observed",
                    -1,
                    0,
                    Float.NaN,
                    -1,
                    -1);
        }
        return new ActionObservation(
                descriptor(spec),
                receipt.acceptedCount,
                receipt.executionCount,
                receipt.resultCount,
                receipt.releaseCount,
                receipt.lastAcceptedGameTime,
                receipt.lastExecutionGameTime,
                receipt.lastResultGameTime,
                receipt.lastReleaseGameTime,
                receipt.lastResultPhase,
                receipt.lastResultHeldTick,
                receipt.lastReleaseHeldTicks,
                receipt.lastEnergyAfterAccept,
                receipt.lastHamonAfterAccept,
                receipt.lastCooldownAfterAccept);
    }

    private static StateSnapshot state(
            @Nullable UltimateLifeformData data) {
        if (data == null) {
            return new StateSnapshot(
                    false,
                    0.0F,
                    0,
                    PillarmanMode.NONE,
                    UltimateLifeformData.ArmorType.NONE,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    Direction.NORTH.name(),
                    false,
                    0,
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    Map.of());
        }
        return new StateSnapshot(
                true,
                data.getEnergy(),
                data.getHamonCharge(),
                data.getPreviousMode(),
                data.getArmorType(),
                data.isStoneForm(),
                data.isFlying(),
                data.isNightVision(),
                data.isBladesVisible(),
                data.isWaterWalking(),
                data.isWallClimbing(),
                data.getWallFace().name(),
                data.isWallMoving(),
                data.getWallClimbTicks(),
                data.getHeldAbility() != null
                        ? data.getHeldAbility().id
                        : null,
                data.getHeldTicks(),
                data.getSummonedRabbitsCount(),
                data.getSummonedRabbitsLifetime(),
                data.getSummonedPhantomsCount(),
                data.getSummonedPhantomsLifetime(),
                data.getHealthDebuff(),
                data.getAbilityCooldownsSnapshot());
    }

    private static CleanupSnapshot cleanup(
            LivingEntity user,
            @Nullable UltimateLifeformData data) {
        int projectiles = 0;
        int areas = 0;
        int rabbits = 0;
        int phantoms = 0;
        int sandstorms = 0;
        int leavesGliders = 0;
        int afterimages = 0;
        int horns = 0;
        int veins = 0;
        Map<UUID, TemporaryEntitySnapshot> temporaryEntities =
                new LinkedHashMap<>();
        if (user.level() instanceof ServerLevel level) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof UltimateProjectileEntity projectile
                        && projectile.getOwner() == user) {
                    projectiles++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(projectile));
                }
                else if (entity instanceof UltimateScaleProjectileEntity scale
                        && scale.getType()
                                == UltimateLifeformAddon.SCALE_PROJECTILE.get()
                        && scale.getOwner() == user) {
                    projectiles++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(scale));
                }
                else if (entity instanceof UltimateAreaEffectEntity area
                        && area.getOwner() == user) {
                    areas++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(area));
                }
                else if (entity instanceof UltimateRabbitEntity rabbit
                        && rabbit.getOwner() == user) {
                    rabbits++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    rabbit, "rabbit"));
                }
                else if (entity instanceof UltimatePhantomEntity phantom
                        && phantom.getOwner() == user) {
                    phantoms++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    phantom, "phantom"));
                }
                else if (entity
                                instanceof PillarmanDivineSandstormEntity
                                        sandstorm
                        && sandstorm.getOwner() == user) {
                    sandstorms++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    sandstorm, "sandstorm"));
                }
                else if (entity instanceof LeavesGliderEntity glider
                        && glider.hasPassenger(user)) {
                    leavesGliders++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    glider, "leaves_glider"));
                }
                else if (entity instanceof AfterimageEntity afterimage
                        && afterimage.getOriginEntity() == user) {
                    afterimages++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    afterimage, "afterimage"));
                }
                else if (entity instanceof PillarmanHornEntity horn
                        && horn.getOwner() == user) {
                    horns++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    horn, "horn"));
                }
                else if (entity instanceof PillarmanVeinEntity vein
                        && vein.getOwner() == user) {
                    veins++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    vein, "vein"));
                }
            }
        }
        return new CleanupSnapshot(
                data != null && data.getHeldAbility() != null,
                data != null ? data.getHeldTicks() : 0,
                data != null && data.isBladesVisible(),
                data != null && data.isFlying(),
                data != null && data.isStoneForm(),
                data != null && data.isNightVision(),
                data != null
                        ? data.getArmorType()
                        : UltimateLifeformData.ArmorType.NONE,
                projectiles,
                areas,
                rabbits,
                phantoms,
                sandstorms,
                leavesGliders,
                afterimages,
                horns,
                veins,
                Map.copyOf(temporaryEntities));
    }

    private static TemporaryEntitySnapshot temporarySnapshot(
            UltimateProjectileEntity projectile) {
        String kind = projectile.isTentacle()
                ? "tentacle_projectile"
                : projectile.isGuardian()
                        ? "guardian_endpoint"
                        : "scale_projectile";
        return new TemporaryEntitySnapshot(
                projectile.getUUID(),
                kind,
                projectile.getElapsedLifeTicks(),
                projectile.getLifeTicks(),
                0,
                List.of());
    }

    private static TemporaryEntitySnapshot temporarySnapshot(
            UltimateScaleProjectileEntity projectile) {
        return new TemporaryEntitySnapshot(
                projectile.getUUID(),
                "scale_projectile",
                projectile.getElapsedLifeTicks(),
                projectile.getLifeTicks(),
                0,
                List.of());
    }

    private static TemporaryEntitySnapshot temporarySnapshot(
            UltimateAreaEffectEntity area) {
        String kind = area.isSendo()
                ? "sendo_area"
                : area.isTurquoise()
                        ? "turquoise_area"
                        : "hamon_block_charge";
        return new TemporaryEntitySnapshot(
                area.getUUID(),
                kind,
                area.getElapsedLifeTicks(),
                area.getEffectLifeTicks(),
                area.isBlockCharge() ? area.getBlockChargeAge() : 0,
                area.isSendo()
                        ? List.copyOf(area.getActiveWaveAges())
                        : List.of());
    }

    private static TemporaryEntitySnapshot temporarySnapshot(
            Entity entity, String kind) {
        return new TemporaryEntitySnapshot(
                entity.getUUID(), kind, entity.tickCount, -1, 0, List.of());
    }

    @Nullable
    private static UltimateLifeformData data(LivingEntity user) {
        return PlayerPower.getPowerData(
                        user, UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
    }

    private static List<AbilityDescriptor> buildDescriptors() {
        List<AbilityDescriptor> descriptors = new ArrayList<>();
        for (UltimateLifeformSpec spec
                : UltimateLifeformSpec.values()) {
            descriptors.add(new AbilityDescriptor(
                    spec,
                    spec.id,
                    UltimateLifeformAddon.MOD_ID + ":" + spec.id,
                    spec.held ? InputMethod.HOLD : InputMethod.CLICK,
                    spec.requiredMode,
                    spec.energyCost,
                    spec.holdEnergyCost,
                    spec.hamonCost,
                    spec.cooldown,
                    spec.chargeTicks,
                    spec.startsServerAction(),
                    spec.maxActionTicks(),
                    spec.requiresMainHandFree(),
                    spec.requiresOffHandFree(),
                    coreResult(spec),
                    serverAssertion(spec),
                    clientReceipts(spec),
                    naturalRelease(spec),
                    naturalCleanup(spec)));
        }
        List<AbilityDescriptor> result = List.copyOf(descriptors);
        Map<UltimateLifeformSpec, List<ClientReceipt>> coverage =
                new EnumMap<>(UltimateLifeformSpec.class);
        for (AbilityDescriptor descriptor : result) {
            coverage.put(
                    descriptor.spec(), descriptor.clientReceipts());
        }
        validateClientReceiptCoverage(coverage);
        return result;
    }

    static void validateClientReceiptCoverage(
            Map<UltimateLifeformSpec, List<ClientReceipt>> coverage) {
        if (coverage.size() != UltimateLifeformSpec.values().length) {
            throw new IllegalStateException(
                    "Ultimate client receipt coverage is incomplete.");
        }
        for (UltimateLifeformSpec spec : UltimateLifeformSpec.values()) {
            List<ClientReceipt> receipts = coverage.get(spec);
            if (receipts == null
                    || !receipts.contains(ClientReceipt.STATE_SYNC)
                    || receipts.stream().noneMatch(receipt ->
                            receipt != ClientReceipt.STATE_SYNC
                                    && receipt != ClientReceipt.HUD)) {
                throw new IllegalStateException(
                        "Ultimate client receipt coverage is state-only for "
                                + spec.id + ".");
            }
        }
    }

    private static Map<UltimateLifeformSpec, AbilityDescriptor>
            descriptorsBySpec() {
        Map<UltimateLifeformSpec, AbilityDescriptor> descriptors =
                new EnumMap<>(UltimateLifeformSpec.class);
        for (AbilityDescriptor descriptor : ABILITIES) {
            descriptors.put(descriptor.spec(), descriptor);
        }
        return Map.copyOf(descriptors);
    }

    private static AbilityTreeIdentity buildAbilityTreeIdentity() {
        String observedOrderSha256 = abilityOrderSha256(
                REGISTERED_HOTBAR_ORDER);
        if (ABILITIES.size() != 47
                || !EXPECTED_ABILITY_ORDER_SHA256.equals(
                        observedOrderSha256)) {
            throw new IllegalStateException(
                    "Ultimate donor ability order identity drifted.");
        }
        return new AbilityTreeIdentity(
                SELECTED_DONOR_TREE,
                NORMALIZED_DONOR_JAVA_TREE_SHA256,
                CANONICAL_DONOR_JAR_SHA256,
                ABILITIES.size(),
                observedOrderSha256);
    }

    private static List<ClientReceipt> clientReceipts(
            UltimateLifeformSpec spec) {
        List<ClientReceipt> receipts = new ArrayList<>();
        receipts.add(ClientReceipt.STATE_SYNC);
        switch (spec) {
            case HEAVY_PUNCH,
                    EVASION, ULTIMATE_AGILITY,
                    HORN_ATTACK, BLADE_SLASH, LIGHT_FLASH,
                    LIGHT_FLASH_DECOY,
                    ERRATIC_BLAZE_KING, GIANT_CARTHWHEEL_PRISON,
                    SELF_DETONATION, ULTIMATE_BARRAGE,
                    ABSORPTION, OVERWHELMING_DESPAIR,
                    NIGHT_VISION,
                    UNLIMITED_POWER, ABSOLUTE_IMMORTALITY -> receipts.add(
                            ClientReceipt.ACTIVE_ACTION_RENDER);
            case ENDERMAN_TELEPORT -> receipts.add(
                    ClientReceipt.TELEPORT_DESTINATION_RENDER);
            case ENHANCED_SENSES -> {
                receipts.add(ClientReceipt.ACTIVE_ACTION_RENDER);
                receipts.add(ClientReceipt.ENHANCED_SENSES_GLOW);
            }
            case HAMON_OVERDRIVE, HAMON_OVERDRIVE_BEAT,
                    ULTIMATE_HAMON_OVERDRIVE -> {
                receipts.add(ClientReceipt.ACTIVE_ACTION_RENDER);
                receipts.add(ClientReceipt.HAMON_AURA_PARTICLE);
                receipts.add(ClientReceipt.RAINBOW_SPARK_PARTICLE);
            }
            // the barrage's 8 sparks follow the hit entity (1.16
            // createUltimateHamonSparkParticles): core entity-offset particles,
            // which record no rainbow spark receipt
            case ULTIMATE_HAMON_BARRAGE -> {
                receipts.add(ClientReceipt.ACTIVE_ACTION_RENDER);
                receipts.add(ClientReceipt.HAMON_AURA_PARTICLE);
            }
            // 1.16 drew no rainbow spark for these: core sparks at the shocked
            // target, none at the hypnotized or infused one (wave 28)
            case HAMON_SHOCK, HAMON_ORGANISM_INFUSION,
                    HYPNOTIZE -> {
                receipts.add(ClientReceipt.ACTIVE_ACTION_RENDER);
                receipts.add(ClientReceipt.HAMON_AURA_PARTICLE);
            }
            case HAMON_PLANT_INFUSION -> {
                receipts.add(ClientReceipt.ACTIVE_ACTION_RENDER);
                receipts.add(ClientReceipt.HAMON_AURA_PARTICLE);
                receipts.add(ClientReceipt.RAINBOW_SPARK_PARTICLE);
                receipts.add(ClientReceipt.HAMON_BLOCK_AREA_ENTITY);
                receipts.add(
                        ClientReceipt.HAMON_BLOCK_CHARGE_PARTICLE_EMISSION);
            }
            case ULTIMATE_HAMON_KICK -> {
                receipts.add(ClientReceipt.ACTIVE_ACTION_RENDER);
                receipts.add(ClientReceipt.HAMON_AURA_PARTICLE);
                receipts.add(ClientReceipt.RAINBOW_SENDO_PARTICLE);
                receipts.add(ClientReceipt.SENDO_AREA_ENTITY);
                receipts.add(
                        ClientReceipt.SENDO_WAVE_PARTICLE_EMISSION);
            }
            case HAMON_BREATH -> {
                receipts.add(ClientReceipt.ACTIVE_ACTION_RENDER);
                receipts.add(ClientReceipt.HAMON_AURA_PARTICLE);
                receipts.add(ClientReceipt.HAMON_BREATH_PARTICLE);
            }
            case STONE_FORM -> receipts.add(
                    ClientReceipt.STONE_LAYER);
            case TENTACLES -> {
                receipts.add(ClientReceipt.TENTACLE_PROJECTILE);
                receipts.add(ClientReceipt.TENTACLE_CHAIN);
            }
            case BLADE_DASH_ATTACK, BLADE_BARRAGE -> receipts.add(
                    ClientReceipt.BLADES_MODEL);
            case ULTIMATE_HAMON_SENDO_OVERDRIVE -> {
                receipts.add(ClientReceipt.SENDO_AREA_ENTITY);
                receipts.add(
                        ClientReceipt.SENDO_WAVE_PARTICLE_EMISSION);
                receipts.add(ClientReceipt.HAMON_AURA_PARTICLE);
                receipts.add(ClientReceipt.RAINBOW_SENDO_PARTICLE);
            }
            // the sphere draws the core light spark (wave 28), which leaves no
            // particle batch; the hit's rainbow emitter still shows
            case ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE -> {
                receipts.add(ClientReceipt.TURQUOISE_AREA_ENTITY);
                receipts.add(ClientReceipt.HAMON_AURA_PARTICLE);
                receipts.add(ClientReceipt.RAINBOW_SPARK_PARTICLE);
            }
            case SMALL_SANDSTORM, DIVINE_SANDSTORM,
                    ATMOSPHERIC_RIFT -> receipts.add(
                    ClientReceipt.SANDSTORM_ENTITY);
            case ULTIMATE_FLIGHT_DASH -> {
                receipts.add(ClientReceipt.WINGS_MODEL);
                receipts.add(ClientReceipt.FEATHERS_MODEL);
            }
            case SCALE_SHOT, SCALE_SPLIT_SHOT -> receipts.add(
                    ClientReceipt.SCALE_PROJECTILE);
            case BUNNY_SUMMON -> receipts.add(
                    ClientReceipt.RABBIT_ENTITY);
            case PHANTOM_SUMMON -> receipts.add(
                    ClientReceipt.PHANTOM_ENTITY);
            case GUARDIAN_LASER -> {
                receipts.add(ClientReceipt.GUARDIAN_EYE_LAYER);
                receipts.add(ClientReceipt.LASER_PROJECTILE);
                receipts.add(ClientReceipt.GUARDIAN_ENDPOINT_BEAM);
            }
            case ARMOR_PROTECTION -> receipts.add(
                    ClientReceipt.NORMAL_ARMOR_LAYER);
            case ARMOR_FIRE_PROTECTION -> receipts.add(
                    ClientReceipt.FIRE_ARMOR_LAYER);
            case HAMON_LIFE_MAGNETISM -> {
                receipts.add(ClientReceipt.LEAVES_GLIDER_ENTITY);
                receipts.add(ClientReceipt.HAMON_AURA_PARTICLE);
            }
            case WALL_CLIMBING -> receipts.add(
                    ClientReceipt.WALL_CLIMB_ANIMATION);
        }
        return List.copyOf(receipts);
    }

    private static String naturalRelease(
            UltimateLifeformSpec spec) {
        if (!spec.startsServerAction()) {
            return spec.held && spec.chargeTicks > 0
                    ? "charge_complete_then_recovery"
                    : "action_recovery";
        }
        if (spec.maxActionTicks() > 0) {
            return "button_stop_max_ticks_or_action_clear";
        }
        if (spec.holdEnergyCost > 0.0F) {
            return "button_stop_energy_depletion_or_action_clear";
        }
        return "button_stop_or_action_clear";
    }

    private static String naturalCleanup(
            UltimateLifeformSpec spec) {
        return switch (spec) {
            case STONE_FORM, ARMOR_PROTECTION,
                    ARMOR_FIRE_PROTECTION, NIGHT_VISION ->
                    "next toggle or power clear removes persistent state";
            case ULTIMATE_FLIGHT_DASH ->
                    "dash changes velocity only; existing fall flight owns cleanup";
            case BLADE_DASH_ATTACK, BLADE_BARRAGE ->
                    "held release clears held state and blade layer";
            case DIVINE_SANDSTORM ->
                    "held release stops emission; waves expire after 10 ticks";
            case ATMOSPHERIC_RIFT ->
                    "held release stops emission; waves expire after 60 ticks";
            case ULTIMATE_HAMON_SENDO_OVERDRIVE,
                    ULTIMATE_HAMON_KICK ->
                    "Sendo area entity expires after its wave sequence";
            case ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE ->
                    "underwater area projectile expires after 190 ticks";
            case SCALE_SHOT, SCALE_SPLIT_SHOT,
                    GUARDIAN_LASER ->
                    "projectiles expire naturally or on impact; guardian endpoint expires when keepalive stops";
            case BUNNY_SUMMON, PHANTOM_SUMMON ->
                    "summons expire after 600 ticks or clean up on death/power clear";
            case TENTACLES ->
                    "eight owner-linked tentacles expire after 42 ticks, or 62 ticks while attached";
            case HORN_ATTACK ->
                    "owner-linked horn retracts after its 40-tick lifespan";
            case ERRATIC_BLAZE_KING,
                    GIANT_CARTHWHEEL_PRISON ->
                    "owner-linked vein entities expire after 25 ticks";
            case HAMON_LIFE_MAGNETISM ->
                    "dismount or core glider removal clears the leaves glider";
            case WALL_CLIMBING ->
                    "jump, grip loss, occupied hands, spectator state, death, descent to the ground, or power clear stops climbing";
            case LIGHT_FLASH, HAMON_OVERDRIVE_BEAT, HAMON_SHOCK,
                    ULTIMATE_HAMON_OVERDRIVE,
                    OVERWHELMING_DESPAIR, HYPNOTIZE,
                    HAMON_ORGANISM_INFUSION ->
                    "applied target effects expire by their production durations";
            case LIGHT_FLASH_DECOY ->
                    "self full-invisibility effect expires after 100 ticks";
            default ->
                    "action recovery clears transient action state; cooldown ticks down";
        };
    }

    private static String coreResult(UltimateLifeformSpec spec) {
        return switch (spec) {
            case HEAVY_PUNCH ->
                    "tick 5 target route; entity takes 12 physical and knockback 2, block route evaluates a 3 by 3 by 3 hardness/tool volume";
            case HAMON_OVERDRIVE ->
                    "aimed 4.5; 16 hamon plus ultimate hamon burn";
            case HAMON_OVERDRIVE_BEAT ->
                    "tick 5 aimed impact; 3 hamon, knockback 1.25 and burn, then the base melee attack";
            case STONE_FORM ->
                    "40-tick enable or 80-tick disable; clear blades and toggle stone passives";
            case ENHANCED_SENSES ->
                    "client-only scan radius grows with ticks to 64; refresh custom color 7693155 for 160 ticks";
            case TENTACLES ->
                    "spawn eight articulated owner-offset tentacles; 0.5 through-invulnerability hit, attach and stun, 42 plus 20 attached lifespan";
            case EVASION ->
                    "held eligible incoming-damage cancellation";
            case ULTIMATE_AGILITY ->
                    "evasion plus 30 percent counterattack";
            case HORN_ATTACK ->
                    "spawn core owner-linked Pillarman horn for 40 ticks";
            case BLADE_SLASH ->
                    "tick 8 aimed 6 physical plus 15 armor piercing; knockback 0.75";
            case BLADE_DASH_ATTACK ->
                    "15-tick velocity-scaled dash; swept multi-target 10 physical plus 15 armor piercing, one hit per target";
            case BLADE_BARRAGE ->
                    "6 physical aimed hit every tick through invulnerability and projectile guard";
            case LIGHT_FLASH ->
                    "radius 16 line-of-sight blindness 200 near or 80 far; mob stun 60";
            case LIGHT_FLASH_DECOY ->
                    "self full invisibility for 100 ticks";
            case SMALL_SANDSTORM ->
                    "core sandstorm radius 1.5 damage 6 duration 40 speed 1.5";
            case ERRATIC_BLAZE_KING ->
                    "tick 10 spawn ten core vein projectiles with 25-tick lifespan";
            case GIANT_CARTHWHEEL_PRISON ->
                    "spawn 32 core vein projectiles and fire resistance for 600 ticks";
            case SELF_DETONATION ->
                    "core custom explosion radius 3 with fire; self damage 40 and fire resistance 200";
            case DIVINE_SANDSTORM ->
                    "periodic divine sandstorm entities";
            case ATMOSPHERIC_RIFT ->
                    "periodic atmospheric rift entities and self damage";
            case ULTIMATE_HAMON_OVERDRIVE ->
                    "tick 5 hamon spread and charge-scaled impact";
            case ULTIMATE_HAMON_SENDO_OVERDRIVE ->
                    "eight expanding four-tick-spaced axis waves; per-wave damage 1.125 radius 9";
            case ULTIMATE_BARRAGE ->
                    "3 physical aimed hit every tick through invulnerability";
            case ULTIMATE_HAMON_BARRAGE ->
                    "3 hamon aimed hit and 20 hamon drain every tick; periodic burn";
            case ULTIMATE_HAMON_KICK ->
                    "ground-only velocity-scaled leap; swept multi-target physical 1 and burn amp 1 until landing, then four radius-4 Sendo waves";
            case ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE ->
                    "water-only moving sphere radius 6 speed 1.5; damage 10 wears to zero over 190 ticks with 3d knockback";
            case HAMON_SHOCK ->
                    "tick 9 hamon shock and consume all hamon";
            case HAMON_PLANT_INFUSION ->
                    "living-block target plus six neighbors receive damage-50 block charges for 802183 ticks; turtle eggs rejected";
            case HAMON_ORGANISM_INFUSION ->
                    "living entity or living-block cross receives charge strength 50 for 802183 ticks";
            case ULTIMATE_FLIGHT_DASH ->
                    "fall-flight-only dash; (existing velocity plus look) times 0.9";
            case SCALE_SHOT ->
                    "damage 7 scale projectile every 5 ticks; speed 2 plus user velocity";
            case SCALE_SPLIT_SHOT ->
                    "five damage 7 scale projectiles at speed 2 plus user velocity";
            case BUNNY_SUMMON ->
                    "five owner-linked rabbits for 600 ticks";
            case ABSORPTION ->
                    "absorb 2 every tick, pull distant target, add 70 energy; stone form uses radius 1.5";
            case HAMON_BREATH ->
                    "add 100 hamon per tick up to 6000";
            case PHANTOM_SUMMON ->
                    "three owner-linked phantoms for 600 ticks";
            case GUARDIAN_LASER ->
                    "persistent eye endpoint ray-traces 24 blocks with 1.25 entity inflation, 0.25 indirect magic through invulnerability, and a full-bright beam";
            case ARMOR_PROTECTION ->
                    "toggle normal armor and remove flight";
            case ARMOR_FIRE_PROTECTION ->
                    "toggle fire armor and cancel fire damage";
            case OVERWHELMING_DESPAIR ->
                    "radius 32 slowness weakness mining fatigue amp 2; stun passive mobs; buff owned summons";
            case NIGHT_VISION -> "synchronized night vision toggle";
            case ENDERMAN_TELEPORT ->
                    "entity/block target teleport or 24-block miss with downward floor search";
            case UNLIMITED_POWER ->
                    "add 120 energy per held tick";
            case ABSOLUTE_IMMORTALITY ->
                    "periodic heal then absorption growth";
            case HYPNOTIZE ->
                    "immediate aimed 8 tameable hypnosis for 64000 ticks";
            case HAMON_LIFE_MAGNETISM ->
                    "consume leaves and mount leaves glider";
            case WALL_CLIMBING ->
                    "click-started persistent directional wall travel with grip checks, jump/pull-up, no energy cost and no time limit; a press aimed at another wall in reach moves the climb there";
        };
    }

    private static String serverAssertion(
            UltimateLifeformSpec spec) {
        return switch (spec) {
            case HEAVY_PUNCH -> "three_by_three_by_three_block_destruction";
            case HAMON_OVERDRIVE -> "target_hamon_damage_and_burn";
            case HAMON_OVERDRIVE_BEAT -> "timed_hamon_and_base_melee_damage";
            case STONE_FORM -> "stone_toggle_and_passive_effects";
            case ENHANCED_SENSES -> "held_scan_without_server_vanilla_glow_and_custom_client_glow";
            case TENTACLES -> "eight_articulated_tentacles_with_attachment_and_stun";
            case EVASION -> "incoming_damage_canceled";
            case ULTIMATE_AGILITY -> "incoming_damage_canceled_and_counter_damage";
            case HORN_ATTACK -> "owned_horn_entity";
            case BLADE_SLASH -> "timed_blade_target_damage";
            case BLADE_DASH_ATTACK -> "dash_motion_and_two_swept_targets_damaged";
            case BLADE_BARRAGE -> "held_target_damage";
            case LIGHT_FLASH -> "target_blindness_and_stun";
            case LIGHT_FLASH_DECOY -> "user_full_invisibility";
            case SMALL_SANDSTORM -> "owned_sandstorm_entity_and_target_damage";
            case ERRATIC_BLAZE_KING -> "ten_owned_vein_entities";
            case GIANT_CARTHWHEEL_PRISON -> "thirty_two_veins_and_fire_resistance";
            case SELF_DETONATION -> "target_explosion_damage_and_fire_resistance";
            case DIVINE_SANDSTORM -> "owned_divine_sandstorm_and_target_damage";
            case ATMOSPHERIC_RIFT -> "owned_atmospheric_rift_target_and_self_damage";
            case ULTIMATE_HAMON_OVERDRIVE -> "target_scaled_impact_and_hamon_depletion";
            case ULTIMATE_HAMON_SENDO_OVERDRIVE -> "eight_axis_waves_and_per_wave_target_damage";
            case ULTIMATE_BARRAGE -> "held_target_damage";
            case ULTIMATE_HAMON_BARRAGE -> "held_target_hamon_damage_and_drain";
            case ULTIMATE_HAMON_KICK -> "two_swept_targets_burned_and_four_wave_sendo_release";
            case ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE -> "water_sphere_geometry_wearoff_and_target_damage";
            case HAMON_SHOCK -> "target_hamon_shock_and_hamon_depletion";
            case HAMON_PLANT_INFUSION -> "seven_exact_living_block_charges";
            case HAMON_ORGANISM_INFUSION -> "target_hamon_charge";
            case ULTIMATE_FLIGHT_DASH -> "fall_flight_velocity_changed";
            case SCALE_SHOT -> "owned_scale_projectile";
            case SCALE_SPLIT_SHOT -> "five_owned_scale_projectiles";
            case BUNNY_SUMMON -> "five_owned_rabbits";
            case ABSORPTION -> "target_absorption_damage_and_debuffs";
            case HAMON_BREATH -> "hamon_charge_increased";
            case PHANTOM_SUMMON -> "three_owned_phantoms";
            case GUARDIAN_LASER -> "persistent_endpoint_ray_damage_and_beam_visual";
            case ARMOR_PROTECTION -> "normal_armor_toggle_and_effect";
            case ARMOR_FIRE_PROTECTION -> "fire_armor_effect_and_damage_cancel";
            case OVERWHELMING_DESPAIR -> "target_three_debuffs";
            case NIGHT_VISION -> "night_vision_toggle_and_effect";
            case ENDERMAN_TELEPORT -> "floor_searched_destination_reached";
            case UNLIMITED_POWER -> "energy_increased";
            case ABSOLUTE_IMMORTALITY -> "health_or_absorption_increased";
            case HYPNOTIZE -> "tameable_hypnosis_effect";
            case HAMON_LIFE_MAGNETISM -> "ridden_leaves_glider";
            case WALL_CLIMBING -> "persistent_directional_wall_state_and_vertical_motion";
        };
    }

    public enum ClientReceipt {
        STATE_SYNC,
        HUD,
        ACTIVE_ACTION_RENDER,
        STONE_LAYER,
        NORMAL_ARMOR_LAYER,
        FIRE_ARMOR_LAYER,
        GUARDIAN_EYE_LAYER,
        BLADES_MODEL,
        WINGS_MODEL,
        FEATHERS_MODEL,
        WALL_CLIMB_ANIMATION,
        TELEPORT_DESTINATION_RENDER,
        SCALE_PROJECTILE,
        LASER_PROJECTILE,
        TENTACLE_PROJECTILE,
        SENDO_AREA_ENTITY,
        TURQUOISE_AREA_ENTITY,
        HAMON_BLOCK_AREA_ENTITY,
        RABBIT_ENTITY,
        PHANTOM_ENTITY,
        SANDSTORM_ENTITY,
        LEAVES_GLIDER_ENTITY,
        HAMON_AURA_PARTICLE,
        HAMON_BREATH_PARTICLE,
        RAINBOW_SENDO_PARTICLE,
        RAINBOW_SPARK_PARTICLE,
        ENHANCED_SENSES_GLOW,
        TENTACLE_CHAIN,
        GUARDIAN_ENDPOINT_BEAM,
        SENDO_WAVE_PARTICLE_EMISSION,
        TURQUOISE_SPHERE_PARTICLE_EMISSION,
        HAMON_BLOCK_CHARGE_PARTICLE_EMISSION
    }

    public record AbilityTreeIdentity(
            String selectedDonorTree,
            String normalizedDonorJavaTreeSha256,
            String canonicalDonorJarSha256,
            int abilityCount,
            String runtimeAbilityOrderSha256) {}

    public record AbilityDescriptor(
            UltimateLifeformSpec spec,
            String actionId,
            String registryId,
            InputMethod inputMethod,
            @Nullable PillarmanMode requiredMode,
            float energyCost,
            float heldEnergyCost,
            int hamonCost,
            int cooldownTicks,
            int chargeTicks,
            boolean startsServerAction,
            int maxActionTicks,
            boolean requiresMainHandFree,
            boolean requiresOffHandFree,
            String coreResult,
            String serverAssertion,
            List<ClientReceipt> clientReceipts,
            String naturalRelease,
            String naturalCleanup) {}

    public record ActionObservation(
            AbilityDescriptor descriptor,
            long acceptedCount,
            long executionCount,
            long resultCount,
            long releaseCount,
            long lastAcceptedGameTime,
            long lastExecutionGameTime,
            long lastResultGameTime,
            long lastReleaseGameTime,
            String lastResultPhase,
            int lastResultHeldTick,
            int lastReleaseHeldTicks,
            float lastEnergyAfterAccept,
            int lastHamonAfterAccept,
            int lastCooldownAfterAccept) {}

    public record StateSnapshot(
            boolean powerPresent,
            float energy,
            int hamonCharge,
            PillarmanMode previousMode,
            UltimateLifeformData.ArmorType armorType,
            boolean stoneForm,
            boolean flying,
            boolean nightVision,
            boolean bladesVisible,
            boolean waterWalking,
            boolean wallClimbing,
            String wallFace,
            boolean wallMoving,
            int wallClimbTicks,
            @Nullable String heldActionId,
            int heldTicks,
            int trackedRabbits,
            int rabbitLifetime,
            int trackedPhantoms,
            int phantomLifetime,
            int healthDebuff,
            Map<String, Integer> cooldownTicks) {}

    public record CleanupSnapshot(
            boolean heldActionPresent,
            int heldTicks,
            boolean bladesVisible,
            boolean flying,
            boolean stoneForm,
            boolean nightVision,
            UltimateLifeformData.ArmorType armorType,
            int ownedProjectiles,
            int ownedAreaEffects,
            int ownedRabbits,
            int ownedPhantoms,
            int ownedSandstorms,
            int riddenLeavesGliders,
            int ownedAfterimages,
            int ownedHorns,
            int ownedVeins,
            Map<UUID, TemporaryEntitySnapshot> temporaryEntities) {}

    public record TemporaryEntitySnapshot(
            UUID uuid,
            String kind,
            int elapsedTicks,
            int lifeTicks,
            int phaseTicks,
            List<Integer> waveAges) {}

    public record ServerSnapshot(
            UUID userUuid,
            long gameTime,
            List<ActionObservation> actions,
            StateSnapshot state,
            CleanupSnapshot cleanup) {}

    private static final class UserTrace {
        private final EnumMap<UltimateLifeformSpec, MutableObservation>
                actions = new EnumMap<>(UltimateLifeformSpec.class);

        private UserTrace() {
            for (UltimateLifeformSpec spec
                    : UltimateLifeformSpec.values()) {
                actions.put(spec, new MutableObservation());
            }
        }
    }

    private static final class MutableObservation {
        private long acceptedCount;
        private long executionCount;
        private long resultCount;
        private long releaseCount;
        private long lastAcceptedGameTime = -1L;
        private long lastExecutionGameTime = -1L;
        private long lastResultGameTime = -1L;
        private long lastReleaseGameTime = -1L;
        private String lastResultPhase = "not_observed";
        private int lastResultHeldTick = -1;
        private int lastReleaseHeldTicks;
        private float lastEnergyAfterAccept = Float.NaN;
        private int lastHamonAfterAccept = -1;
        private int lastCooldownAfterAccept = -1;
    }
}
