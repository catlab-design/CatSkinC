package com.sammy.catskinc.client;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;

/**
 * A GPU texture holding pre-encoded S3TC data. It never keeps an RGBA copy, and because it is not
 * a {@code DynamicTexture} the F3+S debug dump skips it.
 */
final class CompressedSkinTexture extends AbstractTexture {
    private final int width;
    private final int height;
    private final DxtEncoder.Format format;
    private final ByteBuffer data;

    CompressedSkinTexture(int width, int height, DxtEncoder.Format format, ByteBuffer data) {
        this.width = width;
        this.height = height;
        this.format = format;
        this.data = data;
    }

    @Override
    public void load(ResourceManager manager) {
        // Uploaded explicitly via upload(); nothing to load from resources.
    }

    /** Uploads the compressed data. Must run on the render thread. Returns false on a GL error. */
    boolean upload() {
        int id = getGlId();
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0.0F);
        setFilter(false, false);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // Drain errors raised elsewhere so the check below only sees ours.
        }
        ByteBuffer view = data.duplicate();
        view.position(0);
        GL13.glCompressedTexImage2D(GL11.GL_TEXTURE_2D, 0, format.glInternalFormat(), width, height, 0, view);
        return GL11.glGetError() == GL11.GL_NO_ERROR;
    }

    int byteSize() {
        return data.capacity();
    }
}
