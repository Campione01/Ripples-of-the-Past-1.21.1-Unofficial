package rotp.addons.mandom.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class RingoClockExtensions
        implements IClientItemExtensions {
    private final RingoClockRenderer renderer =
            new RingoClockRenderer();

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return renderer;
    }
}
