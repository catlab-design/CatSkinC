package com.sammy.catskinc.client;

import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceManager;

/**
 * An uncompressed RGBA texture whose CPU-side image is released right after upload, so the
 * lossless near-player skin costs VRAM only. Not a {@code DynamicTexture}, so F3+S skips it.
 */
final class RawSkinTexture extends AbstractTexture {
    private final int width;
    private final int height;
    private NativeImage pending;

    RawSkinTexture(NativeImage image) {
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.pending = image;
    }

    @Override
    public void load(ResourceManager manager) {
        // Uploaded explicitly via upload(); nothing to load from resources.
    }

    /** Uploads and frees the image. Must run on the render thread. */
    void upload() {
        NativeImage image = pending;
        if (image == null) {
            return;
        }
        pending = null;
        TextureUtil.prepareImage(getGlId(), width, height);
        image.upload(0, 0, 0, true);
    }

    long byteSize() {
        return (long) width * height * 4L;
    }

    @Override
    public void close() {
        NativeImage image = pending;
        pending = null;
        if (image != null) {
            image.close();
        }
        super.close();
    }
}
