package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.item.ItemLunchBox;
import io.netty.buffer.ByteBuf;

/** Client -> server: 请求打开手持午餐盒的仓库界面（是否真的开界面，由服务端再判定一次）。 */
public class OpenLunchBoxPacket implements IMessageHandler<OpenLunchBoxPacket.Message, IMessage> {

    @Override
    public IMessage onMessage(final Message message, final MessageContext ctx) {
        if (ctx.side.isServer()) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            final ItemStack held = player.getHeldItem();
            if (!ItemLunchBox.isLunchBox(held)) return null;
            final ItemLunchBox lunchBox = (ItemLunchBox) held.getItem();

            // 客户端说的是「盒子合着，请打开仓库」（开盖却吃不了的情况客户端根本不会发这个包）。
            // 唯一要防的是：客户端 NBT 滞后时服务端其实已经在进食了 —— 那就绝不能弹界面打断它。
            if (player.isUsingItem()) return null;

            lunchBox.setIsOpen(held, true);
            FMLNetworkHandler.openGui(
                player,
                ElinarCurios.instance,
                ElinarCurios.GUI_ID_LUNCH_BOX,
                player.worldObj,
                (int) player.posX,
                (int) player.posY,
                (int) player.posZ);
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
