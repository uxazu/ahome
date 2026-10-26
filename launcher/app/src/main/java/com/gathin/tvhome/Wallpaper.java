package com.gathin.tvhome;


/**
 * Generated wallpapers: the styles and palettes of the Wally / Nyri wallpaper generators, drawn live. Every style is
 * rebuilt from a seed each frame (same seed, same picture) and moved by the time, so one call draws any frame at any
 * size, which the blurred dock band also uses at a few dozen pixels.
 */
final class Wallpaper {
    static final String[] NAMES = {"Терракота", "Шалфей", "Океан", "Пион", "Пыль", "Лагуна", "Полночь", "Лес", "Ретро",
            "Радуга", "Пустыня", "Зефир", "Мята", "Лаванда", "Персик", "Лёд", "Песок", "Кофе", "Олива", "Глина", "Осень",
            "Неон", "Поп", "Тропики", "Конфета", "Ночь", "Слива", "Глубина", "Уголь", "Графит", "Бумага", "Индиго", "Роза"};

    /** The palettes' names in English: the Russian ones above are what a saved choice stores, so they stay as ids. */
    private static final String[] LABELS = {"Terracotta", "Sage", "Ocean", "Peony", "Dust", "Lagoon", "Midnight", "Forest",
            "Retro", "Rainbow", "Desert", "Marshmallow", "Mint", "Lavender", "Peach", "Ice", "Sand", "Coffee", "Olive", "Clay",
            "Autumn", "Neon", "Pop", "Tropics", "Candy", "Night", "Plum", "Deep", "Charcoal", "Graphite", "Paper", "Indigo", "Rose"};

    /** What to show for a palette id: the Russian name in Russian, the English one elsewhere. */
    static String label(String id) {
        if ("ru".equals(java.util.Locale.getDefault().getLanguage())) return id;
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(id)) return LABELS[i];
        return id;
    }

    /** Background first, then the accents. */
    private static final int[][] PAL = {
        {0xFFF3C6A0, 0xFF6B3F24, 0xFFE97A2F, 0xFFD8603B, 0xFFF7D7B8},
        {0xFFA8DEC2, 0xFF2F8F72, 0xFF25483E, 0xFFDDEFE3, 0xFF60B492},
        {0xFF2D5C9A, 0xFF8CC3E4, 0xFF5B95CB, 0xFF1A2C52, 0xFF4579B9},
        {0xFFF2A7D3, 0xFF6A3A6C, 0xFF8E5A99, 0xFFE8A9A2, 0xFFF7C6E0},
        {0xFF6A8CA5, 0xFF4D6E86, 0xFFD88A8D, 0xFFE7CF9C, 0xFF9CC6D4},
        {0xFF1F4F55, 0xFFE05A48, 0xFF8FB9C1, 0xFF1B8A80, 0xFFE8B85E},
        {0xFF265459, 0xFF8FB2B6, 0xFF183E43, 0xFFEDDCC8, 0xFFD7784A},
        {0xFF4E6A5C, 0xFF3B5446, 0xFFC8DDC0, 0xFF82BF66, 0xFFE3B055},
        {0xFF0F5F6E, 0xFFEC7462, 0xFFEFC07C, 0xFFA7D9DC, 0xFF4DB7B8, 0xFFEE9A2E},
        {0xFF071820, 0xFFE3162D, 0xFFD65A1A, 0xFFD88A22, 0xFF3F6B58, 0xFF1F2B55, 0xFF1E6BA6, 0xFF1488B5},
        {0xFFA9D6E8, 0xFFF0B888, 0xFFF08A5E, 0xFF4863AE, 0xFF4F6A48, 0xFFB5CB78, 0xFF97A8EE},
        {0xFFFFF4EC, 0xFFF6C1C7, 0xFFC9B6F2, 0xFFB5E3D8, 0xFFFFD99A},
        {0xFFE9F4EE, 0xFF9ED2C0, 0xFF3E9C84, 0xFFF2A65A},
        {0xFFF1EDFB, 0xFFB9A7E8, 0xFF8A76C8, 0xFFF4C5D8, 0xFFFFE3A3},
        {0xFFFFE8DC, 0xFFFFB997, 0xFFF79A7B, 0xFFB8D8C9, 0xFF6F9C8D},
        {0xFFE6EEF6, 0xFF9CB8DE, 0xFF3A5A8C, 0xFFF08C7A},
        {0xFFF1E4CF, 0xFFC0643E, 0xFF2F4858, 0xFF86A69D, 0xFFE2A55B},
        {0xFF3B2A22, 0xFFD9A77A, 0xFF8C5A3C, 0xFFF1D7B8, 0xFFB86B45},
        {0xFFE9E4D0, 0xFF6B7045, 0xFFA3A56A, 0xFF3F3A2A, 0xFFD08A45},
        {0xFFEAD7C3, 0xFFB5654A, 0xFF7A3E2E, 0xFFDDA27A, 0xFF4E6B63},
        {0xFF2E2420, 0xFFD2692A, 0xFFE8A33D, 0xFF8E3B2E, 0xFFF3E1C4},
        {0xFF0D0B1E, 0xFFFF3D8B, 0xFF7B5CFF, 0xFF00E0C6, 0xFFFFE14D},
        {0xFFFFF3D6, 0xFFFF5A36, 0xFF2451FF, 0xFFFFC21A, 0xFF101010},
        {0xFF003B46, 0xFFFF7A59, 0xFF2EC4B6, 0xFFFFD166, 0xFFEF476F},
        {0xFFFFE0F0, 0xFFFF4FA3, 0xFF7C4DFF, 0xFF00C2FF, 0xFFFFD000},
        {0xFF0B2A4D, 0xFF9FB8EC, 0xFF5B8FF0, 0xFF2B3E66, 0xFF3C4D78},
        {0xFF2A1B35, 0xFFB78CD8, 0xFF5E3D7A, 0xFFF4D8A8, 0xFFF0A3B5},
        {0xFF071A24, 0xFF1D5C63, 0xFF2F8F8A, 0xFF9FD8CB, 0xFFF2B36B},
        {0xFF141414, 0xFF3A3A3A, 0xFF6B6B6B, 0xFFD9D4CC, 0xFFE0634A},
        {0xFF8A8A8A, 0xFF4A4A4A, 0xFF6E6E6E, 0xFFA9A9A9, 0xFFA99F7A},
        {0xFFF4EFE6, 0xFF2B2926, 0xFF9A958E, 0xFFD9D4CC, 0xFF5A5652},
        {0xFF1B2140, 0xFF2E3A73, 0xFF5163B5, 0xFF8FA0E8, 0xFFD6DDFB},
        {0xFFFBE6EA, 0xFFE9A1B0, 0xFFC96079, 0xFF8C3350, 0xFF52182D},
    };

    private Wallpaper() {}

    /** The palette called name (background first), in its dark or light version when asked, or null. */
    static int[] palette(String name, String variant) {
        for (int i = 0; i < NAMES.length; i++) {
            if (!NAMES[i].equals(name)) continue;
            int[] p = PAL[i].clone();
            if ("dark".equals(variant)) return Wally.toned(p, true);
            if ("light".equals(variant)) return Wally.toned(p, false);
            return p;
        }
        return null;
    }

    /** The theme's own colours: the surface behind, the containers as the shapes. */
    static int[] theme() {
        return new int[]{Expressive.SURFACE, Expressive.PRIMARY_CONTAINER, Expressive.SECONDARY_CONTAINER,
                Expressive.TERTIARY_CONTAINER, Expressive.SURFACE_CONTAINER_HIGHEST};
    }
}
