package local.roselink.lite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Bounded thumbnails rendered from existing app assets instead of full-size Binder bitmaps. */
final class NotificationProductImage {
    private static final Bitmap[] cache = new Bitmap[3];
    static int color(Context context) {
        return Math.max(0, Math.min(2, context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
                .getInt("product_color", 0)));
    }
    static synchronized Bitmap icon(Context context) {
        int color = color(context);
        if (cache[color] != null) return cache[color];
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 8; options.inScaled = false;
        Bitmap original = BitmapFactory.decodeResource(context.getResources(), color == 1 ? R.drawable.zircon_silver
                : color == 2 ? R.drawable.zircon_purple : R.drawable.zircon_black, options);
        Bitmap result = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        if (original == null) return cache[color] = result;
        int width = original.getWidth(), height = original.getHeight();
        int[] pixels = new int[width * height];
        original.getPixels(pixels, 0, width, 0, 0, width, height);
        int left = width, top = height, right = 0, bottom = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if ((pixels[y * width + x] >>> 24) > 128) {
            left = Math.min(left, x); top = Math.min(top, y);
            right = Math.max(right, x + 1); bottom = Math.max(bottom, y + 1);
        }
        if (right > left && bottom > top) {
            float scale = Math.min(120f / (right - left), 120f / (bottom - top));
            float w = (right - left) * scale, h = (bottom - top) * scale;
            new Canvas(result).drawBitmap(original, new Rect(left, top, right, bottom),
                    new RectF((128 - w) / 2, (128 - h) / 2, (128 + w) / 2, (128 + h) / 2),
                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        }
        original.recycle();
        return cache[color] = result;
    }
}
