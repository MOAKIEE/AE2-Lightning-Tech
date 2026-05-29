package com.moakiee.ae2lt.integration.jei.category;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.BuddingOverloadCrystalBlock;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

import appeng.core.definitions.AEBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

public class OverloadGrowthCategory implements IRecipeCategory<OverloadGrowthCategory.Page> {
    public static final RecipeType<Page> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "overload_growth", Page.class);

    private static final int WIDTH = 150;
    private static final int HEIGHT = 60;
    private static final int BODY_COLOR = 0xFF404040;
    private static final int CENTER_X = WIDTH / 2;

    private static final ResourceLocation RECIPE_TEXTURE =
            new ResourceLocation("jei", "textures/gui/gui.png");
    private static final int ARROW_U = 82;
    private static final int ARROW_V = 128;
    private static final int ARROW_W = 24;
    private static final int ARROW_H = 16;
    private static final long ARROW_CYCLE_MS = 250L;

    private final IDrawable background;
    private final IDrawable icon;

    private final List<ItemStack> buddingOverloadVariants = List.of(
            new ItemStack(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL.get()));

    private final List<ItemStack> imperfectBuddingOverloadVariants = List.of(
            new ItemStack(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get()));

    private final List<ItemStack> buddingOverloadDecayOrder = List.of(
            new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get()),
            new ItemStack(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get()));

    private final List<ItemStack> imperfectBuddingOverloadDecayOrder = List.of(
            new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get()),
            new ItemStack(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()));

    private final List<ItemStack> budGrowthStages = List.of(
            new ItemStack(ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD.get()),
            new ItemStack(ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD.get()),
            new ItemStack(ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD.get()),
            new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get()));

    public OverloadGrowthCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get()));
    }

    @Override
    public RecipeType<Page> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2lt.overload_growth.title");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Page recipe, IFocusGroup focuses) {
        getView(recipe).buildSlots(builder);
    }

    @Override
    public void draw(
            Page page,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        getView(page).draw(guiGraphics);
    }

    private View getView(Page page) {
        return switch (page) {
            case BUD_GROWTH -> new BudGrowthView();
            case BUD_LOOT, CLUSTER_LOOT -> new LootView(page);
            case BUDDING_OVERLOAD_DECAY -> new BuddingOverloadDecayView();
            case BUDDING_OVERLOAD_MOVING -> new BuddingOverloadMovingView();
            case BUDDING_OVERLOAD_ACCELERATION -> new BuddingOverloadAccelerationView();
        };
    }

    public enum Page {
        BUD_GROWTH,
        BUD_LOOT,
        CLUSTER_LOOT,
        BUDDING_OVERLOAD_DECAY,
        BUDDING_OVERLOAD_MOVING,
        BUDDING_OVERLOAD_ACCELERATION
    }

    private interface View {
        default void buildSlots(IRecipeLayoutBuilder builder) {
        }

        default void draw(GuiGraphics guiGraphics) {
        }
    }

    private void drawRecipeArrow(GuiGraphics guiGraphics, int x, int y) {
        long elapsed = net.minecraft.Util.getMillis() % ARROW_CYCLE_MS;
        double progress = elapsed / (double) ARROW_CYCLE_MS;
        int fillW = Mth.clamp((int) Math.ceil(progress * ARROW_W), 0, ARROW_W);
        if (fillW <= 0) {
            return;
        }
        guiGraphics.blit(
                RECIPE_TEXTURE,
                x, y,
                ARROW_U, ARROW_V,
                fillW, ARROW_H,
                256, 256);
    }

    private class BudGrowthView implements View {
        @Override
        public void draw(GuiGraphics guiGraphics) {
            var font = Minecraft.getInstance().font;
            var text = Component.translatable("jei.ae2lt.overload_growth.bud_growth");
            int textX = (WIDTH - font.width(text)) / 2;
            guiGraphics.drawString(font, text, textX, 8, BODY_COLOR, false);
            drawRecipeArrow(guiGraphics, CENTER_X - 12, 25);
        }

        @Override
        public void buildSlots(IRecipeLayoutBuilder builder) {
            builder.addSlot(RecipeIngredientRole.CATALYST, CENTER_X - 40, 25)
                    .addItemStacks(buddingOverloadVariants);

            builder.addSlot(RecipeIngredientRole.OUTPUT, CENTER_X + 22, 25)
                    .addItemStacks(budGrowthStages);
        }
    }

    private class LootView implements View {
        private final Page page;

        private LootView(Page page) {
            this.page = page;
        }

        @Override
        public void draw(GuiGraphics guiGraphics) {
            var font = Minecraft.getInstance().font;
            var key = page == Page.BUD_LOOT
                    ? "jei.ae2lt.overload_growth.bud_loot"
                    : "jei.ae2lt.overload_growth.cluster_loot";
            var text = Component.translatable(key);
            int textX = (WIDTH - font.width(text)) / 2;
            guiGraphics.drawString(font, text, textX, 8, BODY_COLOR, false);
            drawRecipeArrow(guiGraphics, CENTER_X - 12, 25);

            if (page == Page.CLUSTER_LOOT) {
                var fortuneText = Component.translatable("jei.ae2lt.overload_growth.cluster_loot_fortune");
                int fortuneX = (WIDTH - font.width(fortuneText)) / 2;
                guiGraphics.drawString(font, fortuneText, fortuneX, 50, BODY_COLOR, false);
            }
        }

        @Override
        public void buildSlots(IRecipeLayoutBuilder builder) {
            if (page == Page.BUD_LOOT) {
                builder.addSlot(RecipeIngredientRole.INPUT, CENTER_X - 40, 25)
                        .addItemStacks(List.of(
                                new ItemStack(ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD.get()),
                                new ItemStack(ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD.get()),
                                new ItemStack(ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD.get())));

                builder.addSlot(RecipeIngredientRole.OUTPUT, CENTER_X + 22, 25)
                        .addItemStack(new ItemStack(ModItems.OVERLOAD_CRYSTAL_DUST.get()));
            } else {
                builder.addSlot(RecipeIngredientRole.INPUT, CENTER_X - 40, 25)
                        .addItemStack(new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get()));

                builder.addSlot(RecipeIngredientRole.OUTPUT, CENTER_X + 22, 25)
                        .addItemStack(new ItemStack(ModItems.OVERLOAD_CRYSTAL.get(), 4));
            }
        }
    }

    private class BuddingOverloadDecayView implements View {
        @Override
        public void draw(GuiGraphics guiGraphics) {
            var font = Minecraft.getInstance().font;
            var text = Component.translatable("jei.ae2lt.overload_growth.decay");
            int textX = (WIDTH - font.width(text)) / 2;
            guiGraphics.drawString(font, text, textX, 8, BODY_COLOR, false);
            drawRecipeArrow(guiGraphics, CENTER_X - 12, 30);

            int decayChancePct = 100 / BuddingOverloadCrystalBlock.DECAY_CHANCE;
            var chanceText = Component.translatable(
                    "jei.ae2lt.overload_growth.decay_chance", decayChancePct);
            int chanceX = (WIDTH - font.width(chanceText)) / 2;
            guiGraphics.drawString(font, chanceText, chanceX, 50, BODY_COLOR, false);
        }

        @Override
        public void buildSlots(IRecipeLayoutBuilder builder) {
            var input = builder.addSlot(RecipeIngredientRole.INPUT, CENTER_X - 40, 30)
                    .addItemStacks(imperfectBuddingOverloadVariants);

            var output = builder.addSlot(RecipeIngredientRole.OUTPUT, CENTER_X + 22, 30)
                    .addItemStacks(imperfectBuddingOverloadDecayOrder);

            builder.createFocusLink(input, output);
        }
    }

    private class BuddingOverloadMovingView implements View {
        @Override
        public void draw(GuiGraphics guiGraphics) {
            drawRecipeArrow(guiGraphics, CENTER_X - 12, 0);

            var font = Minecraft.getInstance().font;
            var lines = List.of(
                    Component.translatable("jei.ae2lt.overload_growth.break_decay"),
                    Component.translatable("jei.ae2lt.overload_growth.silk_touch"),
                    Component.translatable("jei.ae2lt.overload_growth.flawless_note"));
            int y = 20;
            for (var line : lines) {
                int x = (WIDTH - font.width(line)) / 2;
                guiGraphics.drawString(font, line, x, y, BODY_COLOR, false);
                y += 12;
            }
        }

        @Override
        public void buildSlots(IRecipeLayoutBuilder builder) {
            var input = builder.addSlot(RecipeIngredientRole.INPUT, CENTER_X - 40, 0)
                    .addItemStacks(buddingOverloadVariants);

            var output = builder.addSlot(RecipeIngredientRole.OUTPUT, CENTER_X + 22, 0)
                    .addItemStacks(buddingOverloadDecayOrder);

            builder.createFocusLink(input, output);
        }
    }

    private class BuddingOverloadAccelerationView implements View {
        @Override
        public void draw(GuiGraphics guiGraphics) {
            var font = Minecraft.getInstance().font;
            var text = Component.translatable("jei.ae2lt.overload_growth.acceleration");
            int textX = (WIDTH - font.width(text)) / 2;
            guiGraphics.drawString(font, text, textX, 8, BODY_COLOR, false);

            var plusText = Component.literal("+");
            int plusX = (WIDTH - font.width(plusText)) / 2;
            guiGraphics.drawString(font, plusText, plusX, 40, 0xFFFFFFFF, true);
        }

        @Override
        public void buildSlots(IRecipeLayoutBuilder builder) {
            builder.addSlot(RecipeIngredientRole.INPUT, CENTER_X - 24, 40)
                    .addItemStacks(buddingOverloadVariants);

            builder.addSlot(RecipeIngredientRole.CATALYST, CENTER_X + 8, 40)
                    .addItemStack(AEBlocks.GROWTH_ACCELERATOR.stack());
        }
    }
}
