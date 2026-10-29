package rotp.addons.heavensdoor.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.addons.heavensdoor.state.BookStateService;
import rotp.addons.heavensdoor.state.BookCommandSessionService;
import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.network.OpenBookSnapshotPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class OpenBookAbility extends Ability {
    public OpenBookAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 open_book did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            return;
        }
        // 1.16 took the mouse target, picked from the Stand under manual
        // control; OpenBook, a StandAction (TargetRequirement.ENTITY), had no
        // overrideVanillaMouseTarget, so a torch or grass on it stayed the
        // target, no entity
        LivingEntity target = BookStateService.ownedBookTarget(
                user, TearOutAPageAbility.aimedTarget(user));
        if (target == null) {
            message(user, "rotp_hd.message.no_owned_book");
            return;
        }
        // Owner rule (2026-10-19): the press summons Heaven's Door, as for
        // the Stand entity actions; the book session is bound to it. 1.16
        // OpenBook opened its screen with the Stand away.
        summonForPress(user);
        target = BookStateService.authorizedTarget(user, target);
        if (target == null) {
            message(user, "rotp_hd.message.no_owned_book");
            return;
        }

        if (user instanceof ServerPlayer player) {
            BookCommandSessionService.IssuedSession session =
                    BookCommandSessionService.issue(player, target);
            if (session == null) {
                message(user, "rotp_hd.message.no_owned_book");
                return;
            }
            net.neoforged.neoforge.network.PacketDistributor
                    .sendToPlayer(
                            player,
                            OpenBookSnapshotPayload.from(
                                    target,
                                    HeavensDoorAddon.bookState(target),
                                    session));
        }
    }

    private static void summonForPress(LivingEntity user) {
        StandPower power = StandPower.get(user);
        if (power != null
                && !power.isSummoned()
                && power.getPowerType() == HeavensDoorAddon.HEAVENS_DOOR.get()) {
            HeavensDoorAddon.HEAVENS_DOOR.get().summon(user, power);
        }
    }

    private static void message(
            LivingEntity user, String key) {
        if (user instanceof Player player) {
            player.displayClientMessage(
                    Component.translatable(key), true);
        }
    }
}
