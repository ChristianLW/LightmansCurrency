package io.github.lightman314.lightmanscurrency.trader.tradedata.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.common.base.Supplier;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.client.gui.screen.TradeRuleScreen;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.icon.IconData;
import io.github.lightman314.lightmanscurrency.client.util.IconAndButtonUtil;
import io.github.lightman314.lightmanscurrency.events.TradeEvent.PostTradeEvent;
import io.github.lightman314.lightmanscurrency.events.TradeEvent.TradeCostEvent;
import io.github.lightman314.lightmanscurrency.trader.tradedata.TradeData.TradeDirection;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class FreeSample extends TradeRule{
	
	public static final ResourceLocation TYPE = new ResourceLocation(LightmansCurrency.MODID, "free_sample");
	
	List<UUID> memory = new ArrayList<>();
	public void resetMemory() { this.memory.clear(); }
	
	public FreeSample() { super(TYPE); }
	
	@Override
	public void tradeCost(TradeCostEvent event) {
		
		if(this.giveDiscount(event.getPlayerReference().id) && event.getTrade().getTradeDirection() != TradeDirection.PURCHASE)
		{
			event.applyCostMultiplier(0d);
		}
		
	}

	@Override
	public void afterTrade(PostTradeEvent event) {
		
		if(this.giveDiscount(event.getPlayerReference().id))
		{
			this.addToMemory(event.getPlayerReference().id);
			event.markDirty();
		}
	}
	
	private void addToMemory(UUID playerID) {
		if(!this.memory.contains(playerID))
			this.memory.add(playerID);
	}
	
	public boolean giveDiscount(UUID playerID) {
		return !this.givenFreeSample(playerID);
	}
	
	private boolean givenFreeSample(UUID playerID) {
		return this.memory.contains(playerID);
	}
	
	@Override
	public CompoundTag write(CompoundTag compound) {
		
		ListTag memoryList = new ListTag();
		for(UUID entry : this.memory)
		{
			CompoundTag tag = new CompoundTag();
			tag.putUUID("id", entry);
			memoryList.add(tag);
		}
		compound.put("Memory", memoryList);
		return compound;
	}
	
	@Override
	public JsonObject saveToJson(JsonObject json) { return json; }

	@Override
	public void readNBT(CompoundTag compound) {
		
		if(compound.contains("Memory", Tag.TAG_LIST))
		{
			this.memory.clear();
			ListTag memoryList = compound.getList("Memory", Tag.TAG_COMPOUND);
			for(int i = 0; i < memoryList.size(); i++)
			{
				CompoundTag tag = memoryList.getCompound(i);
				if(tag.contains("id"))
					this.memory.add(tag.getUUID("id"));
				
			}
		}
	}
	
	@Override
	public void handleUpdateMessage(CompoundTag updateInfo)
	{
		if(updateInfo.contains("ClearMemory"))
		{
			this.resetMemory();
		}
	}
	
	@Override
	public CompoundTag savePersistentData() {
		CompoundTag data = new CompoundTag();
		ListTag memoryList = new ListTag();
		for(UUID entry : this.memory)
		{
			CompoundTag tag = new CompoundTag();
			tag.putUUID("id", entry);
			memoryList.add(tag);
		}
		data.put("Memory", memoryList);
		return data;
	}
	
	@Override
	public void loadPersistentData(CompoundTag data) {
		if(data.contains("Memory", Tag.TAG_LIST))
		{
			this.memory.clear();
			ListTag memoryList = data.getList("Memory", Tag.TAG_COMPOUND);
			for(int i = 0; i < memoryList.size(); i++)
			{
				CompoundTag tag = memoryList.getCompound(i);
				if(tag.contains("id"))
					this.memory.add(tag.getUUID("id"));
				
			}
		}
	}
	
	@Override
	public void loadFromJson(JsonObject json) { }
	
	@Override
	public IconData getButtonIcon() { return IconAndButtonUtil.ICON_FREE_SAMPLE; }

	@Override
	@OnlyIn(Dist.CLIENT)
	public TradeRule.GUIHandler createHandler(TradeRuleScreen screen, Supplier<TradeRule> rule)
	{
		return new GUIHandler(screen, rule);
	}
	
	@OnlyIn(Dist.CLIENT)
	private static class GUIHandler extends TradeRule.GUIHandler
	{
		
		private final FreeSample getRule()
		{
			if(getRuleRaw() instanceof FreeSample)
				return (FreeSample)getRuleRaw();
			return null;
		}
		
		GUIHandler(TradeRuleScreen screen, Supplier<TradeRule> rule)
		{
			super(screen, rule);
		}
		
		Button buttonClearMemory;
		
		@Override
		public void initTab() {
			
			this.buttonClearMemory = this.addCustomRenderable(new Button(screen.guiLeft() + 10, screen.guiTop() + 50, screen.xSize - 20, 20, Component.translatable("gui.button.lightmanscurrency.free_sample.reset"), this::PressClearMemoryButton));
			
		}

		@Override
		public void renderTab(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
			
			if(this.buttonClearMemory.isMouseOver(mouseX, mouseY))
				screen.renderTooltip(poseStack, Component.translatable("gui.button.lightmanscurrency.free_sample.reset.tooltip"), mouseX, mouseY);
			
		}

		@Override
		public void onTabClose() {
			this.removeCustomWidget(this.buttonClearMemory);
		}
		
		@Override
		public void onScreenTick() { }
		
		void PressClearMemoryButton(Button button)
		{
			this.getRule().memory.clear();
			CompoundTag updateInfo = new CompoundTag();
			updateInfo.putBoolean("ClearMemory", true);
			this.screen.updateServer(TYPE, updateInfo);
		}
		
	}
	
}
