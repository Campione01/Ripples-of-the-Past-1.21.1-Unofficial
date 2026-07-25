package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.util.ArrayList;
import java.util.List;

import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonSkillDefinition;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class HamonSkillToast implements Toast {
	private static final Component NAME = Component.translatable("hamon_skill.toast.title");
	private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/recipe");
	private static final long DISPLAY_TIME = 5000L;

	private final Type type;
	private final Component description;
	private final List<String> skills = new ArrayList<>();
	private long lastChanged;
	private boolean changed;

	private HamonSkillToast(Type type, String skillName) {
		this.type = type;
		this.description = Component.translatable("hamon_skill.toast." + type.skillType + "description",
				Component.keybind("jojo_ripples.key.jojo_menu").withStyle(ChatFormatting.BOLD));
		this.skills.add(skillName);
	}

	@Override
	public Toast.Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long delta) {
		if (changed) {
			lastChanged = delta;
			changed = false;
		}
		if (skills.isEmpty()) {
			return Toast.Visibility.HIDE;
		}

		Minecraft mc = toastComponent.getMinecraft();
		guiGraphics.blitSprite(BACKGROUND_SPRITE, 0, 0, width(), height());
		guiGraphics.drawString(mc.font, NAME, 30, 7, 0xFF500050, false);
		guiGraphics.drawString(mc.font, description, 30, 18, 0xFF000000, false);

		String skillName = skills.get((int) (delta
				/ Math.max(1L, DISPLAY_TIME / (long) skills.size())
				% (long) skills.size()));
		ResourceLocation icon = JojoMod.resLoc("textures/hamon/" + skillName + ".png");
		BlitFloat.blit(guiGraphics.pose(), mc, icon, 8, 8, 16, 16, 0, BlitFloat.NO_TINT);

		return delta - lastChanged >= DISPLAY_TIME ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
	}

	private void addSkill(String skillName) {
		if (!skills.contains(skillName)) {
			skills.add(skillName);
			changed = true;
		}
	}

	public static void addOrUpdate(ToastComponent toastComponent, Type type, String skillName) {
		HamonSkillToast toast = toastComponent.getToast(HamonSkillToast.class, type);
		if (toast == null) {
			toastComponent.addToast(new HamonSkillToast(type, skillName));
		}
		else {
			toast.addSkill(skillName);
		}
	}

	@Override
	public Type getToken() {
		return type;
	}

	public enum Type {
		STRENGTH("strength."),
		CONTROL("control."),
		TECHNIQUE("technique.");

		private final String skillType;

		Type(String skillType) {
			this.skillType = skillType;
		}

		public static Type forSkill(HamonSkillDefinition skill) {
			if (skill.branch() == HamonSkillDefinition.HamonSkillBranch.CHARACTER_TECHNIQUE) {
				return TECHNIQUE;
			}
			return HamonData.statForSkillBranch(skill.branch()) == HamonData.HamonStat.STRENGTH
					? STRENGTH : CONTROL;
		}
	}
}
