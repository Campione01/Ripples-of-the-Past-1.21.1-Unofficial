package rotp.addons.ultimate_lifeform.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.client.input.InputHandler;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import rotp.core.client.input.controlscheme.ClientControlScheme.Bind;
import rotp.core.client.input.controlscheme.ClientControlScheme.Hotbar;
import rotp.core.client.input.controlscheme.ClientControlScheme.HotbarSlot;
import rotp.core.client.input.controlscheme.ClientControlScheme.MoveGroup;
import rotp.core.customobjects.AfterimageEntity;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;
import rotp.core.impl.powers.pillarman.PillarmanDivineSandstormEntity;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptanceSemantics;
import rotp.addons.ultimate_lifeform.client.UltimateParticle.ParticleBatchProof;
import rotp.addons.ultimate_lifeform.client.UltimateProjectileRenderer.GeometrySubmissionProof;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.ParticleBatchEvidence;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.SubmissionContext;
import rotp.addons.ultimate_lifeform.client.UltimateLifeformDownstreamSubmissions.RegisteredSubmission;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimatePhantomEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence.EmissionBatch;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateRabbitEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Read-only client receipts for synchronization, controls, and rendering.
 * Counts are monotonic for the current game process and cannot be reset here.
 */
@OnlyIn(Dist.CLIENT)
public final class UltimateLifeformClientAcceptance {
    private static final int MAX_TRACKED_PLAYERS = 64;
    private static final int MAX_TRACKED_TEMPORARY_AGES = 4096;
    private static final UUID GLOBAL = new UUID(0L, 0L);
    public static final Set<String> CLIENT_TRACKED_TEMPORARY_KINDS = Set.of(
            "scale_projectile",
            "guardian_endpoint",
            "tentacle_projectile",
            "sendo_area",
            "turquoise_area",
            "hamon_block_charge",
            "rabbit",
            "phantom",
            "sandstorm",
            "leaves_glider",
            "afterimage");
    public static final Set<String> SERVER_OWNED_TEMPORARY_KINDS = Set.of(
            "scale_projectile",
            "guardian_endpoint",
            "tentacle_projectile",
            "sendo_area",
            "turquoise_area",
            "hamon_block_charge",
            "rabbit",
            "phantom",
            "sandstorm",
            "leaves_glider",
            "afterimage",
            "horn",
            "vein");
    private static final Map<UUID, VisualTrace> VISUAL_TRACES =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<UUID, VisualTrace> eldest) {
                    return size() > MAX_TRACKED_PLAYERS;
                }
            };
    private static final Map<UUID, ContinuityAge> TEMPORARY_AGES =
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<UUID, ContinuityAge> eldest) {
                    return size() > MAX_TRACKED_TEMPORARY_AGES;
                }
            };
    private UltimateLifeformClientAcceptance() {}

    @Nullable
    public static String profileBindingMismatch(
            String requestedProfile,
            String observedProfile,
            List<String> allowedProfiles) {
        return UltimateLifeformAcceptanceSemantics
                .profileBindingMismatch(
                        requestedProfile,
                        observedProfile,
                        allowedProfiles);
    }

    public static Map<UUID, String> identityKinds(
            Map<UUID, TemporaryEntitySnapshot> entities) {
        Map<UUID, String> identities = new LinkedHashMap<>();
        entities.forEach((uuid, entity) ->
                identities.put(uuid, entity.kind()));
        return Map.copyOf(identities);
    }

    public static boolean exactIdentitySetMatches(
            Map<UUID, String> expected,
            Map<UUID, String> current) {
        return UltimateLifeformAcceptanceSemantics
                .exactIdentitySetMatches(expected, current);
    }

    @Nullable
    public static String temporaryBridgeMismatch(
            Map<UUID, TemporaryEntitySnapshot> serverEntities,
            Map<UUID, TemporaryEntitySnapshot> clientEntities,
            Map<String, Integer> expectedCardinality,
            int minimumClientAge,
            int maximumAgeDrift,
            boolean requireSendoCadence) {
        return UltimateLifeformAcceptanceSemantics
                .temporaryBridgeMismatch(
                        semanticTemporaryEntities(serverEntities),
                        semanticTemporaryEntities(clientEntities),
                        expectedCardinality,
                        minimumClientAge,
                        maximumAgeDrift,
                        requireSendoCadence);
    }

    @Nullable
    public static String temporaryContinuityMismatch(
            Map<UUID, TemporaryEntitySnapshot> first,
            Map<UUID, TemporaryEntitySnapshot> current) {
        return UltimateLifeformAcceptanceSemantics
                .temporaryContinuityMismatch(
                        semanticTemporaryEntities(first),
                        semanticTemporaryEntities(current));
    }

    private static Map<UUID,
            UltimateLifeformAcceptanceSemantics.TemporaryIdentity>
    semanticTemporaryEntities(
            Map<UUID, TemporaryEntitySnapshot> entities) {
        Map<UUID, UltimateLifeformAcceptanceSemantics.TemporaryIdentity>
                semantic = new LinkedHashMap<>();
        entities.forEach((uuid, entity) -> semantic.put(
                uuid,
                new UltimateLifeformAcceptanceSemantics.TemporaryIdentity(
                        entity.uuid(),
                        entity.kind(),
                        entity.elapsedTicks(),
                        entity.lifeTicks(),
                        entity.phaseTicks(),
                        entity.waveAges())));
        return Map.copyOf(semantic);
    }

    @Nullable
    public static synchronized String spatialReceiptMismatch(
            VisualReceiptSnapshot receipt,
            ClientReceipt receiptType,
            SpatialReceiptContract contract,
            ClientLevel level,
            Entity entity,
            UUID expectedOwnerUuid,
            long expectedSceneEpoch) {
        if (receipt == null) {
            return "spatial_downstream_submission_missing";
        }
        SubmissionIdentitySnapshot identity = receipt.submissionIdentity();
        SubmissionContext expectedContext = identity != null
                && identity.sceneEpoch() == expectedSceneEpoch
                ? UltimateLifeformDownstreamSubmissions.validationContext(
                        level,
                        entity,
                        expectedOwnerUuid,
                        receiptType.name(),
                        identity.resource(),
                        identity.evidenceDigest(),
                        identity.sessionEpoch(),
                        identity.sceneEpoch(),
                        identity.sceneId(),
                        receipt.lastClientGameTime(),
                        receipt.lastFrameNanos())
                : null;
        String ticketMismatch =
                UltimateLifeformDownstreamSubmissions.consumeReceipt(
                        receipt.submissionTicketIdentity(),
                        receipt.submissionTicketId(),
                        expectedContext,
                        receipt);
        if (ticketMismatch != null) {
            return ticketMismatch;
        }
        if (!receipt.downstreamSubmission()) {
            return "spatial_downstream_submission_missing";
        }
        return UltimateLifeformAcceptanceSemantics
                .spatialReceiptMismatch(
                        new UltimateLifeformAcceptanceSemantics.SpatialReceipt(
                                receipt.count(),
                                receipt.lastBatchCount(),
                                receipt.submissionSequence(),
                                receipt.submissionKind(),
                                receipt.downstreamSubmission(),
                                receipt.vertexCount(),
                                receipt.x(),
                                receipt.y(),
                                receipt.z(),
                                receipt.minX(),
                                receipt.minY(),
                                receipt.minZ(),
                                receipt.maxX(),
                                receipt.maxY(),
                                receipt.maxZ(),
                                receipt.submittedMinX(),
                                receipt.submittedMinY(),
                                receipt.submittedMinZ(),
                                receipt.submittedMaxX(),
                                receipt.submittedMaxY(),
                                receipt.submittedMaxZ(),
                                receipt.entityX(),
                                receipt.entityY(),
                                receipt.entityZ(),
                                receipt.startX(),
                                receipt.startY(),
                                receipt.startZ(),
                                receipt.endX(),
                                receipt.endY(),
                                receipt.endZ()),
                        new UltimateLifeformAcceptanceSemantics.SpatialContract(
                                contract.submissionKind(),
                                contract.exactVertexCount(),
                                contract.minimumVertexCount(),
                                contract.maximumVertexCount(),
                                contract.minimumWorldSpan(),
                                contract.maximumWorldSpan(),
                                contract.maximumSubmittedSpan(),
                                contract.maximumEntityRelativeDistance(),
                                contract.anchorTolerance(),
                                UltimateLifeformAcceptanceSemantics
                                        .EndpointAnchor.valueOf(
                                                contract.endpointAnchor()
                                                        .name())));
    }

    public static synchronized ClientSnapshot snapshot(
            LivingEntity subject) {
        UltimateLifeformData data = data(subject);
        long syncReceipts = data != null
                ? data.getReceivedSyncCount()
                : 0L;
        return new ClientSnapshot(
                subject.getUUID(),
                syncReceipts,
                data != null && data.wasLastSyncSentToTracking(),
                state(data),
                visualSnapshot(subject.getUUID(), syncReceipts),
                visualSnapshot(GLOBAL, 0L),
                liveEntities(),
                activeControls());
    }

    public static synchronized VisualSnapshot visuals(
            LivingEntity subject) {
        UltimateLifeformData data = data(subject);
        return visualSnapshot(
                subject.getUUID(),
                data != null ? data.getReceivedSyncCount() : 0L);
    }

    public static synchronized VisualSnapshot globalVisuals() {
        return visualSnapshot(GLOBAL, 0L);
    }

    public static ActiveControlsSnapshot activeControls() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return ActiveControlsSnapshot.unavailable(
                    "no_client_player");
        }
        PlayerPower power = PlayerPower.get(minecraft.player);
        if (power == null
                || power.getPowerType()
                        != UltimateLifeformAddon
                                .ULTIMATE_LIFEFORM.get()) {
            return ActiveControlsSnapshot.unavailable(
                    "ultimate_lifeform_not_active");
        }
        ClientControlScheme controls = InputHandler.getInstance()
                .getActiveControlScheme();
        if (controls == null) {
            return ActiveControlsSnapshot.unavailable(
                    "no_active_control_scheme");
        }
        MoveGroup group = controls.getCurGroup();
        List<DirectBindSnapshot> directBinds = new ArrayList<>();
        for (Bind bind : group.binds) {
            directBinds.add(new DirectBindSnapshot(
                    bind.ability.abilityName(),
                    bind.inputMethod));
        }
        List<HotbarSnapshot> hotbars = new ArrayList<>();
        for (int hotbarIndex = 0;
                hotbarIndex < group.hotbars.size();
                hotbarIndex++) {
            Hotbar hotbar = group.hotbars.get(hotbarIndex);
            List<HotbarSlotSnapshot> slots = new ArrayList<>();
            for (HotbarSlot slot : hotbar.slots) {
                List<AbilityInputSnapshot> inputs = new ArrayList<>();
                for (InputMethod inputMethod : InputMethod.values()) {
                    AbilityControlsEntry entry =
                            slot.getBaseBind(inputMethod);
                    if (entry != null) {
                        inputs.add(new AbilityInputSnapshot(
                                entry.abilityName(), inputMethod));
                    }
                }
                slots.add(new HotbarSlotSnapshot(
                        slot.index, List.copyOf(inputs)));
            }
            hotbars.add(new HotbarSnapshot(
                    hotbarIndex,
                    hotbar.slotIndex,
                    List.copyOf(slots)));
        }
        return new ActiveControlsSnapshot(
                true,
                "available",
                group.internalName,
                controls.moveGroups.size(),
                List.copyOf(directBinds),
                List.copyOf(hotbars));
    }

    public static synchronized LiveEntitySnapshot liveEntities() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return new LiveEntitySnapshot(
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());
        }
        int scaleProjectiles = 0;
        int laserProjectiles = 0;
        int tentacleProjectiles = 0;
        int sendoAreas = 0;
        int turquoiseAreas = 0;
        int hamonBlockAreas = 0;
        int rabbits = 0;
        int phantoms = 0;
        int sandstorms = 0;
        int leavesGliders = 0;
        int afterimages = 0;
        Map<UUID, TemporaryEntitySnapshot> temporaryEntities =
                new LinkedHashMap<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof UltimateProjectileEntity projectile) {
                if (entity.getType()
                        == UltimateLifeformAddon
                                .SCALE_PROJECTILE.get()) {
                    scaleProjectiles++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    projectile, "scale_projectile"));
                }
                else if (entity.getType()
                        == UltimateLifeformAddon.LASER_END.get()) {
                    laserProjectiles++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    projectile, "guardian_endpoint"));
                }
                else if (entity.getType()
                        == UltimateLifeformAddon
                                .SQUID_TENTACLE.get()) {
                    tentacleProjectiles++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    projectile, "tentacle_projectile"));
                }
            }
            else if (entity instanceof UltimateScaleProjectileEntity scale
                    && scale.getType()
                            == UltimateLifeformAddon.SCALE_PROJECTILE.get()) {
                scaleProjectiles++;
                temporaryEntities.put(
                        entity.getUUID(), temporarySnapshot(
                                scale, "scale_projectile"));
            }
            else if (entity instanceof UltimateAreaEffectEntity area) {
                if (entity.getType()
                        == UltimateLifeformAddon
                                .ULTIMATE_SENDO_HAMON_OVERDRIVE
                                .get()) {
                    sendoAreas++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    area, "sendo_area"));
                }
                else if (entity.getType()
                        == UltimateLifeformAddon
                                .ULTIMATE_TURQUOISE_BLUE_OVERDRIVE
                                .get()) {
                    turquoiseAreas++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    area, "turquoise_area"));
                }
                else if (entity.getType()
                        == UltimateLifeformAddon
                                .HAMON_BLOCK_CHARGE.get()) {
                    hamonBlockAreas++;
                    temporaryEntities.put(
                            entity.getUUID(), temporarySnapshot(
                                    area, "hamon_block_charge"));
                }
            }
            else if (entity instanceof UltimateRabbitEntity) {
                rabbits++;
                temporaryEntities.put(
                        entity.getUUID(), temporarySnapshot(entity, "rabbit"));
            }
            else if (entity instanceof UltimatePhantomEntity) {
                phantoms++;
                temporaryEntities.put(
                        entity.getUUID(), temporarySnapshot(entity, "phantom"));
            }
            else if (entity
                    instanceof PillarmanDivineSandstormEntity) {
                sandstorms++;
                temporaryEntities.put(
                        entity.getUUID(), temporarySnapshot(
                                entity, "sandstorm"));
            }
            else if (entity instanceof LeavesGliderEntity) {
                leavesGliders++;
                temporaryEntities.put(
                        entity.getUUID(), temporarySnapshot(
                                entity, "leaves_glider"));
            }
            else if (entity instanceof AfterimageEntity) {
                afterimages++;
                temporaryEntities.put(
                        entity.getUUID(), temporarySnapshot(
                                entity, "afterimage"));
            }
        }
        return new LiveEntitySnapshot(
                scaleProjectiles,
                laserProjectiles,
                tentacleProjectiles,
                sendoAreas,
                turquoiseAreas,
                hamonBlockAreas,
                rabbits,
                phantoms,
                sandstorms,
                leavesGliders,
                afterimages,
                Map.copyOf(temporaryEntities));
    }

    private static TemporaryEntitySnapshot temporarySnapshot(
            UltimateProjectileEntity projectile, String kind) {
        return new TemporaryEntitySnapshot(
                projectile.getUUID(),
                kind,
                projectile.getElapsedLifeTicks(),
                projectile.getLifeTicks(),
                0,
                List.of());
    }

    private static TemporaryEntitySnapshot temporarySnapshot(
            UltimateScaleProjectileEntity projectile, String kind) {
        return new TemporaryEntitySnapshot(
                projectile.getUUID(),
                kind,
                projectile.getElapsedLifeTicks(),
                projectile.getLifeTicks(),
                0,
                List.of());
    }

    private static TemporaryEntitySnapshot temporarySnapshot(
            UltimateAreaEffectEntity area, String kind) {
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
                entity.getUUID(),
                kind,
                continuityAge(entity),
                -1,
                0,
                List.of());
    }

    private static int continuityAge(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level != null
                ? minecraft.level.getGameTime() : 0L;
        ContinuityAge previous = TEMPORARY_AGES.get(entity.getUUID());
        int logicalAge = Math.max(entity.tickCount, 0);
        if (previous != null && gameTime >= previous.gameTime) {
            long advanced = Math.min(
                    Integer.MAX_VALUE,
                    (long) previous.logicalAge
                            + gameTime - previous.gameTime);
            logicalAge = Math.max(logicalAge, (int) advanced);
        }
        TEMPORARY_AGES.put(
                entity.getUUID(), new ContinuityAge(gameTime, logicalAge));
        return logicalAge;
    }

    static synchronized void recordPlayerVisual(
            LivingEntity player, ClientReceipt receipt) {
        recordPlayerVisual(
                player,
                receipt,
                receipt.name().toLowerCase(java.util.Locale.ROOT));
    }

    static synchronized void recordPlayerVisual(
            LivingEntity player,
            ClientReceipt receipt,
            String evidenceToken) {
        record(
                player.getUUID(),
                receipt,
                1,
                evidenceToken,
                player.getId(),
                player.getX(),
                player.getY(0.5D),
                player.getZ());
    }

    static synchronized void recordGlobalVisual(
            ClientReceipt receipt) {
        record(
                GLOBAL,
                receipt,
                1,
                receipt.name().toLowerCase(java.util.Locale.ROOT),
                -1,
                Double.NaN,
                Double.NaN,
                Double.NaN);
    }

    static synchronized void recordGlobalVisual(
            ClientReceipt receipt, int batchCount) {
        record(
                GLOBAL,
                receipt,
                batchCount,
                receipt.name().toLowerCase(java.util.Locale.ROOT),
                -1,
                Double.NaN,
                Double.NaN,
                Double.NaN);
    }

    static synchronized void recordGlobalVisual(
            Entity entity,
            ClientReceipt receipt,
            String evidenceToken) {
        SpatialBounds bounds = visualBounds(entity, receipt);
        record(
                GLOBAL,
                receipt,
                1,
                evidenceToken,
                entity.getId(),
                entity.getX(),
                entity.getY(0.5D),
                entity.getZ(),
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ());
    }

    static synchronized void recordGlobalGeometrySubmission(
            GeometrySubmissionProof proof) {
        if (proof == null || !proof.claim()) {
            return;
        }
        boolean tentacle = "tentacle_mesh".equals(
                proof.submissionKind())
                && proof.primaryReceipt()
                        == ClientReceipt.TENTACLE_PROJECTILE
                && proof.spatialReceipt() == ClientReceipt.TENTACLE_CHAIN;
        boolean guardian = "guardian_mesh".equals(
                proof.submissionKind())
                && proof.primaryReceipt() == ClientReceipt.LASER_PROJECTILE
                && proof.spatialReceipt()
                        == ClientReceipt.GUARDIAN_ENDPOINT_BEAM;
        if (!tentacle && !guardian) {
            UltimateLifeformDownstreamSubmissions.discardRegistration(
                    proof.registration(proof.primaryReceipt()));
            UltimateLifeformDownstreamSubmissions.discardRegistration(
                    proof.registration(proof.spatialReceipt()));
            return;
        }
        recordGlobalGeometrySubmission(proof, proof.primaryReceipt());
        recordGlobalGeometrySubmission(proof, proof.spatialReceipt());
    }

    private static void recordGlobalGeometrySubmission(
            GeometrySubmissionProof proof, ClientReceipt receipt) {
        RegisteredSubmission registration = proof.registration(receipt);
        if (registration == null) {
            return;
        }
        Entity entity = proof.entity();
        Vec3 start = proof.start();
        Vec3 end = proof.end();
        SpatialBounds bounds = SpatialBounds.between(start, end);
        record(
                GLOBAL,
                receipt,
                1,
                proof.evidenceToken(),
                entity.getId(),
                start.x,
                start.y,
                start.z,
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ(),
                proof.submissionKind(),
                proof.vertexCount(),
                proof.submittedMinX(),
                proof.submittedMinY(),
                proof.submittedMinZ(),
                proof.submittedMaxX(),
                proof.submittedMaxY(),
                proof.submittedMaxZ(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                start.x,
                start.y,
                start.z,
                end.x,
                end.y,
                end.z,
                registration);
    }

    static synchronized void recordGlobalParticleEmission(
            ParticleBatchProof proof) {
        if (proof == null || !proof.claim()) {
            return;
        }
        RegisteredSubmission registration = proof.registration();
        if (registration == null) {
            return;
        }
        UltimateAreaEffectEntity entity = proof.entity();
        ClientReceipt receipt = proof.receipt();
        EmissionBatch emission = proof.batch();
        record(
                GLOBAL,
                receipt,
                emission.count(),
                emission.evidenceToken(),
                entity.getId(),
                emission.lastX(),
                emission.lastY(),
                emission.lastZ(),
                emission.minX(),
                emission.minY(),
                emission.minZ(),
                emission.maxX(),
                emission.maxY(),
                emission.maxZ(),
                "particle_batch",
                emission.count(),
                emission.minX(),
                emission.minY(),
                emission.minZ(),
                emission.maxX(),
                emission.maxY(),
                emission.maxZ(),
                entity.getX(),
                entity.getY(0.5D),
                entity.getZ(),
                emission.minX(),
                emission.minY(),
                emission.minZ(),
                emission.maxX(),
                emission.maxY(),
                emission.maxZ(),
                registration);
    }

    static synchronized void recordGlobalVisual(
            ClientReceipt receipt,
            String evidenceToken,
            double x,
            double y,
            double z) {
        record(
                GLOBAL,
                receipt,
                1,
                evidenceToken,
                -1,
                x,
                y,
                z);
    }

    private static void record(
            UUID owner,
            ClientReceipt receipt,
            int batchCount,
            String evidenceToken,
            int entityId,
            double x,
            double y,
            double z) {
        record(
                owner,
                receipt,
                batchCount,
                evidenceToken,
                entityId,
                x,
                y,
                z,
                x,
                y,
                z,
                x,
                y,
                z);
    }

    private static void record(
            UUID owner,
            ClientReceipt receipt,
            int batchCount,
            String evidenceToken,
            int entityId,
            double x,
            double y,
            double z,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ) {
        record(
                owner,
                receipt,
                batchCount,
                evidenceToken,
                entityId,
                x,
                y,
                z,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                "none",
                0,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                x,
                y,
                z,
                x,
                y,
                z,
                x,
                y,
                z,
                null);
    }

    private static void record(
            UUID owner,
            ClientReceipt receipt,
            int batchCount,
            String evidenceToken,
            int entityId,
            double x,
            double y,
            double z,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            String submissionKind,
            int vertexCount,
            double submittedMinX,
            double submittedMinY,
            double submittedMinZ,
            double submittedMaxX,
            double submittedMaxY,
            double submittedMaxZ,
            double entityX,
            double entityY,
            double entityZ,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            @Nullable RegisteredSubmission registration) {
        if (batchCount <= 0) {
            UltimateLifeformDownstreamSubmissions.discardRegistration(
                    registration);
            return;
        }
        VisualTrace trace = VISUAL_TRACES.computeIfAbsent(
                owner, ignored -> new VisualTrace());
        MutableVisualReceipt counter = trace.receipts.get(receipt);
        SubmissionContext context = registration != null
                ? registration.context()
                : null;
        Minecraft minecraft = Minecraft.getInstance();
        long frameNanos = context != null
                ? context.frameNanos()
                : Util.getNanos();
        long clientGameTime = context != null
                ? context.gameTime()
                : minecraft.level != null
                        ? minecraft.level.getGameTime()
                        : -1L;
        SubmissionIdentitySnapshot identity = context != null
                ? submissionIdentity(context, registration.evidence())
                : null;
        VisualReceiptSnapshot candidate = new VisualReceiptSnapshot(
                counter.count + batchCount,
                batchCount,
                frameNanos,
                clientGameTime,
                evidenceToken,
                entityId,
                counter.submissionSequence + 1L,
                submissionKind,
                registration != null,
                registration != null
                        ? registration.ticket().identity()
                        : null,
                registration != null ? registration.ticket().id() : 0L,
                identity,
                vertexCount,
                x,
                y,
                z,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                submittedMinX,
                submittedMinY,
                submittedMinZ,
                submittedMaxX,
                submittedMaxY,
                submittedMaxZ,
                entityX,
                entityY,
                entityZ,
                startX,
                startY,
                startZ,
                endX,
                endY,
                endZ);
        if (registration != null
                && !UltimateLifeformDownstreamSubmissions.bindReceipt(
                        registration, candidate)) {
            return;
        }
        UltimateLifeformDownstreamSubmissions.discardReceiptTicket(
                counter.submissionTicketIdentity,
                counter.submissionTicketId);
        apply(counter, candidate);
    }

    private static SubmissionIdentitySnapshot submissionIdentity(
            SubmissionContext context,
            DownstreamTicketAuthority.SubmissionEvidence evidence) {
        int orderedPointCount = evidence instanceof ParticleBatchEvidence batch
                ? batch.points().size()
                : 0;
        return new SubmissionIdentitySnapshot(
                context.ownerUuid(),
                context.entityUuid(),
                context.entityRuntimeId(),
                System.identityHashCode(context.entityIdentity()),
                System.identityHashCode(context.levelIdentity()),
                context.dimension(),
                context.sessionEpoch(),
                context.sceneEpoch(),
                context.sceneId(),
                context.receiptType(),
                context.resource(),
                orderedPointCount,
                context.evidenceDigest());
    }

    private static void apply(
            MutableVisualReceipt target,
            VisualReceiptSnapshot source) {
        target.count = source.count();
        target.submissionSequence = source.submissionSequence();
        target.lastBatchCount = source.lastBatchCount();
        target.lastFrameNanos = source.lastFrameNanos();
        target.lastClientGameTime = source.lastClientGameTime();
        target.evidenceToken = source.evidenceToken();
        target.entityId = source.entityId();
        target.submissionKind = source.submissionKind();
        target.downstreamSubmission = source.downstreamSubmission();
        target.submissionTicketIdentity = source.submissionTicketIdentity();
        target.submissionTicketId = source.submissionTicketId();
        target.submissionIdentity = source.submissionIdentity();
        target.vertexCount = source.vertexCount();
        target.x = source.x();
        target.y = source.y();
        target.z = source.z();
        target.minX = source.minX();
        target.minY = source.minY();
        target.minZ = source.minZ();
        target.maxX = source.maxX();
        target.maxY = source.maxY();
        target.maxZ = source.maxZ();
        target.submittedMinX = source.submittedMinX();
        target.submittedMinY = source.submittedMinY();
        target.submittedMinZ = source.submittedMinZ();
        target.submittedMaxX = source.submittedMaxX();
        target.submittedMaxY = source.submittedMaxY();
        target.submittedMaxZ = source.submittedMaxZ();
        target.entityX = source.entityX();
        target.entityY = source.entityY();
        target.entityZ = source.entityZ();
        target.startX = source.startX();
        target.startY = source.startY();
        target.startZ = source.startZ();
        target.endX = source.endX();
        target.endY = source.endY();
        target.endZ = source.endZ();
    }

    private static SpatialBounds visualBounds(
            Entity entity, ClientReceipt receipt) {
        if (entity instanceof UltimateProjectileEntity projectile) {
            if (receipt == ClientReceipt.TENTACLE_CHAIN
                    && projectile.isTentacle()) {
                return SpatialBounds.between(
                        projectile.getTentacleOrigin(1.0F),
                        projectile.getTentacleEnd(1.0F));
            }
            if (receipt == ClientReceipt.GUARDIAN_ENDPOINT_BEAM
                    && projectile.isGuardian()) {
                return SpatialBounds.between(
                        projectile.position(), projectile.getBeamEnd());
            }
        }
        return SpatialBounds.from(entity.getBoundingBox());
    }

    private static VisualSnapshot visualSnapshot(
            UUID owner, long stateSyncCount) {
        VisualTrace trace = VISUAL_TRACES.get(owner);
        Map<ClientReceipt, VisualReceiptSnapshot> receipts =
                new EnumMap<>(ClientReceipt.class);
        for (ClientReceipt kind : ClientReceipt.values()) {
            MutableVisualReceipt receipt = trace != null
                    ? trace.receipts.get(kind)
                    : null;
            receipts.put(kind, kind == ClientReceipt.STATE_SYNC
                    ? emptyVisualReceipt(
                            stateSyncCount,
                            stateSyncCount > 0L ? 1 : 0,
                            "state_sync")
                     : receipt != null
                     ? visualReceipt(receipt)
                     : emptyVisualReceipt(0L, 0, "not_observed"));
        }
        return new VisualSnapshot(owner, Map.copyOf(receipts));
    }

    private static VisualReceiptSnapshot visualReceipt(
            MutableVisualReceipt receipt) {
        return new VisualReceiptSnapshot(
                receipt.count,
                receipt.lastBatchCount,
                receipt.lastFrameNanos,
                receipt.lastClientGameTime,
                receipt.evidenceToken,
                receipt.entityId,
                receipt.submissionSequence,
                receipt.submissionKind,
                receipt.downstreamSubmission,
                receipt.submissionTicketIdentity,
                receipt.submissionTicketId,
                receipt.submissionIdentity,
                receipt.vertexCount,
                receipt.x,
                receipt.y,
                receipt.z,
                receipt.minX,
                receipt.minY,
                receipt.minZ,
                receipt.maxX,
                receipt.maxY,
                receipt.maxZ,
                receipt.submittedMinX,
                receipt.submittedMinY,
                receipt.submittedMinZ,
                receipt.submittedMaxX,
                receipt.submittedMaxY,
                receipt.submittedMaxZ,
                receipt.entityX,
                receipt.entityY,
                receipt.entityZ,
                receipt.startX,
                receipt.startY,
                receipt.startZ,
                receipt.endX,
                receipt.endY,
                receipt.endZ);
    }

    private static VisualReceiptSnapshot emptyVisualReceipt(
            long count, int batchCount, String evidenceToken) {
        return new VisualReceiptSnapshot(
                count,
                batchCount,
                -1L,
                -1L,
                evidenceToken,
                -1,
                0L,
                "none",
                false,
                null,
                0L,
                null,
                0,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN);
    }

    private static ClientStateSnapshot state(
            @Nullable UltimateLifeformData data) {
        if (data == null) {
            return new ClientStateSnapshot(
                    false,
                    0.0F,
                    0,
                    "NONE",
                    "NONE",
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "NORTH",
                    false,
                    0,
                    null,
                    0,
                    0,
                    0,
                    Map.of());
        }
        return new ClientStateSnapshot(
                true,
                data.getEnergy(),
                data.getHamonCharge(),
                data.getPreviousMode().name(),
                data.getArmorType().name(),
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
                data.getSummonedPhantomsCount(),
                data.getAbilityCooldownsSnapshot());
    }

    @Nullable
    private static UltimateLifeformData data(LivingEntity entity) {
        return PlayerPower.getPowerData(
                        entity,
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
    }

    public record ClientSnapshot(
            UUID subjectUuid,
            long stateSyncReceiptCount,
            boolean lastSyncSentToTracking,
            ClientStateSnapshot state,
            VisualSnapshot subjectVisuals,
            VisualSnapshot globalVisuals,
            LiveEntitySnapshot liveEntities,
            ActiveControlsSnapshot activeControls) {}

    public record ClientStateSnapshot(
            boolean powerPresent,
            float energy,
            int hamonCharge,
            String previousMode,
            String armorType,
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
            int trackedPhantoms,
            Map<String, Integer> cooldownTicks) {}

    public record VisualReceiptSnapshot(
            long count,
            int lastBatchCount,
            long lastFrameNanos,
            long lastClientGameTime,
            String evidenceToken,
            int entityId,
            long submissionSequence,
            String submissionKind,
            boolean downstreamSubmission,
            @Nullable Object submissionTicketIdentity,
            long submissionTicketId,
            @Nullable SubmissionIdentitySnapshot submissionIdentity,
            int vertexCount,
            double x,
            double y,
            double z,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            double submittedMinX,
            double submittedMinY,
            double submittedMinZ,
            double submittedMaxX,
            double submittedMaxY,
            double submittedMaxZ,
            double entityX,
            double entityY,
            double entityZ,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ) {
        public double worldSpan() {
            if (!Double.isFinite(minX)
                    || !Double.isFinite(minY)
                    || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX)
                    || !Double.isFinite(maxY)
                    || !Double.isFinite(maxZ)) {
                return Double.NaN;
            }
            return new Vec3(
                    maxX - minX,
                    maxY - minY,
                    maxZ - minZ).length();
        }

        public double submittedSpan() {
            if (!Double.isFinite(submittedMinX)
                    || !Double.isFinite(submittedMinY)
                    || !Double.isFinite(submittedMinZ)
                    || !Double.isFinite(submittedMaxX)
                    || !Double.isFinite(submittedMaxY)
                    || !Double.isFinite(submittedMaxZ)) {
                return Double.NaN;
            }
            return new Vec3(
                    submittedMaxX - submittedMinX,
                    submittedMaxY - submittedMinY,
                    submittedMaxZ - submittedMinZ).length();
        }
    }

    public record SubmissionIdentitySnapshot(
            UUID ownerUuid,
            UUID entityUuid,
            int entityRuntimeId,
            int entityIdentityHash,
            int clientLevelIdentityHash,
            String dimension,
            long sessionEpoch,
            long sceneEpoch,
            String sceneId,
            String receiptType,
            String resource,
            int orderedPointCount,
            String evidenceDigest) {}

    public enum EndpointAnchor {
        NONE,
        START,
        END
    }

    public record SpatialReceiptContract(
            String submissionKind,
            int exactVertexCount,
            int minimumVertexCount,
            int maximumVertexCount,
            double minimumWorldSpan,
            double maximumWorldSpan,
            double maximumSubmittedSpan,
            double maximumEntityRelativeDistance,
            double anchorTolerance,
            EndpointAnchor endpointAnchor) {}

    public record VisualSnapshot(
            UUID owner,
            Map<ClientReceipt, VisualReceiptSnapshot> receipts) {}

    public record AbilityInputSnapshot(
            String actionId, InputMethod inputMethod) {}

    public record DirectBindSnapshot(
            String actionId, InputMethod inputMethod) {}

    public record HotbarSlotSnapshot(
            int slotIndex, List<AbilityInputSnapshot> inputs) {}

    public record HotbarSnapshot(
            int hotbarIndex,
            int selectedSlotIndex,
            List<HotbarSlotSnapshot> slots) {
        public List<String> orderedActionIds() {
            return slots.stream()
                    .flatMap(slot -> slot.inputs().stream())
                    .map(AbilityInputSnapshot::actionId)
                    .toList();
        }
    }

    public record ActiveControlsSnapshot(
            boolean available,
            String reason,
            String moveGroupName,
            int moveGroupCount,
            List<DirectBindSnapshot> directBinds,
            List<HotbarSnapshot> hotbars) {
        private static ActiveControlsSnapshot unavailable(
                String reason) {
            return new ActiveControlsSnapshot(
                    false,
                    reason,
                    "unavailable",
                    0,
                    List.of(),
                    List.of());
        }
    }

    public record LiveEntitySnapshot(
            int scaleProjectiles,
            int laserProjectiles,
            int tentacleProjectiles,
            int sendoAreaEffects,
            int turquoiseAreaEffects,
            int hamonBlockAreaEffects,
            int rabbits,
            int phantoms,
            int sandstorms,
            int leavesGliders,
            int afterimages,
            Map<UUID, TemporaryEntitySnapshot> temporaryEntities) {}

    public record TemporaryEntitySnapshot(
            UUID uuid,
            String kind,
            int elapsedTicks,
            int lifeTicks,
            int phaseTicks,
            List<Integer> waveAges) {}

    private record SpatialBounds(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ) {
        private static SpatialBounds from(AABB box) {
            return new SpatialBounds(
                    box.minX,
                    box.minY,
                    box.minZ,
                    box.maxX,
                    box.maxY,
                    box.maxZ);
        }

        private static SpatialBounds between(Vec3 first, Vec3 second) {
            return new SpatialBounds(
                    Math.min(first.x, second.x),
                    Math.min(first.y, second.y),
                    Math.min(first.z, second.z),
                    Math.max(first.x, second.x),
                    Math.max(first.y, second.y),
                    Math.max(first.z, second.z));
        }
    }

    private static final class VisualTrace {
        private final EnumMap<ClientReceipt, MutableVisualReceipt>
                receipts = new EnumMap<>(ClientReceipt.class);

        private VisualTrace() {
            for (ClientReceipt receipt : ClientReceipt.values()) {
                receipts.put(receipt, new MutableVisualReceipt());
            }
        }
    }

    private record ContinuityAge(long gameTime, int logicalAge) {}

    private static final class MutableVisualReceipt {
        private long count;
        private long submissionSequence;
        private int lastBatchCount;
        private long lastFrameNanos = -1L;
        private long lastClientGameTime = -1L;
        private String evidenceToken = "not_observed";
        private int entityId = -1;
        private String submissionKind = "none";
        private boolean downstreamSubmission;
        @Nullable private Object submissionTicketIdentity;
        private long submissionTicketId;
        @Nullable private SubmissionIdentitySnapshot submissionIdentity;
        private int vertexCount;
        private double x = Double.NaN;
        private double y = Double.NaN;
        private double z = Double.NaN;
        private double minX = Double.NaN;
        private double minY = Double.NaN;
        private double minZ = Double.NaN;
        private double maxX = Double.NaN;
        private double maxY = Double.NaN;
        private double maxZ = Double.NaN;
        private double submittedMinX = Double.NaN;
        private double submittedMinY = Double.NaN;
        private double submittedMinZ = Double.NaN;
        private double submittedMaxX = Double.NaN;
        private double submittedMaxY = Double.NaN;
        private double submittedMaxZ = Double.NaN;
        private double entityX = Double.NaN;
        private double entityY = Double.NaN;
        private double entityZ = Double.NaN;
        private double startX = Double.NaN;
        private double startY = Double.NaN;
        private double startZ = Double.NaN;
        private double endX = Double.NaN;
        private double endY = Double.NaN;
        private double endZ = Double.NaN;
    }
}
