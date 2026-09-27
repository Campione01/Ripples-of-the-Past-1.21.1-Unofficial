package rotp.core.client.itemrender.custommodel;

import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class ISTERItemCaptureEntity extends ItemOverrides {
	@Nullable private final BakedModel originalModel;
	private final Map<BakedModel, BakedModel> capturedModels = new IdentityHashMap<>();

	public ISTERItemCaptureEntity() {
		this(null);
	}

	public ISTERItemCaptureEntity(@Nullable BakedModel originalModel) {
		super();
		this.originalModel = originalModel;
	}

	@Override
	public BakedModel resolve(BakedModel model, ItemStack item, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
		BlockEntityWithoutLevelRenderer ister = IClientItemExtensions.of(item).getCustomRenderer();
		if (ister instanceof ISTERWithEntity) {
			((ISTERWithEntity) ister).setEntity(entity);
		}
		if (originalModel != null) {
			BakedModel resolved = originalModel.getOverrides().resolve(originalModel, item, world, entity, seed);
			if (resolved != null && resolved != originalModel && resolved != model) {
				return capturedModels.computeIfAbsent(resolved, replacement ->
						replacement instanceof BakedCustomModel ? replacement : new BakedCustomModel(replacement).setCaptureEntity());
			}
		}
		return model;
	}
}
