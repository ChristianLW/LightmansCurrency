package io.github.lightman314.lightmanscurrency.api.traders.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.taxes.ITaxCollector;
import io.github.lightman314.lightmanscurrency.api.traders.TradeContext;
import io.github.lightman314.lightmanscurrency.api.traders.TraderData;
import io.github.lightman314.lightmanscurrency.common.traders.rules.ITradeRuleHost;
import io.github.lightman314.lightmanscurrency.common.traders.rules.TradeRule;
import io.github.lightman314.lightmanscurrency.api.traders.trade.client.TradeRenderManager;
import io.github.lightman314.lightmanscurrency.api.traders.trade.comparison.TradeComparisonResult;
import io.github.lightman314.lightmanscurrency.api.events.TradeEvent.PostTradeEvent;
import io.github.lightman314.lightmanscurrency.api.events.TradeEvent.PreTradeEvent;
import io.github.lightman314.lightmanscurrency.api.events.TradeEvent.TradeCostEvent;
import io.github.lightman314.lightmanscurrency.common.menus.traderstorage.trades_basic.BasicTradeEditTab;
import io.github.lightman314.lightmanscurrency.api.network.LazyPacketData;
import io.github.lightman314.lightmanscurrency.util.MathUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

public abstract class TradeData implements ITradeRuleHost {

	public static final String DEFAULT_KEY = "Trades";
	
	public enum TradeDirection { SALE(0), PURCHASE(1), NONE(-1);
		public final int index;
		TradeDirection(int index) { this.index = index; }
		public static TradeDirection fromIndex(int index) {
			for(TradeDirection d : TradeDirection.values())
			{
				if(d.index == index)
					return d;
			}
			return TradeDirection.SALE;
		}
	}
	
	protected MoneyValue cost = MoneyValue.empty();
	
	List<TradeRule> rules = new ArrayList<>();
	
	public abstract TradeDirection getTradeDirection();

	public boolean validCost() { return this.getCost().isValidPrice(); }
	
	public boolean isValid() { return validCost(); }
	
	public MoneyValue getCost() { return this.cost; }

	private TradeCostCache cachedCost = TradeCostCache.EMPTY;

	protected void flagPriceAsChanged() { this.cachedCost = TradeCostCache.EMPTY; }

	/**
	 * Standard method of obtaining the cost of a trade given a context.
	 * Will run the {@link TradeCostEvent} to calculate any price changes,
	 * and will cache the results to save framerate for client-side displays.
	 */
	public final MoneyValue getCost(@Nonnull TradeContext context) {
		if(!context.hasPlayerReference() || !context.hasTrader())
			return this.getCost();
		TradeCostEvent event = context.getTrader().runTradeCostEvent(context.getPlayerReference(), this);
		if(!this.cachedCost.matches(event))
			this.cachedCost = TradeCostCache.cache(event);
		return this.cachedCost.getPrice();
	}

	/**
	 * Gets the cost after all taxes are applied.
	 * Assumes that this is a purchase trade.
	 */
	public MoneyValue getCostWithTaxes(TraderData trader)
	{
		MoneyValue cost = this.getCost();
		MoneyValue taxAmount = MoneyValue.empty();
		for(ITaxCollector entry : trader.getApplicableTaxes())
			taxAmount = taxAmount.addValue(cost.percentageOfValue(entry.getTaxRate()));
		return cost.addValue(taxAmount);
	}
	public MoneyValue getCostWithTaxes(TradeContext context)
	{
		MoneyValue cost = this.getCost(context);
		if(context.hasTrader())
		{
			TraderData trader = context.getTrader();
			MoneyValue taxAmount = MoneyValue.empty();
			for(ITaxCollector entry : trader.getApplicableTaxes())
				taxAmount = taxAmount.addValue(cost.percentageOfValue(entry.getTaxRate()));
			return cost.addValue(taxAmount);
		}
		return cost;
	}
	
	public void setCost(MoneyValue value) { this.cost = value; this.flagPriceAsChanged(); }

	public final int stockCountOfCost(TraderData trader)
	{
		if(this.getCost().isFree())
			return 1;
		if(!this.getCost().isValidPrice())
			return 0;
		MoneyValue storedMoney = trader.getStoredMoney().getStoredMoney().valueOf(this.getCost().getUniqueName());
		MoneyValue price = this.getCostWithTaxes(trader);
		return (int)(storedMoney.getCoreValue() / price.getCoreValue());
	}

	public final int stockCountOfCost(TradeContext context)
	{
		if(!context.hasTrader())
			return 0;

		TraderData trader = context.getTrader();
		if(this.getCost().isFree())
			return 1;
		if(!this.getCost().isValidPrice())
			return 0;
		MoneyValue storedMoney = trader.getStoredMoney().getStoredMoney().valueOf(this.getCost().getUniqueName());
		MoneyValue price = this.getCostWithTaxes(context);
		return (int) MathUtil.SafeDivide(storedMoney.getCoreValue(), price.getCoreValue(), 1);
	}

	private final boolean validateRules;
	
	protected TradeData(boolean validateRules) {
		this.validateRules = validateRules;
		if(this.validateRules)
			TradeRule.ValidateTradeRuleList(this.rules, this);
	}
	
	public CompoundTag getAsNBT()
	{
		CompoundTag tradeNBT = new CompoundTag();
		tradeNBT.put("Price", this.cost.save());
		TradeRule.saveRules(tradeNBT, this.rules, "RuleData");
		
		return tradeNBT;
	}
	
	protected void loadFromNBT(CompoundTag nbt)
	{
		this.cost = MoneyValue.safeLoad(nbt, "Price");
		this.flagPriceAsChanged();
		//Set whether it's free or not
		if(nbt.contains("IsFree") && nbt.getBoolean("IsFree"))
		{
			this.cost = MoneyValue.free();
			this.flagPriceAsChanged();
		}
		
		this.rules.clear();
		if(nbt.contains("TradeRules"))
		{
			this.rules = TradeRule.loadRules(nbt, "TradeRules", this);
			for(TradeRule r : this.rules) r.setActive(true);
		}
		else
			this.rules = TradeRule.loadRules(nbt, "RuleData", this);
		
		if(this.validateRules)
			TradeRule.ValidateTradeRuleList(this.rules, this);
		
	}

	@Override
	public final boolean isTrader() { return false; }

	@Override
	public final boolean isTrade() { return true; }

	@Override
	public boolean allowTradeRule(@Nonnull TradeRule rule) { return true; }
	
	public void beforeTrade(PreTradeEvent event) {
		for(TradeRule rule : this.rules)
		{
			if(rule.isActive())
				rule.beforeTrade(event);
		}
	}

	public void tradeCost(TradeCostEvent event)
	{
		for(TradeRule rule : this.rules)
		{
			if(rule.isActive())
				rule.tradeCost(event);
		}
	}
	
	public void afterTrade(PostTradeEvent event) {
		for(TradeRule rule : this.rules)
		{
			if(rule.isActive())
				rule.afterTrade(event);
		}
	}
	
	@Nonnull
	@Override
	public List<TradeRule> getRules() { return new ArrayList<>(this.rules); }

	@Override
	public void markTradeRulesDirty() { }

	/**
	 * Only to be used for persistent trader loading
	 */
	public void setRules(List<TradeRule> rules) { this.rules = rules; }

	public abstract TradeComparisonResult compare(TradeData otherTrade);
	
	public abstract boolean AcceptableDifferences(TradeComparisonResult result);
	
	public abstract List<Component> GetDifferenceWarnings(TradeComparisonResult differences);
	
	@OnlyIn(Dist.CLIENT)
	public abstract TradeRenderManager<?> getButtonRenderer();

	/**
	 * Called when an input display is clicked on in display mode.
	 * Runs on the client, but can (and should) be called on the server by running tab.sendInputInteractionMessage for consistent execution
	 *
	 * @param tab The Trade Edit tab that is being used to display this tab.
	 * @param clientHandler The client handler that can be used to send custom client messages to the currently opened tab. Will be null on the server.
	 * @param index The index of the input display that was clicked.
	 * @param button The mouse button that was clicked.
	 * @param heldItem The item being held by the player.
	 */
	public abstract void OnInputDisplayInteraction(@Nonnull BasicTradeEditTab tab, @Nullable Consumer<LazyPacketData.Builder> clientHandler, int index, int button, @Nonnull ItemStack heldItem);

	/**
	 * Called when an output display is clicked on in display mode.
	 * Runs on the client, but can (and should) be called on the server by running tab.sendOutputInteractionMessage for consistent execution
	 *
	 * @param tab The Trade Edit tab that is being used to display this tab.
	 * @param clientHandler The client handler that can be used to send custom client messages to the currently opened tab. Will be null on the server.
	 * @param index The index of the input display that was clicked.
	 * @param button The mouse button that was clicked.
	 * @param heldItem The item being held by the player.
	 */
	public abstract void OnOutputDisplayInteraction(@Nonnull BasicTradeEditTab tab, @Nullable Consumer<LazyPacketData.Builder> clientHandler, int index, int button, @Nonnull ItemStack heldItem);

	@Deprecated(since = "2.1.2.4")
	public void onInteraction(@Nonnull BasicTradeEditTab tab, @Nullable Consumer<CompoundTag> clientHandler, int mouseX, int mouseY, int button, @Nonnull ItemStack heldItem) {}
	/**
	 * Called when the trade is clicked on in display mode, but the mouse wasn't over any of the input or output slots.
	 * Runs on the client, but can (and should) be called on the server by running tab.sendOtherInteractionMessage for consistent code execution.
	 *
	 * @param tab The Trade Edit tab that is being used to display this tab.
	 * @param clientHandler The client handler that can be used to send custom client messages to the currently opened tab. Will be null on the server.
	 * @param mouseX The local X position of the mouse button when it was clicked. [0,tradeButtonWidth)
	 * @param mouseY The local Y position of the mouse button when it was clicked. [0,tradeButtonHeight)
	 * @param button The mouse button that was clicked.
	 * @param heldItem The item currently being held by the player.
	 */
	public abstract void OnInteraction(@Nonnull BasicTradeEditTab tab, @Nullable Consumer<LazyPacketData.Builder> clientHandler, int mouseX, int mouseY, int button, @Nonnull ItemStack heldItem);

	@NotNull
	public final List<Integer> getRelevantInventorySlots(TradeContext context, List<Slot> slots) {
		List<Integer> results = new ArrayList<>();
		this.collectRelevantInventorySlots(context, slots, results);
		return results;
	}

	protected void collectRelevantInventorySlots(TradeContext context, List<Slot> slots, List<Integer> results) { }

	private static class TradeCostCache {
		private final boolean free;
		private final int percentage;
		private final MoneyValue price;
		public MoneyValue getPrice() { return this.free ? MoneyValue.free() : price; }
		private TradeCostCache(boolean free, int percentage, MoneyValue price) {
			this.free = free;
			this.percentage = percentage;
			this.price = price;

		}
		public static final TradeCostCache EMPTY = new TradeCostCache(true,0,MoneyValue.free());

		public static TradeCostCache cache(@Nonnull TradeCostEvent event) {
			boolean free = event.forcedFree() || event.getPricePercentage() <= 0;
			return new TradeCostCache(free, event.getPricePercentage(), event.getCostResult());
		}

		public boolean matches(@Nonnull TradeCostEvent event) {
			if(this.free && (event.forcedFree() || event.getPricePercentage() <= 0))
				return true;
			return this.percentage == event.getPricePercentage();
		}

	}

}
