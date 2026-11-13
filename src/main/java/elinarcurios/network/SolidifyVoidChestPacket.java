package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.inventory.ContainerEternalChest;
import elinarcurios.tile.TileEntityVoidChest;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: solidify the open Void Chest — every non-empty slot's count
 * is refreshed to 1,000,000,000. The server is authoritative; it mutates the
 * TileEntity storage and pushes a full slot snapshot to every watching player.
 */
public class SolidifyVoidChestPacket implements IMessageHandler<SolidifyVoidChestPacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            if (!(player.openContainer instanceof ContainerEternalChest)) return null;
            final ContainerEternalChest container = (ContainerEternalChest) player.openContainer;
            if (!(container.chest instanceof TileEntityVoidChest)) return null;
            ((TileEntityVoidChest) container.chest).solidify();
            container.refreshAndResyncAll();
        }
        return null;
    }

    /** Empty payload: the target chest is taken from the player's open container. */
    public static class Message implements IMessage {

        public Message() {}

        @Override
        public void fromBytes(final ByteBuf buf) {}

        @Override
        public void toBytes(final ByteBuf buf) {}
    }
}
