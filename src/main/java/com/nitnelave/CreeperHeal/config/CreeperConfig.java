package com.nitnelave.CreeperHeal.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.nitnelave.CreeperHeal.CreeperHeal;
import com.nitnelave.CreeperHeal.block.BlockManager;
import com.nitnelave.CreeperHeal.utils.CreeperLog;
import com.nitnelave.CreeperHeal.utils.FileUtils;

/**
 * Configuration management class.
 * 
 * @author nitnelave
 * 
 */
public abstract class CreeperConfig {

    protected static final int CONFIG_VERSION = 8;

    private static int configVersion = CONFIG_VERSION;
    private static final File CONFIG_FILE = new File (CreeperHeal.getCHFolder () + "/config.yml"), ADVANCED_FILE = new File (CreeperHeal.getCHFolder ()
            + "/advanced.yml");

    protected static final Logger LOG = Logger.getLogger ("Minecraft");
    protected static final Map<String, WorldConfig> world_config = Collections.synchronizedMap (new HashMap<String, WorldConfig> ());
    protected static final Map<String, ConfigValue<Boolean>> booleans = new HashMap<String, ConfigValue<Boolean>> ();
    protected static final Map<String, ConfigValue<Integer>> integers = new HashMap<String, ConfigValue<Integer>> ();
    protected static final YamlConfiguration config = new YamlConfiguration (), advanced = new YamlConfiguration ();
    protected static ConfigValue<String> alias;
    public static boolean lockette;

    static
    {
        fillMaps ();
        load ();
    }

    /*
     * Put the config values in the maps, with the default values.
     */
    private static void fillMaps () {
        booleans.clear ();
        integers.clear ();
        for (CfgVal v : CfgVal.values ())
            if (v.getDefaultValue () instanceof Boolean)
                booleans.put (v.getKey (), new BooleanConfigValue (v, getFile (v)));
            else if (v.getDefaultValue () instanceof Integer)
                integers.put (v.getKey (), new IntegerConfigValue (v, getFile (v)));
            else if (v == CfgVal.ALIAS)
                alias = new StringConfigValue (v, getFile (v));
            else
                CreeperLog.warning ("Unknown config value : " + v.toString ());

    }

    private static YamlConfiguration getFile (CfgVal v) {
        return v.isAdvanced () ? advanced : config;
    }

    /**
     * Get the boolean value associated with the CfgVal.
     * 
     * @param val
     *            The config key.
     * @return The boolean value.
     */
    public static boolean getBool (CfgVal val) {
        ConfigValue<Boolean> v = booleans.get (val.getKey ());
        if (v == null)
            throw new NullPointerException ("Missing config value : " + val.getKey ());
        return v.getValue ();
    }

    /**
     * Get the int value associated with the CfgVal.
     * 
     * @param val
     *            The config key.
     * @return The int value.
     */
    public static int getInt (CfgVal val) {
        ConfigValue<Integer> v = integers.get (val.getKey ());
        if (v == null)
            throw new NullPointerException ("Missing config value : " + val.getKey ());
        return v.getValue ();
    }

    /**
     * Set the boolean value associated with the key.
     * 
     * @param val
     *            The key
     * @param value
     *            The value.
     */
    public static void setBool (CfgVal val, boolean value) {
        ConfigValue<Boolean> v = booleans.get (val.getKey ());
        if (v == null)
            throw new NullPointerException ("Unknown config key path : " + val.getKey ());
        v.setValue (value);
    }

    /**
     * Set the int value associated with the key.
     * 
     * @param val
     *            The key
     * @param value
     *            The value.
     */
    public static void setInt (CfgVal val, int value) {
        ConfigValue<Integer> v = integers.get (val.getKey ());
        if (v == null)
            throw new NullPointerException ("Unknown config key path : " + val.getKey ());
        v.setValue (value);
    }

    /*
     * Load a file, with all the checks that go with this.
     */
    private static void loadFile (YamlConfiguration conf, File f) {
        try
        {
            conf.load (f);
        } catch (FileNotFoundException e1)
        {
            FileUtils.copyJarConfig (f);
            try
            {
                conf.load (f);
            } catch (Exception e)
            {
                e.printStackTrace ();
                return;
            }
        } catch (IOException e1)
        {
            e1.printStackTrace ();
            return;
        } catch (InvalidConfigurationException e1)
        {
            CreeperLog.warning ("Invalid configuration : " + f.getName () + " is not a valid YAML file.");
            return;
        }
    }

    /**
     * Load/reload the main and advanced configuration.
     */
    public static void load () {
        if (!CONFIG_FILE.exists ())
        {
            FileUtils.copyJarConfig (CONFIG_FILE);
            FileUtils.copyJarConfig (ADVANCED_FILE);
        }
        else
        {
            loadFile (config, CONFIG_FILE);
            configVersion = config.getInt ("config-version", 4);
            if (configVersion < CONFIG_VERSION)
                ConfigUpdater.importFrom (configVersion);
            else
            {
                if (!ADVANCED_FILE.exists ())
                    FileUtils.copyJarConfig (ADVANCED_FILE);
                loadFile (advanced, ADVANCED_FILE);
                for (ConfigValue<Boolean> v : booleans.values ())
                    v.load ();
                for (ConfigValue<Integer> v : integers.values ())
                    v.load ();
                alias.load ();
            }
            config.set ("config-version", CONFIG_VERSION);
            configVersion = CONFIG_VERSION;
            write ();
        }

        loadWorlds ();
    }

    /*
     * Load every world detected.
     */
    private static void loadWorlds () {
        world_config.clear ();
        try
        {
            for (World w : Bukkit.getServer ().getWorlds ())
            {
                WorldConfig world = loadWorld (w.getName ());
                world_config.put (w.getName (), world);
            }
        } catch (Exception e)
        {
            CreeperLog.severe ("[CreeperHeal] Could not load world configurations");
            CreeperLog.severe (e.getMessage ());
        }
    }

    /*
     * Load a world, and return the loaded world.
     */
    private static WorldConfig loadWorld (String name) {
        WorldConfig w;
        if (configVersion < CONFIG_VERSION)
            w = WorldConfigImporter.importFrom (name, configVersion);
        else
        {
            w = new WorldConfig (name);
            w.load ();
        }
        if (w.isRepairTimed ())
            BlockManager.scheduleTimeRepairs ();
        if (w.hasGriefProtection ())
            CreeperHeal.registerGriefEvents ();
        return w;
    }

    /**
     * Save the main and advanced configuration to the file.
     */
    public static void write () {
        if (!CONFIG_FILE.exists () && !FileUtils.createNewFile (CONFIG_FILE) || !ADVANCED_FILE.exists () && !FileUtils.createNewFile (ADVANCED_FILE))
            return;

        for (ConfigValue<Boolean> v : booleans.values ())
            v.write ();

        for (ConfigValue<Integer> v : integers.values ())
            v.write ();

        alias.write ();
        config.set ("config-version", CONFIG_VERSION);

        try
        {
            for (WorldConfig w : world_config.values ())
                w.save ();
            config.save (CONFIG_FILE);
            advanced.save (ADVANCED_FILE);
        } catch (IOException e)
        {
            e.printStackTrace ();
        }
    }

    /**
     * Load a world configuration file into memory the first time, and return
     * the configuration
     * 
     * @param world
     *            The world to load.
     * @return The world configuration file.
     */
    public static WorldConfig getWorld (World world) {
        return getWorld (world.getName ());
    }

    public static WorldConfig getWorld (String name) {
        WorldConfig returnValue = world_config.get (name);
        if (returnValue == null)
            try
            {
                returnValue = loadWorld (name);
                world_config.put (name, returnValue);
            } catch (Exception e)
            {
                LOG.severe ("[CreeperHeal] Could not load configuration for world : " + name);
                e.printStackTrace ();
            }
        return returnValue;
    }

    protected static void setAlias (String cmdAlias) {
        alias.setValue (cmdAlias);
    }

    public static String getAlias () {
        return alias.getValue ();
    }

    public static Collection<WorldConfig> getWorlds () {
        return world_config.values ();
    }

    public static boolean isLightWeight () {
        return getBool (CfgVal.LIGHTWEIHGTMODE);
    }

}
