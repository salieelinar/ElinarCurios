package elinarcurios.network;

import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import io.netty.buffer.ByteBuf;

/**
 * Server -> client: synchronize the worn backpack (needed for back rendering and the
 * client-side wearing check).
 *
 * <p>
 * The actual client-side logic lives in {@link elinarcurios.client.ClientProxy}
 * ({@code handleSyncBagMessage}): this handler must stay free of client-only class
 * references so that FML can instantiate it on a dedicated server during preInit.
 */
public class SyncBagPacket implements IMessageHandler<SyncBagPacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isClient()) {
            ElinarCurios.proxy.handleSyncBagMessage(message.entityId, message.data);
        }
        return null;
    }

    public static class Message implements IMessage {

        private int entityId;
        private NBTTagCompound data;

        public Message() {}

        public Message(final int entityId, final NBTTagCompound data) {
            this.entityId = entityId;
            this.data = data;
        }

        @Override
        public void fromBytes(final ByteBuf buf) {
            entityId = buf.readInt();
            data = ByteBufUtils.readTag(buf);
        }

        @Override
        public void toBytes(final ByteBuf buf) {
            buf.writeInt(entityId);
            ByteBufUtils.writeTag(buf, data);
        }
    }
}
