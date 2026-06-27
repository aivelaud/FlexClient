package com.flex.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * RenderUtils — ESP/Tracers/NameTags için yardımcı çizim sınıfı.
 * WorldRenderEvents context ile çalışır.
 */
public class RenderUtils {

    /**
     * ARGB rengini R,G,B,A bileşenlerine ayırır (0.0-1.0 arası float).
     */
    public static float[] argbToFloat(int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ((argb      ) & 0xFF) / 255f;
        return new float[]{r, g, b, a};
    }

    /**
     * 3D world'de bir kutu (Box) çizer — yalnızca kenarlar (wireframe).
     * matrices: context.matrixStack() 
     * box: entity.getBoundingBox().offset(-cameraPos)
     */
    public static void drawBoxOutline(MatrixStack matrices, Box box, int color, float lineWidth) {
        float[] c = argbToFloat(color);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.getBuffer();
        buf.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        Matrix4f m = matrices.peek().getPositionMatrix();
        double x0=box.minX, y0=box.minY, z0=box.minZ;
        double x1=box.maxX, y1=box.maxY, z1=box.maxZ;

        // Bottom face
        line(buf, m, x0,y0,z0, x1,y0,z0, c);
        line(buf, m, x1,y0,z0, x1,y0,z1, c);
        line(buf, m, x1,y0,z1, x0,y0,z1, c);
        line(buf, m, x0,y0,z1, x0,y0,z0, c);
        // Top face
        line(buf, m, x0,y1,z0, x1,y1,z0, c);
        line(buf, m, x1,y1,z0, x1,y1,z1, c);
        line(buf, m, x1,y1,z1, x0,y1,z1, c);
        line(buf, m, x0,y1,z1, x0,y1,z0, c);
        // Vertical edges
        line(buf, m, x0,y0,z0, x0,y1,z0, c);
        line(buf, m, x1,y0,z0, x1,y1,z0, c);
        line(buf, m, x1,y0,z1, x1,y1,z1, c);
        line(buf, m, x0,y0,z1, x0,y1,z1, c);

        tessellator.draw();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Dolu (filled) semi-transparent kutu çizer.
     */
    public static void drawBoxFilled(MatrixStack matrices, Box box, int color) {
        float[] c = argbToFloat(color);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        Matrix4f m = matrices.peek().getPositionMatrix();
        double x0=box.minX, y0=box.minY, z0=box.minZ;
        double x1=box.maxX, y1=box.maxY, z1=box.maxZ;

        // All 6 faces
        quad(buf, m, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, c); // bottom
        quad(buf, m, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0, c); // top
        quad(buf, m, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, c); // north
        quad(buf, m, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1, c); // south
        quad(buf, m, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0, c); // west
        quad(buf, m, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, c); // east

        tessellator.draw();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Kameradan bir noktaya çizgi çizer (Tracer).
     */
    public static void drawTracer(MatrixStack matrices, Vec3d from, Vec3d to, int color, float lineWidth) {
        float[] c = argbToFloat(color);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.getBuffer();
        buf.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        Matrix4f m = matrices.peek().getPositionMatrix();
        buf.vertex(m, (float)from.x, (float)from.y, (float)from.z).color(c[0],c[1],c[2],c[3]).next();
        buf.vertex(m, (float)to.x,   (float)to.y,   (float)to.z  ).color(c[0],c[1],c[2],c[3]).next();

        tessellator.draw();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void line(BufferBuilder buf, Matrix4f m,
            double x0, double y0, double z0,
            double x1, double y1, double z1, float[] c) {
        buf.vertex(m,(float)x0,(float)y0,(float)z0).color(c[0],c[1],c[2],c[3]).next();
        buf.vertex(m,(float)x1,(float)y1,(float)z1).color(c[0],c[1],c[2],c[3]).next();
    }

    private static void quad(BufferBuilder buf, Matrix4f m,
            double x0,double y0,double z0, double x1,double y1,double z1,
            double x2,double y2,double z2, double x3,double y3,double z3, float[] c) {
        buf.vertex(m,(float)x0,(float)y0,(float)z0).color(c[0],c[1],c[2],c[3]).next();
        buf.vertex(m,(float)x1,(float)y1,(float)z1).color(c[0],c[1],c[2],c[3]).next();
        buf.vertex(m,(float)x2,(float)y2,(float)z2).color(c[0],c[1],c[2],c[3]).next();
        buf.vertex(m,(float)x3,(float)y3,(float)z3).color(c[0],c[1],c[2],c[3]).next();
    }

    /**
     * 2D ekranda (HUD) text çizer — WorldRender dışında kullanılır.
     */
    public static void drawText(DrawContext ctx, String text, int x, int y, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.drawTextWithShadow(mc.textRenderer, text, x, y, color);
    }
}
