package rotp.addons.nextalbum.item;

import java.util.List;
import java.util.Map;

import rotp.addons.nextalbum.NextAlbumIds;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

public final class MistaSuitArmorMaterial {
    public static final int DURABILITY_MULTIPLIER = 16;
    public static final Holder<ArmorMaterial> MISTA_SUIT =
            Holder.direct(new ArmorMaterial(
                    Map.of(
                            ArmorItem.Type.BOOTS, 1,
                            ArmorItem.Type.LEGGINGS, 2,
                            ArmorItem.Type.CHESTPLATE, 3,
                            ArmorItem.Type.HELMET, 1),
                    15,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(
                            NextAlbumIds.id("mista_suit"))),
                    0.0F,
                    0.0F));

    private MistaSuitArmorMaterial() {}
}
