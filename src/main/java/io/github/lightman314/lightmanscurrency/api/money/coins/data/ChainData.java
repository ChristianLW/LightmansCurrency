package io.github.lightman314.lightmanscurrency.api.money.coins.data;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Pair;
import io.github.lightman314.lightmanscurrency.api.events.BuildDefaultMoneyDataEvent;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.client.CoinInputTypeHelper;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.coin.CoinEntry;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.coin.MainCoinEntry;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.coin.SideBaseCoinEntry;
import io.github.lightman314.lightmanscurrency.api.money.coins.display.ValueDisplayAPI;
import io.github.lightman314.lightmanscurrency.api.money.coins.display.ValueDisplayData;
import io.github.lightman314.lightmanscurrency.api.money.coins.display.ValueDisplaySerializer;
import io.github.lightman314.lightmanscurrency.api.money.coins.display.builtin.Null;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import io.github.lightman314.lightmanscurrency.api.money.coins.atm.data.ATMData;
import io.github.lightman314.lightmanscurrency.common.capability.event_unlocks.CapabilityEventUnlocks;
import io.github.lightman314.lightmanscurrency.api.misc.EasyText;
import io.github.lightman314.lightmanscurrency.util.EnumUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.ResourceLocationException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.*;

public class ChainData {

    public static final Comparator<CoinEntry> SORT_HIGHEST_VALUE_FIRST = Comparator.comparingLong(CoinEntry::getCoreValue);
    public static final Comparator<CoinEntry> SORT_LOWEST_VALUE_FIRST = (a,b) -> Long.compare(b.getCoreValue(),a.getCoreValue());

    public final boolean isEvent;
    public final String chain;
    private final MutableComponent displayName;
    public MutableComponent getDisplayName() { return this.displayName; }

    private final CoinInputType inputType;
    public CoinInputType getInputType() { return this.inputType; }

    private final ValueDisplayData displayData;
    public ValueDisplayData getDisplayData() { return this.displayData; }

    public boolean isVisibleTo(@Nonnull Player player) { return !this.isEvent || CapabilityEventUnlocks.isUnlocked(player, this.chain); }

    private final ATMData atmData;
    public boolean hasATMData() { return this.atmData != null && this.atmData.getExchangeButtons().size() > 0; }
    @Nonnull
    public ATMData getAtmData() { return this.atmData; }
    @Nonnull
    public MutableComponent formatValue(@Nonnull CoinValue value, @Nonnull MutableComponent empty) { return this.displayData.formatValue(value, empty); }
    public void formatCoinTooltip(@Nonnull ItemStack stack, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        this.displayData.formatCoinTooltip(stack, tooltip);
        if(flag.isAdvanced())
        {
            CoinEntry entry = this.findEntry(stack);
            if(entry != null)
            {
                tooltip.add(EasyText.translatable("tooltip.lightmanscurrency.coin.advanced.chain", this.chain).withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(EasyText.translatable("tooltip.lightmanscurrency.coin.advanced.value", DecimalFormat.getIntegerInstance().format(entry.getCoreValue())).withStyle(ChatFormatting.DARK_GRAY));
                if(entry.isSideChain())
                    tooltip.add(EasyText.translatable("tooltip.lightmanscurrency.coin.advanced.side_chain").withStyle(ChatFormatting.DARK_GRAY));
                else
                    tooltip.add(EasyText.translatable("tooltip.lightmanscurrency.coin.advanced.core_chain").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    private final List<CoinEntry> coreChain;
    private final List<List<CoinEntry>> sideChains;

    protected ChainData(@Nonnull Builder builder)
    {
        this.chain = builder.chain;
        this.displayName = builder.displayName;
        this.displayData = builder.displayData;
        this.displayData.setParent(this);
        this.inputType = builder.inputType;
        this.coreChain = ImmutableList.copyOf(builder.coreChain.entries);
        this.isEvent = builder.isEvent;
        List<List<CoinEntry>> temp = new ArrayList<>();
        builder.sideChains.forEach(chain -> temp.add(ImmutableList.copyOf(chain.entries)));
        this.sideChains = ImmutableList.copyOf(temp);
        this.atmData = builder.atmDataBuilder.build(this);
        this.defineEntryCoreValues();
    }

    protected ChainData(@Nonnull List<CoinEntry> existingEntries, @Nonnull JsonObject json) throws JsonSyntaxException, ResourceLocationException
    {
        this.chain = GsonHelper.getAsString(json, "chain");
        if(this.chain.equalsIgnoreCase("null"))
            throw new JsonSyntaxException("Chain id cannot be null!");
        this.displayName = Component.Serializer.fromJson(json.get("name"));

        this.isEvent = GsonHelper.getAsBoolean(json, "EventChain", false);

        ResourceLocation displayType = new ResourceLocation(GsonHelper.getAsString(json, "displayType"));
        ValueDisplaySerializer displaySerializer = ValueDisplayAPI.get(displayType);
        if(displaySerializer == null)
            throw new JsonSyntaxException(displayType + " is not a valid displayType");
        //Reset the builder for a fresh load
        displaySerializer.resetBuilder();
        displaySerializer.parseAdditional(json);
        
        this.inputType = EnumUtil.enumFromString(GsonHelper.getAsString(json, "InputType"), CoinInputType.values(), null);
        if(this.inputType == null)
            throw new JsonSyntaxException("InputType is not valid!");

        JsonArray coreChainArray = GsonHelper.getAsJsonArray(json, "CoreChain");
        if(coreChainArray.size() == 0)
            throw new JsonSyntaxException("CoreChain must have at least 1 entry!");
        List<CoinEntry> coreChainTemp = new ArrayList<>();
        try { //Load first entry manually
            JsonObject baseEntry = coreChainArray.get(0).getAsJsonObject();
            CoinEntry temp = CoinEntry.parse(baseEntry);
            validateNoDuplicateCoins(temp, existingEntries);
            coreChainTemp.add(temp);
            displaySerializer.parseAdditionalFromCoin(temp, baseEntry);
        } catch(JsonSyntaxException | ResourceLocationException e) { throw new JsonSyntaxException("Error parsing core chain entry #1 in the " + this.chain + " chain!", e); }
        //Load the rest of the entries
        for(int i = 1; i < coreChainArray.size(); ++i)
        {
            try {
                JsonObject entry = coreChainArray.get(i).getAsJsonObject();
                CoinEntry temp = MainCoinEntry.parseMain(entry);
                validateNoDuplicateCoins(temp, existingEntries);
                displaySerializer.parseAdditionalFromCoin(temp, entry);
                coreChainTemp.add(temp);
            } catch(JsonSyntaxException | ResourceLocationException e) { throw new JsonSyntaxException("Error parsing core chain entry #" + (i + 1) + " in the " + this.chain + " chain!", e); }
        }
        //Make results immutable
        this.coreChain = ImmutableList.copyOf(coreChainTemp);

        List<List<CoinEntry>> sideChainsTemp = new ArrayList<>();
        JsonArray sideChainsArray = GsonHelper.getAsJsonArray(json, "SideChains", new JsonArray());
        for(int c = 0; c < sideChainsArray.size(); ++c)
        {
            try {
                JsonArray chainArray = sideChainsArray.get(c).getAsJsonArray();
                if(chainArray.size() > 0) //Do not throw error if size is 0 as empty side-chains can simply be ignored.
                {
                    List<CoinEntry> tempList = new ArrayList<>();
                    //Load the first entry manually
                    try {
                        JsonObject baseEntry = chainArray.get(0).getAsJsonObject();
                        CoinEntry temp = SideBaseCoinEntry.parseSub(baseEntry, this.coreChain);
                        validateNoDuplicateCoins(temp, existingEntries);
                        displaySerializer.parseAdditionalFromCoin(temp, baseEntry);
                        tempList.add(temp);
                    } catch (JsonSyntaxException | ResourceLocationException e) { throw new JsonSyntaxException("Error parsing entry #1 in side chain #" + (c + 1) + " in the " + this.chain + " chain!", e); }
                    for(int i = 1; i < chainArray.size(); ++i)
                    {
                        try {
                            JsonObject entry = chainArray.get(i).getAsJsonObject();
                            CoinEntry temp = MainCoinEntry.parseMain(entry, true);
                            validateNoDuplicateCoins(temp, existingEntries);
                            displaySerializer.parseAdditionalFromCoin(temp, entry);
                            tempList.add(temp);
                        } catch (JsonSyntaxException | ResourceLocationException e) { throw new JsonSyntaxException("Error parsing entry #" + (i + 1) + " in side chain #" + (c + 1) + " in the " + this.chain + " chain!", e); }
                    }
                    sideChainsTemp.add(ImmutableList.copyOf(tempList));
                }
            } catch (JsonSyntaxException | ResourceLocationException e) { throw new JsonSyntaxException("Error parsing side chain #" + (c + 1)  + " in the " + this.chain + " chain!", e); }
        }

        this.sideChains = ImmutableList.copyOf(sideChainsTemp);

        this.displayData = displaySerializer.build();
        this.displayData.setParent(this);

        //Load ATM Data
        if(json.has("ATMData"))
            this.atmData = ATMData.parse(GsonHelper.getAsJsonObject(json, "ATMData"), this);
        else
            this.atmData = ATMData.builder(null).build(this);

        this.defineEntryCoreValues();

    }

    private void defineEntryCoreValues()
    {
        long coreValue = 1;
        for(int i = 0; i < this.coreChain.size(); ++i)
        {
            CoinEntry entry = this.coreChain.get(i);
            if(i == 0)
                entry.setCoreValue(coreValue);
            else
            {
                coreValue *= entry.getExchangeRate();
                entry.setCoreValue(coreValue);
            }
        }
        for(List<CoinEntry> sideChain : this.sideChains)
        {
            coreValue = 0;
            for(int i = 0; i < sideChain.size(); ++i)
            {
                CoinEntry entry = sideChain.get(i);
                if(i == 0 && entry instanceof SideBaseCoinEntry e)
                    coreValue = e.parentCoin.getCoreValue();
                coreValue *= entry.getExchangeRate();
                entry.setCoreValue(coreValue);
            }
        }
    }

    public JsonObject getAsJson()
    {
        JsonObject json = new JsonObject();
        //Write base data
        json.addProperty("chain", this.chain);
        json.add("name", Component.Serializer.toJsonTree(this.displayName));
        json.addProperty("displayType", this.displayData.getType().toString());
        this.displayData.getSerializer().writeAdditional(this.displayData, json);
        json.addProperty("InputType", this.inputType.name());

        if(this.isEvent)
            json.addProperty("EventChain",true);

        //Write core chain
        JsonArray coreChainArray = new JsonArray();
        for(CoinEntry entry : this.coreChain)
            coreChainArray.add(entry.serialize(this.displayData));
        json.add("CoreChain", coreChainArray);

        //Write side chains
        if(this.sideChains.size() > 0)
        {
            JsonArray sideChainArray = new JsonArray();
            for(List<CoinEntry> sideChain : this.sideChains)
            {
                JsonArray chainArray = new JsonArray();
                for(CoinEntry entry : sideChain)
                    chainArray.add(entry.serialize(this.displayData));
                sideChainArray.add(chainArray);
            }
            json.add("SideChains", sideChainArray);
        }

        //Write ATM Data
        if(this.atmData.getExchangeButtons().size() > 0)
            json.add("ATMData", this.atmData.save());

        return json;
    }

    public boolean containsEntry(@Nonnull ItemStack item)  { return findEntry(item) != null; }
    public boolean containsEntry(@Nonnull Item item)  { return findEntry(item) != null; }

    @Nullable
    public CoinEntry findMatchingEntry(@Nonnull CoinEntry entry)
    {
        for(CoinEntry e : this.getAllEntries(true))
        {
            if(e.matches(entry))
                return e;
        }
        return null;
    }

    @Nullable
    public CoinEntry findMatchingEntry(@Nonnull CompoundTag entryTag)
    {
        for(CoinEntry e : this.getAllEntries(true))
        {
            if(e.matches(entryTag))
                return e;
        }
        return null;
    }

    @Nullable
    public CoinEntry findEntry(@Nonnull ItemStack item) { return this.findEntry(item.getItem()); }
    public CoinEntry findEntry(@Nonnull Item item)
    {
        for(CoinEntry entry : this.coreChain)
        {
            if(entry.matches(item))
                return entry;
        }
        for(List<CoinEntry> sideChain : this.sideChains)
        {
            for(CoinEntry entry : sideChain)
            {
                if(entry.matches(item))
                    return entry;
            }
        }
        return null;
    }

    @Nonnull
    public List<CoinEntry> getAllEntries(boolean includeSideChains, @Nonnull Comparator<CoinEntry> sorter)
    {
        List<CoinEntry> list = this.getAllEntries(includeSideChains);
        list.sort(sorter);
        return list;
    }

    public List<CoinEntry> getAllEntries(boolean includeSideChains)
    {
        List<CoinEntry> results = new ArrayList<>(this.coreChain);
        if(includeSideChains)
            this.sideChains.forEach(results::addAll);
        return results;
    }

    /**
     * Returns the internal value of the given item stack
     * Ignores the items count when doing this calculation.
     */
    public long getCoreValue(@Nonnull ItemStack item) { return this.getCoreValue(item.getItem()); }
    /**
     * Returns the internal value of the given item
     */
    public long getCoreValue(@Nonnull Item item)
    {
        CoinEntry entry = this.findEntry(item);
        if(entry == null)
            return 0;
        return entry.getCoreValue();
    }

    @Nullable
    public Pair<CoinEntry,Integer> getLowerExchange(@Nonnull Item item)
    {
        CoinEntry entry = this.findEntry(item);
        if(entry == null)
            return null;
        return getLowerExchange(entry);
    }
    @Nullable
    public Pair<CoinEntry,Integer> getLowerExchange(@Nonnull CoinEntry entry)
    {
        for(int i = 0; i < this.coreChain.size(); ++i)
        {
            CoinEntry queryEntry = this.coreChain.get(i);
            if(queryEntry.matches(entry))
            {
                if(i == 0)
                    return null;
                return Pair.of(this.coreChain.get(i - 1), queryEntry.getExchangeRate());
            }
        }
        for(List<CoinEntry> sideChain : this.sideChains)
        {
            for(int i = 0; i < sideChain.size(); ++i)
            {
                CoinEntry queryEntry = sideChain.get(i);
                if(queryEntry.matches(entry))
                {
                    if(i == 0 && queryEntry instanceof SideBaseCoinEntry e)
                        return Pair.of(e.parentCoin, e.getExchangeRate());
                    return Pair.of(sideChain.get(i - 1), queryEntry.getExchangeRate());
                }
            }
        }
        return null;
    }

    @Nullable
    public Pair<CoinEntry,Integer> getUpperExchange(@Nonnull Item item)
    {
        CoinEntry entry = this.findEntry(item);
        if(entry == null)
            return null;
        return getUpperExchange(entry);
    }
    @Nullable
    public Pair<CoinEntry,Integer> getUpperExchange(@Nonnull CoinEntry entry)
    {
        for(int i = 0; i < this.coreChain.size(); ++i)
        {
            CoinEntry queryEntry = this.coreChain.get(i);
            if(queryEntry.matches(entry))
            {
                if(i + 1 >= this.coreChain.size())
                    return null;
                CoinEntry nextEntry = this.coreChain.get(i + 1);
                return Pair.of(nextEntry, nextEntry.getExchangeRate());
            }
        }
        for(List<CoinEntry> sideChain : this.sideChains)
        {
            for(int i = 0; i < sideChain.size(); ++i)
            {
                CoinEntry queryEntry = sideChain.get(i);
                if(queryEntry.matches(entry))
                {
                    if(i + 1 >= sideChain.size())
                        return null;
                    CoinEntry nextEntry = sideChain.get(i + 1);
                    return Pair.of(nextEntry, nextEntry.getExchangeRate());
                }
            }
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    public Object getInputHandler() { return CoinInputTypeHelper.getHandler(this.inputType, this); }

    private static void validateNoDuplicateCoins(@Nonnull CoinEntry newEntry, @Nonnull List<CoinEntry> existingEntries)
    {
        for(CoinEntry entry : existingEntries)
        {
            if(entry.matches(newEntry.getCoin()) || newEntry.matches(entry))
                throw new JsonSyntaxException("Matching coin entry is already present");
        }
        existingEntries.add(newEntry);
    }

    public static Builder builder(@Nonnull String chain) { return new Builder(BuildDefaultMoneyDataEvent.getExistingEntries(), chain, EasyText.translatable("lightmanscurrency.money.chain." + chain)); }
    public static Builder builder(@Nonnull String chain, @Nonnull MutableComponent displayName) { return new Builder(BuildDefaultMoneyDataEvent.getExistingEntries(), chain, displayName); }

    public static ChainData fromJson(@Nonnull List<CoinEntry> existingEntries, @Nonnull JsonObject json) throws JsonSyntaxException, ResourceLocationException { return new ChainData(existingEntries, Objects.requireNonNull(json)); }

    public static class Builder
    {
        private static Builder latest = null;
        public static Builder getLatest() { return latest; }

        public final String chain;
        private final MutableComponent displayName;
        private boolean isEvent = false;
        private ValueDisplayData displayData = Null.INSTANCE;
        private CoinInputType inputType = CoinInputType.DEFAULT;

        private ChainBuilder coreChain = null;
        private final List<ChainBuilder> sideChains = new ArrayList<>();

        private final List<CoinEntry> existingEntries;

        private final ATMData.Builder atmDataBuilder = ATMData.builder(this);

        private void validateNoDuplicateEntries(@Nonnull CoinEntry newEntry) { validateNoDuplicateCoins(newEntry, this.existingEntries); }

        private Builder(@Nonnull List<CoinEntry> existingEntries, @Nonnull String chain, @Nonnull MutableComponent displayName) { this.chain = chain; this.displayName = displayName; this.existingEntries = existingEntries; latest = this; }

        public Builder withDisplay(@Nonnull ValueDisplayData display) { this.displayData = display; return this; }
        public Builder withInputType(@Nonnull CoinInputType inputType) { this.inputType = inputType; return this; }

        public Builder asEvent() { this.isEvent = true; return this; }

        public ChainBuilder withCoreChain(@Nonnull RegistryObject<? extends ItemLike> baseCoin) { return this.withCoreChain(baseCoin.get()); }
        public ChainBuilder withCoreChain(@Nonnull ItemLike baseCoin)
        {
            if(this.coreChain != null)
                throw new IllegalArgumentException("Core Chain has already been built!");
            this.coreChain = new ChainBuilder(this, new CoinEntry(baseCoin.asItem()));
            return this.coreChain;
        }

        public ChainBuilder getCoreChain() { if(this.coreChain == null) throw new IllegalArgumentException("Core Chain has not yet been built!"); return this.coreChain; }

        public ChainBuilder withSideChain(@Nonnull RegistryObject<? extends ItemLike> baseCoin, int exchangeRate, @Nonnull RegistryObject<? extends ItemLike> parentCoin) { return this.withSideChain(baseCoin.get(), exchangeRate, parentCoin.get()); }
        public ChainBuilder withSideChain(@Nonnull ItemLike baseCoin, int exchangeRate, @Nonnull ItemLike parentCoin) {
            if(this.coreChain == null)
                throw new IllegalArgumentException("Cannot build a side chain until the core chain has been built!");

            CoinEntry parentEntry = null;
            for(CoinEntry entry : this.coreChain.entries)
            {
                if(entry.matches(parentCoin.asItem()))
                {
                    parentEntry = entry;
                    break;
                }
            }
            if(parentEntry == null)
                throw new IllegalArgumentException("Coin is not in the core chain!");
            ChainBuilder subChain = new ChainBuilder(this, new SideBaseCoinEntry(baseCoin.asItem(), parentEntry, exchangeRate));
            this.sideChains.add(subChain);
            return subChain;
        }

        public List<ChainBuilder> getSideChains() { return ImmutableList.copyOf(this.sideChains); }

        public ATMData.Builder atmBuilder() { return this.atmDataBuilder; }

        public void apply(@Nonnull BuildDefaultMoneyDataEvent event) { event.addDefault(this);}
        public void apply(@Nonnull BuildDefaultMoneyDataEvent event, boolean allowOverride) { event.addDefault(this, allowOverride);}

        @Nonnull
        public ChainData build() { return new ChainData(this); }

        public static final class ChainBuilder
        {
            private final Builder parent;
            private final List<CoinEntry> entries = new ArrayList<>();

            private ChainBuilder(@Nonnull Builder parent, @Nonnull CoinEntry baseCoin)
            {
                this.parent = parent;
                this.parent.validateNoDuplicateEntries(baseCoin);
                this.entries.add(baseCoin);
            }
            public ChainBuilder withCoin(@Nonnull RegistryObject<? extends ItemLike> coin, int exchangeRate) { return this.withCoin(coin.get(), exchangeRate); }
            public ChainBuilder withCoin(@Nonnull ItemLike coin, int exchangeRate) {
                CoinEntry newEntry = new MainCoinEntry(coin.asItem(), exchangeRate);
                this.parent.validateNoDuplicateEntries(newEntry);
                this.entries.add(newEntry);
                return this;
            }
            public Builder back() { return this.parent; }

            public List<CoinEntry> getEntries() { return ImmutableList.copyOf(this.entries); }
        }

    }

    public static void addCoinTooltips(@Nonnull ItemStack stack, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag, @Nullable Player player)
    {
        ChainData chain = CoinAPI.chainForCoin(stack);
        if(chain != null)
        {
            if(player == null || chain.isVisibleTo(player))
                chain.formatCoinTooltip(stack, tooltip, flag);
        }
    }

}
