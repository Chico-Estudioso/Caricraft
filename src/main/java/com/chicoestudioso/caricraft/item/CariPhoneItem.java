package com.chicoestudioso.caricraft.item;

import com.chicoestudioso.caricraft.core.phone.PhonePolicy.PhoneTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * El CariPhone. Solo abre la interfaz en el cliente; no contiene reglas de negocio. Es personal: el servidor
 * lo vincula a su dueño y se encarga de que cada jugador tenga uno y solo uno.
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
            if (PhoneBinding.usableBy(stack, player.getUUID())) {
                clientOpener.run();
            } else {
                player.displayClientMessage(Component.translatable("caricraft.phone.not_yours", PhoneBinding.read(stack).ownerName())
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** No cabe en sacos ni cajas de shulker. */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PhoneTag tag = PhoneBinding.read(stack);
        if (tag.bound()) {
            tooltip.add(Component.translatable("caricraft.phone.tooltip.owner", tag.ownerName()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("caricraft.phone.tooltip.personal").withStyle(ChatFormatting.DARK_AQUA));
            if (flag.isAdvanced()) {
                tooltip.add(Component.translatable("caricraft.phone.tooltip.serial", tag.serial()).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.translatable("caricraft.phone.tooltip.unbound").withStyle(ChatFormatting.GRAY));
        }
    }
}
