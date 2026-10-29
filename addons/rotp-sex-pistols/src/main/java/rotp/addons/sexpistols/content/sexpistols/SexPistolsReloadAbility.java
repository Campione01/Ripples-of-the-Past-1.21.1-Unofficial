package rotp.addons.sexpistols.content.sexpistols;

import java.util.List;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.revolver.state.RevolverMemberAttachments;
import rotp.addons.sexpistols.content.revolver.state.RevolverReload;
import rotp.addons.sexpistols.content.revolver.state.RevolverState;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsSounds;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SexPistolsReloadAbility extends Ability {
    public static final float QUICK_RELOAD_STAMINA_PER_BULLET =
            5.0F;
    public static final int STAND_RELOAD_COOLDOWN_TICKS = 0;
    public static final int QUICK_RELOAD_COOLDOWN_TICKS = 40;
    public static final int QUICK_RELOAD_RESOLVE_LEVEL = 1;
    private static final float COOLDOWN_FAIL_CLICK_VOLUME = 0.35F;
    private static final float NOTHING_LOADED_FAIL_CLICK_VOLUME = 0.45F;

    private final boolean quickReload;

    public SexPistolsReloadAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            boolean quickReload) {
        super(abilityType, abilityId);
        this.quickReload = quickReload;
        cooldown(quickReload
                ? QUICK_RELOAD_COOLDOWN_TICKS
                : STAND_RELOAD_COOLDOWN_TICKS);
        resolveLevelToUnlock(
                quickReload
                        ? QUICK_RELOAD_RESOLVE_LEVEL
                        : 0);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck parent =
                super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        // 1.16 sex_pistols actions did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        if (!(context instanceof StandPower standPower)
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 SexPistolsStandReloadAction asked only for the revolver: the
        // members load into it with the Stand put away.
        LivingEntity owner = context.getUser();
        if (!(owner instanceof Player)) {
            return ConditionCheck.NEGATIVE;
        }
        return owner.getMainHandItem().getItem()
                        instanceof RevolverItem
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "sex_pistols_revolver_required");
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return quickReload
                ? "sex_pistols_quick_reload"
                : "sex_pistols_stand_reload";
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity owner,
            FriendlyByteBuf extraClientInput) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(owner instanceof Player player)) {
            return;
        }
        StandPower standPower = StandPower.get(owner);
        ItemStack revolver = owner.getMainHandItem();
        if (standPower == null
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()
                || !(revolver.getItem()
                        instanceof RevolverItem)) {
            return;
        }
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        if (!RevolverItem.openForStandReload(
                serverLevel, player, revolver, true, true)) {
            fail(
                    serverLevel,
                    owner,
                    "sex_pistols_revolver_unavailable",
                    COOLDOWN_FAIL_CLICK_VOLUME);
            return;
        }

        boolean changed = quickReload
                ? quickReload(
                        player,
                        standPower,
                        coordinator,
                        revolver)
                : attachSelected(
                        serverLevel,
                        owner,
                        coordinator,
                        revolver);
        if (!changed) {
            fail(
                    serverLevel,
                    owner,
                    "sex_pistols_reload_unavailable",
                    NOTHING_LOADED_FAIL_CLICK_VOLUME);
            return;
        }

        RevolverItem.applyReloadCooldown(owner, revolver);
        setCooldownOnUse(standPower);
        level.playSound(
                null,
                owner.blockPosition(),
                SexPistolsSounds.REVOLVER_SPEEDLOADER.get(),
                owner.getSoundSource(),
                0.65F,
                quickReload ? 1.35F : 1.15F);
        JojoModUtil.sayVoiceLine(
                owner,
                SexPistolsSounds.MISTA_STAND_RELOAD.get(),
                SexPistolsSounds.MISTA_STAND_RELOAD_DELAY);
    }

    private boolean attachSelected(
            ServerLevel level,
            LivingEntity owner,
            SexPistolsCoordinator coordinator,
            ItemStack revolver) {
        RevolverState state = RevolverItem.state(revolver);
        int chamber = RevolverReload.selectedLiveChamber(state);
        List<Integer> available =
                coordinator.availableMemberSlots(level);
        if (chamber < 0 || available.isEmpty()) {
            return false;
        }
        int memberSlot = available.get(0);
        if (!coordinator.markMemberLoaded(
                level, owner, memberSlot)) {
            return false;
        }
        RevolverMemberAttachments.attach(
                revolver, chamber, memberSlot);
        return true;
    }

    private boolean quickReload(
            Player player,
            StandPower standPower,
            SexPistolsCoordinator coordinator,
            ItemStack revolver) {
        int affordable = RevolverState.CHAMBER_COUNT;
        while (affordable > 0
                && !StandAbilityStamina.canPay(
                        standPower,
                        affordable
                                * QUICK_RELOAD_STAMINA_PER_BULLET)) {
            affordable--;
        }
        int loadedBullets =
                RevolverItem.loadAvailableAmmoIntoEmptyChambers(
                        player, revolver, affordable, true);
        if (loadedBullets > 0) {
            StandAbilityStamina.consume(
                    this,
                    standPower,
                    loadedBullets
                            * QUICK_RELOAD_STAMINA_PER_BULLET,
                    false);
            // 1.16 consumeQuickReloadStamina paused regen like every other payment.
            SexPistolsStamina.pauseRecovery(player);
        }

        boolean attachedAny = false;
        if (player.level() instanceof ServerLevel serverLevel) {
            List<Integer> available =
                    coordinator.availableMemberSlots(serverLevel);
            for (int memberSlot : available) {
                RevolverState state = RevolverItem.state(revolver);
                int chamber =
                        RevolverMemberAttachments
                                .leastLoadedLiveChamber(
                                        revolver, state);
                if (chamber < 0
                        || !coordinator.markMemberLoaded(
                                serverLevel,
                                player,
                                memberSlot)) {
                    continue;
                }
                RevolverMemberAttachments.attach(
                        revolver, chamber, memberSlot);
                attachedAny = true;
            }
        }
        return loadedBullets > 0 || attachedAny;
    }

    // 1.16 answered a failed reload with a vanilla click and no text.
    private void fail(
            ServerLevel level,
            LivingEntity owner,
            String reason,
            float clickVolume) {
        level.playSound(
                null,
                owner.blockPosition(),
                SoundEvents.DISPENSER_FAIL,
                owner.getSoundSource(),
                clickVolume,
                quickReload ? 1.2F : 1.0F);
        ConditionCheck.sendActionFailedMessage(
                this,
                ConditionCheck.createNegative(reason),
                owner);
    }
}
