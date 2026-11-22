package elinarcurios.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temporary diagnostic counters for the Thaumcraft reveal features (node rendering and Waila).
 * Writes a small report to {@code logs/elinarcurios-probe.txt} at most once per second so the
 * counters can be inspected without restarting the game. Remove once the features are confirmed.
 */
public final class ElinarProbe {

    public static final AtomicInteger NODE_REDIRECT = new AtomicInteger();
    public static final AtomicInteger NODE_REDIRECT_FORCED = new AtomicInteger();
    public static final AtomicInteger NODE_ARG = new AtomicInteger();
    public static final AtomicInteger NODE_ARG_FORCED = new AtomicInteger();
    public static final AtomicInteger GOGGLE = new AtomicInteger();
    public static final AtomicInteger GOGGLE_FORCED = new AtomicInteger();
    public static final AtomicInteger WAILA = new AtomicInteger();
    public static final AtomicInteger WAILA_FORCED = new AtomicInteger();

    private static volatile long lastDump;

    private ElinarProbe() {}

    public static void dump() {
        final long now = System.currentTimeMillis();
        if (lastDump != 0L && now - lastDump < 1000L) return;
        lastDump = now;
        try {
            final File file = new File("logs/elinarcurios-probe.txt");
            final File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            final StringBuilder sb = new StringBuilder();
            sb.append("time=")
                .append(new Date())
                .append('\n');
            sb.append("nodeRedirect=")
                .append(NODE_REDIRECT.get())
                .append(" forced=")
                .append(NODE_REDIRECT_FORCED.get())
                .append('\n');
            sb.append("nodeArg=")
                .append(NODE_ARG.get())
                .append(" forced=")
                .append(NODE_ARG_FORCED.get())
                .append('\n');
            sb.append("goggle=")
                .append(GOGGLE.get())
                .append(" forced=")
                .append(GOGGLE_FORCED.get())
                .append('\n');
            sb.append("waila=")
                .append(WAILA.get())
                .append(" forced=")
                .append(WAILA_FORCED.get())
                .append('\n');
            Files.write(
                file.toPath(),
                sb.toString()
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }
}
