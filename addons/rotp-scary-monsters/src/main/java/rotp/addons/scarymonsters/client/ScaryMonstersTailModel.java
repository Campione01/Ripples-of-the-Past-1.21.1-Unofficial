package rotp.addons.scarymonsters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * The 1.16 {@code TailModel}: three tail segments hung from the lower back of the player's body part,
 * swaying with a sine wave while the Scary Monsters Stand is summoned.
 */
public final class ScaryMonstersTailModel extends Model {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ScaryMonstersAddon.id("tail"), "main");
    private static final int TEXTURE_SIZE = 64;

    private final ModelPart root;
    private final ModelPart bone2;
    private final ModelPart bone3;
    private final ModelPart bone4;

    public ScaryMonstersTailModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root;
        ModelPart da = root.getChild("da");
        this.bone2 = da.getChild("bone2");
        this.bone3 = bone2.getChild("bone3");
        this.bone4 = bone3.getChild("bone4");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition da = root.addOrReplaceChild("da", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition bone2 = da.addOrReplaceChild("bone2", CubeListBuilder.create()
                .texOffs(0, 51).addBox(-2.0F, -1.0F, 0.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(0, 32).addBox(-3.0F, -2.4F, 1.0F, 4.0F, 4.0F, 5.0F),
                PartPose.offset(1.0F, 11.5F, 2.0F));
        PartDefinition bone3 = bone2.addOrReplaceChild("bone3", CubeListBuilder.create()
                .texOffs(8, 51).addBox(-2.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(32, 0).addBox(-3.0F, -1.8F, 0.0F, 4.0F, 3.0F, 5.0F),
                PartPose.offset(0.0F, 0.0F, 6.0F));
        bone3.addOrReplaceChild("bone4", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(24, 27).addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 8.0F),
                PartPose.offset(-1.0F, 0.0F, 8.0F));
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    public void setupAnim(float ageInTicks) {
        float wave = Mth.sin(ageInTicks * 0.15F) * 0.15F;
        bone2.xRot = wave;
        bone3.xRot = -wave * 0.7F;
        bone4.xRot = wave * 0.5F;
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
