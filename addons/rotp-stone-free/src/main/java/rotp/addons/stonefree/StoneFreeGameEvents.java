package rotp.addons.stonefree;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.stonefree.ability.StoneFreeStringAbility;
import rotp.addons.stonefree.state.StoneFreeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = StoneFreeAddon.MOD_ID)
public final class StoneFreeGameEvents {
    private StoneFreeGameEvents() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity()
                instanceof LivingEntity living)
                || living.level().isClientSide()) {
            return;
        }
        if (!StoneFreeServices.isStoneFreeUser(living)) {
            if (living.hasEffect(
                    StoneFreeAddon.STRING_DECOMPOSITION)) {
                living.removeEffect(
                        StoneFreeAddon.STRING_DECOMPOSITION);
            }
            if (living.hasEffect(StoneFreeAddon.MOBIUS_STRIP)) {
                living.removeEffect(StoneFreeAddon.MOBIUS_STRIP);
            }
            return;
        }

        // 1.16 StoneFreeStandType.tickUser: the user's own attacks can hurt Stands
        living.addEffect(new MobEffectInstance(
                ModStatusEffects.INTEGRATED_STAND,
                10,
                0,
                false,
                false,
                false));

        StandPower power = StandPower.get(living);
        StoneFreeState state = StoneFreeState.get(living);
        if (living.tickCount % 20 == 0) {
            state.reconcileActiveStrings(
                    living,
                    StoneFreeServices.countActiveUserStrings(
                            living));
        }

        int strings = state.placedStrings();
        if (strings >= 21 && strings <= 100) {
            int amplifier = Math.min(
                    7, (strings - 21) / 10);
            living.addEffect(new MobEffectInstance(
                    StoneFreeAddon.STRING_DECOMPOSITION,
                    20,
                    amplifier,
                    false,
                    false,
                    true));
        }
        else {
            living.removeEffect(
                    StoneFreeAddon.STRING_DECOMPOSITION);
        }

        int inversionLevel =
                StoneFreeServices.inversionLevel(living);
        tryMobiusSpecialUnlock(
                living, power, state, inversionLevel, strings);

        if (inversionLevel > 0
                && state.mobiusMode()
                && !living.hasEffect(
                        StoneFreeAddon.MOBIUS_STRIP)
                && strings <= 90) {
            StoneFreeStringAbility.playUserStandSound(
                    (ServerLevel) living.level(),
                    living,
                    power,
                    StoneFreeAddon.STONE_FREE_MOBIUS_SOUND);
            living.addEffect(new MobEffectInstance(
                    StoneFreeAddon.MOBIUS_STRIP,
                    200,
                    0,
                    false,
                    false,
                    true));
            if (power != null) {
                power.consumeStamina(
                        25.0F * inversionLevel);
            }
        }
    }

    // 1.16 StoneFreeStandType.tickUser:187-190, the only unlock of the
    // Mobius Strip (noResolveUnlock). The inversion level is a parameter
    // because it comes from C-Moon, which game tests do not load.
    public static boolean tryMobiusSpecialUnlock(
            LivingEntity living,
            StandPower power,
            StoneFreeState state,
            int inversionLevel,
            int strings) {
        boolean mobiusUnlockCondition =
                power != null
                        && power.getResolveLevel() >= 4
                        && inversionLevel > 0
                        && living.getHealth() <= 10.0F
                        && strings <= 90;
        if (!mobiusUnlockCondition
                || power.getCurTypeData() == null
                || power.getCurTypeData().isSkillUnlocked(
                        StoneFreeAddon
                                .USER_MOBIUS_STRIP_NAME)) {
            return false;
        }
        if (power.getCurTypeData()._setSkillUnlocked(
                StoneFreeAddon.USER_MOBIUS_STRIP_NAME,
                true,
                false)) {
            power.getCurTypeData().syncOnUpdate(living);
        }
        state.setMobiusMode(living, true);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity user = event.getEntity();
        if (!StoneFreeServices.isStoneFreeUser(user)
                || event.getSource().getEntity() == null
                || event.getSource().is(
                        DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        float reduction = Math.min(
                0.6F,
                StoneFreeState.get(user).placedStrings()
                        * 0.006F);
        if (reduction > 0.0F) {
            event.setAmount(
                    event.getAmount() * (1.0F - reduction));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (!living.level().isClientSide()
                && StoneFreeServices.isStoneFreeUser(living)) {
            StoneFreeServices.discardUserGrapples(living);
            StoneFreeServices.discardBarriers(living);
            living.removeEffect(
                    StoneFreeAddon.STRING_DECOMPOSITION);
            living.removeEffect(StoneFreeAddon.MOBIUS_STRIP);
        }
    }
}
