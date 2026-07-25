package com.github.standobyte.jojoimpl.stands.crazydiamond.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.stands.crazydiamond.AngeloRockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

public class AngeloRockModel extends EntityModel<AngeloRockEntity> {
	public final ModelPart upperHalf;
	public final ModelPart lowerHalf;
	public final ModelPart shadow;
	private final Map<ModelPart, List<ModelPart.Cube>> allCubesByParts;
	private final List<ModelPart.Cube> allCubes;
	private float progress;
	private final Set<ModelPart.Cube> visibleCubes;

	public AngeloRockModel() {
		super(RenderType::entityCutoutNoCull);
		ModelPart rockRoot = createRockLayer().bakeRoot();
		this.upperHalf = rockRoot.getChild("upperHalf");
		this.lowerHalf = rockRoot.getChild("lowerHalf");
		ModelPart shadowRoot = createShadowLayer().bakeRoot();
		this.shadow = shadowRoot.getChild("shadow");

		allCubesByParts = new HashMap<>();
		allCubes = new ArrayList<>(113);
		visibleCubes = new HashSet<>();
		addCubesFrom(upperHalf);
		addCubesFrom(lowerHalf);
		Random random = new Random();
		List<ModelPart.Cube> cubesShuffled = new ArrayList<>(allCubes);
		Collections.shuffle(cubesShuffled, random);
	}

	private static LayerDefinition createRockLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition upperHalf = root.addOrReplaceChild("upperHalf", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		PartDefinition lowerHalf = root.addOrReplaceChild("lowerHalf", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		upperHalf.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(5, 5).mirror().addBox(-2.0F, -1.06F, -0.5482F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.7183F, -15.7599F, -1.9089F, 0.6723F, 0.2515F, 0.6957F));
		upperHalf.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(7, 4).mirror().addBox(-0.5F, -2.5F, -1.0F, 1.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.4678F, -15.9199F, -2.9893F, 0.0677F, 0.0992F, 0.7584F));
		upperHalf.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(5, 8).addBox(-0.5F, -2.5F, -1.0F, 1.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0498F, -16.206F, -3.4631F, 0.0345F, 0.0383F, -0.7368F));
		upperHalf.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(13, 9).addBox(-2.5F, -19.5F, -3.5F, 6.0F, 11.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, -7.65F, -1.0F, -1.248F, -1.4329F, 1.2509F));
		upperHalf.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(6, 5).mirror().addBox(-1.0F, -1.06F, -0.5482F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.2183F, -19.1599F, -0.3589F, -0.1753F, 0.9512F, -0.5726F));
		upperHalf.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(12, 15).mirror().addBox(-2.0F, -2.0F, -3.5F, 4.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.987F, -16.8635F, 1.834F, -0.1058F, 1.4622F, -1.8226F));
		upperHalf.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(7, 8).addBox(-2.0F, -8.5F, -2.5F, 7.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5815F, -14.5F, 3.0261F, 2.8617F, -1.0844F, -2.8657F));
		upperHalf.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(14, 11).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.4762F, -20.5232F, 6.7451F, -2.9596F, -0.2701F, -3.0725F));
		upperHalf.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(9, 10).addBox(-2.0F, -2.5F, -1.5F, 5.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.8315F, -16.5F, 1.0261F, -2.0839F, -1.3544F, 1.8804F));
		upperHalf.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(6, 4).addBox(-1.6548F, 1.5433F, -0.398F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.603F, -25.4857F, -1.5031F, 0.5683F, 1.0823F, -0.8161F));
		upperHalf.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(0, 1).addBox(-2.0207F, -2.2021F, -0.398F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.603F, -25.4857F, -1.5031F, 0.1394F, 1.1599F, -1.2904F));
		upperHalf.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(6, 4).addBox(-0.2518F, -3.6348F, -0.398F, 3.0F, 2.25F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.603F, -25.4857F, -1.5031F, -1.0679F, 0.6081F, -2.7086F));
		upperHalf.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(8, 2).addBox(-2.0F, -2.0F, -0.5F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7034F, -27.5561F, 1.7341F, -0.8212F, 1.1492F, -2.3533F));
		upperHalf.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(7, 4).addBox(0.0091F, -2.3311F, -0.398F, 2.0F, 1.0F, 3.0F, new CubeDeformation(-0.001F)), PartPose.offsetAndRotation(0.603F, -25.4857F, -1.5031F, -0.8451F, 0.9323F, -2.3939F));
		upperHalf.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(13, 12).mirror().addBox(-1.0F, -2.5F, -1.5F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5815F, -19.0F, 2.5261F, 2.2896F, 0.2721F, 2.208F));
		upperHalf.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(9, 2).mirror().addBox(-0.5F, -1.0F, -2.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0865F, -21.3472F, 3.1215F, 2.5927F, 0.2702F, 2.4116F));
		upperHalf.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(4, 2).mirror().addBox(-3.0F, -2.5F, -1.5F, 5.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.8315F, -18.5F, 4.2761F, 2.4907F, 0.6898F, 2.332F));
		upperHalf.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(6, 13).addBox(1.0F, -2.5F, -2.5F, 4.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0815F, -16.75F, 4.7761F, 3.0235F, -0.5502F, -2.5147F));
		upperHalf.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(5, 13).addBox(2.0F, -2.5F, -2.5F, 3.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.3315F, -14.5F, 4.7761F, 2.2632F, -1.155F, -2.657F));
		upperHalf.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(9, 12).addBox(-2.5F, -3.0F, -3.0F, 5.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.551F, -16.0381F, 5.5679F, 1.9104F, -1.2029F, -1.8779F));
		upperHalf.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(4, 5).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0633F, -19.3507F, 6.8138F, -1.6108F, -1.2519F, 1.8394F));
		upperHalf.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(3, 2).addBox(0.5302F, -1.7797F, -2.8565F, 3.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.3975F, -19.286F, -1.8665F, -0.5589F, -0.9935F, 0.8948F));
		upperHalf.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(10, 1).mirror().addBox(-1.5F, -2.0F, -1.0F, 3.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.3606F, -20.9278F, -0.6169F, -0.1175F, 0.383F, 0.7463F));
		upperHalf.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(3, 7).mirror().addBox(-2.0F, -1.06F, -0.5482F, 4.0F, 4.25F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6183F, -19.0599F, -2.1089F, 0.0339F, 0.3194F, 0.7529F));
		upperHalf.addOrReplaceChild("cube_r25", CubeListBuilder.create().texOffs(0, 4).mirror().addBox(-6.0F, -2.9163F, -0.7646F, 8.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.7183F, -19.1599F, -2.0589F, -0.1843F, 0.3194F, 0.7529F));
		upperHalf.addOrReplaceChild("cube_r26", CubeListBuilder.create().texOffs(6, 9).addBox(-3.5459F, -3.5813F, -0.993F, 7.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.3975F, -19.286F, -1.8665F, -0.3139F, -0.3131F, -0.7163F));
		upperHalf.addOrReplaceChild("cube_r27", CubeListBuilder.create().texOffs(5, 7).addBox(-3.6958F, -1.7542F, -0.6304F, 6.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.3975F, -19.286F, -1.8665F, -0.0957F, -0.3131F, -0.7163F));
		upperHalf.addOrReplaceChild("cube_r28", CubeListBuilder.create().texOffs(6, 5).addBox(-1.0F, -2.5F, -1.0F, 4.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.275F, -21.0721F, -0.6892F, -1.1582F, -1.2349F, 0.6829F));
		upperHalf.addOrReplaceChild("cube_r29", CubeListBuilder.create().texOffs(11, 11).addBox(-2.0F, -1.5F, -2.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.4626F, -23.8765F, 2.5666F, -0.9411F, -0.8948F, -0.305F));
		upperHalf.addOrReplaceChild("cube_r30", CubeListBuilder.create().texOffs(7, 10).addBox(-2.5F, -2.0F, -3.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.051F, -20.5381F, 4.8179F, -0.2797F, -1.3999F, -0.0268F));
		upperHalf.addOrReplaceChild("cube_r31", CubeListBuilder.create().texOffs(12, 8).addBox(-1.0F, -1.0F, -1.5F, 2.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.1766F, -24.3429F, 3.5363F, 0.3178F, -0.5732F, -0.8982F));
		upperHalf.addOrReplaceChild("cube_r32", CubeListBuilder.create().texOffs(11, 9).addBox(-2.0F, -2.0F, -2.5F, 4.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.4315F, -22.9629F, 4.8246F, 1.1122F, -0.3447F, -2.3343F));
		upperHalf.addOrReplaceChild("cube_r33", CubeListBuilder.create().texOffs(12, 11).addBox(-1.0F, -2.0F, -1.5F, 2.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.9266F, -23.8429F, 6.0363F, 1.4008F, -0.9866F, -1.628F));
		upperHalf.addOrReplaceChild("cube_r34", CubeListBuilder.create().texOffs(12, 12).mirror().addBox(-2.0F, -2.5F, -1.5F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.3315F, -23.5F, 3.2761F, 2.028F, 0.1806F, 1.9952F));
		upperHalf.addOrReplaceChild("cube_r35", CubeListBuilder.create().texOffs(10, 9).mirror().addBox(-1.5F, -1.5F, -1.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.3476F, -25.3139F, 1.3424F, 1.5414F, 0.7759F, 2.4594F));
		upperHalf.addOrReplaceChild("cube_r36", CubeListBuilder.create().texOffs(4, 4).addBox(-1.2871F, 1.1259F, -2.4335F, 5.0F, 3.6F, 3.0F, new CubeDeformation(-0.001F)), PartPose.offsetAndRotation(0.603F, -25.4857F, -1.0031F, 1.8907F, 0.9426F, 0.3011F));
		upperHalf.addOrReplaceChild("cube_r37", CubeListBuilder.create().texOffs(7, 4).addBox(1.6095F, -4.6629F, -2.4335F, 2.0F, 3.6F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.603F, -25.4857F, -1.0031F, -1.8531F, 0.8454F, 2.9672F));
		upperHalf.addOrReplaceChild("cube_r38", CubeListBuilder.create().texOffs(7, 2).addBox(0.0818F, -3.1344F, -2.4335F, 2.0F, 3.6F, 3.0F, new CubeDeformation(-0.001F)), PartPose.offsetAndRotation(0.603F, -25.4857F, -1.0031F, -3.1032F, 1.3848F, 1.6492F));
		upperHalf.addOrReplaceChild("cube_r39", CubeListBuilder.create().texOffs(8, 0).addBox(0.1205F, -0.3509F, -2.4335F, 2.0F, 3.6F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.603F, -25.4857F, -1.0031F, -2.6765F, 1.3625F, 2.084F));
		upperHalf.addOrReplaceChild("cube_r40", CubeListBuilder.create().texOffs(1, 5).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.001F)), PartPose.offsetAndRotation(4.7187F, -23.9366F, -2.3122F, -2.6517F, 0.8448F, -0.0438F));
		upperHalf.addOrReplaceChild("cube_r41", CubeListBuilder.create().texOffs(15, 0).mirror().addBox(-1.4772F, -0.5747F, -0.9995F, 2.0F, 3.6F, 2.0F, new CubeDeformation(-0.001F)), PartPose.offsetAndRotation(-2.8507F, -23.4696F, -1.7094F, 2.1618F, -0.8956F, -0.019F));
		upperHalf.addOrReplaceChild("cube_r42", CubeListBuilder.create().texOffs(3, 0).mirror().addBox(-1.476F, -3.0257F, -1.0005F, 2.0F, 3.6F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.8507F, -23.4696F, -1.7094F, -2.7478F, -1.1841F, -1.5289F));
		upperHalf.addOrReplaceChild("cube_r43", CubeListBuilder.create().texOffs(3, 0).addBox(-0.8722F, -4.0741F, -1.0F, 2.0F, 5.6F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.4125F, -23.3533F, -2.2979F, -2.5804F, 1.1929F, 1.7422F));
		upperHalf.addOrReplaceChild("cube_r44", CubeListBuilder.create().texOffs(5, 0).addBox(-1.4543F, -1.9443F, -1.0847F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.3975F, -19.286F, -1.8665F, -0.2419F, 0.1744F, 0.7245F));
		upperHalf.addOrReplaceChild("cube_r45", CubeListBuilder.create().texOffs(9, 3).addBox(-1.5F, -1.5F, -0.5F, 4.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.25F, -20.35F, -2.0F, -0.3147F, 0.1053F, 0.0689F));
		upperHalf.addOrReplaceChild("cube_r46", CubeListBuilder.create().texOffs(6, 8).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.25F, -20.35F, -2.3F, 0.3942F, -0.767F, -0.5787F));
		upperHalf.addOrReplaceChild("cube_r47", CubeListBuilder.create().texOffs(6, 8).addBox(-1.06F, -1.3447F, -0.8181F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.3975F, -19.286F, -1.8665F, 0.5318F, -0.3653F, 1.0428F));
		upperHalf.addOrReplaceChild("cube_r48", CubeListBuilder.create().texOffs(7, 2).mirror().addBox(-1.0961F, -0.9227F, -0.777F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.2147F, -17.66F, -3.0913F, 0.0953F, -1.1343F, 1.5618F));
		lowerHalf.addOrReplaceChild("cube_r49", CubeListBuilder.create().texOffs(4, 0).addBox(-1.0F, -2.5F, -1.5F, 5.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.927F, -13.285F, -2.4268F, -2.6233F, -0.8447F, 2.4411F));
		lowerHalf.addOrReplaceChild("cube_r50", CubeListBuilder.create().texOffs(3, 2).addBox(-2.5F, -2.5F, -3.5F, 4.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.3657F, -12.6431F, 0.2949F, 3.1183F, 0.3786F, -2.8293F));
		lowerHalf.addOrReplaceChild("cube_r51", CubeListBuilder.create().texOffs(11, 15).mirror().addBox(-2.0F, -2.0F, -2.5F, 4.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.487F, -8.8635F, -0.916F, 0.4442F, 1.0881F, -0.1495F));
		lowerHalf.addOrReplaceChild("cube_r52", CubeListBuilder.create().texOffs(11, 15).addBox(-3.5F, -1.5F, -3.5F, 7.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.7723F, -8.425F, 0.2908F, -0.0851F, 0.108F, -0.3206F));
		lowerHalf.addOrReplaceChild("cube_r53", CubeListBuilder.create().texOffs(16, 18).addBox(-1.5F, -2.0F, -1.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.4758F, -4.0938F, -4.2116F, -0.3099F, -0.7723F, 0.4415F));
		lowerHalf.addOrReplaceChild("cube_r54", CubeListBuilder.create().texOffs(9, 10).addBox(-2.5F, -1.5F, -0.5F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, -3.95F, -4.45F, -0.886F, -1.1655F, 0.7328F));
		lowerHalf.addOrReplaceChild("cube_r55", CubeListBuilder.create().texOffs(9, 8).addBox(-0.5952F, -2.25F, -1.5765F, 1.0F, 4.5F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.352F, -12.1823F, -2.8899F, -2.4363F, -0.0266F, 2.7661F));
		lowerHalf.addOrReplaceChild("cube_r56", CubeListBuilder.create().texOffs(9, 7).addBox(-0.7295F, -2.25F, 0.0374F, 1.0F, 4.5F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.352F, -12.1823F, -2.8899F, -2.3402F, -0.4461F, 2.3697F));
		lowerHalf.addOrReplaceChild("cube_r57", CubeListBuilder.create().texOffs(12, 9).addBox(-0.1585F, -2.5F, -2.7745F, 1.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5134F, -12.4487F, -3.5369F, -2.2894F, 0.1884F, -2.6274F));
		lowerHalf.addOrReplaceChild("cube_r58", CubeListBuilder.create().texOffs(20, 9).addBox(-0.1585F, -2.5F, -0.2255F, 1.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.3033F, -12.8553F, -4.4911F, -1.6711F, 0.733F, -1.4169F));
		lowerHalf.addOrReplaceChild("cube_r59", CubeListBuilder.create().texOffs(14, 10).addBox(-0.1585F, -2.5F, -2.7745F, 1.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.3033F, -12.8553F, -4.4911F, -2.0656F, 0.5731F, -2.0506F));
		lowerHalf.addOrReplaceChild("cube_r60", CubeListBuilder.create().texOffs(9, 11).mirror().addBox(-1.3976F, 0.1016F, -0.2515F, 5.0F, 1.55F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.2487F, -15.4312F, -3.2798F, -0.2209F, -0.121F, 2.3321F));
		lowerHalf.addOrReplaceChild("cube_r61", CubeListBuilder.create().texOffs(6, 6).mirror().addBox(-1.853F, -2.1616F, -0.1681F, 4.4F, 1.75F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.2487F, -15.4312F, -3.2798F, 0.3899F, -0.1503F, 1.0645F));
		lowerHalf.addOrReplaceChild("cube_r62", CubeListBuilder.create().texOffs(14, 6).addBox(-1.5F, -1.5F, -1.5F, 6.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.25F, -5.65F, -5.5F, -2.6521F, -1.0764F, 2.7163F));
		lowerHalf.addOrReplaceChild("cube_r63", CubeListBuilder.create().texOffs(20, 0).addBox(-2.5F, -1.5F, -2.5F, 4.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.25F, -6.65F, -4.5F, -0.5369F, -1.1655F, 0.7328F));
		lowerHalf.addOrReplaceChild("cube_r64", CubeListBuilder.create().texOffs(5, 13).addBox(-1.4012F, -2.5F, -2.2313F, 1.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.9871F, -13.0372F, -4.96F, -2.3853F, -0.1654F, 2.5292F));
		lowerHalf.addOrReplaceChild("cube_r65", CubeListBuilder.create().texOffs(9, 13).addBox(-1.4012F, -3.5F, -0.7687F, 1.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.2885F, -13.0099F, -4.7884F, -1.704F, -0.819F, 1.2939F));
		lowerHalf.addOrReplaceChild("cube_r66", CubeListBuilder.create().texOffs(13, 20).addBox(-2.5F, -8.5F, -3.5F, 6.0F, 12.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, -7.65F, -1.0F, -1.248F, -1.4329F, 1.2509F));
		lowerHalf.addOrReplaceChild("cube_r67", CubeListBuilder.create().texOffs(9, 12).addBox(-2.5F, -1.5F, -3.5F, 6.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -3.25F, -3.6F, 0.417F, -1.0197F, -0.4213F));
		lowerHalf.addOrReplaceChild("cube_r68", CubeListBuilder.create().texOffs(13, 14).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.2186F, -4.2232F, -7.2086F, 0.4609F, -0.6966F, 0.0098F));
		lowerHalf.addOrReplaceChild("cube_r69", CubeListBuilder.create().texOffs(12, 14).addBox(-3.5F, 2.5F, -1.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.65F, -3.35F, -4.0F, -0.0227F, -0.8711F, 0.015F));
		lowerHalf.addOrReplaceChild("cube_r70", CubeListBuilder.create().texOffs(16, 12).addBox(-3.5F, -0.5F, -1.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.4F, -3.1F, -4.5F, -0.0936F, -0.5176F, 0.1004F));
		lowerHalf.addOrReplaceChild("cube_r71", CubeListBuilder.create().texOffs(9, 13).addBox(-0.5F, -3.5F, -3.5F, 4.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.75F, -1.4F, 4.0F, -0.1268F, -0.7371F, 0.0587F));
		lowerHalf.addOrReplaceChild("cube_r72", CubeListBuilder.create().texOffs(10, 10).addBox(-2.0F, -0.5F, 1.5F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0542F, -6.975F, 0.9872F, -0.2034F, -0.978F, 0.5399F));
		lowerHalf.addOrReplaceChild("cube_r73", CubeListBuilder.create().texOffs(15, 11).addBox(-2.0F, -2.0F, 0.5F, 4.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.2575F, -12.9546F, 3.1415F, 0.4444F, -0.8721F, -0.234F));
		lowerHalf.addOrReplaceChild("cube_r74", CubeListBuilder.create().texOffs(4, 5).addBox(-2.0F, -5.5F, -3.5F, 5.0F, 8.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.8042F, -12.475F, 3.7372F, 0.1569F, -0.9413F, -0.0438F));
		lowerHalf.addOrReplaceChild("cube_r75", CubeListBuilder.create().texOffs(8, 8).addBox(-2.0F, -0.5F, -0.5F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.9458F, -9.475F, 6.9872F, 0.1576F, -1.199F, 0.0163F));
		lowerHalf.addOrReplaceChild("cube_r76", CubeListBuilder.create().texOffs(9, 9).addBox(-2.0F, -0.5F, 0.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.1958F, -5.725F, 6.7372F, -0.1601F, -0.4721F, 0.2655F));
		lowerHalf.addOrReplaceChild("cube_r77", CubeListBuilder.create().texOffs(9, 9).addBox(-2.0F, -2.0F, -1.5F, 4.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.9578F, -8.4083F, 7.4336F, 0.6043F, -0.6785F, -0.2158F));
		lowerHalf.addOrReplaceChild("cube_r78", CubeListBuilder.create().texOffs(9, 7).addBox(-2.0F, -2.5F, 0.5F, 4.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.8042F, -7.475F, 5.9872F, -0.5386F, -0.8408F, 0.3432F));
		lowerHalf.addOrReplaceChild("cube_r79", CubeListBuilder.create().texOffs(21, 9).addBox(-2.0F, -2.5F, 0.5F, 4.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5542F, -7.475F, 3.4872F, 0.2708F, -1.0123F, -0.0288F));
		lowerHalf.addOrReplaceChild("cube_r80", CubeListBuilder.create().texOffs(10, 9).addBox(-2.0F, -0.5F, -1.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.794F, -0.6307F, 7.6691F, -0.4406F, -1.0584F, 0.0765F));
		lowerHalf.addOrReplaceChild("cube_r81", CubeListBuilder.create().texOffs(12, 10).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.456F, -0.4307F, 7.6691F, 0.0695F, -0.7778F, -0.0922F));
		lowerHalf.addOrReplaceChild("cube_r82", CubeListBuilder.create().texOffs(12, 10).addBox(0.0F, 1.5F, 0.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.3042F, -2.625F, 6.2372F, -0.1571F, -1.0815F, 0.1578F));
		lowerHalf.addOrReplaceChild("cube_r83", CubeListBuilder.create().texOffs(12, 10).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.7856F, -0.4913F, 1.2271F, -0.3339F, -1.0646F, 0.2985F));
		lowerHalf.addOrReplaceChild("cube_r84", CubeListBuilder.create().texOffs(12, 10).addBox(0.0F, 0.5F, 0.5F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0542F, -2.225F, 6.2372F, 2.3154F, -1.3188F, -2.2679F));
		lowerHalf.addOrReplaceChild("cube_r85", CubeListBuilder.create().texOffs(9, 9).addBox(-2.0F, -2.5F, 0.5F, 4.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5542F, -2.225F, 4.2372F, -0.5236F, -1.4201F, 0.6166F));
		lowerHalf.addOrReplaceChild("cube_r86", CubeListBuilder.create().texOffs(7, 3).addBox(-1.0F, -2.5F, -2.5F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5815F, -11.5F, 4.7761F, -2.8382F, -1.4074F, -3.1339F));
		lowerHalf.addOrReplaceChild("cube_r87", CubeListBuilder.create().texOffs(8, 9).addBox(-2.0F, -2.5F, -1.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.5815F, -10.0F, 4.5261F, -2.7609F, -0.7802F, 2.6414F));
		lowerHalf.addOrReplaceChild("cube_r88", CubeListBuilder.create().texOffs(8, 9).addBox(-2.0F, -2.5F, -1.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0815F, -9.0F, 1.0261F, -1.3423F, -1.0673F, 2.2032F));
		lowerHalf.addOrReplaceChild("cube_r89", CubeListBuilder.create().texOffs(7, 8).addBox(-2.0F, -1.5F, -2.5F, 5.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5815F, -5.5F, 6.5261F, -2.4034F, -0.8231F, -3.0838F));
		lowerHalf.addOrReplaceChild("cube_r90", CubeListBuilder.create().texOffs(9, 10).addBox(-2.0F, -1.5F, -2.5F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5815F, -1.5F, 6.5261F, -3.0107F, -1.2217F, 3.1416F));
		lowerHalf.addOrReplaceChild("cube_r91", CubeListBuilder.create().texOffs(9, 11).addBox(-3.5F, -2.5F, -1.5F, 7.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -3.475F, 5.0F, -0.2409F, -0.9001F, 0.1395F));
		lowerHalf.addOrReplaceChild("cube_r92", CubeListBuilder.create().texOffs(10, 13).addBox(-2.5F, -7.5F, -3.5F, 6.0F, 11.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -3.2F, 4.5F, 0.0873F, -0.3054F, 0.0F));
		lowerHalf.addOrReplaceChild("cube_r93", CubeListBuilder.create().texOffs(1, 0).addBox(-3.5F, -3.5F, -3.5F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, -3.4F, -1.0F, -0.054F, 0.3892F, -0.1415F));
		lowerHalf.addOrReplaceChild("cube_r94", CubeListBuilder.create().texOffs(9, 11).addBox(-2.5F, -1.5F, -1.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.4786F, -1.3F, -5.2866F, -0.2128F, -1.0834F, 0.0925F));
		lowerHalf.addOrReplaceChild("cube_r95", CubeListBuilder.create().texOffs(14, 12).addBox(-1.0F, -0.6F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.8706F, -0.1283F, -4.1168F, -0.1662F, 0.983F, -0.2952F));
		lowerHalf.addOrReplaceChild("cube_r96", CubeListBuilder.create().texOffs(14, 12).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.1206F, -0.3283F, 4.8832F, -0.1662F, 0.983F, -0.2952F));
		lowerHalf.addOrReplaceChild("cube_r97", CubeListBuilder.create().texOffs(14, 12).addBox(0.5F, -1.0F, 0.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5587F, -1.6588F, 0.6694F, 0.1223F, 0.29F, -0.184F));
		lowerHalf.addOrReplaceChild("cube_r98", CubeListBuilder.create().texOffs(9, 10).addBox(-2.5F, -1.0F, -2.0F, 5.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.8087F, -0.9088F, 1.1694F, -0.0702F, 0.3346F, 0.18F));
		lowerHalf.addOrReplaceChild("cube_r99", CubeListBuilder.create().texOffs(9, 13).addBox(-3.5F, -3.5F, -3.5F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0723F, -3.425F, -0.7092F, -0.0406F, -0.4346F, 0.0962F));
		lowerHalf.addOrReplaceChild("cube_r100", CubeListBuilder.create().texOffs(8, 6).addBox(-1.5F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.15F, -11.2651F, -6.636F, -0.1454F, 0.4214F, -0.222F));
		lowerHalf.addOrReplaceChild("cube_r101", CubeListBuilder.create().texOffs(8, 6).addBox(-1.5F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.1494F, -11.197F, -7.2923F, -0.0389F, 0.399F, -0.2421F));
		lowerHalf.addOrReplaceChild("cube_r102", CubeListBuilder.create().texOffs(8, 6).addBox(-1.5F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.6477F, -10.8762F, -6.5697F, -0.0168F, -0.0775F, 0.2906F));
		lowerHalf.addOrReplaceChild("cube_r103", CubeListBuilder.create().texOffs(9, 6).addBox(-0.5F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.4421F, -10.6842F, -5.1124F, -0.1772F, 0.723F, -0.2801F));
		lowerHalf.addOrReplaceChild("cube_r104", CubeListBuilder.create().texOffs(8, 5).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.4296F, -9.8726F, -4.2717F, -0.4978F, 0.7758F, -0.4427F));
		lowerHalf.addOrReplaceChild("cube_r105", CubeListBuilder.create().texOffs(3, 7).addBox(-1.0F, -0.5F, -0.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.1875F, -9.5652F, -6.97F, -0.0601F, 0.0857F, 0.053F));
		lowerHalf.addOrReplaceChild("cube_r106", CubeListBuilder.create().texOffs(1, 0).addBox(-1.0F, 0.0F, -0.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(-4.7027F, -9.9043F, -5.7403F, 0.0328F, 0.485F, 0.1633F));
		lowerHalf.addOrReplaceChild("cube_r107", CubeListBuilder.create().texOffs(9, 3).addBox(-1.0F, -0.75F, -0.5F, 3.0F, 2.25F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5645F, -9.1516F, -6.9144F, -0.2547F, 0.1965F, 0.1344F));
		lowerHalf.addOrReplaceChild("cube_r108", CubeListBuilder.create().texOffs(7, 5).addBox(-1.0F, 0.25F, -0.5F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.1502F, -9.3721F, -5.2316F, 0.0345F, -1.1595F, -0.4152F));
		lowerHalf.addOrReplaceChild("cube_r109", CubeListBuilder.create().texOffs(7, 5).addBox(-1.0F, -0.75F, -0.5F, 3.0F, 2.25F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0694F, -8.7251F, -6.8108F, -0.3916F, -0.5993F, 0.2489F));
		lowerHalf.addOrReplaceChild("cube_r110", CubeListBuilder.create().texOffs(8, 6).addBox(-1.5F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.7692F, -11.6887F, -7.4259F, -0.2052F, -0.1257F, -0.2664F));
		lowerHalf.addOrReplaceChild("cube_r111", CubeListBuilder.create().texOffs(5, 1).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.8734F, -11.3739F, -6.4403F, -0.5244F, -0.7512F, 0.308F));
		lowerHalf.addOrReplaceChild("cube_r112", CubeListBuilder.create().texOffs(8, 6).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.8634F, -10.4528F, -4.4917F, -1.1744F, -0.9446F, 1.3766F));
		lowerHalf.addOrReplaceChild("cube_r113", CubeListBuilder.create().texOffs(12, 15).mirror().addBox(-2.0F, -2.0F, -3.5F, 4.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.487F, -11.8635F, 0.834F, 1.4824F, 1.1482F, 0.2277F));
		return LayerDefinition.create(mesh, 16, 16);
	}

	private static LayerDefinition createShadowLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition shadow = root.addOrReplaceChild("shadow", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		shadow.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(28, 10).addBox(-1.6012F, -3.5F, -0.7687F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.3873F, -12.992F, -4.6752F, -1.704F, -0.819F, 1.2939F));
		shadow.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(24, 10).addBox(-1.6012F, -2.5F, -2.2313F, 1.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.8871F, -13.0372F, -4.96F, -2.3853F, -0.1654F, 2.5292F));
		shadow.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(7, 5).mirror().addBox(-2.6876F, -1.25F, -0.7133F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.9456F, -21.2411F, -1.8968F, -0.3001F, 0.3473F, 0.4623F));
		shadow.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(13, 0).addBox(1.0415F, -2.25F, -2.7745F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.4199F, -12.5011F, -3.6079F, -2.2894F, 0.1884F, -2.6274F));
		shadow.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(29, 0).addBox(1.0415F, -2.25F, -0.2255F, 0.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.4037F, -12.8575F, -4.4657F, -1.6711F, 0.733F, -1.4169F));
		shadow.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(22, 1).addBox(1.0415F, -2.25F, -1.7745F, 0.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.2588F, -12.8569F, -4.5045F, -2.0897F, 0.5519F, -2.0957F));
		shadow.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(1, 12).mirror().addBox(-0.5F, -1.5F, -0.3F, 1.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.564F, -17.1914F, -3.1438F, 0.0467F, 0.3204F, 0.7819F));
		shadow.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(1, 19).mirror().addBox(-0.801F, -1.25F, -0.583F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.6734F, -20.9604F, -1.9949F, 0.0922F, 0.3246F, 1.8211F));
		shadow.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(1, 8).mirror().addBox(-0.801F, -1.25F, -0.583F, 5.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.9456F, -21.2411F, -1.8968F, -0.2864F, 0.1801F, 0.5145F));
		shadow.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(1, 5).addBox(0.7067F, -1.25F, -0.661F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.8693F, -20.9731F, -1.9272F, -0.3755F, -0.269F, -0.3171F));
		shadow.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(0, 20).mirror().addBox(-2.2F, -1.025F, -0.6F, 4.4F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.8882F, -15.6353F, -3.4242F, 0.3186F, -0.0613F, 0.9595F));
		shadow.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(4, 13).addBox(-0.5F, -1.5F, -0.3F, 1.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.748F, -17.8068F, -3.307F, -0.2221F, -0.2459F, -0.6509F));
		shadow.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(1, 19).addBox(-2.4F, -0.5F, -0.475F, 3.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0757F, -20.6782F, -2.1176F, 0.0181F, -0.3923F, -1.618F));
		shadow.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(1, 1).addBox(-4.2131F, -1.25F, -0.5631F, 5.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.8693F, -20.9731F, -1.9272F, -0.3655F, -0.147F, -0.3655F));
		shadow.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(7, 13).mirror().addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(-5.224F, -20.2983F, -2.1751F, -0.1637F, 0.2928F, 0.793F));
		shadow.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(7, 13).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(4.7567F, -19.792F, -2.5243F, -0.1795F, -0.2382F, -0.8036F));
		shadow.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(13, 8).addBox(-0.5F, -3.0F, 0.5F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.9248F, -9.3861F, -5.3675F, -0.3668F, -1.1986F, 0.3494F));
		shadow.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(18, 10).addBox(-0.5F, -3.5F, 1.5F, 1.0F, 4.5F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.9681F, -9.4237F, -5.7792F, -0.6472F, -1.3528F, 0.6415F));
		shadow.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(13, 9).addBox(-0.5F, -3.275F, 1.5F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.696F, -9.533F, -6.6861F, -0.3668F, -1.1986F, 0.3494F));
		shadow.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(17, 6).addBox(-0.5F, -2.4F, -0.5F, 1.0F, 3.4F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.4742F, -9.1796F, -4.0333F, -2.9397F, -0.8629F, 2.993F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	private void addCubesFrom(ModelPart modelPart) {
		allCubesByParts.put(modelPart, new ArrayList<>(modelPart.cubes));
		allCubes.addAll(modelPart.cubes);
		for (ModelPart child : modelPart.children.values()) {
			addCubesFrom(child);
		}
	}

	@Override
	public void setupAnim(AngeloRockEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		float yRot = netHeadYaw * MathUtil.DEG_TO_RAD;
		upperHalf.yRot = yRot;
		lowerHalf.yRot = yRot;
		shadow.yRot = yRot;
	}

	public void setCreationAnim(AngeloRockEntity entity, float progress) {
		progress = Mth.clamp(progress, 0, 1);
		if (progress == 0 && this.progress == 0 || progress == 1 && this.progress == 1) return;
		this.progress = progress;
		if (progress == 0) return;

		if (progress == 1) {
			visibleCubes.addAll(allCubes);
		}
		else {
			visibleCubes.clear();
			int renderParts = 1 + (int) (progress * allCubes.size());
			allCubes.stream().limit(renderParts).forEach(visibleCubes::add);
		}
		for (Map.Entry<ModelPart, List<ModelPart.Cube>> modelPartEntry : this.allCubesByParts.entrySet()) {
			ModelPart modelPart = modelPartEntry.getKey();
			if (modelPart.visible) {
				List<ModelPart.Cube> allCubes = modelPartEntry.getValue();
				modelPart.cubes.clear();
				allCubes.stream().filter(visibleCubes::contains).forEach(modelPart.cubes::add);
			}
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		if (progress == 0) return;
		poseStack.pushPose();
		poseStack.translate(0, -1.5, 0);
		upperHalf.render(poseStack, buffer, packedLight, packedOverlay, color);
		lowerHalf.render(poseStack, buffer, packedLight, packedOverlay, color);
		shadow.render(poseStack, buffer, packedLight, packedOverlay, color);
		poseStack.popPose();
	}
}
