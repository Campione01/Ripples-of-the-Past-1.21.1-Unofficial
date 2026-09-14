package rotp.core.init;

import rotp.core.core.JojoMod;
import rotp.core.mechanics.clothes.container.PlayerClothesMenu;
import rotp.core.mechanics.clothes.sewing.SewingMachineContainer;
import rotp.core.item.cassette.WalkmanMenu;
import rotp.core.subsystems.entity_externalcontainer._stand.StandHandsContainerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModContainers {
	public static final DeferredRegister<MenuType<?>> CONTAINERS = DeferredRegister.create(Registries.MENU, JojoMod.MOD_ID);


	public static final DeferredHolder<MenuType<?>, MenuType<PlayerClothesMenu>> PLAYER_CLOTHES = CONTAINERS.register("clothes", 
			key -> new MenuType<>(PlayerClothesMenu::new, FeatureFlags.DEFAULT_FLAGS));

	public static final DeferredHolder<MenuType<?>, MenuType<SewingMachineContainer>> SEWING_MACHINE = CONTAINERS.register("sewing_machine", 
			key -> new MenuType<>((id, inventory) -> new SewingMachineContainer(id, inventory, ContainerLevelAccess.NULL), FeatureFlags.DEFAULT_FLAGS));

	public static final DeferredHolder<MenuType<?>, MenuType<WalkmanMenu>> WALKMAN = CONTAINERS.register("walkman",
			key -> new MenuType<>((IContainerFactory<WalkmanMenu>) WalkmanMenu::client, FeatureFlags.DEFAULT_FLAGS));

	public static final DeferredHolder<MenuType<?>, MenuType<StandHandsContainerMenu>> STAND_HANDS = CONTAINERS.register("stand_hands", 
			key -> new MenuType<>(StandHandsContainerMenu.CLIENT_FACTORY, FeatureFlags.DEFAULT_FLAGS));
}
