package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: open (or refresh) the floating backpack overlay.
 *
 * <p>
 * The overlay never occupies {@code openContainer}; the server answers with a full
 * {@link BagOverlaySyncPacket} built from a throw-away container instance.
 */
public class BagOverlayRequestPacket implements IMessageHandler<BagOverlayRequestPacket.Message, IMessage> {

    /** Worn backpack (BagProperty.wearable). */
    public static final byte FROM_WEARING = 0;
    /** Backpack currently held in the main hand. */
    public static final byte FROM_HOLDING = 1;

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            BagOverlayServer.sendFull(player, message.from, (short) -1);
        }
        return null;
    }

    public static class Message implements IMessage {

        private byte from;

        public Message() {}

        public Message(final byte from) {
            this.from = from;
        }

        @Override
        public void fromBytes(final ByteBuf buf) {
            from = buf.readByte();
        }

        @Override
        public void toBytes(final ByteBuf buf) {
            buf.writeByte(from);
        }
    }
}
