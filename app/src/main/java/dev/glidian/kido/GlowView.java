package dev.glidian.kido;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * A soft colored glow: a shape with a blurred halo, inset by the blur radius so the halo fits inside
 * the view. It renders into its own hardware layer, so the blur is computed once and moving or fading
 * the view afterwards costs no more than any other view. Used behind focused tiles and the status block.
 */
final class GlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path hex = new Path();
    private final float radius;
    private final boolean oval;
    private final boolean hexagon;
    private int color;
    private boolean on = true;

    GlowView(Context c, float radius, boolean oval) {
        this(c, radius, oval, false);
    }

    GlowView(Context c, float radius, boolean oval, boolean hexagon) {
        super(c);
        this.radius = radius;
        this.oval = oval;
        this.hexagon = hexagon;
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setColor(int color) {
        if (this.color == color) return;
        this.color = color;
        invalidate();
    }

    void setGlowing(boolean on) {
        if (this.on == on) return;
        this.on = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        paint.setColor(color);
        if (on) paint.setShadowLayer(radius, 0, 0, color);
        else paint.clearShadowLayer();
        r.set(radius, radius, getWidth() - radius, getHeight() - radius);
        if (hexagon) {
            float w = r.width(), h = r.height();
            hex.reset();
            hex.moveTo(r.left + w / 2, r.top);
            hex.lineTo(r.right, r.top + h * 0.25f);
            hex.lineTo(r.right, r.top + h * 0.75f);
            hex.lineTo(r.left + w / 2, r.bottom);
            hex.lineTo(r.left, r.top + h * 0.75f);
            hex.lineTo(r.left, r.top + h * 0.25f);
            hex.close();
            c.drawPath(hex, paint);
        } else if (oval) c.drawOval(r, paint);
        else c.drawRect(r, paint);
    }
}
