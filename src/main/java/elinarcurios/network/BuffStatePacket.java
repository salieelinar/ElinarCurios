package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerInfiniteBackpack;
import elinarcurios.item.ItemInfiniteBackpack;
import io.netty.buffer.ByteBuf;

/** Server -> client: current buff flags of the open backpack. */
public class BuffStatePacket implements IMessage {

    private boolean[] flags;

    public BuffStatePacket() {}

    public BuffStatePacket(final boolean[] flags) {
        this.flags = flags;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        final int count = Math.min(ItemInfiniteBackpack.BUFF_KEYS.length, buf.readByte());
        flags = new boolean[ItemInfiniteBackpack.BUFF_KEYS.length];
        for (int i = 0; i < count; i++) flags[i] = buf.readBoolean();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeByte(ItemInfiniteBackpack.BUFF_KEYS.length);
        for (int i = 0; i < ItemInfiniteBackpack.BUFF_KEYS.length; i++) buf.writeBoolean(flags[i]);
    }

    public static class Handler implements IMessageHandler<BuffStatePacket, IMessage> {

        @Override
        public IMessage onMessage(final BuffStatePacket message, final MessageContext ctx) {
            if (ctx.side.isClient()) {
                final EntityPlayer player = ElinarCurios.proxy.getEntityPlayerFromContext(ctx);
                if (player != null && player.openContainer instanceof ContainerInfiniteBackpack) {
                    final ContainerInfiniteBackpack container = (ContainerInfiniteBackpack) player.openContainer;
                    final ItemStack bag = container.getBagStack();
                    ItemInfiniteBackpack.setBuffFlags(bag, message.flags);
                    // GUI refresh is client-only; delegate to the sided proxy so this
                    // handler stays instantiable on a dedicated server (FML preInit).
                    ElinarCurios.proxy.refreshBuffButtonsIfOpen();
                }
            }
            return null;
        }
    }
}
