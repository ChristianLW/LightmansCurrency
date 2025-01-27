package io.github.lightman314.lightmanscurrency.common.menus.wallet;

import io.github.lightman314.lightmanscurrency.api.money.value.holder.MoneyContainer;
import io.github.lightman314.lightmanscurrency.api.money.bank.menu.IBankAccountMenu;
import io.github.lightman314.lightmanscurrency.common.core.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

public class WalletBankMenu extends WalletMenuBase implements IBankAccountMenu {

	public static final int BANK_WIDGET_SPACING = 128;

	private final MoneyContainer moneyContainer;
	
	public WalletBankMenu(int windowId, Inventory inventory, int walletStackIndex) {
		
		super(ModMenus.WALLET_BANK.get(), windowId, inventory, walletStackIndex);
		this.addValidator(this::hasBankAccess);

		this.moneyContainer = new MoneyContainer(this.player, this.coinInput);
		
		this.addCoinSlots(BANK_WIDGET_SPACING + 1);
		this.addDummySlots(WalletMenuBase.getMaxWalletSlots());
		
	}

	@Override
	public MoneyContainer getCoinInput() { return this.moneyContainer; }

	@Override
	public boolean isClient() { return this.player.level().isClientSide; }

	@Nonnull
	@Override
	public ItemStack quickMoveStack(@Nonnull Player player, int slot) { return ItemStack.EMPTY; }

	@Override
	protected void onValidationTick(@Nonnull Player player) {
		super.onValidationTick(player);
		this.getBankAccountReference();
	}

	@Override
	public void onDepositOrWithdraw() {
		if(this.getAutoExchange()) //Don't need to saveItem if converting, as the ExchangeCoins function auto-saves.
			this.ExchangeCoints();
		else //Save the wallet contents on bank interaction.
			this.saveWalletContents();
		
	}
	
}
