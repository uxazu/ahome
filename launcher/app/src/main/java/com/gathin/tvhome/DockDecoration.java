package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    /** glass_style liquid: the band gets a lens bloom and a rim lit at two opposite corners. */
    private boolean liquid;
    /** Room under the dock for the search bar, and above the first row below it for a section title, in px. */
    private float searchGap, sectionGap;
    private String sectionTitle;
    private DockListener dockListener;
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF appsArea = new RectF();
    private float tintAlpha = 1f;
    interface FrostListener { void onFrost(float progress); }
    FrostListener frostListener;
    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BitmapShader blurShader;
    private Bitmap blurSource;

    interface DockListener {
        /** The dock band is on screen ({@code visible}) with these edges, in the grid's coordinates; or it is not. */
        void onDock(boolean visible, float left, float bottom, float right);
    }

    /** Makes room for the search bar under the dock and titles the apps below it. */
    void setSearch(float gapPx, float sectionPx, String title, DockListener listener) {
        searchGap = gapPx;
        sectionGap = sectionPx;
        sectionTitle = title;
        dockListener = listener;
        titlePaint.setTextSize(15f * density);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(Expressive.on ? Expressive.ON_SURFACE_VARIANT : 0xB3FFFFFF);
    }
    private final Paint bloom = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The liquid glass baked from the backdrop, and what it was baked for. */
    private Bitmap baked;
    private int bakedW, bakedH, bakedVersion = -1;
    private float bakedLeft, bakedTop;

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;
    /** Light glass like the top bar's pills (config.xml dock_glass): a white tint that fades downwards. */
    private final boolean lightGlass;
    /** Padding all round the tiles equal to the gap between them (config.xml dock_even_padding). */
    private final boolean evenPadding;
    /** The band's top and bottom the glass gradient was made for; it is remade when they move. */
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;
    /** Draw the band and its edge (config.xml dock_band); false leaves the tiles floating on the wallpaper. */
    private final boolean bandVisible;
    /** single_row: the tiles after the dock share its row, so they take the same offsets to stay level with it. */
    private final boolean singleRow;
    /** dock_band_all: in a single row, the band runs behind every app instead of the dock apps only. */
    private final boolean bandAll;

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     * @param opacity     the plain band's opacity in percent (dock_opacity), so a wallpaper can show through it.
     * @param lightGlass  a light frosted band like the pills, for a sky or wallpaper behind it.
     * @param evenPadding the band's padding is the gap between the tiles on all four sides (dock_even_padding).
     * @param bandVisible false skips the band and its edge, so the tiles float on the wallpaper (dock_band).
     * @param singleRow   every app sits in the dock's row (single_row); the band covers the dock apps only, unless bandAll.
     * @param bandAll     with singleRow, the band runs behind every app in the row (dock_band_all).
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names, int opacity, boolean lightGlass,
                   boolean evenPadding, boolean bandVisible, boolean singleRow, boolean bandAll) {
        this.dockSize = dockSize;
        this.singleRow = singleRow;
        this.bandAll = singleRow && bandAll;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        this.lightGlass = lightGlass;
        this.evenPadding = evenPadding;
        this.bandVisible = bandVisible;
        band.setColor(translucent ? 0x8C121014 : (Math.round(opacity * 2.55f) << 24) | 0x141416);
        if (Expressive.on) band.setColor(Expressive.SURFACE_CONTAINER);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(lightGlass ? 0x38FFFFFF : translucent ? 0x26FFFFFF : 0x1FFFFFFF);
    }

    /**
     * The favourites band as frosted glass: the animated wallpaper's small copy, stretched over the screen (which
     * blurs it) and drawn through the band's shape, with a light tint of the container colour over it so the tiles
     * keep their contrast. False when there is no wallpaper copy to show.
     */
    private boolean drawBlurredBand(Canvas c, View parent, float radius) {
        return drawBlurred(c, parent, rect, radius);
    }

    private boolean drawBlurred(Canvas c, View parent, RectF area, float radius) {
        Bitmap snap = ShapesView.snapshot;
        if (snap == null || !ShapesView.blur) return false;
        if (blurShader == null || blurSource != snap) {
            blurShader = new BitmapShader(snap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            blurSource = snap;
        }
        int[] at = new int[2];
        parent.getLocationInWindow(at);
        View root = parent.getRootView();
        Matrix m = new Matrix();
        m.setScale(root.getWidth() / (float) snap.getWidth(), root.getHeight() / (float) snap.getHeight());
        m.postTranslate(-at[0], -at[1]);
        blurShader.setLocalMatrix(m);
        blurPaint.setShader(blurShader);
        c.drawRoundRect(area, radius, radius, blurPaint);
        tintPaint.setColor(ShapesView.frostTint);
        tintPaint.setAlpha(Math.round(Color.alpha(ShapesView.frostTint) * tintAlpha));
        c.drawRoundRect(area, radius, radius, tintPaint);
        return true;
    }

    private Bitmap bakedGlass(View parent, float radius) {
        Bitmap backdrop = Backdrop.get();
        if (backdrop == null) return null;
        int w = Math.round(rect.width()), h = Math.round(rect.height());
        boolean stale = baked == null || w != bakedW || h != bakedH || Backdrop.version() != bakedVersion
                || Math.abs(rect.left - bakedLeft) > 6 * density || Math.abs(rect.top - bakedTop) > 6 * density;
        if (stale) {
            int[] at = new int[2];
            parent.getLocationInWindow(at);
            View root = parent.getRootView();
            RectF onScreen = new RectF(rect.left + at[0], rect.top + at[1], rect.right + at[0], rect.bottom + at[1]);
            baked = LiquidGlass.bake(backdrop, root.getWidth(), root.getHeight(), onScreen, radius, density);
            bakedW = w;
            bakedH = h;
            bakedVersion = Backdrop.version();
            bakedLeft = rect.left;
            bakedTop = rect.top;
        }
        return baked;
    }

    void setLiquid(boolean on) {
        liquid = on;
        shaderTop = Float.NaN;
        bloom.setStyle(Paint.Style.FILL);
        edge.setStrokeWidth((on ? 1.6f : 1f) * density);
    }

    /** Concentric corners: the band's radius is the tiles' radius plus the band's padding around them. */
    static float radiusPx(float tileRadiusPx, float paddingPx) {
        return tileRadiusPx + paddingPx;
    }

    /**
     * The band's padding on every side for dock_even_padding: the gap between two dock tiles (a cell minus its tile, as
     * AppAdapter.tileWidthPx lays them out), capped by the room the grid leaves at its edges.
     */
    static float evenPaddingPx(int innerWidthPx, int columns, int tileWidthPx, float roomPx) {
        float gap = columns > 0 ? innerWidthPx / (float) columns - tileWidthPx : 0f;
        return Math.max(0f, Math.min(gap, roomPx));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && (singleRow || pos < dockSize)) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
        if (pos >= 0 && !singleRow) {
            if (pos < dockSize) outRect.bottom += (int) searchGap;
            else if (pos < 2 * dockSize) outRect.top += (int) sectionGap;
            if (pos >= dockSize && searchGap > 0) outRect.bottom -= (int) (8 * density);   // the apps list sits closer together
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (!bandVisible) return;
        boolean any = false;
        int cardWidth = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || (!bandAll && pos >= dockSize)) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            cardWidth = card.getWidth();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) {
            // The dock has scrolled out: the apps page is on, and its frosted layer and title stay.
            float firstRow = drawAppsPage(c, parent, state);
            if (dockListener != null) {
                if (firstRow != Float.MAX_VALUE && searchGap > 0) {
                    // The search bar rides the list: it sits a fixed way above the first row of apps.
                    float left = rect.width() > 0 ? rect.left : 81 * density;
                    float right = rect.width() > 0 ? rect.right : parent.getWidth() - 81 * density;
                    dockListener.onDock(true, left, firstRow - 248 * density, right);
                } else {
                    dockListener.onDock(false, 0, 0, 0);
                }
            }
            return;
        }
        float pad = 14 * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        // 22dp at the sides (the grid's 10dp padding makes room): a focused first or last tile grows about 7dp
        // outwards and still keeps about the band's top margin from its rounded end.
        float sidePad = 22 * density;
        float side = Math.max(0f, Math.min(sidePad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        float radius = AppAdapter.cornerRadius(cardWidth, density);
        if (evenPadding) {
            // The same padding on all four sides, so the band's corners are concentric with the tiles' everywhere.
            int inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            // A single row's tiles can run off the screen, so only its top limits the padding; its ends are clamped below.
            float room = (singleRow ? rect.top : Math.min(rect.top, Math.min(rect.left, parent.getWidth() - rect.right))) - density;
            float even = evenPaddingPx(inner, dockSize, cardWidth, room);
            rect.inset(-even, -even);
            radius = radiusPx(radius, even);
        } else {
            // dock_even_padding off: the band as before, with its fixed 26 dp corners.
            rect.inset(singleRow ? -sidePad : -side, -pad);
            radius = 26 * density;
        }
        if (singleRow) {
            // Tiles past the screen's edge: the band stops at the grid's edge with its rounded end instead of being
            // sliced square there.
            rect.left = Math.max(rect.left, density);
            rect.right = Math.min(rect.right, parent.getWidth() - density);
        }
        rect.bottom += namesRoom;
        if (dockListener != null) dockListener.onDock(true, rect.left, rect.bottom, rect.right);
        if (lightGlass && (rect.top != shaderTop || rect.bottom != shaderBottom)) {
            // The pills' glass (pill_bg: white, 18% fading to 8%), a little fainter over this much larger area.
            band.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x24FFFFFF, 0x0DFFFFFF,
                    Shader.TileMode.CLAMP));
            shaderTop = rect.top;
            shaderBottom = rect.bottom;
        }
        Bitmap glass = liquid ? bakedGlass(parent, radius) : null;
        if (glass != null) c.drawBitmap(glass, rect.left, rect.top, null);
        else if (!(Expressive.on && drawBlurredBand(c, parent, radius))) c.drawRoundRect(rect, radius, radius, band);
        if (liquid) {
            float w = rect.width(), h = rect.height();
            bloom.setShader(new RadialGradient(rect.left + w * 0.2f, rect.top + h * 0.1f, w * 0.55f,
                    0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, radius, radius, bloom);
            edge.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{0xE0FFFFFF, 0x20FFFFFF, 0x14FFFFFF, 0xA0FFFFFF}, new float[]{0f, 0.35f, 0.65f, 1f},
                    Shader.TileMode.CLAMP));
        }
        c.drawRoundRect(rect, radius, radius, edge);
        drawAppsPage(c, parent, state);
    }

    /** The apps page: one frosted layer over the whole screen that fades in as the list scrolls up, and the section title. */
    private float drawAppsPage(Canvas c, RecyclerView parent, RecyclerView.State state) {
        float firstRow = Float.MAX_VALUE;
        if (sectionTitle != null) {
            float minTop = Float.MAX_VALUE, lastBottom = 0;
            boolean tail = false;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                int pos = parent.getChildAdapterPosition(child);
                if (pos >= dockSize && pos < 2 * dockSize) minTop = Math.min(minTop, child.getTop());
                if (pos >= dockSize) lastBottom = Math.max(lastBottom, child.getBottom());
                if (pos == state.getItemCount() - 1) tail = true;
            }
            float p = 0f;
            if (Expressive.on && lastBottom > 0) {
                // Zero while the first row of apps is below the dock page, one once it has scrolled up into the page.
                p = minTop == Float.MAX_VALUE ? 1f
                        : Math.max(0f, Math.min(1f, 1f - (minTop - 330 * density) / (parent.getHeight() * 0.45f)));
            }
            if (frostListener != null) frostListener.onFrost(p);
            firstRow = minTop;
            if (Fonts.small() != null && titlePaint.getTypeface() != Fonts.small()) {
                titlePaint.setTypeface(Fonts.small());
                titlePaint.setFakeBoldText(false);
            }
            if (minTop != Float.MAX_VALUE) c.drawText(sectionTitle, rect.left + 2 * density, minTop - 16 * density, titlePaint);
        }
        return firstRow;
    }
}
