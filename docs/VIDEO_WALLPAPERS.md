# 5.15 · 4K 动态壁纸

桌面壁纸不再随应用图标变色。保留图标自身的焦点效果。视频切换和解码器释放在独立线程执行，设置即时生效。

| 壁纸 | 内容 | 分辨率 | 循环 |
|---|---|---|---|
| 珊瑚海洋 | 蓝色海水、密集鱼群、蝶鱼、小丑鱼，多镜头柔和切换 | 3840×2160 | 20 秒 |
| 水草鱼缸 | 用户确认的绿色水草缸、孔雀鱼与灯鱼 | 3840×2160 | 16 秒 |
| 伊瓜苏瀑布 | 远景航拍瀑布群、峡谷、水雾和彩虹 | 3840×2160 | 18 秒 |
| 桃花纷飞 | 大片远景桃林、飘落花瓣、轻雾 | 3840×2160 | 16 秒 |
| 热带海岸 | 碧蓝海水与沙滩 | 3840×2160 | 11 秒 |

海洋、鱼缸、瀑布、海岸使用横向 4K 视频源；桃花使用 AI 生成底图放大并制作 4K 动效。全部输出为 H.264 High Level 5.1、30 fps、无音轨，APK 内离线播放。素材许可独立于源码 MIT 许可，海洋、瀑布和海岸使用 [Pexels License](https://www.pexels.com/license/)。

- 珊瑚海洋：[Adrien JACTA](https://www.pexels.com/video/vibrant-coral-reef-and-tropical-fish-in-clear-waters-31991094/), [JUN HO LEE](https://www.pexels.com/video/vibrant-coral-reef-with-school-of-fish-underwater-35480156/), [Tom Fisk](https://www.pexels.com/video/clownfish-and-sea-anemone-4001923/)。
- 水草鱼缸：[Motion Backgrounds / Planted Aquarium](https://motionbgs.com/planted-aquarium)。
- 伊瓜苏瀑布：[Sergey Guk](https://www.pexels.com/video/stunning-aerial-view-of-iguazu-falls-33054007/)。
- 桃花纷飞：内置 imagegen 生成远景桃林底图，本地 Python / Pillow 制作 110 层花瓣与轻雾循环，无实拍来源。
- 热带海岸：[Hāyca](https://www.pexels.com/video/serene-tropical-beach-with-turquoise-waters-34306594/)。

五套可固定选择或轮换。电视桌面输出为 1080p，视频解码为原生 3840×2160，最终画面按电视当前输出缩放。未修改电视输出设置。文件散列见 assets/video-wallpapers/manifest.json。

5.15 实机验证：使用 MediaExtractor + MediaCodec 直接硬件解码，绕过小米固件 MediaPlayer 释放卡住的问题。已连续切换海洋、鱼缸、瀑布并确认新场景出帧；268 项 JVM 测试通过。电视截图背景像素变化确认视频持续运动。

水草鱼缸按用户确认的参考替换，来源站点个人壁纸用途；该视频独立于源码许可，不作为 Pexels 或 MIT 素材发布。鱼缸未放大低分辨率源。桃花为用户确认的“生成底图＋本地动效”方案；底图原始尺寸 1672×941，成片 3840×2160，不能称为原生 4K 实拍。

5.15：没有已保存选择的新安装，默认固定播放水草鱼缸（aquarium）；无效选择也回退到水草鱼缸。用户可手动选择五套轮换或其他壁纸。升级时保留已有的有效选择。

仓库中的 ocean.mp4 和 waterfall.mp4 以 launcher/app/video-parts 的编号分段保存。Gradle 在构建前自动合并，校验清单中的文件长度与 SHA-256；还原后的字节与发布 APK 中的视频一致。
