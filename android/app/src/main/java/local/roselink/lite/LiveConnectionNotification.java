package local.roselink.lite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Public Android Live Updates compatibility bridge; no vendor-private calls or overlays. */
final class LiveConnectionNotification {
    static final String PREFERENCES = "live_connection";
    static final String ENABLED = "enabled";
    static final String CHANNEL = "earphone_live_connection";
    static final String ACTION_DISMISS = "local.roselink.lite.DISMISS_LIVE_CONNECTION";
    static final String ACTION_OPEN_CONTROLS = "local.roselink.lite.OPEN_CONTROLS";
    private static final int ID = 78;
    // Documented EXTRA_REQUEST_PROMOTED_ONGOING. Its builder setter was only exposed in 36.1.
    private static final String REQUEST_PROMOTION = "android.requestPromotedOngoing";
    private final Context context;
    private final NotificationManager manager;
    private final SharedPreferences preferences;
    private final Consumer<String> log;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConnectionPopupWindow window = new ConnectionPopupWindow();
    private DeviceState lastState;
    private String signature = "";
    private boolean auditScheduled;
    private int generation;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (prefs, key) -> {
        if (ENABLED.equals(key) && !prefs.getBoolean(ENABLED, true)) cancel();
    };
    private final Runnable finish = () -> {
        if (lastState == null || !window.update(lastState.connected, lastState.connecting, SystemClock.elapsedRealtime())) cancel();
    };

    LiveConnectionNotification(Context context, Consumer<String> log) {
        this.context = context;
        this.log = log;
        manager = context.getSystemService(NotificationManager.class);
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "流体云连接提示", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("耳机连接过程与连接完成后的短时电量提示；实际展示由系统决定");
        channel.setShowBadge(false); channel.enableVibration(false); channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
    static boolean apiAvailable() {
        return Build.VERSION.SDK_INT >= 36
                && method(Notification.Builder.class, "setShortCriticalText", String.class) != null
                && method(NotificationManager.class, "canPostPromotedNotifications") != null;
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try { return type.getMethod(name, parameters); }
        catch (ReflectiveOperationException | RuntimeException error) { return null; }
    }
    private static Boolean promotionAllowed(NotificationManager manager) {
        Method query = method(NotificationManager.class, "canPostPromotedNotifications");
        try { return query == null ? null : (Boolean) query.invoke(manager); }
        catch (ReflectiveOperationException | RuntimeException error) { return null; }
    }
    static String status(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        String system = Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT;
        String api = !apiAvailable() ? "当前系统未提供所需的实时更新公开接口" : "实时更新公开接口：可调用";
        Boolean allowed = apiAvailable() ? promotionAllowed(manager) : null;
        String permission = allowed == null ? "实时更新授权：无法查询" : allowed ? "实时更新授权：已允许" : "实时更新授权：未允许，请到系统设置开启";
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        String notifications = !manager.areNotificationsEnabled() ? "应用通知：已关闭"
                : channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE ? "连接提示分类：已关闭" : "应用通知：已允许";
        String last = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString("last_result", "尚未尝试，请授权后重新连接耳机");
        return system + "\n" + api + "\n" + permission + "\n" + notifications + "\n上次结果：" + last;
    }
    void begin(DeviceState initial) {
        cancel();
        if (!preferences.getBoolean(ENABLED, true)) return;
        if (!apiAvailable()) { result("系统未提供实时更新接口，保留四模式通知"); return; }
        if (!manager.areNotificationsEnabled()) { result("应用通知未授权，无法显示连接提示"); return; }
        if (!Boolean.TRUE.equals(promotionAllowed(manager))) { result("实时更新尚未获系统允许，请到设置开启后重新连接"); return; }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            result("流体云连接提示分类被关闭"); return;
        }
        window.begin();
        update(initial);
    }
    void update(DeviceState state) {
        lastState = state.copy();
        if (!window.update(state.connected, state.connecting, SystemClock.elapsedRealtime())) { cancel(); return; }
        if (!preferences.getBoolean(ENABLED, true)) { cancel(); return; }
        String content = ConnectionPopupWindow.text(state);
        String chip = ConnectionPopupWindow.chip(state);
        String title = EarphoneCardContent.title(state);
        String nextSignature = title + chip + content + NotificationProductImage.color(context);
        if (!signature.equals(nextSignature)) {
            try {
                PendingIntent open = PendingIntent.getActivity(context, 0,
                        new Intent(context, MainActivity.class).setAction(ACTION_OPEN_CONTROLS)
                                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                PendingIntent dismiss = PendingIntent.getService(context, ID,
                        new Intent(context, EarphoneService.class).setAction(ACTION_DISMISS),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification.Builder builder = new Notification.Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title).setLargeIcon(NotificationProductImage.icon(context))
                        .setContentText(content).setStyle(new Notification.BigTextStyle().bigText(content))
                        .setContentIntent(open).setDeleteIntent(dismiss)
                        .setCategory(Notification.CATEGORY_PROGRESS).setOngoing(true).setOnlyAlertOnce(true)
                        .setShowWhen(false).setColor(0xff3482ff).setVisibility(Notification.VISIBILITY_PRIVATE)
                        .setTimeoutAfter(state.connected ? Math.max(1, window.remaining(SystemClock.elapsedRealtime())) : 95000);
                if (!state.connected) builder.setProgress(0, 0, true); // Actual indeterminate connection, no invented percentage.
                Bundle extras = new Bundle(); extras.putBoolean(REQUEST_PROMOTION, true); builder.addExtras(extras);
                Method shortText = method(Notification.Builder.class, "setShortCriticalText", String.class);
                if (shortText != null) shortText.invoke(builder, chip);
                Notification notice = builder.build();
                Method eligible = method(Notification.class, "hasPromotableCharacteristics");
                if (eligible != null && Boolean.FALSE.equals(eligible.invoke(notice))) {
                    result("系统判定该连接通知不满足实时更新条件"); cancel(); return;
                }
                manager.notify(ID, notice);
                signature = nextSignature;
                scheduleAudit();
            } catch (ReflectiveOperationException | RuntimeException error) {
                result("原生连接提示未完成：" + error.getClass().getSimpleName()); cancel(); return;
            }
        }
        main.removeCallbacks(finish);
        long remaining = window.remaining(SystemClock.elapsedRealtime());
        if (remaining >= 0) main.postDelayed(finish, remaining);
    }
    private void scheduleAudit() {
        if (auditScheduled) return;
        auditScheduled = true;
        final int session = generation;
        main.postDelayed(() -> {
            if (session != generation) return;
            auditScheduled = false;
            try {
                int promoted = Notification.class.getField("FLAG_PROMOTED_ONGOING").getInt(null);
                for (StatusBarNotification item : manager.getActiveNotifications()) {
                    if (item.getId() == ID) {
                        result((item.getNotification().flags & promoted) != 0
                                ? "系统已确认提升为实时更新；实际卡片样式由系统决定"
                                : "请求已发送，系统未确认提升；可能仅显示普通连接通知");
                        return;
                    }
                }
                result("连接提示当前未出现在系统通知列表中");
            } catch (ReflectiveOperationException | RuntimeException error) {
                result("请求已发送，当前系统无法读取提升结果");
            }
        }, 700);
    }
    private void result(String value) {
        if (value.equals(preferences.getString("last_result", ""))) return;
        preferences.edit().putString("last_result", value).apply();
        log.accept("流体云：" + value);
    }
    void cancel() {
        generation++;
        window.cancel(); signature = ""; auditScheduled = false;
        main.removeCallbacksAndMessages(null);
        manager.cancel(ID);
    }
    void close() { cancel(); preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener); }
}
