package com.vigia;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Toda a lógica de quando e onde o Vigia aparece. Roda só no servidor. */
public final class VigiaEvents {
	// ---- Ajustes (20 ticks = 1 segundo) ----
	/** Intervalo entre eventos aleatórios (vidro ou longe): de 3 a 7 minutos. */
	private static final int INTERVALO_MIN = 20 * 60 * 3;
	private static final int INTERVALO_MAX = 20 * 60 * 7;
	/** Chance de o Vigia aparecer ao quebrar um tronco. */
	private static final float CHANCE_MADEIRA = 0.12F;
	/** Tempo mínimo entre duas aparições na madeira: 90 segundos. */
	private static final long ESPERA_MADEIRA = 20 * 90;
	/** Raio em que se procura vidro perto do jogador. */
	private static final int RAIO_VIDRO = 6;

	private static final Map<UUID, Integer> proximoEvento = new HashMap<>();
	private static final Map<UUID, Long> ultimaMadeira = new HashMap<>();

	private VigiaEvents() { }

	// ---------------------------------------------------------------- relógio

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!podeAssombrar(player)) {
				continue;
			}
			UUID id = player.getUUID();
			int restante = proximoEvento.computeIfAbsent(id, k -> sortearIntervalo(player.getRandom())) - 1;
			if (restante <= 0) {
				eventoAleatorio(player);
				restante = sortearIntervalo(player.getRandom());
			}
			proximoEvento.put(id, restante);
		}
	}

	public static void reiniciarRelogio(ServerPlayer player) {
		proximoEvento.put(player.getUUID(), sortearIntervalo(player.getRandom()));
	}

	private static int sortearIntervalo(RandomSource random) {
		return INTERVALO_MIN + random.nextInt(INTERVALO_MAX - INTERVALO_MIN + 1);
	}

	private static boolean podeAssombrar(ServerPlayer player) {
		return !player.isSpectator()
				&& player.isAlive()
				&& Boolean.TRUE.equals(player.getAttached(VigiaMod.CONCORDOU));
	}

	/** Se tiver vidro por perto, metade das vezes é o evento do vidro; senão, aparece longe. */
	private static void eventoAleatorio(ServerPlayer player) {
		BlockPos vidro = acharVidro(player);
		if (vidro != null && player.getRandom().nextBoolean() && eventoVidro(player, vidro)) {
			return;
		}
		if (!eventoLonge(player) && vidro != null) {
			eventoVidro(player, vidro);
		}
	}

	// ---------------------------------------------------------------- madeira

	public static void aoQuebrarBloco(Level level, Player player, BlockPos pos, BlockState state) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (!state.is(BlockTags.LOGS) || !podeAssombrar(serverPlayer)) {
			return;
		}
		long agora = level.getGameTime();
		Long ultima = ultimaMadeira.get(serverPlayer.getUUID());
		if (ultima != null && agora - ultima < ESPERA_MADEIRA) {
			return;
		}
		if (serverPlayer.getRandom().nextFloat() < CHANCE_MADEIRA) {
			ultimaMadeira.put(serverPlayer.getUUID(), agora);
			eventoMadeira(serverPlayer, pos);
		}
	}

	/** O Vigia aparece logo atrás do tronco quebrado por meio segundo. */
	public static void eventoMadeira(ServerPlayer player, BlockPos tronco) {
		ServerLevel level = (ServerLevel) player.level();
		double dx = tronco.getX() + 0.5D - player.getX();
		double dz = tronco.getZ() + 0.5D - player.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.01D) {
			dx = 1.0D;
			dz = 0.0D;
			len = 1.0D;
		}
		double x = tronco.getX() + 0.5D + dx / len * 1.3D;
		double z = tronco.getZ() + 0.5D + dz / len * 1.3D;
		spawnar(level, player, x, player.getY(), z, VigiaEntity.Modo.MADEIRA, null);
	}

	// ---------------------------------------------------------------- vidro

	public static BlockPos acharVidro(ServerPlayer player) {
		Level level = player.level();
		BlockPos centro = player.blockPosition();
		BlockPos melhor = null;
		double melhorDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO_VIDRO, -1, -RAIO_VIDRO), centro.offset(RAIO_VIDRO, 3, RAIO_VIDRO))) {
			if (VigiaEntity.ehVidro(level, p)) {
				double d = p.distSqr(centro);
				if (d < melhorDist) {
					melhorDist = d;
					melhor = p.immutable();
				}
			}
		}
		return melhor;
	}

	/** O Vigia aparece do outro lado do vidro, bate, quebra e corre atrás do jogador por 5 s. */
	public static boolean eventoVidro(ServerPlayer player, BlockPos vidro) {
		ServerLevel level = (ServerLevel) player.level();
		double dx = vidro.getX() + 0.5D - player.getX();
		double dz = vidro.getZ() + 0.5D - player.getZ();

		// Lado de fora: um bloco além do vidro, no eixo dominante jogador -> vidro.
		BlockPos fora = Math.abs(dx) >= Math.abs(dz)
				? vidro.offset(dx >= 0 ? 1 : -1, 0, 0)
				: vidro.offset(0, 0, dz >= 0 ? 1 : -1);

		// Desce até achar chão (no máximo 3 blocos).
		for (int i = 0; i < 3 && level.getBlockState(fora.below()).isAir(); i++) {
			fora = fora.below();
		}
		if (!level.getBlockState(fora).isAir()) {
			return false; // não há espaço do outro lado
		}

		spawnar(level, player, fora.getX() + 0.5D, fora.getY(), fora.getZ() + 0.5D, VigiaEntity.Modo.VIDRO, vidro);
		return true;
	}

	// ---------------------------------------------------------------- longe

	/** O Vigia surge longe, à frente do jogador. Se o jogador mirar nele, ele corre. */
	public static boolean eventoLonge(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		RandomSource random = player.getRandom();

		// Se o jogador estiver no subsolo, não dá para vê-lo na superfície: cancela.
		int superficieAqui = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
		if (player.getY() < superficieAqui - 8) {
			return false;
		}

		float olhar = player.getYRot();
		for (int tentativa = 0; tentativa < 12; tentativa++) {
			double angulo = Math.toRadians(olhar + (random.nextFloat() * 140.0F - 70.0F));
			double dist = 32.0D + random.nextDouble() * 12.0D;
			double x = player.getX() - Math.sin(angulo) * dist;
			double z = player.getZ() + Math.cos(angulo) * dist;
			BlockPos coluna = BlockPos.containing(x, player.getY(), z);
			if (!level.isLoaded(coluna)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, coluna.getX(), coluna.getZ());
			if (Math.abs(y - player.getY()) > 20) {
				continue;
			}
			spawnar(level, player, coluna.getX() + 0.5D, y, coluna.getZ() + 0.5D, VigiaEntity.Modo.LONGE, null);
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- comum

	private static void spawnar(ServerLevel level, ServerPlayer player, double x, double y, double z,
			VigiaEntity.Modo modo, BlockPos vidro) {
		VigiaEntity vigia = new VigiaEntity(VigiaMod.VIGIA, level);
		vigia.snapTo(x, y, z, 0.0F, 0.0F);
		vigia.iniciarEvento(modo, player, vidro);
		level.addFreshEntity(vigia);
	}

	// ---------------------------------------------------------------- o trato

	/** Executado pelo botão [CONCORDAR] do livro (comando /vigia concordar). */
	public static int concordar(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getAttached(VigiaMod.CONCORDOU))) {
			player.sendSystemMessage(Component.literal("O trato já foi feito.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			return 0;
		}
		player.setAttached(VigiaMod.CONCORDOU, true);
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));  // 4 s
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));  // 4 s
		player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 1.0F, 0.4F);
		player.sendSystemMessage(Component.literal("Ele aceitou.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		reiniciarRelogio(player);
		return 1;
	}
}
