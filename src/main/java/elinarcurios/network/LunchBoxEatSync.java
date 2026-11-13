package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerLunchBox;
import elinarcurios.item.ItemLunchBox;
import elinarcurios.util.LunchBoxNbt;
import elinarcurios.util.VanillaSyncGuard;
import io.netty.buffer.ByteBuf;

/**
 * Server -> client: 进食后只推「变的那一格」（页内扁平索引 + 新堆叠，int 计数）。
 *
 * <p>
 * 没有这个包的话，客户端只能靠原版整包 NBT 同步 -- 吃一口就把 12 页仓库全推一遍。
 * 服务端发完本包后立即对齐原版同步快照（{@link VanillaSyncGuard}），于是那一口只剩这几十字节。
 */
public class LunchBoxEatSync implements IMessage {

    private int flatSlot;
    private ItemStack stack;
    private int stackSize;

    public LunchBoxEatSync() {}

    public LunchBoxEatSync(final int flatSlot, final ItemStack stack) {
        this.flatSlot = flatSlot;
        this.stack = stack;
        this.stackSize = stack != null ? stack.stackSize : 0;
    }

    /** 服务端侧入口：推增量 + 抑制原版整包。 */
    public static void pushAfterEat(final EntityPlayerMP player, final int flatSlot, final ItemStack result) {
        if (player == null) return;
        ElinarCurios.networkWrapper.sendTo(new LunchBoxEatSync(flatSlot, result), player);
        VanillaSyncGuard.alignLunchBoxSnapshots(player);
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        flatSlot = ByteBufUtils.readVarInt(buf, 5);
        stack = ByteBufUtils.readItemStack(buf);
        // ByteBufUtils 里的 Count 是 byte，超过 127 的大堆叠必须在后面单独补一份 int 计数。
        stackSize = ByteBufUtils.readVarInt(buf, 5);
        if (stack != null) stack.stackSize = stackSize;
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, flatSlot, 5);
        ByteBufUtils.writeItemStack(buf, stack);
        ByteBufUtils.writeVarInt(buf, stackSize, 5);
    }

    public static class Handler implements IMessageHandler<LunchBoxEatSync, IMessage> {

        @Override
        public IMessage onMessage(final LunchBoxEatSync message, final MessageContext ctx) {
            final EntityPlayer player = ElinarCurios.proxy.getEntityPlayerFromContext(ctx);
            if (player == null) return null;
            // 进食必须是手持（原版 setItemInUse 的前提），所以客户端要更新的就是当前手持物。
            final ItemStack held = player.inventory.getCurrentItem();
            if (ItemLunchBox.isLunchBox(held)) {
                LunchBoxNbt.writeSlot(held, message.flatSlot, message.stack);
            }
            if (player.openContainer instanceof ContainerLunchBox) {
                ((ContainerLunchBox) player.openContainer).applyEatenSlot(message.flatSlot, message.stack);
            }
            return null;
        }
    }
}
