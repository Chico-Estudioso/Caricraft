package com.chicoestudioso.caricraft.item;

import com.chicoestudioso.caricraft.core.phone.PhonePolicy.PhoneTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

/**
 * Vínculo de un CariPhone con su dueño, guardado en los datos del ítem ({@code custom_data.caricraft_phone}).
 * Lo usan cliente y servidor; las decisiones las toma el servidor.
 */
public final class PhoneBinding {
    private static final String KEY = "caricraft_phone";

    /**
     * Si el CariPhone es personal (un único teléfono por jugador). Lo fija el servidor con su configuración;
     * en el cliente se asume que sí para que la interfaz no deje guardarlo en contenedores.
     */
    private static volatile boolean personal = true;

    private PhoneBinding() {
    }

    public static boolean personal() {
        return personal;
    }

    public static void setPersonal(boolean value) {
        personal = value;
    }

    public static boolean isPhone(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.CARIPHONE);
    }

    /** Teléfono personal: está activado y el ítem es un CariPhone. */
    public static boolean isProtected(ItemStack stack) {
        return personal && isPhone(stack);
    }

    /** Lee el vínculo; un teléfono sin dueño devuelve {@link PhoneTag#unbound()}. */
    public static PhoneTag read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) {
            return PhoneTag.unbound();
        }
        CompoundTag tag = data.copyTag().getCompound(KEY);
        if (!tag.hasUUID("owner")) {
            return PhoneTag.unbound();
        }
        return new PhoneTag(tag.getUUID("owner"), tag.getString("name"), tag.getInt("serial"));
    }

    public static void bind(ItemStack stack, UUID owner, String ownerName, int serial) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("owner", owner);
            tag.putString("name", ownerName == null ? "" : ownerName);
            tag.putInt("serial", serial);
            root.put(KEY, tag);
        });
    }

    public static ItemStack create(UUID owner, String ownerName, int serial) {
        ItemStack stack = new ItemStack(ModItems.CARIPHONE);
        bind(stack, owner, ownerName, serial);
        return stack;
    }

    /** El jugador puede usar este teléfono: es suyo o aún no tiene dueño. */
    public static boolean usableBy(ItemStack stack, UUID player) {
        if (!isPhone(stack)) {
            return false;
        }
        PhoneTag tag = read(stack);
        return !tag.bound() || tag.owner().equals(player);
    }
}
