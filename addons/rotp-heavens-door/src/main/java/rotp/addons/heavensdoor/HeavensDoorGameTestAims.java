package rotp.addons.heavensdoor;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/**
 * The game tests' stand-in for a player's client, which sends the server its aims (ClientsideAim): the vanilla pick of
 * its camera entity (GameRenderer.pick: a block by its outline within the player's block reach, an entity within its
 * entity reach, never the player's own Stand), as the user's aim, and under manual control the same pick from the Stand
 * as the Stand's aim. 1.16's mouse target was that pick (InputHandler.pickMouseTarget).
 */
final class HeavensDoorGameTestAims {
    private HeavensDoorGameTestAims() {}

    /** Turns the player's view to the target's eyes and sends what its crosshair meets. */
    static void lookAt(Player player, Entity target) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        send(player);
    }

    /** Sends what the player's crosshair meets along its current view. */
    static void send(Player player) {
        aim(player, pick(player, player));
    }

    /** Under manual control: turns the Stand to the target's eyes and sends the Stand's pick for both aims. */
    static void standLooksAt(Player player, StandEntity stand, Entity target) {
        stand.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        sendFromStand(player, stand);
    }

    static void sendFromStand(Player player, StandEntity stand) {
        ActionTarget pick = pick(stand, player);
        aim(stand, pick);
        // the core never keeps the player itself as the player's aim
        aim(player, pick.getEntity() == player ? ActionTarget.EMPTY : pick);
    }

    static void aim(LivingEntity aiming, ActionTarget target) {
        LivingComponentAction.getComponent(aiming);
        LivingComponentAction.getAim(aiming).setTarget(target);
    }

    // GameRenderer.pick of 1.21.1 with this camera and this player's reach
    static ActionTarget pick(Entity camera, Player player) {
        double blockRange = player.blockInteractionRange();
        double entityRange = player.entityInteractionRange();
        double range = Math.max(blockRange, entityRange);
        Vec3 eyes = camera.getEyePosition();
        HitResult block = camera.pick(range, 1.0F, false);
        double rangeSq = block.getType() != HitResult.Type.MISS
                ? block.getLocation().distanceToSqr(eyes)
                : range * range;
        Vec3 view = camera.getViewVector(1.0F);
        Vec3 end = eyes.add(view.scale(range));
        AABB box = camera.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0D);
        Entity ownStand = StandUtil.getSummonedStand(player);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(camera, eyes, end, box,
                candidate -> !candidate.isSpectator() && candidate.isPickable() && candidate != ownStand, rangeSq);
        if (entity != null && entity.getLocation().distanceToSqr(eyes) < rangeSq) {
            return entity.getLocation().closerThan(eyes, entityRange)
                    ? new ActionTarget(entity.getEntity())
                    : ActionTarget.EMPTY;
        }
        return block.getType() != HitResult.Type.MISS && block.getLocation().closerThan(eyes, blockRange)
                ? ActionTarget.fromVanilla(block)
                : ActionTarget.EMPTY;
    }
}
