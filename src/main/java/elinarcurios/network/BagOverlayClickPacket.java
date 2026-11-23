package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: one click inside the floating backpack overlay.
 *
 * <p>
 * The overlay is not a real container window, so the click cannot travel through the vanilla
 * {@code C0EPacketClickWindow} channel. The server re-checks the carried item against
 * {@link #cursorBefore} and replays the click on a throw-away container instance; a mismatch is
 * answered with a full resync instead of applying the click (no duplication / no item loss).
 *
 * <p>
 * {@code stackSize} travels as an int because {@link ByteBufUtils#writeItemStack} stores it as a
 * byte (the 243-slot storage allows int-sized stacks).
 */
public class BagOverlayClickPacket implements IMessageHandler<BagOverlayClickPacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            BagOverlayServer.click(
                player,
                message.from,
                message.slot,
                message.button,
                message.modifier,
                message.cursorBefore,
                message.cursorSize,
                message.txId);
        }
        return null;
    }

    public static class Message implements IMessage {

        private int from;
        private int slot;
        private int button;
        private int modifier;
        private ItemStack cursorBefore;
        private int cursorSize;
        private int txId;

        public Message() {}

        public Message(final int from, final int slot, final int button, final int modifier,
            final ItemStack cursorBefore, final int cursorSize, final int txId) {
            this.from = from;
            this.slot = slot;
            this.button = button;
            this.modifier = modifier;
            this.cursorBefore = cursorBefore;
            this.cursorSize = cursorBefore == null ? 0 : cursorSize;
            this.txId = txId;
        }

        @Override
        public void fromBytes(final ByteBuf buf) {
            from = ByteBufUtils.readVarInt(buf, 1);
            slot = ByteBufUtils.readVarShort(buf);
            button = buf.readByte();
            modifier = buf.readByte();
            cursorBefore = ByteBufUtils.readItemStack(buf);
            cursorSize = ByteBufUtils.readVarInt(buf, 5);
            if (cursorBefore != null) cursorBefore.stackSize = cursorSize;
            txId = ByteBufUtils.readVarShort(buf);
        }

        @Override
        public void toBytes(final ByteBuf buf) {
            ByteBufUtils.writeVarInt(buf, from, 1);
            ByteBufUtils.writeVarShort(buf, slot);
            buf.writeByte(button);
            buf.writeByte(modifier);
            ByteBufUtils.writeItemStack(buf, cursorBefore);
            ByteBufUtils.writeVarInt(buf, cursorSize, 5);
            ByteBufUtils.writeVarShort(buf, txId);
        }
    }
}
