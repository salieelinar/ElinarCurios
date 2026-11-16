package elinarcurios.command;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.FMLLog;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * {@code /Elinar gtid} —— exports the occupancy of the GT MetaTileEntity id table (1..32768) to
 * {@code logs/gregtech_machine_ids.csv}.
 *
 * <p>
 * The scan is deliberately spread over server ticks (512 ids per tick, about 3.2 seconds for the
 * whole range) so it cannot cause a visible lag spike. Only the final file path is echoed in chat;
 * individual rows are never printed in game.
 */
public final class GtidExport {

    /** Last id exported, per the user's requested 1..32768 range. */
    private static final int MAX_ID = 32768;
    /** Ids looked at per server tick (32768 / 512 = 64 ticks ~= 3.2 s). */
    private static final int IDS_PER_TICK = 512;
    private static final String FILE_NAME = "gregtech_machine_ids.csv";

    private static boolean running;
    private static int nextId;
    private static BufferedWriter writer;
    private static ICommandSender requester;
    private static File target;

    private GtidExport() {}

    /** Called from {@code /Elinar gtid}; returns immediately and finishes over later ticks. */
    public static void start(final ICommandSender sender) {
        if (running) {
            send(sender, EnumChatFormatting.YELLOW + "[Elinar] GT 机器 ID 表正在导出，请稍候。");
            return;
        }
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            send(sender, EnumChatFormatting.RED + "[Elinar] 当前没有服务端实例，无法导出。");
            return;
        }
        final File logs = server.getFile("logs");
        if (!logs.isDirectory() && !logs.mkdirs()) {
            send(sender, EnumChatFormatting.RED + "[Elinar] 无法创建日志目录：" + logs.getAbsolutePath());
            return;
        }
        target = new File(logs, FILE_NAME);
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target), "UTF-8"));
            // BOM so Excel on Chinese Windows reads the localized names correctly.
            writer.write('\uFEFF');
            writer.write("id,occupied,meta_name,local_name,class_name,note");
            writer.newLine();
        } catch (final IOException e) {
            closeQuietly();
            send(sender, EnumChatFormatting.RED + "[Elinar] 无法写入 CSV：" + e.getMessage());
            return;
        }
        running = true;
        nextId = 1;
        requester = sender;
    }

    /** Called once per server tick (Phase.END) by {@link GtidExportHandler}. */
    public static void tick() {
        if (!running) return;
        try {
            for (int i = 0; i < IDS_PER_TICK && nextId <= MAX_ID; i++) {
                writeRow(nextId);
                nextId++;
            }
            if (nextId > MAX_ID) finish();
        } catch (final Throwable t) {
            fail(t);
        }
    }

    /** Abort an in-flight scan (server stopping); keeps a partial file. */
    public static void abort() {
        if (!running) return;
        closeQuietly();
        running = false;
        FMLLog.info("[ElinarCurios] GT id export aborted, partial file: %s", target);
    }

    private static void writeRow(final int id) throws IOException {
        final IMetaTileEntity mte = id >= 0 && id < GregTechAPI.METATILEENTITIES.length
            ? GregTechAPI.METATILEENTITIES[id]
            : null;
        final boolean occupied = mte != null;
        String metaName = "";
        String localName = "";
        String className = "";
        String note = "";

        if (id >= GregTechAPI.METATILEENTITIES.length) {
            note = "out_of_gt_range(max=" + (GregTechAPI.METATILEENTITIES.length - 1) + ")";
        } else if (mte != null) {
            try {
                metaName = mte.getMetaName();
            } catch (final Throwable t) {
                note = appendNote(note, "meta_error");
            }
            try {
                localName = mte.getLocalName();
            } catch (final Throwable t) {
                note = appendNote(note, "local_error");
            }
            className = mte.getClass()
                .getName();
        }

        writer.write(
            id + ","
                + (occupied ? "1" : "0")
                + ","
                + quote(metaName)
                + ","
                + quote(localName)
                + ","
                + quote(className)
                + ","
                + quote(note));
        writer.newLine();
    }

    private static void finish() {
        try {
            writer.flush();
        } catch (final IOException ignored) {
            // The rows are already written; the close below is the important part.
        }
        closeQuietly();
        running = false;
        send(requester, EnumChatFormatting.GREEN + "[Elinar] GT 机器 ID 表已导出：" + target.getAbsolutePath());
        requester = null;
    }

    private static void fail(final Throwable t) {
        closeQuietly();
        running = false;
        send(requester, EnumChatFormatting.RED + "[Elinar] GT 机器 ID 表导出失败：" + t);
        requester = null;
    }

    private static void closeQuietly() {
        if (writer != null) {
            try {
                writer.close();
            } catch (final IOException ignored) {}
            writer = null;
        }
    }

    private static String appendNote(final String existing, final String extra) {
        return existing.isEmpty() ? extra : existing + ";" + extra;
    }

    private static String quote(final String value) {
        final String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static void send(final ICommandSender sender, final String text) {
        if (sender != null) sender.addChatMessage(new ChatComponentText(text));
    }
}
