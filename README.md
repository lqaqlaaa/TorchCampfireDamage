# Torch Campfire Damage - 火把篝火伤害模组

一个 NeoForge 1.21.1 模组，让篝火和火把能够对周围的敌对生物造成伤害。

## 功能特性

- ✅ **篝火和火把伤害**：普通篝火、灵魂篝火、普通火把、灵魂火把都可以对敌对生物造成伤害
- ✅ **玩家归属判定**：伤害算作最近玩家的攻击，掉落物和经验归该玩家所有
- ✅ **掉落物传送**：被篝火/火把杀死的生物掉落物会自动传送到篝火/火把位置
- ✅ **多篝火/火把支持**：多个篝火或火把不会冲突，各自独立工作
- ✅ **多玩家支持**：多个玩家不会冲突，各自归属自己的篝火/火把
- ✅ **完全可配置**：范围、伤害量、伤害间隔都可以自定义

## 配置选项

配置文件位于 `config/torchcampfiredamage-common.toml`：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `damageRadius` | 8 | 检测范围（方块）|
| `damageAmount` | 2.0 | 每次伤害量（2.0 = 1颗心）|
| `damageInterval` | 20 | 伤害间隔（tick，20 = 1秒）|
| `enableCampfire` | true | 启用普通篝火 |
| `enableTorch` | true | 启用普通火把 |
| `enableSoulCampfire` | true | 启用灵魂篝火 |
| `enableSoulTorch` | true | 启用灵魂火把 |
| `teleportDrops` | true | 传送掉落物到篝火/火把位置 |
| `countAsPlayerKill` | true | 算作玩家击杀 |
| `ignoreNamedMobs` | true | 忽略被命名的生物 |

## 工作原理

1. **扫描**：每5tick扫描一次玩家周围的篝火和火把
2. **归属判定**：每个篝火/火把归属于最近的玩家
3. **伤害执行**：到达伤害间隔时，对范围内的敌对生物造成伤害
4. **掉落物传送**：被伤害的生物死亡时，掉落物传送到篝火/火把位置

## 构建

```bash
./gradlew build
```

构建产物位于 `build/libs/` 目录。

## 安装

1. 安装 NeoForge 1.21.1
2. 将模组 jar 文件放入 `mods` 文件夹
3. 启动游戏

## 兼容性

- Minecraft 1.21.1
- NeoForge 21.1.x

## 许可证

MIT License