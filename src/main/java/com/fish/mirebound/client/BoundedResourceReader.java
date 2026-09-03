package com.fish.mirebound.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads resource-pack data with bounds before handing it to native decoders. */
public final class BoundedResourceReader {
    public static final int MAX_IMAGE_DIMENSION = 4096;
    public static final long MAX_IMAGE_PIXELS = 16L * 1024L * 1024L;
    public static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private BoundedResourceReader() {
    }

    public static byte[] readBytes(InputStream input, int maximumBytes) throws IOException {
        if (input == null || maximumBytes <= 0) {
            throw new IOException("Invalid bounded resource input");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.min(maximumBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > maximumBytes - total) {
                throw new IOException("Resource exceeds bounded input size");
            }
            output.write(buffer, 0, count);
            total += count;
        }
        return output.toByteArray();
    }

    public static NativeImage readImage(InputStream input) throws IOException {
        byte[] data = readBytes(input, MAX_IMAGE_BYTES);
        validatePngHeader(data);
        NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new IOException("Image decoder returned no image");
        }
        if (!isImageSizeAllowed(image.getWidth(), image.getHeight())) {
            image.close();
            throw new IOException("Decoded image exceeds bounded dimensions");
        }
        return image;
    }

    public static boolean isImageSizeAllowed(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_IMAGE_DIMENSION
                && height <= MAX_IMAGE_DIMENSION
                && (long) width * height <= MAX_IMAGE_PIXELS;
    }

    private static void validatePngHeader(byte[] data) throws IOException {
        if (data.length < 33) {
            throw new IOException("PNG header is incomplete");
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (data[index] != PNG_SIGNATURE[index]) {
                throw new IOException("Only bounded PNG resources are supported");
            }
        }
        int chunkLength = readInt(data, 8);
        if (chunkLength < 13 || !matches(data, 12, "IHDR")) {
            throw new IOException("PNG IHDR is invalid");
        }
        int width = readInt(data, 16);
        int height = readInt(data, 20);
        if (!isImageSizeAllowed(width, height)) {
            throw new IOException("PNG dimensions exceed bounded limits");
        }
    }

    private static int readInt(byte[] data, int offset) {
        return data[offset] << 24 | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8 | (data[offset + 3] & 0xFF);
    }

    private static boolean matches(byte[] data, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            if (data[offset + index] != value.charAt(index)) {
                return false;
            }
        }
        return true;
    }
}
