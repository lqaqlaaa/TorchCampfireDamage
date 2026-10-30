package com.torchcampfiredamage.compat;

import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;

/**
 * 守卫护符的 Curios 饰品能力。
 * <p>
 * 只是把"这件物品可以作为饰品放进饰品栏"这个身份标记出来。
 * 实际的效果判定在 {@code OffhandTorchHandler} 里做
 * （副手 / 饰品栏任一处有护符即生效）。
 */
public class WardCharmCurio implements ICurio {

    private final ItemStack stack;

    public WardCharmCurio(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public ItemStack getStack() {
        return stack;
    }

    /** 允许被装备进饰品栏（默认就是 true，这里显式写明便于以后调整） */
    @Override
    public boolean canEquip(SlotContext slotContext) {
        return true;
    }

    /** 护符效果不依赖装备数量叠加，但同一个人戴多个也没必要阻止 */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext) {
        return true;
    }
}