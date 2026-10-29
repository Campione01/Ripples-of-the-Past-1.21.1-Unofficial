package rotp.addons.frosttraveler.client;

/** Pure receipt validation shared by runtime diagnostics and tests. */
public final class FrostTravelerMaskReceiptValidator {
    public static final String MASK_MODEL_RESOURCE_ID =
            "rotp_zcs:models/item/meat_dgs.json";
    public static final String DONOR_MASK_MODEL_SHA256 =
            "7C434145791E377B182BC99027B19ECD"
                    + "DC76AC725F9BFFEFE30E79E8FCB5429C";
    public static final String CANONICAL_MASK_MODEL_SHA256 =
            "4B58FD5478140C22C65D01DD361386E6"
                    + "FF7A1D03361C9C10CDC7D2E98D52FC40";
    private static final float POSITION_LIMIT = 8.0F;
    private static final float UV_EPSILON = 0.001F;

    private FrostTravelerMaskReceiptValidator() {}

    public static boolean itemModelReceiptAccepted(
            ItemModelReceipt candidate,
            String expectedSourcePackId,
            long expectedReloadGeneration,
            int expectedResourceManagerIdentity) {
        if (candidate == null
                || expectedSourcePackId == null
                || expectedSourcePackId.isBlank()) {
            return false;
        }
        String hash = candidate.contentSha256();
        boolean canonicalHash = DONOR_MASK_MODEL_SHA256.equals(hash)
                || CANONICAL_MASK_MODEL_SHA256.equals(hash);
        return MASK_MODEL_RESOURCE_ID.equals(candidate.resourceId())
                && expectedSourcePackId.equals(candidate.sourcePackId())
                && candidate.reloadGeneration()
                        == expectedReloadGeneration
                && candidate.resourceManagerIdentity()
                        == expectedResourceManagerIdentity
                && canonicalHash
                && candidate.resourcePresent()
                && candidate.elementsAbsent()
                && candidate.headScaleExactZero()
                && Double.isFinite(candidate.headScaleX())
                && Double.isFinite(candidate.headScaleY())
                && Double.isFinite(candidate.headScaleZ())
                && candidate.headScaleX() == 0.0D
                && candidate.headScaleY() == 0.0D
                && candidate.headScaleZ() == 0.0D;
    }

    public static boolean geometryReceiptAccepted(
            GeometryReceipt candidate) {
        return candidate != null
                && candidate.vertexCount() > 0
                && candidate.positionSampleCount()
                        == candidate.vertexCount()
                && candidate.uvSampleCount() == candidate.vertexCount()
                && candidate.uninspectedVertexCount() == 0
                && candidate.allPositionsFinite()
                && candidate.allUvsFinite()
                && finiteOrdered(candidate.minX(), candidate.maxX())
                && finiteOrdered(candidate.minY(), candidate.maxY())
                && finiteOrdered(candidate.minZ(), candidate.maxZ())
                && finiteOrdered(candidate.minU(), candidate.maxU())
                && finiteOrdered(candidate.minV(), candidate.maxV())
                && within(candidate.minX(), -POSITION_LIMIT,
                        POSITION_LIMIT)
                && within(candidate.maxX(), -POSITION_LIMIT,
                        POSITION_LIMIT)
                && within(candidate.minY(), -POSITION_LIMIT,
                        POSITION_LIMIT)
                && within(candidate.maxY(), -POSITION_LIMIT,
                        POSITION_LIMIT)
                && within(candidate.minZ(), -POSITION_LIMIT,
                        POSITION_LIMIT)
                && within(candidate.maxZ(), -POSITION_LIMIT,
                        POSITION_LIMIT)
                && within(candidate.minU(), -UV_EPSILON,
                        1.0F + UV_EPSILON)
                && within(candidate.maxU(), -UV_EPSILON,
                        1.0F + UV_EPSILON)
                && within(candidate.minV(), -UV_EPSILON,
                        1.0F + UV_EPSILON)
                && within(candidate.maxV(), -UV_EPSILON,
                        1.0F + UV_EPSILON);
    }

    private static boolean finiteOrdered(float min, float max) {
        return Float.isFinite(min)
                && Float.isFinite(max)
                && min <= max;
    }

    private static boolean within(
            float value, float minimum, float maximum) {
        return value >= minimum && value <= maximum;
    }

    public record ItemModelReceipt(
            String resourceId,
            String sourcePackId,
            String contentSha256,
            long reloadGeneration,
            int resourceManagerIdentity,
            boolean resourcePresent,
            boolean elementsAbsent,
            boolean headScaleExactZero,
            double headScaleX,
            double headScaleY,
            double headScaleZ) {}

    public record GeometryReceipt(
            int vertexCount,
            int positionSampleCount,
            int uvSampleCount,
            int uninspectedVertexCount,
            boolean allPositionsFinite,
            boolean allUvsFinite,
            float minX,
            float maxX,
            float minY,
            float maxY,
            float minZ,
            float maxZ,
            float minU,
            float maxU,
            float minV,
            float maxV) {}
}
