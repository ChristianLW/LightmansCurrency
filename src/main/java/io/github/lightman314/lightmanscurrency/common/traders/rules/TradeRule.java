package io.github.lightman314.lightmanscurrency.common.traders.rules;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.google.gson.JsonSyntaxException;
import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.api.network.LazyPacketData;
import io.github.lightman314.lightmanscurrency.api.traders.TraderAPI;
import io.github.lightman314.lightmanscurrency.api.traders.rules.TradeRuleType;
import io.github.lightman314.lightmanscurrency.client.gui.screen.inventory.traderstorage.trade_rules.TradeRulesClientSubTab;
import io.github.lightman314.lightmanscurrency.client.gui.screen.inventory.traderstorage.trade_rules.TradeRulesClientTab;
import io.github.lightman314.lightmanscurrency.api.misc.EasyText;
import io.github.lightman314.lightmanscurrency.api.events.TradeEvent.PostTradeEvent;
import io.github.lightman314.lightmanscurrency.api.events.TradeEvent.PreTradeEvent;
import io.github.lightman314.lightmanscurrency.api.events.TradeEvent.TradeCostEvent;
import net.minecraft.ResourceLocationException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class TradeRule {
	
	public final TradeRuleType<?> type;
	public static MutableComponent nameOfType(ResourceLocation ruleType) { return EasyText.translatable("traderule." + ruleType.getNamespace() + "." + ruleType.getPath());
	}
	public final MutableComponent getName() { return nameOfType(this.type.type); }

	private ITradeRuleHost host = null;

	private boolean isActive = false;
	public boolean isActive() { return this.canActivate(this.host) && this.isActive; }
	public void setActive(boolean active) { this.isActive = active; }

	protected boolean allowHost(@Nonnull ITradeRuleHost host)
	{
		if(this.onlyAllowOnTraders() && !host.isTrader())
			return false;
		if(this.onlyAllowOnTrades() && !host.isTrade())
			return false;
		return true;
	}
	public boolean canActivate() { return this.canActivate(this.host); }
	protected boolean canActivate(@Nullable ITradeRuleHost host) { return this.allowHost(host); }

	protected boolean onlyAllowOnTraders() { return false; }
	protected boolean onlyAllowOnTrades() { return false; }

	public void beforeTrade(PreTradeEvent event) {}
	public void tradeCost(TradeCostEvent event) {}
	public void afterTrade(PostTradeEvent event) {}
	
	protected TradeRule(@Nonnull TradeRuleType<?> type) { this.type = type; }

	@Nonnull
	public CompoundTag save()
	{
		CompoundTag compound = new CompoundTag();
		compound.putString("Type", this.type.toString());
		compound.putBoolean("Active", this.isActive);
		this.saveAdditional(compound);
		return compound;
	}
	
	protected abstract void saveAdditional(@Nonnull CompoundTag compound);
	
	public final void load(@Nonnull CompoundTag compound)
	{
		this.isActive = compound.getBoolean("Active");
		this.loadAdditional(compound);
	}
	protected abstract void loadAdditional(@Nonnull CompoundTag compound);
	
	public abstract JsonObject saveToJson(@Nonnull JsonObject json);
	public abstract void loadFromJson(@Nonnull JsonObject json) throws JsonSyntaxException, ResourceLocationException;
	
	public abstract CompoundTag savePersistentData();
	public abstract void loadPersistentData(CompoundTag data);
	
	public final void receiveUpdateMessage(@Nonnull LazyPacketData data)
	{
		if(data.contains("SetActive"))
			this.isActive = data.getBoolean("SetActive");
		this.handleUpdateMessage(data);
	}
	
	protected abstract void handleUpdateMessage(@Nonnull LazyPacketData updateInfo);

	public static void saveRules(@Nonnull CompoundTag compound, @Nonnull List<TradeRule> rules, @Nonnull String tag)
	{
		ListTag ruleData = new ListTag();
		for (TradeRule rule : rules) ruleData.add(rule.save());
		compound.put(tag, ruleData);
	}
	
	public static boolean savePersistentData(@Nonnull CompoundTag compound, @Nonnull List<TradeRule> rules, @Nonnull String tag) {
		ListTag ruleData = new ListTag();
		for (TradeRule rule : rules) {
			CompoundTag thisRuleData = rule.savePersistentData();
			if (thisRuleData != null) {
				thisRuleData.putString("Type", rule.type.toString());
				ruleData.add(thisRuleData);
			}
		}
		if(ruleData.size() == 0)
			return false;
		compound.put(tag, ruleData);
		return true;
	}
	
	public static JsonArray saveRulesToJson(@Nonnull List<TradeRule> rules) {
		JsonArray ruleData = new JsonArray();
		for (TradeRule rule : rules) {
			if (rule.isActive) {
				JsonObject thisRuleData = rule.saveToJson(new JsonObject());
				if (thisRuleData != null) {
					thisRuleData.addProperty("Type", rule.type.toString());
					ruleData.add(thisRuleData);
				}
			}
		}
		return ruleData;
	}

	public static List<TradeRule> loadRules(@Nonnull CompoundTag compound, @Nonnull String tag, @Nullable ITradeRuleHost host)
	{
		List<TradeRule> rules = new ArrayList<>();
		if(compound.contains(tag, Tag.TAG_LIST))
		{
			ListTag ruleData = compound.getList(tag, Tag.TAG_COMPOUND);
			for(int i = 0; i < ruleData.size(); i++)
			{
				CompoundTag thisRuleData = ruleData.getCompound(i);
				TradeRule thisRule = Deserialize(thisRuleData);
				if(thisRule != null)
				{
					rules.add(thisRule);
					thisRule.host = host;
				}
			}
		}
		return rules;
	}
	
	public static void loadPersistentData(@Nonnull CompoundTag compound, @Nonnull List<TradeRule> tradeRules, @Nonnull String tag)
	{
		if(compound.contains(tag, Tag.TAG_LIST))
		{
			ListTag ruleData = compound.getList(tag, Tag.TAG_COMPOUND);
			for(int i = 0; i < ruleData.size(); ++i)
			{
				CompoundTag thisRuleData = ruleData.getCompound(i);
				boolean query = true;
				for(int r = 0; query && r < tradeRules.size(); ++r)
				{
					if(tradeRules.get(r).type.toString().contentEquals(thisRuleData.getString("Type")))
					{
						tradeRules.get(r).loadPersistentData(thisRuleData);
						query = false;
					}
				}
			}
		}
	}

	public static List<TradeRule> Parse(@Nonnull JsonArray tradeRuleData, @Nullable ITradeRuleHost host)
	{
		List<TradeRule> rules = new ArrayList<>();
		for(int i = 0; i < tradeRuleData.size(); ++i)
		{
			try {
				JsonObject thisRuleData = tradeRuleData.get(i).getAsJsonObject();
				TradeRule thisRule = Deserialize(thisRuleData);
				thisRule.host = host;
				rules.add(thisRule);
			}
			catch(Throwable t) { LightmansCurrency.LogError("Error loading Trade Rule at index " + i + ".", t); }
		}
		return rules;
	}

	public static boolean ValidateTradeRuleList(@Nonnull List<TradeRule> rules, @Nonnull ITradeRuleHost host)
	{
		boolean changed = false;
		//Add missing rules
		for(TradeRuleType<?> ruleType : TraderAPI.getTradeRuleTypes())
		{
			TradeRule rule = ruleType.createNew();
			if(rule != null && host.allowTradeRule(rule) && rule.allowHost(host) && !HasTradeRule(rules,rule.type.type))
			{
				rules.add(rule);
				rule.host = host;
				changed = true;
			}
		}
		//Confirm no duplicates
		for(int i = 0; i < rules.size(); ++i)
		{
			TradeRule r1 = rules.get(i);
			for(int j = i + 1; j < rules.size(); ++j)
			{
				if(rules.get(j).type == r1.type)
				{
					rules.remove(j--);
					changed = true;
				}
			}
		}
		return changed;
	}

	public static boolean ValidateTradeRuleActiveStates(@Nonnull List<TradeRule> rules)
	{
		boolean changed = false;
		for(TradeRule rule : rules)
		{
			if(rule.isActive && !rule.canActivate())
			{
				rule.isActive = false;
				changed = true;
			}
		}
		return changed;
	}
	
	public static boolean HasTradeRule(@Nonnull List<TradeRule> rules, @Nonnull ResourceLocation type) { return GetTradeRule(rules, type) != null; }

	@Nullable
	public static TradeRule GetTradeRule(@Nonnull List<TradeRule> rules, @Nonnull ResourceLocation type)
	{
		for(TradeRule rule : rules)
		{
			if(rule.type.type.equals(type))
				return rule;
		}
		return null;
	}

	@OnlyIn(Dist.CLIENT)
	@Nonnull
	public abstract TradeRulesClientSubTab createTab(TradeRulesClientTab<?> parent);

	@Nonnull
	public static TradeRule CreateRule(@Nonnull ResourceLocation type)
	{
		TradeRuleType<?> ruleType = TraderAPI.getTradeRuleType(type);
		if(ruleType != null)
			return ruleType.createNew();
		LightmansCurrency.LogError("Could not find a TradeRuleType of type '" + type + "'. Unable to createTrue the Trade Rule.");
		return null;
	}

	@Nonnull
	public static TradeRule Deserialize(@Nonnull CompoundTag compound)
	{
		String thisType = compound.contains("Type") ? compound.getString("Type") : compound.getString("type");
		TradeRuleType<?> ruleType = TraderAPI.getTradeRuleType(new ResourceLocation(thisType));
		if(ruleType != null)
			return ruleType.load(compound);
		LightmansCurrency.LogError("Could not find a TradeRuleType of type '" + thisType + "'. Unable to load the Trade Rule.");
		return null;
	}

	@Nonnull
	public static TradeRule Deserialize(@Nonnull JsonObject json) throws JsonSyntaxException, ResourceLocationException {
		String thisType = GsonHelper.getAsString(json, "Type");
		TradeRuleType<?> ruleType = TraderAPI.getTradeRuleType(new ResourceLocation(thisType));
		if(ruleType != null)
		{
			TradeRule rule = ruleType.loadFromJson(json);
			rule.loadFromJson(json);
			rule.setActive(true);
			return rule;
		}
		throw new JsonSyntaxException("Could not find a deserializer of type '" + thisType + "'.");
	}

	@Nullable
	public static TradeRule getRule(@Nonnull ResourceLocation type, @Nonnull List<TradeRule> rules) {
		for(TradeRule rule : rules)
		{
			if(rule.type.type.equals(type))
				return rule;
		}
		return null;
	}
	
	public static CompoundTag CreateRuleMessage() { CompoundTag tag = new CompoundTag(); tag.putBoolean("Create", true); return tag; }
	public static CompoundTag RemoveRuleMessage() { CompoundTag tag = new CompoundTag(); tag.putBoolean("Remove", true); return tag; }
	
	public static boolean isCreateMessage(CompoundTag tag) { return tag.contains("Create") && tag.getBoolean("Create"); }
	public static boolean isRemoveMessage(CompoundTag tag) { return tag.contains("Remove") && tag.getBoolean("Remove"); }
	
}
