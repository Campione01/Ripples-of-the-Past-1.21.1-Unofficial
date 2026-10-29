package rotp.addons.sexpistols.content.revolver.casing;

import java.util.List;

import rotp.core.init.ModDataAttachmentTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CasingWorldPhysics {
    private CasingWorldPhysics() {}

    public static CasingFlightState.CollisionResolver resolver(
            Level level) {
        return (x, y, z, width, height,
                requestedX, requestedY, requestedZ) -> {
            double halfWidth = width * 0.5D;
            AABB bounds = new AABB(
                    x - halfWidth,
                    y,
                    z - halfWidth,
                    x + halfWidth,
                    y + height,
                    z + halfWidth);
            Vec3 resolved = Entity.collideBoundingBox(
                    null,
                    new Vec3(
                            requestedX,
                            requestedY,
                            requestedZ),
                    bounds,
                    level,
                    List.of());
            return new CasingFlightState.ResolvedMotion(
                    resolved.x, resolved.y, resolved.z);
        };
    }

    public static boolean isTimeStopped(
            ServerLevel level, CasingFlightState state) {
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        return level.hasData(attachmentType)
                && level.getData(attachmentType).isTimeStopped(
                        new ChunkPos(BlockPos.containing(
                                state.x(), state.y(), state.z())));
    }
}
