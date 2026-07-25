package com.github.standobyte.jojo.client.polaroid;

import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.firstperson.FirstPersonRender;
import com.github.standobyte.jojo.client.polaroid.PhotosCache.PhotoInstance;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.item.PhotoItem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public class PolaroidHelper {
	private static final int PHOTO_WIDTH = 272;
	private static final int PHOTO_HEIGHT = 236;

	@Nullable private static PendingPhoto pendingPhoto;
	private static boolean guiWasHidden;
	private static CameraType previousCameraType;
	private static boolean previousCanSeeStands;

	public static void takePicture(@Nullable Vec3 cameraPos, @Nullable UnaryOperator<Vector3f> cameraAngle,
			boolean canCaptureStands, int giveToPlayerId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		guiWasHidden = mc.options.hideGui;
		previousCameraType = mc.options.getCameraType();
		previousCanSeeStands = ClientGlobals.canSeeStands;
		mc.setScreen(null);
		mc.options.hideGui = true;
		mc.options.setCameraType(CameraType.FIRST_PERSON);
		if (!canCaptureStands) {
			ClientGlobals.canSeeStands = false;
		}
		pendingPhoto = new PendingPhoto(cameraPos, cameraAngle, canCaptureStands, giveToPlayerId);
	}

	public static boolean isTakingPhoto() {
		return pendingPhoto != null;
	}

	public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		PendingPhoto pending = pendingPhoto;
		if (pending != null && pending.cameraAngle != null) {
			Vector3f angles = pending.cameraAngle.apply(new Vector3f(event.getPitch(), event.getYaw(), event.getRoll()));
			event.setPitch(angles.x());
			event.setYaw(angles.y());
			event.setRoll(angles.z());
		}
	}

	public static void capturePhoto(RenderLevelStageEvent event) {
		if (pendingPhoto == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
			return;
		}
		PendingPhoto capture = pendingPhoto;
		pendingPhoto = null;

		Minecraft mc = Minecraft.getInstance();
		try {
			NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
			NativeImage cropped = cropToPhotoRatio(image);
			image.close();
			NativeImage resized = resizeNearest(cropped, Math.min(cropped.getWidth(), PHOTO_WIDTH),
					Math.min(cropped.getHeight(), PHOTO_HEIGHT));
			PhotosCache.queueToSendToServer(resized, cropped, capture.giveToPlayerId);
		}
		finally {
			mc.options.hideGui = guiWasHidden;
			if (previousCameraType != null) {
				mc.options.setCameraType(previousCameraType);
			}
			if (!capture.canCaptureStands) {
				ClientGlobals.canSeeStands = previousCanSeeStands;
			}
		}
	}

	private static NativeImage cropToPhotoRatio(NativeImage image) {
		int ssWidth = image.getWidth();
		int ssHeight = image.getHeight();
		double widthRatio = (double) ssWidth / PHOTO_WIDTH;
		double heightRatio = (double) ssHeight / PHOTO_HEIGHT;
		double photoRatio = Math.min(widthRatio, heightRatio);
		int cropWidth = Math.max(1, (int) (photoRatio * PHOTO_WIDTH));
		int cropHeight = Math.max(1, (int) (photoRatio * PHOTO_HEIGHT));
		return cropImage(image,
				(ssWidth - cropWidth) / 2, (ssHeight - cropHeight) / 2,
				(ssWidth + cropWidth) / 2, (ssHeight + cropHeight) / 2);
	}

	private static NativeImage cropImage(NativeImage image, int x0, int y0, int x1, int y1) {
		x0 = Math.max(x0, 0);
		y0 = Math.max(y0, 0);
		x1 = Math.min(x1, image.getWidth());
		y1 = Math.min(y1, image.getHeight());
		int width = Math.max(1, x1 - x0);
		int height = Math.max(1, y1 - y0);
		NativeImage cropped = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				cropped.setPixelRGBA(x, y, image.getPixelRGBA(x0 + x, y0 + y));
			}
		}
		return cropped;
	}

	private static NativeImage resizeNearest(NativeImage image, int width, int height) {
		NativeImage resized = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			int srcY = y * image.getHeight() / height;
			for (int x = 0; x < width; x++) {
				int srcX = x * image.getWidth() / width;
				resized.setPixelRGBA(x, y, image.getPixelRGBA(srcX, srcY));
			}
		}
		return resized;
	}

	public static void renderPhotoInHand(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer, int light,
			float equippedProgress, HumanoidArm hand, float swingProgress, ItemStack stack, float partialTick) {
		poseStack.pushPose();
		float side = hand == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		poseStack.translate(side * 0.125F, -0.125D, 0.0D);
		if (!entity.isInvisible()) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.ZP.rotationDegrees(side * 10.0F));
			FirstPersonRender.renderEntityArm(FirstPersonRender.getLivingRenderer(entity), entity,
					poseStack, buffer, light, partialTick, equippedProgress, swingProgress, hand);
			poseStack.popPose();
		}

		poseStack.pushPose();
		poseStack.translate(side * 0.51F, -0.08F + equippedProgress * -1.2F, -0.75D);
		float rootSwing = Mth.sqrt(swingProgress);
		float swingSin = Mth.sin(rootSwing * (float) Math.PI);
		float xOffset = -0.5F * swingSin;
		float yOffset = 0.4F * Mth.sin(rootSwing * (float) (Math.PI * 2.0D));
		float zOffset = -0.3F * Mth.sin(swingProgress * (float) Math.PI);
		poseStack.translate(side * xOffset, yOffset - 0.3F * swingSin, zOffset);
		poseStack.mulPose(Axis.XP.rotationDegrees(swingSin * -45.0F));
		poseStack.mulPose(Axis.YP.rotationDegrees(side * swingSin * -30.0F));
		renderPhoto(poseStack, buffer, light, stack, partialTick);
		poseStack.popPose();
		poseStack.popPose();
	}

	public static final ResourceLocation PHOTO_TEXTURE = JojoMod.resLoc("textures/photo_background.png");
	public static final RenderType PHOTO_BACKGROUND = RenderType.text(PHOTO_TEXTURE);

	private static void renderPhoto(PoseStack poseStack, MultiBufferSource buffer, int light, ItemStack stack, float partialTick) {
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
		poseStack.scale(0.38F, 0.38F, 0.38F);
		poseStack.translate(-0.5D, -0.5D, 0.0D);
		poseStack.scale(0.0078125F, 0.0078125F, 0.0078125F);
		VertexConsumer vertexBuilder = buffer.getBuffer(PHOTO_BACKGROUND);
		Matrix4f matrix = poseStack.last().pose();
		vertexBuilder.addVertex(matrix, -7.0F, 135.0F, 0.0F).setColor(255, 255, 255, 255).setUv(0.0F, 1.0F).setLight(light);
		vertexBuilder.addVertex(matrix, 135.0F, 135.0F, 0.0F).setColor(255, 255, 255, 255).setUv(1.0F, 1.0F).setLight(light);
		vertexBuilder.addVertex(matrix, 135.0F, -7.0F, 0.0F).setColor(255, 255, 255, 255).setUv(1.0F, 0.0F).setLight(light);
		vertexBuilder.addVertex(matrix, -7.0F, -7.0F, 0.0F).setColor(255, 255, 255, 255).setUv(0.0F, 0.0F).setLight(light);

		PhotoInstance photo = PhotosCache.getOrTryLoadPhoto(PhotosCache.currentServerId(), PhotoItem.getPhotoId(stack));
		if (photo != null) {
			drawPhoto(poseStack, buffer, light, PhotoItem.getPhotoAlpha(stack, partialTick), photo.renderType);
		}

		if (stack.has(DataComponents.CUSTOM_NAME)) {
			Component name = stack.getHoverName();
			Minecraft mc = Minecraft.getInstance();
			Font font = mc.font;
			float x = 64.0F - font.width(name) / 2.0F;
			poseStack.pushPose();
			poseStack.translate(0.0D, 0.0D, -0.1D);
			font.drawInBatch(name, x, 117.0F, 0x606060, false, poseStack.last().pose(),
					buffer, Font.DisplayMode.NORMAL, 0, light);
			poseStack.popPose();
		}
	}

	private static void drawPhoto(PoseStack poseStack, MultiBufferSource buffer, int light, float alpha, RenderType photo) {
		if (photo != null && alpha > 0.0F) {
			float x0 = 3.65F - 0.01F;
			float x1 = x0 + 120.7F + 0.02F;
			float y0 = 3.65F - 0.01F;
			float y1 = y0 + 104.725F + 0.02F;
			Matrix4f matrix = poseStack.last().pose();
			VertexConsumer vertexBuilder = buffer.getBuffer(photo);
			int alphaInt = Mth.clamp((int) (alpha * 255.0F), 0, 255);
			vertexBuilder.addVertex(matrix, x0, y1, -0.01F).setColor(255, 255, 255, alphaInt).setUv(0.0F, 1.0F).setLight(light);
			vertexBuilder.addVertex(matrix, x1, y1, -0.01F).setColor(255, 255, 255, alphaInt).setUv(1.0F, 1.0F).setLight(light);
			vertexBuilder.addVertex(matrix, x1, y0, -0.01F).setColor(255, 255, 255, alphaInt).setUv(1.0F, 0.0F).setLight(light);
			vertexBuilder.addVertex(matrix, x0, y0, -0.01F).setColor(255, 255, 255, alphaInt).setUv(0.0F, 0.0F).setLight(light);
		}
	}

	private record PendingPhoto(@Nullable Vec3 cameraPos, @Nullable UnaryOperator<Vector3f> cameraAngle,
			boolean canCaptureStands, int giveToPlayerId) {}
}
