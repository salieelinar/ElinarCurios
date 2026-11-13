package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerMultiPage;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: ask which page of the open multi-page container is active. Query only -
 * the server never switches pages in response to this packet.
 *
 * <p>
 * Used when a backpack GUI opens: the client-side copy of a worn bag is only synced on
 * equip/unequip/login, so it can be stale after the remembered page changed. Sending a
 * {@link SwitchPagePacket} with the stale page would make the server switch (and
 * re-remember) the page, losing the player's choice; this packet just asks.
 *
 * <p>
 * 判断走公共父类 {@link ContainerMultiPage}，因此无尽背包、永恒箱子与午餐盒共用同一条查询路径。
 */
public class RequestCurrentPagePacket implements IMessageHandler<RequestCurrentPagePacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            if (!(player.openContainer instanceof ContainerMultiPage)) return null;
            final ContainerMultiPage container = (ContainerMultiPage) player.openContainer;
            ElinarCurios.networkWrapper.sendTo(new CurrentPagePacket(container.getCurrentPage()), player);
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
