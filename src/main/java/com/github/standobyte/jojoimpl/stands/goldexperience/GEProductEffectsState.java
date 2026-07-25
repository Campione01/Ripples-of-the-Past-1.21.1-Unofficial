package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class GEProductEffectsState implements INBTSerializable<CompoundTag> {
    private static final String PRODUCT_EFFECTS_TAG = "ProductPotion";

    private final List<MobEffectInstance> productEffects = new ArrayList<>();

    public GEProductEffectsState(LivingEntity entity) {}

    public static GEProductEffectsState get(LivingEntity entity) {
        return entity.getData(ModDataAttachmentTypes.GE_PRODUCT_EFFECTS);
    }

    public static List<MobEffectInstance> getItemEffects(ItemStack itemStack) {
        PotionContents contents = itemStack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || !contents.hasEffects()) {
            return List.of();
        }

        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : contents.getAllEffects()) {
            effects.add(new MobEffectInstance(effect));
        }
        return effects;
    }

    public void setProductEffects(Collection<MobEffectInstance> effects) {
        productEffects.clear();
        for (MobEffectInstance effect : effects) {
            productEffects.add(new MobEffectInstance(effect));
        }
    }

    public List<MobEffectInstance> getProductEffects() {
        return productEffects.stream().map(MobEffectInstance::new).toList();
    }

    public boolean hasProductEffects() {
        return !productEffects.isEmpty();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (!productEffects.isEmpty()) {
            ListTag effectsNbt = new ListTag();
            for (MobEffectInstance effect : productEffects) {
                MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, effect)
                        .ifSuccess(effectsNbt::add);
            }
            tag.put(PRODUCT_EFFECTS_TAG, effectsNbt);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        productEffects.clear();
        if (!tag.contains(PRODUCT_EFFECTS_TAG, Tag.TAG_LIST)) {
            return;
        }

        ListTag effectsNbt = tag.getList(PRODUCT_EFFECTS_TAG, Tag.TAG_COMPOUND);
        for (Tag effectNbt : effectsNbt) {
            MobEffectInstance.CODEC.decode(NbtOps.INSTANCE, effectNbt).result()
                    .map(Pair::getFirst)
                    .ifPresent(productEffects::add);
        }
    }
}
