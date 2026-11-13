package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.util.BagUtils;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: resend the worn backpack. Sent when the client player entity is
 * (re)created, e.g. on dimension change: 1.7.10's
 * {@code Minecraft.setDimensionAndSpawnPlayer} builds a brand-new
 * {@code EntityClientPlayerMP}, which discards the old entity's extended properties
 * (including our {@code BagProperty}) while the server keeps its copy. The server
 * replies with a {@link SyncBagPacket} containing the authoritative worn bag.
 */
public class RequestWornBagPacket implements IMessageHandler<RequestWornBagPacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            BagUtils.syncWornBagToClient(player);
        }
        return null;
    }

    public static class Message implements IMessage {

        public Message() {}

        @Override
        public void fromBytes(final ByteBuf buf) {}

        @Override
        public void toBytes(final ByteBuf buf) {}
    }
}
