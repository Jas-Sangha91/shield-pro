package com.jas.shieldhome;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Simple line icons drawn in code (no image files), in the same style as the top bar icons. */
final class Icons {

    static final int INFO = 0, SUN = 1, CLOCK = 2, GLOBE = 3, ARTWORK = 4, BACKUP = 5, TV = 6, CHEVRON = 7;

    private Icons() {
    }

    static Drawable make(int kind, int sizePx) {
        return new LineIcon(kind, sizePx);
    }

    private static final class LineIcon extends Drawable {
        private final int kind;
        private final int size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color = 0xFFFFFFFF;

        LineIcon(int kind, int size) {
            this.kind = kind;
            this.size = size;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void setTintList(ColorStateList tint) {
            color = tint != null ? tint.getDefaultColor() : 0xFFFFFFFF;
            invalidateSelf();
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            float x0 = b.centerX() - s / 2f;
            float y0 = b.centerY() - s / 2f;
            float cx = b.centerX();
            float cy = b.centerY();
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.09f);

            switch (kind) {
                case INFO: {
                    c.drawCircle(cx, cy, s * 0.42f, paint);
                    c.drawLine(cx, cy - s * 0.02f, cx, cy + s * 0.22f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy - s * 0.19f, s * 0.06f, paint);
                    break;
                }
                case SUN: {
                    c.drawCircle(cx, cy, s * 0.18f, paint);
                    for (int i = 0; i < 8; i++) {
                        double ang = Math.PI * i / 4.0;
                        float r1 = s * 0.31f, r2 = s * 0.44f;
                        c.drawLine(cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1,
                                cx + (float) Math.cos(ang) * r2, cy + (float) Math.sin(ang) * r2, paint);
                    }
                    break;
                }
                case CLOCK: {
                    c.drawCircle(cx, cy, s * 0.42f, paint);
                    c.drawLine(cx, cy, cx, cy - s * 0.24f, paint);
                    c.drawLine(cx, cy, cx + s * 0.17f, cy + s * 0.08f, paint);
                    break;
                }
                case GLOBE: {
                    float r = s * 0.42f;
                    c.drawCircle(cx, cy, r, paint);
                    c.drawOval(new RectF(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r), paint);
                    c.drawLine(cx - r, cy, cx + r, cy, paint);
                    break;
                }
                case ARTWORK: {
                    RectF f = new RectF(x0 + s * 0.08f, y0 + s * 0.16f, x0 + s * 0.92f, y0 + s * 0.84f);
                    c.drawRoundRect(f, s * 0.08f, s * 0.08f, paint);
                    Path p = new Path();
                    p.moveTo(x0 + s * 0.20f, y0 + s * 0.70f);
                    p.lineTo(x0 + s * 0.40f, y0 + s * 0.48f);
                    p.lineTo(x0 + s * 0.55f, y0 + s * 0.62f);
                    p.lineTo(x0 + s * 0.66f, y0 + s * 0.52f);
                    p.lineTo(x0 + s * 0.80f, y0 + s * 0.70f);
                    c.drawPath(p, paint);
                    paint.setStyle(Paint.Style.FILL);
                    c.drawCircle(x0 + s * 0.70f, y0 + s * 0.34f, s * 0.06f, paint);
                    break;
                }
                case BACKUP: {
                    Path tray = new Path();
                    tray.moveTo(x0 + s * 0.12f, y0 + s * 0.60f);
                    tray.lineTo(x0 + s * 0.12f, y0 + s * 0.86f);
                    tray.lineTo(x0 + s * 0.88f, y0 + s * 0.86f);
                    tray.lineTo(x0 + s * 0.88f, y0 + s * 0.60f);
                    c.drawPath(tray, paint);
                    c.drawLine(cx, y0 + s * 0.66f, cx, y0 + s * 0.12f, paint);
                    Path head = new Path();
                    head.moveTo(x0 + s * 0.30f, y0 + s * 0.32f);
                    head.lineTo(cx, y0 + s * 0.12f);
                    head.lineTo(x0 + s * 0.70f, y0 + s * 0.32f);
                    c.drawPath(head, paint);
                    break;
                }
                case TV: {
                    RectF tv = new RectF(x0 + s * 0.06f, y0 + s * 0.14f, x0 + s * 0.94f, y0 + s * 0.72f);
                    c.drawRoundRect(tv, s * 0.08f, s * 0.08f, paint);
                    c.drawLine(x0 + s * 0.32f, y0 + s * 0.88f, x0 + s * 0.68f, y0 + s * 0.88f, paint);
                    break;
                }
                case CHEVRON: {
                    Path ch = new Path();
                    ch.moveTo(x0 + s * 0.38f, y0 + s * 0.20f);
                    ch.lineTo(x0 + s * 0.64f, cy);
                    ch.lineTo(x0 + s * 0.38f, y0 + s * 0.80f);
                    c.drawPath(ch, paint);
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
