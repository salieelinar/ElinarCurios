package elinarcurios.client.overlay;

import java.util.Arrays;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerInfiniteBackpack;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.network.BagOverlayBuffPacket;
import elinarcurios.network.BagOverlayClickPacket;
import elinarcurios.network.BagOverlayPagePacket;
import elinarcurios.network.BagOverlayRequestPacket;
import elinarcurios.network.BagOverlaySyncPacket;

/**
 * Client-side state of the floating Infinite Backpack overlay.
 *
 * <p>
 * The overlay is NOT a container window: it renders a local snapshot of one backpack page and
 * sends every interaction to the server as a request. There is a throw-away client container used
 * only to predict the click result locally (same {@link ContainerInfiniteBackpack} code path as
 * the real GUI), while the server stays authoritative and corrects the snapshot on every reply.
 */
@SideOnly(Side.CLIENT)
public final class BagOverlayState {

    public static final int WIDTH = 500;
    public static final int HEIGHT = 186;
    public static final int SLOTS = ItemInfiniteBackpack.STORAGE_SLOTS;
    /** How many pixels of the panel must stay on screen so it can always be grabbed back. */
    private static final int MIN_VISIBLE = 32;

    private static boolean open;
    private static int from = -1;
    private static int page;
    private static final ItemStack[] stacks = new ItemStack[SLOTS];
    private static boolean[] buffs = new boolean[ItemInfiniteBackpack.BUFF_KEYS.length];

    private static int x = Integer.MIN_VALUE;
    private static int y = Integer.MIN_VALUE;
    private static boolean dragging;
    private static int dragOffsetX;
    private static int dragOffsetY;

    private static ContainerInfiniteBackpack predictor;
    private static InventoryPlayer predictorInventory;
    private static int nextTxId = 1;

    private BagOverlayState() {}

    // ------------------------------------------------------------------ lifecycle

    /** Opens (or refreshes) the overlay for the given source. */
    public static void requestOpen(final int source) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        open = true;
        from = source;
        Arrays.fill(stacks, null);
        if (x == Integer.MIN_VALUE) {
            final GuiScreen screen = mc.currentScreen;
            if (screen != null) {
                x = Math.max(0, (screen.width - WIDTH) / 2);
                y = Math.max(0, (screen.height - HEIGHT) / 2 - 12);
            }
        }
        ElinarCurios.networkWrapper.sendToServer(new BagOverlayRequestPacket.Message((byte) source));
    }

    public static void close() {
        open = false;
        dragging = false;
    }

    public static boolean isOpen() {
        return open;
    }

    public static int getFrom() {
        return from;
    }

    public static int getPage() {
        return page;
    }

    public static boolean isBuffEnabled(final int index) {
        return index >= 0 && index < buffs.length && buffs[index];
    }

    public static ItemStack getStack(final int slot) {
        return slot >= 0 && slot < SLOTS ? stacks[slot] : null;
    }

    // ------------------------------------------------------------------ position

    public static int getX() {
        return x;
    }

    public static int getY() {
        return y;
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static int getDragOffsetX() {
        return dragOffsetX;
    }

    public static int getDragOffsetY() {
        return dragOffsetY;
    }

    public static void beginDrag(final int offsetX, final int offsetY) {
        dragging = true;
        dragOffsetX = offsetX;
        dragOffsetY = offsetY;
    }

    public static void endDrag() {
        dragging = false;
    }

    public static void setPos(final int newX, final int newY) {
        final GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen == null) {
            x = newX;
            y = newY;
            return;
        }
        x = loose(newX, WIDTH, screen.width);
        y = loose(newY, HEIGHT, screen.height);
    }

    /** Keeps at least a grabable strip of the panel visible after GUI scale changes. */
    public static void ensurePosition(final int screenWidth, final int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return;
        if (x == Integer.MIN_VALUE) {
            x = Math.max(0, (screenWidth - WIDTH) / 2);
            y = Math.max(0, (screenHeight - HEIGHT) / 2 - 12);
        }
        x = loose(x, WIDTH, screenWidth);
        y = loose(y, HEIGHT, screenHeight);
    }

    /**
     * Clamps the panel position so any part of it may leave the screen while a
     * {@link #MIN_VISIBLE}px strip stays reachable — the user can drag the panel mostly
     * off-screen to keep the machine GUI in the middle free.
     */
    private static int loose(final int value, final int size, final int screenSize) {
        final int min = MIN_VISIBLE - size;
        final int max = screenSize - MIN_VISIBLE;
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------ sync

    /** Applies the authoritative snapshot pushed by the server. */
    public static void onSync(@Nonnull final BagOverlaySyncPacket message) {
        if (!message.isValid()) {
            if (open) {
                open = false;
                dragging = false;
                final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
                if (player != null) {
                    final IChatComponent text = new ChatComponentTranslation("elinarcurios:messages.bag_missing");
                    player.addChatMessage(text);
                }
            }
            return;
        }
        if (!open) return;
        if (message.isFull()) Arrays.fill(stacks, null);
        final int count = message.getCount();
        final int[] slots = message.getSlots();
        final ItemStack[] synced = message.getStacks();
        for (int i = 0; i < count && i < slots.length && i < synced.length; i++) {
            final int slot = slots[i];
            if (slot < 0 || slot >= SLOTS) continue;
            stacks[slot] = synced[i];
        }
        final int newPage = message.getPage();
        if (newPage >= 0 && newPage < ItemInfiniteBackpack.PAGE_COUNT) page = newPage;
        final boolean[] flags = message.getBuffs();
        if (flags != null && flags.length == buffs.length) buffs = flags.clone();
    }

    // ------------------------------------------------------------------ actions

    /** Sends a slot click after applying the same click locally for prediction. */
    public static void clickSlot(final int slot, final int button, final boolean shift) {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityPlayer player = mc.thePlayer;
        if (player == null || slot < 0 || slot >= SLOTS) return;
        final ItemStack carried = player.inventory.getItemStack();
        final ItemStack before = carried == null ? null : carried.copy();
        final int beforeSize = carried == null ? 0 : carried.stackSize;

        final ContainerInfiniteBackpack container = predictContainer(player.inventory);
        if (container != null) {
            for (int i = 0; i < SLOTS; i++) container.inventory.contents[i] = stacks[i];
            container.slotClick(slot, button, shift ? 1 : 0, player);
            for (int i = 0; i < SLOTS; i++) stacks[i] = container.inventory.contents[i];
        }

        final int tx = nextTxId++;
        ElinarCurios.networkWrapper
            .sendToServer(new BagOverlayClickPacket.Message(from, slot, button, shift ? 1 : 0, before, beforeSize, tx));
    }

    /** Requests a page switch; the server confirms with a full snapshot. */
    public static void switchPage(final int newPage) {
        if (from < 0 || newPage < 0 || newPage >= ItemInfiniteBackpack.PAGE_COUNT) return;
        page = newPage;
        Arrays.fill(stacks, null);
        ElinarCurios.networkWrapper.sendToServer(new BagOverlayPagePacket.Message(from, newPage));
    }

    public static void toggleBuff(final int buffId) {
        if (from < 0 || buffId < 0 || buffId >= ItemInfiniteBackpack.BUFF_KEYS.length) return;
        buffs[buffId] = !buffs[buffId];
        ElinarCurios.networkWrapper.sendToServer(new BagOverlayBuffPacket.Message(from, buffId));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Throw-away prediction container. The real bag is never touched: the dummy stack absorbs the
     * NBT writes that {@code markDirty()} performs, and the synced snapshot is copied in before
     * each click.
     */
    private static ContainerInfiniteBackpack predictContainer(final InventoryPlayer inventory) {
        if (ItemInfiniteBackpack.instance == null) return null;
        if (predictor == null || predictorInventory != inventory) {
            predictorInventory = inventory;
            predictor = new ContainerInfiniteBackpack(new ItemStack(ItemInfiniteBackpack.instance), inventory);
        }
        return predictor;
    }
}
