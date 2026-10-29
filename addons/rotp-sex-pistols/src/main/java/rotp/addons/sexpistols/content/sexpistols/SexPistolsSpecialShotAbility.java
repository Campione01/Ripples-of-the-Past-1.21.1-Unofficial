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

import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.revolver.state.RevolverMemberAttachments;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers.ShotType;
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

public final class SexPistolsSpecialShotAbility extends Ability {
    public static final int PIERCING_COOLDOWN_TICKS = 160;
    public static final int SPLITTING_COOLDOWN_TICKS = 240;
    public static final int ENCIRCLEMENT_COOLDOWN_TICKS = 240;
    private static final float FAIL_CLICK_VOLUME = 0.45F;
    private static final float FAIL_CLICK_PITCH = 1.0F;

    private final ShotType shotType;

    public SexPistolsSpecialShotAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            ShotType shotType) {
        super(abilityType, abilityId);
        this.shotType = shotType;
        cooldown(cooldownTicks(shotType), 0, 0.5F);
        resolveLevelToUnlock(resolveLevel(shotType));
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
        // 1.16 special shots asked only for the revolver; the swarm is
        // looked up when the shot is primed, with the Stand out or put away.
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
        return shotType.spriteName();
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
        if (!coordinator.isSwarmEstablished()) {
            fail(owner, "sex_pistols_not_summoned");
            return;
        }

        int retainedMask =
                RevolverMemberAttachments.retainMembers(
                        revolver,
                        coordinator.loadedMemberMask());
        List<Integer> available =
                coordinator.availableMemberSlots(serverLevel);
        if (retainedMask == 0 && available.isEmpty()) {
            fail(owner, "sex_pistols_special_members_required");
            return;
        }
        // Donor 025 special shots only switch the cylinder open and prime
        // the chamber already selected; opening never moves the selection
        // and, unlike Stand Reload, ejects no spent casing.
        if (!RevolverItem.openForStandReload(
                serverLevel, player, revolver, true, false)) {
            fail(owner, "sex_pistols_revolver_unavailable");
            return;
        }

        int chamber =
                RevolverItem.state(revolver).selectedChamber();
        if (!RevolverItem.loadSelectedChamberIfEmpty(
                player, revolver)) {
            fail(owner, "sex_pistols_special_ammo_required");
            return;
        }

        int memberMask = retainedMask;
        for (int slot : available) {
            if (coordinator.markMemberLoaded(
                    serverLevel, owner, slot)) {
                memberMask |= 1 << slot;
            }
        }
        if (memberMask == 0) {
            fail(owner, "sex_pistols_special_members_required");
            return;
        }

        RevolverMemberAttachments.clear(revolver);
        for (int slot = 0;
                slot < SexPistolsCoordinator.MEMBER_COUNT;
                slot++) {
            if ((memberMask & 1 << slot) != 0) {
                RevolverMemberAttachments.attach(
                        revolver, chamber, slot);
            }
        }
        RevolverSpecialChambers.prime(
                revolver, chamber, shotType);
        RevolverItem.applyReloadCooldown(owner, revolver);
        setCooldownOnUse(standPower);
        level.playSound(
                null,
                owner.getX(),
                owner.getY(),
                owner.getZ(),
                SexPistolsSounds.REVOLVER_SPEEDLOADER.get(),
                owner.getSoundSource(),
                0.65F,
                1.15F);
        // Donor 025 special shots end with sayStandReload once primed.
        JojoModUtil.sayVoiceLine(
                owner,
                SexPistolsSounds.MISTA_STAND_RELOAD.get(),
                SexPistolsSounds.MISTA_STAND_RELOAD_DELAY);
    }

    // 1.16 answered every refused special shot with this click and no text.
    private void fail(
            LivingEntity owner, String reason) {
        owner.level().playSound(
                null,
                owner.blockPosition(),
                SoundEvents.DISPENSER_FAIL,
                owner.getSoundSource(),
                FAIL_CLICK_VOLUME,
                FAIL_CLICK_PITCH);
        ConditionCheck.sendActionFailedMessage(
                this,
                ConditionCheck.createNegative(reason),
                owner);
    }

    private static int cooldownTicks(ShotType shotType) {
        return switch (shotType) {
            case PIERCING -> PIERCING_COOLDOWN_TICKS;
            case SPLITTING -> SPLITTING_COOLDOWN_TICKS;
            case ENCIRCLEMENT -> ENCIRCLEMENT_COOLDOWN_TICKS;
            case NONE -> 0;
        };
    }

    private static int resolveLevel(ShotType shotType) {
        return switch (shotType) {
            case PIERCING -> 2;
            case SPLITTING -> 3;
            case ENCIRCLEMENT -> 4;
            case NONE -> 0;
        };
    }
}
