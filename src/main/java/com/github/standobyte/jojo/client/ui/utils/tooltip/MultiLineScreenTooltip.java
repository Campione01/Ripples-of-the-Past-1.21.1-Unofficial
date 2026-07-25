package com.github.standobyte.jojo.client.ui.utils.tooltip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class MultiLineScreenTooltip extends Tooltip {
	public static final int MAX_WIDTH = 170;
	public Component title;
	public int width;
	public List<Component> body;
	@Nullable
	public List<FormattedCharSequence> cachedTooltip;
	
	@Nullable
	public Language splitWithLanguage;

	public MultiLineScreenTooltip(Component title, Component... body) {
		super(title, title);
		setTitle(title);
		this.body = new ArrayList<>();
		Collections.addAll(this.body, body);
	}

	@Override
	public List<FormattedCharSequence> toCharSequence(Minecraft minecraft) {
		Language language = Language.getInstance();
		if (this.cachedTooltip == null || language != this.splitWithLanguage) {
			this.cachedTooltip = new ArrayList<>();
			this.cachedTooltip.add(title.getVisualOrderText());
			this.width = Math.max(Minecraft.getInstance().font.width(title), MAX_WIDTH);
			this.body.stream().map(line -> minecraft.font.split(line, width))
					.flatMap(Collection::stream)
					.forEach(this.cachedTooltip::add);
			this.splitWithLanguage = language;
		}

		return this.cachedTooltip;
	}
	
	public void setTitle(Component title) {
		this.title = title;
		if (this.cachedTooltip != null) {
			int width = Math.max(Minecraft.getInstance().font.width(title), MAX_WIDTH);
			if (this.width != width) {
				this.width = width;
				this.cachedTooltip = null;
			}
			else {
				this.cachedTooltip.set(0, title.getVisualOrderText());
			}
		}
	}

}
