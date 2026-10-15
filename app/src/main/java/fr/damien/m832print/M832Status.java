package fr.damien.m832print;

import android.content.Context;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Parses cover and paper responses observed on a physical M832 over Bluetooth serial. */
final class M832Status {
    static final int UNKNOWN = -1;
    static final int NO_PAPER = 0;
    static final int PAPER_READY = 1;
    static final int COVER_CLOSED = 0;
    static final int COVER_OPEN = 1;

    static final class Reading {
        final int cover;
        final int paper;

        Reading(int cover, int paper) {
            this.cover = cover;
            this.paper = paper;
        }

        boolean ready() { return cover == COVER_CLOSED && paper == PAPER_READY; }
    }

    private M832Status() { }

    static Reading query(Context context, InputStream input, OutputStream output) throws IOException, InterruptedException {
        output.write(new byte[]{0x1F, 0x11, 0x12, 0x1F, 0x11, 0x11});
        output.flush();
        long deadline = SystemClock.elapsedRealtime() + 1800;
        int previous2 = -1;
        int previous1 = -1;
        int cover = UNKNOWN;
        int paper = UNKNOWN;
        while (SystemClock.elapsedRealtime() < deadline) {
            int available = input.available();
            if (available == 0) {
                Thread.sleep(30);
                continue;
            }
            for (int i = 0; i < available; i++) {
                int value = input.read();
                if (value < 0) throw new IOException(context.getString(R.string.connection_interrupted));
                if (previous2 == 0x1A && previous1 == 0x06) {
                    if (value == 0x89) paper = PAPER_READY;
                    if (value == 0x88) paper = NO_PAPER;
                }
                if (previous2 == 0x1A && previous1 == 0x05) {
                    if (value == 0x98) cover = COVER_CLOSED;
                    if (value == 0x99) cover = COVER_OPEN;
                }
                if (cover != UNKNOWN && paper != UNKNOWN) return new Reading(cover, paper);
                previous2 = previous1;
                previous1 = value;
            }
        }
        return new Reading(cover, paper);
    }

    static Reading queryWithRetry(Context context, InputStream input, OutputStream output)
            throws IOException, InterruptedException {
        Reading reading = new Reading(UNKNOWN, UNKNOWN);
        for (int attempt = 0; attempt < 3; attempt++) {
            reading = query(context, input, output);
            if (reading.cover != UNKNOWN && reading.paper != UNKNOWN) return reading;
            Thread.sleep(300);
        }
        return reading;
    }
}
