package com.mochilas;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Mochilas: al hacer clic derecho con una mochila en la mano se abre un menu
 * de 54 slots (6 filas, como un cofre doble). El contenido se guarda en el
 * propio item. Funciona solo del lado del servidor (no registra items nuevos).
 */
public class MochilasMod implements ModInitializer {

    public static final int FILAS = 6;
    public static final int SLOTS = FILAS * 9;
    /** Clave dentro de minecraft:custom_data que marca a un item como mochila. */
    public static final String CLAVE = "mochila";

    @Override
    public void onInitialize() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!esMochila(stack)) {
                return InteractionResult.PASS;
            }
            // Solo se abre si hay una unica mochila en el stack (evita duplicar contenido).
            if (stack.getCount() == 1 && player instanceof ServerPlayer servidor) {
                abrir(servidor, hand, stack);
            }
            return InteractionResult.SUCCESS;
        });
    }

    public static boolean esMochila(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CustomData datos = stack.get(DataComponents.CUSTOM_DATA);
        return datos != null && datos.contains(CLAVE);
    }

    private static void abrir(ServerPlayer jugador, InteractionHand mano, ItemStack stack) {
        Mochila contenedor = new Mochila(jugador, mano, stack);
        jugador.openMenu(new SimpleMenuProvider(
                (id, inventario, p) -> ChestMenu.sixRows(id, inventario, contenedor),
                stack.getHoverName()));
    }

    /** Contenedor de 54 slots que se sincroniza con el componente minecraft:container del item. */
    private static class Mochila extends SimpleContainer {
        private final ItemStack mochila;
        private final InteractionHand mano;

        Mochila(Player jugador, InteractionHand mano, ItemStack mochila) {
            super(SLOTS);
            this.mochila = mochila;
            this.mano = mano;

            ItemContainerContents guardado =
                    mochila.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
            guardado.copyInto(items);
            for (int i = 0; i < SLOTS; i++) {
                setItem(i, items.get(i));
            }
            // Se registra despues de llenar para no escribir al abrir.
            addListener(c -> guardar());
        }

        private void guardar() {
            List<ItemStack> lista = new ArrayList<>(SLOTS);
            for (int i = 0; i < SLOTS; i++) {
                lista.add(getItem(i).copy());
            }
            mochila.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(lista));
        }

        /** No se pueden meter mochilas dentro de mochilas. */
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !esMochila(stack);
        }

        /** Si la mochila sale de la mano (se mueve, se tira, etc.) el menu se cierra. */
        @Override
        public boolean stillValid(Player jugador) {
            return !mochila.isEmpty() && jugador.getItemInHand(mano) == mochila;
        }
    }
}
