package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerMultiPage;
import io.netty.buffer.ByteBuf;

/**
 * Per-slot sync carrying int-sized stack counts.
 * Derived from Avaritiaddons' InfinityChestSlotSync (MPL-2.0, WanionCane).
 */
public final class InfiniteBagSlotSync implements IMessage {

    private ItemStack itemStack;
    private int slot;
    private int stackSize;

    public InfiniteBagSlotSync() {}

    public InfiniteBagSlotSync(final ItemStack itemStack, final int slot) {
        if (itemStack != null) this.stackSize = (this.itemStack = itemStack).stackSize;
        else this.itemStack = null;
        this.slot = slot;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        itemStack = ByteBufUtils.readItemStack(buf);
        slot = ByteBufUtils.readVarShort(buf);
        stackSize = ByteBufUtils.readVarInt(buf, 5);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        ByteBufUtils.writeItemStack(buf, itemStack);
        ByteBufUtils.writeVarShort(buf, slot);
        ByteBufUtils.writeVarInt(buf, stackSize, 5);
    }

    public static class Handler implements IMessageHandler<InfiniteBagSlotSync, IMessage> {

        @Override
        public IMessage onMessage(final InfiniteBagSlotSync message, final MessageContext ctx) {
            final EntityPlayer entityPlayer = ElinarCurios.proxy.getEntityPlayerFromContext(ctx);
            if (entityPlayer.openContainer instanceof ContainerMultiPage)
                ((ContainerMultiPage) entityPlayer.openContainer)
                    .syncData(message.itemStack, message.slot, message.stackSize);
            return null;
        }
    }
}
