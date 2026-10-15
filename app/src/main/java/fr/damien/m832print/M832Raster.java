package fr.damien.m832print;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;

/** Streams a full-page monochrome raster using the commands tested on the M832. */
final class M832Raster {
    private M832Raster() {}

    static void writeUncompressed(Context context, OutputStream out, Bitmap bitmap, int density,
                                  BooleanSupplier cancelled) throws IOException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || (width & 7) != 0 || width > 4096 || height <= 0 || height > 65535) {
            throw new IllegalArgumentException(context.getString(R.string.invalid_raster_dimensions));
        }
        int bytesPerRow = width / 8;
        out.write(new byte[]{0x1F, 0x11, 0x12}); // Cover query used by the tested print sequence.
        out.write(new byte[]{0x1B, 0x40});       // ESC @
        // Medium keeps the byte sequence validated on the physical printer.
        if (density == 1 || density == 3) {
            out.write(new byte[]{0x1B, 0x4E, 0x07, (byte) (density == 1 ? 4 : 12)});
        }
        out.write(new byte[]{0x1D, 0x76, 0x30, 0x00,
                (byte) bytesPerRow, (byte) (bytesPerRow >> 8),
                (byte) height, (byte) (height >> 8)});
        byte[] row = new byte[bytesPerRow];
        int[] pixels = new int[width];
        int threshold = density == 1 ? 125 : density == 3 ? 185 : 150;
        for (int y = 0; y < height; y++) {
            if ((y & 31) == 0 && cancelled.getAsBoolean())
                throw new InterruptedIOException(context.getString(R.string.print_cancelled));
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
            java.util.Arrays.fill(row, (byte) 0);
            for (int x = 0; x < width; x++) {
                int pixel = pixels[x];
                int luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000;
                if (luminance < threshold) row[x >> 3] |= (byte) (0x80 >> (x & 7));
            }
            out.write(row);
        }
    }
}
