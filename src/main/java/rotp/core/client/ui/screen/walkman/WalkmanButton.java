package rotp.core.client.ui.screen.walkman;

import java.util.function.Supplier;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** 1.16 walkman key: drawn from the walkman texture, no label, text only as a hover tooltip. */
public class WalkmanButton extends AbstractButton {
	private final Runnable onPress;
	private final Supplier<Component> tooltip;
	private final int texX;

	public WalkmanButton(int x, int y, int width, int height, Runnable onPress, Supplier<Component> tooltip, int texX) {
		super(x, y, width, height, Component.empty());
		this.onPress = onPress;
		this.tooltip = tooltip;
		this.texX = texX;
	}

	@Override
	public void onPress() {
		onPress.run();
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		renderSprite(guiGraphics);
	}

	protected void renderSprite(GuiGraphics guiGraphics) {
		guiGraphics.blit(WalkmanScreen.TEXTURE, getX(), getY(), texX, WalkmanScreenLayout.keyTexY(active, isHovered()), width, height);
	}

	boolean showsTooltip() {
		return visible && WalkmanScreenLayout.showsTooltip(active, isHovered());
	}

	Component tooltip() {
		Component text = tooltip.get();
		return text != null ? text : Component.empty();
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
		soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.5F, 0.1F));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, tooltip());
	}
}
