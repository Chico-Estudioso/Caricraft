package com.chicoestudioso.caricraft.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * El CariPhone. Solo abre la interfaz en el cliente; no contiene reglas de negocio.
 */
public class CariPhoneItem extends Item {
    /** Lo asigna el inicializador del cliente. En un servidor dedicado no hace nada. */
    private static Runnable clientOpener = () -> {
    };

    public CariPhoneItem(Properties properties) {
        super(properties);
    }

    public static void setClientOpener(Runnable opener) {
        clientOpener = opener;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            clientOpener.run();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
