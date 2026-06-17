# 云端编译 APK

推荐用 GitHub Actions 编译，电脑不需要安装 Android Studio。

## 步骤

1. 注册或登录 GitHub。
2. 新建一个仓库，可以选择 Private。
3. 把本项目里的所有文件上传到仓库根目录。
4. 打开仓库页面的 Actions。
5. 选择 `Build Android Debug APK`。
6. 点击 `Run workflow`。
7. 等构建完成后，打开本次运行记录。
8. 在 Artifacts 里下载 `CarrotGuardOverlay-debug-apk`。
9. 解压后得到 `app-debug.apk`，传到安卓手机安装。

## 手机安装

如果手机提示不能安装未知来源应用，需要在系统设置里允许浏览器、文件管理器或聊天软件安装未知来源应用。

这是 debug 包，适合自己测试，不适合发布到应用商店。
