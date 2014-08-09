package tsteelworks.network;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidStack;
import tsteelworks.TSteelworks;
import tsteelworks.blocks.logic.DeepTankLogic;
import tsteelworks.blocks.logic.HighOvenDuctLogic;
import tsteelworks.blocks.logic.HighOvenLogic;
import tsteelworks.common.core.TSRepo;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.Side;

public class TSPacketHandler implements IPacketHandler
{
    @Override
    public void onPacketData (INetworkManager manager, Packet250CustomPayload packet, Player player)
    {
        final Side side = FMLCommonHandler.instance().getEffectiveSide();
        if (packet.channel.equals(TSRepo.modChan))
            if (side == Side.SERVER)
                handleServerPacket(packet, (EntityPlayerMP) player);
            else
                handleClientPacket(packet, (EntityPlayer) player);
    }

    Entity getEntity (World world, int id)
    {
        for (final Object o : world.loadedEntityList)
            if (((Entity) o).entityId == id)
                return (Entity) o;
        return null;
    }

    void handleClientPacket (Packet250CustomPayload packet, EntityPlayer player)
    {
        final DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(packet.data));
        try
        {
            inputStream.readByte();
        }
        catch (final Exception e)
        {
            TSteelworks.logError("Failed at reading client packet for TSteelworks.", e);
        }
    }

    void handleServerPacket (Packet250CustomPayload packet, EntityPlayerMP player)
    {
        final DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(packet.data));
        byte packetID;
        try
        {
            packetID = inputStream.readByte();
            // High Oven
            if (packetID == TSRepo.ovenPacketID)
            {
                final int dimension = inputStream.readInt();
                final World world = DimensionManager.getWorld(dimension);
                final int x = inputStream.readInt();
                final int y = inputStream.readInt();
                final int z = inputStream.readInt();
                final boolean isShiftPressed = inputStream.readBoolean();
                final int fluidID = inputStream.readInt();
                final TileEntity te = world.getBlockTileEntity(x, y, z);
                if (te instanceof HighOvenLogic)
                {
                    FluidStack temp = null;
                    for (final FluidStack liquid : ((HighOvenLogic) te).getFluidlist())
                        if (liquid.fluidID == fluidID)
                            temp = liquid;
                    if (temp != null)
                    {
                        ((HighOvenLogic) te).removeFromFluidList(temp);
                        if (isShiftPressed)
                            ((HighOvenLogic) te).addToFluidList(temp);
                        else
                            ((HighOvenLogic) te).addToFluidList(0, temp);;
                    }
                    PacketDispatcher.sendPacketToAllInDimension(te.getDescriptionPacket(), dimension);
                }
            }
            // Duct
            if (packetID == TSRepo.ductPacketID)
            {
                final int dimension = inputStream.readInt();
                final World world = DimensionManager.getWorld(dimension);
                final int x = inputStream.readInt();
                final int y = inputStream.readInt();
                final int z = inputStream.readInt();
                final int mode = inputStream.readInt();
                final TileEntity te = world.getBlockTileEntity(x, y, z);
                if (te instanceof HighOvenDuctLogic)
                {
                    final int tempMode = ((HighOvenDuctLogic) te).getMode();
                    if (tempMode != mode)
                        ((HighOvenDuctLogic) te).setMode(mode);
                    PacketDispatcher.sendPacketToAllInDimension(te.getDescriptionPacket(), dimension);
                }

            }
            // Deep Tank
            if (packetID == TSRepo.tankPacketID)
            {
                final int dimension = inputStream.readInt();
                final World world = DimensionManager.getWorld(dimension);
                final int x = inputStream.readInt();
                final int y = inputStream.readInt();
                final int z = inputStream.readInt();
                final boolean isShiftPressed = inputStream.readBoolean();
                final int fluidID = inputStream.readInt();
                final TileEntity te = world.getBlockTileEntity(x, y, z);
                if (te instanceof DeepTankLogic)
                {
                    FluidStack temp = null;
                    for (final FluidStack liquid : ((DeepTankLogic) te).getFluidList())
                        if (liquid.fluidID == fluidID)
                            temp = liquid;
                    if (temp != null)
                    {
                        ((DeepTankLogic) te).getFluidList().remove(temp);
                        if (isShiftPressed)
                            ((DeepTankLogic) te).getFluidList().add(temp);
                        else
                            ((DeepTankLogic) te).getFluidList().add(0, temp);
                    }
                    PacketDispatcher.sendPacketToAllInDimension(te.getDescriptionPacket(), dimension);
                }

            }
        }
        catch (final IOException e)
        {
            TSteelworks.logError("Failed at reading server packet for TSteelworks.", e);
        }
    }
}