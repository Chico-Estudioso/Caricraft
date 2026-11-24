package com.chicoestudioso.caricraft.item;

import com.chicoestudioso.caricraft.CaricraftConstants;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Registro de ítems del mod. */
public final class ModItems {
    public static final Item CARIPHONE = new CariPhoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    private ModItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, CaricraftConstants.id("cariphone"), CARIPHONE);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(CARIPHONE));
    }
}
