package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.util.BagUtils;
import io.netty.buffer.ByteBuf;

/** Client -> server: request to open the backpack GUI (held or worn). */
public class OpenBagPacket implements IMessageHandler<OpenBagPacket.Message, IMessage> {

    public static final byte FROM_HOLDING = 0;
    public static final byte FROM_WEARING = 1;

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            final World world = player.worldObj;
            final int x = (int) player.posX;
            final int y = (int) player.posY;
            final int z = (int) player.posZ;

            if (message.from == FROM_HOLDING) {
                final ItemStack held = player.getHeldItem();
                if (ItemInfiniteBackpack.isInfiniteBackpack(held)) FMLNetworkHandler
                    .openGui(player, ElinarCurios.instance, ElinarCurios.GUI_ID_BAG_HOLDING, world, x, y, z);
            } else if (message.from == FROM_WEARING) {
                if (BagUtils.isWearingInfiniteBackpack(player)) FMLNetworkHandler
                    .openGui(player, ElinarCurios.instance, ElinarCurios.GUI_ID_BAG_WEARING, world, x, y, z);
            }
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
