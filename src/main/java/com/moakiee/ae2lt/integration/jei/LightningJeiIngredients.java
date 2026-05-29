package com.moakiee.ae2lt.integration.jei;

import java.util.List;

import com.moakiee.ae2lt.me.key.LightningKey;

import mezz.jei.api.ingredients.IIngredientType;

public final class LightningJeiIngredients {
    public static final IIngredientType<LightningKey> TYPE = new IIngredientType<>() {
        @Override
        public Class<? extends LightningKey> getIngredientClass() {
            return LightningKey.class;
        }
    };

    public static final List<LightningKey> INGREDIENTS = List.of();
    public static final LightningJeiIngredientHelper HELPER = new LightningJeiIngredientHelper();
    public static final LightningJeiIngredientRenderer RENDERER = new LightningJeiIngredientRenderer();

    private LightningJeiIngredients() {
    }
}
