package io.github.lightman314.lightmanscurrency.common.items;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import io.github.lightman314.lightmanscurrency.common.upgrades.UpgradeType;
import io.github.lightman314.lightmanscurrency.common.upgrades.UpgradeType.IUpgradeItem;
import io.github.lightman314.lightmanscurrency.common.upgrades.UpgradeType.UpgradeData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public abstract class UpgradeItem extends Item implements IUpgradeItem{

	protected final UpgradeType upgradeType;
	private boolean addTooltips = true;
	Function<UpgradeData,List<Component>> customTooltips = null;
	
	public UpgradeItem(UpgradeType upgradeType, Properties properties)
	{
		super(properties);
		this.upgradeType = upgradeType;
	}
	
	public final boolean addsTooltips() { return this.addTooltips; }
	protected final void ignoreTooltips() { this.addTooltips = false; }
	protected final void setCustomTooltips(Function<UpgradeData,List<Component>> customTooltips) { this.customTooltips = customTooltips; }
	
	@Override
	public UpgradeType getUpgradeType() { return this.upgradeType; }
	
	@Override
	public UpgradeData getDefaultUpgradeData()
	{
		UpgradeData data = this.upgradeType.getDefaultData();
		this.fillUpgradeData(data);
		return data;
	}
	
	protected abstract void fillUpgradeData(UpgradeData data);
	
	public static UpgradeData getUpgradeData(ItemStack stack)
	{
		if(stack.getItem() instanceof UpgradeItem)
		{
			UpgradeData data = ((UpgradeItem)stack.getItem()).getDefaultUpgradeData();
			if(stack.hasTag())
			{
				CompoundTag tag = stack.getTag();
				if(tag.contains("UpgradeData", Tag.TAG_COMPOUND))
					data.read(tag.getCompound("UpgradeData"));
			}
			return data;
		}
		return UpgradeData.EMPTY;
	}
	
	public static void setUpgradeData(ItemStack stack, UpgradeData data)
	{
		if(stack.getItem() instanceof UpgradeItem upgradeItem)
		{
			CompoundTag tag = stack.getOrCreateTag();
			tag.put("UpgradeData",  data.writeToNBT(upgradeItem.upgradeType));
		}
		else
		{
			CompoundTag tag = stack.getOrCreateTag();
			tag.put("UpgradeData", data.writeToNBT());
		}
	}
	
	public static List<Component> getUpgradeTooltip(ItemStack stack) { return getUpgradeTooltip(stack, false); }
	
	public static List<Component> getUpgradeTooltip(ItemStack stack, boolean forceCollection)
	{
		if(stack.getItem() instanceof UpgradeItem item)
		{
			if(!item.addTooltips && !forceCollection) //Block if tooltips have been blocked
				return Lists.newArrayList();
			UpgradeType type = item.getUpgradeType();
			UpgradeData data = getUpgradeData(stack);
			if(item.customTooltips != null)
				return item.customTooltips.apply(data);
			return type.getTooltip(data);
		}
		return Lists.newArrayList();
	}
	
	@Override
	public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flagIn)
	{
		//Add upgrade tooltips
		List<Component> upgradeTooltips = getUpgradeTooltip(stack);
		if(upgradeTooltips != null)
			tooltip.addAll(upgradeTooltips);
		
		super.appendHoverText(stack, level, tooltip, flagIn);
		
	}
	
	public static class Simple extends UpgradeItem
	{
		public Simple(UpgradeType upgradeType, Properties properties) { super(upgradeType, properties); }
		@Override
		protected void fillUpgradeData(UpgradeData data) { }
	}
	
}