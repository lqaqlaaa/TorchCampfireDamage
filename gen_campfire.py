#!/usr/bin/env python3
"""内联生成篝火/篝火熄灭模型（不依赖 minecraft:block/campfire 继承，避免贴图丢失）"""
import json
import os

BASE = "/storage/emulated/0/Download/我的世界mod/TorchCampfireDamage/src/main/resources/assets/torchcampfiredamage"
MOD = "torchcampfiredamage"

def w(path, obj):
    full = os.path.join(BASE, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
    print("✓", path)

# 原版 campfire 模型（点燃）：木柴 + 火焰
campfire_model = {
    "textures": {
        "particle": "minecraft:block/campfire_log",
        "log": "minecraft:block/campfire_log",
        "log_lit": "minecraft:block/campfire_log_lit",
        "fire": "minecraft:block/campfire_fire"
    },
    "elements": [
        # 前方木头
        {
            "from": [0, 0, 0],
            "to": [16, 4, 4],
            "faces": {
                "down":  {"uv": [0, 12, 16, 16], "texture": "#log"},
                "up":    {"uv": [0, 12, 16, 16], "texture": "#log_lit"},
                "north": {"uv": [0, 0, 16, 4],   "texture": "#log"},
                "south": {"uv": [0, 0, 16, 4],   "texture": "#log"},
                "west":  {"uv": [0, 12, 4, 16],  "texture": "#log"},
                "east":  {"uv": [12, 12, 16, 16], "texture": "#log"}
            }
        },
        # 后方木头
        {
            "from": [0, 0, 12],
            "to": [16, 4, 16],
            "faces": {
                "down":  {"uv": [0, 12, 16, 16], "texture": "#log"},
                "up":    {"uv": [0, 12, 16, 16], "texture": "#log_lit"},
                "north": {"uv": [0, 0, 16, 4],   "texture": "#log"},
                "south": {"uv": [0, 0, 16, 4],   "texture": "#log"},
                "west":  {"uv": [0, 12, 4, 16],  "texture": "#log"},
                "east":  {"uv": [12, 12, 16, 16], "texture": "#log"}
            }
        },
        # 左方木头
        {
            "from": [0, 0, 4],
            "to": [4, 4, 12],
            "faces": {
                "down":  {"uv": [4, 12, 12, 16], "texture": "#log"},
                "up":    {"uv": [4, 12, 12, 16], "texture": "#log_lit"},
                "north": {"uv": [0, 12, 4, 16],  "texture": "#log"},
                "south": {"uv": [12, 12, 16, 16], "texture": "#log"},
                "west":  {"uv": [0, 0, 16, 4],   "texture": "#log"},
                "east":  {"uv": [0, 0, 16, 4],   "texture": "#log"}
            }
        },
        # 右方木头
        {
            "from": [12, 0, 4],
            "to": [16, 4, 12],
            "faces": {
                "down":  {"uv": [4, 12, 12, 16], "texture": "#log"},
                "up":    {"uv": [4, 12, 12, 16], "texture": "#log_lit"},
                "north": {"uv": [0, 12, 4, 16],  "texture": "#log"},
                "south": {"uv": [12, 12, 16, 16], "texture": "#log"},
                "west":  {"uv": [0, 0, 16, 4],   "texture": "#log"},
                "east":  {"uv": [0, 0, 16, 4],   "texture": "#log"}
            }
        },
        # 火焰
        {
            "from": [4, 4, 4],
            "to": [12, 16, 12],
            "shade": False,
            "light": 15,
            "faces": {
                "down":  {"uv": [0, 0, 16, 16], "texture": "#fire"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#fire"},
                "north": {"uv": [0, 0, 16, 16], "texture": "#fire"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#fire"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#fire"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#fire"}
            }
        }
    ]
}

# 熄灭版：只有木头（同 UV），无火焰
campfire_off_model = {
    "textures": {
        "particle": "minecraft:block/campfire_log",
        "log": "minecraft:block/campfire_log",
        "log_lit": "minecraft:block/campfire_log_lit"
    },
    "elements": campfire_model["elements"][:4]
}

w("models/block/ward_campfire.json", campfire_model)
w("models/block/ward_campfire_off.json", campfire_off_model)

print("\n篝火模型已内联完成")
