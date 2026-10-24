package local.roselink.lite;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.widget.RemoteViews;

/** Compact product/battery card and expanded four-mode controls use different RemoteViews. */
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
    private PendingIntent panel(int request, String action) {
        return PendingIntent.getActivity(context, request,
                new Intent(context, NotificationPanelActivity.class).setAction(action)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    Notification build(DeviceState state, NoiseModeSelection modes) {
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class).setAction(LiveConnectionNotification.ACTION_OPEN_CONTROLS)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent controls = panel(80, NotificationPanelActivity.ACTION_CONTROLS);
        PendingIntent level = panel(81, NotificationPanelActivity.ACTION_LEVEL);
        PendingIntent done = panel(82, NotificationPanelActivity.ACTION_DONE);
        PendingIntent disconnect = PendingIntent.getService(context, 20,
                new Intent(context, EarphoneService.class).setAction(EarphoneService.ACTION_DISCONNECT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = EarphoneCardContent.title(state);
        int text = context.getColor(R.color.notification_text);
        RemoteViews compact = new RemoteViews(context.getPackageName(), R.layout.notification_compact);
        compact.setTextViewText(R.id.notification_title, title);
        compact.setTextViewText(R.id.notification_batteries, EarphoneCardContent.batteryLine(state));
        compact.setImageViewBitmap(R.id.notification_product, NotificationProductImage.icon(context));
        compact.setOnClickPendingIntent(R.id.notification_header, open);
        compact.setOnClickPendingIntent(R.id.notification_switch, controls);
        compact.setOnClickPendingIntent(R.id.notification_disconnect, disconnect);

        RemoteViews expanded = new RemoteViews(context.getPackageName(), R.layout.notification_expanded);
        expanded.setTextViewText(R.id.notification_title, title);
        expanded.setTextViewText(R.id.battery_left, EarphoneCardContent.battery(state.connected, state.leftBattery));
        expanded.setTextViewText(R.id.battery_right, EarphoneCardContent.battery(state.connected, state.rightBattery));
        expanded.setTextViewText(R.id.battery_case, EarphoneCardContent.battery(state.connected, state.caseBattery));
        for (int i = 0; i < MODES.length; i++) {
            boolean selected = modes.confirmed() == MODES[i];
            String label = NoiseModeSelection.label(MODES[i]);
            expanded.setImageViewBitmap(ICONS[i], icons[i][selected ? 1 : 0]);
            expanded.setTextViewText(LABELS[i], label);
            expanded.setTextColor(LABELS[i], selected ? 0xff168aff : text);
            expanded.setBoolean(BUTTONS[i], "setEnabled", state.connected);
            expanded.setContentDescription(BUTTONS[i], label + (selected ? "，耳机已确认" : "，点击切换")
                    + (state.connected ? "" : "，连接后可用"));
            expanded.setOnClickPendingIntent(BUTTONS[i], PendingIntent.getService(context, MODES[i],
                    new Intent(context, EarphoneService.class).setAction(EarphoneService.ACTION_MODE)
                            .putExtra(EarphoneService.EXTRA_MODE, MODES[i]),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        expanded.setTextViewText(R.id.notification_anc_value, EarphoneCardContent.ancLevel(state) + "  ⌄");
        expanded.setBoolean(R.id.notification_anc_level, "setEnabled", EarphoneCardContent.canSetAncLevel(state));
        expanded.setFloat(R.id.notification_anc_level, "setAlpha", EarphoneCardContent.canSetAncLevel(state) ? 1f : .45f);
        expanded.setOnClickPendingIntent(R.id.notification_anc_level, level);
        expanded.setOnClickPendingIntent(R.id.notification_more, open);
        expanded.setOnClickPendingIntent(R.id.notification_done, done);
        String status = state.connecting ? "正在连接耳机…" : "当前：" + NoiseModeSelection.label(modes.confirmed());
        if (state.connected && modes.requested() != NoiseModeSelection.UNKNOWN)
            status += " · 正在切换" + NoiseModeSelection.label(modes.requested());
        if (!state.connected && !state.connecting) status = "控制通道未连接 · 点击切换声音模式打开面板";
        expanded.setTextViewText(R.id.notification_status, status);
        compact.setBoolean(R.id.notification_disconnect, "setEnabled", state.connected || state.connecting);
        compact.setFloat(R.id.notification_disconnect, "setAlpha", state.connected || state.connecting ? 1f : .45f);
        return new Notification.Builder(context, EarphoneService.CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
                .setContentText(EarphoneCardContent.batteryLine(state)).setContentIntent(open)
                .setCustomContentView(compact).setCustomBigContentView(expanded)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setColor(0xff3482ff).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE)
                .build();
    }
}
