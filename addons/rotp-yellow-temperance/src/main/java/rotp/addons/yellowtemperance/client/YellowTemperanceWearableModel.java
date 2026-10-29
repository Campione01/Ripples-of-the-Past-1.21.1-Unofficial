package rotp.addons.yellowtemperance.client;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * Native 256x256 player-space geometry. The 0.25 UV denominator maps the
 * standard logical skin layout over four physical texels per logical texel.
 */
public final class YellowTemperanceWearableModel<
        T extends LivingEntity> extends PlayerModel<T> {
    public static final int TEXTURE_SIZE = 256;
    public static final float UV_DENOMINATOR_SCALE = 0.25F;
    public static final ModelLayerLocation DEFAULT =
            layer("yellow_temperance_default");
    public static final ModelLayerLocation SLIM =
            layer("yellow_temperance_slim");

    public YellowTemperanceWearableModel(
            ModelPart root, boolean slim) {
        super(root, slim);
    }

    public static LayerDefinition createDefaultLayer() {
        return createLayer(false);
    }

    public static LayerDefinition createSlimLayer() {
        return createLayer(true);
    }

    private static LayerDefinition createLayer(boolean slim) {
        CubeDeformation inner = new CubeDeformation(0.18F);
        CubeDeformation outer = new CubeDeformation(0.43F);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
                "head",
                box(0, 0).addBox(
                        -4, -8, -4, 8, 8, 8,
                        inner,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.ZERO);
        root.addOrReplaceChild(
                "hat",
                box(32, 0).addBox(
                        -4, -8, -4, 8, 8, 8,
                        outer,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.ZERO);
        root.addOrReplaceChild(
                "body",
                box(16, 16).addBox(
                        -4, 0, -2, 8, 12, 4,
                        inner,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.ZERO);
        root.addOrReplaceChild(
                "jacket",
                box(16, 32).addBox(
                        -4, 0, -2, 8, 12, 4,
                        outer,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.ZERO);

        int armWidth = slim ? 3 : 4;
        float rightArmX = slim ? -2.0F : -3.0F;
        float leftArmX = -1.0F;
        float armOffset = slim ? 2.5F : 2.0F;
        root.addOrReplaceChild(
                "right_arm",
                box(40, 16).addBox(
                        rightArmX, -2, -2,
                        armWidth, 12, 4,
                        inner,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.offset(-5, armOffset, 0));
        root.addOrReplaceChild(
                "left_arm",
                (slim ? box(32, 48) : mirrored(40, 16))
                        .addBox(
                                leftArmX, -2, -2,
                                armWidth, 12, 4,
                                inner,
                                UV_DENOMINATOR_SCALE,
                                UV_DENOMINATOR_SCALE),
                PartPose.offset(5, armOffset, 0));
        root.addOrReplaceChild(
                "right_sleeve",
                box(40, 32).addBox(
                        rightArmX, -2, -2,
                        armWidth, 12, 4,
                        outer,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.offset(-5, armOffset, 0));
        root.addOrReplaceChild(
                "left_sleeve",
                box(48, 48).addBox(
                        leftArmX, -2, -2,
                        armWidth, 12, 4,
                        outer,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.offset(5, armOffset, 0));

        root.addOrReplaceChild(
                "right_leg",
                box(0, 16).addBox(
                        -2, 0, -2, 4, 12, 4,
                        inner,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.offset(-1.9F, 12, 0));
        root.addOrReplaceChild(
                "left_leg",
                box(16, 48).addBox(
                        -2, 0, -2, 4, 12, 4,
                        inner,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.offset(1.9F, 12, 0));
        root.addOrReplaceChild(
                "right_pants",
                box(0, 32).addBox(
                        -2, 0, -2, 4, 12, 4,
                        outer,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.offset(-1.9F, 12, 0));
        root.addOrReplaceChild(
                "left_pants",
                box(0, 48).addBox(
                        -2, 0, -2, 4, 12, 4,
                        outer,
                        UV_DENOMINATOR_SCALE,
                        UV_DENOMINATOR_SCALE),
                PartPose.offset(1.9F, 12, 0));
        root.addOrReplaceChild(
                "ear",
                box(24, 0),
                PartPose.ZERO);
        root.addOrReplaceChild(
                "cloak",
                box(0, 0),
                PartPose.ZERO);
        return LayerDefinition.create(
                mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private static CubeListBuilder box(int u, int v) {
        return CubeListBuilder.create().texOffs(u, v);
    }

    private static CubeListBuilder mirrored(int u, int v) {
        return box(u, v).mirror();
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(
                YellowTemperanceAddon.id(name), "main");
    }
}
