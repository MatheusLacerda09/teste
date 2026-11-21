package br.com.lasthorizon.akuma.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

/** Conteúdo do tooltip da fruta (tudo que aparece logo abaixo do nome). */
public record FruitTooltip(List<Entry> entries) implements TooltipComponent {

    public enum Icon { NONE, RUBBER, UP, DOWN }

    public record Badge(Component label, int color, int glyph) {
        public Badge(Component label, int color) { this(label, color, 0); }
    }

    /** Uma linha: etiquetas, texto (com ícone opcional) ou espaço vazio. */
    public record Entry(List<Badge> badges, Icon icon, Component text, int gap) {
        public static Entry badges(Badge... b) { return new Entry(List.of(b), Icon.NONE, null, 0); }
        public static Entry line(Icon icon, Component text) { return new Entry(List.of(), icon, text, 0); }
        public static Entry gap(int px) { return new Entry(List.of(), Icon.NONE, null, px); }
    }
}
