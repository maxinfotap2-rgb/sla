package com.vigia;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

/**
 * O Vigia.
 *
 * Modo LIVRE  = invocado com /summon vigia:vigia. Monstro hostil normal, pode ser morto.
 * Modo MADEIRA/VIDRO/LONGE = aparições dos eventos. São invulneráveis e somem sozinhas.
 */
public class VigiaEntity extends Monster {
	public enum Modo { LIVRE, MADEIRA, VIDRO, LONGE }

	// Durações em ticks (20 ticks = 1 segundo)
	private static final int MADEIRA_DURACAO = 10;            // 0,5 s
	private static final int VIDRO_INICIO_PERSEGUICAO = 70;   // batidas antes de quebrar
	private static final int VIDRO_PERSEGUICAO = 100;         // 5 s correndo
	private static final int LONGE_ESPERA_MAX = 20 * 60;      // some se ninguém olhar em 60 s
	private static final int LONGE_PERSEGUICAO = 120;         // 6 s
	private static final double VELOCIDADE_PERSEGUICAO = 1.6D;

	private Modo modo = Modo.LIVRE;
	private int timer = 0;
	private int inicioPerseguicao = -1;
	private UUID alvoId;
	private BlockPos vidroPos;

	public VigiaEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, VELOCIDADE_PERSEGUICAO, true));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Chamado pelos eventos antes de colocar o Vigia no mundo. */
	public void iniciarEvento(Modo modo, ServerPlayer alvo, BlockPos vidro) {
		this.modo = modo;
		this.alvoId = alvo.getUUID();
		this.vidroPos = vidro;
		this.timer = 0;
		this.inicioPerseguicao = -1;
		// Marca persistente: se o mundo for salvo no meio de um evento, a aparição é apagada ao recarregar.
		this.setAttached(VigiaMod.E_EVENTO, true);
		this.setNoAi(true); // fica parado até a hora de correr
		olharPara(alvo);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.modo != Modo.LIVRE) {
			return false; // aparições não podem ser feridas
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void tick() {
		super.tick();

		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}

		if (this.modo == Modo.LIVRE) {
			// Sobra de um evento após recarregar o mundo: apaga.
			if (Boolean.TRUE.equals(this.getAttached(VigiaMod.E_EVENTO))) {
				this.discard();
			}
			return;
		}

		ServerPlayer alvo = this.alvoId == null ? null : level.getServer().getPlayerList().getPlayer(this.alvoId);
		if (alvo == null || !alvo.isAlive() || alvo.level() != level) {
			sumir(level);
			return;
		}

		this.timer++;

		switch (this.modo) {
			case MADEIRA -> tickMadeira(level, alvo);
			case VIDRO -> tickVidro(level, alvo);
			case LONGE -> tickLonge(level, alvo);
			default -> { }
		}
	}

	// ---------------------------------------------------------------- eventos

	private void tickMadeira(ServerLevel level, ServerPlayer alvo) {
		olharPara(alvo);
		if (this.timer == 1) {
			level.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 0.6F, 0.5F);
		}
		if (this.timer >= MADEIRA_DURACAO) {
			sumir(level);
		}
	}

	private void tickVidro(ServerLevel level, ServerPlayer alvo) {
		if (this.inicioPerseguicao < 0) {
			olharPara(alvo);

			// Três batidas no vidro
			if (this.vidroPos != null && (this.timer == 15 || this.timer == 32 || this.timer == 49)) {
				float pitch = 0.5F + this.random.nextFloat() * 0.2F;
				level.playSound(null, this.vidroPos, SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 2.0F, pitch);
				level.playSound(null, this.vidroPos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.35F, 1.6F);
			}

			if (this.timer >= VIDRO_INICIO_PERSEGUICAO) {
				quebrarVidro(level);
				comecarPerseguicao(level, alvo);
			}
		} else {
			manterPerseguicao(alvo);
			if (this.timer - this.inicioPerseguicao >= VIDRO_PERSEGUICAO) {
				sumir(level);
			}
		}
	}

	private void tickLonge(ServerLevel level, ServerPlayer alvo) {
		if (this.inicioPerseguicao < 0) {
			olharPara(alvo);

			if (jogadorEstaOlhando(alvo)) {
				alvo.addEffect(new MobEffectInstance(MobEffects.DARKNESS, LONGE_PERSEGUICAO, 0));
				comecarPerseguicao(level, alvo);
			} else if (this.timer >= LONGE_ESPERA_MAX) {
				sumir(level);
			}
		} else {
			manterPerseguicao(alvo);
			if (this.timer - this.inicioPerseguicao >= LONGE_PERSEGUICAO) {
				sumir(level);
			}
		}
	}

	// ---------------------------------------------------------------- ajudantes

	private void comecarPerseguicao(ServerLevel level, ServerPlayer alvo) {
		this.inicioPerseguicao = this.timer;
		this.setNoAi(false);
		this.setTarget(alvo);
		this.getNavigation().moveTo(alvo, VELOCIDADE_PERSEGUICAO);
		level.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.5F, 0.5F);
	}

	private void manterPerseguicao(ServerPlayer alvo) {
		if (this.getTarget() != alvo) {
			this.setTarget(alvo);
		}
		if (this.getNavigation().isDone()) {
			this.getNavigation().moveTo(alvo, VELOCIDADE_PERSEGUICAO);
		}
	}

	private void quebrarVidro(ServerLevel level) {
		if (this.vidroPos == null) {
			return;
		}
		// Quebra o vidro e os blocos de vidro logo acima/abaixo, para abrir passagem.
		for (int dy = -1; dy <= 2; dy++) {
			BlockPos pos = this.vidroPos.offset(0, dy, 0);
			if (ehVidro(level, pos)) {
				level.destroyBlock(pos, false);
			}
		}
		level.playSound(null, this.vidroPos, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.7F);
	}

	public static boolean ehVidro(Level level, BlockPos pos) {
		var state = level.getBlockState(pos);
		return state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES);
	}

	/** O jogador está mirando a câmera no Vigia (com visão livre)? */
	private boolean jogadorEstaOlhando(ServerPlayer alvo) {
		Vec3 olhar = alvo.getViewVector(1.0F).normalize();
		Vec3 ateMim = new Vec3(
				this.getX() - alvo.getX(),
				this.getY(0.6D) - alvo.getEyeY(),
				this.getZ() - alvo.getZ()).normalize();
		return olhar.dot(ateMim) > 0.99D && alvo.hasLineOfSight(this);
	}

	private void olharPara(ServerPlayer alvo) {
		double dx = alvo.getX() - this.getX();
		double dz = alvo.getZ() - this.getZ();
		double dy = alvo.getEyeY() - this.getEyeY();
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0F;
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		this.setYRot(yaw);
		this.setYHeadRot(yaw);
		this.yBodyRot = yaw;
		this.setXRot(pitch);
	}

	private void sumir(ServerLevel level) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.2D, this.getZ(),
				25, 0.3D, 0.9D, 0.3D, 0.01D);
		this.discard();
	}
}
