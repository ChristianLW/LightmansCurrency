package io.github.lightman314.lightmanscurrency.mixin;

import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.MoneyAPI;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.ChainData;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyView;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.common.capability.wallet.IWalletHandler;
import io.github.lightman314.lightmanscurrency.common.capability.wallet.WalletCapability;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import io.github.lightman314.lightmanscurrency.common.items.WalletItem;
import io.github.lightman314.lightmanscurrency.common.menus.wallet.WalletMenu;
import io.github.lightman314.lightmanscurrency.util.MathUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {

    @Unique
    protected MerchantMenu self() { return (MerchantMenu)(Object)this; }

    @Accessor("trader")
    public abstract Merchant getTrader();
    @Accessor("tradeContainer")
    public abstract MerchantContainer getTradeContainer();
    public Player getPlayer() { Merchant m = this.getTrader(); if(m != null) return m.getTradingPlayer(); return null; }

    @Inject(at = @At("HEAD"), method = "tryMoveItems")
    private void tryMoveItemsEarly(int trade, CallbackInfo info)
    {
        //Clear coin items into the wallet instead of their inventory
        try {
            MerchantMenu self = this.self();
            if(trade >= 0 && trade < self.getOffers().size())
                this.EjectMoneyIntoWallet(this.getPlayer(), false);
        } catch (Throwable ignored) {}
    }

    @Inject(at = @At("TAIL"), method = "tryMoveItems")
    private void tryMoveItems(int trade, CallbackInfo info)
    {
        try {
            MerchantMenu self = this.self();
            if(trade >= 0 && trade < self.getOffers().size())
            {
                MerchantContainer tradeContainer = this.getTradeContainer();
                if(tradeContainer.getItem(0).isEmpty() && tradeContainer.getItem(1).isEmpty())
                {
                    MerchantOffer offer = self.getOffers().get(trade);
                    if(CoinAPI.isCoin(offer.getCostA(), false) && isCoinOrEmpty(offer.getCostB()))
                    {
                        ItemStack coinA = offer.getCostA().copy();
                        ItemStack coinB = offer.getCostB().copy();

                        ChainData chainA = CoinAPI.chainForCoin(coinA);
                        ChainData chainB = CoinAPI.chainForCoin(coinB);

                        long valueA = chainA.getCoreValue(coinA);
                        long valueB = 0;
                        if(chainB != null)
                            valueB = chainB.getCoreValue(coinB);

                        //LightmansCurrency.LogDebug("Coin Value of the selected trade is " + tradeValue.getString());
                        Player player = this.getPlayer();

                        MoneyView availableFunds = WalletCapability.getWalletMoney(player);

                        MoneyValue fundsToExtractA = MoneyValue.empty();
                        MoneyValue fundsToExtractB = MoneyValue.empty();
                        int coinACount = coinA.getCount();
                        int coinBCount = coinB.isEmpty() ? 0 : coinB.getCount();
                        int coinAMaxCount = coinA.getMaxStackSize();
                        int coinBMaxCount = coinB.isEmpty() ? 0 : coinB.getMaxStackSize();
                        int coinToAddA = 0;
                        int coinToAddB = 0;

                        for(boolean keepLooping = true; keepLooping;)
                        {
                            int tempC2AA = coinAMaxCount > coinToAddA ? MathUtil.clamp(coinToAddA + coinACount, 0, coinAMaxCount) : coinToAddA;
                            int tempC2AB = coinBMaxCount > coinToAddB ? MathUtil.clamp(coinToAddB + coinBCount, 0, coinBMaxCount) : coinToAddB;

                            coinA.setCount(tempC2AA);
                            coinB.setCount(tempC2AB);

                            if(!containsValueFor(availableFunds, chainA, valueA, tempC2AA, chainB, valueB, tempC2AB))
                                keepLooping = false;
                            else
                            {
                                fundsToExtractA = CoinValue.fromNumber(chainA.chain, valueA * tempC2AA);
                                if(chainB != null)
                                    fundsToExtractB = CoinValue.fromNumber(chainB.chain, valueB * tempC2AB);
                                coinToAddA = tempC2AA;
                                coinToAddB = tempC2AB;
                                if(coinToAddA >= coinAMaxCount && coinToAddB >= coinBMaxCount)
                                    keepLooping = false;
                            }
                        }

                        if((coinToAddA > 0 || coinToAddB > 0) && !fundsToExtractA.isEmpty())
                        {
                            coinA.setCount(coinToAddA);
                            coinB.setCount(coinToAddB);
                            if(MoneyAPI.takeMoneyFromPlayer(player, fundsToExtractA))
                            {
                                if(!MoneyAPI.takeMoneyFromPlayer(player, fundsToExtractB)) {
                                    MoneyAPI.giveMoneyToPlayer(player, fundsToExtractA);
                                    return;
                                }
                                tradeContainer.setItem(0, coinA.copy());
                                tradeContainer.setItem(1, coinB.copy());
                                LightmansCurrency.LogDebug("Moved " + fundsToExtractA.getString() + " & " + fundsToExtractB.getString() + " worth of coins into the Merchant Menu!");
                            }
                        }
                    }
                }
            }
        } catch(Throwable ignored) {}
    }

    private static boolean containsValueFor(@Nonnull MoneyView query, @Nonnull ChainData chainA, long valueA, int countA, @Nullable ChainData chainB, long valueB, int countB)
    {
        MoneyValue cvA = CoinValue.fromNumber(chainA.chain, valueA * countA);
        MoneyValue cvB = chainB == null ? MoneyValue.empty() : CoinValue.fromNumber(chainB.chain, valueB * countB);
        if(cvA.sameType(cvB))
        {
            cvA = cvA.addValue(cvB);
            cvB = CoinValue.empty();
        }
        return query.containsValue(cvA) && query.containsValue(cvB);
    }

    @Inject(at = @At("HEAD"), method = "removed")
    private void removed(Player player, CallbackInfo info) {
        if(this.isPlayerAliveAndValid(player))
            this.EjectMoneyIntoWallet(player, true);
    }

    protected boolean isPlayerAliveAndValid(Player player)
    {
        if(player.isAlive())
        {
            if(player instanceof ServerPlayer sp)
                return !sp.hasDisconnected();
            return true;
        }
        return false;
    }

    private void EjectMoneyIntoWallet(Player player, boolean noUpdate)
    {
        MerchantContainer tradeContainer = this.getTradeContainer();
        ItemStack item = tradeContainer.getItem(0);
        if (!item.isEmpty() && CoinAPI.isCoin(item, false)) {
            IWalletHandler walletHandler = WalletCapability.lazyGetWalletHandler(player);
            if(walletHandler != null)
            {
                ItemStack wallet = walletHandler.getWallet();
                if(WalletItem.isWallet(wallet))
                {
                    ItemStack leftovers = WalletItem.PickupCoin(wallet, item);
                    //Shouldn't be needed as the player *should* be in the MerchantMenu at this point, but I'm leaving it here just to be safe.
                    WalletMenu.OnWalletUpdated(player);
                    if(!leftovers.isEmpty())
                        ItemHandlerHelper.giveItemToPlayer(player, leftovers);
                    if(noUpdate)
                        tradeContainer.removeItemNoUpdate(0);
                    else
                        tradeContainer.setItem(0, ItemStack.EMPTY);
                }
            }
        }
        item = tradeContainer.getItem(1);
        if (!item.isEmpty() && CoinAPI.isCoin(item, false)) {
            IWalletHandler walletHandler = WalletCapability.lazyGetWalletHandler(player);
            if(walletHandler != null)
            {
                ItemStack wallet = walletHandler.getWallet();
                if(WalletItem.isWallet(wallet))
                {
                    ItemStack leftovers = WalletItem.PickupCoin(wallet, item);
                    WalletMenu.OnWalletUpdated(player);
                    if(!leftovers.isEmpty())
                        ItemHandlerHelper.giveItemToPlayer(player, leftovers);
                    if(noUpdate)
                        tradeContainer.removeItemNoUpdate(0);
                    else
                        tradeContainer.setItem(0, ItemStack.EMPTY);
                }
            }
        }
    }

    private static boolean isCoinOrEmpty(ItemStack item) { return item.isEmpty() || CoinAPI.isCoin(item, false); }

}
