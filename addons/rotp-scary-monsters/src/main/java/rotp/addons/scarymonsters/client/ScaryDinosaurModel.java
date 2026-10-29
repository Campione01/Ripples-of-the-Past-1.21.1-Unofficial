package rotp.addons.scarymonsters.client;

import rotp.core.client.entityanim.AnimationLoader;
import rotp.core.client.entityanim.AnimationSet;
import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.entity.ScaryDinosaurEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The 1.16 dinosaur was a GeckoLib entity whose controllers played the donor clips: {@code walk}
 * while moving (sped up by the movement attribute relative to the donor's 0.3), {@code idle}
 * otherwise and {@code attack} for 15 ticks after each swing. The port renders the same geometry
 * through the core model loader, so the same clips are driven through the core animation system.
 */
public final class ScaryDinosaurModel extends EntityModel<ScaryDinosaurEntity> {
    /** {@code assets/scary_monsters/animations/dinosaur.animation.json} */
    public static final ResourceLocation DINOSAUR_ANIMS = ScaryMonstersAddon.id("dinosaur");
    /** {@code assets/scary_monsters/animations/scarymonsters.animation.json} (player-controlled variant) */
    public static final ResourceLocation CONTROLLED_ANIMS = ScaryMonstersAddon.id("scarymonsters");

    private static final ActionAnimIdentifier IDLE = ActionAnimIdentifier.getOrCreate("idle", true);
    private static final ActionAnimIdentifier WALK = ActionAnimIdentifier.getOrCreate("walk", false);
    private static final ActionAnimIdentifier ATTACK = ActionAnimIdentifier.getOrCreate("attack", false);
    private static final int ATTACK_ANIM_TICKS = 15;
    private static final float DONOR_MOVEMENT_SPEED = 0.3F;
    private static final float MOVING_THRESHOLD = 0.05F;
    private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;

    private final ModelPart root;
    private final ResourceLocation animSetPath;

    public ScaryDinosaurModel(ModelPart root) {
        this(root, DINOSAUR_ANIMS);
    }

    public ScaryDinosaurModel(ModelPart root, ResourceLocation animSetPath) {
        this.root = root;
        this.animSetPath = animSetPath;
        ((Model_1_21_2plus) (Object) this).jojo_ripples$initRoot(root);
    }

    @Override
    public void setupAnim(
            ScaryDinosaurEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float partialTick = ageInTicks - entity.tickCount;
        AnimationLoader loader = AnimationLoader.getInstance();
        AnimationSet anims = loader != null ? loader.getAnimSet(animSetPath) : null;
        if (anims != null) {
            RotpAnimDefinition anim = null;
            float seconds = 0.0F;

            if (entity.swinging
                    && entity.swingTime <= 0
                    && entity.tickCount - entity.clientAttackAnimStart >= ATTACK_ANIM_TICKS) {
                entity.clientAttackAnimStart = entity.tickCount;
            }
            float attackTicks = ageInTicks - entity.clientAttackAnimStart;
            if (attackTicks >= 0.0F && attackTicks < ATTACK_ANIM_TICKS) {
                anim = anims.getNamedAnim(ATTACK);
                seconds = attackTicks / 20.0F;
            }
            if (anim == null && entity.walkAnimation.speed(partialTick) > MOVING_THRESHOLD) {
                anim = anims.getNamedAnim(WALK);
                float speedMultiplier = (float) (entity.getAttributeValue(Attributes.MOVEMENT_SPEED)
                        / DONOR_MOVEMENT_SPEED);
                seconds = loopTime(anim, ageInTicks / 20.0F * speedMultiplier);
            }
            if (anim == null) {
                anim = anims.getNamedAnim(IDLE);
                seconds = loopTime(anim, ageInTicks / 20.0F);
            }
            if (anim != null) {
                AnimFramePose frame = anim.calcAnimPose(
                        AnimMolangVariables.extract(entity, partialTick), null, seconds, 1.0F);
                RotpAnimDefinition.animate(this, frame);
            }
        }
        root.yRot += netHeadYaw * DEG_TO_RAD;
    }

    private static float loopTime(RotpAnimDefinition anim, float seconds) {
        if (anim == null || anim.lengthInSeconds <= 0.0F) {
            return seconds;
        }
        return seconds % anim.lengthInSeconds;
    }

    @Override
    public void renderToBuffer(
            PoseStack stack,
            VertexConsumer buffer,
            int light,
            int overlay,
            int color) {
        root.render(stack, buffer, light, overlay, color);
    }
}
