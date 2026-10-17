package local.roselink.lite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Code-drawn device illustration; no other model's product photograph is used. */
public final class PodArtworkView extends View {
    private final Paint paint = new Paint(3);
    public PodArtworkView(Context context) { super(context); setContentDescription("无线耳机示意图"); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float scale = Math.min(getWidth()/320f, getHeight()/178f);
        canvas.translate((getWidth()-320*scale)/2, (getHeight()-178*scale)/2);
        canvas.scale(scale, scale);
        paint.setColor(0x0c000000); canvas.drawOval(80,151,240,169,paint);
        paint.setShader(new LinearGradient(0,95,0,156,new int[]{0xffebedef,0xffced2d5,0xffe8eaec},null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(80,85,240,157),28,28,paint);
        paint.setShader(null); paint.setColor(0xffb8bec3); paint.setStrokeWidth(1);
        canvas.drawLine(83,108,237,108,paint);
        paint.setColor(0xff9aa1a6); canvas.drawCircle(160,129,2,paint);
        ear(canvas,116,62,-17); ear(canvas,204,62,17);
        canvas.restore();
    }
    private void ear(Canvas canvas,float x,float y,float rotation) {
        canvas.save(); canvas.translate(x,y); canvas.rotate(rotation);
        paint.setShader(new LinearGradient(-23,0,20,0,new int[]{0xffc9cdd0,Color.WHITE,0xffd3d7da},null,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(-9,-1,10,49),9,9,paint);
        canvas.drawOval(-25,-27,22,15,paint);
        paint.setShader(null); paint.setColor(0xff9da4ab); canvas.drawOval(-22,-15,-12,4,paint);
        paint.setColor(0xffc6cbcf); canvas.drawRoundRect(new RectF(-5,23,-2,38),2,2,paint);
        canvas.restore();
    }
}
