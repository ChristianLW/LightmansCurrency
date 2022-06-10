package io.github.lightman314.lightmanscurrency.integration.jeiplugin;

import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.core.ModBlocks;
import io.github.lightman314.lightmanscurrency.crafting.CoinMintRecipe;
import io.github.lightman314.lightmanscurrency.crafting.RecipeValidator;
import io.github.lightman314.lightmanscurrency.crafting.RecipeValidator.Results;
import io.github.lightman314.lightmanscurrency.menus.MintMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class LCJeiPlugin implements IModPlugin{

	public static final ResourceLocation COIN_MINT_UID = new ResourceLocation(LightmansCurrency.MODID, "coin_mint");
	public static final RecipeType<CoinMintRecipe> COIN_MINT_TYPE = RecipeType.create(COIN_MINT_UID.getNamespace(), COIN_MINT_UID.getPath(), CoinMintRecipe.class);
	
	@Override
	public ResourceLocation getPluginUid() { return new ResourceLocation(LightmansCurrency.MODID, LightmansCurrency.MODID); }

	@Override
	public void registerCategories(IRecipeCategoryRegistration registry)
	{
		IGuiHelper guiHelper = registry.getJeiHelpers().getGuiHelper();
		registry.addRecipeCategories(new CoinMintCategory(guiHelper));
	}
	
	@Override
	@SuppressWarnings("resource")
	public void registerRecipes(IRecipeRegistration registration)
	{
		Results recipes = RecipeValidator.getValidRecipes(Minecraft.getInstance().level);
		registration.addRecipes(COIN_MINT_TYPE, recipes.getCoinMintRecipes());
	}
	
	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration)
	{
		
	}
	
	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration)
	{
		registration.addRecipeCatalyst(new ItemStack(ModBlocks.MACHINE_MINT.get()), COIN_MINT_TYPE);
	}
	
	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration)
	{
		registration.addRecipeTransferHandler(MintMenu.class, COIN_MINT_TYPE, 0, 1, 2, 36);
	}
	
}
