package elinarcurios.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import elinarcurios.block.BlockBackpackLight;
import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;

/**
 * Applies the passive buffs of the backpack's extra-config pages (settings 12-25) and maintains the
 * invisible miner-light block (setting 22).
 *
 * <p>
 * The modded effects are resolved at runtime from {@link Potion#potionTypes} by class name, so this
 * class never imports Witchery / Blood Magic / Thaumic Horizons / Botania / Blood Arsenal /
 * Hunger Overhaul types. An absent mod simply makes the corresponding row unavailable.
 *
 * <p>
 * Effects are refreshed every {@link #REFRESH_TICKS} ticks with a {@link #DURATION}-tick duration;
 * level 0 / off removes the effect on the next update (and immediately when the GUI packet arrives).
 * The applied amplifier is tracked per player so a different source's effect is not removed by
 * accident.
 */
public final class BackpackBuffHandler {

    private static final int DURATION = 60;
    private static final int REFRESH_TICKS = 20;
    private static final Potion[] CACHE = new Potion[BagSettings.COUNT];

    private static BackpackBuffHandler instance;

    private final Map<UUID, Map<Integer, Integer>> applied = new HashMap<UUID, Map<Integer, Integer>>();
    private final Map<UUID, LightSpot> lights = new HashMap<UUID, LightSpot>();

    public static void setInstance(final BackpackBuffHandler handler) {
        instance = handler;
    }

    public static BackpackBuffHandler getInstance() {
        return instance;
    }

    public static void removeAllFor(final EntityPlayer player) {
        if (instance == null) return;
        instance.removeAll(player);
        instance.removeLight(player);
    }

    @SubscribeEvent
    public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        final EntityPlayer player = event.player;
        if (player == null || player.worldObj == null || player.worldObj.isRemote || player.isDead) return;

        final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
        if (bag == null) {
            removeAll(player);
            removeLight(player);
            return;
        }

        updateLight(player, bag);
        if (player.ticksExisted % REFRESH_TICKS != 0) return;

        for (int id = BagSettings.BUFF_REGEN; id < BagSettings.COUNT; id++) {
            if (id == BagSettings.MINE_LIGHT) continue;
            update(player, bag, id);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null) return;
        removeLight(event.player);
        applied.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onServerStopping(final FMLServerStoppingEvent event) {
        for (final LightSpot spot : lights.values()) removeSpot(spot);
        lights.clear();
    }

    /** Called immediately after the GUI writes a setting, so toggles feel instant. */
    public void applySetting(final EntityPlayer player, final ItemStack bag, final int settingId) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if (settingId == BagSettings.MINE_LIGHT) {
            updateLight(player, bag);
            return;
        }
        if (!BagSettings.isBuffSetting(settingId)) return;
        update(player, bag, settingId);
    }

    private void update(final EntityPlayer player, final ItemStack bag, final int settingId) {
        final Potion potion = potionFor(settingId);
        if (potion == null) {
            removeApplied(player, potion);
            return;
        }

        final int level = Math
            .max(0, Math.min((int) BagSettings.getFloat(bag, settingId), (int) BagSettings.maxValue(settingId)));
        if (level <= 0) {
            removeApplied(player, potion);
            return;
        }

        final int amplifier = BagSettings.isBoolean(settingId) ? 0 : Math.max(0, level - 1);
        final Map<Integer, Integer> map = mapFor(player);
        PotionEffect current = player.getActivePotionEffect(potion);
        if (current != null && current.getAmplifier() != amplifier) {
            // Vanilla keeps the stronger effect when a lower amplifier is re-applied; remove first
            // so lowering a slider takes effect immediately.
            player.removePotionEffect(potion.id);
            current = null;
        }
        if (current == null || current.getDuration() <= DURATION - REFRESH_TICKS) {
            player.addPotionEffect(new PotionEffect(potion.id, DURATION, amplifier, true));
        }
        map.put(Integer.valueOf(potion.id), Integer.valueOf(amplifier));
    }

    private void removeApplied(final EntityPlayer player, final Potion potion) {
        final UUID id = player.getUniqueID();
        final Map<Integer, Integer> map = applied.get(id);
        if (map == null) return;

        final Integer tracked;
        if (potion != null) {
            tracked = map.remove(Integer.valueOf(potion.id));
        } else {
            // The mod is absent, so this setting has no effect to remove; just drop any stale entry.
            tracked = null;
        }
        if (tracked != null) {
            final PotionEffect current = player.getActivePotionEffect(potion);
            if (current != null && current.getAmplifier() == tracked.intValue()) {
                player.removePotionEffect(potion.id);
            }
        }
        if (map.isEmpty()) applied.remove(id);
    }

    private void removeAll(final EntityPlayer player) {
        final UUID id = player.getUniqueID();
        final Map<Integer, Integer> map = applied.remove(id);
        if (map == null) return;

        for (final Map.Entry<Integer, Integer> entry : map.entrySet()) {
            final int potionId = entry.getKey()
                .intValue();
            final Potion potion = potionId >= 0 && potionId < Potion.potionTypes.length ? Potion.potionTypes[potionId]
                : null;
            if (potion == null) continue;
            final PotionEffect current = player.getActivePotionEffect(potion);
            if (current != null && current.getAmplifier() == entry.getValue()
                .intValue()) {
                player.removePotionEffect(potionId);
            }
        }
    }

    // ---------------------------------------------------------------- miner light

    private void updateLight(final EntityPlayer player, final ItemStack bag) {
        if (BlockBackpackLight.forLevel(1) == null) return;
        final int level = Math.max(0, Math.min(15, (int) BagSettings.getFloat(bag, BagSettings.MINE_LIGHT)));
        if (level <= 0) {
            removeLight(player);
            return;
        }

        final Block block = BlockBackpackLight.forLevel(level);
        if (block == null) {
            removeLight(player);
            return;
        }

        final World world = player.worldObj;
        if (world == null) {
            removeLight(player);
            return;
        }

        final UUID id = player.getUniqueID();
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.posY + 0.1D);
        int z = MathHelper.floor_double(player.posZ);
        if (!isAir(world, x, y, z)) {
            if (isAir(world, x, y + 1, z)) {
                y++;
            } else if (isAir(world, x, y + 2, z)) {
                y += 2;
            } else {
                removeLight(player);
                return;
            }
        }

        LightSpot old = lights.get(id);
        if (old != null && (old.world != world || old.x != x || old.y != y || old.z != z)) {
            removeSpot(old);
            lights.remove(id);
            old = null;
        }
        if (old != null && world.getBlock(old.x, old.y, old.z) != null
            && !BlockBackpackLight.isLightBlock(world.getBlock(old.x, old.y, old.z))) {
            lights.remove(id);
            old = null;
        }

        if (old == null) {
            world.setBlock(x, y, z, block, 0, 3);
            lights.put(id, new LightSpot(world, x, y, z, level));
        } else if (old.level != level) {
            world.setBlock(x, y, z, block, 0, 3);
            old.level = level;
        }
    }

    private void removeLight(final EntityPlayer player) {
        final LightSpot spot = lights.remove(player.getUniqueID());
        if (spot != null) removeSpot(spot);
    }

    private static void removeSpot(final LightSpot spot) {
        if (spot == null || spot.world == null) return;
        if (BlockBackpackLight.isLightBlock(spot.world.getBlock(spot.x, spot.y, spot.z))) {
            spot.world.setBlockToAir(spot.x, spot.y, spot.z);
        }
    }

    private static boolean isAir(final World world, final int x, final int y, final int z) {
        return y >= 0 && y < world.getHeight() && world.blockExists(x, y, z) && world.isAirBlock(x, y, z);
    }

    private static final class LightSpot {

        private final World world;
        private final int x;
        private final int y;
        private final int z;
        private int level;

        private LightSpot(final World world, final int x, final int y, final int z, final int level) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }
    }

    // ---------------------------------------------------------------- potion lookup

    private Map<Integer, Integer> mapFor(final EntityPlayer player) {
        final UUID id = player.getUniqueID();
        Map<Integer, Integer> map = applied.get(id);
        if (map == null) {
            map = new HashMap<Integer, Integer>();
            applied.put(id, map);
        }
        return map;
    }

    /** True when the setting's source mod is present (the GUI hides rows that would do nothing). */
    public static boolean isAvailable(final int settingId) {
        if (settingId == BagSettings.MINE_LIGHT) return BlockBackpackLight.forLevel(1) != null;
        return !BagSettings.isBuffSetting(settingId) || potionFor(settingId) != null;
    }

    /** Used by {@link elinarcurios.util.StatusCleanse} so its "bad effect" flag cannot eat a buff. */
    public static boolean isManagedPotion(final int potionId) {
        for (int id = BagSettings.BUFF_REGEN; id < BagSettings.COUNT; id++) {
            if (id == BagSettings.MINE_LIGHT) continue;
            final Potion potion = potionFor(id);
            if (potion != null && potion.id == potionId) return true;
        }
        return false;
    }

    private static Potion potionFor(final int settingId) {
        if (settingId < 0 || settingId >= BagSettings.COUNT) return null;
        if (CACHE[settingId] != null) return CACHE[settingId];

        Potion potion = null;
        switch (settingId) {
            case BagSettings.BUFF_REGEN:
                potion = byVanillaId(10); // Regeneration
                break;
            case BagSettings.BUFF_HEALTH_BOOST:
                potion = byVanillaId(21); // Health Boost
                break;
            case BagSettings.BUFF_RESISTANCE:
                potion = byVanillaId(11); // Resistance
                break;
            case BagSettings.BUFF_ABSORPTION:
                potion = byVanillaId(22); // Absorption
                break;
            case BagSettings.BUFF_HASTE:
                potion = byVanillaId(3); // Haste
                break;
            case BagSettings.BUFF_STRENGTH:
                potion = byVanillaId(5); // Strength
                break;
            case BagSettings.BUFF_JUMP:
                potion = byVanillaId(8); // Jump Boost
                break;
            case BagSettings.BUFF_REFLECT:
                potion = byClass("PotionReciprocation"); // Blood Magic
                break;
            case BagSettings.BUFF_SYNTHESIS:
                potion = byClass("PotionSynthesis"); // Thaumic Horizons
                break;
            case BagSettings.BUFF_VIS_REGEN:
                potion = byClass("PotionVisRegen"); // Thaumic Horizons
                break;
            case BagSettings.BUFF_WELL_FED:
                potion = byClass("PotionWellFed"); // Hunger Overhaul
                break;
            case BagSettings.BUFF_VAMPIRIC_AURA:
                potion = byClassAndName("PotionBloodArsenal", "Vampiric Aura"); // Blood Arsenal
                break;
            case BagSettings.BUFF_SOUL_CROSS:
                potion = byClass("PotionSoulCross"); // Botania
                break;
            default:
                break;
        }
        CACHE[settingId] = potion;
        return potion;
    }

    private static Potion byVanillaId(final int id) {
        return id >= 0 && id < Potion.potionTypes.length ? Potion.potionTypes[id] : null;
    }

    private static Potion byClass(final String simpleName) {
        for (final Potion potion : Potion.potionTypes) {
            if (potion != null && potion.getClass()
                .getSimpleName()
                .equals(simpleName)) {
                return potion;
            }
        }
        return null;
    }

    private static Potion byClassAndName(final String simpleName, final String name) {
        for (final Potion potion : Potion.potionTypes) {
            if (potion == null) continue;
            if (!potion.getClass()
                .getSimpleName()
                .equals(simpleName)) continue;
            if (name.equals(potion.getName())) return potion;
        }
        return null;
    }
}
