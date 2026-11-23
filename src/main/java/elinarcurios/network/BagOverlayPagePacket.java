package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client -> server: switch the page shown in the floating backpack overlay. */
public class BagOverlayPagePacket implements IMessageHandler<BagOverlayPagePacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            BagOverlayServer.switchPage(player, message.from, message.page);
        }
        return null;
    }

    public static class Message implements IMessage {

        private int from;
        private int page;

        public Message() {}

        public Message(final int from, final int page) {
            this.from = from;
            this.page = page;
        }

        @Override
        public void fromBytes(final ByteBuf buf) {
            from = ByteBufUtils.readVarInt(buf, 1);
            page = buf.readByte();
        }

        @Override
        public void toBytes(final ByteBuf buf) {
            ByteBufUtils.writeVarInt(buf, from, 1);
            buf.writeByte(page);
        }
    }
}
