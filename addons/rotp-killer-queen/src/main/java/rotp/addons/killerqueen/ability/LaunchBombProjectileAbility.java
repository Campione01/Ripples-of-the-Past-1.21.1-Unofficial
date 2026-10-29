package rotp.addons.killerqueen.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.powers.hamon.abilities.HamonSoapHelper;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.KillerQueenPhase1Spec;
import rotp.addons.killerqueen.entity.BubbleBombEntity;
import rotp.addons.killerqueen.entity.KillerQueenBombProjectile;
import rotp.addons.killerqueen.entity.SnowBombEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class LaunchBombProjectileAbility
        extends StandEntityAbility {
    public enum Kind {
        SNOW,
        BUBBLE
    }

    public static final int WINDUP_TICKS = 0;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 0;
    public static final int COOLDOWN_TICKS = 100;

    private final Kind kind;

    public LaunchBombProjectileAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Kind kind) {
        super(
                abilityType,
                abilityId,
                type -> new LaunchAction(type, kind));
        this.kind = kind;
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY, RECOVERY_TICKS);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(
                kind == Kind.SNOW
                        ? KillerQueenPhase1Spec
                                .SNOW_BOMB_RESOLVE_LEVEL
                        : 1);
        partsRequired(StandPart.ARMS);
        setIgnoresPerformerStun();
    }

    // 1.16 SnowBomb and HBubbleBomb (020 InitStands :92-93) were StandActions
    // of the user: no Stand task and no summon, the bomb left at the press
    // whatever the Stand was doing.
    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        return level.isClientSide() ? null : setOrBufferAction(level, user,
                user, inputMethod, extraClientInput, clickHoldResolveTime,
                bufferingState);
    }

    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        return user;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        // 1.16 StandAction.getPerformer, whose stun refused the press, was
        // the summoned Stand, else the user
        check = checkPerformerStun(context);
        if (!check.isPositive()) {
            return check;
        }
        // 1.16 SnowBomb: refused while Bites the Dust is armed (HBubbleBomb was not)
        if (kind == Kind.SNOW
                && BitesTheDustKitLock.locked(context)) {
            return ConditionCheck.NEGATIVE;
        }
        if (!(context.getUser() instanceof Player player)) {
            return ConditionCheck.NEGATIVE;
        }
        return kind == Kind.SNOW
                ? hasSnowball(player)
                        ? ConditionCheck.POSITIVE
                        : ConditionCheck.createNegative(
                                "killer_queen_no_snowball")
                : HamonSoapHelper.checkSoap(player);
    }

    // 1.16 SnowBomb.replaceAction: with soap at hand (HamonBubbleLauncher
    // soap, gloves with ammo) the snow bomb's slot is the bubble bomb
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        if (kind == Kind.SNOW
                && context != null
                && context.getUser() != null
                && HamonSoapHelper.checkSoap(context.getUser())
                        .isPositive()) {
            Ability bubble = context.getMoveset().getAbility(
                    KillerQueenAddon.BUBBLE_BOMB_NAME);
            if (bubble != null) {
                return bubble;
            }
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    private static boolean hasSnowball(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            if (player.getInventory().getItem(slot)
                    .is(Items.SNOWBALL)) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeSnowball(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack =
                    player.getInventory().getItem(slot);
            if (stack.is(Items.SNOWBALL)) {
                stack.shrink(1);
                player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    public static final class LaunchAction
            extends EntityActionInstance {
        private final Kind kind;

        public LaunchAction(
                EntityActionType ability, Kind kind) {
            super(ability);
            this.kind = kind;
        }

        // A StandAction took no time: no press, its own neither, waits for this one
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 PowerBaseImpl.onClickAction performed it at the press
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (level().isClientSide()
                    || isRestoringInputLifecycle()
                    || !(getPowerUser()
                            instanceof ServerPlayer user)) {
                return;
            }
            StandEntity stand = StandUtil.getSummonedStand(user);
            // 1.16 StandAction.onPerform pays the cost first and goes on
            // whatever the bar held
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    KillerQueenPhase1Spec.PROJECTILE_BOMB_STAMINA,
                    false);
            boolean materialConsumed = kind == Kind.SNOW
                    ? consumeSnowball(user)
                    : HamonSoapHelper.consumeSoap(user, 1)
                            != HamonSoapHelper.TookSoapFrom.NONE;
            if (!materialConsumed) {
                return;
            }

            KillerQueenBombProjectile projectile =
                    kind == Kind.SNOW
                            ? new SnowBombEntity(
                                    user, stand, level())
                            : new BubbleBombEntity(
                                    user, stand, level());
            projectile.setShootingPosOf(user);
            projectile.shootFromRotation(user, 0.25F, 1.0F);
            level().addFreshEntity(projectile);
            if (kind == Kind.SNOW) {
                level().playSound(
                        null,
                        user.blockPosition(),
                        SoundEvents.SNOWBALL_THROW,
                        user.getSoundSource(),
                        1.0F,
                        1.0F);
            }
        }
    }
}
