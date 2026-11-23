package elinarcurios.network;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerInfiniteBackpack;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.util.BagUtils;

/**
 * Server-side handling of the floating backpack overlay.
 *
 * <p>
 * Stateless by design: the overlay never holds an open container window, so every request builds a
 * throw-away {@link ContainerInfiniteBackpack} around the authoritative bag stack (worn property or
 * held item), replays the requested operation on it, and answers with the changed slots. The
 * temporary container is dropped immediately, so there is nothing to leak when the client closes
 * the overlay, dies or changes dimension.
 *
 * <p>
 * Trust boundary: the client only names a source (worn/held), a slot index and the carried item it
 * believes it has. The server re-locates the bag, re-checks the carried item, and ignores slots
 * outside the 243 storage slots, so a modified client cannot reach the player inventory or another
 * container through this channel.
 */
public final class BagOverlayServer {

    private BagOverlayServer() {}

    /** Answers a request (or a failed click) with the full current page. */
    public static void sendFull(final EntityPlayerMP player, final int from, final short txId) {
        final ItemStack bag = locate(player, from);
        if (bag == null) {
            sendInvalid(player, txId);
            return;
        }
        final ContainerInfiniteBackpack container = new ContainerInfiniteBackpack(bag, player.inventory);
        final ItemStack[] contents = container.inventory.contents;
        final ArrayList<Integer> slotList = new ArrayList<>();
        final ArrayList<ItemStack> stackList = new ArrayList<>();
        final ArrayList<Integer> sizeList = new ArrayList<>();
        for (int i = 0; i < ItemInfiniteBackpack.STORAGE_SLOTS; i++) {
            final ItemStack stack = contents[i];
            if (stack == null) continue;
            slotList.add(i);
            stackList.add(stack.copy());
            sizeList.add(stack.stackSize);
        }
        send(player, true, true, container.getCurrentPage(), bag, slotList, stackList, sizeList, txId);
    }

    /** Applies one overlay click (0..242 only) on a temporary container. */
    public static void click(final EntityPlayerMP player, final int from, final int slot, final int button,
        final int modifier, final ItemStack cursorBefore, final int cursorSize, final int txId) {
        final ItemStack bag = locate(player, from);
        if (bag == null) {
            sendInvalid(player, (short) txId);
            return;
        }
        if (slot < 0 || slot >= ItemInfiniteBackpack.STORAGE_SLOTS) return;

        final ItemStack serverCursor = player.inventory.getItemStack();
        if (!sameStack(cursorBefore, cursorSize, serverCursor)) {
            // Client and server disagree about the carried item: refuse and let the client
            // roll back to the authoritative snapshot (never apply a half-valid click).
            sendFull(player, from, (short) txId);
            player.updateHeldItem();
            return;
        }

        final ContainerInfiniteBackpack container = new ContainerInfiniteBackpack(bag, player.inventory);
        final ItemStack[] before = snapshot(container.inventory.contents);
        container.slotClick(slot, button, modifier, player);
        final ItemStack[] after = container.inventory.contents;

        final ArrayList<Integer> slotList = new ArrayList<>();
        final ArrayList<ItemStack> stackList = new ArrayList<>();
        final ArrayList<Integer> sizeList = new ArrayList<>();
        for (int i = 0; i < ItemInfiniteBackpack.STORAGE_SLOTS; i++) {
            if (!ItemStack.areItemStacksEqual(before[i], after[i])) {
                slotList.add(i);
                stackList.add(after[i] == null ? null : after[i].copy());
                sizeList.add(after[i] == null ? 0 : after[i].stackSize);
            }
        }
        send(player, true, false, container.getCurrentPage(), bag, slotList, stackList, sizeList, (short) txId);

        // Carried item and the vanilla container (player inventory slots) are shared state with
        // whatever GUI is open below the overlay; push both back through the vanilla channels.
        player.updateHeldItem();
        if (player.openContainer != null) player.openContainer.detectAndSendChanges();
    }

    /** Switches the page of the overlay and answers with the full new page. */
    public static void switchPage(final EntityPlayerMP player, final int from, final int page) {
        final ItemStack bag = locate(player, from);
        if (bag == null) {
            sendInvalid(player, (short) -1);
            return;
        }
        if (page < 0 || page >= ItemInfiniteBackpack.PAGE_COUNT) return;
        final ContainerInfiniteBackpack container = new ContainerInfiniteBackpack(bag, player.inventory);
        container.switchPage(page);
        sendFull(player, from, (short) -1);
    }

    /** Toggles one passive buff and answers with the new flags. */
    public static void toggleBuff(final EntityPlayerMP player, final int from, final int buffId) {
        final ItemStack bag = locate(player, from);
        if (bag == null) {
            sendInvalid(player, (short) -1);
            return;
        }
        if (buffId < 0 || buffId >= ItemInfiniteBackpack.BUFF_KEYS.length) return;
        ItemInfiniteBackpack.toggleBuff(bag, ItemInfiniteBackpack.BUFF_KEYS[buffId]);
        final ArrayList<Integer> emptySlots = new ArrayList<>();
        final ArrayList<ItemStack> emptyStacks = new ArrayList<>();
        final ArrayList<Integer> emptySizes = new ArrayList<>();
        send(
            player,
            true,
            false,
            ItemInfiniteBackpack.getCurrentPage(bag),
            bag,
            emptySlots,
            emptyStacks,
            emptySizes,
            (short) -1);
    }

    private static void sendInvalid(final EntityPlayerMP player, final short txId) {
        ElinarCurios.networkWrapper
            .sendTo(new BagOverlaySyncPacket(false, false, 0, null, null, null, null, txId), player);
    }

    private static void send(final EntityPlayerMP player, final boolean valid, final boolean full, final int page,
        final ItemStack bag, final ArrayList<Integer> slotList, final ArrayList<ItemStack> stackList,
        final ArrayList<Integer> sizeList, final short txId) {
        final int[] slots = new int[slotList.size()];
        final ItemStack[] stacks = new ItemStack[slotList.size()];
        final int[] sizes = new int[slotList.size()];
        for (int i = 0; i < slotList.size(); i++) {
            slots[i] = slotList.get(i);
            stacks[i] = stackList.get(i);
            sizes[i] = sizeList.get(i);
        }
        ElinarCurios.networkWrapper.sendTo(
            new BagOverlaySyncPacket(
                valid,
                full,
                page,
                ItemInfiniteBackpack.getBuffFlags(bag),
                slots,
                stacks,
                sizes,
                txId),
            player);
    }

    /** Resolves the authoritative bag stack for the requested source. */
    private static ItemStack locate(final EntityPlayerMP player, final int from) {
        if (from == BagOverlayRequestPacket.FROM_WEARING) {
            return BagUtils.getWornInfiniteBackpack(player);
        }
        if (from == BagOverlayRequestPacket.FROM_HOLDING) {
            final ItemStack held = player.getHeldItem();
            return ItemInfiniteBackpack.isInfiniteBackpack(held) ? held : null;
        }
        return null;
    }

    /** Carried-item comparison that keeps int-sized stack counts (ItemStack.writeToNBT stores a byte). */
    private static boolean sameStack(final ItemStack clientStack, final int clientSize, final ItemStack serverStack) {
        if (clientStack == null || serverStack == null) return clientStack == null && serverStack == null;
        if (clientStack.getItem() != serverStack.getItem()) return false;
        if (clientStack.getItemDamage() != serverStack.getItemDamage()) return false;
        if (clientSize != serverStack.stackSize) return false;
        return ItemStack.areItemStackTagsEqual(clientStack, serverStack);
    }

    private static ItemStack[] snapshot(final ItemStack[] contents) {
        final ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].copy();
        }
        return copy;
    }
}
