package elinarcurios.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.util.WandPoolConstants;
import io.netty.buffer.ByteBuf;

/**
 * Server -> owner client: full snapshot of the player's shared wand vis pool.
 *
 * <p>
 * Sent when the pool changes (batched to one packet per tick) and once on login. The client stores it
 * in {@link elinarcurios.client.WandPoolClient}; every Celestial Wand owned by that player reads this
 * cache for display, which is what keeps a wand in the arcane workbench in sync with the one in hand
 * even though tile-inventory item NBT is not refreshed every tick.
 */
public class WandPoolSyncPacket implements IMessageHandler<WandPoolSyncPacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isClient()) {
            ElinarCurios.proxy.handleWandPoolSync(message.values, message.ownerName);
        }
        return null;
    }

    public static class Message implements IMessage {

        private long[] values = new long[WandPoolConstants.ASPECT_COUNT];
        private String ownerName = "";

        public Message() {}

        public Message(final long[] values, final String ownerName) {
            if (values != null && values.length == this.values.length) {
                System.arraycopy(values, 0, this.values, 0, this.values.length);
            }
            this.ownerName = ownerName == null ? "" : ownerName;
        }

        @Override
        public void fromBytes(final ByteBuf buf) {
            for (int i = 0; i < values.length; i++) values[i] = buf.readLong();
            ownerName = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(final ByteBuf buf) {
            for (final long value : values) buf.writeLong(value);
            ByteBufUtils.writeUTF8String(buf, ownerName);
        }
    }
}
