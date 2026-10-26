package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;

/**
 * Material 3 Expressive mode (theme key "m3_expressive": true). Every colour is a role of the dark scheme generated
 * from one seed colour (theme key "m3_seed", any #RRGGBB; green by default) and is only used with its pair:
 * containers carry their on-colours, surfaces carry on-surface. Elevation is tonal (surface-container levels), not
 * shadows; the focus indicator is a 3dp primary ring; the shape of a focused tile morphs and its container changes
 * to primary-container. Type follows the M3 scale.
 */
final class Expressive {
    static boolean on;

    static final String DEFAULT_SEED = "#4CAF50";

    // Accent roles (dark scheme: tones 80 / 20 / 30 / 90)
    static int PRIMARY, ON_PRIMARY, PRIMARY_CONTAINER, ON_PRIMARY_CONTAINER,
            SECONDARY, SECONDARY_CONTAINER, ON_SECONDARY_CONTAINER,
            TERTIARY, TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER;
    // Surface roles (neutral tones 6 / 90 and the container ladder 4 / 10 / 12 / 17 / 22)
    static int SURFACE, ON_SURFACE, ON_SURFACE_VARIANT,
            SURFACE_CONTAINER_LOWEST, SURFACE_CONTAINER_LOW, SURFACE_CONTAINER,
            SURFACE_CONTAINER_HIGH, SURFACE_CONTAINER_HIGHEST,
            OUTLINE, OUTLINE_VARIANT;

    static {
        build(0xFF4CAF50);   // DEFAULT_SEED; parsed by hand so the class loads in a plain JVM test too
    }

    private Expressive() {}

    /** TextView.setLineHeight needs API 28; on Android 8 the same line height comes from the extra line spacing. */
    static void lineHeight(android.widget.TextView v, int px) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            v.setLineHeight(px);
        } else {
            v.setLineSpacing(Math.max(0, px - v.getPaint().getFontMetricsInt(null)), 1f);
        }
    }

    static void init(Context ctx) {
        on = "true".equals(Config.extra(ctx, "m3_expressive", ""));
        int seed;
        try {
            seed = Color.parseColor(Config.extra(ctx, "m3_seed", DEFAULT_SEED));
        } catch (IllegalArgumentException e) {
            seed = 0xFF4CAF50;
        }
        build(seed);
    }

    private static void build(int seed) {
        float h = TonalPalette.hueOf(seed);
        TonalPalette primary = new TonalPalette(h, 40), secondary = new TonalPalette(h, 16),
                tertiary = new TonalPalette((h + 60) % 360, 28), neutral = new TonalPalette(h, 5),
                variant = new TonalPalette(h, 8);
        PRIMARY = primary.tone(80);
        ON_PRIMARY = primary.tone(20);
        PRIMARY_CONTAINER = primary.tone(30);
        ON_PRIMARY_CONTAINER = primary.tone(90);
        SECONDARY = secondary.tone(80);
        SECONDARY_CONTAINER = secondary.tone(30);
        ON_SECONDARY_CONTAINER = secondary.tone(90);
        TERTIARY = tertiary.tone(80);
        TERTIARY_CONTAINER = tertiary.tone(30);
        ON_TERTIARY_CONTAINER = tertiary.tone(90);
        SURFACE = neutral.tone(6);
        ON_SURFACE = neutral.tone(90);
        ON_SURFACE_VARIANT = variant.tone(80);
        SURFACE_CONTAINER_LOWEST = neutral.tone(4);
        SURFACE_CONTAINER_LOW = neutral.tone(10);
        SURFACE_CONTAINER = neutral.tone(12);
        SURFACE_CONTAINER_HIGH = neutral.tone(17);
        SURFACE_CONTAINER_HIGHEST = neutral.tone(22);
        OUTLINE = variant.tone(60);
        OUTLINE_VARIANT = variant.tone(30);
    }
}
