package elinarcurios.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import elinarcurios.compat.ThaumcraftCompat;
import elinarcurios.player.WandPool;
import elinarcurios.util.FabulousText;
import elinarcurios.util.WandPoolConstants;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.wands.ItemFocusBasic;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.items.wands.ItemWandCasting;

/**
 * 天玄之杖 —— 注册为 elinarcurios 自己的物品(而非复用 TC 的 itemWandCasting),
 * 因此创造标签 / NEI 物品列表 / 配方输出全部可见(TC 的 getSubItems 是硬编码枚举,
 * 自定义 rod/cap 组合不会自动出现在 TC 物品列表里)。
 *
 * <p>
 * 2026-11-19 起 vis 改为「玩家共享魔力池」:
 * <ul>
 * <li>池子存在玩家 {@code PlayerPersisted} 里(6 x long),死亡/换维度不丢;</li>
 * <li>杖本身只存「绑定玩家 UUID + 显示快照」,同一个玩家的所有天玄之杖共用同一个池;</li>
 * <li>首次使用永久绑定(杖可能被放进奥术工作台/附魔器等没有玩家上下文的机器里);</li>
 * <li>客户端显示统一读 {@code WandPoolSyncPacket} 推来的缓存,所以「工作台一把、手上一把」
 * 任何时刻显示的都是同一个数。</li>
 * </ul>
 *
 * <p>
 * TC 的 vis 接口是 int,超过 {@link Integer#MAX_VALUE} 的部分对外夹取;tooltip 显示完整 long,
 * 拨盘(HUD)按玩家要求显示 int 上限。详见
 * {@code D:\Agent\GTNH\ref\ElinarCurios-天玄之杖-共享魔力池-long存储-20261119.md}。
 *
 * <p>
 * 先例:ThaumicHorizons 的 ItemWandCastingDisposable 同款继承方案;
 * 纹理照搬 Avaritia 的水晶缠绕杖(wand_rod_neutronium / wand_cap_crystal,见 LICENSES/LICENSE-MIT-Avaritia.txt)。
 */
public class ItemElinarWand extends ItemWandCasting {

    public static ItemElinarWand instance;

    /** 组装好的默认法杖(空池)。 */
    public ItemStack wand;

    public ItemElinarWand() {
        this.maxStackSize = 1;
        this.setMaxDamage(0);
        this.setHasSubtypes(true);
        this.setCreativeTab(ElinarCurios.creativeTab);
        this.wand = new ItemStack(this);
        this.setCap(this.wand, ThaumcraftCompat.wandCapElinar);
        this.setRod(this.wand, ThaumcraftCompat.wandRodElinar);
    }

    // ---------------------------------------------------------------- identity / display

    /** 「天玄之杖」彩虹渐变动画名(服务端返回静态文本,安全)。 */
    @Override
    public String getItemStackDisplayName(final ItemStack stack) {
        return FabulousText.makeFabulous(StatCollector.translateToLocal("item.elinarcurios.wand.name"));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void getSubItems(final Item item, final CreativeTabs tab, final List list) {
        // copy(): creative inventory must never hand out (and later bind) the template stack itself.
        list.add(this.wand.copy());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List tooltip,
        final boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("item.elinarcurios.wand.tooltip"));

        final String owner = WandPool.ownerName(stack);
        final String binding = owner == null ? StatCollector.translateToLocal("item.elinarcurios.wand.unbound")
            : StatCollector.translateToLocalFormatted("item.elinarcurios.wand.bound", owner);
        tooltip.add("§b" + StatCollector.translateToLocal("item.elinarcurios.wand.pool") + " §7" + binding);

        final boolean shift = Thaumcraft.proxy.isShiftKeyDown();
        if (shift) {
            tooltip.add(
                "§7" + StatCollector.translateToLocal("item.elinarcurios.wand.capacity")
                    + " §f"
                    + WandPool.format(WandPoolConstants.CAPACITY)
                    + " §7"
                    + StatCollector.translateToLocal("item.elinarcurios.wand.eachelement"));
            for (final Aspect aspect : WandPool.PRIMALS) {
                tooltip.add(
                    " §" + aspect.getChatcolor()
                        + aspectName(aspect)
                        + " §f"
                        + WandPool.format(displayPool(stack, aspect)));
            }
        } else {
            final StringBuilder line = new StringBuilder();
            for (final Aspect aspect : WandPool.PRIMALS) {
                if (line.length() > 0) line.append(" §7|§r");
                line.append("§")
                    .append(aspect.getChatcolor())
                    .append(WandPool.format(displayPool(stack, aspect)));
            }
            tooltip.add(line.toString());
        }

        final ItemFocusBasic focus = getFocus(stack);
        if (focus != null) {
            tooltip.add(
                EnumChatFormatting.BOLD + ""
                    + EnumChatFormatting.ITALIC
                    + ""
                    + EnumChatFormatting.GREEN
                    + focus.getItemStackDisplayName(getFocusItem(stack)));
            if (shift) {
                focus.addFocusInformation(getFocusItem(stack), player, tooltip, advanced);
            }
        }
    }

    private static String aspectName(final Aspect aspect) {
        final String localized = aspect.getLocalizedDescription();
        if (localized == null || localized.isEmpty()) return aspect.getName();
        final int comma = localized.indexOf(',');
        return comma > 0 ? localized.substring(0, comma) : localized;
    }

    // ---------------------------------------------------------------- vis storage bridge

    /**
     * TC's int capacity face. The pool's real capacity is a long, but every int-based consumer
     * (dial bar, third-party chargers) still needs a sane int value; Integer.MAX_VALUE makes the
     * dial read "full" once the pool leaves int range, exactly as requested.
     */
    @Override
    public int getMaxVis(final ItemStack stack) {
        return (int) Math.min(WandPoolConstants.CAPACITY, (long) Integer.MAX_VALUE);
    }

    @Override
    public int getVis(final ItemStack stack, final Aspect aspect) {
        if (aspect == null || !aspect.isPrimal()) return super.getVis(stack, aspect);
        return WandPool.face(displayPool(stack, aspect));
    }

    @Override
    public void storeVis(final ItemStack stack, final Aspect aspect, final int amount) {
        if (aspect == null || !aspect.isPrimal()) {
            super.storeVis(stack, aspect, amount);
            return;
        }
        if (!isServerSide()) return; // authoritative value arrives through WandPoolSyncPacket

        final EntityPlayer owner = WandPool.resolveOwner(stack);
        if (owner == null) return;

        final long current = WandPool.get(owner, aspect);
        final long next;
        if (amount < 0) {
            // int overflow from a read-modify-write on a clamped value: never corrupt the pool.
            next = current;
        } else if (amount == 0) {
            // explicit "empty the wand" — unique value whose absolute and delta interpretations agree.
            next = 0L;
        } else if (current > (long) WandPoolConstants.INT_FACE) {
            // TC/addons do storeVis(getVis() +/- x); getVis() was clamped, so apply their delta.
            next = current + ((long) amount - (long) WandPoolConstants.INT_FACE);
        } else {
            next = amount;
        }
        WandPool.set(owner, aspect, next);
        WandPool.writeSnapshot(stack, owner);
    }

    @Override
    public AspectList getAllVis(final ItemStack stack) {
        final AspectList out = new AspectList();
        for (final Aspect aspect : WandPool.PRIMALS) out.merge(aspect, getVis(stack, aspect));
        return out;
    }

    @Override
    public void storeAllVis(final ItemStack stack, final AspectList aspects) {
        if (aspects == null || !isServerSide()) return;
        final EntityPlayer owner = WandPool.resolveOwner(stack);
        if (owner == null) return;
        for (final Aspect aspect : aspects.getAspects()) {
            if (aspect == null || !aspect.isPrimal()) continue;
            WandPool.set(owner, aspect, aspects.getAmount(aspect));
        }
        WandPool.writeSnapshot(stack, owner);
    }

    @Override
    public AspectList getAspectsWithRoom(final ItemStack stack) {
        final AspectList out = new AspectList();
        final String ownerName = WandPool.ownerName(stack);
        if (ownerName == null) return out; // unbound: never charge
        if (isServerSide()) {
            final EntityPlayer owner = WandPool.resolveOwner(stack);
            if (owner == null) return out; // owner offline: do not let pedestals drain nodes into nothing
            for (final Aspect aspect : WandPool.PRIMALS) {
                if (WandPool.get(owner, aspect) < WandPoolConstants.CAPACITY) out.add(aspect, 1);
            }
        } else {
            for (final Aspect aspect : WandPool.PRIMALS) {
                if (displayPool(stack, aspect) < WandPoolConstants.CAPACITY) out.add(aspect, 1);
            }
        }
        return out;
    }

    @Override
    public int addVis(final ItemStack stack, final Aspect aspect, final int amount, final boolean doit) {
        if (aspect == null || !aspect.isPrimal() || amount <= 0) return 0;
        final long leftover = addRaw(stack, aspect, (long) amount * 100L, doit);
        return (int) Math.min(leftover / 100L, (long) Integer.MAX_VALUE);
    }

    @Override
    public int addRealVis(final ItemStack stack, final Aspect aspect, final int amount, final boolean doit) {
        if (aspect == null || !aspect.isPrimal() || amount <= 0) return 0;
        final long leftover = addRaw(stack, aspect, amount, doit);
        return (int) Math.min(leftover, (long) Integer.MAX_VALUE);
    }

    /** Adds {@code amount} raw vis and returns the raw leftover. */
    private long addRaw(final ItemStack stack, final Aspect aspect, final long amount, final boolean doit) {
        if (!isServerSide()) {
            // Display-only estimate; the server owns the pool and syncs the real value.
            final long current = displayPool(stack, aspect);
            final long accepted = Math
                .max(0L, Math.min(amount, WandPoolConstants.CAPACITY - Math.min(current, WandPoolConstants.CAPACITY)));
            return amount - accepted;
        }
        final EntityPlayer owner = WandPool.resolveOwner(stack);
        if (owner == null) return amount;
        final long current = WandPool.get(owner, aspect);
        final long accepted = Math.max(0L, Math.min(amount, WandPoolConstants.CAPACITY - current));
        if (doit && accepted > 0L) {
            WandPool.set(owner, aspect, current + accepted);
            WandPool.writeSnapshot(stack, owner);
        }
        return amount - accepted;
    }

    @Override
    public boolean consumeVis(final ItemStack stack, final EntityPlayer player, final Aspect aspect, final int amount,
        final boolean crafting) {
        if (aspect == null || !aspect.isPrimal()) return super.consumeVis(stack, player, aspect, amount, crafting);
        final int cost = (int) (amount * getConsumptionModifier(stack, player, aspect, crafting));
        if (!isServerSide()) return displayPool(stack, aspect) >= cost;

        final EntityPlayer owner = ownerForUse(stack, player);
        if (owner == null) return false;
        final long current = WandPool.get(owner, aspect);
        if (current < cost) return false;
        WandPool.set(owner, aspect, current - cost);
        WandPool.writeSnapshot(stack, owner);
        return true;
    }

    @Override
    public boolean consumeAllVis(final ItemStack stack, final EntityPlayer player, final AspectList aspects,
        final boolean doit, final boolean crafting) {
        if (aspects == null || aspects.size() == 0) return false;

        final AspectList cost = new AspectList();
        for (final Aspect aspect : aspects.getAspects()) {
            if (aspect == null) continue;
            int value = aspects.getAmount(aspect);
            value = (int) (value * getConsumptionModifier(stack, player, aspect, crafting));
            cost.add(aspect, value);
        }
        if (cost.size() == 0) return false;

        if (!isServerSide()) {
            for (final Aspect aspect : cost.getAspects()) {
                if (displayPool(stack, aspect) < cost.getAmount(aspect)) return false;
            }
            return true;
        }

        final EntityPlayer owner = ownerForUse(stack, player);
        if (owner == null) return false;
        for (final Aspect aspect : cost.getAspects()) {
            if (WandPool.get(owner, aspect) < cost.getAmount(aspect)) return false;
        }
        if (doit) {
            for (final Aspect aspect : cost.getAspects()) {
                WandPool.set(owner, aspect, WandPool.get(owner, aspect) - cost.getAmount(aspect));
            }
            WandPool.writeSnapshot(stack, owner);
        }
        return true;
    }

    /** Owner for a use: binds an unbound wand to the acting (server) player on first use. */
    private static EntityPlayer ownerForUse(final ItemStack stack, final EntityPlayer player) {
        EntityPlayer owner = WandPool.resolveOwner(stack);
        if (owner == null && player != null) {
            WandPool.bind(stack, player);
            owner = WandPool.resolveOwner(stack);
        }
        return owner;
    }

    // ---------------------------------------------------------------- lifecycle / binding

    @Override
    public void onUpdate(final ItemStack stack, final World world, final Entity entity, final int slot,
        final boolean currentItem) {
        super.onUpdate(stack, world, entity, slot, currentItem);
        if (world == null || world.isRemote || !(entity instanceof EntityPlayerMP)) return;

        final EntityPlayerMP player = (EntityPlayerMP) entity;
        if (!WandPool.hasOwner(stack)) {
            WandPool.bind(stack, player); // permanent first-use binding
            return;
        }
        if (WandPool.isOwnedBy(stack, player) && !WandPool.snapshotMatches(stack, player)) {
            WandPool.writeSnapshot(stack, player);
        }
    }

    @Override
    public boolean onItemUseFirst(final ItemStack stack, final EntityPlayer player, final World world, final int x,
        final int y, final int z, final int side, final float hitX, final float hitY, final float hitZ) {
        if (!world.isRemote) WandPool.bind(stack, player);
        return super.onItemUseFirst(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack stack, final World world, final EntityPlayer player) {
        if (!world.isRemote) WandPool.bind(stack, player);
        return super.onItemRightClick(stack, world, player);
    }

    @Override
    public void onUsingTick(final ItemStack stack, final EntityPlayer player, final int count) {
        if (player != null && player.worldObj != null && !player.worldObj.isRemote) WandPool.bind(stack, player);
        super.onUsingTick(stack, player, count);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isServerSide() {
        return FMLCommonHandler.instance()
            .getEffectiveSide()
            .isServer();
    }

    /**
     * Current pool value used for display.
     *
     * <p>
     * Server: the online owner's pool; when the owner is offline the pool is unreachable, so this returns
     * 0 instead of the snapshot. That matters for third-party blocks that do
     * {@code if (getVis() >= cost) storeVis(getVis() - cost)} — returning the snapshot would let them
     * "craft" and silently fail to charge. Client: the synced cache when this wand belongs to the local
     * player, otherwise the item's own snapshot (foreign wands, display only).
     */
    private static long displayPool(final ItemStack stack, final Aspect aspect) {
        if (isServerSide()) {
            final EntityPlayer owner = WandPool.resolveOwner(stack);
            return owner != null ? WandPool.get(owner, aspect) : 0L;
        }
        final String owner = WandPool.ownerName(stack);
        if (owner != null && ElinarCurios.proxy.hasClientWandPool()) {
            final String local = ElinarCurios.proxy.getClientWandPoolOwner();
            if (local != null && owner.equalsIgnoreCase(local)) {
                return ElinarCurios.proxy.getClientWandPool(WandPool.indexOf(aspect));
            }
        }
        return WandPool.snapshot(stack, aspect);
    }
}
