package com.moakiee.ae2lt.logic.craft;

import java.util.List;

import appeng.api.crafting.IPatternDetails;

public interface MatrixPatternCore {
    List<IPatternDetails> getAvailablePatterns();

    default boolean hasPattern(IPatternDetails details) {
        if (details == null) {
            return false;
        }
        for (var pattern : getAvailablePatterns()) {
            if (pattern == details) {
                return true;
            }
        }
        return false;
    }
}
