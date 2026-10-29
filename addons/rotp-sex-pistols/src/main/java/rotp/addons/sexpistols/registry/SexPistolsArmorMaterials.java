package rotp.addons.sexpistols.registry;

import java.util.List;
import java.util.Map;

import rotp.addons.sexpistols.SexPistolsIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SexPistolsArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(
                    Registries.ARMOR_MATERIAL,
                    SexPistolsIds.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial>
            MISTA_HAT = ARMOR_MATERIALS.register(
                    SexPistolsIds.MISTA_HAT_ARMOR_MATERIAL_PATH,
                    () -> new ArmorMaterial(
                            Map.of(ArmorItem.Type.HELMET, 1),
                            15,
                            SoundEvents.ARMOR_EQUIP_LEATHER,
                            () -> Ingredient.EMPTY,
                            List.of(new ArmorMaterial.Layer(
                                    SexPistolsIds.id(
                                            SexPistolsIds
                                                    .MISTA_HAT_ARMOR_LAYER_PATH))),
                            0.0F,
                            0.0F));

    private SexPistolsArmorMaterials() {}

    public static void register(IEventBus modEventBus) {
        ARMOR_MATERIALS.register(modEventBus);
    }
}
