package rotp.addons.ultimate_lifeform.client;

import java.util.Map;
import java.util.WeakHashMap;

import rotp.core.api.client.render.EntityPostRenderContext;
import rotp.core.api.client.render.EntityPostRenderExtensions;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;
import rotp.core.impl.powers.pillarman.PillarmanDivineSandstormEntity;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAbility;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Render-thread receipts used by the external Ultimate acceptance scenes. */
final class UltimateLifeformClientRenderReceipts {
    private static final double TELEPORT_RENDER_DISTANCE_SQR = 64.0D;
    private static final Map<Player, Vec3> LAST_RENDERED_PLAYER_POSITIONS =
            new WeakHashMap<>();

    @SuppressWarnings("unused")
    private static final EntityPostRenderExtensions.Registration
            POST_RENDER_RECEIPTS = EntityPostRenderExtensions.register(
                    UltimateLifeformAddon.id(
                            "acceptance_post_render_receipts"),
                    1_040,
                    UltimateLifeformClientRenderReceipts
                            ::onEntityPostRender);

    private UltimateLifeformClientRenderReceipts() {}

    static void bootstrap() {
        // Class initialization installs the post-render observer.
    }

    private static void onEntityPostRender(
            EntityPostRenderContext context) {
        Entity entity = context.entity();
        if (entity instanceof LivingEntity living) {
            EntityActionInstance action =
                    LivingComponentAction.getCurEntityAction(living);
            if (action != null
                    && action.ability
                            instanceof UltimateLifeformAbility ability) {
                UltimateLifeformClientAcceptance.recordPlayerVisual(
                        living,
                        ClientReceipt.ACTIVE_ACTION_RENDER,
                        ability.spec().id);
            }
            if (living instanceof Player player
                    && PlayerPower.getPowerData(
                                    player,
                                    UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                            .isPresent()) {
                Vec3 currentPosition = player.position();
                Vec3 previousPosition = LAST_RENDERED_PLAYER_POSITIONS.put(
                        player, currentPosition);
                if (previousPosition != null
                        && previousPosition.distanceToSqr(currentPosition)
                                >= TELEPORT_RENDER_DISTANCE_SQR) {
                    UltimateLifeformClientAcceptance.recordPlayerVisual(
                            player,
                            ClientReceipt.TELEPORT_DESTINATION_RENDER,
                            UltimateLifeformSpec.ENDERMAN_TELEPORT.id);
                }
            }
        }
        if (entity instanceof PillarmanDivineSandstormEntity) {
            UltimateLifeformClientAcceptance.recordGlobalVisual(
                    entity,
                    ClientReceipt.SANDSTORM_ENTITY,
                    "pillarman_divine_sandstorm");
        }
        else if (entity instanceof LeavesGliderEntity) {
            UltimateLifeformClientAcceptance.recordGlobalVisual(
                    entity,
                    ClientReceipt.LEAVES_GLIDER_ENTITY,
                    "hamon_life_magnetism");
        }
    }
}
