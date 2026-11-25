package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.core.CaricraftState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Contenedor de {@link CaricraftState} en el almacenamiento de datos del Overworld ({@code data/caricraft.dat}). */
public final class CaricraftSavedData extends SavedData {
    public static final String NAME = "caricraft";
    private static final Factory<CaricraftSavedData> FACTORY = new Factory<>(
            CaricraftSavedData::new, CaricraftSavedData::load, null);

    private final CaricraftState state;
    /** Contenido de los CariBox por grupo (objetos de Minecraft, por eso no están en el núcleo). */
    private final Map<UUID, SimpleContainer> boxes = new HashMap<>();
    /** Todos los cofres creados (también los sustituidos al ampliar), para reconocerlos por identidad. */
    private final java.util.Set<SimpleContainer> knownBoxes =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private CaricraftSavedData() {
        this(new CaricraftState());
    }

    private CaricraftSavedData(CaricraftState state) {
        this.state = state;
    }

    private static CaricraftSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CaricraftSavedData data = new CaricraftSavedData(StateCodec.read(tag));
        CompoundTag boxes = tag.getCompound("caribox");
        for (String key : boxes.getAllKeys()) {
            try {
                CompoundTag box = boxes.getCompound(key);
                int size = Math.max(9, Math.min(54, box.getInt("size")));
                NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(box, items, registries);
                SimpleContainer container = new SimpleContainer(size);
                for (int i = 0; i < size; i++) {
                    container.setItem(i, items.get(i));
                }
                data.attach(UUID.fromString(key), container);
            } catch (RuntimeException e) {
                com.chicoestudioso.caricraft.CaricraftMod.LOGGER.error("CariBox {} ilegible", key, e);
            }
        }
        return data;
    }

    /** Cofre del grupo con el tamaño pedido (lo crea o lo amplía conservando los objetos). */
    public SimpleContainer box(UUID partyId, int size) {
        SimpleContainer current = boxes.get(partyId);
        if (current != null && current.getContainerSize() >= size) {
            return current;
        }
        SimpleContainer resized = new SimpleContainer(size);
        if (current != null) {
            for (int i = 0; i < current.getContainerSize(); i++) {
                resized.setItem(i, current.getItem(i));
            }
        }
        attach(partyId, resized);
        setDirty();
        return resized;
    }

    /** Cofre existente o {@code null}. */
    public SimpleContainer existingBox(UUID partyId) {
        return boxes.get(partyId);
    }

    public SimpleContainer removeBox(UUID partyId) {
        SimpleContainer removed = boxes.remove(partyId);
        setDirty();
        return removed;
    }

    /** ¿Es este contenedor un CariBox (actual o antiguo)? */
    public boolean isBox(net.minecraft.world.Container container) {
        return container instanceof SimpleContainer simple && knownBoxes.contains(simple);
    }

    private void attach(UUID partyId, SimpleContainer container) {
        knownBoxes.add(container);
        container.addListener(changed -> setDirty());
        boxes.put(partyId, container);
    }

    public static CaricraftSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public CaricraftState state() {
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(StateCodec.write(state));
        CompoundTag boxesTag = new CompoundTag();
        boxes.forEach((partyId, container) -> {
            if (!state.parties().containsKey(partyId) || container.isEmpty()) {
                return; // grupos que ya no existen o cofres vacíos no se guardan
            }
            CompoundTag box = new CompoundTag();
            box.putInt("size", container.getContainerSize());
            NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
            for (int i = 0; i < container.getContainerSize(); i++) {
                items.set(i, container.getItem(i));
            }
            ContainerHelper.saveAllItems(box, items, registries);
            boxesTag.put(partyId.toString(), box);
        });
        tag.put("caribox", boxesTag);
        return tag;
    }
}
