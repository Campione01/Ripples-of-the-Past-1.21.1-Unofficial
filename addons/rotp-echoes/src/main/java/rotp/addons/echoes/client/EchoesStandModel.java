package rotp.addons.echoes.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.stand.EchoesStandEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class EchoesStandModel extends StandEntityModel<
        EchoesStandEntity, StandEntityRenderState> {
    public static final float MAX_HEAD_YAW = 35.0F;
    public static final float MAX_HEAD_PITCH = 25.0F;
    public static final float ACT2_MAX_HEAD_YAW = 50.0F;
    public static final float ACT2_MAX_HEAD_PITCH = 35.0F;
    public static final float ACT2_HEAD_YAW_SCALE = 0.6F;
    public static final float ACT2_HEAD_PITCH_SCALE = 1.2F;
    public static final float HEAD_LERP = 0.15F;
    /**
     * The author's rigs keep the chest cubes on the bone that also parents the arms and neck. The core re-shows
     * every ancestor of an arm in arms-only mode, so those cubes were moved to {@code torso_bend_cubes}, a
     * track-less twin under {@code torso_no_arms}; it takes {@code torso_bend}'s transform every frame here, which
     * covers the authored clips and the core's fallback clips alike without any channel being applied twice.
     */
    public static final String TORSO_BEND_CUBES = "torso_bend_cubes";
    private static final ResourceLocation ACT2_ID = RotpEchoes.id("echoes_act2");

    private final ModelPart torsoBendCubes;
    private float previousHeadYaw;
    private float previousHeadPitch;

    public EchoesStandModel(ModelPart root) {
        super(root);
        // Model_1_21_2plus is a mixin-injected interface on Model; a final class can only reach it via Object
        torsoBendCubes = ((Model_1_21_2plus) (Object) this)
                .jojo_ripples$getAnyDescendantWithName(TORSO_BEND_CUBES)
                .orElse(null);
    }

    @Override
    public void setupAnim(StandEntityRenderState renderState) {
        super.setupAnim(renderState);
        if (torsoBendCubes != null && torso_bend != null) {
            torsoBendCubes.copyFrom(torso_bend);
        }
        if (head == null) {
            return;
        }
        boolean act2 = renderState.skin != null
                && ACT2_ID.equals(renderState.skin.standTypeId);
        float maxYaw = act2 ? ACT2_MAX_HEAD_YAW : MAX_HEAD_YAW;
        float maxPitch = act2 ? ACT2_MAX_HEAD_PITCH : MAX_HEAD_PITCH;
        float targetYaw = Mth.clamp(renderState.yRot, -maxYaw, maxYaw);
        float targetPitch = Mth.clamp(renderState.xRot, -maxPitch, maxPitch);
        previousHeadYaw = Mth.lerp(
                HEAD_LERP, previousHeadYaw, targetYaw);
        previousHeadPitch = Mth.lerp(
                HEAD_LERP, previousHeadPitch, targetPitch);
        head.xRot = previousHeadPitch * ((float) Math.PI / 180.0F);
        head.yRot = previousHeadYaw * ((float) Math.PI / 180.0F);
        if (act2) {
            head.xRot *= ACT2_HEAD_PITCH_SCALE;
            head.yRot *= ACT2_HEAD_YAW_SCALE;
        }
        head.zRot = 0.0F;
    }
}
