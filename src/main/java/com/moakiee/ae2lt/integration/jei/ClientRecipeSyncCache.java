package com.moakiee.ae2lt.integration.jei;

import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class ClientRecipeSyncCache {
    private static RecipeMap recipeMap = RecipeMap.EMPTY;

    private ClientRecipeSyncCache() {
    }

    @SubscribeEvent
    public static void onRecipesReceived(RecipesReceivedEvent event) {
        recipeMap = event.getRecipeMap();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        recipeMap = RecipeMap.EMPTY;
    }

    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getRecipes(RecipeType<T> recipeType) {
        return recipeMap.byType(recipeType).stream()
                .toList();
    }
}
