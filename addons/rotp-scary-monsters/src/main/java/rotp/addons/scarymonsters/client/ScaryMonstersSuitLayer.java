package rotp.addons.scarymonsters.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import rotp.core.client.ClientGlobals;
import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.ability.ScaryMonstersAbility;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * The 1.16 {@code WhiteAlbumLayer} of the Scary Monsters addon: the inflated dinosaur-skin suit worn by the
 * user while the Stand is summoned (suit texture), while blocking (block texture) and while charging the
 * dragon or in dragon form (dragon texture, fading in over 160 frames while the charge is held). Also draws
 * the suit's arm in first person like the donor did.
 */
public final class ScaryMonstersSuitLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
        implements FirstPersonModelLayer {
    private static final ResourceLocation SUIT =
            ScaryMonstersAddon.id("textures/entity/stand/scary_monsters.png");
    private static final ResourceLocation SUIT_SLIM =
            ScaryMonstersAddon.id("textures/entity/stand/scary_monsters_slim.png");
    private static final ResourceLocation BLOCK =
            ScaryMonstersAddon.id("textures/entity/stand/scary_monsters_block1.png");
    private static final ResourceLocation BLOCK_SLIM =
            ScaryMonstersAddon.id("textures/entity/stand/monsters_block_slim1.png");
    private static final ResourceLocation DRAGON =
            ScaryMonstersAddon.id("textures/entity/stand/dragon.png");
    private static final ResourceLocation DRAGON_SLIM =
            ScaryMonstersAddon.id("textures/entity/stand/dragon_slim.png");
    private static final int DRAGON_FADE_IN_FRAMES = 160;
    private static final int AUTO_GUARD_TICKS = 5;
    private static final Map<UUID, Integer> DRAGON_CHARGE_FRAMES = new HashMap<>();

    private final ScaryMonstersSuitModel<AbstractClientPlayer> suitModel;
    private final boolean slim;

    public ScaryMonstersSuitLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            ScaryMonstersSuitModel<AbstractClientPlayer> suitModel,
            boolean slim) {
        super(parent);
        this.suitModel = suitModel;
        this.slim = slim;
    }

    record Look(ResourceLocation texture, float alpha) {}

    /** The donor's condition set; {@code advanceCharge} counts the dragon fade-in only from third-person frames. */
    static Look resolve(LivingEntity entity, boolean slim, boolean advanceCharge) {
        if (!ClientGlobals.canSeeStands || entity.isSpectator()) {
            return null;
        }
        StandPower power = StandPower.get(entity);
        if (power == null || power.getPowerType() != ScaryMonstersAddon.SCARY_MONSTERS.get()) {
            return null;
        }
        UUID uuid = entity.getUUID();
        boolean summoned = power.isSummoned();
        // 1.16 read the power's held action. The Stand performs it here, so its action is read, while it is held.
        StandEntity stand = power.getSummonedStandEntity();
        EntityActionInstance action = stand != null ? LivingComponentAction.getCurEntityAction(stand) : null;
        boolean held = action != null && !action.isOver()
                && action.ability instanceof EntityActionAbility heldAbility && heldAbility.isActionHeld(action);
        // Only the held block shows the block suit. This Stand does not auto-guard (autoGuardsOnHit, as in 1.16);
        // the 5-tick filter stays as a guard against a core auto-guard.
        boolean blocking = held && action.ability instanceof StandEntityBlockAbility
                && action.getCurPhaseLength() > AUTO_GUARD_TICKS;
        boolean dragonHeld = held && action.ability instanceof ScaryMonstersAbility ability
                && ability.mode() == ScaryMonstersAbility.Mode.DRAGON;
        boolean dragonForm = ScaryMonstersAddon.state(entity).dragonForm();
        if (!(summoned || blocking || dragonHeld || dragonForm)) {
            DRAGON_CHARGE_FRAMES.remove(uuid);
            return null;
        }
        float alpha = 1.0F;
        if (dragonHeld) {
            if (advanceCharge) {
                DRAGON_CHARGE_FRAMES.merge(uuid, 1, Integer::sum);
            }
            alpha = Math.min(DRAGON_CHARGE_FRAMES.getOrDefault(uuid, 0) / (float) DRAGON_FADE_IN_FRAMES, 1.0F);
        }
        else {
            DRAGON_CHARGE_FRAMES.remove(uuid);
        }
        boolean dragonTexture = dragonHeld || dragonForm;
        ResourceLocation texture = dragonTexture
                ? (slim ? DRAGON_SLIM : DRAGON)
                : blocking ? (slim ? BLOCK_SLIM : BLOCK) : (slim ? SUIT_SLIM : SUIT);
        return new Look(texture, alpha);
    }

    private static MultiBufferSource withAlpha(MultiBufferSource buffer, float alpha) {
        return alpha < 1.0F ? AlphaMultiBufferSource.wrap(buffer, alpha) : buffer;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        Look look = resolve(player, slim, true);
        if (look == null) {
            return;
        }
        PlayerModel<AbstractClientPlayer> parent = getParentModel();
        suitModel.prepareMobModel(player, limbSwing, limbSwingAmount, partialTick);
        parent.copyPropertiesTo(suitModel);
        suitModel.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        suitModel.setAllVisible(true);
        suitModel.leftArm.visible = parent.leftArm.visible;
        suitModel.rightArm.visible = parent.rightArm.visible;
        VertexConsumer vertices = withAlpha(buffer, look.alpha())
                .getBuffer(RenderType.entityTranslucent(look.texture()));
        suitModel.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(player, 0.0F),
                0xFFFFFFFF);
    }

    @Override
    public void renderHandFirstPerson(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            LivingEntityRenderer<?, ?> entityRenderer,
            float partialTick) {
        Look look = resolve(entity, slim, false);
        if (look == null) {
            return;
        }
        suitModel.setAllVisible(true);
        FirstPersonModelLayer.setupForFirstPersonRender(cast(suitModel), entity, partialTick);
        VertexConsumer vertices = withAlpha(buffer, look.alpha())
                .getBuffer(RenderType.entityTranslucent(look.texture()));
        FirstPersonModelLayer.renderArmAndOuter(
                suitModel,
                side,
                poseStack,
                vertices,
                light,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                FirstPersonModelLayer.isRipplesAnimPlaying(suitModel));
    }

    @SuppressWarnings("unchecked")
    private static HumanoidModel<LivingEntity> cast(HumanoidModel<?> model) {
        return (HumanoidModel<LivingEntity>) model;
    }
}
