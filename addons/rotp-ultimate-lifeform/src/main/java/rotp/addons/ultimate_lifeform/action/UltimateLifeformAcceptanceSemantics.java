package rotp.addons.ultimate_lifeform.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Pure semantic validators shared by the runtime gate and mutation tests. */
public final class UltimateLifeformAcceptanceSemantics {
    private UltimateLifeformAcceptanceSemantics() {}

    public static String profileBindingMismatch(
            String requestedProfile,
            String observedProfile,
            List<String> allowedProfiles) {
        String requested = requestedProfile == null
                ? "" : requestedProfile.trim().toUpperCase(Locale.ROOT);
        String observed = observedProfile == null
                ? "" : observedProfile.trim().toUpperCase(Locale.ROOT);
        if (requested.isEmpty()) {
            return "requested_profile_property_missing";
        }
        if (!allowedProfiles.contains(requested)) {
            return "requested_profile_not_in_contract";
        }
        return requested.equals(observed)
                ? null : "requested_and_observed_profile_mismatch";
    }

    public static boolean exactIdentitySetMatches(
            Map<UUID, String> expected,
            Map<UUID, String> current) {
        return expected.equals(current);
    }

    public static String temporaryBridgeMismatch(
            Map<UUID, TemporaryIdentity> serverEntities,
            Map<UUID, TemporaryIdentity> clientEntities,
            Map<String, Integer> expectedCardinality,
            int minimumClientAge,
            int maximumAgeDrift,
            boolean requireSendoCadence) {
        Map<String, Integer> actualCardinality = new LinkedHashMap<>();
        for (Map.Entry<UUID, TemporaryIdentity> entry
                : serverEntities.entrySet()) {
            TemporaryIdentity entity = entry.getValue();
            if (!entry.getKey().equals(entity.uuid())) {
                return "server_uuid_key_mismatch";
            }
            actualCardinality.merge(entity.kind(), 1, Integer::sum);
        }
        if (!actualCardinality.equals(expectedCardinality)) {
            return "server_kind_cardinality_mismatch";
        }
        if (!clientEntities.keySet().equals(serverEntities.keySet())) {
            return "client_server_uuid_set_mismatch";
        }
        for (Map.Entry<UUID, TemporaryIdentity> entry
                : serverEntities.entrySet()) {
            UUID uuid = entry.getKey();
            TemporaryIdentity server = entry.getValue();
            TemporaryIdentity client = clientEntities.get(uuid);
            if (client == null || !uuid.equals(client.uuid())) {
                return "client_uuid_key_mismatch";
            }
            if (!server.kind().equals(client.kind())) {
                return "temporary_kind_mismatch";
            }
            if (server.lifeTicks() != client.lifeTicks()) {
                return "temporary_life_mismatch";
            }
            if (client.elapsedTicks() < minimumClientAge
                    || Math.abs(server.elapsedTicks()
                            - client.elapsedTicks()) > maximumAgeDrift) {
                return "temporary_elapsed_age_mismatch";
            }
            if (Math.abs(server.phaseTicks()
                    - client.phaseTicks()) > maximumAgeDrift) {
                return "temporary_phase_age_mismatch";
            }
            if (server.waveAges().size() != client.waveAges().size()) {
                return "temporary_wave_cardinality_mismatch";
            }
            for (int index = 0; index < server.waveAges().size(); index++) {
                if (Math.abs(server.waveAges().get(index)
                        - client.waveAges().get(index))
                        > maximumAgeDrift) {
                    return "temporary_wave_age_mismatch";
                }
            }
            if (requireSendoCadence
                    && "sendo_area".equals(server.kind())) {
                if (client.waveAges().size() < 2) {
                    return "sendo_client_wave_history_incomplete";
                }
                for (int index = 1;
                        index < client.waveAges().size(); index++) {
                    if (Math.abs(client.waveAges().get(index - 1)
                            - client.waveAges().get(index)) != 4) {
                        return "sendo_client_wave_cadence_mismatch";
                    }
                }
            }
        }
        return null;
    }

    public static String temporaryContinuityMismatch(
            Map<UUID, TemporaryIdentity> first,
            Map<UUID, TemporaryIdentity> current) {
        if (!first.keySet().equals(current.keySet())) {
            return "temporary_continuity_uuid_set_mismatch";
        }
        for (Map.Entry<UUID, TemporaryIdentity> entry : first.entrySet()) {
            TemporaryIdentity initial = entry.getValue();
            TemporaryIdentity latest = current.get(entry.getKey());
            if (latest == null
                    || !initial.uuid().equals(latest.uuid())
                    || !initial.kind().equals(latest.kind())
                    || initial.lifeTicks() != latest.lifeTicks()) {
                return "temporary_continuity_identity_mismatch";
            }
            if (latest.elapsedTicks() < initial.elapsedTicks()
                    || latest.phaseTicks() < initial.phaseTicks()) {
                return "temporary_continuity_age_regressed";
            }
        }
        return null;
    }

    public static String spatialReceiptMismatch(
            SpatialReceipt receipt, SpatialContract contract) {
        if (receipt == null
                || receipt.count() <= 0L
                || receipt.lastBatchCount() <= 0
                || receipt.submissionSequence() <= 0L
                || !receipt.downstreamSubmission()) {
            return "spatial_downstream_submission_missing";
        }
        if (!contract.submissionKind().equals(receipt.submissionKind())) {
            return "spatial_submission_kind_mismatch";
        }
        if (contract.exactVertexCount() >= 0
                && receipt.vertexCount() != contract.exactVertexCount()) {
            return "spatial_exact_vertex_count_mismatch";
        }
        if (receipt.vertexCount() < contract.minimumVertexCount()
                || receipt.vertexCount() > contract.maximumVertexCount()) {
            return "spatial_plausible_vertex_count_mismatch";
        }
        if ("particle_batch".equals(contract.submissionKind())
                && receipt.vertexCount() != receipt.lastBatchCount()) {
            return "particle_submission_count_mismatch";
        }
        if (!finite(
                receipt.x(), receipt.y(), receipt.z(),
                receipt.minX(), receipt.minY(), receipt.minZ(),
                receipt.maxX(), receipt.maxY(), receipt.maxZ(),
                receipt.submittedMinX(), receipt.submittedMinY(),
                receipt.submittedMinZ(), receipt.submittedMaxX(),
                receipt.submittedMaxY(), receipt.submittedMaxZ(),
                receipt.entityX(), receipt.entityY(), receipt.entityZ(),
                receipt.startX(), receipt.startY(), receipt.startZ(),
                receipt.endX(), receipt.endY(), receipt.endZ())) {
            return "spatial_non_finite_coordinate";
        }
        if (!ordered(
                        receipt.minX(), receipt.minY(), receipt.minZ(),
                        receipt.maxX(), receipt.maxY(), receipt.maxZ())
                || !ordered(
                        receipt.submittedMinX(),
                        receipt.submittedMinY(),
                        receipt.submittedMinZ(),
                        receipt.submittedMaxX(),
                        receipt.submittedMaxY(),
                        receipt.submittedMaxZ())) {
            return "spatial_inverted_bounds";
        }
        double worldSpan = distance(
                receipt.minX(), receipt.minY(), receipt.minZ(),
                receipt.maxX(), receipt.maxY(), receipt.maxZ());
        double submittedSpan = distance(
                receipt.submittedMinX(),
                receipt.submittedMinY(),
                receipt.submittedMinZ(),
                receipt.submittedMaxX(),
                receipt.submittedMaxY(),
                receipt.submittedMaxZ());
        if (worldSpan < contract.minimumWorldSpan()
                || worldSpan > contract.maximumWorldSpan()) {
            return "spatial_world_span_out_of_range";
        }
        if (submittedSpan > contract.maximumSubmittedSpan()
                || receipt.vertexCount() > 1
                        && submittedSpan <= 1.0E-5D) {
            return "spatial_submitted_span_out_of_range";
        }
        if (!contains(receipt, receipt.startX(), receipt.startY(),
                        receipt.startZ())
                || !contains(receipt, receipt.endX(), receipt.endY(),
                        receipt.endZ())
                || !contains(receipt, receipt.x(), receipt.y(), receipt.z())) {
            return "spatial_endpoint_or_sample_outside_bounds";
        }
        double relativeLimit = contract.maximumEntityRelativeDistance();
        if (distance(
                        receipt.entityX(), receipt.entityY(), receipt.entityZ(),
                        receipt.startX(), receipt.startY(), receipt.startZ())
                        > relativeLimit
                || distance(
                        receipt.entityX(), receipt.entityY(), receipt.entityZ(),
                        receipt.endX(), receipt.endY(), receipt.endZ())
                        > relativeLimit
                || distance(
                        receipt.entityX(), receipt.entityY(), receipt.entityZ(),
                        (receipt.minX() + receipt.maxX()) * 0.5D,
                        (receipt.minY() + receipt.maxY()) * 0.5D,
                        (receipt.minZ() + receipt.maxZ()) * 0.5D)
                        > relativeLimit) {
            return "spatial_bounds_not_relative_to_entity";
        }
        double anchorDistance = switch (contract.endpointAnchor()) {
            case NONE -> 0.0D;
            case START -> distance(
                    receipt.entityX(), receipt.entityY(), receipt.entityZ(),
                    receipt.startX(), receipt.startY(), receipt.startZ());
            case END -> distance(
                    receipt.entityX(), receipt.entityY(), receipt.entityZ(),
                    receipt.endX(), receipt.endY(), receipt.endZ());
        };
        return anchorDistance <= contract.anchorTolerance()
                ? null : "spatial_endpoint_order_mismatch";
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean ordered(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ) {
        return minX <= maxX && minY <= maxY && minZ <= maxZ;
    }

    private static boolean contains(
            SpatialReceipt receipt, double x, double y, double z) {
        double epsilon = 1.0E-4D;
        return x >= receipt.minX() - epsilon
                && y >= receipt.minY() - epsilon
                && z >= receipt.minZ() - epsilon
                && x <= receipt.maxX() + epsilon
                && y <= receipt.maxY() + epsilon
                && z <= receipt.maxZ() + epsilon;
    }

    private static double distance(
            double firstX,
            double firstY,
            double firstZ,
            double secondX,
            double secondY,
            double secondZ) {
        double x = secondX - firstX;
        double y = secondY - firstY;
        double z = secondZ - firstZ;
        return Math.sqrt(x * x + y * y + z * z);
    }

    public record TemporaryIdentity(
            UUID uuid,
            String kind,
            int elapsedTicks,
            int lifeTicks,
            int phaseTicks,
            List<Integer> waveAges) {
        public TemporaryIdentity {
            waveAges = List.copyOf(waveAges);
        }
    }

    public enum EndpointAnchor {
        NONE,
        START,
        END
    }

    public record SpatialContract(
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

    public record SpatialReceipt(
            long count,
            int lastBatchCount,
            long submissionSequence,
            String submissionKind,
            boolean downstreamSubmission,
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
            double endZ) {}
}
