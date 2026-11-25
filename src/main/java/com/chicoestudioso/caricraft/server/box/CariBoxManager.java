package com.chicoestudioso.caricraft.server.box;

import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * CariBox: cofre compartido del dúo que se abre desde el teléfono, esté donde esté el jugador.
 * Ambos miembros pueden tenerlo abierto a la vez (como un cofre normal con dos jugadores).
 */
public final class CariBoxManager {
    private CariBoxManager() {
    }

    public static SimpleContainer container(CaricraftServer mod, Party party) {
        return mod.savedData().box(party.id(), mod.core().box().slots(party));
    }

    public static ActionResult open(CaricraftServer mod, ServerPlayer player) {
        CaricraftCore core = mod.core();
        Party party = core.partyOf(player.getUUID());
        if (!core.box().hasApp(party)) {
            return ActionResult.fail("app_locked", "caribox");
        }
        int rows = core.box().rows(party);
        SimpleContainer box = container(mod, party);
        // Si al ampliarlo alguien tenía abierto el cofre viejo, se le cierra en este mismo tick: así no puede
        // sacar objetos que ya se copiaron al nuevo (evita duplicados).
        for (UUID member : party.members()) {
            ServerPlayer online = mod.server().getPlayerList().getPlayer(member);
            if (online != null && online.containerMenu instanceof ChestMenu menu && menu.getContainer() != box
                    && mod.savedData().isBox(menu.getContainer())) {
                online.closeContainer();
            }
        }
        MenuType<ChestMenu> type = switch (rows) {
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> MenuType.GENERIC_9x3;
        };
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(type, id, inventory, box, rows),
                Component.translatable("caricraft.caribox.title", rows * 9)));
        return null; // la respuesta es el propio cofre
    }

    public static int usedSlots(CaricraftServer mod, Party party) {
        SimpleContainer box = mod.savedData().existingBox(party.id());
        if (box == null) {
            return 0;
        }
        int used = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            if (!box.getItem(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /**
     * Al unirse a un dúo: lo que había en el cofre individual de quien se une pasa al cofre del dúo; lo que no
     * quepa vuelve a su inventario (o cae a sus pies).
     */
    public static void onMerge(CaricraftServer mod, UUID sourcePartyId, Party target, ServerPlayer joiner) {
        SimpleContainer source = mod.savedData().removeBox(sourcePartyId);
        if (source == null || source.isEmpty()) {
            return;
        }
        SimpleContainer destination = container(mod, target);
        int returned = 0;
        for (int i = 0; i < source.getContainerSize(); i++) {
            ItemStack stack = source.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = destination.addItem(stack.copy());
            if (!rest.isEmpty()) {
                returned += rest.getCount();
                if (!joiner.getInventory().add(rest) && !rest.isEmpty()) {
                    joiner.drop(rest, false);
                }
            }
        }
        if (returned > 0) {
            mod.notify(joiner, true, "caricraft.notify.caribox_returned", java.util.List.of(String.valueOf(returned)));
        }
        mod.markDirty();
    }
}
