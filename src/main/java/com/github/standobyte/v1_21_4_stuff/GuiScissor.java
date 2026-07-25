package com.github.standobyte.v1_21_4_stuff;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Mth;

// THEY CAN'T KEEP GETTING AWAY WITH THIS
public class GuiScissor {

	public static void enableScissor(GuiGraphics gui, int minX, int minY, int maxX, int maxY) {
		ScreenRectangle rectangle = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY);
		rectangle = transformAxisAligned(rectangle, gui.pose().last().pose()); // 1.21.4 does this, 1.21.1 does not
		minX = rectangle.left();
		minY = rectangle.top();
		maxX = rectangle.right();
		maxY = rectangle.bottom();
		gui.enableScissor(minX, minY, maxX, maxY);
	}

	public static ScreenRectangle transformAxisAligned(ScreenRectangle rectangle, Matrix4f pose) {
		boolean isIdentity = (pose.properties() & 4) != 0;
		if (isIdentity) {
			return rectangle;
		} else {
			Vector3f vector3f = pose.transformPosition((float)rectangle.left(), (float)rectangle.top(), 0.0F, new Vector3f());
			Vector3f vector3f1 = pose.transformPosition((float)rectangle.right(), (float)rectangle.bottom(), 0.0F, new Vector3f());
			return new ScreenRectangle(Mth.floor(vector3f.x), Mth.floor(vector3f.y), Mth.floor(vector3f1.x - vector3f.x), Mth.floor(vector3f1.y - vector3f.y));
		}
	}

}
