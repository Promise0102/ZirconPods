# ZirconPods 0.6.0：ColorOS 流体云兼容测试

目标设备由用户报告为 OPPO Find N2，ColorOS16.0.5。当前电脑没有连接该手机，不能将系统版本号或编译成功当作上岛成功的证据。

## 使用

1. 覆盖安装0.6.0，打开右上角设置，找到“流体云连接提示”。
2. 点击“系统实时更新权限”，在系统允许的情况下开启应用通知、实时更新和“流体云连接提示”通知分类。部分系统没有独立权限入口时会进入应用通知设置。
3. 回到应用点击“重新检查系统支持”，查看接口和授权结果。
4. 在应用设备面板中断开并重新连接耳机；发起连接后可返回桌面，观察顶部系统胶囊或连接通知。前台应用中的显示策略也由系统控制。

本次提示只对应用户在ZirconPods中发起的真实连接操作：连接中使用不确定进度，成功后展示约5秒左右耳/充电盒电量和当前模式，点击进入控制界面。基础四按钮通知继续由原前台服务提供。成功后的5秒截止时间固定，连续状态回包不会续期；断开、失败、手动关闭或关闭开关都会撤销提示。退出主界面或打开应用本身不会制造一次新连接任务。

胶囊短文字遵循建议的7字符长度，超长时只显示模式，完整电量在卡片中。未知电量显示“—”，未知模式显示“未读取”，不会使用截图中的电量或普通模式作为默认值。

这不是OPPO Enco系统快连协议，也不改变锆势的蓝牙设备身份；单纯开盖而未在ZirconPods发起连接时，不承诺出现原厂配件弹窗。

## 原生接口与限制

使用Android公开的实时更新接口：`NotificationManager.canPostPromotedNotifications()`、`Notification.Builder.setShortCriticalText()`、`android.requestPromotedOngoing` extra。Builder的`setRequestPromotedOngoing`在36.1才公开，因此使用官方extra兼容36系列系统。项目保持compileSdk34，在运行时仅查找公开框架方法，不访问厂商私有接口、不修改系统标记、不伪装媒体播放或导航。

新增声明 `android.permission.POST_PROMOTED_NOTIFICATIONS`。该权限不是普通运行时弹窗权限，实际提升能力通过系统方法查询，并提供系统设置入口。没有添加网络、悬浮窗、全屏通知、通知监听或无障碍权限。

实时更新不接受自定义RemoteViews，而原有四模式通知正使用RemoteViews。因此原生连接通知采用标准BigTextStyle并使用独立通知ID78，原来的四按钮控制通知ID77保持不变。原生通知不着色、不设分组摘要，使用ONGOING和标题，单独的DEFAULT重要度通道关闭声音和振动。

系统确认API不存在、通知关闭或提升授权未允许时，保留原通知功能并记录原因。设备支持API不代表系统一定提升该连接任务。发布请求后，应用查询自己的系统通知并读取公开FLAG_PROMOTED_ONGOING：区分“系统确认提升”和“请求已发送但未确认提升”。即使系统确认提升，具体胶囊、面板位置及动效仍由ColorOS决定。

完成通知设置系统超时，连接中通知也设置95秒上限，避免进程异常退出后留下无期限连接提示；正常连接超时仍由原蓝牙控制器处理。不增加定时保活或持续状态轮询。

## 验证边界

118项主机检查已通过：77项协议、22项四模式通知状态、19项原生连接提示的生命周期与内容。新检查包含连接阶段、完成5秒、迟到回包不延长/不重开、重新连接、用户关闭、断开与失败、未知值，以及极限降噪与100%电量的长度边界。

这些检查不运行Android系统，不验证ColorOS渲染或真实提升。安装后如未上岛，请提供设置页“重新检查系统支持”中的文字结果；它能够区分实际Android版本、公开API、系统授权和提升结果。

## 官方依据（查阅于2026-10-17）

- [Android实时更新要求与适用场景](https://developer.android.com/develop/ui/views/notifications/live-update)：实时更新面向用户发起、正在进行、具有时效性的任务；不用于长期快捷功能入口。本实现仅覆盖有限连接任务。
- [setRequestPromotedOngoing](https://developer.android.com/reference/android/app/Notification.Builder#setRequestPromotedOngoing(boolean))：公开版本36.1；对应官方extra。
- [setShortCriticalText](https://developer.android.com/reference/android/app/Notification.Builder#setShortCriticalText(java.lang.String))：API36，建议短文字最多7字符。
- [canPostPromotedNotifications](https://developer.android.com/reference/android/app/NotificationManager#canPostPromotedNotifications())：API36，可查询是否获准提升并跳转系统设置。
- [OPPO流体云卡片设置](https://open.oppomobile.com/new/developmentDoc/info?id=13330)：厂商Seedling卡片属于另一套接入路径；本版未申请服务ID或声称取得厂商卡片合作权限。
