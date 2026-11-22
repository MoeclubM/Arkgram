package org.telegram.ui.Components;

public enum IconBackgroundColors {
    SAND(0xFFB18955, 0xFF876230),
    SAND_ALT(0xFFAE8754, 0xFF80592E),
    SAND_DEEP(0xFF9C7546, 0xFF74502A),
    SAND_LIGHT(0xFFB28A54, 0xFF946334),

    ORANGE(0xFFF09F1B, 0xFFE18A11),
    ORANGE_DEEP(0xFFF28B31, 0xFFE26314),

    GREEN(0xFF55CA47, 0xFF27B434),
    RED(0xFFF45255, 0xFFDF3955),
    SAND_GOLD(0xFFB48E57, 0xFF987043),
    PURPLE(0xFFC46EF4, 0xFF9F55DF),
    GRAY(0xFF8699AA, 0xFF6E8397);

    public final int top;
    public final int bottom;

    private IconBackgroundColors(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;
    }
}
