package rotp.addons.d4c.client;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CRuntimeRules;
import rotp.addons.d4c.entity.D4CMarkerEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class D4CFlagModel extends EntityModel<D4CMarkerEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(D4CAddon.id("flag"), "main");

    private static final float MODE_ZERO_DURATION = 1.5417F;
    private static final float MODE_ONE_DURATION = 1.9167F;

    private final ModelPart flagRoot;
    private final ModelPart animationRoot;
    private final ModelPart center;
    private final ModelPart lowerMiddle;
    private final ModelPart lowerTip;
    private final ModelPart upperMiddle;
    private final ModelPart upperTip;
    private float renderScaleX = 1.0F;
    private float renderScaleY = 1.0F;
    private float renderScaleZ = 1.0F;

    public D4CFlagModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        flagRoot = root.getChild("flag_root");
        animationRoot = flagRoot.getChild("animation_root");
        center = animationRoot.getChild("center");
        lowerMiddle = center.getChild("lower_middle");
        lowerTip = lowerMiddle.getChild("lower_tip");
        upperMiddle = center.getChild("upper_middle");
        upperTip = upperMiddle.getChild("upper_tip");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition flagRoot = root.addOrReplaceChild(
                "flag_root",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        0.0F,
                        6.0F,
                        0.75F,
                        0.0F,
                        0.0F,
                        -1.5708F));
        PartDefinition animationRoot = flagRoot.addOrReplaceChild(
                "animation_root",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 4.0F));
        PartDefinition center = animationRoot.addOrReplaceChild(
                "center",
                CubeListBuilder.create()
                        .texOffs(0, 23)
                        .addBox(
                                -8.0F,
                                -3.0F,
                                -0.1F,
                                16.0F,
                                6.0F,
                                0.3F),
                PartPose.offset(0.0F, 0.0F, -3.75F));
        PartDefinition lowerMiddle = center.addOrReplaceChild(
                "lower_middle",
                CubeListBuilder.create()
                        .texOffs(0, 18)
                        .addBox(
                                -8.0F,
                                0.0F,
                                -0.1F,
                                16.0F,
                                6.0F,
                                0.3F),
                PartPose.offset(0.0F, 3.0F, 0.0F));
        lowerMiddle.addOrReplaceChild(
                "lower_tip",
                CubeListBuilder.create()
                        .texOffs(0, 12)
                        .addBox(
                                -8.0F,
                                0.0F,
                                -0.1F,
                                16.0F,
                                6.0F,
                                0.3F),
                PartPose.offset(0.0F, 6.0F, 0.0F));
        PartDefinition upperMiddle = center.addOrReplaceChild(
                "upper_middle",
                CubeListBuilder.create()
                        .texOffs(0, 6)
                        .addBox(
                                -8.0F,
                                -6.0F,
                                -0.1F,
                                16.0F,
                                6.0F,
                                0.3F),
                PartPose.offset(0.0F, -3.0F, 0.0F));
        upperMiddle.addOrReplaceChild(
                "upper_tip",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -8.0F,
                                -6.0F,
                                -0.1F,
                                16.0F,
                                6.0F,
                                0.3F),
                PartPose.offset(0.0F, -6.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(
            D4CMarkerEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float netHeadYaw,
            float headPitch) {
        flagRoot.getAllParts().forEach(ModelPart::resetPose);
        renderScaleX = 1.0F;
        renderScaleY = 1.0F;
        renderScaleZ = 1.0F;
        float progress = entity.animationProgress(partialTick);
        if (entity.mode() == D4CMarkerEntity.Mode.KICK_FLAG) {
            applyModeOne(progress);
        } else {
            applyModeZero(progress);
        }
    }

    private void applyModeZero(float progress) {
        animationRoot.xRot = radians(sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {
                        0.0F, 0.125F, 0.25F, 0.375F, 0.5F,
                        0.625F, 0.75F, 0.875F, 1.0417F,
                        1.125F, 1.2083F
                },
                new float[] {
                        0.0F, -20.0F, -125.0F, -205.0F,
                        -282.5F, -370.0F, -480.0F, -627.5F,
                        -721.4243F, -722.2189F, -867.6107F
                }));
        animationRoot.yRot = radians(sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {
                        0.0F, 0.125F, 0.25F, 0.375F, 0.5F,
                        0.625F, 0.75F, 0.875F, 1.0417F,
                        1.125F, 1.2083F
                },
                new float[] {
                        0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                        0.0F, 0.0F, -51.3925F, -66.3854F,
                        -88.3408F
                }));
        animationRoot.zRot = radians(sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {
                        0.0F, 0.125F, 0.25F, 0.375F, 0.5F,
                        0.625F, 0.75F, 0.875F, 1.0417F,
                        1.125F, 1.2083F
                },
                new float[] {
                        0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                        0.0F, 0.0F, 16.6989F, 17.6191F,
                        163.2074F
                }));
        animationRoot.x = sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {0.0F, 1.0417F, 1.125F, 1.2917F},
                new float[] {0.0F, 6.0F, 13.0F, 14.0F});
        animationRoot.z = 4.0F + sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {0.0F, 1.0417F, 1.125F, 1.2917F},
                new float[] {0.0F, 0.0F, -3.0F, -3.0F});
        renderScaleX = 1.2F * sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {0.0F, 1.3333F, 1.5417F},
                new float[] {1.0F, 1.0F, 0.6F});
        renderScaleY = 1.2F * sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {0.0F, 1.3333F, 1.5417F},
                new float[] {1.0F, 1.0F, 0.0F});
        renderScaleZ = 1.2F * sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {0.0F, 1.3333F, 1.5417F},
                new float[] {1.0F, 1.0F, 0.0F});
        center.xRot = radians(sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {0.0F, 1.0417F},
                new float[] {0.0F, -17.5F}));
        lowerMiddle.xRot = radians(sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {
                        0.0F, 0.125F, 0.25F, 1.0417F, 1.2083F
                },
                new float[] {
                        -12.5F, 17.5F, 67.5F, 17.5F, 5.0F
                }));
        lowerTip.xRot = radians(sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {
                        0.0F, 0.125F, 0.25F, 0.5F,
                        1.0417F, 1.125F, 1.2917F
                },
                new float[] {
                        12.5F, 30.0F, 62.5F, 70.0F, 10.0F,
                        -17.5F, -5.0F
                }));
        upperMiddle.xRot = radians(sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {
                        0.0F, 0.125F, 0.25F, 1.0417F,
                        1.2083F, 1.2917F
                },
                new float[] {
                        -22.5F, -42.5F, -45.0F, -27.5F,
                        -5.0F, 7.5F
                }));
        upperTip.xRot = radians(sample(
                progress,
                MODE_ZERO_DURATION,
                new float[] {
                        0.0F, 0.25F, 1.0417F, 1.125F, 1.2083F
                },
                new float[] {
                        -60.0F, -72.5F, -65.0F, 15.0F, -7.5F
                }));
    }

    private void applyModeOne(float progress) {
        animationRoot.xRot = radians(sample(
                progress,
                MODE_ONE_DURATION,
                new float[] {
                        0.0F, 0.25F, 0.5833F, 1.375F, 1.6667F
                },
                new float[] {
                        0.0F, 0.0F, -360.0F, -360.0F, -522.5F
                }));
        animationRoot.yRot = radians(sample(
                progress,
                MODE_ONE_DURATION,
                new float[] {
                        0.0F, 0.25F, 0.5833F, 1.375F, 1.6667F
                },
                new float[] {
                        90.0F, 90.0F, 80.0F, 72.5F, 85.0F
                }));
        animationRoot.zRot = radians(sample(
                progress,
                MODE_ONE_DURATION,
                new float[] {
                        0.0F, 0.25F, 0.5833F, 1.375F, 1.6667F
                },
                new float[] {
                        15.0F, 15.0F, -345.0F, -345.0F, -525.0F
                }));
        animationRoot.x = sample(
                progress,
                MODE_ONE_DURATION,
                new float[] {
                        0.0F, 0.25F, 0.5833F, 1.0F,
                        1.375F, 1.6667F
                },
                new float[] {
                        -61.0F, -61.0F, -37.0F, -16.0F,
                        10.0F, 20.0F
                });
        lowerMiddle.xRot = radians(sample(
                progress,
                MODE_ONE_DURATION,
                new float[] {0.0F, 0.25F, 1.0F},
                new float[] {-20.0F, -20.0F, -5.0F}));
        lowerTip.xRot = radians(sample(
                progress,
                MODE_ONE_DURATION,
                new float[] {
                        0.0F, 0.25F, 0.5833F, 1.0F,
                        1.375F, 1.6667F
                },
                new float[] {
                        10.0F, 10.0F, -30.0F, -15.0F,
                        -22.5F, 0.0F
                }));
        upperMiddle.xRot = radians(sample(
                progress,
                MODE_ONE_DURATION,
                new float[] {0.0F, 0.25F, 0.5833F, 1.0F},
                new float[] {10.0F, 10.0F, 15.0F, 7.5F}));
        upperTip.xRot = radians(sample(
                progress,
                MODE_ONE_DURATION,
                new float[] {
                        0.0F, 0.25F, 0.5833F, 1.0F, 1.375F
                },
                new float[] {
                        -10.0F, -10.0F, 45.0F, 20.0F, -5.0F
                }));
    }

    private static float sample(
            float progress,
            float duration,
            float[] times,
            float[] values) {
        return D4CRuntimeRules.sampleNormalizedKeyframes(
                progress, duration, times, values);
    }

    private static float radians(float degrees) {
        return degrees * Mth.DEG_TO_RAD;
    }

    public float renderScaleX() {
        return renderScaleX;
    }

    public float renderScaleY() {
        return renderScaleY;
    }

    public float renderScaleZ() {
        return renderScaleZ;
    }

    RenderPose renderPose() {
        return new RenderPose(
                animationRoot.x,
                animationRoot.y,
                animationRoot.z,
                animationRoot.xRot,
                animationRoot.yRot,
                animationRoot.zRot,
                center.xRot,
                lowerMiddle.xRot,
                lowerTip.xRot,
                upperMiddle.xRot,
                upperTip.xRot);
    }

    record RenderPose(
            float animationRootX,
            float animationRootY,
            float animationRootZ,
            float animationRootXRot,
            float animationRootYRot,
            float animationRootZRot,
            float centerXRot,
            float lowerMiddleXRot,
            float lowerTipXRot,
            float upperMiddleXRot,
            float upperTipXRot) {}

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int color) {
        flagRoot.render(
                poseStack,
                vertices,
                packedLight,
                packedOverlay,
                color);
    }
}
