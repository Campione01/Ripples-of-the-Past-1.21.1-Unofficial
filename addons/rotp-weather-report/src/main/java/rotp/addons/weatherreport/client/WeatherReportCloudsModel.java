package rotp.addons.weatherreport.client;

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * The 1.16 {@code CloudsModel}: a PlayerModel inflated by 0.35 whose body carries four groups of cloud
 * cubes (all UV 24,0 on {@code clouds.png}) that slowly counter-rotate around the user while the cloud
 * shield effect is active. The player cubes themselves map onto empty areas of the texture.
 */
public final class WeatherReportCloudsModel extends PlayerModel<AbstractClientPlayer> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(WeatherReportAddon.id("cloud_shield"), "main");
    private static final float INFLATION = 0.35F;
    private static final int TEXTURE_SIZE = 64;
    private static final float ROTATION_STEP = 0.005F;

    private final ModelPart cloud1;
    private final ModelPart cloud2;
    private final ModelPart cloud3;
    private final ModelPart cloud4;
    private float rotation;

    public WeatherReportCloudsModel(ModelPart root) {
        super(root, false);
        this.cloud1 = body.getChild("cloud1");
        this.cloud2 = body.getChild("cloud2");
        this.cloud3 = body.getChild("cloud3");
        this.cloud4 = body.getChild("cloud4");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(INFLATION), false);
        PartDefinition body = mesh.getRoot().getChild("body");
        body.addOrReplaceChild("cloud2", CubeListBuilder.create()
                .texOffs(24, 0).addBox(-8.75F, 6.0F, 6.25F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-12.75F, 6.0F, 6.25F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-12.75F, 6.0F, 2.25F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-12.75F, 4.0F, 6.25F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, 1.0F, 0.0F));
        body.addOrReplaceChild("cloud1", CubeListBuilder.create()
                .texOffs(24, 0).addBox(-11.25F, -2.0F, 0.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-15.25F, -2.0F, -3.0F, 4.0F, 2.0F, 4.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("cloud4", CubeListBuilder.create()
                .texOffs(24, 0).addBox(2.0F, 13.0F, -13.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-2.0F, 13.0F, -9.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-2.0F, 15.0F, -13.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-6.0F, 13.0F, -13.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-2.0F, 13.0F, -13.0F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, -2.0F));
        body.addOrReplaceChild("cloud3", CubeListBuilder.create()
                .texOffs(24, 0).addBox(3.0F, -13.0F, 7.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-1.0F, -13.0F, 11.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-1.0F, -15.0F, 7.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-5.0F, -13.0F, 11.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-1.0F, -13.0F, 7.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(24, 0).addBox(-5.0F, -13.0F, 7.0F, 4.0F, 2.0F, 4.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    /** Donor-exact: advanced once per rendered frame, clouds 1/4 and 2/3 spin in opposite directions. */
    public void updateRotation() {
        cloud2.yRot = -rotation;
        cloud1.yRot = rotation;
        cloud4.yRot = rotation;
        cloud3.yRot = -rotation;
        if (rotation >= 360.0F) {
            rotation = 0.0F;
        }
        rotation += ROTATION_STEP;
    }
}
