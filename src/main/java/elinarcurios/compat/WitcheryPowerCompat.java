package elinarcurios.compat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.emoniph.witchery.common.IPowerSource;
import com.emoniph.witchery.common.PowerSources;
import com.emoniph.witchery.util.Coord;

/**
 * Optional Witchery bridge for the Infinite Backpack.
 *
 * <p>
 * While the backpack is worn, the owner is published to Witchery's global power-source registry as an
 * {@link IPowerSource} with effectively unlimited altar power. Every Witchery consumer that looks up
 * the nearest power source (cauldron rituals/bottling, kettle, distillery, spinning wheel, crystal
 * ball, brazier, mirror, waystone and rites/upkeep) then finds the wearer and works without a real
 * altar.
 *
 * <p>
 * The Witchery types are confined to the nested {@link Impl} class so this outer class can be loaded
 * safely when Witchery is absent. Call sites must guard with
 * {@code Loader.isModLoaded("witchery")} before calling {@link #update} (DEV_GUIDE §3.5).
 */
public final class WitcheryPowerCompat {

    private WitcheryPowerCompat() {}

    /**
     * Called every server tick for every player. Idempotent: registers the source when {@code active},
     * removes it otherwise.
     */
    public static void update(final EntityPlayer player, final boolean active) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        Impl.update(player, active);
    }

    /** Removes the player's source (logout, death clone, etc.). */
    public static void remove(final EntityPlayer player) {
        if (player == null) return;
        Impl.remove(player);
    }

    /** Drops every registered source (server stopping). */
    public static void clearAll() {
        Impl.clearAll();
    }

    /**
     * Witchery-facing implementation. Loaded only when Witchery is installed, because every member
     * here references types from that mod.
     */
    private static final class Impl {

        /** Large enough to pass every {@code getCurrentPower() >= cost} check. */
        private static final float FAKE_POWER = 1.0E9F;

        /** Same range as the default altar, so only devices near the wearer are affected. */
        private static final float RANGE = 16.0F;

        private static final Map<UUID, PlayerPowerSource> SOURCES = new HashMap<UUID, PlayerPowerSource>();

        private Impl() {}

        static void update(final EntityPlayer player, final boolean active) {
            final UUID id = player.getUniqueID();
            if (active) {
                PlayerPowerSource source = SOURCES.get(id);
                if (source == null) {
                    source = new PlayerPowerSource(player);
                    SOURCES.put(id, source);
                } else {
                    source.setPlayer(player);
                }
                // Witchery's registry is created during its mod init; retry if it was not ready yet.
                if (!source.registered) {
                    source.registered = register(source);
                }
            } else {
                final PlayerPowerSource source = SOURCES.remove(id);
                if (source != null) unregister(source);
            }
        }

        static void remove(final EntityPlayer player) {
            final PlayerPowerSource source = SOURCES.remove(player.getUniqueID());
            if (source != null) unregister(source);
        }

        static void clearAll() {
            for (final PlayerPowerSource source : SOURCES.values()) {
                unregister(source);
            }
            SOURCES.clear();
        }

        private static boolean register(final PlayerPowerSource source) {
            final PowerSources registry = PowerSources.instance();
            if (registry == null) return false;
            registry.registerPowerSource(source);
            return true;
        }

        private static void unregister(final PlayerPowerSource source) {
            source.registered = false;
            final PowerSources registry = PowerSources.instance();
            if (registry != null) registry.removePowerSource(source);
        }

        /** Player-bound source: infinite power, no deduction, follows the wearer. */
        private static final class PlayerPowerSource implements IPowerSource {

            private EntityPlayer player;
            private Coord lastLocation;
            private boolean registered;

            PlayerPowerSource(final EntityPlayer player) {
                setPlayer(player);
            }

            void setPlayer(final EntityPlayer player) {
                this.player = player;
                if (player != null) this.lastLocation = new Coord(player);
            }

            @Override
            public World getWorld() {
                return player == null ? null : player.worldObj;
            }

            @Override
            public Coord getLocation() {
                return player == null ? lastLocation : new Coord(player);
            }

            @Override
            public boolean isLocationEqual(final Coord location) {
                return location != null && location.equals(getLocation());
            }

            @Override
            public boolean consumePower(final float amount) {
                return true;
            }

            @Override
            public float getCurrentPower() {
                return FAKE_POWER;
            }

            @Override
            public float getRange() {
                return RANGE;
            }

            @Override
            public int getEnhancementLevel() {
                return 0;
            }

            @Override
            public boolean isPowerInvalid() {
                return player == null || player.isDead || player.worldObj == null;
            }
        }
    }
}
