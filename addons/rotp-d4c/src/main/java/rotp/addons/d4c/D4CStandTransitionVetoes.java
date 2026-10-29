package rotp.addons.d4c;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.stand.StandPowerTransitions.MutationQuery;
import rotp.core.api.stand.StandPowerTransitions.TransitionQuery;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Love Train protection for server-authoritative Stand ownership changes.
 */
public final class D4CStandTransitionVetoes {
    public static final ResourceLocation OWNER =
            D4CAddon.id("love_train_transition_veto");

    private D4CStandTransitionVetoes() {}

    public static void register() {
        StandPowerTransitions.registerVeto(
                OWNER,
                D4CStandTransitionVetoes::vetoes);
        StandPowerTransitions.registerMutationVeto(
                OWNER,
                D4CStandTransitionVetoes::vetoesMutation);
    }

    static final String MSG_STAND_PROTECTED =
            "message.rotp_d4c.stand_protected_by_love_train";
    static final String MSG_STAND_CHANGE_PROTECTED =
            "message.rotp_d4c.stand_change_protected_by_love_train";

    static boolean vetoes(TransitionQuery query) {
        if (!isProtected(query.target())) {
            return false;
        }
        boolean vetoed = switch (query.operation()) {
            case CLEAR, EXTRACT, FULL_RESET -> true;
        };
        // 1.16 StandPowerMixin putOutStand/clear notice (action bar only)
        if (vetoed) {
            D4CPhase2BService.notify(query.target(), MSG_STAND_PROTECTED);
        }
        return vetoed;
    }

    static boolean vetoesMutation(MutationQuery query) {
        if (!isProtected(query.target())) {
            return false;
        }
        boolean vetoed = switch (query.operation()) {
            case INSERT, REPLACE -> true;
        };
        // 1.16 StandPowerMixin giveStandFromInstance notice
        if (vetoed) {
            D4CPhase2BService.notify(
                    query.target(), MSG_STAND_CHANGE_PROTECTED);
        }
        return vetoed;
    }

    private static boolean isProtected(LivingEntity target) {
        return target != null && target.hasEffect(D4CAddon.LOVE_TRAIN);
    }
}
