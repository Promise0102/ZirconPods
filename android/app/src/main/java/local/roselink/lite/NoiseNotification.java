package local.roselink.lite;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.widget.RemoteViews;

/** Four direct controls in both the collapsed and expanded system notification. */
final class NoiseNotification {
    private static final int[] MODES = {1, 4, 3, 2};
    private static final int[] KINDS = {0, 3, 2, 1};
    private static final int[] BUTTONS = {R.id.mode_anc, R.id.mode_wind, R.id.mode_transparency, R.id.mode_normal};
    private static final int[] ICONS = {R.id.icon_anc, R.id.icon_wind, R.id.icon_transparency, R.id.icon_normal};
    private static final int[] LABELS = {R.id.label_anc, R.id.label_wind, R.id.label_transparency, R.id.label_normal};
    private final Context context;
    private final Bitmap[][] icons = new Bitmap[4][2];

    NoiseNotification(Context context) {
        this.context = context;
        // Both RemoteViews travel over Binder. Bound icon bitmaps even on very high DPI phones.
        float density = Math.min(2f, context.getResources().getDisplayMetrics().density);
        int size = Math.round(52 * density);
        for (int i = 0; i < 4; i++) for (int selected = 0; selected < 2; selected++) {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            NoiseModeDrawable drawable = new NoiseModeDrawable(KINDS[i], selected == 1, density);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(new Canvas(bitmap));
            icons[i][selected] = bitmap;
        }
    }
    Notification build(DeviceState state, NoiseModeSelection modes) {
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int text = dark ? 0xffdddddd : 0xff555555;
        RemoteViews compact = controls(R.layout.notification_compact, state, modes, open, text);
        RemoteViews expanded = controls(R.layout.notification_expanded, state, modes, open, text);
        String status = state.connecting ? "正在连接耳机…" : "当前：" + NoiseModeSelection.label(modes.confirmed());
        if (state.connected && modes.requested() != NoiseModeSelection.UNKNOWN)
            status += " · 正在切换" + NoiseModeSelection.label(modes.requested());
        if (state.connected) status += "　左 " + battery(state.leftBattery) + " / 右 " + battery(state.rightBattery);
        expanded.setTextViewText(R.id.notification_status, status);
        expanded.setTextColor(R.id.notification_status, text);
        expanded.setTextColor(R.id.notification_disconnect, text);
        expanded.setOnClickPendingIntent(R.id.notification_disconnect, PendingIntent.getService(context, 20,
                new Intent(context, EarphoneService.class).setAction(EarphoneService.ACTION_DISCONNECT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return new Notification.Builder(context, EarphoneService.CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("ZirconPods")
                .setContentText(status).setContentIntent(open)
                .setCustomContentView(compact).setCustomBigContentView(expanded)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setColor(0xff3482ff).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE)
                .build();
    }
    private RemoteViews controls(int layout, DeviceState state, NoiseModeSelection modes, PendingIntent open, int text) {
        RemoteViews view = new RemoteViews(context.getPackageName(), layout);
        for (int i = 0; i < 4; i++) {
            boolean selected = modes.confirmed() == MODES[i];
            String label = NoiseModeSelection.label(MODES[i]);
            view.setImageViewBitmap(ICONS[i], icons[i][selected ? 1 : 0]);
            view.setTextViewText(LABELS[i], label);
            view.setTextColor(LABELS[i], selected ? 0xff168aff : text);
            view.setContentDescription(BUTTONS[i], label + (selected ? "，耳机已确认" : "，点击切换")
                    + (state.connected ? "" : "，连接后可用"));
            PendingIntent action = state.connected ? PendingIntent.getService(context, MODES[i],
                    new Intent(context, EarphoneService.class).setAction(EarphoneService.ACTION_MODE)
                            .putExtra(EarphoneService.EXTRA_MODE, MODES[i]),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : open;
            view.setOnClickPendingIntent(BUTTONS[i], action);
        }
        return view;
    }
    private static String battery(int value) { return value >= 0 && value <= 100 ? value + "%" : "—"; }
}
