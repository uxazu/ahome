package com.gathin.tvhome;

import android.graphics.Bitmap;

/**
 * A small copy of whatever sits behind the dock (the wallpaper, or the sky scene), kept so the liquid glass band can
 * bend and blur it. Android 11 has no live blur, so the glass works on this copy and is baked once per change.
 */
final class Backdrop {
    private static Bitmap bitmap;
    private static int version;

    private Backdrop() {}

    /** Keeps a 240x135 copy of the picture; the caller keeps ownership of {@code source}. */
    static synchronized void set(Bitmap source) {
        if (source == null || source.isRecycled()) return;
        Bitmap small = Bitmap.createScaledBitmap(source, 240, 135, true);
        if (small.getConfig() != Bitmap.Config.ARGB_8888) small = small.copy(Bitmap.Config.ARGB_8888, false);
        bitmap = small;
        version++;
    }

    static synchronized Bitmap get() {
        return bitmap;
    }

    static synchronized int version() {
        return version;
    }
}
