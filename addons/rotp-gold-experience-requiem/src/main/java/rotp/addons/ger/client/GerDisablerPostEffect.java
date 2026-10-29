package rotp.addons.ger.client;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import rotp.core.api.client.render.AddonPostEffect;
import rotp.core.api.client.render.ClientRenderCompatibility;
import com.mojang.blaze3d.shaders.AbstractUniform;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.entity.GerFieldEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@OnlyIn(Dist.CLIENT)
public final class GerDisablerPostEffect {
    private static final float DEFAULT_CENTER = 0.5F;
    private static final float PROJECTION_EPSILON = 1.0E-4F;

    private static final AddonPostEffect EFFECT =
            AddonPostEffect.register(
                    GoldExperienceRequiemAddon.id(
                            "disabler_collapse"),
                    GoldExperienceRequiemAddon.id(
                            "disabler_collapse"),
                    RenderLevelStageEvent.Stage.AFTER_LEVEL,
                    GerDisablerPostEffect::updateUniforms);

    private static int activeEntityId = -1;
    private static long activeStartTick = Long.MIN_VALUE;
    @Nullable private static UUID activeOwnerUuid;
    private static float centerX = DEFAULT_CENTER;
    private static float centerY = DEFAULT_CENTER;
    private static float visibility;

    private GerDisablerPostEffect() {}

    public static void syncFromLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }

        long now = minecraft.level.getGameTime();
        GerFieldEntity latest = null;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof GerFieldEntity field)
                    || field.getKind() != GerFieldEntity.DISABLER
                    || field.isRemoved()
                    || field.getExpiresAt() <= now) {
                continue;
            }
            if (latest == null
                    || field.getCreatedAt() > latest.getCreatedAt()
                    || (field.getCreatedAt() == latest.getCreatedAt()
                            && field.getId() > latest.getId())) {
                latest = field;
            }
        }

        if (latest == null || latest.getOwnerUuid() == null) {
            reset();
            return;
        }

        boolean newTrigger =
                latest.getId() != activeEntityId
                        || latest.getCreatedAt() != activeStartTick;
        activeEntityId = latest.getId();
        activeStartTick = latest.getCreatedAt();
        activeOwnerUuid = latest.getOwnerUuid();
        if (newTrigger) {
            EFFECT.setActive(false);
        }
        EFFECT.setActive(true);
    }

    private static void updateUniforms(
            PostChain postChain,
            float frameDelta,
            ClientRenderCompatibility.Snapshot compatibility) {
        for (PostPass pass : postChain.passes) {
            AbstractUniform center =
                    pass.getEffect().safeGetUniform("Center");
            if (center != null) {
                center.set(centerX, centerY);
            }
            AbstractUniform visible =
                    pass.getEffect().safeGetUniform("Visibility");
            if (visible != null) {
                visible.set(visibility);
            }
        }
    }

    public static void captureScreenTarget(
            RenderLevelStageEvent event) {
        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || activeOwnerUuid == null) {
            hide();
            return;
        }

        if (activeOwnerUuid.equals(
                        minecraft.player.getUUID())
                && minecraft.options.getCameraType()
                        .isFirstPerson()) {
            centerX = DEFAULT_CENTER;
            centerY = DEFAULT_CENTER;
            visibility = 1.0F;
            return;
        }

        Player owner =
                minecraft.level.getPlayerByUUID(activeOwnerUuid);
        if (owner == null) {
            hide();
            return;
        }

        float partialTick = event.getPartialTick()
                .getGameTimeDeltaPartialTick(
                        !minecraft.level.tickRateManager()
                                .isEntityFrozen(owner));
        Vec3 target = new Vec3(
                Mth.lerp(
                        partialTick,
                        owner.xOld,
                        owner.getX()),
                Mth.lerp(
                        partialTick,
                        owner.yOld,
                        owner.getY())
                        + owner.getBbHeight() * 0.5D,
                Mth.lerp(
                        partialTick,
                        owner.zOld,
                        owner.getZ()));
        Vec3 relative = target.subtract(
                event.getCamera().getPosition());
        Matrix4f combined = new Matrix4f(
                event.getProjectionMatrix())
                .mul(event.getModelViewMatrix());
        Vector4f clip = combined.transform(new Vector4f(
                (float) relative.x,
                (float) relative.y,
                (float) relative.z,
                1.0F));
        float w = clip.w();
        if (!Float.isFinite(clip.x())
                || !Float.isFinite(clip.y())
                || !Float.isFinite(clip.z())
                || !Float.isFinite(w)
                || Math.abs(w) < PROJECTION_EPSILON
                || w <= 0.0F) {
            hide();
            return;
        }

        float inverseW = 1.0F / w;
        float ndcX = clip.x() * inverseW;
        float ndcY = clip.y() * inverseW;
        float ndcZ = clip.z() * inverseW;
        if (!Float.isFinite(ndcX)
                || !Float.isFinite(ndcY)
                || !Float.isFinite(ndcZ)
                || Math.abs(ndcX) >= 1.0F
                || Math.abs(ndcY) >= 1.0F
                || ndcZ <= -1.0F
                || ndcZ >= 1.0F) {
            hide();
            return;
        }

        centerX = ndcX * 0.5F + 0.5F;
        centerY = ndcY * 0.5F + 0.5F;
        visibility = 1.0F;
    }

    private static void hide() {
        centerX = DEFAULT_CENTER;
        centerY = DEFAULT_CENTER;
        visibility = 0.0F;
    }

    private static void reset() {
        activeEntityId = -1;
        activeStartTick = Long.MIN_VALUE;
        activeOwnerUuid = null;
        hide();
        EFFECT.setActive(false);
    }
}
