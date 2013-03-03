package com.nitnelave.CreeperHeal.block;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.nitnelave.CreeperHeal.CreeperHeal;
import com.nitnelave.CreeperHeal.config.CfgVal;
import com.nitnelave.CreeperHeal.config.CreeperConfig;
import com.nitnelave.CreeperHeal.config.WorldConfig;
import com.nitnelave.CreeperHeal.utils.CreeperLog;
import com.nitnelave.CreeperHeal.utils.NeighborFire;

/**
 * Manager to handle the burnt blocks.
 * 
 * @author nitnelave
 * 
 */
public abstract class BurntBlockManager {

    /*
     * The list of burnt blocks waiting to be replaced.
     */
    private static List<CreeperBurntBlock> burntList = Collections.synchronizedList (new LinkedList<CreeperBurntBlock> ());
    /*
     * If the plugin is not in lightweight mode, the list of recently burnt
     * blocks to prevent them from burning again soon.
     */
    private static Map<Location, Date> recentlyBurnt;
    /*
     * If the plugin is not in lightweight mode, the list of recently burnt
     * blocks for neighbor finding.
     */
    private static NeighborFire fireIndex;

    static
    {
        if (!CreeperConfig.isLightWeight ())
        {
            fireIndex = new NeighborFire ();
            recentlyBurnt = Collections.synchronizedMap (new HashMap<Location, Date> ());

            Bukkit.getScheduler ().runTaskTimerAsynchronously (CreeperHeal.getInstance (), new Runnable () {
                @Override
                public void run () {
                    cleanUp ();
                }
            }, 200, 2400);
        }

        if (Bukkit.getServer ().getScheduler ().scheduleSyncRepeatingTask (CreeperHeal.getInstance (), new Runnable () {
            @Override
            public void run () {
                replaceBurnt ();
            }
        }, 200, 20) == -1)
            CreeperLog.warning ("[CreeperHeal] Impossible to schedule the replace-burnt task. Burnt blocks replacement will not work");

    }

    /**
     * Force immediate replacement of all blocks burnt in the past few seconds,
     * or all of them.
     * 
     * @param worldConfig
     *            The world in which to replace the blocks.
     */
    public static void forceReplaceBurnt (WorldConfig worldConfig) {
        World world = Bukkit.getServer ().getWorld (worldConfig.getName ());

        synchronized (burntList)
        {
            Iterator<CreeperBurntBlock> iter = burntList.iterator ();
            while (iter.hasNext ())
            {
                CreeperBurntBlock cBlock = iter.next ();
                if (cBlock.getWorld () == world)
                {
                    cBlock.replace (true);
                    if (!CreeperConfig.isLightWeight ())
                    {
                        recentlyBurnt.put (cBlock.getLocation (),
                                new Date (System.currentTimeMillis () + 1000 * CreeperConfig.getInt (CfgVal.WAIT_BEFORE_BURN_AGAIN)));
                        fireIndex.removeElement (cBlock);
                    }
                    iter.remove ();
                }
            }
        }
    }

    /**
     * Replace the burnt blocks that have disappeared for long enough.
     */
    public static void replaceBurnt () {

        Date now = new Date ();
        synchronized (burntList)
        {
            Iterator<CreeperBurntBlock> iter = burntList.iterator ();
            while (iter.hasNext ())
            {
                CreeperBurntBlock cBlock = iter.next ();
                if (cBlock.checkReplace ())
                {
                    if (cBlock.wasReplaced ())
                    {
                        iter.remove ();
                        if (!CreeperConfig.isLightWeight ())
                        {
                            fireIndex.removeElement (cBlock);
                            recentlyBurnt.put (cBlock.getLocation (), new Date (now.getTime () + 1000 * CreeperConfig.getInt (CfgVal.WAIT_BEFORE_BURN_AGAIN)));
                        }
                    }
                }
                else
                    break;
            }
        }
    }

    /*
     * If the block relative to the face is dependent on the main block, record
     * it.
     */
    private static void recordAttachedBurntBlock (CreeperBlock block, BlockFace face) {
        Block block_up = block.getBlock ().getRelative (face);
        NeighborBlock neighbor = new NeighborBlock (block_up, face);
        if (neighbor.isNeighbor ())
            recordBurntBlock (new CreeperBurntBlock (new Date (new Date ().getTime () + 100), block_up.getState ()));
    }

    /**
     * Record a burnt block.
     * 
     * @param block
     *            The block to be recorded.
     */
    public static void recordBurntBlock (Block block) {
        if (block.getType () != Material.TNT)
        {
            CreeperBlock b = CreeperBlock.newBlock (block.getState ());
            for (BlockFace face : CreeperBlock.CARDINALS)
                recordAttachedBurntBlock (b, face);
            recordBurntBlock (new CreeperBurntBlock (new Date (), b));
        }
    }

    /**
     * Add a block to the list of burnt blocks to be replaced, and remove it
     * from the world.
     * 
     * @param block
     *            The block to add.
     */
    public static void recordBurntBlock (CreeperBurntBlock block) {
        if (block.getBlock () != null)
        {
            burntList.add (block);
            if (!(CreeperConfig.isLightWeight ()))
                fireIndex.addElement (block);
            block.remove ();
        }
    }

    /**
     * Get whether the location is close to a recently burnt block.
     * 
     * @param location
     *            The location to check.
     * @return Whether the location is close to a recently burnt block.
     */
    public static boolean isNextToFire (Location location) {
        return fireIndex.hasNeighbor (location);
    }

    /**
     * Get whether there is no recorded blocks to be replaced.
     * 
     * @return Whether there is no recorded blocks to be replaced.
     */
    public static boolean isIndexEmpty () {
        return fireIndex.isEmpty ();
    }

    /**
     * Get whether the block was recently burnt and should burn again.
     * 
     * @param block
     *            The block.
     * @return Whether the block was recently burnt.
     */
    public static boolean wasRecentlyBurnt (Block block) {
        Date d = recentlyBurnt.get (block.getLocation ());
        return d != null && d.after (new Date ());
    }

    /**
     * Clean up the block lists, remove the useless blocks. Do not use when in
     * light weight mode.
     */
    private static void cleanUp () {
        fireIndex.clean ();
        synchronized (recentlyBurnt)
        {
            Iterator<Location> iter = recentlyBurnt.keySet ().iterator ();
            Date now = new Date ();
            while (iter.hasNext ())
            {
                Location l = iter.next ();
                Date d = recentlyBurnt.get (l);
                if (d.before (now))
                    iter.remove ();
            }
        }

    }

}
