package dev.engine.bindings.assimp;

import dev.engine.core.asset.AssetLoader;
import dev.engine.core.asset.AssetSource;
import dev.engine.core.asset.TextureData;
import dev.engine.core.asset.TextureData.PixelFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;

/**
 * Loads DDS (DirectDraw Surface) texture files.
 * Supports BC1, BC3, BC5, BC7 compressed formats and uncompressed RGBA.
 */
public class DdsLoader implements AssetLoader<TextureData> {

    private static final int DDS_MAGIC = 0x20534444; // "DDS " in little-endian

    // DDS header offsets (after the 4-byte magic)
    private static final int HEADER_SIZE = 124;
    private static final int OFFSET_HEIGHT = 8;
    private static final int OFFSET_WIDTH = 12;
    private static final int OFFSET_MIP_MAP_COUNT = 24;
    private static final int OFFSET_PF_FLAGS = 76;
    private static final int OFFSET_PF_FOURCC = 80;
    private static final int OFFSET_PF_RGB_BIT_COUNT = 84;
    private static final int OFFSET_PF_R_MASK = 88;
    private static final int OFFSET_PF_G_MASK = 92;
    private static final int OFFSET_PF_B_MASK = 96;
    private static final int OFFSET_PF_A_MASK = 100;

    // Pixel format flags
    private static final int DDPF_FOURCC = 0x4;
    private static final int DDPF_RGB = 0x40;

    // FourCC values
    private static final int FOURCC_DXT1 = makeFourCC('D', 'X', 'T', '1');
    private static final int FOURCC_DXT3 = makeFourCC('D', 'X', 'T', '3');
    private static final int FOURCC_DXT5 = makeFourCC('D', 'X', 'T', '5');
    private static final int FOURCC_DX10 = makeFourCC('D', 'X', '1', '0');

    // DXGI format values for DX10 extended header
    private static final int DXGI_FORMAT_BC1_UNORM = 71;
    private static final int DXGI_FORMAT_BC3_UNORM = 77;
    private static final int DXGI_FORMAT_BC5_UNORM = 83;
    private static final int DXGI_FORMAT_BC7_UNORM = 98;

    @Override
    public boolean supports(String path) {
        return path.toLowerCase().endsWith(".dds");
    }

    @Override
    public Class<TextureData> assetType() {
        return TextureData.class;
    }

    @Override
    public TextureData load(AssetSource.AssetData data) {
        ByteBuffer buf = ByteBuffer.wrap(data.bytes()).order(ByteOrder.LITTLE_ENDIAN);

        // Validate magic number
        int magic = buf.getInt(0);
        if (magic != DDS_MAGIC) {
            throw new RuntimeException("Not a DDS file: " + data.path());
        }

        // Parse header (starts at byte 4)
        int headerStart = 4;
        int height = buf.getInt(headerStart + OFFSET_HEIGHT);
        int width = buf.getInt(headerStart + OFFSET_WIDTH);
        int mipMapCount = buf.getInt(headerStart + OFFSET_MIP_MAP_COUNT);
        if (mipMapCount == 0) mipMapCount = 1;

        int pfFlags = buf.getInt(headerStart + OFFSET_PF_FLAGS);
        int fourCC = buf.getInt(headerStart + OFFSET_PF_FOURCC);

        int dataOffset = 4 + HEADER_SIZE; // magic + header
        PixelFormat format;

        if ((pfFlags & DDPF_FOURCC) != 0) {
            if (fourCC == FOURCC_DX10) {
                // DX10 extended header: 20 bytes after the standard header
                int dxgiFormat = buf.getInt(dataOffset);
                dataOffset += 20; // skip DX10 header
                format = mapDxgiFormat(dxgiFormat, data.path());
            } else {
                format = mapFourCC(fourCC, data.path());
            }
        } else if ((pfFlags & DDPF_RGB) != 0) {
            // Uncompressed format
            int rgbBitCount = buf.getInt(headerStart + OFFSET_PF_RGB_BIT_COUNT);
            int rMask = buf.getInt(headerStart + OFFSET_PF_R_MASK);
            int aMask = buf.getInt(headerStart + OFFSET_PF_A_MASK);

            if (rgbBitCount == 32 && aMask != 0) {
                format = PixelFormat.RGBA8;
            } else if (rgbBitCount == 24) {
                format = PixelFormat.RGB8;
            } else {
                format = PixelFormat.RGBA8; // best-effort fallback
            }
        } else {
            throw new RuntimeException("Unsupported DDS pixel format flags in: " + data.path());
        }

        // Extract pixel data
        int remaining = data.bytes().length - dataOffset;
        ByteBuffer pixels = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder());
        buf.position(dataOffset);
        pixels.put(buf);
        pixels.flip();

        return TextureData.of(width, height, format, pixels);
    }

    private static PixelFormat mapFourCC(int fourCC, String path) {
        if (fourCC == FOURCC_DXT1) return PixelFormat.BC1;
        if (fourCC == FOURCC_DXT3) return PixelFormat.BC3;
        if (fourCC == FOURCC_DXT5) return PixelFormat.BC3;
        throw new RuntimeException("Unsupported DDS FourCC: " + fourCCToString(fourCC) + " in " + path);
    }

    private static PixelFormat mapDxgiFormat(int dxgiFormat, String path) {
        return switch (dxgiFormat) {
            case DXGI_FORMAT_BC1_UNORM -> PixelFormat.BC1;
            case DXGI_FORMAT_BC3_UNORM -> PixelFormat.BC3;
            case DXGI_FORMAT_BC5_UNORM -> PixelFormat.BC5;
            case DXGI_FORMAT_BC7_UNORM -> PixelFormat.BC7;
            default -> throw new RuntimeException("Unsupported DXGI format: " + dxgiFormat + " in " + path);
        };
    }

    private static int makeFourCC(char a, char b, char c, char d) {
        return (a) | (b << 8) | (c << 16) | (d << 24);
    }

    private static String fourCCToString(int fourCC) {
        return "" + (char) (fourCC & 0xFF)
                + (char) ((fourCC >> 8) & 0xFF)
                + (char) ((fourCC >> 16) & 0xFF)
                + (char) ((fourCC >> 24) & 0xFF);
    }
}
