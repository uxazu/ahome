package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Themes kept on the TV in files/themes/&lt;name&gt;/ (a config.json and optional wallpaper.jpg or wallpapers/),
 * so one can be applied from Launcher settings without a computer. Applying copies it over the files the launcher
 * already reads, as tools/theme.sh does, and bumps theme_revision so on-TV choices follow the new theme.
 */
final class ThemeLibrary {
    static final String FOLDER = "themes";

    private ThemeLibrary() {}

    static List<String> names(File dir) {
        List<String> out = new ArrayList<>();
        File[] kids = dir == null ? null : new File(dir, FOLDER).listFiles();
        if (kids == null) return out;
        for (File k : kids) if (k.isDirectory() && new File(k, "config.json").isFile()) out.add(k.getName());
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Copies the theme over the live files; false when something could not be written. */
    static boolean apply(File dir, String name) {
        File src = new File(new File(dir, FOLDER), name);
        if (!new File(src, "config.json").isFile()) return false;
        try {
            copy(new File(src, "config.json"), new File(dir, "config.json"));
            File wp = new File(src, Config.WALLPAPER_NAME);
            File liveWp = new File(dir, Config.WALLPAPER_NAME);
            if (wp.isFile()) copy(wp, liveWp); else liveWp.delete();
            File font = new File(src, "font.ttf");
            if (font.isFile()) copy(font, new File(dir, "font.ttf")); else new File(dir, "font.ttf").delete();
            File liveDir = new File(dir, "wallpapers");
            File[] old = liveDir.listFiles();
            if (old != null) for (File f : old) if (f.isFile()) f.delete();
            File[] pics = new File(src, "wallpapers").listFiles();
            if (pics != null) {
                liveDir.mkdirs();
                for (File f : pics) if (f.isFile()) copy(f, new File(liveDir, f.getName()));
            }
            try (OutputStream o = new FileOutputStream(new File(dir, "theme_revision"))) {
                o.write(UUID.randomUUID().toString().getBytes("UTF-8"));
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void copy(File from, File to) throws IOException {
        byte[] buf = new byte[16384];
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}
