package com.github.standobyte.jojo.mixin.client.v1_21_1_itemmodel;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.v1_21_4_stuff.itemmodel.__ItemModelComponent;
import com.github.standobyte.v1_21_4_stuff.itemmodel.client.StandaloneItemModelLoader;

import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemModelShaper.class)
public class ItemModelShaperMixin {
	@Shadow @Final private ModelManager modelManager;

	@Inject(method = "getItemModel", at = @At("HEAD"), cancellable = true)
	public void jojo_ripples$customItemModel(ItemStack stack, CallbackInfoReturnable<BakedModel> ci) {
		ResourceLocation customModel = __ItemModelComponent.get(stack);
		if (customModel != null) {
			BakedModel model = null;
			ModelResourceLocation modelId = StandaloneItemModelLoader.getModelLocation(customModel);
			if (modelId != null) {
				ModelBakery bakery = modelManager.getModelBakery();
				model = bakery.getBakedTopLevelModels().get(modelId);
			}
			ci.setReturnValue(model != null ? model : modelManager.getMissingModel());
		}
	}
}
