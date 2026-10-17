package local.roselink.lite;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** Displays the approved screenshot-two cutout, cropping only transparent margins at draw time. */
public final class ProductImageView extends View {
    private Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();
    private int color = -1;
    public ProductImageView(Context context) {
        super(context);
        setColor(0);
    }
    public void setColor(int next) {
        if(next < 0 || next > 2 || color == next)return;
        color = next;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
        bitmap=BitmapFactory.decodeResource(getResources(),next==1?R.drawable.zircon_silver:next==2?R.drawable.zircon_purple:R.drawable.zircon_black,options);
        // Ignore transparent margins at draw time; the source raster and alpha stay untouched.
        int left=bitmap.getWidth(),top=bitmap.getHeight(),right=0,bottom=0;
        int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if((pixels[y*bitmap.getWidth()+x]>>>24)>128){
            left=Math.min(left,x);right=Math.max(right,x+1);top=Math.min(top,y);bottom=Math.max(bottom,y+1);
        }
        source.set(left,top,right,bottom);
        setContentDescription((next==1?"银色":next==2?"紫色":"黑色")+"锆势耳机与打开的充电盒");
        requestLayout();invalidate();
    }
    @Override protected void onMeasure(int w,int h) {
        int width=MeasureSpec.getSize(w);
        int height=Math.round(Math.min(width*0.76f,340*getResources().getDisplayMetrics().density)*source.height()/source.width()+20*getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(width,w),resolveSize(height,h));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);if(bitmap==null)return;
        float height=getHeight()-16*getResources().getDisplayMetrics().density;
        float width=height*source.width()/source.height();
        canvas.drawBitmap(bitmap,source,new RectF((getWidth()-width)/2,8*getResources().getDisplayMetrics().density,(getWidth()+width)/2,getHeight()-8*getResources().getDisplayMetrics().density),paint);
    }
}
