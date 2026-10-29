package rotp.addons.memory_discs;

import java.util.Comparator;

import rotp.core.init.ModItems;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.addons.memory_discs.item.MemoryDiscItem;
import rotp.addons.memory_discs.item.MemoryDiscRemoverItem;
import rotp.addons.memory_discs.item.MemoryDiscRemoverItem.Mode;
import rotp.addons.memory_discs.item.PowerDiscData;
import rotp.addons.memory_discs.item.dispenser.MemoryDiscDispenserBehaviors;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RotpMemoryDiscsAddon.MOD_ID)
public final class RotpMemoryDiscsAddon {
    public static final String MOD_ID = "rotp_mds";

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);

    public static final DeferredHolder<Item, MemoryDiscItem> MEMORY_DISC =
            ITEMS.register(
                    "memory_disc",
                    () -> new MemoryDiscItem(
                            new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, MemoryDiscRemoverItem>
            MEMORY_REMOVER = remover(
                    "memory_remover", Mode.REMOVE, false, 1);
    public static final DeferredHolder<Item, MemoryDiscRemoverItem>
            MEMORY_REMOVER_ONE_TIME = remover(
                    "memory_remover_one_time",
                    Mode.REMOVE,
                    true,
                    64);
    public static final DeferredHolder<Item, MemoryDiscRemoverItem>
            MEMORY_EJECT = remover(
                    "memory_eject", Mode.EJECT, false, 1);
    public static final DeferredHolder<Item, MemoryDiscRemoverItem>
            MEMORY_EJECT_ONE_TIME = remover(
                    "memory_eject_one_time",
                    Mode.EJECT,
                    true,
                    64);

    public RotpMemoryDiscsAddon(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::buildCreativeTab);
    }

    private static DeferredHolder<Item, MemoryDiscRemoverItem> remover(
            String id, Mode mode, boolean oneTimeUse, int stackSize) {
        return ITEMS.register(
                id,
                () -> new MemoryDiscRemoverItem(
                        new Item.Properties().stacksTo(stackSize),
                        mode,
                        oneTimeUse));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MemoryDiscDispenserBehaviors::register);
    }

    private void buildCreativeTab(
            BuildCreativeModeTabContentsEvent event) {
        if (!ModItems.MAIN_TAB.getKey().equals(
                event.getTabKey())) {
            return;
        }

        event.accept(MEMORY_DISC.get());
        PlayerPowerType.getAllEnabledPlayerPowers()
                .sorted(Comparator.comparing(
                        type -> type.getId().toString()))
                .map(PowerDiscData::newFilledDisc)
                .forEach(event::accept);
        event.accept(MEMORY_REMOVER.get());
        event.accept(MEMORY_REMOVER_ONE_TIME.get());
        event.accept(MEMORY_EJECT.get());
        event.accept(MEMORY_EJECT_ONE_TIME.get());
    }
}
