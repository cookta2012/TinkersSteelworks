package tsteelworks.blocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import tsteelworks.common.core.TSContent;
import tsteelworks.lib.TSteelworksRegistry;

import java.util.List;

public class ScorchedSlab extends TSBaseSlab {
	public ScorchedSlab() {
		super(Material.rock);

		setCreativeTab(TSteelworksRegistry.SteelworksCreativeTab);
		setHardness(12F);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		meta = meta % 8;

		if (meta == 0)
			return TSContent.highoven.getIcon(side, 2);
		if (meta <= 3)
			return TSContent.highoven.getIcon(side, meta + 3);

		return TSContent.highoven.getIcon(side, meta + 4);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Override
	public void getSubBlocks(Item id, CreativeTabs tab, List list) {
		for (int iter = 0; iter < 8; iter++) {
			list.add(new ItemStack(id, 1, iter));
		}
	}
}