# GitLab 云端编译 APK

如果 GitHub Actions 被禁用，可以改用 GitLab CI。

## 步骤

1. 注册或登录 GitLab。
2. 新建一个空项目，可以选择 Private。
3. 上传本项目根目录里的所有文件。
4. GitLab 会自动读取 `.gitlab-ci.yml` 并开始构建。
5. 打开左侧 `Build > Pipelines`。
6. 等 `build_debug_apk` 任务完成。
7. 点进任务，在右侧或页面上方下载 Artifacts。
8. 解压后得到 `app-debug.apk`，传到安卓手机安装。

这是 debug 包，适合自己测试。
