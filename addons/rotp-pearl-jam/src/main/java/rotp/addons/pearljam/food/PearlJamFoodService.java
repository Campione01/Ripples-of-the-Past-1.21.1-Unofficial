package rotp.addons.pearljam.food;

import java.util.List;

import rotp.core.init.ModParticles;

import rotp.addons.pearljam.PearlJamAddon;
import rotp.addons.pearljam.state.PearlJamPlayerState;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

public final class PearlJamFoodService {
    public static final int DIAGNOSIS_TTL_TICKS = 2400;
    public static final int TREATMENT_LOCK_TICKS = 100;
    public static final int LEGACY_EFFECT_TICKS = 250;
    public static final int BASE_INJECT_COST = 100;
    public static final int BASE_POISON_COST = 200;
    public static final int COST_PER_ITEM = 10;

    // 1.16 GameplayUtil.addBuffs cases 0-10, in order.
    private static final List<Holder<MobEffect>> BENEFICIAL_EFFECTS =
            List.of(
                    MobEffects.MOVEMENT_SPEED,
                    MobEffects.DAMAGE_BOOST,
                    MobEffects.FIRE_RESISTANCE,
                    MobEffects.JUMP,
                    MobEffects.NIGHT_VISION,
                    MobEffects.HEALTH_BOOST,
                    MobEffects.REGENERATION,
                    MobEffects.SLOW_FALLING,
                    MobEffects.DIG_SPEED,
                    MobEffects.DOLPHINS_GRACE,
                    MobEffects.WATER_BREATHING);
    // 1.16 GameplayUtil.addDeBuffs cases 0-8, in order.
    private static final List<Holder<MobEffect>> POISON_EFFECTS =
            List.of(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    MobEffects.BLINDNESS,
                    MobEffects.WEAKNESS,
                    MobEffects.HUNGER,
                    MobEffects.DIG_SLOWDOWN,
                    MobEffects.DIG_SPEED,
                    MobEffects.POISON,
                    MobEffects.UNLUCK,
                    MobEffects.WITHER);

    private PearlJamFoodService() {}

    public static PearlJamTreatment diagnose(
            LivingEntity target) {
        if (hasHarmfulEffect(target)) {
            return PearlJamTreatment.DETOX;
        }
        if (target.getHealth() < target.getMaxHealth()
                || target instanceof Player player
                        && player.getFoodData().getFoodLevel() < 20) {
            return PearlJamTreatment.RESTORATION;
        }
        return PearlJamTreatment.NONE;
    }

    public static InteractionHand selectedHand(Player player) {
        return PearlJamAddon.state(player).mainHandSelected()
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
    }

    public static ItemStack selectedFood(Player player) {
        ItemStack stack = player.getItemInHand(
                selectedHand(player));
        return isFood(stack, player) ? stack : ItemStack.EMPTY;
    }

    public static boolean isPrepared(ItemStack stack) {
        return !stack.isEmpty()
                && stack.get(PearlJamAddon.DISH_DATA.get()) != null;
    }

    public static int injectCost(ItemStack stack) {
        return stackCost(BASE_INJECT_COST, stack);
    }

    public static int poisonCost(ItemStack stack) {
        return stackCost(BASE_POISON_COST, stack);
    }

    public static int unjectCost(ItemStack stack) {
        return stackCost(BASE_INJECT_COST, stack);
    }

    public static boolean prepareInjected(ServerPlayer chef) {
        PearlJamPlayerState state = PearlJamAddon.state(chef);
        long gameTime = chef.level().getGameTime();
        // 1.16 injected any food for whoever eats it; a live diagnosis
        // also makes the dish a treatment for the diagnosed target.
        if (!state.hasUsableDiagnosis(gameTime)
                || state.targetUuid() == null) {
            return prepare(
                    chef,
                    PearlJamDishKind.INJECTED,
                    null,
                    "",
                    PearlJamTreatment.NONE);
        }

        LivingEntity intended = livingByUuid(
                chef.serverLevel(), state.targetUuid());
        String targetName = intended != null
                ? intended.getDisplayName().getString()
                : state.targetUuid().toString();
        return prepare(
                chef,
                PearlJamDishKind.INJECTED,
                state.targetUuid(),
                targetName,
                state.treatment());
    }

    public static boolean preparePoisoned(ServerPlayer chef) {
        return prepare(
                chef,
                PearlJamDishKind.POISONED,
                null,
                "",
                PearlJamTreatment.NONE);
    }

    public static boolean unject(ServerPlayer chef) {
        InteractionHand hand = selectedHand(chef);
        ItemStack selected = chef.getItemInHand(hand);
        if (!isFood(selected, chef) || !isPrepared(selected)) {
            return false;
        }
        selected.remove(PearlJamAddon.DISH_DATA.get());
        // the ability says UNJECT_SOUND as its 1.16 .shout
        return true;
    }

    public static void spawnUseParticle(
            LivingEntity consumer, ItemStack stack) {
        PearlJamDishData dish =
                stack.get(PearlJamAddon.DISH_DATA.get());
        if (dish == null || !consumer.level().isClientSide()) {
            return;
        }
        consumer.level().addParticle(
                dish.kind() == PearlJamDishKind.POISONED
                        ? ModParticles.HAMON_AURA_BLUE.get()
                        : ModParticles.HAMON_AURA_RED.get(),
                consumer.getX(),
                consumer.getY() + consumer.getBbHeight(),
                consumer.getZ(),
                0.0D,
                0.0D,
                0.0D);
    }

    public static boolean applyOnEating(
            LivingEntity consumer,
            ItemStack consumedStack) {
        if (consumer.level().isClientSide()) {
            return false;
        }
        PearlJamDishData dish =
                consumedStack.get(PearlJamAddon.DISH_DATA.get());
        FoodProperties food =
                consumedStack.getFoodProperties(consumer);
        if (dish == null || food == null) {
            return false;
        }

        return switch (dish.kind()) {
            case INJECTED ->
                    applyInjected(consumer, food, dish);
            case POISONED ->
                    applyPoisoned(consumer);
        };
    }

    private static boolean prepare(
            ServerPlayer chef,
            PearlJamDishKind kind,
            java.util.UUID targetUuid,
            String targetName,
            PearlJamTreatment treatment) {
        InteractionHand hand = selectedHand(chef);
        ItemStack held = chef.getItemInHand(hand);
        FoodProperties food = held.getFoodProperties(chef);
        if (food == null || isPrepared(held)) {
            return false;
        }

        PearlJamPlayerState state = PearlJamAddon.state(chef);
        PearlJamDishData dish = new PearlJamDishData(
                chef.getUUID(),
                state.chefSignatureHidden()
                        ? ""
                        : chef.getDisplayName().getString(),
                targetUuid,
                targetName,
                kind,
                treatment,
                potency(food));
        held.set(PearlJamAddon.DISH_DATA.get(), dish);

        // the ability says INJECT_SOUND as its 1.16 .shout
        return true;
    }

    private static boolean applyInjected(
            LivingEntity consumer,
            FoodProperties food,
            PearlJamDishData dish) {
        // Treatment first, so the buff below (Health Boost raises max
        // health) cannot change the diagnosis match.
        if (dish.isIntendedFor(consumer.getUUID())) {
            applyTreatment(consumer, food, dish);
        }
        // 1.16 GameplayUtil.onEatEvent: whoever eats an injected dish
        // gets one random buff, treated or not.
        addRandomEffect(consumer, BENEFICIAL_EFFECTS);
        return true;
    }

    // Port extension: the diagnosed consumer, still in that condition,
    // is also healed or detoxed and gets a short aftershock.
    private static void applyTreatment(
            LivingEntity consumer,
            FoodProperties food,
            PearlJamDishData dish) {
        if (!dish.treatment().isActive()
                || diagnose(consumer) != dish.treatment()) {
            return;
        }

        long gameTime = consumer.level().getGameTime();
        PearlJamPlayerState playerState =
                consumer instanceof Player player
                        ? PearlJamAddon.state(player)
                        : null;
        if (playerState != null) {
            playerState.expire(gameTime);
            if (playerState.treatmentLocked(gameTime)) {
                return;
            }
        }

        boolean applied = switch (dish.treatment()) {
            case RESTORATION ->
                    applyRestoration(
                            consumer, food, dish.potency());
            case DETOX ->
                    applyDetox(consumer, dish.potency());
            case NONE -> false;
        };
        if (!applied) {
            return;
        }

        consumer.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION,
                40 + dish.potency() * 20,
                0,
                false,
                false,
                true));
        if (playerState != null) {
            playerState.lockTreatmentUntil(
                    gameTime + TREATMENT_LOCK_TICKS);
            PearlJamAddon.syncState((Player) consumer);
        }
        finishTreatmentEffects(consumer, dish.treatment());
    }

    private static boolean applyPoisoned(
            LivingEntity consumer) {
        addRandomEffect(consumer, POISON_EFFECTS);
        return true;
    }

    private static void addRandomEffect(
            LivingEntity consumer,
            List<Holder<MobEffect>> effects) {
        Holder<MobEffect> effect = effects.get(
                consumer.getRandom().nextInt(effects.size()));
        int amplifier = consumer.getRandom().nextBoolean()
                ? 3
                : 0;
        // 1.16 case 5 gave Health Boost at amplifier - 2 (-2 or 1).
        int legacyAmplifier = MobEffects.HEALTH_BOOST.equals(effect)
                ? amplifier - 2
                : amplifier;
        MobEffectInstance instance = new MobEffectInstance(
                effect,
                LEGACY_EFFECT_TICKS,
                Math.max(0, legacyAmplifier),
                false,
                false,
                true);
        if (legacyAmplifier < 0) {
            PearlJamHealthBoostCompat.markWeak(instance);
        }
        consumer.addEffect(instance);
        PearlJamHealthBoostCompat.applyActiveModifiers(consumer);
    }

    private static boolean applyRestoration(
            LivingEntity consumer,
            FoodProperties food,
            int potency) {
        if (consumer.getHealth() >= consumer.getMaxHealth()
                && !(consumer instanceof Player player
                        && player.getFoodData().getFoodLevel() < 20)) {
            return false;
        }
        float amount = Math.max(
                2.0F,
                food.nutrition() + potency * 2.0F);
        consumer.heal(amount);
        consumer.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION,
                40 * potency,
                Math.max(0, potency - 2),
                false,
                false,
                true));
        return true;
    }

    private static boolean applyDetox(
            LivingEntity consumer, int potency) {
        List<Holder<MobEffect>> harmful = consumer
                .getActiveEffects()
                .stream()
                .filter(effect -> effect.getEffect()
                        .value()
                        .getCategory() == MobEffectCategory.HARMFUL)
                .limit(potency)
                .map(MobEffectInstance::getEffect)
                .toList();
        if (harmful.isEmpty()) {
            return false;
        }
        harmful.forEach(consumer::removeEffect);
        consumer.heal(potency);
        return true;
    }

    private static void finishTreatmentEffects(
            LivingEntity consumer,
            PearlJamTreatment treatment) {
        if (consumer.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    treatment == PearlJamTreatment.DETOX
                            ? ModParticles.HAMON_AURA_BLUE.get()
                            : ModParticles.HAMON_AURA_RED.get(),
                    consumer.getX(),
                    consumer.getY()
                            + consumer.getBbHeight() * 0.6D,
                    consumer.getZ(),
                    12,
                    0.35D,
                    0.45D,
                    0.35D,
                    0.04D);
        }
    }

    private static int potency(FoodProperties food) {
        float quality =
                food.nutrition() + food.saturation() * 4.0F;
        return Mth.clamp(
                quality >= 10.0F ? 3 : quality >= 5.0F ? 2 : 1,
                PearlJamDishData.MIN_POTENCY,
                PearlJamDishData.MAX_POTENCY);
    }

    private static int stackCost(
            int baseCost, ItemStack stack) {
        return baseCost
                + COST_PER_ITEM * Math.max(0, stack.getCount());
    }

    private static boolean isFood(
            ItemStack stack, LivingEntity entity) {
        return !stack.isEmpty()
                && stack.getFoodProperties(entity) != null;
    }

    private static boolean hasHarmfulEffect(
            LivingEntity target) {
        return target.getActiveEffects().stream().anyMatch(
                effect -> effect.getEffect()
                        .value()
                        .getCategory() == MobEffectCategory.HARMFUL);
    }

    private static LivingEntity livingByUuid(
            ServerLevel level, java.util.UUID uuid) {
        return level.getEntity(uuid)
                instanceof LivingEntity living
                ? living
                : null;
    }
}
