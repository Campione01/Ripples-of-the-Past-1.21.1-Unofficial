package rotp.addons.hermitpurple;

import rotp.core.api.stand.AutomatedStandGrantVetoes;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HermitPurpleAddon.MOD_ID)
public final class HermitPurpleGameEvents {
    private HermitPurpleGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                HermitPurpleAddon.HERMIT_PURPLE_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()
                || event.getAmount() <= 0.0F
                || !HermitPurpleAddon.isHermitPurpleUser(victim)
                || HermitPurpleAddon.controller(victim) == null
                || !HermitPurpleAddon.controller(victim)
                        .isStandBlocking()) {
            return;
        }

        DamageSource source = event.getSource();
        if (source.is(DamageTypes.THORNS)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)
                || source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_FIRE)
                || !(source.getDirectEntity()
                        instanceof LivingEntity)) {
            return;
        }

        event.setAmount(Math.max(
                0.0F,
                event.getAmount()
                        - HermitPurpleSpec
                                .THORNS_FLAT_REDUCTION));

        LivingEntity attacker =
                resolveAttacker(source.getEntity());
        if (attacker == null) {
            attacker =
                    resolveAttacker(source.getDirectEntity());
        }
        if (attacker != null
                && attacker != victim
                && attacker.isAlive()
                && !attacker.isRemoved()
                && !(attacker instanceof Player player
                        && (player.isCreative()
                                || player.isSpectator()))) {
            attacker.hurt(
                    victim.damageSources().thorns(victim),
                    HermitPurpleSpec
                            .THORNS_REFLECT_DAMAGE);
            if (HermitHamon.hasSkill(
                            victim,
                            ModHamonSkills
                                    .THROWABLES_INFUSION
                                    .get())
                    && HermitHamon.hasChargedVines(victim)) {
                HermitHamon.hurt(
                        attacker,
                        victim,
                        HermitPurpleSpec
                                .THORNS_HAMON_REFLECT_DAMAGE,
                        false);
            }
        }
    }

    private static LivingEntity resolveAttacker(Entity entity) {
        if (entity instanceof StandEntity stand) {
            return stand.getUser();
        }
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    // 1.16 HermitPurpleStandType.tickUser: integrated_stand for 10 ticks
    // under the hermitHamon gates, so RHCP treats the user as a Stand.
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living
                && !living.level().isClientSide()
                && HermitPurpleAddon.hasHermitHamonGates(living)) {
            // Looked up by id: a core without the effect makes this a no-op
            BuiltInRegistries.MOB_EFFECT.getHolder(INTEGRATED_STAND)
                    .ifPresent(effect -> living.addEffect(
                            new MobEffectInstance(
                                    effect, 10, 0, false, false, false)));
        }
    }

    private static final ResourceKey<MobEffect> INTEGRATED_STAND =
            ResourceKey.create(Registries.MOB_EFFECT,
                    ResourceLocation.fromNamespaceAndPath(
                            "jojo_ripples", "integrated_stand"));

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData()
                .getBoolean("rotp_zhp.triedHermit")) {
            event.getEntity().getPersistentData().putBoolean(
                    "rotp_zhp.triedHermit", true);
        }
        clear(event.getOriginal());
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (AutomatedStandGrantVetoes.isVetoed(
                        HermitPurpleAddon.HAMON_TO_HERMIT_SOURCE,
                        player)
                || player.tickCount % 20 != 0
                || !HermitPurpleConfig.HAMON_TO_HERMIT.get()
                || player.getPersistentData()
                        .getBoolean("rotp_zhp.triedHermit")) {
            return;
        }
        HermitHamon.data(player).ifPresent(hamon -> {
            StandPower stand = StandPower.get(player);
            if (stand != null
                    && !stand.hasPower()
                    && hamon.getHamonControlLevel()
                            >= HermitPurpleConfig
                                    .CONTROL_THRESHOLD
                                    .get()
                    && hamon.getHamonStrengthLevel()
                            >= HermitPurpleConfig
                                    .STRENGTH_THRESHOLD
                                    .get()
                    && hamon.getBreathingLevel()
                            >= HermitPurpleConfig
                                    .BREATHING_THRESHOLD
                                    .get()) {
                player.getPersistentData().putBoolean(
                        "rotp_zhp.triedHermit", true);
                stand.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterCommands(
            RegisterCommandsEvent event) {
        HermitPurpleCommands.register(event);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(
            EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity()
                        instanceof LivingEntity living) {
            clear(living);
        }
    }

    private static void clear(LivingEntity entity) {
        if (entity != null && !entity.level().isClientSide()) {
            HermitPurpleAddon.discardActiveVines(entity);
        }
    }
}
