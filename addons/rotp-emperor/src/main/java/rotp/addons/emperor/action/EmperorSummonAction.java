package rotp.addons.emperor.action;

import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier.ActionAnimIdHandsided;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.entityaction.type.SpecialEntityActionType;

import rotp.addons.emperor.EmperorAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class EmperorSummonAction extends SpecialEntityActionType {
    public static final int MOTION_TICKS = 20;
    public static final int FADE_TICKS = 10;
    public static final int TOTAL_TICKS = MOTION_TICKS + FADE_TICKS;
    public static final String ANIMATION_SET_ID =
            "jojo_ripples:emperor_summon";
    public static final ResourceLocation ANIMATION_SET =
            ResourceLocation.parse(ANIMATION_SET_ID);

    private final ActionAnimIdHandsided handsidedAnimation;

    public EmperorSummonAction(ResourceLocation id) {
        super(null, id);
        animSet = ANIMATION_SET;
        anim = ActionAnimIdentifier.getOrCreate(
                "emperor_summon", false);
        handsidedAnimation = new ActionAnimIdHandsided(anim);
    }

    @Override
    public EntityActionInstance createActionObj() {
        return new SummonInstance(this);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return handsidedAnimation.get(
                action.getPerformer().getMainArm());
    }

    public static void play(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        SummonInstance action = new SummonInstance(
                EmperorAddon.EMPEROR_SUMMON_ACTION.get());
        action.setStartingPhase();
        LivingComponentAction.getComponent(player).setAction(
                action, player, SyncType.TRACKING_AND_SELF);
    }

    public static final class SummonInstance
            extends EntityActionInstance {
        private SummonInstance(EntityActionType ability) {
            super(ability);
            phasesLength.put(ActionPhase.PERFORM, TOTAL_TICKS);
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }
    }
}
