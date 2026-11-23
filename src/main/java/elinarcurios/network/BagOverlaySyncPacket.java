package elinarcurios.network;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import io.netty.buffer.ByteBuf;

/**
 * Server -> client: authoritative state of the floating backpack overlay.
 *
 * <p>
 * {@code valid == false} means the server could not find the backpack (dropped, unequipped,
 * moved out of the hand); the client closes the overlay. A {@code full} packet replaces the whole
 * page (and is also the rollback path when a click fails validation); otherwise only the listed
 * slots changed. Stack sizes travel as ints for the 243-slot int-sized storage.
 */
public class BagOverlaySyncPacket implements IMessage {

    private boolean valid;
    private boolean full;
    private int page;
    private boolean[] buffs = new boolean[0];
    private int count;
    private int[] slots = new int[0];
    private ItemStack[] stacks = new ItemStack[0];
    private int[] sizes = new int[0];
    private short txId;

    public BagOverlaySyncPacket() {}

    public BagOverlaySyncPacket(final boolean valid, final boolean full, final int page, final boolean[] buffs,
        final int[] slots, final ItemStack[] stacks, final int[] sizes, final short txId) {
        this.valid = valid;
        this.full = full;
        this.page = page;
        this.buffs = buffs == null ? new boolean[0] : buffs;
        this.slots = slots == null ? new int[0] : slots;
        this.stacks = stacks == null ? new ItemStack[0] : stacks;
        this.sizes = sizes == null ? new int[0] : sizes;
        this.count = this.slots.length;
        this.txId = txId;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        valid = buf.readBoolean();
        full = buf.readBoolean();
        page = buf.readByte();
        final int buffCount = buf.readByte() & 255;
        buffs = new boolean[buffCount];
        for (int i = 0; i < buffCount; i++) buffs[i] = buf.readBoolean();
        count = ByteBufUtils.readVarShort(buf);
        slots = new int[count];
        stacks = new ItemStack[count];
        sizes = new int[count];
        for (int i = 0; i < count; i++) {
            slots[i] = ByteBufUtils.readVarShort(buf);
            stacks[i] = ByteBufUtils.readItemStack(buf);
            sizes[i] = ByteBufUtils.readVarInt(buf, 5);
            if (stacks[i] != null) stacks[i].stackSize = sizes[i];
        }
        txId = (short) ByteBufUtils.readVarShort(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeBoolean(valid);
        buf.writeBoolean(full);
        buf.writeByte(page);
        buf.writeByte(buffs.length);
        for (boolean buff : buffs) buf.writeBoolean(buff);
        ByteBufUtils.writeVarShort(buf, count);
        for (int i = 0; i < count; i++) {
            ByteBufUtils.writeVarShort(buf, slots[i]);
            ByteBufUtils.writeItemStack(buf, stacks[i]);
            ByteBufUtils.writeVarInt(buf, sizes[i], 5);
        }
        ByteBufUtils.writeVarShort(buf, txId);
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isFull() {
        return full;
    }

    public int getPage() {
        return page;
    }

    public boolean[] getBuffs() {
        return buffs;
    }

    public int getCount() {
        return count;
    }

    public int[] getSlots() {
        return slots;
    }

    public ItemStack[] getStacks() {
        return stacks;
    }

    public short getTxId() {
        return txId;
    }

    public static class Handler implements IMessageHandler<BagOverlaySyncPacket, IMessage> {

        @Override
        public IMessage onMessage(final BagOverlaySyncPacket message, final MessageContext ctx) {
            if (ctx.side.isClient()) ElinarCurios.proxy.handleBagOverlaySync(message);
            return null;
        }
    }
}
