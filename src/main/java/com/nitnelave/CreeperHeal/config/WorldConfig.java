package com.nitnelave.CreeperHeal.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.inventory.InventoryHolder;

import com.nitnelave.CreeperHeal.CreeperHeal;
import com.nitnelave.CreeperHeal.PluginHandler;
import com.nitnelave.CreeperHeal.block.BlockId;
import com.nitnelave.CreeperHeal.utils.CreeperLog;
import com.nitnelave.CreeperHeal.utils.FileUtils;

/**
 * World configuration settings. Gathers all the settings for a given world.
 * 
 * @author nitnelave
 * 
 */
public class WorldConfig {
    //TODO: Apply the same system as the CreeperConfig.

    private final HashMap<String, ConfigValue<Boolean>> booleans = new HashMap<String, ConfigValue<Boolean>> ();
    private final String name;
    private IntegerConfigValue repairTime, replaceLimit;
    private BlockIdListValue replaceBlackList, griefPlaceList, protectList, replaceWhiteList;
    private final YamlConfiguration config = new YamlConfiguration (), advanced = new YamlConfiguration (), grief = new YamlConfiguration ();
    private final File worldFolder, configFile, advancedFile, griefFile;

    /**
     * Main constructor. Load the config from the file, or create a default one
     * if needed.
     * 
     * @param name
     *            The world's name.
     * @param folder
     *            The plugin folder.
     * @throws FileNotFoundException
     *             Carry the exception from load.
     * @throws IOException
     *             Carry the exception from load.
     * @throws InvalidConfigurationException
     *             Carry the exception from load.
     */
    public WorldConfig (String name) {
        this.name = name;
        worldFolder = new File (CreeperHeal.getCHFolder ().getPath () + "/" + name);
        configFile = new File (worldFolder + "/config.yml");
        advancedFile = new File (worldFolder + "/advanced.yml");
        griefFile = new File (worldFolder + "/grief.yml");
        fillMaps ();
    }

    private void fillMaps () {
        for (WCfgVal v : WCfgVal.values ())
            if (v.getDefaultValue () instanceof Boolean)
                booleans.put (v.getKey (), new BooleanConfigValue (v, getFile (v)));
            else
                switch (v)
                {
                    case REPAIR_TIME:
                        repairTime = new IntegerConfigValue (v, getFile (v));
                        break;
                    case REPLACE_LIMIT:
                        replaceLimit = new IntegerConfigValue (v, getFile (v));
                        break;
                    case REPLACE_BLACK_LIST:
                        replaceBlackList = new BlockIdListValue (v, getFile (v));
                        break;
                    case REPLACE_WHITE_LIST:
                        replaceWhiteList = new BlockIdListValue (v, getFile (v));
                        break;
                    case GRIEF_PLACE_LIST:
                        griefPlaceList = new BlockIdListValue (v, getFile (v));
                        break;
                    case PROTECTED_LIST:
                        protectList = new BlockIdListValue (v, getFile (v));
                        break;
                    default:
                        CreeperLog.warning ("Unknown config value : " + v.toString ());
                }
    }

    private YamlConfiguration getFile (WCfgVal v) {
        switch (v.getFile ())
        {
            case ADVANCED:
                return advanced;
            case CONFIG:
                return config;
            case GRIEF:
                return grief;
        }
        return null;
    }

    /**
     * Get the world's name.
     * 
     * @return The world's name.
     */
    public String getName () {
        return name;
    }

    /**
     * Get whether the world has timed repairs enabled.
     * 
     * @return Whether the world has timed repairs enabled.
     */
    public boolean isRepairTimed () {
        return repairTime.getValue () > -1;
    }

    /**
     * Load the config from the file.
     */
    public void load () {
        worldFolder.mkdirs ();
        if (!configFile.exists ())
            FileUtils.copyJarConfig (configFile, "world.yml");
        if (!advancedFile.exists ())
            FileUtils.copyJarConfig (advancedFile, "world-advanced.yml");
        if (!griefFile.exists ())
            FileUtils.copyJarConfig (griefFile, "world-grief.yml");
        try
        {
            config.load (configFile);
            advanced.load (advancedFile);
            grief.load (griefFile);
        } catch (FileNotFoundException e)
        {
            e.printStackTrace ();
            return;
        } catch (IOException e)
        {
            e.printStackTrace ();
            return;
        } catch (InvalidConfigurationException e)
        {
            e.printStackTrace ();
            return;
        }

        for (ConfigValue<Boolean> v : booleans.values ())
            v.load ();
        repairTime.load ();
        replaceLimit.load ();
        replaceBlackList.load ();
        replaceWhiteList.load ();
        protectList.load ();
        griefPlaceList.load ();

    }

    /**
     * Write the world's settings to the corresponding file.
     */
    public void save () {
        for (ConfigValue<Boolean> v : booleans.values ())
            v.write ();
        repairTime.write ();
        replaceLimit.write ();
        replaceBlackList.write ();
        replaceWhiteList.write ();
        protectList.write ();
        griefPlaceList.write ();

        try
        {
            config.save (configFile);
            advanced.save (advancedFile);
            grief.save (griefFile);
        } catch (IOException e)
        {
            e.printStackTrace ();
        }
    }

    public boolean getBool (WCfgVal v) {
        return booleans.get (v.getKey ()).getValue ();
    }

    /**
     * Get whether damage caused by an entity should be replaced in this world.
     * 
     * @param entity
     *            The entity that caused the damage.
     * @return Whether the damage should be replaced.
     */
    public boolean shouldReplace (Entity entity) {
        if (entity != null)
            if (entity instanceof Creeper && getBool (WCfgVal.CREEPERS) || entity instanceof TNTPrimed && getBool (WCfgVal.TNT) || entity instanceof Fireball
                    && getBool (WCfgVal.GHAST))
                return isAbove (entity.getLocation ());
            else if (entity instanceof EnderDragon)
                return getBool (WCfgVal.DRAGONS);
            else if (entity instanceof Wither)
                return getBool (WCfgVal.WITHER);
        return getBool (WCfgVal.MAGICAL);
    }

    /**
     * Get whether the location is above the replace limit defined in this
     * world, if height replacement is enabled.
     * 
     * @param loc
     *            The location to test.
     * @return Whether the location is above the limit, or true if height
     *         replacement is not enabled.
     */
    public boolean isAbove (Location loc) {
        return !getBool (WCfgVal.REPLACE_ABOVE) || loc.getBlockY () >= replaceLimit.getValue ();
    }

    /**
     * Get whether the given block type is protected in this world.
     * 
     * @param block
     *            The block to test.
     * @return Whether the block's type is protected.
     */
    public boolean isProtected (Block block) {
        return protectList.getValue ().contains (new BlockId (block))
                || (block.getState () instanceof InventoryHolder && CreeperConfig.getBool (CfgVal.REPLACE_PROTECTED_CHESTS) && PluginHandler
                        .isProtected (block));
    }

    /**
     * Get whether any grief protection feature is enabled, thus requiring to
     * listen to the grief events.
     * 
     * @return Whether any grief protection is enabled.
     */
    public boolean hasGriefProtection () {
        return getBool (WCfgVal.BLOCK_LAVA) || getBool (WCfgVal.BLOCK_IGNITE) || getBool (WCfgVal.BLOCK_PVP) || getBool (WCfgVal.BLOCK_SPAWN_EGGS)
                || getBool (WCfgVal.BLOCK_TNT) || getBool (WCfgVal.WARN_IGNITE) || getBool (WCfgVal.WARN_LAVA) || getBool (WCfgVal.WARN_PVP)
                || getBool (WCfgVal.WARN_SPAWN_EGGS) || getBool (WCfgVal.WARN_TNT)
                || (!griefPlaceList.getValue ().isEmpty () && (getBool (WCfgVal.GRIEF_BLOCK_BLACKLIST) || getBool (WCfgVal.WARN_BLACKLIST)));
    }

    /**
     * Get whether the block is in the grief blackList (or not in the whitelist,
     * if the whitelist is used).
     * 
     * @param block
     * @return Whether the block is blacklisted.
     */
    public boolean isGriefBlackListed (Block block) {
        return griefPlaceList.getValue ().contains (new BlockId (block));
    }

    public World getWorld () {
        return Bukkit.getWorld (name);
    }

    /**
     * Set the boolean value associated with the key.
     * 
     * @param val
     *            The key
     * @param value
     *            The value.
     */
    public void setBool (WCfgVal val, boolean value) {
        ConfigValue<Boolean> v = booleans.get (val.getKey ());
        if (v == null)
            throw new NullPointerException ("Unknown config key path : " + val.getKey ());
        v.setValue (value);
    }

    /**
     * Set the boolean value associated with the key.
     * 
     * @param val
     *            The key
     * @param value
     *            The value.
     */
    public void setInt (WCfgVal val, int value) {
        switch (val)
        {
            case REPAIR_TIME:
                repairTime.setValue (value);
                break;
            case REPLACE_LIMIT:
                replaceLimit.setValue (value);
                break;
            default:
                CreeperLog.warning ("Wrong key type : " + val.toString ());
        }
    }

    protected void setList (WCfgVal val, HashSet<BlockId> value) {
        switch (val)
        {
            case REPLACE_BLACK_LIST:
                replaceBlackList.setValue (value);
                break;
            case REPLACE_WHITE_LIST:
                replaceWhiteList.setValue (value);
                break;
            case PROTECTED_LIST:
                protectList.setValue (value);
                break;
            case GRIEF_PLACE_LIST:
                griefPlaceList.setValue (value);
                break;
            default:
                CreeperLog.warning ("Wrong key type : " + val.toString ());
        }
    }

    public int getRepairTime () {
        return repairTime.getValue ();
    }

    public boolean isBlackListed (BlockId id) {
        if (getBool (WCfgVal.USE_REPLACE_WHITE_LIST))
            return !replaceWhiteList.getValue ().contains (id);
        else
            return replaceBlackList.getValue ().contains (id);
    }
}