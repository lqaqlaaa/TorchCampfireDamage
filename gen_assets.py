#!/usr/bin/env python3
"""生成守卫火把/守卫篝火的资源文件（模型、方块状态、物品模型、掉落表、语言）"""
import json
import os

BASE = "/storage/emulated/0/Download/我的世界mod/TorchCampfireDamage/src/main/resources"
MOD = "torchcampfiredamage"

def write(path, obj):
    full = os.path.join(BASE, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
    print("✓", path)

# ---------- 守卫火把（方块模型：复用原版火把形状） ----------
write(f"assets/{MOD}/models/block/ward_torch.json", {
    "parent": "minecraft:block/template_torch",
    "textures": {"torch": "minecraft:block/torch"}
})

write(f"assets/{MOD}/models/block/ward_wall_torch.json", {
    "parent": "minecraft:block/template_torch_wall",
    "textures": {"torch": "minecraft:block/torch"}
})

write(f"assets/{MOD}/models/item/ward_torch.json", {
    "parent": "minecraft:item/generated",
    "textures": {"layer0": "minecraft:block/torch"}
})

# 火把方块状态（含墙火把的四个朝向）
write(f"assets/{MOD}/blockstates/ward_torch.json", {
    "variants": {"": {"model": f"{MOD}:block/ward_torch"}}
})

write(f"assets/{MOD}/blockstates/ward_wall_torch.json", {
    "variants": {
        "facing=east":  {"model": f"{MOD}:block/ward_wall_torch"},
        "facing=north": {"model": f"{MOD}:block/ward_wall_torch", "y": 270},
        "facing=south": {"model": f"{MOD}:block/ward_wall_torch", "y": 90},
        "facing=west":  {"model": f"{MOD}:block/ward_wall_torch", "y": 180}
    }
})

# ---------- 守卫篝火 ----------
# 篝火有多状态：lit + facing。为简单起见复用原版篝火模型
for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
    for lit in ["true", "false"]:
        pass  # 统一在下面 blockstate 里生成

variants = {}
for lit in ["true", "false"]:
    for facing in ["north", "east", "south", "west"]:
        key = f"facing={facing},lit={lit},signal_fire=false"
        model = "minecraft:block/campfire_off" if lit == "false" else "minecraft:block/campfire"
        variants[key] = {"model": model}
        key2 = f"facing={facing},lit={lit},signal_fire=true"
        model2 = "minecraft:block/campfire_off" if lit == "false" else "minecraft:block/campfire"
        variants[key2] = {"model": model2}

write(f"assets/{MOD}/blockstates/ward_campfire.json", {"variants": variants})

write(f"assets/{MOD}/models/item/ward_campfire.json", {
    "parent": "minecraft:item/generated",
    "textures": {"layer0": "minecraft:item/campfire"}
})

# ---------- 掉落表 ----------
write(f"data/{MOD}/loot_table/blocks/ward_torch.json", {
    "type": "minecraft:block",
    "pools": [{
        "rolls": 1,
        "entries": [{"type": "minecraft:item", "name": f"{MOD}:ward_torch"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]
    }]
})

write(f"data/{MOD}/loot_table/blocks/ward_campfire.json", {
    "type": "minecraft:block",
    "pools": [{
        "rolls": 1,
        "entries": [{"type": "minecraft:item", "name": f"{MOD}:ward_campfire"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]
    }]
})

print("\n全部资源文件生成完成")
