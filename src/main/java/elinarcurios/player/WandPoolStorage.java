package elinarcurios.player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Persistence for {@link WandPoolData} with an atomic write.
 *
 * <p>
 * Vanilla {@code MapStorage.saveData} writes straight to the target file; a crash in that window can
 * leave a truncated gzip and wipe every player's pool. This class instead writes
 * {@code <file>.atomic.tmp}, fsyncs it, then moves it over the target (ATOMIC_MOVE, with a
 * REPLACE_EXISTING fallback for filesystems that reject atomic moves). A crash leaves either the old
 * complete file or the new complete file.
 *
 * <p>
 * The on-disk wrapper stays {@code {data: <payload>}} — the same layout vanilla MapStorage used — so
 * a world written by either this class or the previous MapStorage-based build stays readable.
 *
 * <p>
 * Saves on world save (Forge event), every {@link #SAVE_INTERVAL_TICKS} server ticks (crash safety)
 * and on server stopping. All calls run on the server thread.
 */
public final class WandPoolStorage {

    private static final Logger LOGGER = LogManager.getLogger("ElinarCurios");

    /** Flush dirty data at most this often (200 ticks = 10 s). */
    private static final int SAVE_INTERVAL_TICKS = 200;

    private static final String TAG_DATA = "data";

    private static WandPoolData data;
    private static int ticks;

    private WandPoolStorage() {}

    /** Loaded world-wide data; a transient empty object before the server/world exists. */
    public static WandPoolData data() {
        if (data != null) return data;
        if (mapFile() == null) return new WandPoolData(WandPoolData.NAME);
        data = load();
        return data;
    }

    public static void saveIfDirty() {
        final WandPoolData current = data;
        if (current == null || !current.isDirty()) return;
        if (save(current)) current.setDirty(false);
    }

    public static void tick() {
        if (++ticks < SAVE_INTERVAL_TICKS) return;
        ticks = 0;
        saveIfDirty();
    }

    /** Drops the cached data when the world/server goes away (single-player world switch). */
    public static void reset() {
        data = null;
        ticks = 0;
    }

    private static WandPoolData load() {
        final WandPoolData loaded = new WandPoolData(WandPoolData.NAME);
        final File file = mapFile();
        if (file != null && file.isFile()) {
            try (FileInputStream in = new FileInputStream(file)) {
                final NBTTagCompound root = CompressedStreamTools.readCompressed(in);
                final NBTTagCompound payload = root.hasKey(TAG_DATA, 10) ? root.getCompoundTag(TAG_DATA) : root;
                loaded.readFromNBT(payload);
            } catch (final Exception e) {
                LOGGER.error("[ElinarCurios] could not read wand pool data; starting with an empty pool", e);
            }
        }
        loaded.setDirty(false);
        return loaded;
    }

    private static boolean save(final WandPoolData current) {
        final File target = mapFile();
        if (target == null) return false;

        final NBTTagCompound payload = new NBTTagCompound();
        current.writeToNBT(payload);
        final NBTTagCompound root = new NBTTagCompound();
        root.setTag(TAG_DATA, payload);

        final File tmp = new File(target.getParentFile(), target.getName() + ".atomic.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            CompressedStreamTools.writeCompressed(root, out);
            out.getFD()
                .sync();
        } catch (final Exception e) {
            LOGGER.warn("[ElinarCurios] could not write wand pool temp file", e);
            return false;
        }

        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (final Exception atomicFailure) {
            // Some filesystems reject ATOMIC_MOVE (or ATOMIC+REPLACE); a normal replace is still
            // vastly better than the old direct overwrite because the temp file was fully written.
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (final Exception fallback) {
                LOGGER.warn("[ElinarCurios] could not replace wand pool data file", fallback);
                return false;
            }
        }
    }

    private static File mapFile() {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        final WorldServer overworld = server.worldServerForDimension(0);
        if (overworld == null || overworld.getSaveHandler() == null) return null;
        return overworld.getSaveHandler()
            .getMapFileFromName(WandPoolData.NAME);
    }
}
