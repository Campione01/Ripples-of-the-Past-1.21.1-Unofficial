package com.github.standobyte.jojo.mixin.client.keybindui;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

@SuppressWarnings("rawtypes")
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin extends AbstractContainerWidget {

	public AbstractSelectionListMixin(int p_313730_, int p_313819_, int p_313847_, int p_313718_, Component p_313894_) {
		super(p_313730_, p_313819_, p_313847_, p_313718_, p_313894_);
	}
	@Inject(method = "getEntryAtPosition", at = @At("TAIL"), cancellable = true)
	public void jojo_ripples$keybindsListDetectButtonsToTheLeft(double mouseX, double mouseY, CallbackInfoReturnable<AbstractSelectionList.Entry> ci) {
		if ((Class) this.getClass() == KeyBindsList.class && ci.getReturnValue() == null) {
//			int i = this.getRowWidth() / 2;
//			int j = this.getX() + this.width / 2;
//			int k = j - i;
//			int l = j + i;
			int i1 = Mth.floor(mouseY - (double)this.getY()) - this.headerHeight + (int)this.getScrollAmount() - 4;
			int j1 = i1 / this.itemHeight;
			if (/* mouseX >= (double)k && mouseX <= (double)l && */ j1 >= 0 && i1 >= 0 && j1 < this.getItemCount()) {
				ci.setReturnValue((AbstractSelectionList.Entry) this.children().get(j1));
			}
		}
	}

	@Shadow protected int headerHeight;
	@Shadow @Final protected int itemHeight;
	@Shadow public abstract int getRowWidth();
	@Shadow public abstract double getScrollAmount();
	@Shadow protected abstract int getItemCount();

}
