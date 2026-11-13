package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerInfiniteBackpack;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.util.DragonBuffs;
import io.netty.buffer.ByteBuf;

/** Client -> server: toggle one passive buff of the backpack whose GUI is open. */
public class ToggleBuffPacket implements IMessageHandler<ToggleBuffPacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            if (!(player.openContainer instanceof ContainerInfiniteBackpack)) return null;
            final ContainerInfiniteBackpack container = (ContainerInfiniteBackpack) player.openContainer;
            if (message.buffId < 0 || message.buffId >= ItemInfiniteBackpack.BUFF_KEYS.length) return null;

            final ItemStack bag = container.getBagStack();
            final String key = ItemInfiniteBackpack.BUFF_KEYS[message.buffId];
            final boolean nowEnabled = ItemInfiniteBackpack.toggleBuff(bag, key);
            if (!nowEnabled) {
                DragonBuffs.removeBuff(player, key);
            }
            ElinarCurios.networkWrapper.sendTo(new BuffStatePacket(ItemInfiniteBackpack.getBuffFlags(bag)), player);
        }
        return null;
    }

    public static class Message implements IMessage {

        private byte buffId;

        public Message() {}

        public Message(final byte buffId) {
            this.buffId = buffId;
        }

        @Override
        public void fromBytes(final ByteBuf buf) {
            buffId = buf.readByte();
        }

        @Override
        public void toBytes(final ByteBuf buf) {
            buf.writeByte(buffId);
        }
    }
}
