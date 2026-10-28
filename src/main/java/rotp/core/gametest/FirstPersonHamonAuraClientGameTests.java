package rotp.core.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 FirstPersonHamonAura.renderParticles turned the lightmap on (LightTexture.turnOnLightLayer) before it drew an
 * arm's aura. With both arms drawn, the first arm's pass flushes an entity render type whose clear step unbinds the
 * lightmap, so the second arm's aura, drawn with the particle shader, which multiplies by the lightmap, comes out black
 * and adds nothing under its additive blending. FirstPersonHamonAura cannot be loaded here: this reads its source.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FirstPersonHamonAuraClientGameTests {
	private static final String FILE = "src/main/java/rotp/core/impl/powers/hamon/client/particle/custom/FirstPersonHamonAura.java";
	private static final String TURN_ON = "lightTexture().turnOnLightLayer();";

	private FirstPersonHamonAuraClientGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void eachArmsFirstPersonAuraTurnsTheLightmapOnBeforeItIsDrawnAsIn116(GameTestHelper helper) {
		String source = null;
		for (Path at = Path.of("").toAbsolutePath(); at != null && source == null; at = at.getParent()) {
			Path file = at.resolve(FILE);
			if (Files.isRegularFile(file)) {
				try {
					source = Files.readString(file);
				}
				catch (IOException e) {
					throw new IllegalStateException("Could not read " + file, e);
				}
			}
		}
		helper.assertTrue(source != null, "Fixture: FirstPersonHamonAura.java not found above " + Path.of("").toAbsolutePath());
		String code = source.replaceAll("//[^\\n]*", "").replaceAll("\\s+", "");
		String render = body(helper, code, "publicvoidrenderParticles(");
		String draw = body(helper, code, "privatestaticvoiddrawParticleMesh(");
		int firstBegin = render.indexOf(".begin(");
		int firstDraw = render.indexOf("drawParticleMesh(");
		helper.assertTrue(firstBegin > 0 && firstDraw > firstBegin && draw.contains("BufferUploader.drawWithShader("),
				"Fixture: renderParticles no longer begins its particle buffers and draws them through drawParticleMesh");
		int inRender = render.indexOf(TURN_ON);
		int inDraw = draw.indexOf(TURN_ON);
		boolean beforeEveryDraw = inRender >= 0 && inRender < firstBegin && render.indexOf("for(") > inRender
				|| inDraw >= 0 && inDraw < draw.indexOf("BufferUploader.drawWithShader(");
		helper.assertTrue(beforeEveryDraw, "FirstPersonHamonAura draws an arm's aura without turning the lightmap on first"
				+ " (1.16 renderParticles: lightTexture.turnOnLightLayer()), so with both arms drawn the second arm's aura is"
				+ " black and invisible under additive blending");
		helper.succeed();
	}

	// the method's body, whitespace and line comments removed, up to the next method of the class
	private static String body(GameTestHelper helper, String code, String signature) {
		int start = code.indexOf(signature);
		helper.assertTrue(start >= 0, "Fixture: " + signature + " not found");
		int depth = 0;
		int open = code.indexOf('{', start);
		for (int i = open; i < code.length(); i++) {
			char c = code.charAt(i);
			if (c == '{') depth++;
			else if (c == '}' && --depth == 0) {
				return code.substring(open, i + 1);
			}
		}
		throw new IllegalStateException("Unbalanced braces after " + signature);
	}
}
