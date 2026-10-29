package rotp.addons.diegosworld.ability;

import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class DiegoBarrageAbility
        extends StandEntityBarrageAbility {
    private boolean suppressStandCryForNextAction;

    public DiegoBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DiegoBarrage::new);
    }

    @Override
    public HeldInput onKeyPress(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput,
            InputMethod inputMethod,
            float clickHoldResolveTime,
            BufferingState bufferingState) {
        StandPower standPower = PowerClass.STAND.get(user);
        boolean canPlayWry =
                standPower != null
                        && standPower.getSummonedStandEntity() != null
                        && isHighBloodVampire(user);
        boolean voiceLineTriggered = false;
        if (canPlayWry && !level.isClientSide()) {
            // donor getShout: skipped while the user sneaks
            voiceLineTriggered = sayShout(
                    user, DiegosTheWorldAddon.DIEGO_WRY);
        }
        suppressStandCryForNextAction = voiceLineTriggered;
        try {
            return super.onKeyPress(
                    level,
                    user,
                    extraClientInput,
                    inputMethod,
                    clickHoldResolveTime,
                    bufferingState);
        }
        finally {
            suppressStandCryForNextAction = false;
        }
    }

    @Override
    public EntityActionInstance initActionOnAbilityUse(
            Level level,
            LivingEntity powerUser,
            LivingEntity performer,
            FriendlyByteBuf extraInput) {
        EntityActionInstance action = super.initActionOnAbilityUse(
                level, powerUser, performer, extraInput);
        if (suppressStandCryForNextAction
                && action instanceof DiegoBarrage barrage) {
            barrage.suppressStandCry();
        }
        return action;
    }

    private static boolean isHighBloodVampire(
            LivingEntity user) {
        return PlayerPower.getPowerData(
                        user, ModPlayerPowers.VAMPIRISM)
                .map(data -> data.isHighOnBlood(user))
                .orElse(false);
    }

    public static final class DiegoBarrage
            extends StandEntityBarrage {
        private boolean suppressStandCry;

        public DiegoBarrage(EntityActionType ability) {
            super(ability);
        }

        public void suppressStandCry() {
            suppressStandCry = true;
        }

        @Override
        protected boolean shouldPlayBarrageCry(
                Level level, StandEntity stand) {
            return !suppressStandCry
                    && super.shouldPlayBarrageCry(level, stand);
        }

        @Override
        public void toBuf(FriendlyByteBuf buf) {
            super.toBuf(buf);
            buf.writeBoolean(suppressStandCry);
        }

        @Override
        public void fromBuf(FriendlyByteBuf buf) {
            super.fromBuf(buf);
            suppressStandCry = buf.readBoolean();
        }
    }
}
