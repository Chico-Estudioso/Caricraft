package com.chicoestudioso.caricraft.item;

import com.chicoestudioso.caricraft.CaricraftConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Registro de ítems del mod. */
public final class ModItems {
    /** Resistente al fuego y la lava; el servidor impide tirarlo, guardarlo o perderlo (ver {@link PhoneBinding}). */
    public static final Item CARIPHONE = new CariPhoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant());
    /** Trofeo por derrotar a un jefe del desafío (decorativo: nombre del jefe, nivel y quién lo derrotó). */
    public static final Item CHALLENGE_TROPHY = new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
            .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    private ModItems() {
    }

    public static void register() {
        // No está en las pestañas del modo creativo: el servidor da a cada jugador el suyo.
        Registry.register(BuiltInRegistries.ITEM, CaricraftConstants.id("cariphone"), CARIPHONE);
        Registry.register(BuiltInRegistries.ITEM, CaricraftConstants.id("challenge_trophy"), CHALLENGE_TROPHY);
    }
}
