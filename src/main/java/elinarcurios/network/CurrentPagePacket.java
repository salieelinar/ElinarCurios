package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerMultiPage;
import io.netty.buffer.ByteBuf;

/** Server -> client: the currently active page of the open backpack. */
public class CurrentPagePacket implements IMessage {

    private byte page;

    public CurrentPagePacket() {}

    public CurrentPagePacket(final int page) {
        this.page = (byte) page;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        page = buf.readByte();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeByte(page);
    }

    public static class Handler implements IMessageHandler<CurrentPagePacket, IMessage> {

        @Override
        public IMessage onMessage(final CurrentPagePacket message, final MessageContext ctx) {
            if (ctx.side.isClient()) {
                final EntityPlayer player = ElinarCurios.proxy.getEntityPlayerFromContext(ctx);
                if (player != null && player.openContainer instanceof ContainerMultiPage) {
                    final ContainerMultiPage container = (ContainerMultiPage) player.openContainer;
                    if (message.page >= 0 && message.page < container.getPageCount()) {
                        container.syncPage(message.page);
                        // GUI refresh is client-only; delegate to the sided proxy so this
                        // handler stays instantiable on a dedicated server (FML preInit).
                        ElinarCurios.proxy.refreshPageButtonsIfOpen(message.page);
                    }
                }
            }
            return null;
        }
    }
}
