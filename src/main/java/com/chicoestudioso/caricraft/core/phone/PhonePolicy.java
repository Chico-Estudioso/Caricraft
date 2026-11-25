package com.chicoestudioso.caricraft.core.phone;

import com.chicoestudioso.caricraft.core.party.PlayerProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Reglas del CariPhone personal: cada jugador tiene uno, ligado a él y con número de serie.
 * <ul>
 *     <li>Vale el teléfono del jugador cuyo número coincide con el de su perfil; el resto se retira.</li>
 *     <li>Un teléfono sin dueño se vincula a quien lo lleve si no tiene ninguno válido.</li>
 *     <li>Si le falta, el servidor le da otro con el número siguiente: el anterior deja de valer, esté donde esté.</li>
 * </ul>
 * Java puro: el servidor reúne lo que lleva el jugador y aplica el plan.
 */
public final class PhonePolicy {
    /** Datos de un CariPhone. {@code owner} nulo significa que aún no tiene dueño. */
    public record PhoneTag(UUID owner, String ownerName, int serial) {
        public static PhoneTag unbound() {
            return new PhoneTag(null, "", 0);
        }

        public boolean bound() {
            return owner != null;
        }
    }

    public enum Action {
        /** Es el teléfono válido del jugador. */
        KEEP,
        /** No tenía dueño: se vincula al jugador con {@link Plan#serial()}. */
        BIND,
        /** Duplicado, de otro jugador o de una serie anterior: se retira. */
        REMOVE
    }

    /**
     * @param actions  qué hacer con cada teléfono, en el mismo orden en que se pasaron
     * @param issueNew dar un teléfono nuevo con {@link #serial()}
     * @param missing  el jugador no lleva ningún teléfono que valga (antes de aplicar el plan)
     * @param serial   número de serie vigente del jugador tras el plan
     */
    public record Plan(List<Action> actions, boolean issueNew, boolean missing, int serial) {
        public boolean changesAnything() {
            return issueNew || actions.stream().anyMatch(a -> a != Action.KEEP);
        }
    }

    private PhonePolicy() {
    }

    public static boolean isValid(PhoneTag tag, PlayerProfile profile) {
        return tag.bound() && tag.owner().equals(profile.uuid()) && profile.phoneSerial() > 0
                && tag.serial() == profile.phoneSerial();
    }

    /**
     * Decide qué hacer con los teléfonos que lleva el jugador. Si hay que vincular o dar uno nuevo, sube el
     * número de serie del perfil.
     *
     * @param carried          teléfonos encontrados, primero los del inventario
     * @param replaceIfMissing si no lleva ninguno válido ni sin dueño, dar uno nuevo ya (el servidor espera
     *                         unos segundos por si solo está fuera del inventario un momento)
     */
    public static Plan plan(PlayerProfile profile, List<PhoneTag> carried, boolean replaceIfMissing) {
        List<Action> actions = new ArrayList<>(Collections.nCopies(carried.size(), Action.REMOVE));

        // Si se perdieron los datos del mundo, el perfil puede ir por detrás del teléfono: se adopta.
        for (PhoneTag tag : carried) {
            if (tag.bound() && tag.owner().equals(profile.uuid()) && tag.serial() > profile.phoneSerial()) {
                profile.setPhoneSerial(tag.serial());
            }
        }
        for (int i = 0; i < carried.size(); i++) {
            if (isValid(carried.get(i), profile)) {
                actions.set(i, Action.KEEP);
                return new Plan(List.copyOf(actions), false, false, profile.phoneSerial());
            }
        }
        for (int i = 0; i < carried.size(); i++) {
            if (!carried.get(i).bound()) {
                actions.set(i, Action.BIND);
                profile.setPhoneSerial(profile.phoneSerial() + 1);
                return new Plan(List.copyOf(actions), false, true, profile.phoneSerial());
            }
        }
        if (replaceIfMissing) {
            profile.setPhoneSerial(profile.phoneSerial() + 1);
            return new Plan(List.copyOf(actions), true, true, profile.phoneSerial());
        }
        return new Plan(List.copyOf(actions), false, true, profile.phoneSerial());
    }
}
