package elinarcurios.network;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerMultiPage;
import io.netty.buffer.ByteBuf;

/**
 * Full container resync. Derived from Avaritiaddons' InfinityChestSyncAllSlots (MPL-2.0).
 */
public class InfiniteBagSyncAllSlots implements IMessage {

    private short slotCount;
    private ItemStack[] itemStacks;
    private int[] stackSizes;

    public InfiniteBagSyncAllSlots() {}

    public InfiniteBagSyncAllSlots(@Nonnull final ItemStack[] itemStacks) {
        slotCount = (short) (stackSizes = new int[(this.itemStacks = itemStacks).length]).length;
        for (int i = 0; i < slotCount; i++) stackSizes[i] = itemStacks[i] != null ? itemStacks[i].stackSize : 0;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        slotCount = (short) ByteBufUtils.readVarShort(buf);
        itemStacks = new ItemStack[slotCount];
        stackSizes = new int[slotCount];
        for (int i = 0; i < slotCount; i++) {
            itemStacks[i] = ByteBufUtils.readItemStack(buf);
            stackSizes[i] = ByteBufUtils.readVarInt(buf, 5);
            if (itemStacks[i] != null) itemStacks[i].stackSize = stackSizes[i];
        }
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        ByteBufUtils.writeVarShort(buf, slotCount);
        for (int i = 0; i < slotCount; i++) {
            ByteBufUtils.writeItemStack(buf, itemStacks[i]);
            ByteBufUtils.writeVarInt(buf, stackSizes[i], 5);
        }
    }

    public static class Handler implements IMessageHandler<InfiniteBagSyncAllSlots, IMessage> {

        @Override
        public IMessage onMessage(final InfiniteBagSyncAllSlots message, final MessageContext ctx) {
            final EntityPlayer entityPlayer = ElinarCurios.proxy.getEntityPlayerFromContext(ctx);
            if (entityPlayer.openContainer instanceof ContainerMultiPage) for (int i = 0; i < message.slotCount; i++)
                ((Slot) (entityPlayer.openContainer.inventorySlots.get(i))).putStack(message.itemStacks[i]);
            return null;
        }
    }
}
