package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerMultiPage;
import io.netty.buffer.ByteBuf;

/** Client -> server: switch the visible page of the open backpack. */
public class SwitchPagePacket implements IMessageHandler<SwitchPagePacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            if (!(player.openContainer instanceof ContainerMultiPage)) return null;
            final ContainerMultiPage container = (ContainerMultiPage) player.openContainer;
            if (message.page < 0 || message.page >= container.getPageCount()) return null;
            container.switchPage(message.page);
            ElinarCurios.networkWrapper.sendTo(new CurrentPagePacket(container.getCurrentPage()), player);
        }
        return null;
    }

    public static class Message implements IMessage {

        private byte page;

        public Message() {}

        public Message(final byte page) {
            this.page = page;
        }

        @Override
        public void fromBytes(final ByteBuf buf) {
            page = buf.readByte();
        }

        @Override
        public void toBytes(final ByteBuf buf) {
            buf.writeByte(page);
        }
    }
}
