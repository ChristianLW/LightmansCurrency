package io.github.lightman314.lightmanscurrency.datagen.common.crafting.builders;

import com.google.gson.JsonObject;
import io.github.lightman314.lightmanscurrency.common.core.ModItems;
import io.github.lightman314.lightmanscurrency.common.core.ModRecipes;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.RequirementsStrategy;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class TicketRecipeBuilder implements RecipeBuilder {

    private final Ingredient ingredient;
    private final Item result;

    private final Advancement.Builder advancement = Advancement.Builder.recipeAdvancement();

    private TicketRecipeBuilder(@Nonnull Ingredient ingredient, @Nonnull ItemLike result) { this.ingredient = ingredient; this.result = result.asItem(); }

    public static TicketRecipeBuilder of(@Nonnull TagKey<Item> tag, @Nonnull ItemLike result) { return of(Ingredient.of(tag), result); }
    public static TicketRecipeBuilder of(@Nonnull ItemLike item, @Nonnull ItemLike result) { return of(Ingredient.of(item), result); }
    public static TicketRecipeBuilder of(@Nonnull Ingredient ingredient, @Nonnull ItemLike result) { return new TicketRecipeBuilder(ingredient, result); }

    @Nonnull
    @Override
    public TicketRecipeBuilder unlockedBy(@Nonnull String name, @Nonnull CriterionTriggerInstance criteria) { this.advancement.addCriterion(name, criteria); return this; }

    @Nonnull
    @Override
    public TicketRecipeBuilder group(@Nullable String group) { return this; }

    @Nonnull
    @Override
    public Item getResult() { return ModItems.TICKET_MASTER.get(); }

    @Override
    public void save(@Nonnull Consumer<FinishedRecipe> consumer, @Nonnull ResourceLocation id) {
        this.ensureValid(id);
        this.advancement.parent(ROOT_RECIPE_ADVANCEMENT).addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id)).rewards(AdvancementRewards.Builder.recipe(id)).requirements(RequirementsStrategy.OR);
        consumer.accept(new Result(id, this.ingredient, this.result, this.advancement, id.withPrefix("recipes/ticket_machine/")));
    }

    private void ensureValid(ResourceLocation id) {
        if (this.advancement.getCriteria().isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + id);
        }
        if(this.ingredient == null)
            throw new IllegalStateException("No ingredient defined for " + id);
    }

    public static class Result implements FinishedRecipe
    {

        private final ResourceLocation id;
        private final Ingredient ingredient;
        private final Item result;
        private final Advancement.Builder advancement;
        private final ResourceLocation advancementId;

        public Result(@Nonnull ResourceLocation id, @Nonnull Ingredient ingredient, @Nonnull Item result, @Nonnull Advancement.Builder advancement, @Nonnull ResourceLocation advancementId)
        {
            this.id = id;
            this.ingredient = ingredient;
            this.result = result;
            this.advancement = advancement;
            this.advancementId = advancementId;
        }

        @Override
        public void serializeRecipeData(@Nonnull JsonObject json) {
            json.add("ingredient", this.ingredient.toJson());
            json.addProperty("result", ForgeRegistries.ITEMS.getKey(this.result).toString());
        }

        @Nonnull
        @Override
        public ResourceLocation getId() { return this.id; }

        @Nonnull
        @Override
        public RecipeSerializer<?> getType() { return ModRecipes.TICKET.get(); }

        @Nullable
        @Override
        public JsonObject serializeAdvancement() { return this.advancement.serializeToJson(); }
        @Nullable
        @Override
        public ResourceLocation getAdvancementId() { return this.advancementId; }

    }

}