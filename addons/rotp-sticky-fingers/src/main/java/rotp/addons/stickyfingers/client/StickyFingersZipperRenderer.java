package rotp.addons.stickyfingers.client;

import rotp.core.client.ClientGlobals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.block.StickyFingersZipperBlock;
import rotp.addons.stickyfingers.blockentity.StickyFingersZipperBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class StickyFingersZipperRenderer
        implements BlockEntityRenderer<
                StickyFingersZipperBlockEntity> {
    private static final ResourceLocation VOID_TEXTURE =
            StickyFingersAddon.id(
                    "textures/zipper/effect/zipper_void.png");

    public StickyFingersZipperRenderer(
            BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            StickyFingersZipperBlockEntity zipper,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        Level level = zipper.getLevel();
        if (level == null || !ClientGlobals.canSeeStands) {
            return;
        }
        BlockState state = zipper.getBlockState();
        Direction facing =
                state.getValue(StickyFingersZipperBlock.FACING);
        renderSide(zipper, level, state, facing,
                poseStack, buffer, packedLight);
        // 1.16 drew the linked zipper of a one-block wall on its far face.
        if (state.getValue(StickyFingersZipperBlock.THROUGH)) {
            renderSide(zipper, level, state, facing.getOpposite(),
                    poseStack, buffer, packedLight);
        }
    }

    private static void renderSide(
            StickyFingersZipperBlockEntity zipper,
            Level level,
            BlockState state,
            Direction facing,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        ZipperFace face = selectFace(
                level, zipper.getBlockPos(), facing);
        boolean open =
                state.getValue(StickyFingersZipperBlock.OPEN);
        if (open) {
            renderPlane(
                    poseStack,
                    buffer.getBuffer(
                            RenderType.entityTranslucent(
                                    VOID_TEXTURE)),
                    facing,
                    0.006F,
                    packedLight);
        }
        renderPlane(
                poseStack,
                buffer.getBuffer(
                        RenderType.entityCutoutNoCull(
                                open
                                        ? openTexture(face.texture())
                                        : face.texture())),
                facing,
                0.002F,
                packedLight);
    }

    private static ZipperFace selectFace(
            Level level, BlockPos position, Direction facing) {
        Direction[] neighbors = planeDirections(facing);
        boolean[] connected = new boolean[4];
        int count = 0;
        for (int index = 0; index < neighbors.length; index++) {
            BlockState neighbor =
                    level.getBlockState(
                            position.relative(neighbors[index]));
            connected[index] =
                    StickyFingersZipperBlock.faces(neighbor, facing);
            if (connected[index]) {
                count++;
            }
        }

        if (count == 0) {
            return texture("diagonal", "");
        }
        if (count == 1) {
            return texture(
                    "straight",
                    connected[0] || connected[2]
                            ? "vertical"
                            : "horizontal");
        }
        if (count == 2) {
            if (connected[0] && connected[2]) {
                return texture("straight", "vertical");
            }
            if (connected[1] && connected[3]) {
                return texture("straight", "horizontal");
            }
            int rotation =
                    connected[0] && connected[1] ? 1
                            : connected[1] && connected[2] ? 2
                            : connected[2] && connected[3] ? 3
                            : 4;
            return texture("corner", Integer.toString(rotation));
        }
        if (count == 3) {
            int missing = 0;
            while (connected[missing]) {
                missing++;
            }
            return texture("t", Integer.toString(missing + 1));
        }
        return texture("cross", "");
    }

    private static ZipperFace texture(
            String type, String rotation) {
        String suffix = rotation.isEmpty()
                ? ""
                : "_" + rotation;
        return new ZipperFace(
                StickyFingersAddon.id(
                        "textures/zipper/" + type
                                + "/zipper_" + type
                                + suffix + ".png"));
    }

    private static ResourceLocation openTexture(
            ResourceLocation texture) {
        String path = texture.getPath();
        return ResourceLocation.fromNamespaceAndPath(
                texture.getNamespace(),
                path.substring(0, path.length() - ".png".length())
                        + "_open.png");
    }

    private static Direction[] planeDirections(
            Direction facing) {
        return switch (facing) {
            case NORTH ->
                    new Direction[] {
                        Direction.UP,
                        Direction.EAST,
                        Direction.DOWN,
                        Direction.WEST
                    };
            case SOUTH ->
                    new Direction[] {
                        Direction.UP,
                        Direction.WEST,
                        Direction.DOWN,
                        Direction.EAST
                    };
            case EAST ->
                    new Direction[] {
                        Direction.UP,
                        Direction.SOUTH,
                        Direction.DOWN,
                        Direction.NORTH
                    };
            case WEST ->
                    new Direction[] {
                        Direction.UP,
                        Direction.NORTH,
                        Direction.DOWN,
                        Direction.SOUTH
                    };
            case UP ->
                    new Direction[] {
                        Direction.NORTH,
                        Direction.EAST,
                        Direction.SOUTH,
                        Direction.WEST
                    };
            case DOWN ->
                    new Direction[] {
                        Direction.SOUTH,
                        Direction.EAST,
                        Direction.NORTH,
                        Direction.WEST
                    };
        };
    }

    private static void renderPlane(
            PoseStack poseStack,
            VertexConsumer vertices,
            Direction facing,
            float inset,
            int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        float low = inset;
        float high = 1.0F - inset;
        switch (facing) {
            case NORTH -> {
                vertex(vertices, pose, low, low, low,
                        0.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, high, low, low,
                        1.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, high, high, low,
                        1.0F, 0.0F, facing, packedLight);
                vertex(vertices, pose, low, high, low,
                        0.0F, 0.0F, facing, packedLight);
            }
            case SOUTH -> {
                vertex(vertices, pose, high, low, high,
                        0.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, low, low, high,
                        1.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, low, high, high,
                        1.0F, 0.0F, facing, packedLight);
                vertex(vertices, pose, high, high, high,
                        0.0F, 0.0F, facing, packedLight);
            }
            case EAST -> {
                vertex(vertices, pose, high, low, low,
                        0.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, high, low, high,
                        1.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, high, high, high,
                        1.0F, 0.0F, facing, packedLight);
                vertex(vertices, pose, high, high, low,
                        0.0F, 0.0F, facing, packedLight);
            }
            case WEST -> {
                vertex(vertices, pose, low, low, high,
                        0.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, low, low, low,
                        1.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, low, high, low,
                        1.0F, 0.0F, facing, packedLight);
                vertex(vertices, pose, low, high, high,
                        0.0F, 0.0F, facing, packedLight);
            }
            case UP -> {
                vertex(vertices, pose, low, high, high,
                        0.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, high, high, high,
                        1.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, high, high, low,
                        1.0F, 0.0F, facing, packedLight);
                vertex(vertices, pose, low, high, low,
                        0.0F, 0.0F, facing, packedLight);
            }
            case DOWN -> {
                vertex(vertices, pose, low, low, low,
                        0.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, high, low, low,
                        1.0F, 1.0F, facing, packedLight);
                vertex(vertices, pose, high, low, high,
                        1.0F, 0.0F, facing, packedLight);
                vertex(vertices, pose, low, low, high,
                        0.0F, 0.0F, facing, packedLight);
            }
        }
    }

    private static void vertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            Direction normal,
            int packedLight) {
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(
                        pose,
                        normal.getStepX(),
                        normal.getStepY(),
                        normal.getStepZ());
    }

    private record ZipperFace(ResourceLocation texture) {
    }
}
