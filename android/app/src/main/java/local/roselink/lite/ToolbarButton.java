package local.roselink.lite;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.Button;

public final class ToolbarButton extends Button {
    private final int kind;private final Paint p=new Paint(3);
    public ToolbarButton(Context context,int kind,String label){super(context);this.kind=kind;setContentDescription(label);setBackgroundColor(0x00000000);setMinWidth(0);setMinimumWidth(0);setStateListAnimator(null);}
    @Override protected void onDraw(Canvas c){
        float d=getResources().getDisplayMetrics().density;c.save();c.translate(getWidth()/2f-14*d,getHeight()/2f-14*d);c.scale(d,d);
        p.setStyle(Paint.Style.STROKE);p.setColor(0xff151515);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        if(kind==0){Path a=new Path();a.moveTo(8,7);a.lineTo(2,13);a.lineTo(8,19);c.drawPath(a,p);c.drawLine(13,7,25,7,p);c.drawLine(13,13,25,13,p);c.drawLine(5,24,25,24,p);}
        else if(kind==1){c.drawArc(4,4,24,24,-65,290,false,p);Path a=new Path();a.moveTo(17,1);a.lineTo(22,5);a.lineTo(16,6);c.drawPath(a,p);}
        else{Path a=new Path();a.moveTo(14,2);a.lineTo(24,8);a.lineTo(24,20);a.lineTo(14,26);a.lineTo(4,20);a.lineTo(4,8);a.close();c.drawPath(a,p);c.drawCircle(14,14,4,p);}
        c.restore();
    }
}
