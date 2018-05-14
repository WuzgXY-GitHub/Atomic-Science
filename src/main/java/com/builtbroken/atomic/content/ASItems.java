package com.builtbroken.atomic.content;

import com.builtbroken.atomic.AtomicScience;
import com.builtbroken.atomic.config.ConfigRadiation;
import com.builtbroken.atomic.content.items.ItemFuelRod;
import com.builtbroken.atomic.content.items.ItemHazmat;
import com.builtbroken.atomic.content.items.ItemHeatProbe;
import com.builtbroken.atomic.content.items.ItemRadioactive;
import com.builtbroken.atomic.content.items.cell.ItemFluidCell;
import com.builtbroken.atomic.content.items.cell.ItemPoweredCell;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;

/**
 * @see <a href="https://github.com/BuiltBrokenModding/VoltzEngine/blob/development/license.md">License</a> for what you can and can't do with the code.
 * Created by Dark(DarkGuardsman, Robert) on 4/18/2018.
 */
public class ASItems
{
    //Armor
    public static ItemHazmat itemArmorHazmatHelm;
    public static ItemHazmat itemArmorHazmatChest;
    public static ItemHazmat itemArmorHazmatLegs;
    public static ItemHazmat itemArmorHazmatBoots;

    //Fluid Cells
    public static ItemFluidCell itemFluidCell; //Generic fluid cell (replaces water cell, toxic waste bucket)
    public static ItemPoweredCell itemPoweredCell; //Generic fluid cell (replaces Anti-matter and strange-matter cells)

    //Machine inputs
    public static Item itemFissileFuelCell;
    public static Item itemBreederFuelCell;

    //Crafting items
    public static Item itemEmptyCell; //crafting item (replaces empty cell)
    public static Item itemYellowCake;
    public static Item itemUranium235;
    public static Item itemUranium238;

    //Tools
    public static Item itemHeatProbe;

    public static final int TICKS_SECOND = 20;
    public static final int TICKS_MIN = TICKS_SECOND * 60;
    public static final int TICKS_HOUR = TICKS_MIN * 60;

    public static void register()
    {
        //Armor
        ItemHazmat.hazmatArmorMaterial = EnumHelper.addArmorMaterial("HAZMAT", 0, new int[]{0, 0, 0, 0}, 0);
        GameRegistry.registerItem(itemArmorHazmatHelm = new ItemHazmat(0, "mask"), "hazmat_helm");
        GameRegistry.registerItem(itemArmorHazmatChest = new ItemHazmat(1, "body"), "hazmat_chest");
        GameRegistry.registerItem(itemArmorHazmatLegs = new ItemHazmat(2, "leggings"), "hazmat_legs");
        GameRegistry.registerItem(itemArmorHazmatBoots = new ItemHazmat(3, "boots"), "hazmat_boots");

        //Cells
        GameRegistry.registerItem(itemFluidCell = new ItemFluidCell(FluidContainerRegistry.BUCKET_VOLUME), "fluid_cell");
        itemFluidCell.addSupportedFluid(FluidRegistry.WATER, AtomicScience.PREFIX + "cell_water", itemFluidCell.getUnlocalizedName() + ".water");
        itemFluidCell.addSupportedFluid(ASFluids.DEUTERIUM.fluid, AtomicScience.PREFIX + "cell_deuterium", itemFluidCell.getUnlocalizedName() + ".deuterium");

        GameRegistry.registerItem(itemPoweredCell = new ItemPoweredCell(), "powered_cell");
        itemPoweredCell.addSupportedFluid(ASFluids.ANTIMATTER.fluid, AtomicScience.PREFIX + "cell_antimatter", itemFluidCell.getUnlocalizedName() + ".antimatter");
        itemPoweredCell.addSupportedFluid(ASFluids.STRANGE_MATTER.fluid, AtomicScience.PREFIX + "cell_strange_matter", itemFluidCell.getUnlocalizedName() + ".strange_matter");

        //Machine inputs
        GameRegistry.registerItem(itemFissileFuelCell = new ItemFuelRod("cell.fuel.fissile", "cell_fissile_fuel",
                        5 * TICKS_HOUR, ConfigRadiation.RADIOACTIVE_MAT_VALUE_FUEL_ROD),
                "fissile_fuel_cell");
        GameRegistry.registerItem(itemBreederFuelCell = new ItemFuelRod("cell.fuel.breeder", "cell_breeder_fuel",
                        2 * TICKS_HOUR, ConfigRadiation.RADIOACTIVE_MAT_VALUE_BREEDER_ROD),
                "breeder_fuel_cell");

        //Crafting items
        GameRegistry.registerItem(itemEmptyCell = new Item()
                .setUnlocalizedName(AtomicScience.PREFIX + "cell.empty")
                .setTextureName(AtomicScience.PREFIX + "cell_empty")
                .setCreativeTab(AtomicScience.creativeTab), "cell_empty");

        GameRegistry.registerItem(itemYellowCake = new ItemRadioactive("cake.yellow", "yellow_cake", ConfigRadiation.RADIOACTIVE_MAT_VALUE_YELLOW_CAKE), "yellow_cake");
        GameRegistry.registerItem(itemUranium235 = new ItemRadioactive("uranium.235", "uranium", ConfigRadiation.RADIOACTIVE_MAT_VALUE_U235), "uranium_235");
        GameRegistry.registerItem(itemUranium238 = new ItemRadioactive("uranium.238", "uranium", ConfigRadiation.RADIOACTIVE_MAT_VALUE_U238), "uranium_238");

        //Tools
        GameRegistry.registerItem(itemHeatProbe = new ItemHeatProbe(), "heat_probe");
    }
}
