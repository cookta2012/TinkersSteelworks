package tsteelworks.blocks.logic;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSourceImpl;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.dispenser.IRegistry;
import net.minecraft.dispenser.RegistryDefaulted;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet132TileEntityData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;
import tconstruct.library.crafting.FluidType;
import tconstruct.library.util.CoordTuple;
import tconstruct.library.util.IActiveLogic;
import tconstruct.library.util.IFacingLogic;
import tconstruct.library.util.IMasterLogic;
import tconstruct.library.util.IServantLogic;
import tsteelworks.TSteelworks;
import tsteelworks.common.TSContent;
import tsteelworks.inventory.HighOvenContainer;
import tsteelworks.lib.ConfigCore;
import tsteelworks.lib.blocks.TSInventoryLogic;
import tsteelworks.lib.crafting.AdvancedSmelting;
import cpw.mods.fml.common.registry.GameRegistry;

public class HighOvenLogic extends TSInventoryLogic implements IActiveLogic, IFacingLogic, IFluidTank, IMasterLogic
{
    public final IRegistry dispenseBehaviorRegistry = new RegistryDefaulted(new BehaviorDefaultDispenseItem());
    public ArrayList<FluidStack> moltenMetal = new ArrayList<FluidStack>();
    boolean structureHasBottom;
    boolean structureHasTop;
    boolean redstoneActivated;
    boolean needsUpdate;
    boolean isMeltingItems;
    public boolean validStructure;
    byte direction;
    public CoordTuple outputDuct;
    public CoordTuple centerPos;
    int internalTemp;
    int fuelHeatRate;
    int internalCoolDownRate;
    int tick;
    int maxLiquid;
    int currentLiquid;
    int numBricks;
    public int layers;
    public int fuelBurnTime;
    public int[] activeTemps;
    public int[] meltingTemps;
    Random rand = new Random();

    /**
     * Initialization
     */
    public HighOvenLogic()
    {
        super(4);
        redstoneActivated = false;
        fuelHeatRate = 3;
        internalCoolDownRate = 10;
        activeTemps = meltingTemps = new int[0];
    }

    /* ==================== Layers ==================== */
    
    /**
     * Adjust Layers for inventory containment
     * 
     * @param lay
     *            Layer
     * @param forceAdjust
     */
    void adjustLayers (int lay, boolean forceAdjust)
    {
        if ((lay != layers) || forceAdjust)
        {
            needsUpdate = true;
            layers = lay;
            maxLiquid = 20000 * lay;
            final int[] tempActive = activeTemps;
            activeTemps = new int[4 + lay];
            final int activeLength = tempActive.length > activeTemps.length ? activeTemps.length : tempActive.length;
            System.arraycopy(tempActive, 0, activeTemps, 0, activeLength);
            final int[] tempMelting = meltingTemps;
            meltingTemps = new int[4 + lay];
            final int meltingLength = tempMelting.length > meltingTemps.length ? meltingTemps.length : tempMelting.length;
            System.arraycopy(tempMelting, 0, meltingTemps, 0, meltingLength);
            final ItemStack[] tempInv = inventory;
            inventory = new ItemStack[4 + lay];
            final int invLength = tempInv.length > inventory.length ? inventory.length : tempInv.length;
            System.arraycopy(tempInv, 0, inventory, 0, invLength);
            if ((activeTemps.length > 0) && (activeTemps.length > tempActive.length))
            {
                for (int i = tempActive.length; i < activeTemps.length; i++)
                {
                    if (!isSmeltingSlot(i))
                        continue;
                    activeTemps[i] = 20;
                    meltingTemps[i] = 20;
                }
            }
            if (tempInv.length > inventory.length)
            {
                for (int i = inventory.length; i < tempInv.length; i++)
                {
                    ItemStack stack = tempInv[i];
                    if (stack != null)
                    {
                        float jumpX = rand.nextFloat() * 0.8F + 0.1F;
                        float jumpY = rand.nextFloat() * 0.8F + 0.1F;
                        float jumpZ = rand.nextFloat() * 0.8F + 0.1F;
                        int offsetX = 0;
                        int offsetZ = 0;
                        switch (getRenderDirection())
                        {
                        case 2: // +z
                            offsetZ = -1;
                            break;
                        case 3: // -z
                            offsetZ = 1;
                            break;
                        case 4: // +x
                            offsetX = -1;
                            break;
                        case 5: // -x
                            offsetX = 1;
                            break;
                        }

                        while (stack.stackSize > 0)
                        {
                            int itemSize = rand.nextInt(21) + 10;
                            if (itemSize > stack.stackSize)
                                itemSize = stack.stackSize;
                            stack.stackSize -= itemSize;
                            EntityItem entityitem = new EntityItem(worldObj, (double) ((float) xCoord + jumpX + offsetX), (double) ((float) yCoord + jumpY),
                                    (double) ((float) zCoord + jumpZ + offsetZ), new ItemStack(stack.itemID, itemSize, stack.getItemDamage()));

                            if (stack.hasTagCompound())
                                entityitem.getEntityItem().setTagCompound((NBTTagCompound) stack.getTagCompound().copy());
                            float offset = 0.05F;
                            entityitem.motionX = (double) ((float) rand.nextGaussian() * offset);
                            entityitem.motionY = (double) ((float) rand.nextGaussian() * offset + 0.2F);
                            entityitem.motionZ = (double) ((float) rand.nextGaussian() * offset);
                            worldObj.spawnEntityInWorld(entityitem);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean canUpdate ()
    {
        return true;
    }

    /* ==================== Misc ==================== */

    @Override
    public String getDefaultName ()
    {
        return "crafters.HighOven";
    }

    @Override
    public Container getGuiContainer (InventoryPlayer inventoryplayer, World world, int x, int y, int z)
    {
        return new HighOvenContainer(inventoryplayer, this);
    }

    @Override
    public boolean isUseableByPlayer (EntityPlayer entityplayer)
    {
        if (worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) != this)
            return false;
        else
            return entityplayer.getDistance(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64D;
    }
    
    /* ==================== Facing Logic ==================== */
    
    @Override
    public byte getRenderDirection ()
    {
        return direction;
    }

    @Override
    public ForgeDirection getForgeDirection ()
    {
        return ForgeDirection.VALID_DIRECTIONS[direction];
    }

    @Override
    public void setDirection (int side)
    {
    }

    @Override
    public void setDirection (float yaw, float pitch, EntityLivingBase player)
    {
        final int facing = MathHelper.floor_double((yaw / 360) + 0.5D) & 3;
        switch (facing)
        {
        case 0: direction = 2; break;
        case 1: direction = 5; break;
        case 2: direction = 3; break;
        case 3: direction = 4; break;
        }
    }

    /* ==================== Active Logic ==================== */
    
    @Override
    public boolean getActive ()
    {
        return validStructure && isBurning();
    }

    @Override
    public void setActive (boolean flag)
    {
        needsUpdate = true;
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /* ==================== Redstone Logic ==================== */
    
    /**
     * Get the current state of redstone-connected power
     * 
     * @return Redstone powered state
     */
    public boolean getRedstoneActive ()
    {
        return redstoneActivated;
    }

    /**
     * Set the redstone powered state
     * 
     * @param flag
     *          true: powered / false: not powered
     */
    public void setRedstoneActive (boolean flag)
    {
        redstoneActivated = flag;
    }
    
    /* ==================== Smelting Logic ==================== */

    /**
     * Update Tile Entity
     */
    @Override
    public void updateEntity ()
    {
        tick++;
        if ((tick % 4) == 0)
            heatItems();
        if ((tick % 20) == 0)
        {
            if (!validStructure)
                checkValidPlacement();
            if (isBurning())
            {
                fuelBurnTime -= 3;
                if (internalTemp > 3000)
                    internalTemp = 3000;
                if (internalTemp < 3000)
                    internalTemp += fuelHeatRate;

            }
            else
            {
                if (internalTemp > 20)
                    internalTemp -= internalCoolDownRate;
                if (internalTemp < 20)
                    internalTemp = 20;
            }
            if (validStructure && (fuelBurnTime <= 0))
                updateFuelGague();
            if (needsUpdate)
            {
                needsUpdate = false;
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
        if ((tick % 40) == 0)
            heatFluids();
        if (tick == 60)
            tick = 0;
    }

    /**
     * Process item heating and liquifying
     */
    void heatItems ()
    {
        if (internalTemp > 20)
        {
            boolean hasUse = false;
            for (int i = 4; i < (layers + 4); i += 1)
                // If an item is present and meltable
                if (isStackInSlot(i) && (meltingTemps[i] > 20))
                {
                    hasUse = true;
                    // Increase temp if its temp is lower than the High Oven's internal 
                    // temp and hasn't reached melting point
                    if ((activeTemps[i] < internalTemp) && (activeTemps[i] < meltingTemps[i]))
                        activeTemps[i] += (internalTemp > 250) ? (internalTemp / 250) : 1;
                    // Decrease temp if its temp is higher than the High Oven's internal
                    // temp and the High Oven's internal temp is lower than the melting point
                    else if ((activeTemps[i] > internalTemp) && (internalTemp < meltingTemps[i]))
                        activeTemps[i] -= 1;
                    // Liquify metals if the temp has reached the melting point
                    else if (activeTemps[i] >= meltingTemps[i])
                        if (!worldObj.isRemote)
                        {
                            final FluidStack result = getNormalResultFor(inventory[i]);
                            final ItemStack resultitemstack = getSolidMixedResultFor(result);
                            if (resultitemstack != null)
                            {
                                if (resultitemstack != null)
                                    meltItemsSolidOutput(i, resultitemstack, true);
                            }
                            else if (result != null)
                            {
                                final FluidStack resultEx = getLiquidMixedResultFor(result);
                                if (resultEx != null)
                                    meltItemsLiquidOutput(i, resultEx, true);
                                else
                                    meltItemsLiquidOutput(i, result, false);
                            }

                        }
                }
                else
                    activeTemps[i] = 20;
            isMeltingItems = hasUse;
        }
    }
    
    void heatFluids()
    {
        if (internalTemp < 1300 || moltenMetal.size() < 1) return;
        // Let's make steam!
        if (getFluid().getFluid() == FluidRegistry.WATER || getFluid().getFluid() == FluidRegistry.getFluid("Steam"))
        {
            int amount = 0;
            for (FluidStack fluid : moltenMetal)
            {
                if (fluid.getFluid() == FluidRegistry.WATER)
                    amount += fluid.amount;
            }
            if (amount > 0)
            {
                FluidStack steam = new FluidStack(TSContent.steamFluid.getID(), amount);
                if (this.addFluidToTank(steam, false))
                {
                    moltenMetal.remove(0);
                    currentLiquid -= amount;
                }
            }
        }
    }
    
    void meltItemsLiquidOutput (int slot, FluidStack fluid, Boolean doMix)
    {
        if (addFluidToTank(fluid, false))
        {
            if (itemIsOre(inventory[slot]))
                outputTE3Slag();

            if (inventory[slot].stackSize >= 2)
                inventory[slot].stackSize--;
            else
                inventory[slot] = null;
            activeTemps[slot] = 20;
            if (doMix)
                removeMixItems();
            onInventoryChanged();
        }
    }

    void meltItemsSolidOutput (int slot, ItemStack stack, Boolean doMix)
    {
        if (inventory[slot].stackSize >= 2)
            inventory[slot].stackSize--;
        else
            inventory[slot] = null;
        activeTemps[slot] = 20;
        if (doMix)
            removeMixItems();
        addSolidItem(stack);
        onInventoryChanged();
    }

    public boolean itemIsOre (ItemStack stack)
    {
        return (stack.getDisplayName().endsWith("Ore"));
    }
    
    /**
     * Get molten result for given item
     * 
     * @param stack
     *            ItemStack
     * @return FluidStack
     */
    public FluidStack getNormalResultFor (ItemStack stack)
    {
        return AdvancedSmelting.instance.getMeltingResult(stack);
    }

    public FluidStack getLiquidMixedResultFor (FluidStack stack)
    {
        final FluidType resultType = FluidType.getFluidType(stack.getFluid());
        final FluidType mixResult = AdvancedSmelting.instance.getMixSmeltingFluidResult(resultType, inventory[0], inventory[1], inventory[2]);
        if (mixResult != null)
            return new FluidStack(mixResult.fluid, stack.amount);
        return null;
    }

    public ItemStack getSolidMixedResultFor (FluidStack stack)
    {
        final FluidType resultType = FluidType.getFluidType(stack.getFluid());
        final ItemStack mixResult = AdvancedSmelting.instance.getMixSmeltingSolidResult(resultType, inventory[0], inventory[1], inventory[2]);
        if (mixResult != null)
            return new ItemStack(mixResult.itemID, mixResult.stackSize, mixResult.getItemDamage());
        return null;
    }

    private void outputTE3Slag ()
    {
        if (TSteelworks.thermalExpansionAvailable && ConfigCore.enableTE3SlagOutput)
            if (new Random().nextInt(100) <= 15)
                    addSolidItem(GameRegistry.findItemStack("ThermalExpansion", "slag", 1));
    }
    
    /**
     * Remove additive materials by preset vs random chance and amount
     */
    private void removeMixItems ()
    {
        for (int i = 0; i < 3; i++)
            if (inventory[i] != null)
            {
                final int consumeChance = AdvancedSmelting.instance.getMixItemConsumeChance(inventory[i]);
                final int consumeAmount = AdvancedSmelting.instance.getMixItemConsumeAmount(inventory[i]);
                if (new Random().nextInt(100) <= consumeChance)
                    if (inventory[i].stackSize >= consumeAmount)
                        inventory[i].stackSize -= consumeAmount;
                if ((inventory[i] != null) && (inventory[i].stackSize == 0))
                    inventory[i] = null;
            }
    }

    /* ==================== Temperatures ==================== */

    /**
     * Get internal temperature for smelting
     * 
     * @return internal temperature value
     */
    public int getInternalTemperature () { return internalTemp; }

    /**
     * Get current temperature for slot
     * 
     * @param slot
     * @return
     */
    public int getTempForSlot (int slot) { return (isSmeltingSlot(slot)) ? activeTemps[slot] : 20; }

    /**
     * Get melting point for item in slot
     * 
     * @param slot
     * @return
     */
    public int getMeltingPointForSlot (int slot) { return (isSmeltingSlot(slot)) ? meltingTemps[slot] : 20; }

    /**
     * Update melting temperatures for items
     */
    void updateTemperatures ()
    {
        isMeltingItems = true;
        for (int i = 4; i < (layers + 4); i += 1)
            meltingTemps[i] = AdvancedSmelting.instance.getLiquifyTemperature(inventory[i]);
    }

    /* ==================== Fuel Handling ==================== */
    
    public boolean isBurning () { return fuelBurnTime > 0; }

    public boolean hasFuel () { return getFuelBurnTime(inventory[3]) > 0; }

    /**
     * Get fuel gauge scaled for display
     * 
     * @param scale
     * @return scaled value
     */
    public int getScaledFuelGague (int scale)
    {
        int ret = fuelBurnTime / scale;
        if (ret < 1) ret = 1;
        return ret;
    }

    /**
     * Update fuel gauge (keeping typo just cuz)
     */
    void updateFuelGague ()
    {
        if (isBurning() || !redstoneActivated) return;
        if (inventory[3] == null)
        {
            fuelBurnTime = 0;
            return;
        }
        if (getFuelBurnTime(inventory[3]) > 0)
        {
            needsUpdate = true;
            fuelBurnTime = getFuelBurnTime(inventory[3]);
            fuelHeatRate = getFuelHeatRate(inventory[3]);
            inventory[3].stackSize--;
            if (inventory[3].stackSize <= 0)
                inventory[3] = null;
        }
    }
    
    /**
     * Update fuel gauge display
     */
    public void updateFuelDisplay ()
    {
        if (getFuelBurnTime(inventory[3]) > 0)
        {
            needsUpdate = true;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public static int getFuelBurnTime (ItemStack stack)
    {
        if (stack == null) return 0;
        return TSteelworks.fuelHandler.getHighOvenFuelBurnTime(stack);
    }

    /**
     * Get the rate of heat increase by given item
     * 
     * @param stack
     * @return
     */
    public static int getFuelHeatRate (ItemStack stack)
    {
        if (stack == null) return 0;
        return TSteelworks.fuelHandler.getHighOvenFuelHeatRate(stack);
    }
    
    /* ==================== Inventory ==================== */

    /**
     * Determine is slot is valid for 'ore' processing
     * 
     * @param slot
     * @return True if slot is valid
     */
    public boolean isSmeltingSlot (int slot) { return (slot > 3); }

    /**
     * Get (& Set) Inventory slot stack limit Returns the maximum stack size for
     * a inventory slot.
     */
    @Override
    public int getInventoryStackLimit () { return 64; }

    /**
     * Called when an the contents of Inventory change
     */
    @Override
    public void onInventoryChanged ()
    {
        updateTemperatures();
        updateEntity();
        super.onInventoryChanged();
        needsUpdate = true;
    }

    @Override
    public int getSizeInventory () { return inventory.length; }

    @Override
    public ItemStack getStackInSlot (int slot) { return inventory[slot]; }

    @Override
    public ItemStack decrStackSize (int slot, int quantity)
    {
        if (inventory[slot] != null)
        {
            if (inventory[slot].stackSize <= quantity)
            {
                final ItemStack stack = inventory[slot];
                inventory[slot] = null;
                return stack;
            }
            final ItemStack split = inventory[slot].splitStack(quantity);
            if (inventory[slot].stackSize == 0)
                inventory[slot] = null;
            return split;
        }
        else
            return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing (int slot) { return null; }

    @Override
    public void setInventorySlotContents (int slot, ItemStack itemstack)
    {
        inventory[slot] = itemstack;
        if ((itemstack != null) && (itemstack.stackSize > getInventoryStackLimit()))
            itemstack.stackSize = getInventoryStackLimit();
    }

    @Override
    public String getInvName () { return isInvNameLocalized() ? invName : getDefaultName(); }

    @Override
    public boolean isInvNameLocalized () { return (invName != null) && (invName.length() > 0); }

    @Override
    public boolean isItemValidForSlot (int slot, ItemStack itemstack)
    {
        if (slot < getSizeInventory())
            if ((inventory[slot] == null) || ((itemstack.stackSize + inventory[slot].stackSize) <= getInventoryStackLimit()))
                return true;
        return false;
    }

    /* ==================== Multiblock ==================== */

    /**
     * Called when servants change their state
     * 
     * @param servant
     *            Servant Tile Entity
     * @param x
     *            Servant X
     * @param y
     *            Servant Y
     * @param z
     *            Servant Z
     */
    @Override
    public void notifyChange (IServantLogic servant, int x, int y, int z) { checkValidPlacement(); }

    /**
     * Check placement validation by facing direction
     */
    public void checkValidPlacement ()
    {
        switch (getRenderDirection())
        {
        case 2: // +z
            alignInitialPlacement(xCoord, yCoord, zCoord + 1);
            break;
        case 3: // -z
            alignInitialPlacement(xCoord, yCoord, zCoord - 1);
            break;
        case 4: // +x
            alignInitialPlacement(xCoord + 1, yCoord, zCoord);
            break;
        case 5: // -x
            alignInitialPlacement(xCoord - 1, yCoord, zCoord);
            break;
        }
    }

    /**
     * Begin structure alignment
     * 
     * @param x
     *            coordinate from controller
     * @param y
     *            coordinate from controller
     * @param z
     *            coordinate from controller
     */
    public void alignInitialPlacement (int x, int y, int z)
    {
        checkValidStructure(x, y, z);
    }

    /**
     * Determine if structure is valid
     * 
     * @param x
     *            coordinate from controller
     * @param y
     *            coordinate from controller
     * @param z
     *            coordinate from controller
     */
    public void checkValidStructure (int x, int y, int z)
    {
        int checkLayers = 0;
        if (checkSameLevel(x, y, z))
        {
            checkLayers++;
            checkLayers += recurseStructureUp(x, y + 1, z, 0);
            checkLayers += recurseStructureDown(x, y - 1, z, 0);
        }
        if ((structureHasTop != structureHasBottom != validStructure) || (checkLayers != layers))
            if (structureHasBottom && structureHasTop)
            {
                adjustLayers(checkLayers, false);
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                validStructure = true;
            }
            else
            {
                internalTemp = 20;
                validStructure = false;
            }
    }

    /**
     * Scan the controller layer of the structure for valid components
     * 
     * @param x
     *            coordinate from center
     * @param y
     *            coordinate from center
     * @param z
     *            coordinate from center
     * @param count
     *            current amount of blocks
     * @return block count
     */
    public boolean checkSameLevel (int x, int y, int z)
    {
        numBricks = 0;
        Block block;
        // Check inside
        for (int xPos = x; xPos <= x; xPos++)
            for (int zPos = z; zPos <= z; zPos++)
            {
                block = Block.blocksList[worldObj.getBlockId(xPos, y, zPos)];
                if ((block != null) && !block.isAirBlock(worldObj, xPos, y, zPos))
                    return false;
            }
        // Check outer layer
        // Scans in a swastica-like pattern
        for (int xPos = x - 1; xPos <= (x + 1); xPos++)
        {
            numBricks += checkBricks(xPos, y, z - 1);
            numBricks += checkBricks(xPos, y, z + 1);
        }
        for (int zPos = z; zPos <= z; zPos++)
        {
            numBricks += checkBricks(x - 1, y, zPos);
            numBricks += checkBricks(x + 1, y, zPos);
        }
        if ((numBricks == 8))
            return true;
        else
            return false;
    }

    /**
     * Scan up the structure for valid components
     * 
     * @param x
     *            coordinate from center
     * @param y
     *            coordinate from center
     * @param z
     *            coordinate from center
     * @param count
     *            current amount of blocks
     * @return block count
     */
    public int recurseStructureUp (int x, int y, int z, int count)
    {
        numBricks = 0;
        // Check inside
        for (int xPos = x; xPos <= x; xPos++)
            for (int zPos = z; zPos <= z; zPos++)
            {
                final int blockID = worldObj.getBlockId(xPos, y, zPos);
                final Block block = Block.blocksList[worldObj.getBlockId(xPos, y, zPos)];
                if ((block != null) && !block.isAirBlock(worldObj, xPos, y, zPos))
                    if (validBlockID(blockID))
                        return validateTop(x, y, z, count);
                    else
                        return count;
            }
        // Check outer layer
        for (int xPos = x - 1; xPos <= (x + 1); xPos++)
        {
            numBricks += checkBricks(xPos, y, z - 1);
            numBricks += checkBricks(xPos, y, z + 1);
        }
        for (int zPos = z; zPos <= z; zPos++)
        {
            numBricks += checkBricks(x - 1, y, zPos);
            numBricks += checkBricks(x + 1, y, zPos);
        }
        if (numBricks != 8)
            return count;
        count++;
        return recurseStructureUp(x, y + 1, z, count);
    }

    /**
     * Scan down the structure for valid components
     * 
     * @param x
     *            coordinate from center
     * @param y
     *            coordinate from center
     * @param z
     *            coordinate from center
     * @param count
     *            current amount of blocks
     * @return block count
     */
    public int recurseStructureDown (int x, int y, int z, int count)
    {
        numBricks = 0;
        // Check inside
        for (int xPos = x; xPos <= x; xPos++)
            for (int zPos = z; zPos <= z; zPos++)
            {
                final int blockID = worldObj.getBlockId(xPos, y, zPos);
                final Block block = Block.blocksList[blockID];
                if ((block != null) && !block.isAirBlock(worldObj, xPos, y, zPos))
                    if (validBlockID(blockID))
                        return validateBottom(x, y, z, count);
                    else
                        return count;
            }
        // Check outer layer X
        for (int xPos = x - 1; xPos <= (x + 1); xPos++)
        {
            numBricks += checkBricks(xPos, y, z - 1);
            numBricks += checkBricks(xPos, y, z + 1);
        }
        // Check outer layer Z
        for (int zPos = z; zPos <= z; zPos++)
        {
            numBricks += checkBricks(x - 1, y, zPos);
            numBricks += checkBricks(x + 1, y, zPos);
        }
        if (numBricks != 8)
            return count;
        count++;
        return recurseStructureDown(x, y - 1, z, count);
    }

    /**
     * Determine if layer is a valid top layer
     * 
     * @param x
     *            coordinate from center
     * @param y
     *            coordinate from center
     * @param z
     *            coordinate from center
     * @param count
     *            current amount of blocks
     * @return block count
     */
    public int validateTop (int x, int y, int z, int count)
    {
        int topBricks = 0;
        for (int xPos = x - 1; xPos <= (x + 1); xPos++)
            for (int zPos = z - 1; zPos <= (z + 1); zPos++)
                if (validBlockID(worldObj.getBlockId(xPos, y, zPos)) && (worldObj.getBlockMetadata(xPos, y, zPos) >= 1))
                    topBricks += checkBricks(xPos, y, zPos);
        structureHasTop = (topBricks == 9);
        return count;
    }

    /**
     * Determine if layer is a valid bottom layer
     * 
     * @param x
     *            coordinate from center
     * @param y
     *            coordinate from center
     * @param z
     *            coordinate from center
     * @param count
     *            current amount of blocks
     * @return block count
     */
    public int validateBottom (int x, int y, int z, int count)
    {
        int bottomBricks = 0;
        for (int xPos = x - 1; xPos <= (x + 1); xPos++)
            for (int zPos = z - 1; zPos <= (z + 1); zPos++)
                if (validBlockID(worldObj.getBlockId(xPos, y, zPos)) && 
                        (worldObj.getBlockMetadata(xPos, y, zPos) >= 1))
                    bottomBricks += checkBricks(xPos, y, zPos);
        structureHasBottom = (bottomBricks == 9);
        if (structureHasBottom)
            centerPos = new CoordTuple(x, y + 1, z);
        return count;
    }

    /**
     * Increments bricks, sets them as part of the structure.
     */
    int checkBricks (int x, int y, int z)
    {
        int tempBricks = 0;
        final int blockID = worldObj.getBlockId(x, y, z);
        if (validBlockID(blockID))
        {
            final TileEntity te = worldObj.getBlockTileEntity(x, y, z);
            if (te == this)
                tempBricks++;
            else if (te instanceof TSMultiServantLogic)
            {
                final TSMultiServantLogic servant = (TSMultiServantLogic) te;
                if (servant.hasValidMaster())
                {
                    if (servant.verifyMaster(this, worldObj, xCoord, yCoord, zCoord))
                        tempBricks++;
                }
                else if (servant.setMaster(xCoord, yCoord, zCoord))
                    tempBricks++;
            }
        }
        return tempBricks;
    }

    /**
     * Determine if block is a valid highoven component
     * 
     * @param blockID
     * @return Success
     */
    boolean validBlockID (int blockID)
    {
        return blockID == TSContent.highoven.blockID;
    }

    /* ==================== Fluid Handling ==================== */

    /**
     * Add molen metal fluidstack
     * 
     * @param liquid
     * @param first
     * @return Success
     */
    boolean addFluidToTank (FluidStack liquid, boolean first)
    {
        needsUpdate = true;
        if (moltenMetal.size() == 0)
        {
            moltenMetal.add(liquid.copy());
            currentLiquid += liquid.amount;
            return true;
        }
        else
        {
//            if (liquid.fluidID != TSContent.steamFluid.getID())
//                return false;
            if ((liquid.amount + currentLiquid) > maxLiquid)
                return false;
            currentLiquid += liquid.amount;
            boolean added = false;
            for (int i = 0; i < moltenMetal.size(); i++)
            {
                FluidStack l = moltenMetal.get(i);
                if (l.isFluidEqual(liquid))
                {
                    l.amount += liquid.amount;
                    added = true;
                }
                if (l.amount <= 0)
                {
                    moltenMetal.remove(l);
                    i--;
                }
            }
            if (!added)
                if (first)
                    moltenMetal.add(0, liquid.copy());
                else
                    moltenMetal.add(liquid.copy());
            return true;
        }
    }

    void addSolidItem (ItemStack stack)
    {
        boolean transferred = false;
        // Dispense item if no duct is present
        if (outputDuct != null)
        {
            final TileEntity te = worldObj.getBlockTileEntity(outputDuct.x, outputDuct.y, outputDuct.z);
            if (te instanceof HighOvenDuctLogic)
            {
                final HighOvenDuctLogic duct = (HighOvenDuctLogic) worldObj.getBlockTileEntity(te.xCoord, te.yCoord, te.zCoord);
                transferred = sendItemToDuct(duct, stack);
            }
        }
        if (!transferred)
            dispenseSolidItem(stack);
    }

    void dispenseSolidItem (ItemStack stack)
    {
        final BlockSourceImpl blocksourceimpl = new BlockSourceImpl(worldObj, xCoord, yCoord, zCoord);
        final IBehaviorDispenseItem ibehaviordispenseitem = (IBehaviorDispenseItem)dispenseBehaviorRegistry.getObject(stack.getItem());

        if (ibehaviordispenseitem != IBehaviorDispenseItem.itemDispenseBehaviorProvider)
        {
            final ItemStack itemstack1 = ibehaviordispenseitem.dispense(blocksourceimpl, stack);
        }
    }

    boolean sendItemToDuct (HighOvenDuctLogic duct, ItemStack stack)
    {
        boolean effective = false;
        ItemStack copystack = new ItemStack(stack.itemID, stack.stackSize, stack.getItemDamage());
        for (int slot = 0; slot < duct.getSizeInventory(); slot++)
        {
            final ItemStack getstack = duct.getStackInSlot(slot);
            
            if (duct.isItemValidForSlot(slot, copystack))
            {
                boolean flag = false;
                if (copystack == null) 
                    break;
                if (getstack == null)
                {
                    final int max = Math.min(stack.getMaxStackSize(), duct.getInventoryStackLimit());
                    if (max >= copystack.stackSize)
                    {
                        duct.setInventorySlotContents(slot, copystack);
                        copystack = null;
                        flag = true;
                        effective = flag;
                    }
                    else
                    {
                        duct.setInventorySlotContents(slot, copystack.splitStack(max));
                        copystack = null;
                        flag = true;
                        effective = flag;
                    }
                }
                else if (duct.areItemStacksEqualItem(getstack, copystack))
                {
                    final int max = Math.min(copystack.getMaxStackSize(), duct.getInventoryStackLimit());
                    if (max > getstack.stackSize)
                    {
                        final int l = Math.min(copystack.stackSize, max - getstack.stackSize);
                        copystack.stackSize -= l;
                        getstack.stackSize += l;
                        if (copystack.stackSize < 1)
                            copystack = null;
                        flag = l > 0;
                        effective = flag;
                    }
                }
            }
            if (effective)
                duct.onInventoryChanged();
        }
        return effective;
    }

    /**
     * Get max liquid capacity
     */
    @Override
    public int getCapacity () { return maxLiquid; }

    /**
     * Get current liquid amount
     * 
     * @return
     */
    public int getTotalLiquid () { return currentLiquid; }

    @Override
    public FluidStack drain (int maxDrain, boolean doDrain)
    {
        if (moltenMetal.size() == 0) return null;
        final FluidStack liquid = moltenMetal.get(0);
        if (liquid != null)
        {
            if ((liquid.amount - maxDrain) <= 0)
            {
                final FluidStack liq = liquid.copy();
                if (doDrain)
                {
                    moltenMetal.remove(liquid);
                    worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                    currentLiquid = 0;
                    needsUpdate = true;
                }
                return liq;
            }
            else
            {
                if (doDrain && (maxDrain > 0))
                {
                    liquid.amount -= maxDrain;
                    worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                    currentLiquid -= maxDrain;
                    needsUpdate = true;
                }
                return new FluidStack(liquid.fluidID, maxDrain, liquid.tag);
            }
        }
        else
            return new FluidStack(0, 0);
    }

    @Override
    public int fill (FluidStack resource, boolean doFill)
    {
        if ((resource != null) && (currentLiquid < maxLiquid))
        {
            boolean first = (resource.getFluid() == FluidRegistry.WATER);
            if ((resource.amount + currentLiquid) > maxLiquid)
                resource.amount = maxLiquid - currentLiquid;
            final int amount = resource.amount;
            if ((amount > 0) && doFill)
            {
                addFluidToTank(resource, first);
                needsUpdate = true;
                worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord);
            }
            return amount;
        }
        else
            return 0;
    }

    @Override
    public FluidStack getFluid ()
    {
        if (moltenMetal.size() == 0) return null;
        return moltenMetal.get(0);
    }

    @Override
    public int getFluidAmount () { return currentLiquid; }

    @Override
    public FluidTankInfo getInfo () { return new FluidTankInfo(this); }

    public FluidTankInfo[] getMultiTankInfo ()
    {
        final FluidTankInfo[] info = new FluidTankInfo[moltenMetal.size() + 1];
        for (int i = 0; i < moltenMetal.size(); i++)
        {
            final FluidStack fluid = moltenMetal.get(i);
            info[i] = new FluidTankInfo(fluid.copy(), fluid.amount);
        }
        info[moltenMetal.size()] = new FluidTankInfo(null, maxLiquid - currentLiquid);
        return info;
    }

    /* ==================== NBT ==================== */

    @Override
    public void readFromNBT (NBTTagCompound tags)
    {
        layers = tags.getInteger("Layers");
        inventory = new ItemStack[4 + layers];
        
        int[] duct = tags.getIntArray("OutputDuct");
        if (duct.length > 2)
            outputDuct = new CoordTuple(duct[0], duct[1], duct[2]);
        
        super.readFromNBT(tags);
        validStructure = tags.getBoolean("ValidStructure");
        redstoneActivated = tags.getBoolean("RedstoneActivated");
        internalTemp = tags.getInteger("InternalTemp");
        isMeltingItems = tags.getBoolean("InUse");
        final int[] center = tags.getIntArray("CenterPos");
        if (center.length > 2)
            centerPos = new CoordTuple(center[0], center[1], center[2]);
        else
            centerPos = new CoordTuple(xCoord, yCoord, zCoord);
        direction = tags.getByte("Direction");
        fuelBurnTime = tags.getInteger("UseTime");
        fuelHeatRate = tags.getInteger("FuelHeatRate");
        currentLiquid = tags.getInteger("CurrentLiquid");
        maxLiquid = tags.getInteger("MaxLiquid");
        meltingTemps = tags.getIntArray("MeltingTemps");
        activeTemps = tags.getIntArray("ActiveTemps");
        final NBTTagList liquidTag = tags.getTagList("Liquids");
        moltenMetal.clear();
        for (int iter = 0; iter < liquidTag.tagCount(); iter++)
        {
            final NBTTagCompound nbt = (NBTTagCompound) liquidTag.tagAt(iter);
            final FluidStack fluid = FluidStack.loadFluidStackFromNBT(nbt);
            if (fluid != null)
                moltenMetal.add(fluid);
        }
    }

    @Override
    public void writeToNBT (NBTTagCompound tags)
    {
        super.writeToNBT(tags);
        tags.setBoolean("ValidStructure", validStructure);
        tags.setBoolean("RedstoneActivated", redstoneActivated);
        tags.setInteger("InternalTemp", internalTemp);
        tags.setBoolean("InUse", isMeltingItems);
        int[] duct = new int[3];
        if (outputDuct != null)
            duct = new int[] { outputDuct.x, outputDuct.y, outputDuct.z };
        tags.setIntArray("OutputDuct", duct);
        
        int[] center = new int[3];
        if (centerPos == null)
            center = new int[] { xCoord, yCoord, zCoord };
        else
            center = new int[] { centerPos.x, centerPos.y, centerPos.z };
        tags.setIntArray("CenterPos", center);
        tags.setByte("Direction", direction);
        tags.setInteger("UseTime", fuelBurnTime);
        tags.setInteger("FuelHeatRate", fuelHeatRate);
        tags.setInteger("CurrentLiquid", currentLiquid);
        tags.setInteger("MaxLiquid", maxLiquid);
        tags.setInteger("Layers", layers);
        tags.setIntArray("MeltingTemps", meltingTemps);
        tags.setIntArray("ActiveTemps", activeTemps);
        final NBTTagList taglist = new NBTTagList();
        for (final FluidStack liquid : moltenMetal)
        {
            final NBTTagCompound nbt = new NBTTagCompound();
            liquid.writeToNBT(nbt);
            taglist.appendTag(nbt);
        }
        tags.setTag("Liquids", taglist);
    }

    @Override
    public Packet getDescriptionPacket ()
    {
        final NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new Packet132TileEntityData(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket (INetworkManager net, Packet132TileEntityData packet)
    {
        readFromNBT(packet.data);
        onInventoryChanged();
        worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord);
        needsUpdate = true;
    }

    /* ==================== Other ==================== */

    @Override
    public void openChest () {}

    @Override
    public void closeChest () {}
}
