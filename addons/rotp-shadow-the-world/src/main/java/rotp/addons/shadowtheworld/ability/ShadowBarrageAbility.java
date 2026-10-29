package rotp.addons.shadowtheworld.ability;

import javax.annotation.Nullable;

import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

// 1.16 SHADOWWORLDBarrage extends TheWorldBarrage: same press shout rule
// as core TheWorldBarrageAbility, with Shadow's lines.
public final class ShadowBarrageAbility
        extends StandEntityBarrageAbility {
    // server thread only: the press shout for the barrage it creates
    @Nullable private Holder<SoundEvent> shoutForNextAction;

    public ShadowBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ShadowBarrage::new);
        anim = ActionAnimIdentifier.getOrCreate("barrage", false);
    }

    @Override
    public HeldInput onKeyPress(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput,
            InputMethod inputMethod,
            float clickHoldResolveTime,
            BufferingState bufferingState) {
        if (level.isClientSide()) {
            return super.onKeyPress(level, user, extraClientInput,
                    inputMethod, clickHoldResolveTime, bufferingState);
        }
        // 1.16 getShout: wasActive is read before the press
        // auto-summons the Stand
        StandPower power = PowerClass.STAND.get(user);
        boolean standAlreadySummoned =
                power != null && power.getSummonedStandEntity() != null;
        Holder<SoundEvent> shout = null;
        if (!skipsShoutWhileSneaking(user)) {
            shout = standAlreadySummoned && isHighBloodVampire(user)
                    ? ShadowTheWorldAddon.SDDIO_WRY
                    : ShadowTheWorldAddon.SDDIO_MUDA_MUDA;
        }
        shoutForNextAction = shout;
        try {
            return super.onKeyPress(level, user, extraClientInput,
                    inputMethod, clickHoldResolveTime, bufferingState);
        }
        finally {
            shoutForNextAction = null;
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
        if (!level.isClientSide()
                && shoutForNextAction != null
                && action instanceof ShadowBarrage barrage) {
            barrage.shoutOnStart(powerUser, shoutForNextAction);
        }
        return action;
    }

    private static boolean isHighBloodVampire(LivingEntity user) {
        return PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM)
                .map(data -> data.isHighOnBlood(user))
                .orElse(false);
    }

    public static final class ShadowBarrage
            extends StandEntityBarrage {
        private boolean recoveryHandled;
        private boolean suppressStandCry;
        // server only: the press shout, said once the barrage is set
        @Nullable private LivingEntity shoutUser;
        @Nullable private Holder<SoundEvent> pendingShout;

        public ShadowBarrage(EntityActionType ability) {
            super(ability);
        }

        void shoutOnStart(LivingEntity user, Holder<SoundEvent> shout) {
            this.shoutUser = user;
            this.pendingShout = shout;
        }

        public boolean isStandCrySuppressed() {
            return suppressStandCry;
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance prevAction) {
            // a said line drops the Stand's MUDA cry; set before the
            // action is synced, so the client reads the flag
            if (pendingShout != null && shoutUser != null
                    && !level().isClientSide()
                    && JojoModUtil.sayVoiceLine(shoutUser, pendingShout)) {
                suppressStandCry = true;
            }
            pendingShout = null;
            shoutUser = null;
            super.onActionSet(prevAction);
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

        // 1.16 onTaskStopped: only the blink sound and the unstable
        // Shadow's forced unsummon, no end line
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.RECOVERY) {
                unsummonUnstableOnce();
            }
        }

        // 1.16 ran it on every stop but into itself (newAction != this);
        // a replaced or force-stopped barrage never reaches RECOVERY
        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            super.onActionCleared(newAction);
            if (newAction == null || newAction.ability != ability) {
                unsummonUnstableOnce();
            }
        }

        private void unsummonUnstableOnce() {
            if (recoveryHandled
                    || performer == null
                    || level().isClientSide()) {
                return;
            }
            recoveryHandled = true;
            LivingEntity user = getPowerUser();
            if (user != null) {
                ShadowAbilitySupport.unsummonIfUnstable(user);
            }
        }
    }
}
