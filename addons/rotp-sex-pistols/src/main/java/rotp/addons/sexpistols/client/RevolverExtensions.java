package rotp.addons.sexpistols.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class RevolverExtensions
        implements IClientItemExtensions {
    private final RevolverRenderer renderer =
            new RevolverRenderer();

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return renderer;
    }
}
