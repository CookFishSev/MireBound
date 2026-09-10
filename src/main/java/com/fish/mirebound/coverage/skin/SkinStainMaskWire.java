package com.fish.mirebound.coverage.skin;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.BitSet;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** The server rate-limits this bounded envelope before inflating client-authored bits. */
public final class SkinStainMaskWire {
    public static final int MAX_COMPRESSED_BYTES = 24 * 1024;
    private final int width, height;
    private final byte[] compressed;

    public SkinStainMaskWire(int width, int height, byte[] compressed) {
        if (width < 1 || height < 1 || width > SkinStainMask.MAX_DIMENSION || height > SkinStainMask.MAX_DIMENSION
                || compressed.length > MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Invalid skin mask envelope");
        this.width=width; this.height=height; this.compressed=compressed.clone();
    }

    public static SkinStainMaskWire encode(SkinStainMask mask) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(mask.copyBits().toByteArray()); deflater.finish();
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            byte[] chunk=new byte[4096];
            while (!deflater.finished()) {
                int size=deflater.deflate(chunk);
                if (size == 0 || result.size()+size>MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Skin mask too complex to share");
                result.write(chunk,0,size);
            }
            return new SkinStainMaskWire(mask.width(),mask.height(),result.toByteArray());
        } finally { deflater.end(); }
    }

    public SkinStainMask decode() {
        Inflater inflater=new Inflater();
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            byte[] chunk=new byte[4096];
            int limit=(width*height+7)/8;
            while (!inflater.finished()) {
                int size=inflater.inflate(chunk);
                if (size==0 && !inflater.finished() || result.size()+size>limit)
                    throw new IllegalArgumentException("Invalid compressed skin mask");
                result.write(chunk,0,size);
            }
            if (inflater.getRemaining()!=0) throw new IllegalArgumentException("Trailing skin mask data");
            return new SkinStainMask(width,height,BitSet.valueOf(result.toByteArray()));
        } catch (DataFormatException error) { throw new IllegalArgumentException("Invalid compressed skin mask",error); }
        finally { inflater.end(); }
    }

    public static SkinStainMaskWire read(RegistryFriendlyByteBuf buffer) {
        return new SkinStainMaskWire(buffer.readVarInt(),buffer.readVarInt(),buffer.readByteArray(MAX_COMPRESSED_BYTES));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(width); buffer.writeVarInt(height); buffer.writeByteArray(compressed);
    }
    @Override public int hashCode() { return 31*(31*width+height)+Arrays.hashCode(compressed); }
    @Override public boolean equals(Object other) {
        return other instanceof SkinStainMaskWire wire && width==wire.width && height==wire.height && Arrays.equals(compressed,wire.compressed);
    }
}
