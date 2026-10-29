package rotp.addons.stand_bearing_creatures.event;

import rotp.core.JojoModConfig;
import rotp.core.init.ModEntityTypes;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.command.MobPowerCommands;
import rotp.addons.stand_bearing_creatures.config.MobPowerConfig;
import rotp.addons.stand_bearing_creatures.entity.goal.ManualHeldInput;
import rotp.addons.stand_bearing_creatures.entity.goal.PlayerPowerMobGoal;
import rotp.addons.stand_bearing_creatures.entity.goal.StandPowerMobGoal;
import rotp.addons.stand_bearing_creatures.mechanics.combo.ComboManager;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;
import org.slf4j.Logger;

@EventBusSubscriber(modid = MobsWithPowersAddon.MOD_ID)
public final class MobPowerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation BABY_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(
                    MobsWithPowersAddon.MOD_ID,
                    "baby_stand_damage");
    private static final ResourceLocation BABY_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(
                    MobsWithPowersAddon.MOD_ID,
                    "baby_stand_speed");
    private static final ResourceLocation BABY_REACH_ID =
            ResourceLocation.fromNamespaceAndPath(
                    MobsWithPowersAddon.MOD_ID,
                    "baby_stand_reach");
    private static boolean warnedMissingAdvancement;

    @SubscribeEvent
    public static void onAddReloadListeners(
            AddReloadListenerEvent event) {
        event.addListener(ComboManager.standReloadListener());
        event.addListener(ComboManager.playerPowerReloadListener());
    }

    @SubscribeEvent
    public static void onRegisterCommands(
            RegisterCommandsEvent event) {
        MobPowerCommands.register(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(
            EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof StandEntity stand) {
            if (MobPowerUtil.isStandEntityPowerEligible(stand)) {
                MobPowerUtil.attachPowers((LivingEntity) stand);
            }
            return;
        }
        if (!MobPowerUtil.isEligibleMob(event.getEntity())) {
            return;
        }
        Mob mob = (Mob) event.getEntity();
        MobPowerUtil.attachPowers(mob);
        addPowerGoals(mob);

        if (mob.getType() == ModEntityTypes.RPS_KID.get()) {
            MobPowerUtil.grantStand(
                    mob,
                    rotp.core.init.power.ModStands
                            .BOY_II_MAN
                            .get(),
                    true);
            mob.getPersistentData().putBoolean(
                    MobPowerUtil.PROCESSED_TAG,
                    true);
            return;
        }

        CompoundTag data = mob.getPersistentData();
        if (data.getBoolean(MobPowerUtil.PROCESSED_TAG)
                || MobPowerUtil.isGoldExperienceCreated(mob)) {
            return;
        }
        if (!MobPowerUtil.advancementGateOpen(
                event.getLevel().getServer())) {
            if (!warnedMissingAdvancement
                    && event.getLevel().getServer()
                            .getAdvancements()
                            .get(ResourceLocation.fromNamespaceAndPath(
                                    "jojo_ripples",
                                    "jojo/summon_stand"))
                            == null) {
                warnedMissingAdvancement = true;
                LOGGER.warn(
                        "The jojo_ripples:jojo/summon_stand advancement "
                                + "does not exist; configured mob Stand "
                                + "spawn rolls remain gated.");
            }
            return;
        }

        float chance = MobPowerConfig
                .PERCENTAGE_CHANCE_TO_GETTING_A_STAND_FOR_MOB
                .get()
                / 100.0F;
        if (mob.getRandom().nextFloat() < chance) {
            if (MobPowerUtil.convertBabyVillager(mob) == null) {
                StandType stand = MobPowerUtil.randomStand(mob);
                if (MobPowerUtil.grantStand(mob, stand, false)) {
                    StandPower power = StandPower.get(mob);
                    if (power != null) {
                        power.setResolveLevel(
                                mob.getRandom().nextInt(4));
                    }
                }
            }
        }
        data.putBoolean(MobPowerUtil.PROCESSED_TAG, true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityTickPost(
            EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living
                && !living.level().isClientSide()) {
            ManualHeldInput.tick(living);
        }
        if (event.getEntity() instanceof StandEntity stand
                && !stand.level().isClientSide()) {
            updateMobOwnedStand(stand);
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)
                || mob.level().isClientSide()
                || !MobPowerUtil.isEligibleMob(mob)) {
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        // The client runs die() for a mob too (entity event 3); cleanup is
        // server state, as in onEntityLeave.
        if (!event.getEntity().level().isClientSide()
                && event.getEntity() instanceof Mob mob
                && MobPowerUtil.isEligibleMob(mob)) {
            MobPowerUtil.cleanup(mob);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || mob.level().isClientSide()
                || !MobPowerUtil.isEligibleMob(mob)
                || !MobPowerConfig.DROP_STAND_DISC_FROM_MOBS.get()) {
            return;
        }
        JojoModConfig.Common coreConfig =
                JojoModConfig.getCommonConfigInstance(false);
        if (coreConfig.keepStandOnDeath.get()
                || coreConfig.dropStandDisc.get()) {
            return;
        }
        StandPower power = StandPower.get(mob);
        if (power == null || !power.hasPower()) {
            return;
        }
        power.getStandInstance().ifPresent(stand -> {
            ItemStack disc = StandDiscItem.withStand(stand);
            event.getDrops().add(new ItemEntity(
                    mob.level(),
                    mob.getX(),
                    mob.getY(),
                    mob.getZ(),
                    disc));
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityLeave(
            EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof Mob mob
                && MobPowerUtil.isEligibleMob(mob)) {
            MobPowerUtil.cleanup(mob);
        }
    }

    private static void addPowerGoals(Mob mob) {
        boolean hasStandGoal = mob.goalSelector
                .getAvailableGoals()
                .stream()
                .anyMatch(wrapped ->
                        wrapped.getGoal()
                                instanceof StandPowerMobGoal);
        if (!hasStandGoal) {
            mob.goalSelector.addGoal(
                    1,
                    new StandPowerMobGoal(mob));
        }
        boolean hasPlayerPowerGoal = mob.goalSelector
                .getAvailableGoals()
                .stream()
                .anyMatch(wrapped ->
                        wrapped.getGoal()
                                instanceof PlayerPowerMobGoal);
        if (!hasPlayerPowerGoal) {
            mob.goalSelector.addGoal(
                    1,
                    new PlayerPowerMobGoal(mob));
        }
        if (mob instanceof PathfinderMob pathfinder
                && mob.targetSelector
                        .getAvailableGoals()
                        .stream()
                        .noneMatch(wrapped ->
                                wrapped.getGoal()
                                        instanceof HurtByTargetGoal)) {
            mob.targetSelector.addGoal(
                    1,
                    new HurtByTargetGoal(pathfinder));
        }
    }

    private static void updateMobOwnedStand(StandEntity stand) {
        if (!(stand.getUser() instanceof Mob owner)
                || !MobPowerUtil.isEligibleMob(owner)) {
            removeBabyModifiers(stand);
            return;
        }

        boolean baby = owner.isBaby()
                || owner.getBbHeight() < 1.5F;
        if (!baby) {
            removeBabyModifiers(stand);
            return;
        }
        double factor = Math.max(
                0.1D,
                owner.getBbHeight() / 1.5D);
        updateModifier(
                stand.getAttribute(Attributes.ATTACK_DAMAGE),
                BABY_DAMAGE_ID,
                -2.0D * factor);
        updateModifier(
                stand.getAttribute(Attributes.ATTACK_SPEED),
                BABY_SPEED_ID,
                -2.5D * factor);
        updateModifier(
                stand.getAttribute(
                        Attributes.ENTITY_INTERACTION_RANGE),
                BABY_REACH_ID,
                -2.0D * factor);
    }

    private static void updateModifier(
            AttributeInstance attribute,
            ResourceLocation id,
            double amount) {
        if (attribute != null) {
            attribute.addOrUpdateTransientModifier(
                    new AttributeModifier(
                            id,
                            amount,
                            AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeBabyModifiers(StandEntity stand) {
        removeModifier(
                stand.getAttribute(Attributes.ATTACK_DAMAGE),
                BABY_DAMAGE_ID);
        removeModifier(
                stand.getAttribute(Attributes.ATTACK_SPEED),
                BABY_SPEED_ID);
        removeModifier(
                stand.getAttribute(
                        Attributes.ENTITY_INTERACTION_RANGE),
                BABY_REACH_ID);
    }

    private static void removeModifier(
            AttributeInstance attribute,
            ResourceLocation id) {
        if (attribute != null) {
            attribute.removeModifier(id);
        }
    }

    private MobPowerEvents() {}
}
