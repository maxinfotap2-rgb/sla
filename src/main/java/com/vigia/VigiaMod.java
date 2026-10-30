package com.vigia;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class VigiaMod implements ModInitializer {
	public static final String MOD_ID = "vigia";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	// ---- Entidade: /summon vigia:vigia ----
	public static final ResourceKey<EntityType<?>> VIGIA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("vigia"));
	public static final EntityType<VigiaEntity> VIGIA = EntityType.Builder.of(VigiaEntity::new, MobCategory.MONSTER)
			.sized(0.6F, 2.5F)
			.clientTrackingRange(10)
			.build(VIGIA_KEY);

	// ---- Dados salvos no jogador / na entidade ----
	public static final AttachmentType<Boolean> RECEBEU_LIVRO = AttachmentRegistry.create(id("recebeu_livro"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	public static final AttachmentType<Boolean> CONCORDOU = AttachmentRegistry.create(id("concordou"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	public static final AttachmentType<Boolean> E_EVENTO = AttachmentRegistry.createPersistent(id("e_evento"), Codec.BOOL);

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, VIGIA_KEY, VIGIA);
		FabricDefaultAttributeRegistry.register(VIGIA, VigiaEntity.createAttributes());

		// Primeiro spawn no mundo: recebe o livro.
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			ServerPlayer player = listener.player;
			if (!Boolean.TRUE.equals(player.getAttached(RECEBEU_LIVRO))) {
				player.setAttached(RECEBEU_LIVRO, true);
				player.getInventory().add(VigiaLivro.criar());
				player.sendSystemMessage(Component.literal("Há um livro na sua mochila. Leia até o fim.")
						.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		});

		// Evento da madeira.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
				VigiaEvents.aoQuebrarBloco(level, player, pos, state));

		// Eventos aleatórios (vidro / longe).
		ServerTickEvents.END_SERVER_TICK.register(VigiaEvents::tick);

		// Comandos.
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
				Commands.literal("vigia")
						// Usado pelo botão do livro. Qualquer jogador pode usar.
						.then(Commands.literal("concordar")
								.executes(ctx -> VigiaEvents.concordar(ctx.getSource().getPlayerOrException())))
						// Pega outro livro, se perder o primeiro.
						.then(Commands.literal("livro")
								.executes(ctx -> {
									ctx.getSource().getPlayerOrException().getInventory().add(VigiaLivro.criar());
									return 1;
								}))
						// Comandos de teste (só operador / cheats ligados).
						.then(Commands.literal("evento")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.literal("madeira").executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayerOrException();
									BlockPos frente = p.blockPosition().relative(p.getDirection(), 2);
									VigiaEvents.eventoMadeira(p, frente);
									return 1;
								}))
								.then(Commands.literal("vidro").executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayerOrException();
									BlockPos vidro = VigiaEvents.acharVidro(p);
									if (vidro == null || !VigiaEvents.eventoVidro(p, vidro)) {
										ctx.getSource().sendFailure(Component.literal("Nenhum vidro com espaço do outro lado num raio de 6 blocos."));
										return 0;
									}
									return 1;
								}))
								.then(Commands.literal("longe").executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayerOrException();
									if (!VigiaEvents.eventoLonge(p)) {
										ctx.getSource().sendFailure(Component.literal("Não achei lugar na superfície à sua frente."));
										return 0;
									}
									return 1;
								})))
						// Desfaz o trato e devolve o livro no próximo login (para testar de novo).
						.then(Commands.literal("resetar")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayerOrException();
									p.setAttached(CONCORDOU, false);
									p.setAttached(RECEBEU_LIVRO, false);
									ctx.getSource().sendSuccess(() -> Component.literal("Trato desfeito. Saia e entre de novo para receber o livro."), false);
									return 1;
								}))));
	}
}
