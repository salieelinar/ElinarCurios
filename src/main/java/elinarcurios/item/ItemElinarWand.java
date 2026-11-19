package elinarcurios.item;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
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
 * 澶╃巹涔嬫潠 鈥斺€?娉ㄥ唽涓?elinarcurios 鑷繁鐨勭墿鍝?鑰岄潪澶嶇敤 TC 鐨?itemWandCasting),
 * 鍥犳鍒涢€犳爣绛?/ NEI 鐗╁搧鍒楄〃 / 閰嶆柟杈撳嚭鍏ㄩ儴鍙(TC 鐨?getSubItems 鏄‖缂栫爜鏋氫妇,
 * 鑷畾涔?rod/cap 缁勫悎涓嶄細鑷姩鍑虹幇鍦?TC 鐗╁搧鍒楄〃閲?銆?
 *
 * <p>
 * 2026-11-19 璧?vis 鏀逛负銆岀帺瀹跺叡浜瓟鍔涙睜銆?
 * <ul>
 * <li>姹犲瓙瀛樺湪涓栫晫绾?{@code WandPoolData}(UUID -> 瑕佺礌琛?,涓讳汉涓嬬嚎鍚庢満鍣ㄤ粛鑳芥墸;</li>
 * <li>鏀寔<b>鎵€鏈?/b>瑕佺礌(涓嶉檺浜?6 绉嶅熀纭€瑕佺礌),涓哄悗缁満鍒堕鐣?</li>
 * <li>鏉栨湰韬彧瀛樸€岀粦瀹氱帺瀹?UUID + 鏄剧ず蹇収銆?鍚屼竴涓帺瀹剁殑鎵€鏈夊ぉ鐜勪箣鏉栧叡鐢ㄥ悓涓€涓睜;</li>
 * <li>棣栨浣跨敤姘镐箙缁戝畾(鏉栧彲鑳借鏀捐繘濂ユ湳宸ヤ綔鍙?闄勯瓟鍣ㄧ瓑娌℃湁鐜╁涓婁笅鏂囩殑鏈哄櫒閲?;</li>
 * <li>瀹㈡埛绔樉绀虹粺涓€璇?{@code WandPoolSyncPacket} 鎺ㄦ潵鐨勭紦瀛?鎵€浠ャ€屽伐浣滃彴涓€鎶娿€佹墜涓婁竴鎶娿€?
 * 浠讳綍鏃跺埢鏄剧ず鐨勯兘鏄悓涓€涓暟銆?/li>
 * </ul>
 *
 * <p>
 * TC 鐨?vis 鎺ュ彛鏄?int,瓒呰繃 {@link Integer#MAX_VALUE} 鐨勯儴鍒嗗澶栧す鍙?tooltip 鏄剧ず瀹屾暣 long,
 * 鎷ㄧ洏(HUD)鎸夌帺瀹惰姹傛樉绀?int 涓婇檺銆傝瑙?
 * {@code D:\Agent\GTNH\ref\ElinarCurios-澶╃巹涔嬫潠-鍏变韩榄斿姏姹?long瀛樺偍-20261119.md}銆?
 *
 * <p>
 * 鍏堜緥:ThaumicHorizons 鐨?ItemWandCastingDisposable 鍚屾缁ф壙鏂规;
 * 绾圭悊鐓ф惉 Avaritia 鐨勬按鏅剁紶缁曟潠(wand_rod_neutronium / wand_cap_crystal,瑙?LICENSES/LICENSE-MIT-Avaritia.txt)銆?
 */
public class ItemElinarWand extends ItemWandCasting {

    public static ItemElinarWand instance;

    /** 缁勮濂界殑榛樿娉曟潠(绌烘睜)銆? */
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

    /** 銆屽ぉ鐜勪箣鏉栥€嶅僵铏规笎鍙樺姩鐢诲悕(鏈嶅姟绔繑鍥為潤鎬佹枃鏈?瀹夊叏)銆? */
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
        tooltip.add("搂b" + StatCollector.translateToLocal("item.elinarcurios.wand.pool") + " 搂7" + binding);

        final Map<String, Long> stored = storedAspects(stack);
        int types = 0;
        long total = 0L;
        for (final Long value : stored.values()) {
            if (value == null || value <= 0L) continue;
            types++;
            total = saturatedAdd(total, value);
        }

        if (GuiScreen.isShiftKeyDown()) {
            // Shift: full per-aspect breakdown (the old layout, now only on demand).
            for (final Aspect aspect : WandPool.PRIMALS) {
                tooltip.add(
                    "搂" + aspect.getChatcolor()
                        + StatCollector.translateToLocalFormatted(
                            "item.elinarcurios.wand.aspectLine",
                            aspectName(aspect),
                            aspect.getName()));
                tooltip.add("搂f" + WandPool.format(displayPool(stack, aspect)));
            }
            tooltip.add(StatCollector.translateToLocalFormatted("item.elinarcurios.wand.totalLine", types));
            tooltip.add("搂f" + WandPool.format(total));
        } else {
            // Default: compact, one colored number per primal, no names; total on one line.
            for (final Aspect aspect : WandPool.PRIMALS) {
                tooltip.add("搂" + aspect.getChatcolor() + WandPool.format(displayPool(stack, aspect)));
            }
            tooltip.add(
                StatCollector.translateToLocalFormatted("item.elinarcurios.wand.totalLine", types) + " 搂f"
                    + WandPool.format(total));
        }

        final ItemFocusBasic focus = getFocus(stack);
        if (focus != null) {
            tooltip.add(
                EnumChatFormatting.BOLD + ""
                    + EnumChatFormatting.ITALIC
                    + ""
                    + EnumChatFormatting.GREEN
                    + focus.getItemStackDisplayName(getFocusItem(stack)));
            if (Thaumcraft.proxy.isShiftKeyDown()) {
                focus.addFocusInformation(getFocusItem(stack), player, tooltip, advanced);
            }
        }
    }

    private static String aspectName(final Aspect aspect) {
        final String key = "item.elinarcurios.wand.aspect." + aspect.getTag();
        final String translated = StatCollector.translateToLocal(key);
        if (!key.equals(translated)) return translated;
        final String localized = aspect.getLocalizedDescription();
        if (localized == null || localized.isEmpty()) return aspect.getName();
        final int comma = localized.indexOf(',');
        return comma > 0 ? localized.substring(0, comma) : localized;
    }

    /** Saturated positive addition; the per-aspect cap makes a plain sum able to overflow a long. */
    private static long saturatedAdd(final long a, final long b) {
        final long sum = a + b;
        return sum < 0L ? Long.MAX_VALUE : sum;
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
        if (aspect == null) return super.getVis(stack, aspect);
        return WandPool.face(displayPool(stack, aspect));
    }

    @Override
    public void storeVis(final ItemStack stack, final Aspect aspect, final int amount) {
        if (aspect == null) return;
        if (!isServerSide()) return; // authoritative value arrives through WandPoolSyncPacket
        if (!WandPool.hasOwner(stack)) return;

        final long current = WandPool.getForOwner(stack, aspect);
        final long next;
        if (amount < 0) {
            // int overflow from a read-modify-write on a clamped value: never corrupt the pool.
            next = current;
        } else if (amount == 0) {
            // explicit "empty the wand" 鈥?unique value whose absolute and delta interpretations agree.
            next = 0L;
        } else if (current > (long) WandPoolConstants.INT_FACE) {
            // TC/addons do storeVis(getVis() +/- x); getVis() was clamped, so apply their delta.
            next = current + ((long) amount - (long) WandPoolConstants.INT_FACE);
        } else {
            next = amount;
        }
        WandPool.setForOwner(stack, aspect, next);
        WandPool.writeSnapshotFor(stack);
    }

    @Override
    public AspectList getAllVis(final ItemStack stack) {
        final AspectList out = new AspectList();
        for (final Aspect aspect : WandPool.PRIMALS) out.merge(aspect, getVis(stack, aspect));
        return out;
    }

    @Override
    public void storeAllVis(final ItemStack stack, final AspectList aspects) {
        if (aspects == null || !isServerSide() || !WandPool.hasOwner(stack)) return;
        for (final Aspect aspect : aspects.getAspects()) {
            if (aspect == null) continue;
            WandPool.setForOwner(stack, aspect, aspects.getAmount(aspect));
        }
        WandPool.writeSnapshotFor(stack);
    }

    @Override
    public AspectList getAspectsWithRoom(final ItemStack stack) {
        final AspectList out = new AspectList();
        if (WandPool.ownerName(stack) == null) return out; // unbound: never charge
        // TC nodes transfer only the aspects present in this list. Returning just the six
        // primals made compound aspects impossible to absorb; expose every registered aspect
        // that still has room in the long pool.
        if (isServerSide()) {
            if (!WandPool.isOwnerAccessible(stack)) return out;
            for (final Aspect aspect : Aspect.aspects.values()) {
                if (WandPool.getForOwner(stack, aspect) < WandPoolConstants.CAPACITY) out.add(aspect, 1);
            }
        } else {
            for (final Aspect aspect : Aspect.aspects.values()) {
                if (displayPool(stack, aspect) < WandPoolConstants.CAPACITY) out.add(aspect, 1);
            }
        }
        return out;
    }

    @Override
    public int addVis(final ItemStack stack, final Aspect aspect, final int amount, final boolean doit) {
        if (aspect == null || amount <= 0) return 0;
        final long leftover = addRaw(stack, aspect, (long) amount * 100L, doit);
        return (int) Math.min(leftover / 100L, (long) Integer.MAX_VALUE);
    }

    @Override
    public int addRealVis(final ItemStack stack, final Aspect aspect, final int amount, final boolean doit) {
        if (aspect == null || amount <= 0) return 0;
        final long leftover = addRaw(stack, aspect, amount, doit);
        return (int) Math.min(leftover, (long) Integer.MAX_VALUE);
    }

    /** Adds {@code amount} raw vis of ANY aspect and returns the raw leftover. */
    private long addRaw(final ItemStack stack, final Aspect aspect, final long amount, final boolean doit) {
        if (!isServerSide()) {
            // Display-only estimate; the server owns the pool and syncs the real value.
            final long current = displayPool(stack, aspect);
            final long accepted = Math
                .max(0L, Math.min(amount, WandPoolConstants.CAPACITY - Math.min(current, WandPoolConstants.CAPACITY)));
            return amount - accepted;
        }
        if (!WandPool.isOwnerAccessible(stack)) return amount;
        final long current = WandPool.getForOwner(stack, aspect);
        final long accepted = Math.max(0L, Math.min(amount, WandPoolConstants.CAPACITY - current));
        if (doit && accepted > 0L) {
            WandPool.setForOwner(stack, aspect, current + accepted);
            WandPool.writeSnapshotFor(stack);
        }
        return amount - accepted;
    }

    @Override
    public boolean consumeVis(final ItemStack stack, final EntityPlayer player, final Aspect aspect, final int amount,
        final boolean crafting) {
        if (aspect == null) return false;
        final int cost = (int) (amount * getConsumptionModifier(stack, player, aspect, crafting));
        if (!isServerSide()) return displayPool(stack, aspect) >= cost;

        if (!ensureAccessible(stack, player)) return false;
        final long current = WandPool.getForOwner(stack, aspect);
        if (current < cost) return false;
        WandPool.setForOwner(stack, aspect, current - cost);
        WandPool.writeSnapshotFor(stack);
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

        if (!ensureAccessible(stack, player)) return false;
        for (final Aspect aspect : cost.getAspects()) {
            if (WandPool.getForOwner(stack, aspect) < cost.getAmount(aspect)) return false;
        }
        if (doit) {
            for (final Aspect aspect : cost.getAspects()) {
                WandPool.setForOwner(stack, aspect, WandPool.getForOwner(stack, aspect) - cost.getAmount(aspect));
            }
            WandPool.writeSnapshotFor(stack);
        }
        return true;
    }

    /**
     * Binds an unbound wand to the acting (server) player on first use, then reports whether the
     * owner's pool is reachable 鈥?the world database works online and offline alike.
     */
    private static boolean ensureAccessible(final ItemStack stack, final EntityPlayer player) {
        if (WandPool.isOwnerAccessible(stack)) return true;
        if (player != null) WandPool.bind(stack, player);
        return WandPool.isOwnerAccessible(stack);
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
     * Server: the world database (works for online and offline owners). Client: the synced cache when
     * this wand belongs to the local player, otherwise the item's own snapshot (foreign wands).
     */
    private static long displayPool(final ItemStack stack, final Aspect aspect) {
        if (aspect == null) return 0L;
        if (isServerSide()) return WandPool.getForOwner(stack, aspect);

        final String owner = WandPool.ownerName(stack);
        if (owner != null && ElinarCurios.proxy.hasClientWandPool()) {
            final String local = ElinarCurios.proxy.getClientWandPoolOwner();
            if (local != null && owner.equalsIgnoreCase(local)) {
                return ElinarCurios.proxy.getClientWandPool(aspect.getTag());
            }
        }
        return WandPool.snapshot(stack, aspect);
    }

    /** All non-zero aspects for the tooltip: server database, client cache, or the item snapshot. */
    private static Map<String, Long> storedAspects(final ItemStack stack) {
        if (isServerSide()) return WandPool.storedForOwner(stack);
        final String owner = WandPool.ownerName(stack);
        if (owner != null && ElinarCurios.proxy.hasClientWandPool()) {
            final String local = ElinarCurios.proxy.getClientWandPoolOwner();
            if (local != null && owner.equalsIgnoreCase(local)) return ElinarCurios.proxy.getClientWandPoolMap();
        }
        return WandPool.snapshotAll(stack);
    }
}
