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

    private ModItems() {
    }

    public static void register() {
        // No está en las pestañas del modo creativo: el servidor da a cada jugador el suyo.
        Registry.register(BuiltInRegistries.ITEM, CaricraftConstants.id("cariphone"), CARIPHONE);
    }
}
