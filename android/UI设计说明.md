# 截图界面适配与主图处理

## 0.5.0 Miuix 与页面整理

实际使用 top.yukonga.miuix.kmp:miuix-android:0.2.9，Kotlin2.0.21 与 Compose1.7.0；不是仅替换颜色的原生 Material 控件。主题、卡片、开关、选择浮层分别由 MiuixTheme、Card、SuperSwitch、SuperDropdown 提供。选项浮层使用右侧圆角卡与蓝色勾选，按用户第二张截图方向设置 alwaysRight。设备未上报选项时显示独立“未读取”占位，不假定第一项已选中。

主界面删除听力保护，提示音等级移入实验室。触控开关和八个左右耳手势独立成页，主界面设置入口与实验室同级。实验室只放入耳检测、提示音等级、极限降噪、降噪触控循环。降噪循环遵循至少两种模式约束。超级大师调音移回主界面。保留0.4.0通知栏控制。

黑、银、紫配色只保存在本地并改变主图，不发送硬件配色命令。每个PNG通过alpha边界在绘制时自动适配，源图像素和透明通道保持不变。主界面与通知共用修正后的四模式圆形图标；移除文字假箭头，使用Miuix矢量箭头；应用与通知小图标改为真无线耳机盒，实验室左右耳用镜像的耳机矢量图。第五张的ACORITE产品外形属于其他型号，因此只参考功能布局。

## 新配色素材与提示词

本次使用 imagegen 技能和内置 image_gen 工具进行透明背景提取，没有使用外部CLI。输入分别为用户第3、第4张截图；生成式提取可能包含细微变化，未称为原始官方素材。

最终文件：
- 银色：app/src/main/res/drawable-nodpi/zircon_silver.png
- 紫色：app/src/main/res/drawable-nodpi/zircon_purple.png
- 原有黑色：app/src/main/res/drawable-nodpi/zircon_black.png

银色最终提示词：

> Use case: background-extraction. Asset type: Android app transparent product cutout. The input screenshot is the edit target. Extract ONLY the silver metallic open Zircon square charging case and its two earbuds in the upper half of the screenshot (approximately x300..780 y235..745 in original 1080x2120). Remove all UI, status bar, background, cards, text, buttons and battery indicators. Preserve the EXACT case design, frontal angle, open lid recess, materials, white LED, earbud geometry and existing silver metallic color from the input. Do not redesign or invent anything. Actual transparent background. Center the complete product with a small even transparent margin; no shadow outside product. This replaces an earphone hero image; retain original perspective and proportions.

紫色最终提示词：

> Use case: background-extraction. Asset type: Android app transparent product cutout. The input screenshot is the edit target. Extract ONLY the pale lavender purple metallic open Zircon square charging case and its two earbuds in the upper half of the screenshot (approximately x300..780 y235..745 in original 1080x2120). Remove all UI, status bar, background, cards, text, buttons and battery indicators. Preserve the EXACT case design, frontal angle, open lid recess, materials, white LED, earbud geometry and existing pale lavender purple metallic color from the input. Do not redesign or invent anything. Actual transparent background. Center the complete product with a small even transparent margin; no shadow outside product. This replaces an earphone hero image; retain original perspective and proportions.

已检查生成结果为完整方形充电盒、真实alpha透明背景，复制到项目后消费。本机没有Android手机或可用模拟器，实际Miuix浮层/页面的像素表现尚未进行实机截图核对。

## 历史：0.3.0 / 0.4.0 主界面



0.4.0 在以下主界面设计之上增加四模式通知栏；折叠与展开通知使用同一组 NoiseModeDrawable 图标。详情与实测边界见 `通知栏控制说明.md`。以下记录主界面在0.3.0完成的截图适配。

本次以用户第一张截图为主界面设计依据：浅灰背景、白色圆角卡、左对齐设备名、居中设备图、三列电量、圆形人像降噪图标、游戏/双设备分组、听力保护和提示音胶囊滑条、编码/查找/实验室入口。配对入口采用第二张图的左上角位置，点击后打开左侧设备面板，主页面不再放配对卡片。ZirconPods 保留为桌面和安装包名称。

没有复制截图中的系统时间、电量、状态栏或原版底部导航。噪声模式和电量均使用真实回包，截图里的普通模式选中态不会变成虚构默认值。当前仅提供黑色产品图。字体、原生对话框和系统控件可能因手机字体缩放而不同；没有实机截图，不称为逐像素复刻。

## 主图

工具：内置 image_gen（imagegen技能），透明背景编辑；输入为用户第二张截图。未使用外部图像API/CLI。

已保存的项目素材：`app/src/main/res/drawable-nodpi/zircon_black.png`。
项目相对路径：`app/src/main/res/drawable-nodpi/zircon_black.png`。

素材已检查为 RGBA，透明角像素为(0,0,0,0)。完整图1398×1125；ProductImageView仅在绘制时略去透明边缘，显示产品主体，不修改原截图。图像是从第二张编辑提取的素材，可能存在生成式编辑的细微变化。

最终处理提示词（原文）：

> Use case: background-extraction. Asset type: exact transparent product cutout for an Android earphone control app. Extract ONLY the black open Zircon charging case with the two black earbuds shown in the upper half of the supplied screenshot (approximately x=300..780, y=240..745 in the 1080x2120 source). Remove every piece of UI, background, status bar, text, white card, battery indicators, and navigation. Preserve the exact product design, angle, black materials, reflections, white LED, perspective, proportions, silhouette, lid recesses, and all visible hardware. Do not invent a different headphone or redraw the case design. No added text or shadow outside the object. Actual transparent background. Center the isolated product with only a small even transparent margin around its bounding box. The output will replace the generic earbud artwork in the app.

## 新增可见控制

听力保护0..6（关闭/75/80/85/90/95/100dB）、提示音0..5、查找1左/2右/3双耳/4停止，均从既有RoseLink的RsCommonV2Sender/Receiver与锆势配置核对后实现。不是用视觉样例猜测命令。详见协议补充说明.md。

更新后59项协议测试通过，编译/签名/对齐通过；所有实际听感、dB效果和设备行为尚待实机联调。
