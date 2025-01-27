package io.github.lightman314.lightmanscurrency.common;

import io.github.lightman314.lightmanscurrency.api.config.ConfigFile;
import io.github.lightman314.lightmanscurrency.api.config.SyncedConfigFile;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.MoneyAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyView;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import io.github.lightman314.lightmanscurrency.common.advancements.date.DateTrigger;
import io.github.lightman314.lightmanscurrency.api.misc.blocks.IOwnableBlock;
import io.github.lightman314.lightmanscurrency.common.capability.CurrencyCapabilities;
import io.github.lightman314.lightmanscurrency.common.capability.event_unlocks.CapabilityEventUnlocks;
import io.github.lightman314.lightmanscurrency.common.capability.event_unlocks.IEventUnlocks;
import io.github.lightman314.lightmanscurrency.common.capability.wallet.IWalletHandler;
import io.github.lightman314.lightmanscurrency.common.capability.wallet.WalletCapability;
import io.github.lightman314.lightmanscurrency.api.events.WalletDropEvent;
import io.github.lightman314.lightmanscurrency.common.gamerule.ModGameRules;
import io.github.lightman314.lightmanscurrency.common.items.WalletItem;
import io.github.lightman314.lightmanscurrency.common.menus.wallet.WalletMenuBase;
import io.github.lightman314.lightmanscurrency.network.LightmansCurrencyPacketHandler;
import io.github.lightman314.lightmanscurrency.network.message.event.SPacketSyncEventUnlocks;
import io.github.lightman314.lightmanscurrency.network.message.wallet.SPacketPlayPickupSound;
import io.github.lightman314.lightmanscurrency.network.message.walletslot.SPacketSyncWallet;

import java.util.ArrayList;
import java.util.List;

import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.PacketTarget;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EventHandler {

	//Pickup Event for wallet coin collection functionality
	@SubscribeEvent
	public static void pickupItem(EntityItemPickupEvent event)
	{
		
		ItemStack pickupItem = event.getItem().getItem();
		if(!CoinAPI.isCoin(pickupItem, false))
			return;
		
		Player player = event.getEntity();
		ItemStack coinStack = event.getItem().getItem();
		WalletMenuBase activeContainer = null;
		
		//Check if the open container is a wallet WalletMenuBase is pickup capable
		if(player.containerMenu instanceof WalletMenuBase container && container.isEquippedWallet())
			activeContainer = container;
		
		boolean cancelEvent = false;
		
		//Get the currently equipped wallet
		ItemStack wallet = CoinAPI.getWalletStack(player);
		if(!wallet.isEmpty())
		{
			WalletItem walletItem = (WalletItem)wallet.getItem();
			if(WalletItem.CanPickup(walletItem))
			{
				cancelEvent = true;
				if(activeContainer != null)
					coinStack = activeContainer.PickupCoins(coinStack);
				else
					coinStack = WalletItem.PickupCoin(wallet, coinStack);
			}
		}
		
		if(event.isCancelable() && cancelEvent)
		{
			//CurrencyMod.LOGGER.info("Canceling the event as the pickup item is being handled by this event instead of vanilla means.");
			event.getItem().setItem(ItemStack.EMPTY);
			if(!coinStack.isEmpty())
				ItemHandlerHelper.giveItemToPlayer(player, coinStack);
			if(!player.level().isClientSide)
				SPacketPlayPickupSound.INSTANCE.sendTo(player);
			event.setCanceled(true);
		}
		
	}

	//Block break event for trader protection functionality.
	//Redundant with the addition of the BreakSpeed event listener, but I'm keeping it in just to be safe.
	@SubscribeEvent
	public static void onBlockBreak(BlockEvent.BreakEvent event)
	{
		
		LevelAccessor level = event.getLevel();
		BlockState state = event.getState();
		
		if(state.getBlock() instanceof IOwnableBlock block)
		{
			if(!block.canBreak(event.getPlayer(), level, event.getPos(), state))
			{
				//CurrencyMod.LOGGER.info("onBlockBreak-Non-owner attempted to break a trader block. Aborting event!");
				event.setCanceled(true);
			}
		}
		
	}

	//Cancel the block break speed events if the block is owned and not breakable by this player.
	@SubscribeEvent
	public static void blockBreakSpeed(PlayerEvent.BreakSpeed event)
	{
		Level level = event.getEntity().level();
		BlockState state = event.getState();

		if(event.getState().getBlock() instanceof IOwnableBlock block)
		{
			event.getPosition().ifPresent(pos -> {
				if(!block.canBreak(event.getEntity(), level, pos, state))
					event.setCanceled(true);
			});
		}
	}
	
	//Adds the wallet capability to the player
	@SubscribeEvent
	public static void attachEntitiesCapabilities(AttachCapabilitiesEvent<Entity> event)
	{
		if(event.getObject() instanceof Player player)
		{
			event.addCapability(CurrencyCapabilities.ID_WALLET, WalletCapability.createProvider(player));
			event.addCapability(CurrencyCapabilities.ID_EVENT_TRACKER, CapabilityEventUnlocks.createProvider(player));
		}

	}
	
	//Sync wallet when the player logs in
	@SubscribeEvent
	public static void playerLogin(PlayerLoggedInEvent event)
	{
		if(event.getEntity().level().isClientSide)
			return;
		sendWalletUpdatePacket(event.getEntity(), LightmansCurrencyPacketHandler.getTarget(event.getEntity()));
		sendEventUpdatePacket(event.getEntity());
		if(event.getEntity() instanceof ServerPlayer player)
			SyncedConfigFile.playerJoined(player);
	}
	
	//Sync wallet contents for newly loaded entities
	@SubscribeEvent
	public static void playerStartTracking(PlayerEvent.StartTracking event)
	{
		Entity target = event.getTarget();
		Player player = event.getEntity();
		sendWalletUpdatePacket(target, LightmansCurrencyPacketHandler.getTarget(player));
	}
	
	//Copy the wallet over to the new player
	@SubscribeEvent
	public static void playerClone(PlayerEvent.Clone event)
	{
		Player player = event.getEntity();
		if(player.level().isClientSide) //Do nothing client-side
			return;
		
		Player oldPlayer = event.getOriginal();
		oldPlayer.revive();
		IWalletHandler oldHandler = WalletCapability.lazyGetWalletHandler(oldPlayer);
		IWalletHandler newHandler = WalletCapability.lazyGetWalletHandler(event.getEntity());

		if (oldHandler != null && newHandler != null) {
			newHandler.setWallet(oldHandler.getWallet());
			newHandler.setVisible(oldHandler.visible());
		}

		IEventUnlocks oldEventHandler = CapabilityEventUnlocks.getCapability(oldPlayer);
		IEventUnlocks newEventHandler = CapabilityEventUnlocks.getCapability(event.getEntity());
		if(oldEventHandler != null && newEventHandler != null)
			newEventHandler.sync(oldEventHandler.getUnlockedList());

		//Invalidate the capabilities now that the reason is no longer needed
		oldPlayer.invalidateCaps();
		
	}
	
	@SubscribeEvent
	public static void playerChangedDimensions(PlayerEvent.PlayerChangedDimensionEvent event) {
		Player player = event.getEntity();
		if(player.level().isClientSide)
			return;
		sendWalletUpdatePacket(player, LightmansCurrencyPacketHandler.getTarget(player));
		sendEventUpdatePacket(player);
	}
	
	private static void sendWalletUpdatePacket(Entity entity, PacketTarget target) {
		if(entity.level().isClientSide)
			return;
		IWalletHandler walletHandler = WalletCapability.lazyGetWalletHandler(entity);
		if(walletHandler != null)
			new SPacketSyncWallet(entity.getId(), walletHandler.getWallet(), walletHandler.visible()).sendToTarget(target);
	}

	private static void sendEventUpdatePacket(Player player)
	{
		if(player.level().isClientSide)
			return;
		IEventUnlocks eventUnlocks = CapabilityEventUnlocks.getCapability(player);
		if(eventUnlocks != null)
			new SPacketSyncEventUnlocks(eventUnlocks.getUnlockedList()).sendTo(player);
	}
	
	//Drop the wallet if keep inventory isn't on.
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void playerDrops(LivingDropsEvent event)
	{
		LivingEntity livingEntity = event.getEntity();
		if(livingEntity.level().isClientSide) //Do nothing client side
			return;
		
		if(!livingEntity.isSpectator())
		{

			IWalletHandler walletHandler = WalletCapability.lazyGetWalletHandler(livingEntity);
			if(walletHandler != null)
			{
				ItemStack walletStack = walletHandler.getWallet();

				if(walletStack.isEmpty())
					return;

				List<ItemStack> drops = new ArrayList<>();
				if(livingEntity instanceof Player player) //Only worry about gamerules on players. Otherwise, it always drops the wallet.
				{

					boolean keepWallet = ModGameRules.safeGetCustomBool(player.level(), ModGameRules.KEEP_WALLET, false);
					//If curios isn't also installed, assume keep inventory will also enforce the keepWallet rule
					if(!LightmansCurrency.isCuriosValid(player) && player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))
						keepWallet = true;

					int coinDropPercent = ModGameRules.safeGetCustomInt(player.level(), ModGameRules.COIN_DROP_PERCENT, 0);

					//Get the wallet inventory
					Container walletInventory = WalletItem.getWalletInventory(walletStack);

					//Post the Wallet Drop Event
					WalletDropEvent wde = new WalletDropEvent(player, walletHandler, walletInventory, event.getSource(), keepWallet, coinDropPercent);
					if(MinecraftForge.EVENT_BUS.post(wde))
						return;

					drops = wde.getDrops();
					walletHandler.setWallet(wde.getWalletStack());

				}
				else
				{
					drops.add(walletStack);
					walletHandler.setWallet(ItemStack.EMPTY);
				}

				event.getDrops().addAll(turnIntoEntities(livingEntity, drops));
			}
			
		}
	}

	private static List<ItemEntity> turnIntoEntities(@Nonnull LivingEntity entity, @Nonnull List<ItemStack> list)
	{
		if(list == null)
			return new ArrayList<>();
		List<ItemEntity> result = new ArrayList<>();
		for(ItemStack stack : list)
			result.add(new ItemEntity(entity.level(), entity.position().x, entity.position().y, entity.position().z, stack));
		return result;
	}

	//Call at low priority so that other mods intercept the event before my behaviour by default
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onWalletDrop(@Nonnull WalletDropEvent event)
	{
		if(event.keepWallet) //Keep the wallet, but drop the wallets contents
		{
			//Spawn the coin drops
			event.addDrops(getWalletDrops(event, event.coinDropPercent));
		}
		//Drop the wallet (unless curios is installed, upon which curios will handle that)
		else if(!LightmansCurrency.isCuriosValid(event.getEntity()))
		{
			event.addDrop(event.getWalletStack());
			event.setWalletStack(ItemStack.EMPTY);
		}
	}

	private static List<ItemStack> getWalletDrops(@Nonnull WalletDropEvent event, int coinDropPercent)
	{
		if(coinDropPercent <= 0)
			return new ArrayList<>();

		Container walletInventory = event.getWalletInventory();
		MoneyView walletFunds = MoneyAPI.valueOfContainer(event.getWalletInventory());

		List<ItemStack> drops = new ArrayList<>();
		//Remove the dropped coins from the wallet

		for(MoneyValue value : walletFunds.allValues())
		{
			if(value instanceof CoinValue)
			{
				MoneyValue takeAmount = value.percentageOfValue(coinDropPercent);
				if(takeAmount.isEmpty())
					continue;

				if(takeAmount instanceof CoinValue coinsToDrop && MoneyAPI.takeMoneyFromContainer(walletInventory, drops::add, takeAmount))
				{
					drops.addAll(coinsToDrop.getAsSeperatedItemList());
				}
			}
		}
		
		return drops;
		
	}

	//Check for wallet updates, and send update packets
	@SubscribeEvent
	public static void entityTick(LivingEvent.LivingTickEvent event) {
		LivingEntity livingEntity = event.getEntity();
		if(livingEntity.level().isClientSide) //Do nothing client side
			return;

		IWalletHandler walletHandler = WalletCapability.lazyGetWalletHandler(livingEntity);
		if(walletHandler != null)
		{
			walletHandler.tick();
			if(walletHandler.isDirty())
			{
				LightmansCurrencyPacketHandler.instance.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> livingEntity), new SPacketSyncWallet(livingEntity.getId(), walletHandler.getWallet(), walletHandler.visible()));
				walletHandler.clean();
			}
		}
		if(livingEntity instanceof Player player)
		{
			IEventUnlocks eventHandler = CapabilityEventUnlocks.getCapability(player);
			if(eventHandler != null && eventHandler.isDirty())
			{
				sendEventUpdatePacket(player);
				eventHandler.clean();
			}
		}
	}

	@SubscribeEvent
	public static void serverStart(ServerStartedEvent event) { ConfigFile.reloadServerFiles(); }

	@SubscribeEvent
	public static void serverTick(TickEvent.ServerTickEvent event)
	{
		if(event.haveTime())
		{
			for(ServerPlayer player : event.getServer().getPlayerList().getPlayers())
				DateTrigger.INSTANCE.trigger(player);
		}
	}
	
}
