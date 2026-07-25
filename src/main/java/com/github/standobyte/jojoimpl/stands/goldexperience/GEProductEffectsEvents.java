package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class GEProductEffectsEvents {
    public static final String MOD_ADDS_EFFECTS_TO_ITEM = "JojoItemUseEffects";

    private GEProductEffectsEvents() {}

    @SubscribeEvent
    public static void onCowProduct(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!(target instanceof Cow cow) || !target.isAlive() || cow.isBaby()) {
            return;
        }

        ItemStack heldItem = event.getItemStack();
        boolean bucket = heldItem.is(Items.BUCKET);
        boolean bowl = heldItem.is(Items.BOWL) && cow instanceof MushroomCow;
        if (!bucket && !bowl) {
            return;
        }

        Player player = event.getEntity();
        if (cow instanceof Leashable leashable && leashable.getLeashHolder() == player) {
            return;
        }

        List<MobEffectInstance> productEffects = GEProductEffectsState.get(cow).getProductEffects();
        if (productEffects.isEmpty()) {
            return;
        }

        if (bucket) {
            player.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
            ItemStack milkBucket = withProductEffects(new ItemStack(Items.MILK_BUCKET), productEffects);
            player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(heldItem, player, milkBucket));
        }
        else {
            ItemStack stew = withProductEffects(new ItemStack(Items.MUSHROOM_STEW), productEffects);
            player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(heldItem, player, stew, false));
            cow.playSound(SoundEvents.MOOSHROOM_MILK, 1.0F, 1.0F);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide()));
    }

    @SubscribeEvent
    public static void usePotionCowProduct(LivingEntityUseItemEvent.Finish event) {
        ItemStack item = event.getItem();
        if (item.isEmpty() || !isModProduct(item)) {
            return;
        }

        LivingEntity entity = event.getEntity();
        for (MobEffectInstance effect : GEProductEffectsState.getItemEffects(item)) {
            entity.addEffect(new MobEffectInstance(effect));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAnimalOffspring(BabyEntitySpawnEvent event) {
        AgeableMob child = event.getChild();
        if (child == null) {
            return;
        }

        List<MobEffectInstance> effectsA = GEProductEffectsState.get(event.getParentA()).getProductEffects();
        List<MobEffectInstance> effectsB = GEProductEffectsState.get(event.getParentB()).getProductEffects();
        List<MobEffectInstance> merged = mergeProductEffects(effectsA, effectsB);
        if (!merged.isEmpty()) {
            GEProductEffectsState.get(child).setProductEffects(merged);
        }
    }

    private static ItemStack withProductEffects(ItemStack itemStack, List<MobEffectInstance> effects) {
        itemStack.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.empty(), copyEffects(effects)));
        CustomData.update(DataComponents.CUSTOM_DATA, itemStack, tag -> tag.putBoolean(MOD_ADDS_EFFECTS_TO_ITEM, true));
        return itemStack;
    }

    private static boolean isModProduct(ItemStack itemStack) {
        return itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getBoolean(MOD_ADDS_EFFECTS_TO_ITEM);
    }

    private static List<MobEffectInstance> mergeProductEffects(List<MobEffectInstance> effectsA,
            List<MobEffectInstance> effectsB) {
        Map<Holder<MobEffect>, MobEffectInstance> effects = new LinkedHashMap<>();
        for (MobEffectInstance effect : effectsA) {
            effects.put(effect.getEffect(), new MobEffectInstance(effect));
        }
        for (MobEffectInstance effect : effectsB) {
            effects.merge(effect.getEffect(), new MobEffectInstance(effect), GEProductEffectsEvents::mergeProductEffect);
        }
        return new ArrayList<>(effects.values());
    }

    private static MobEffectInstance mergeProductEffect(MobEffectInstance effectA, MobEffectInstance effectB) {
        return new MobEffectInstance(effectA.getEffect(),
                Math.max(effectA.getDuration(), effectB.getDuration()),
                Math.max(effectA.getAmplifier(), effectB.getAmplifier()));
    }

    private static List<MobEffectInstance> copyEffects(List<MobEffectInstance> effects) {
        return effects.stream().map(MobEffectInstance::new).toList();
    }
}
