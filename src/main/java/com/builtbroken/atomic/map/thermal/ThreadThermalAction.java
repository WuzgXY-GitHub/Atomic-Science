package com.builtbroken.atomic.map.thermal;

import com.builtbroken.atomic.AtomicScience;
import com.builtbroken.atomic.api.thermal.IThermalSource;
import com.builtbroken.atomic.lib.thermal.HeatSpreadDirection;
import com.builtbroken.atomic.lib.thermal.ThermalHandler;
import com.builtbroken.atomic.map.MapHandler;
import com.builtbroken.atomic.map.data.DataChange;
import com.builtbroken.atomic.map.data.DataPos;
import com.builtbroken.atomic.map.data.ThreadDataChange;
import com.builtbroken.atomic.api.thermal.IThermalNode;
import com.builtbroken.atomic.map.thermal.node.ThermalNode;
import com.builtbroken.jlib.lang.StringHelpers;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.*;

/**
 * Handles updating the radiation map
 *
 *
 * Created by Dark(DarkGuardsman, Robert) on 4/28/2018.
 */
public class ThreadThermalAction extends ThreadDataChange
{
    public ThreadThermalAction()
    {
        super("ThreadThermalAction");
    }

    @Override
    protected boolean updateLocation(DataChange change)
    {
        //Get world
        final World world = DimensionManager.getWorld(change.dim());

        final int cx = change.xi();
        final int cy = change.yi();
        final int cz = change.zi();

        if (world != null && change.source instanceof IThermalSource)
        {
            //Collect data
            HashMap<DataPos, DataPos> collectedData = calculateHeatSpread(world, cx, cy, cz, change.value); //TODO store data into heat source

            //TODO convert to method or class
            ((WorldServer) world).addScheduledTask(() ->
            {
                if (change.source instanceof IThermalSource)
                {
                    final IThermalSource source = ((IThermalSource) change.source);
                    //Get data
                    final HashMap<BlockPos, IThermalNode> oldMap = source.getCurrentNodes();
                    final HashMap<BlockPos, IThermalNode> newMap = new HashMap();

                    //Remove old data from map
                    source.disconnectMapData();

                    //Add new data, recycle old nodes to reduce memory churn
                    for (Map.Entry<DataPos, DataPos> entry : collectedData.entrySet()) //TODO move this to source to give full control over data structure
                    {
                        final BlockPos pos = entry.getKey().disposeReturnBlockPos();
                        final int value = entry.getValue().x - entry.getValue().y;

                        if (oldMap != null && oldMap.containsKey(pos))
                        {
                            final IThermalNode node = oldMap.get(pos);
                            if (node != null)
                            {
                                //Update value
                                node.setHeatValue(value);

                                //Store in new map
                                newMap.put(pos, node);
                            }

                            //Remove from old map
                            oldMap.remove(pos);
                        }
                        else
                        {
                            newMap.put(pos, ThermalNode.get(source, value));
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


            return true;
        }
        return false;
    }

    /**
     * Calculates spread of heat from source.
     * <p>
     * Works by pathing all tiles and calculating heat movement towards current tile.
     * In other words: Pulls heat towards tile instead of pushing heat.
     * <p>
     * Heat is not consumed for movement as would be expected with real world movement. Instead
     * its assumed heat will constantly be generated. Thus migrating heat is not needed beyond
     * estimating how much heat would be moved.
     *
     * @param world  - map to pull data from
     * @param cx   - center of heat
     * @param cy   - center of heat
     * @param cz   - center of heat
     * @param heat - amount of heat to move
     * @return positions and changes (first pos is position, second is data (x -> heat, y -> heat used))
     */
    protected HashMap<DataPos, DataPos> calculateHeatSpread(final World world, final int cx, final int cy, final int cz, final int heat)
    {
        final DataPos center = DataPos.get(cx, cy, cz);
        //TODO consider splitting over several threads
        //TODO map fluid(water, air, lava, etc) pockets to allow convection currents
        //TODO use fluid pockets to equalize heat levels

        //Max range, hard coded until algs can be completed
        final int range = 50;

        //Track data, also used to prevent looping same tiles (first pos is location, second stores data)
        final HashMap<DataPos, DataPos> heatSpreadData = new HashMap();

        long time = System.nanoTime();
        if (heat > 6)
        {
            //Track tiles to path
            final Queue<DataPos> currentPathQueue = new LinkedList();
            final List<DataPos> nextPathQueue = new LinkedList();

            //Add center point
            heatSpreadData.put(DataPos.get(cx, cy, cz), DataPos.get(heat, 0, 0));

            //Add connected tiles
            for (EnumFacing direction : EnumFacing.VALUES)
            {
                DataPos pos = DataPos.get(cx, cy, cz, direction);
                currentPathQueue.add(pos);
                heatSpreadData.put(pos, DataPos.get(0, 0, 0));
            }

            //List of node to add to path queue after each loop
            final ArrayList<DataPos> tempHold = new ArrayList(6);

            //Breadth first pathfinder
            while (!currentPathQueue.isEmpty() || !nextPathQueue.isEmpty())
            {
                if(currentPathQueue.isEmpty())
                {
                    Collections.sort(nextPathQueue, Comparator.comparingDouble(pos -> pos.distance(center)));
                    currentPathQueue.addAll(nextPathQueue);

                    nextPathQueue.clear();
                }
                //Get next
                final DataPos currentPos = currentPathQueue.poll();

                //Calculate heat pushed from all sides & look for new tiles to path
                int heatAsPosition = 0;

                //Total heat transfer ratio, used to convert ratio to percentages when balancing heat flow
                double heatRateTotal = 0;

                //Find directions to spread heat and calculate max heat ratio
                Set<HeatSpreadDirection> spreadDirections = new HashSet();
                for (HeatSpreadDirection direction : HeatSpreadDirection.values())
                {
                    //Check range to prevent infinite spread
                    int x = currentPos.x + direction.offsetX;
                    int y = currentPos.y + direction.offsetY;
                    int z = currentPos.z + direction.offsetZ;
                    if (inRange(cx, cy, cz, x, y, z, range) && y >= 0 && y < 256) //TODO check delta temp, ignore high heat values to improve heat spread
                    {
                        //Add to set
                        spreadDirections.add(direction);

                        //Increase heat spread ratio
                        heatRateTotal += ThermalHandler.getHeatTransferRate(world, new BlockPos(x, y, z));
                    }
                }

                //Only loop values we had within range
                for (HeatSpreadDirection direction : spreadDirections)
                {
                    final DataPos pos = DataPos.get(
                            currentPos.x + direction.offsetX,
                            currentPos.y + direction.offsetY,
                            currentPos.z + direction.offsetZ);

                    //If we have no path position add to queue
                    if (!heatSpreadData.containsKey(pos))
                    {
                        //Only add if only sides, do not path corners. As it will result in low heat spread.
                        if (direction.ordinal() < 6)
                        {
                            tempHold.add(pos);
                        }
                    }
                    //If we have data do heat movement
                    else
                    {
                        //Get heat from direction
                        final int heatAtNext = heatSpreadData.get(pos).x;

                        //Calculate spread ratio from direction
                        double transferRate = ThermalHandler.getHeatTransferRate(world, new BlockPos(pos.x, pos.y, pos.z));   //TODO recycle block pos

                        //Convert ratio into percentage
                        double percentage = transferRate / heatRateTotal;

                        //Calculate heat to move to current position from direction
                        int heatMoved = getHeatToSpread(world, pos, currentPos, heatAtNext, percentage * direction.percentage, heatSpreadData);

                        //Update direction position with heat moved
                        heatSpreadData.get(pos).y += heatMoved;

                        //Increase heat at position
                        heatAsPosition += heatMoved;

                        //Recycle
                        pos.dispose();
                    }
                }

                //Only add positions from temp if there is heat to move from current
                if (heatAsPosition > 0)
                {
                    //Add to path queue
                    nextPathQueue.addAll(tempHold);

                    //Add to map so we don't path over again and have init data to grab
                    tempHold.forEach(e -> heatSpreadData.put(e, DataPos.get(0, 0, 0)));
                }
                else
                {
                    //Recycle objects
                    tempHold.forEach(e -> e.dispose());
                }
                //Clear for next run
                tempHold.clear();

                //Keep track of value
                heatSpreadData.get(currentPos).x = heatAsPosition;
            }
        }

        //Logging
        if (AtomicScience.runningAsDev)
        {
            time = System.nanoTime() - time;
            AtomicScience.logger.info(String.format("%s: Spread heat %s | %s tiles | %s %s %s | in %s",
                    name,
                    heat,
                    heatSpreadData.size(),
                    cx, cy, cz,
                    StringHelpers.formatNanoTime(time)));
        }

        center.dispose();
        return heatSpreadData;
    }


    /**
     * Called to get the heat to spread to the target tile
     *
     * @param world          - world
     * @param heatSource     - source of heat
     * @param heatTarget     - target of heat
     * @param heatToMove     - total heat to move
     * @param heatSpreadData - data of current heat movement
     * @return heat moved
     */
    protected int getHeatToSpread(World world, DataPos heatSource, DataPos heatTarget, final int heatToMove, final double percentage, HashMap<DataPos, DataPos> heatSpreadData)
    {
        if (world.isBlockLoaded(new BlockPos(heatTarget.x, heatTarget.y, heatTarget.z)))   //TODO recycle block pos
        {
            //Get heat actual movement (only move 25% of heat)
            return getHeatSpread(world, heatSource, heatTarget, (int) Math.floor(heatToMove * percentage), heatSpreadData);
        }
        return heatToMove;
    }

    /**
     * Checks how much heat should spread from one block to the next.
     * <p>
     * In theory each block should have a different spread value. As
     * heat does not transfer evenly between sources.
     * <p>
     * As well heat travels differently between different types of blocks.
     * Air blocks will use convection while solid blocks direct heat transfer.
     *
     * @param heatSource - source of heat
     * @param heatTarget - where to move heat
     * @param heat       - heat to transfer (some % of total heat), in kilo-joules
     * @return heat to actually transfer, in kilo-joules
     */
    public int getHeatSpread(World world, DataPos heatSource, DataPos heatTarget, int heat, HashMap<DataPos, DataPos> heatSpreadData)
    {
        double deltaTemp = getTemperature(world, heatSource, heatSpreadData); //We assume target is zero relative to source
        if (deltaTemp > 0)
        {
            double heatMovementRate1 = ThermalHandler.getHeatTransferRate(world, new BlockPos(heatTarget.x, heatTarget.y, heatTarget.z)) * deltaTemp;
            //double heatMovementRate2 = ThermalHandler.getHeatTransferRate(world, heatSource.x, heatSource.y, heatSource.z) * deltaTemp;
            //double heatMovementRate = (heatMovementRate1 + heatMovementRate2) / 2;
            return (int) Math.min(heatMovementRate1 * 20 * 60, heat);
        }
        return 0;
    }

    public double getTemperature(World world, DataPos pos, HashMap<DataPos, DataPos> heatSpreadData)
    {
        if (heatSpreadData.containsKey(pos))
        {
            //TODO recycle block pos
            return MapHandler.THERMAL_MAP.getTemperature(world, new BlockPos(pos.x, pos.y, pos.z), heatSpreadData.get(pos).x * 1000L); //kj -> j
        }
        return 0;
    }

}
