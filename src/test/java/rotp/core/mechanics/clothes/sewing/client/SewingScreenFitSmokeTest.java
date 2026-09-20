package rotp.core.mechanics.clothes.sewing.client;

import org.joml.Matrix4f;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import rotp.core.compat.v1_21_4.GuiScissor;

public final class SewingScreenFitSmokeTest {
    private SewingScreenFitSmokeTest() {}

    public static void main(String[] args) {
        int cases = 0;
        for (int width : new int[] {320, 321, 349, 350, 360, 427, 480, 640, 960}) {
            for (int height : new int[] {240, 241, 258, 270, 300, 360, 480}) {
                verifyBoundsAndInput(width, height);
                cases++;
            }
        }
        verifySmallViewportControls();
        verifyLargeViewportParity();
        verifyTooltipBounds();
        verifyLongNameScissor();
        System.out.println("Sewing screen fit smoke test passed (" + cases + " viewports, inverse input, previews, tooltips, long-name scissor, identity).");
    }

    private static SewingScreenFit fit(int width, int height) {
        return SewingScreenFit.forBounds(width, height, (width - 331) / 2 - 18, (height - 258) / 2, 349, 258);
    }

    private static void verifyBoundsAndInput(int width, int height) {
        SewingScreenFit fit = fit(width, height);
        int left = (width - 331) / 2 - 18;
        int top = (height - 258) / 2;
        check(fit.scale() > 0 && fit.scale() <= 1, "invalid scale");
        check(fit.toScreenX(left) >= 4 - 0.0001, "left character tabs clipped");
        check(fit.toScreenY(top) >= 4 - 0.0001, "top controls clipped");
        check(fit.toScreenX(left + 349) <= width - 4 + 0.0001, "right panel clipped");
        check(fit.toScreenY(top + 258) <= height - 4 + 0.0001, "inventory clipped");
        for (double x : new double[] {left - 5, left, left + 8, left + 42.5, left + 340, width}) {
            close(fit.toLayoutX(fit.toScreenX(x)), x, "click/release x round trip");
        }
        for (double y : new double[] {top - 5, top, top + 8, top + 199.25, top + 256, height}) {
            close(fit.toLayoutY(fit.toScreenY(y)), y, "click/release y round trip");
        }
        for (double delta : new double[] {-19.5, -1, 0, 0.25, 22}) {
            close(fit.toLayoutDelta(delta * fit.scale()), delta, "drag delta round trip");
            close(fit.toLayoutX(fit.toScreenX(left) + delta) - left, fit.toLayoutDelta(delta), "drag and point mapping differ");
        }
        check(fit.toLayoutX(fit.toScreenX(left) - 1) < left, "outside clicks moved into container");
        check(fit.toLayoutY(fit.toScreenY(top + 258) + 1) > top + 258, "outside releases moved into container");
        // The player preview's transformed scissor and the independent outfit pose use this same mapping.
        int windowX = left + 18;
        close((fit.toScreenX(windowX + 26) + fit.toScreenX(windowX + 75)) / 2,
                fit.toScreenX(windowX + 50.5), "preview center differs from scissor");
        close(fit.toScreenY(top + 248) - fit.toScreenY(top + 178), 70.0 * fit.scale(), "preview scissor height");
        close(fit.toScreenX(windowX + 43 + 43) - fit.toScreenX(windowX + 43), 43.0 * fit.scale(), "outfit pose scale");
    }

    private static void verifySmallViewportControls() {
        SewingScreenFit fit = fit(320, 240);
        check(!fit.isIdentity(), "GUI3 must fit locally");
        int left = (320 - 331) / 2 - 18;
        int top = (240 - 258) / 2;
        check(fit.toScreenY(top + 7) > 4, "search box still above viewport");
        check(fit.toScreenY(top + 5) > 4, "part filter still above viewport");
        check(fit.toScreenX(left + 18 + 324) < 316, "last part filter clipped");
        close(fit.scale(), 312.0F / 349.0F, "unexpected GUI3 scale");
    }

    private static void verifyLargeViewportParity() {
        for (int width : new int[] {427, 480, 640, 960}) {
            SewingScreenFit fit = fit(width, 360);
            check(fit.isIdentity(), "large viewport moved or scaled");
            close(fit.toScreenX(123.25), 123.25, "large x changed");
            close(fit.toScreenY(234.5), 234.5, "large y changed");
            close(fit.toLayoutDelta(-7.25), -7.25, "large drag changed");
        }
    }

    private static void verifyTooltipBounds() {
        SewingScreenFit fit = fit(320, 240);
        for (int point : new int[] {-1000, -30, 0, 160, 400, 1000}) {
            int x = fit.tooltipX(point, 140, 320);
            int y = fit.tooltipY(point, 45, 240);
            check(x >= 4 && x + 140 <= 316, "tooltip horizontal clipping");
            check(y >= 4 && y + 45 <= 236, "tooltip vertical clipping");
        }
    }

    private static void verifyLongNameScissor() {
        for (int width : new int[] {320, 480}) {
            int height = width == 320 ? 240 : 360;
            SewingScreenFit fit = fit(width, height);
            int windowX = (width - 331) / 2;
            int top = (height - 258) / 2;
            int minX = windowX + 90 + 2;
            int minY = top + 43;
            int maxX = minX + 130;
            int maxY = minY + 15;
            int glyphY = (minY + maxY - 9) / 2 + 1;
            Matrix4f pose = new Matrix4f().translate((float) fit.offsetX(), (float) fit.offsetY(), 0)
                    .scale(fit.scale(), fit.scale(), 1);
            ScreenRectangle bounds = GuiScissor.transformAxisAligned(
                    new ScreenRectangle(minX, minY, maxX - minX, maxY - minY), pose);
            check(bounds.top() <= Math.floor(fit.toScreenY(glyphY)), "long name clipped above glyphs");
            check(bounds.bottom() >= Math.ceil(fit.toScreenY(glyphY + 9)), "long name clipped below glyphs");
            close(bounds.left(), Math.floor(fit.toScreenX(minX)), "long-name scissor x");
            close(bounds.width(), Math.floor(130.0 * fit.scale()), "long-name scissor width");
            if (fit.isIdentity()) {
                check(bounds.top() == minY && bounds.bottom() == maxY, "unscaled text clip changed");
            } else {
                check(bounds.top() != minY, "GUI3 clip remained in unscaled coordinates");
            }
        }
    }

    private static void close(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 0.0001) {
            throw new AssertionError(message + ": " + actual + " != " + expected);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
