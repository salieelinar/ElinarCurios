package elinarcurios.network;

import java.util.HashMap;
import java.util.Map;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import io.netty.buffer.ByteBuf;

/**
 * Server -> owner client: full snapshot of the player's shared wand pool.
 *
 * <p>
 * Sent when the pool changes (batched to one packet per tick) and once on login. The client stores it
 * in {@link elinarcurios.client.WandPoolClient}; every Celestial Wand owned by that player reads this
 * cache for display, which keeps a wand in the arcane workbench in sync with the one in hand even
 * though tile-inventory item NBT is not refreshed every tick.
 *
 * <p>
 * The pool is a {@code aspect tag -> long} map (all aspects, non-zero entries only), so later
 * mechanics can sync compound/custom aspects through the same packet.
 */
public class WandPoolSyncPacket implements IMessageHandler<WandPoolSyncPacket.Message, IMessage> {

    /** Hard cap against a malformed packet; the pool never has anywhere near this many aspects. */
    private static final int MAX_ASPECTS = 1024;

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isClient()) {
            ElinarCurios.proxy.handleWandPoolSync(message.values, message.ownerName);
        }
        return null;
    }

    public static class Message implements IMessage {

        private Map<String, Long> values = new HashMap<String, Long>();
        private String ownerName = "";

        public Message() {}

        public Message(final Map<String, Long> values, final String ownerName) {
            if (values != null) this.values.putAll(values);
            this.ownerName = ownerName == null ? "" : ownerName;
        }

        @Override
        public void fromBytes(final ByteBuf buf) {
            final int count = Math.max(0, Math.min(buf.readInt(), MAX_ASPECTS));
            for (int i = 0; i < count; i++) {
                final String key = ByteBufUtils.readUTF8String(buf);
                values.put(key, buf.readLong());
            }
            ownerName = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(final ByteBuf buf) {
            buf.writeInt(values.size());
            for (final Map.Entry<String, Long> entry : values.entrySet()) {
                ByteBufUtils.writeUTF8String(buf, entry.getKey());
                buf.writeLong(entry.getValue() == null ? 0L : entry.getValue());
            }
            ByteBufUtils.writeUTF8String(buf, ownerName);
        }
    }
}
