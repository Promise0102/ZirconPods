package local.roselink.lite;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Circular listener glyphs matching the supplied first-screen noise-control visual language. */
public final class NoiseModeDrawable extends Drawable {
    private final Paint p=new Paint(3);
    private final int kind; private final boolean selected; private final float density;
    public NoiseModeDrawable(int kind,boolean selected,float density){this.kind=kind;this.selected=selected;this.density=density;}
    @Override public int getIntrinsicWidth(){return Math.round(52*density);}
    @Override public int getIntrinsicHeight(){return Math.round(52*density);}
    @Override public void draw(Canvas c){
        c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/52f,getBounds().height()/52f);
        p.setStyle(Paint.Style.FILL);p.setColor(selected?0xff168aff:0xffededed);c.drawCircle(26,26,selected?21:21,p);
        if(selected){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(26,26,25,p);}
        p.setStyle(Paint.Style.FILL);p.setColor(selected?0xffffffff:0xff606060);
        c.drawCircle(26,21,5,p);c.drawRoundRect(new RectF(17,28,35,37),4,4,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setStrokeCap(Paint.Cap.ROUND);
        if(kind==0)c.drawArc(new RectF(14,9,38,33),-215,250,false,p);
        if(kind==2){
            c.drawArc(new RectF(21,6,31,15),25,130,false,p);c.drawArc(new RectF(18,3,34,16),25,130,false,p);
            c.drawArc(new RectF(9,15,16,29),-65,130,false,p);c.drawArc(new RectF(6,13,18,31),-65,130,false,p);
            c.drawArc(new RectF(36,15,43,29),115,130,false,p);c.drawArc(new RectF(34,13,46,31),115,130,false,p);
        }
        if(kind==3){
            android.graphics.Path wave=new android.graphics.Path();wave.moveTo(9,20);wave.lineTo(11,17);wave.lineTo(13,26);wave.lineTo(15,16);wave.lineTo(17,23);
            wave.moveTo(35,20);wave.lineTo(37,17);wave.lineTo(39,26);wave.lineTo(41,16);wave.lineTo(43,23);c.drawPath(wave,p);
        }
        c.restore();
    }
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
