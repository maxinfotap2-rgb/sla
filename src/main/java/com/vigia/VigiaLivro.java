package com.vigia;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/** O livro com o poema e o botão [CONCORDAR]. */
public final class VigiaLivro {
	private VigiaLivro() { }

	private static final String[] PAGINAS = {
			"      O VIGIA\n\n"
					+ "Quando a lua some\natrás do monte\n"
					+ "e o vento cala\no seu lamento,\n"
					+ "há alguém parado\nno horizonte\n"
					+ "que não respira\nnem faz movimento.",

			"Ele mora onde\na madeira estala,\n"
					+ "no vidro frio\nda tua janela;\n"
					+ "não grita, não corre,\nnão fala —\n"
					+ "só espera que\napagues a vela.",

			"Se um dia sentires\num olhar pesado\n"
					+ "descendo nas costas,\ndevagar,\n"
					+ "não vires.\nNão olhes de lado.\n"
					+ "Ele já sabe\nonde te encontrar.",
	};

	private static final String FINAL =
			"Quem lê estas linhas\naceita o trato:\n"
					+ "a noite é dele,\ne tu és o retrato.\n\n\n";

	public static ItemStack criar() {
		List<Filterable<Component>> paginas = new ArrayList<>();
		for (String texto : PAGINAS) {
			paginas.add(Filterable.passThrough(Component.literal(texto)));
		}

		Component botao = Component.literal("   [ CONCORDAR ]")
				.withStyle(style -> style
						.withColor(ChatFormatting.DARK_RED)
						.withBold(true)
						.withClickEvent(new ClickEvent.RunCommand("/vigia concordar"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Não há volta."))));

		Component ultima = Component.empty()
				.append(Component.literal(FINAL))
				.append(botao);
		paginas.add(Filterable.passThrough(ultima));

		ItemStack livro = new ItemStack(Items.WRITTEN_BOOK);
		livro.set(DataComponents.WRITTEN_BOOK_CONTENT,
				new WrittenBookContent(Filterable.passThrough("O Vigia"), "???", 0, paginas, true));
		return livro;
	}
}
