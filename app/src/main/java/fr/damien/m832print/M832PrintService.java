package fr.damien.m832print;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintJobId;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local print service for PDFs supplied by Android's print preview. */
public final class M832PrintService extends PrintService {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int A4_WIDTH = 2480;
    private static final int A4_HEIGHT = 3508;
    private static final long MAX_PDF_BYTES = 100L * 1024 * 1024;
    private final ExecutorService queue = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<PrintJobId, Task> tasks = new ConcurrentHashMap<>();

    private static final class Task {
        final PrintJob job;
        final PrintJobId id;
        final ParcelFileDescriptor data;
        final PageRange[] ranges;
        final int copies;
        final int density;
        final AtomicBoolean cancelled = new AtomicBoolean();
        volatile BluetoothSocket socket;

        Task(PrintJob job, ParcelFileDescriptor data, PageRange[] ranges, int copies, int density) {
            this.job = job;
            this.id = job.getId();
            this.data = data;
            this.ranges = ranges;
            this.copies = copies;
            this.density = density;
        }
    }

    @Override protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new M832DiscoverySession();
    }

    @Override protected void onPrintJobQueued(PrintJob job) {
        // PrintJob calls must stay on the main thread; capture job data before queuing work.
        ParcelFileDescriptor data = job.getDocument().getData();
        if (data == null) { job.fail(getString(R.string.print_data_missing)); return; }
        PageRange[] ranges = job.getInfo().getPages();
        int copies = Math.max(1, job.getInfo().getCopies());
        int density = job.hasAdvancedOption(AdvancedOptionsActivity.DENSITY_OPTION)
                ? job.getAdvancedIntOption(AdvancedOptionsActivity.DENSITY_OPTION) : 2;
        Task task = new Task(job, data, ranges, copies, density);
        if (!job.start()) {
            try { data.close(); } catch (IOException ignored) { }
            return;
        }
        tasks.put(task.id, task);
        queue.execute(() -> print(task));
    }

    @Override protected void onRequestCancelPrintJob(PrintJob job) {
        Task task = tasks.get(job.getId());
        if (task != null) task.cancelled.set(true);
        job.cancel();
        BluetoothSocket socket = task == null ? null : task.socket;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    @Override public void onDestroy() {
        queue.shutdownNow();
        for (Task task : tasks.values()) {
            task.cancelled.set(true);
            if (task.socket != null) {
                try { task.socket.close(); } catch (IOException ignored) { }
            }
        }
        tasks.clear();
        super.onDestroy();
    }

    private void print(Task task) {
        Exception failure = null;
        File pdfFile = null;
        try {
            if (task.cancelled.get()) return;
            pdfFile = copyPdfToPrivateFile(task);
            BluetoothDevice device = findBondedPrinter();
            if (device == null) throw new IOException(getString(R.string.printer_not_paired));
            try (ParcelFileDescriptor seekable = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer pdf = new PdfRenderer(seekable)) {
                int total = Math.max(1, pdf.getPageCount() * task.copies);
                int done = 0;
                for (int copy = 0; copy < task.copies; copy++) {
                    for (int index = 0; index < pdf.getPageCount(); index++) {
                        if (!selected(index, task.ranges)) continue;
                        if (task.cancelled.get()) return;
                        try (BluetoothSocket socket = connectReadySocket(task, device)) {
                            if (task.cancelled.get()) return;
                            OutputStream stream = socket.getOutputStream();
                            int pageNumber = index + 1;
                            int pageCount = pdf.getPageCount();
                            main.post(() -> {
                                if (!task.job.isCancelled())
                                    task.job.setStatus(getString(R.string.page_progress, pageNumber, pageCount));
                            });
                            Bitmap bitmap = renderPage(pdf, index);
                            try {
                                M832Raster.writeUncompressed(this, stream, bitmap, task.density, task.cancelled::get);
                                stream.flush();
                            } finally {
                                bitmap.recycle();
                            }
                            // Let the printer finish the last rows before closing the socket.
                            Thread.sleep(3000);
                        } finally {
                            task.socket = null;
                        }
                        float progress = Math.min(1f, ++done / (float) total);
                        main.post(() -> { if (!task.job.isCancelled()) task.job.setProgress(progress); });
                    }
                }
            }
        } catch (Exception error) {
            failure = error;
        } finally {
            if (pdfFile != null && !pdfFile.delete()) pdfFile.deleteOnExit();
            try { task.data.close(); } catch (IOException ignored) { }
            tasks.remove(task.id);
            final Exception result = failure;
            main.post(() -> {
                if (task.job.isCancelled()) return;
                if (result == null) task.job.complete();
                else {
                    String detail = result.getMessage() == null
                            ? getString(R.string.unexpected_error) : result.getMessage();
                    task.job.fail(getString(R.string.print_error, detail));
                }
            });
        }
    }

    private BluetoothSocket connectReadySocket(Task task, BluetoothDevice device)
            throws IOException, InterruptedException {
        boolean blocked = false;
        while (!task.cancelled.get()) {
            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP);
            task.socket = socket;
            boolean keep = false;
            try {
                socket.connect();
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                M832Status.Reading reading = M832Status.queryWithRetry(this, input, output);
                if (reading.ready()) {
                    if (blocked) Thread.sleep(1200);
                    if (blocked) main.post(() -> {
                        if (!task.job.isCancelled() && task.job.isBlocked()) task.job.start();
                    });
                    keep = true;
                    return socket;
                }
                String message = statusMessage(reading);
                if (!blocked) {
                    blocked = true;
                    main.post(() -> {
                        if (!task.job.isCancelled() && task.job.isStarted())
                            task.job.block(message);
                    });
                } else {
                    main.post(() -> {
                        if (!task.job.isCancelled() && task.job.isBlocked())
                            task.job.setStatus(message);
                    });
                }
                if (reading.cover == M832Status.UNKNOWN || reading.paper == M832Status.UNKNOWN) {
                    Thread.sleep(1200);
                    continue;
                }
                while (!task.cancelled.get()) {
                    Thread.sleep(1200);
                    reading = M832Status.queryWithRetry(this, input, output);
                    String nextMessage = statusMessage(reading);
                    main.post(() -> {
                        if (!task.job.isCancelled() && task.job.isBlocked()) task.job.setStatus(nextMessage);
                    });
                    if (reading.ready()) break;
                    if (reading.cover == M832Status.UNKNOWN || reading.paper == M832Status.UNKNOWN)
                        break; // Reconnect if a status response was lost.
                }
            } catch (IOException disconnected) {
                if (task.cancelled.get()) throw disconnected;
                if (!blocked) {
                    blocked = true;
                    main.post(() -> {
                        if (!task.job.isCancelled() && task.job.isStarted())
                            task.job.block(getString(R.string.printer_waiting));
                    });
                }
            } finally {
                if (!keep) {
                    try { socket.close(); } catch (IOException ignored) { }
                    task.socket = null;
                }
            }
            // Paper detection can precede the feed motor. Reconnect after the sheet settles.
            Thread.sleep(2500);
        }
        throw new IOException(getString(R.string.print_cancelled));
    }

    private String statusMessage(M832Status.Reading reading) {
        if (reading.cover == M832Status.COVER_OPEN) return getString(R.string.cover_close_prompt);
        if (reading.cover == M832Status.COVER_CLOSED && reading.paper == M832Status.NO_PAPER)
            return getString(R.string.paper_insert_prompt);
        return getString(R.string.status_checking);
    }

    private File copyPdfToPrivateFile(Task task) throws IOException {
        File temp = File.createTempFile("m832-print-", ".pdf", getCacheDir());
        boolean success = false;
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(task.data);
             OutputStream output = new FileOutputStream(temp)) {
            byte[] buffer = new byte[32 * 1024];
            long size = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (task.cancelled.get()) throw new IOException(getString(R.string.print_cancelled));
                size += count;
                if (size > MAX_PDF_BYTES) throw new IOException(getString(R.string.pdf_too_large));
                output.write(buffer, 0, count);
            }
            if (size == 0) throw new IOException(getString(R.string.pdf_empty));
            success = true;
            return temp;
        } finally {
            if (!success) temp.delete();
        }
    }

    private Bitmap renderPage(PdfRenderer pdf, int index) {
        PdfRenderer.Page page = pdf.openPage(index);
        try {
            Bitmap bitmap = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            float scale = Math.min(A4_WIDTH / (float) page.getWidth(), A4_HEIGHT / (float) page.getHeight());
            float x = (A4_WIDTH - page.getWidth() * scale) / 2f;
            float y = (A4_HEIGHT - page.getHeight() * scale) / 2f;
            Matrix transform = new Matrix();
            transform.setScale(scale, scale);
            transform.postTranslate(x, y);
            page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            return bitmap;
        } finally {
            page.close();
        }
    }

    private boolean selected(int index, PageRange[] ranges) {
        if (ranges == null || ranges.length == 0) return true;
        for (PageRange range : ranges) {
            if (index >= range.getStart() && index <= range.getEnd()) return true;
        }
        return false;
    }

    private BluetoothDevice findBondedPrinter() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null;
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return null;
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        for (BluetoothDevice device : bonded) {
            if ("M832".equalsIgnoreCase(device.getName())) return device;
        }
        return null;
    }

    private final class M832DiscoverySession extends PrinterDiscoverySession {
        private void publish() {
            if (findBondedPrinter() == null) return;
            PrinterId id = generatePrinterId("m832");
            PrinterCapabilitiesInfo capabilities = new PrinterCapabilitiesInfo.Builder(id)
                    .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                    .addResolution(new PrintAttributes.Resolution("300dpi", "300 dpi", 300, 300), true)
                    .setMinMargins(new PrintAttributes.Margins(300, 300, 300, 300))
                    .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                    .build();
            PrinterInfo info = new PrinterInfo.Builder(id, "Phomemo M832", PrinterInfo.STATUS_IDLE)
                    .setCapabilities(capabilities)
                    .build();
            addPrinters(Collections.singletonList(info));
        }

        @Override public void onStartPrinterDiscovery(List<PrinterId> priorityList) { publish(); }
        @Override public void onStopPrinterDiscovery() { }
        @Override public void onValidatePrinters(List<PrinterId> printerIds) { publish(); }
        @Override public void onStartPrinterStateTracking(PrinterId printerId) { publish(); }
        @Override public void onStopPrinterStateTracking(PrinterId printerId) { }
        @Override public void onDestroy() { }
    }
}
