package dev.alfxyz.leoneclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jspecify.annotations.Nullable;

/**
 * A batch of pre-transformed textured quads, in painter's order. Each vertex is
 * four floats (x, y, u, v) plus one ARGB colour.
 */
public record CanvasElement(
	RenderPipeline pipeline,
	TextureSetup textureSetup,
	float[] data,
	int[] colors,
	int vertexCount,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
	@Override
	public void buildVertices(final VertexConsumer vertexConsumer) {
		float[] d = data;
		for (int i = 0, o = 0; i < vertexCount; i++, o += 4) {
			vertexConsumer.addVertex(d[o], d[o + 1], 0.0F).setUv(d[o + 2], d[o + 3]).setColor(colors[i]);
		}
	}
}
