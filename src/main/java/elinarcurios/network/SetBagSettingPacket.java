package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerInfiniteBackpack;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.util.BagSettings;
import io.netty.buffer.ByteBuf;

/** Client -> server: change one "extra config" setting of the backpack whose GUI is open. */
public class SetBagSettingPacket implements IMessageHandler<SetBagSettingPacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (!ctx.side.isServer()) return null;
        final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        if (player == null || player.isDead) return null;
        if (!(player.openContainer instanceof ContainerInfiniteBackpack)) return null;
        if (message.settingId < 0 || message.settingId >= BagSettings.COUNT) return null;

        final ContainerInfiniteBackpack container = (ContainerInfiniteBackpack) player.openContainer;
        final ItemStack bag = container.getBagStack();
        if (!ItemInfiniteBackpack.isInfiniteBackpack(bag)) return null;

        BagSettings.set(bag, message.settingId, message.value);
        ElinarCurios.networkWrapper.sendTo(new BagSettingsStatePacket(BagSettings.readAll(bag)), player);
        return null;
    }

    public static class Message implements IMessage {

        private byte settingId;
        private float value;

        public Message() {}

        public Message(final int settingId, final float value) {
            this.settingId = (byte) settingId;
            this.value = value;
        }

        @Override
        public void fromBytes(final ByteBuf buf) {
            settingId = buf.readByte();
            value = buf.readFloat();
        }

        @Override
        public void toBytes(final ByteBuf buf) {
            buf.writeByte(settingId);
            buf.writeFloat(value);
        }
    }
}
