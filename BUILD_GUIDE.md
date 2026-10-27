# 在线构建指南

由于本地构建需要下载大量依赖，我们提供以下几种构建方式：

## 方式 1：GitHub Actions（推荐）

1. 将本项目 fork 到你的 GitHub 账号
2. 进入 Actions 页面
3. 点击 "Build Mod" workflow
4. 点击 "Run workflow"
5. 等待构建完成，下载 Artifacts 中的 JAR 文件

## 方式 2：GitHub Codespaces

1. 在 GitHub 仓库页面点击 "Code" -> "Codespaces"
2. 创建新的 Codespace
3. 在终端中运行：
   ```bash
   ./gradlew build
   ```
4. 构建完成后，在 `build/libs/` 目录找到 JAR 文件

## 方式 3：Gitpod

1. 访问：https://gitpod.io/#https://github.com/你的用户名/TorchCampfireDamage
2. 等待环境初始化
3. 在终端中运行：
   ```bash
   ./gradlew build
   ```

## 方式 4：本地构建

如果你本地有 Java 21 和 Gradle：

```bash
# 使用 Gradle Wrapper
./gradlew build

# 或使用系统 Gradle
gradle build
```

## 方式 5：使用预编译 JAR

如果你不想自己构建，可以：
1. 下载 NeoForge MDK
2. 将我们的源代码复制到 MDK 中
3. 使用 MDK 的 Gradle 构建

## 构建产物

构建成功后，JAR 文件位于：
```
build/libs/torchcampfiredamage-1.0.0.jar
```

## 安装使用

1. 确保已安装 NeoForge 1.21.1
2. 将 JAR 文件放入 `.minecraft/mods/` 目录
3. 启动游戏

## 配置

配置文件位于：
```
.minecraft/config/torchcampfiredamage-common.toml
```