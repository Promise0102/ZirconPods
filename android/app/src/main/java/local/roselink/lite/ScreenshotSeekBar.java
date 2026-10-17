package local.roselink.lite;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.SeekBar;

/** Discrete pill track and white ring thumb; retains native SeekBar input and accessibility. */
public final class ScreenshotSeekBar extends SeekBar {
    private final Paint paint=new Paint(3);
    public ScreenshotSeekBar(Context context){super(context);setBackgroundColor(0);int pad=dp(12);setPadding(pad,0,pad,0);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected synchronized void onDraw(Canvas canvas){
        float left=getPaddingLeft(),right=getWidth()-getPaddingRight(),cy=getHeight()/2f,r=dp(12);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xffededed);canvas.drawRoundRect(left,cy-r,right,cy+r,r,r,paint);
        float position=left+(right-left)*getProgress()/Math.max(1f,getMax());
        if(getProgress()>0){paint.setColor(isEnabled()?0xff3482ff:0xffa6c9f8);canvas.save();canvas.clipRect(left,cy-r,position,cy+r);canvas.drawRoundRect(left,cy-r,right,cy+r,r,r,paint);canvas.restore();}
        for(int i=0;i<=getMax();i++){float x=left+(right-left)*i/Math.max(1f,getMax());paint.setColor(i<=getProgress()?0xff8cbbff:0xffd7dce3);canvas.drawCircle(x,cy,dp(3),paint);}
        paint.setColor(0xffffffff);canvas.drawCircle(position,cy,dp(11),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setColor(isEnabled()?0xff3482ff:0xffa6c9f8);canvas.drawCircle(position,cy,dp(11),paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
