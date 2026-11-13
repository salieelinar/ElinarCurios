package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.util.BagUtils;
import io.netty.buffer.ByteBuf;

/** Client -> server: equip the held backpack on the back / unequip the worn one. */
public class EquipUnequipBagPacket implements IMessageHandler<EquipUnequipBagPacket.Message, IMessage> {

    public static final byte EQUIP = 0;
    public static final byte UNEQUIP = 1;

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;

            if (message.action == EQUIP) {
                if (!BagUtils.equip(player)) {
                    player.addChatComponentMessage(
                        new ChatComponentTranslation("elinarcurios:messages.already.equipped"));
                }
            } else if (message.action == UNEQUIP) {
                BagUtils.unequip(player);
            }
        }
        return null;
    }

    public static class Message implements IMessage {

        private byte action;

        public Message() {}

        public Message(final byte action) {
            this.action = action;
        }

        @Override
        public void fromBytes(final ByteBuf buf) {
            action = buf.readByte();
        }

        @Override
        public void toBytes(final ByteBuf buf) {
            buf.writeByte(action);
        }
    }
}
