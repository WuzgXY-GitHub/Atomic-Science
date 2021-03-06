package com.builtbroken.atomic.map.exposure;

import com.builtbroken.atomic.AtomicScience;
import com.builtbroken.atomic.api.map.DataMapType;
import com.builtbroken.atomic.api.radiation.IRadiationNode;
import com.builtbroken.atomic.api.radiation.IRadiationSource;
import com.builtbroken.atomic.config.logic.ConfigRadiation;
import com.builtbroken.atomic.lib.radiation.RadiationHandler;
import com.builtbroken.atomic.map.data.DataChange;
import com.builtbroken.atomic.map.data.DataPos;
import com.builtbroken.atomic.map.data.ThreadDataChange;
import com.builtbroken.atomic.map.data.storage.DataChunk;
import com.builtbroken.atomic.map.exposure.node.RadSourceMap;
import com.builtbroken.atomic.map.exposure.node.RadiationNode;
import com.builtbroken.jlib.lang.StringHelpers;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 * Handles updating the radiation map
 *
 *
 * Created by Dark(DarkGuardsman, Robert) on 4/28/2018.
 */
public class ThreadRadExposure extends ThreadDataChange
{
    public ThreadRadExposure()
    {
        super("ThreadRadExposure");
    }

    @Override
    protected boolean updateLocation(DataChange change)
    {
        long time = System.nanoTime();

        final World world = DimensionManager.getWorld(change.dim());
        if (world != null) //TODO check if world is loaded
        {
            final HashMap<BlockPos, Integer> collectedData = updateValue(world, change.xi(), change.yi(), change.zi(), change.value);
            if (shouldRun)
            {
                //TODO convert to method or class
                ((WorldServer) world).addScheduledTask(() ->
                {
                    if (change.source instanceof IRadiationSource)
                    {
                        final IRadiationSource source = ((IRadiationSource) change.source);
                        //Get data
                        final HashMap<BlockPos, IRadiationNode> oldMap = source.getCurrentNodes();
                        final HashMap<BlockPos, IRadiationNode> newMap = new HashMap();

                        //Remove old data from map
                        source.disconnectMapData();

                        //Add new data, recycle old nodes to reduce memory churn
                        for (Map.Entry<BlockPos, Integer> entry : collectedData.entrySet()) //TODO move this to source to give full control over data structure
                        {
                            final int value = entry.getValue();
                            final BlockPos pos = entry.getKey();

                            if (oldMap != null && oldMap.containsKey(pos))
                            {
                                final IRadiationNode node = oldMap.get(pos);
                                if (node != null)
                                {
                                    //Update value
                                    node.setRadiationValue(value);

                                    //Store in new map
                                    newMap.put(pos, node);
                                }

                                //Remove from old map
                                oldMap.remove(pos);
                            }
                            else
                            {
                                newMap.put(pos, RadiationNode.get(source, value));
                            }
                        }

                        //Clear old data
                        source.disconnectMapData();
                        source.clearMapData();

                        //Set new data
                        source.setCurrentNodes(newMap);

                        //Tell the source to connect to the map
                        source.connectMapData();

                        //Trigger source update
                        source.initMapData();
                    }
                });
            }

            if (AtomicScience.runningAsDev)
            {
                time = System.nanoTime() - time;
                AtomicScience.logger.info(String.format("ThreadRadExposure: %sx %sy %sz | %sn | took %s",
                        change.xi(), change.yi(), change.zi(),
                        change.value,
                        StringHelpers.formatNanoTime(time)
                ));
            }
            return true;
        }
        return false;
    }

    /**
     * Removes the old value from the map
     *
     * @param value - value to remove
     * @param cx    - change location
     * @param cy    - change location
     * @param cz    - change location
     * @return map of position to edit
     */
    protected HashMap<BlockPos, Integer> updateValue(World world, int cx, int cy, int cz, int value)
    {
        if (value > 0)
        {
            //Track data, also used to prevent editing same tiles (first pos is location, second stores data)
            final HashMap<BlockPos, Integer> radiationData = new HashMap();

            final int rad = RadiationHandler.getRadFromMaterial(value);
            final int edit_range = Math.min(ConfigRadiation.MAX_UPDATE_RANGE, (int) Math.floor(RadiationHandler.getDecayRange(rad)));

            if (edit_range > 1)
            {
                //How many steps to go per rotation
                final int steps = (int) Math.ceil(Math.PI / Math.atan(1.0D / edit_range));

                for (int phi_n = 0; phi_n < 2 * steps; phi_n++)
                {
                    for (int theta_n = 0; theta_n < 2 * steps; theta_n++)
                    {
                        //Get angles for rotation steps
                        double yaw = Math.PI * 2 / steps * phi_n;
                        double pitch = Math.PI * 2 / steps * theta_n;

                        //Figure out vector to move for trace (cut in half to improve trace skipping blocks)
                        double dx = sin(pitch) * cos(yaw) * 0.5;
                        double dy = cos(pitch) * 0.5;
                        double dz = sin(pitch) * sin(yaw) * 0.5;

                        path(radiationData, world,
                                cx + 0.5, cy + 0.5, cz + 0.5,
                                dx, dy, dz,
                                rad, edit_range);
                    }
                }
            }
            return radiationData;
        }
        return new HashMap();
    }

    protected void path(HashMap<BlockPos, Integer> radiationData, World world,
                        final double center_x, final double center_y, final double center_z,
                        final double dx, final double dy, final double dz,
                        double power, double edit_range)
    {
        final int cx = (int) Math.floor(center_x);
        final int cy = (int) Math.floor(center_y);
        final int cz = (int) Math.floor(center_z);

        //Position
        double x = center_x;
        double y = center_y;
        double z = center_z;

        double distanceSQ = 1;
        double radDistance = 1;

        DataPos prevPos = null;

        do
        {
            if (!shouldRun)
            {
                return;
            }

            //Convert double position to int position
            int xi = (int) Math.floor(x);
            int yi = (int) Math.floor(y);
            int zi = (int) Math.floor(z);

            if(y < 0 || y > world.getHeight()) //TODO hook into config to allow increase for cubic chunk maps
            {
                return;
            }

            //Ignore center block
            if (!(xi == cx && yi == cy && zi == cz))
            {

                //Get distance to center of block from center
                double distanceX = center_x - (xi + 0.5);
                double distanceY = center_y - (yi + 0.5);
                double distanceZ = center_z - (zi + 0.5);
                distanceSQ = distanceX * distanceX + distanceZ * distanceZ + distanceY * distanceY;

                DataPos pos = DataPos.get(xi, yi, zi);

                //Only do action one time per block (not a perfect solution, but solves double hit on the same block in the same line)
                if (prevPos != pos)
                {
                    //Reduce radiation for distance
                    power = RadiationHandler.getRadForDistance(power, radDistance, distanceSQ);

                    //Reduce radiation
                    power = RadiationHandler.reduceRadiationForBlock(world, xi, yi, zi, power);


                    //Store change
                    int change = (int) Math.floor(power);
                    radiationData.put(pos.disposeReturnBlockPos(), change);

                    //Note previous block
                    prevPos = pos;

                    //Track last distance of radiation, as power is now measured from that position
                    radDistance = distanceSQ;
                }
                else
                {
                    pos.dispose();
                }
            }

            //Move forward
            x += dx;
            y += dy;
            z += dz;
        }
        while ((distanceSQ <= edit_range * edit_range) && power > 1);
    }

    /**
     * Called to scan a chunk to add remove calls
     *
     * @param chunk
     */
    protected void queueRemove(DataChunk chunk)
    {
        chunk.forEachValue((dim, x, y, z, value) -> ThreadRadExposure.this.queuePosition(DataChange.get(new RadSourceMap(dim, new BlockPos(x, y, z), value), 0)), DataMapType.RAD_MATERIAL); //TODO see if needed
    }

    /**
     * Called to scan a chunk to add addition calls
     *
     * @param chunk
     */
    protected void queueAddition(DataChunk chunk)
    {
        chunk.forEachValue((dim, x, y, z, value) -> queuePosition(DataChange.get(new RadSourceMap(dim, new BlockPos(x, y, z), value), value)), DataMapType.RAD_MATERIAL);
    }
}
