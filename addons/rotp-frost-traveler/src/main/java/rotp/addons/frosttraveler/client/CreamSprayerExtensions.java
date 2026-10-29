package rotp.addons.frosttraveler.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class CreamSprayerExtensions
        implements IClientItemExtensions {
    private final CreamSprayerRenderer renderer =
            new CreamSprayerRenderer();

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return renderer;
    }
}
