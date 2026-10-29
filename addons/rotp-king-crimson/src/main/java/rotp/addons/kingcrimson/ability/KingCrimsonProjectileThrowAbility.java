package rotp.addons.kingcrimson.ability;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.customobjects.entity_projectile.MolotovEntity;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.mechanics.standarrow.StandArrowItem;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase2Spec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class KingCrimsonProjectileThrowAbility
        extends StandEntityAbility {
    private static final ActionAnimIdentifier THROW_ANIMATION =
            ActionAnimIdentifier.getOrCreate(
                    "kingcrimson_throw", false);
    // 1.16 threw the ghast fireball (FireballEntity, power 1).
    private static final int FIRE_CHARGE_EXPLOSION_POWER = 1;

    private final int maxProjectiles;

    public KingCrimsonProjectileThrowAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        this(abilityType, abilityId, 1);
    }

    protected KingCrimsonProjectileThrowAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            int maxProjectiles) {
        super(abilityType, abilityId, ProjectileThrowAction::new);
        this.maxProjectiles = maxProjectiles;
        partsRequired(StandPart.ARMS);
        // 1.16 StandEntityLightAttack: one arm is summoned for the throw, and the finisher meter holds during it.
        standAutoSummonMode(AutoSummonMode.MAIN_ARM);
        noFinisherBarDecay = true;
        setDefaultPhaseLength(
                ActionPhase.WINDUP,
                StandStatFormulas.getHeavyAttackWindup(12.0D, 0.0F));
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY,
                StandStatFormulas.getHeavyAttackRecovery(12.0D));
        cooldown(KingCrimsonPhase2Spec.PROJECTILE_COOLDOWN_TICKS);
    }

    // 1.16 KCConfig projectileThrowCooldown (the triple throw shares it)
    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        return KingCrimsonConfig.configuredCooldown(
                super.getCooldown(context, ticksHeld),
                KingCrimsonConfig.projectileCooldownTicks());
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        if (!(context instanceof StandPower)) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player)) {
            return negative("player_only");
        }
        return validateHeldItem(player);
    }

    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        return !standEntity.canAttackMelee()
                ? ConditionCheck.NEGATIVE
                : super.checkStandEntityConditions(standPower, standEntity);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        if (!level.isClientSide() && performer instanceof StandEntity stand) {
            action.phasesLength.put(
                    ActionPhase.WINDUP,
                    StandStatFormulas.getHeavyAttackWindup(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
            // Clients get the action with this length; PERFORM entry samples it again (1.16 getStandActionTicks).
            action.phasesLength.put(
                    ActionPhase.PERFORM,
                    StandStatFormulas.getLightAttackRecovery(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
            action.phasesLength.put(
                    ActionPhase.RECOVERY,
                    StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(),
                            stand.getLastHeavyFinisherValue()));
            if (powerUser != null) {
                sayShout(
                        powerUser,
                        KingCrimsonAddon.DOPPIO_THERE_YOU_ARE);
            }
        }
    }

    private static ConditionCheck validateHeldItem(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return negative("no_throwable_item_in_mainhand");
        }
        // 1.16 refused a Stand Arrow only when the throw starts; one swapped in later flies as a plain arrow.
        if (stack.getItem() instanceof StandArrowItem) {
            return negative("stand_arrow_not_throwable");
        }
        if (stack.is(ModItems.MOLOTOV.get())
                && !(player.getOffhandItem().getItem()
                        instanceof FlintAndSteelItem)) {
            return negative("molotov_flint_and_steel");
        }
        // 1.16: arrows always, anything else only when allowedProjectiles lists it
        return isThrowable(stack.getItem())
                        && (stack.getItem() instanceof ArrowItem
                                || KingCrimsonConfig.allowsProjectile(
                                        stack.getItem()))
                ? ConditionCheck.POSITIVE
                : negative("not_throwable");
    }

    private static ConditionCheck negative(String key) {
        return ConditionCheck.createNegative(
                Component.translatable(
                        "rotp_kingcrimson.message." + key));
    }

    private static boolean isThrowable(Item item) {
        return item instanceof ArrowItem
                || item == Items.EGG
                || item == Items.SNOWBALL
                || item == Items.ENDER_PEARL
                || item == Items.FIREWORK_ROCKET
                || item == Items.SPLASH_POTION
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.FIRE_CHARGE
                || item == ModItems.KNIFE.get()
                || item == ModItems.MOLOTOV.get();
    }

    @Nullable
    private static Projectile createProjectile(
            ServerLevel level,
            Player player,
            ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArrowItem arrowItem) {
            if (item == Items.SPECTRAL_ARROW
                    && KingCrimsonConfig.allowsProjectile(item)) {
                return arrowItem.createArrow(level, stack, player, null);
            }
            // 1.16 threw every other ArrowItem as a plain ArrowEntity whose only effects came from a tipped arrow.
            ItemStack pickup = stack.is(Items.ARROW) || stack.is(Items.TIPPED_ARROW)
                    ? stack.copyWithCount(1)
                    : new ItemStack(Items.ARROW);
            return new Arrow(level, player, pickup, null);
        }
        if (item == Items.EGG) {
            return new ThrownEgg(level, player);
        }
        if (item == Items.SNOWBALL) {
            return new Snowball(level, player);
        }
        if (item == Items.ENDER_PEARL) {
            return new ThrownEnderpearl(level, player);
        }
        if (item == Items.FIREWORK_ROCKET) {
            return new FireworkRocketEntity(
                    level,
                    stack,
                    player,
                    player.getX(),
                    player.getEyeY() - 0.1D,
                    player.getZ(),
                    true);
        }
        if (item == Items.SPLASH_POTION) {
            ThrownPotion potion = new ThrownPotion(level, player);
            potion.setItem(stack);
            return potion;
        }
        if (item == Items.EXPERIENCE_BOTTLE) {
            return new ThrownExperienceBottle(level, player);
        }
        if (item == Items.FIRE_CHARGE) {
            // Explodes on impact; griefing follows the mobGriefing event.
            return new LargeFireball(
                    level,
                    player,
                    player.getLookAngle(),
                    FIRE_CHARGE_EXPLOSION_POWER);
        }
        if (item == ModItems.KNIFE.get()) {
            return new KnifeEntity(level, player, stack);
        }
        if (item == ModItems.MOLOTOV.get()) {
            return new MolotovEntity(level, player, stack);
        }
        return null;
    }

    private static final class ProjectileThrowAction
            extends EntityActionInstance {
        private boolean playedSwingSound;

        private ProjectileThrowAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 StandEntityLightAttack.onTaskSet
        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            if (performer instanceof StandEntity stand && !stand.level().isClientSide()) {
                InteractionHand hand = stand.alternateHands();
                if (stand.isArmsOnlyMode() && hand == InteractionHand.OFF_HAND) {
                    stand.setArmsOnlyMode(true, true);
                }
            }
        }

        // 1.16 StandEntityLightAttack.standTickPerform: IHasStandPunch.playPunchSwingSound(task, PERFORM, 5, ...)
        @Override
        public void actionTick() {
            if (!playedSwingSound
                    && getPhase() == ActionPhase.PERFORM
                    && (getCurPhaseLength() < 5 || getPhaseTicksLeft() <= 5)
                    && performer instanceof StandEntity stand
                    && stand.level().isClientSide()) {
                playedSwingSound = true;
                if (ClientGlobals.canHearStand(stand)) {
                    stand.level().playLocalSound(
                            stand.getX(), stand.getEyeY(), stand.getZ(),
                            ClientsideSoundsHelper.withStandSkin(
                                    ModSoundEvents.STAND_PUNCH_SWING.get(), stand),
                            stand.getSoundSource(),
                            1.0F,
                            0.9F + stand.getRandom().nextFloat() * 0.2F,
                            false);
                }
            }
        }

        @Override
        public void setPhase(ActionPhase phase, int tick) {
            StandEntity stand = getPerformer() instanceof StandEntity body ? body : null;
            boolean refreshPerform = phase == ActionPhase.PERFORM
                    && phase != getPhase()
                    && stand != null
                    && !stand.level().isClientSide();
            boolean refreshRecovery = phase == ActionPhase.RECOVERY
                    && phase != getPhase()
                    && stand != null
                    && !stand.level().isClientSide();
            float previousLength = refreshPerform || refreshRecovery
                    ? phasesLength.getFloat(phase)
                    : 0.0F;
            if (refreshPerform) {
                phasesLength.put(
                        ActionPhase.PERFORM,
                        StandStatFormulas.getLightAttackRecovery(
                                stand.getAttackSpeed(), stand.getFinisherMeter()));
            }
            if (refreshRecovery) {
                phasesLength.put(ActionPhase.RECOVERY,
                        StandStatFormulas.getHeavyAttackRecovery(
                                stand.getAttackSpeed(),
                                stand.getLastHeavyFinisherValue()));
            }
            boolean lengthChanged = (refreshPerform || refreshRecovery)
                    && Float.compare(previousLength, phasesLength.getFloat(phase)) != 0;
            super.setPhase(phase, tick);
            // Clients already run the same length; a resync of an unchanged one would move them back in time.
            if (lengthChanged) {
                syncPhaseChanges();
            }
        }

        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return getPhase() == ActionPhase.RECOVERY;
        }

        @Override
        public ActionAnimIdentifier getEntityAnim() {
            return THROW_ANIMATION;
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPerformer() instanceof StandEntity)
                    || !(getPowerUser() instanceof Player player)
                    || !(ability
                            instanceof KingCrimsonProjectileThrowAbility
                            throwAbility)) {
                return;
            }

            StandPower standPower = StandPower.get(player);
            if (standPower == null || !standPower.hasPower()) {
                forceStop();
                return;
            }
            if (performThrow(serverLevel, player, throwAbility)) {
                // The donor task pays after a normal perform, even with no live payload.
                StandAbilityStamina.consume(
                        (Ability) throwAbility,
                        standPower,
                        KingCrimsonPhase2Spec.PROJECTILE_STAMINA,
                        false);
            }
        }

        private boolean performThrow(
                ServerLevel serverLevel,
                Player player,
                KingCrimsonProjectileThrowAbility throwAbility) {
            ItemStack heldStack = player.getMainHandItem();
            if (heldStack.isEmpty() || !isThrowable(heldStack.getItem())) {
                return true;
            }
            if (!(heldStack.getItem() instanceof ArrowItem)
                    && !KingCrimsonConfig.allowsProjectile(heldStack.getItem())) {
                return true;
            }

            int projectileCount = Math.min(
                    heldStack.getCount(),
                    throwAbility.maxProjectiles);
            if (heldStack.is(Items.FIRE_CHARGE)) {
                projectileCount = 1;
            }

            List<Projectile> projectiles =
                    new ArrayList<>(projectileCount);
            for (int index = 0;
                    index < projectileCount;
                    index++) {
                Projectile projectile = createProjectile(
                        serverLevel,
                        player,
                        heldStack.copyWithCount(1));
                if (projectile == null) {
                    forceStop();
                    return false;
                }
                projectiles.add(projectile);
            }

            Vec3 direction = player.getLookAngle();
            for (int index = 0;
                    index < projectiles.size();
                    index++) {
                Vec3 offset = projectileOffset(
                        index, projectiles.size());
                Projectile projectile = projectiles.get(index);
                projectile.setPos(
                        player.getX() + offset.x,
                        player.getEyeY() + offset.y,
                        player.getZ() + offset.z);
                projectile.shoot(
                        direction.x,
                        direction.y,
                        direction.z,
                        KingCrimsonPhase2Spec.PROJECTILE_SPEED,
                        KingCrimsonPhase2Spec.PROJECTILE_INACCURACY);
                serverLevel.addFreshEntity(projectile);
            }

            if (!player.getAbilities().instabuild) {
                heldStack.shrink(projectileCount);
            }
            return true;
        }

        private static Vec3 projectileOffset(
                int index,
                int totalProjectiles) {
            if (totalProjectiles <= 1 || index == 1) {
                return Vec3.ZERO;
            }
            double spread =
                    KingCrimsonPhase2Spec.TRIPLE_PROJECTILE_SPREAD;
            return index == 0
                    ? new Vec3(-spread, 0.0D, -spread)
                    : new Vec3(spread, 0.0D, spread);
        }
    }
}
