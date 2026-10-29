package rotp.addons.sexpistols.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.standskin.StandSkin;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class SexPistolsRenderer extends StandEntityRenderer<
        SexPistolsEntity,
        SexPistolsRenderState,
        StandEntityModel<SexPistolsEntity, SexPistolsRenderState>> {
    private static final int PREVIEW_MEMBER_NUMBER = 1;
    private static final float DONOR_MODEL_SCALE = 0.7F;

    public SexPistolsRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SexPistolsRenderState createRenderState() {
        return new SexPistolsRenderState();
    }

    @Override
    protected void scale(
            SexPistolsEntity entity,
            PoseStack poseStack,
            float partialTick) {
        poseStack.scale(
                DONOR_MODEL_SCALE,
                DONOR_MODEL_SCALE,
                DONOR_MODEL_SCALE);
    }

    @Override
    public void extractSkinMenuRenderState(
            SexPistolsRenderState renderState,
            StandSkin skin,
            ResourceLocation standId,
            float ticks,
            int tint,
            MenuType menuType) {
        super.extractSkinMenuRenderState(
                renderState,
                skin,
                standId,
                ticks,
                tint,
                menuType);
        renderState.memberNumber = PREVIEW_MEMBER_NUMBER;
    }

    @Override
    public void extractRenderState(
            SexPistolsEntity entity,
            SexPistolsRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.memberNumber = entity.getMemberNumber();
        renderState.kickTicks = entity.getKickTicks();
        renderState.kickVariant = entity.getKickVariant();
        renderState.eatingTicks = entity.getEatingTicks();
        renderState.summonTicks = entity.getSummonTicks();
        renderState.summonVariant = entity.getSummonVariant();
        renderState.foodBegging = entity.isFoodBegging();

        if (renderState.kickTicks > 0) {
            applyNamedAnimation(
                    entity,
                    renderState,
                    partialTick,
                    "kick" + renderState.kickVariant,
                    SexPistolsEntity.KICK_DURATION_TICKS,
                    renderState.kickTicks);
        }
        else if (renderState.eatingTicks > 0) {
            applyNamedAnimation(
                    entity,
                    renderState,
                    partialTick,
                    "pick_item",
                    SexPistolsEntity.EAT_ANIMATION_TICKS,
                    renderState.eatingTicks);
        }
        else if (renderState.summonTicks > 0) {
            applyNamedAnimation(
                    entity,
                    renderState,
                    partialTick,
                    summonAnimationName(
                            renderState.summonVariant),
                    SexPistolsEntity.SUMMON_ANIMATION_TICKS,
                    renderState.summonTicks);
        }
    }

    private static void applyNamedAnimation(
            SexPistolsEntity entity,
            SexPistolsRenderState renderState,
            float partialTick,
            String animationName,
            int durationTicks,
            int remainingTicks) {
        if (renderState.skin == null) {
            return;
        }
        ActionAnimIdentifier animationId =
                ActionAnimIdentifier.getOrCreate(
                        animationName, false);
        RotpAnimDefinition animation =
                renderState.skin.getStandAnimation(
                        animations -> animations.getNamedAnim(
                                animationId));
        if (animation == null) {
            return;
        }
        float elapsedTicks = durationTicks
                - remainingTicks + partialTick;
        renderState.action.pose = animation
                .calcAnimPose(
                        AnimMolangVariables.extract(
                                entity, partialTick),
                        null,
                        Math.max(0.0F, elapsedTicks) / 20.0F,
                        1.0F)
                .deepCopy();
        renderState.action.animId = animationId;
    }

    private static String summonAnimationName(int variant) {
        return switch (variant) {
            case 5 -> "summon5_ge1";
            case 6 -> "summon6_ge2";
            case 7 -> "summon7_ge3";
            case 8 -> "summon8_tw1";
            case 9 -> "summon9_sp1";
            case 10 -> "summon10_cd1";
            case 11 -> "summon11_cd2";
            case 12 -> "summon12_giogio";
            case 14 -> "summon14_kick";
            case 15 -> "summon15_jotaro";
            default -> "summon" + variant;
        };
    }

    @Override
    public ResourceLocation getTextureLocation(
            SexPistolsRenderState renderState) {
        int memberNumber = validMemberNumber(
                renderState.memberNumber);
        ResourceLocation memberTexture =
                SexPistolsIds.id(
                        "textures/entity/sex_pistols_no_"
                                + memberNumber
                                + ".png");
        return renderState.skin != null
                ? renderState.skin.getTexture(
                        memberTexture, MISSING_TEXTURE)
                : MISSING_TEXTURE;
    }

    private static int validMemberNumber(int memberNumber) {
        return switch (memberNumber) {
            case 1, 2, 3, 5, 6, 7 -> memberNumber;
            default -> PREVIEW_MEMBER_NUMBER;
        };
    }
}
