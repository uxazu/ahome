package com.gathin.tvhome;

import android.content.Context;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/**
 * The apps page's frosted layer: the animated wallpaper's small copy stretched over the whole screen (which blurs it)
 * under a tint of the container colour. Its alpha is how far the apps page is scrolled in.
 */
final class FrostView extends View {
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint();
    private final Matrix matrix = new Matrix();
    private BitmapShader shader;
    private android.graphics.Bitmap source;

    FrostView(Context context) {
        super(context);
        setFocusable(false);
        setAlpha(0f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        android.graphics.Bitmap snap = ShapesView.snapshot;
        if (getAlpha() <= 0f) return;
        if (!ShapesView.blur || snap == null) {
            tint.setColor((ShapesView.frostTint & 0x00FFFFFF) | 0xF2000000);
            c.drawRect(0, 0, getWidth(), getHeight(), tint);
            return;
        }
        if (shader == null || source != snap) {
            shader = new BitmapShader(snap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            source = snap;
        }
        matrix.setScale(getWidth() / (float) snap.getWidth(), getHeight() / (float) snap.getHeight());
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        tint.setColor(ShapesView.frostTint);
        c.drawRect(0, 0, getWidth(), getHeight(), tint);
        postInvalidateDelayed(125);
    }
}
