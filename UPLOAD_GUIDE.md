# GitHub 上传指南

## 步骤 1：下载 ZIP 文件

ZIP 文件已创建在：
```
/storage/emulated/0/Download/我的世界mod/TorchCampfireDamage.zip
```

## 步骤 2：上传到 GitHub

### 方法 A：Web 界面上传（推荐）

1. 访问你的仓库：https://github.com/lqaqlaaa/TorchCampfireDamage
2. 点击 "Add file" → "Upload files"
3. 将 `TorchCampfireDamage.zip` 拖拽到页面中
4. 点击 "Commit changes"

### 方法 B：使用 GitHub CLI

如果你安装了 GitHub CLI：

```bash
# 登录
gh auth login

# 上传文件
gh repo upload lqaqlaaa/TorchCampfireDamage /storage/emulated/0/Download/我的世界mod/TorchCampfireDamage.zip
```

## 步骤 3：设置仓库为公开

1. 进入仓库页面：https://github.com/lqaqlaaa/TorchCampfireDamage
2. 点击 "Settings"（设置）
3. 滚动到最底部 "Danger Zone"
4. 点击 "Change repository visibility"
5. 选择 "Make public"
6. 输入仓库名称 `TorchCampfireDamage` 确认

## 步骤 4：启用 GitHub Actions

1. 进入仓库页面
2. 点击 "Actions" 标签
3. 如果提示 "Workflows aren't being run on this repository"，点击 "I understand my workflows, go ahead and enable them"
4. 点击 "Build Mod" workflow
5. 点击 "Run workflow" 按钮
6. 等待构建完成（约 2-3 分钟）
7. 在 "Artifacts" 中下载 JAR 文件

## 步骤 5：下载构建好的 JAR

构建完成后：
1. 进入 Actions 页面
2. 点击最新的构建记录
3. 在 "Artifacts" 部分下载 `torchcampfiredamage-jar`
4. 解压后得到 `torchcampfiredamage-1.0.0.jar`

## 完成！

现在你可以：
- 将 JAR 文件放入 Minecraft 的 `mods` 文件夹
- 启动游戏
- 享受篝火和火把伤害敌对生物的功能！

## 配置

配置文件位于：
```
.minecraft/config/torchcampfiredamage-common.toml
```

你可以修改以下配置：
- `damageRadius`：检测范围（默认 8 格）
- `damageAmount`：伤害量（默认 2.0 = 1 颗心）
- `damageInterval`：伤害间隔（默认 20 tick = 1 秒）
- `enableCampfire`：启用普通篝火（默认 true）
- `enableTorch`：启用普通火把（默认 true）
- `teleportDrops`：传送掉落物（默认 true）
- `countAsPlayerKill`：算作玩家击杀（默认 true）