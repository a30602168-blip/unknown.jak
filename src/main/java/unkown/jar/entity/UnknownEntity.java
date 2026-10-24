package unkown.jar.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import unkown.jar.UnkownConfig;
import unkown.jar.director.EncounterDirector;
import unkown.jar.director.SpawnFinder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Das Entity von unknown.jar. Eine einzige Entity-Art, deren Verhalten, Groesse, Tempo und Aussehen
 * von der Phase (1-4) abhaengen. Die Phase wird beim Erscheinen aus dem Ingame-Tag bestimmt.
 */
public class UnknownEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> DATA_PHASE =
			SynchedEntityData.defineId(UnknownEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_HUNTING =
			SynchedEntityData.defineId(UnknownEntity.class, EntityDataSerializers.BOOLEAN);

	private enum Mode { OBSERVE, DASH, WINDUP, HUNT, GRAB }

	private Mode mode = Mode.OBSERVE;
	private UUID targetId;
	private int life;
	private int maxLife = 800;
	private int modeTicks;
	private int gazeTicks;
	private int soundTimer = 60;
	private int attackCooldown;
	private int hits;
	private int lostSightTicks;
	private static final int COUNTDOWN_TICKS = 100; // 5 Sekunden Vorsprung
	private Mode afterCountdown = Mode.HUNT;
	private int chatLines;
	/** Blocks conjured over water: position -> original state, restored when it leaves. */
	private final Map<BlockPos, BlockState> waterBlocks = new LinkedHashMap<>();

	public UnknownEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.FOLLOW_RANGE, 90.0)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, 0);
		builder.define(DATA_HUNTING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	public int getPhase() {
		return this.entityData.get(DATA_PHASE);
	}

	public boolean isHunting() {
		return this.entityData.get(DATA_HUNTING);
	}

	public void bindToPlayer(UUID playerId) {
		this.targetId = playerId;
	}

	// ------------------------------------------------------------------ Phase

	/** Stellt Groesse, Tempo, Schaden, Leben und Name fuer die Phase ein. Nur serverseitig aufrufen. */
	public void initForPhase(int phase) {
		this.entityData.set(DATA_PHASE, phase);
		setAttr(Attributes.SCALE, Phases.scale(phase));
		double speed = Phases.speed(phase);
		if (phase >= 3) {
			speed *= UnkownConfig.get().huntSpeedMultiplier;
		}
		setAttr(Attributes.MOVEMENT_SPEED, speed);
		setAttr(Attributes.ATTACK_DAMAGE, Phases.damage(phase));
		setAttr(Attributes.MAX_HEALTH, Phases.health(phase));
		setHealth(getMaxHealth());
		setCustomName(Component.literal(Phases.name(phase)));
		setCustomNameVisible(false);

		this.maxLife = Phases.minLife(phase) + this.random.nextInt(Phases.maxLife(phase) - Phases.minLife(phase) + 1);
		this.chatLines = 0;
		this.afterCountdown = Mode.HUNT;
		this.mode = phase >= 3 ? Mode.WINDUP : Mode.OBSERVE;
		this.modeTicks = 0;
		this.life = 0;
	}

	private void setAttr(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	// ------------------------------------------------------------------ Tick

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel serverLevel) {
			serverTick(serverLevel);
		}
	}

	private void serverTick(ServerLevel sl) {
		if (getPhase() == 0) {
			initForPhase(Phases.current(sl));
			onAppear(sl);
		}
		this.life++;
		this.modeTicks++;
		if (this.attackCooldown > 0) {
			this.attackCooldown--;
		}

		ServerPlayer target = resolveTarget(sl);
		if (target == null) {
			discard();
			return;
		}
		if (this.life > this.maxLife && this.mode == Mode.OBSERVE) {
			vanish(sl, true);
			return;
		}

		walkOnWater(sl);
		int phase = getPhase();
		boolean peaceful = sl.getDifficulty() == Difficulty.PEACEFUL;
		double dist = distanceTo(target);

		switch (this.mode) {
			case OBSERVE -> observe(sl, target, phase, dist, peaceful);
			case DASH -> dash(sl, target, dist);
			case WINDUP -> windup(sl, target, dist);
			case HUNT -> hunt(sl, target, phase, dist, peaceful);
			case GRAB -> grab(target);
		}

		if (!isRemoved()) {
			ambientSounds(sl, phase, target);
		}
	}

	private ServerPlayer resolveTarget(ServerLevel sl) {
		Player p = this.targetId == null ? sl.getNearestPlayer(this, 128.0) : sl.getPlayerByUUID(this.targetId);
		if (p instanceof ServerPlayer sp && sp.isAlive() && !sp.isSpectator()) {
			this.targetId = sp.getUUID();
			return sp;
		}
		return null;
	}

	private void onAppear(ServerLevel sl) {
		int phase = getPhase();
		if (phase >= 3) {
			Fx.play(sl, ModSounds.APPEAR, getX(), getY(), getZ(), 2.0F, 0.9F);
		} else if (this.random.nextFloat() < 0.4F) {
			Fx.play(sl, ModSounds.APPEAR, getX(), getY(), getZ(), 0.5F, 1.0F);
		}
	}

	// ------------------------------------------------------------------ Water

	/** Over water the entity conjures blackstone under its feet (and a step ahead) and takes it back later. */
	private void walkOnWater(ServerLevel sl) {
		BlockPos feet = blockPosition();
		int radius = getScale() > 1.4F ? 1 : 0;
		Vec3 dir = getDeltaMovement();
		int ax = dir.x > 0.02 ? 1 : (dir.x < -0.02 ? -1 : 0);
		int az = dir.z > 0.02 ? 1 : (dir.z < -0.02 ? -1 : 0);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				conjureOver(sl, feet.offset(dx, -1, dz));
				if (ax != 0 || az != 0) {
					conjureOver(sl, feet.offset(dx + ax, -1, dz + az));
				}
			}
		}
		// Keep it tidy: only the newest blocks stay, older ones turn back into water.
		if (this.waterBlocks.size() > 14) {
			List<BlockPos> old = new ArrayList<>(this.waterBlocks.keySet());
			for (int i = 0; i < old.size() - 14; i++) {
				restoreBlock(sl, old.get(i));
			}
		}
	}

	private void conjureOver(ServerLevel sl, BlockPos pos) {
		if (this.waterBlocks.containsKey(pos)) {
			return;
		}
		BlockState state = sl.getBlockState(pos);
		if (state.getFluidState().is(FluidTags.WATER) && state.is(Blocks.WATER)) {
			this.waterBlocks.put(pos.immutable(), state);
			sl.setBlockAndUpdate(pos, Blocks.BLACKSTONE.defaultBlockState());
		}
	}

	private void restoreBlock(ServerLevel sl, BlockPos pos) {
		BlockState original = this.waterBlocks.remove(pos);
		if (original != null && sl.getBlockState(pos).is(Blocks.BLACKSTONE)) {
			sl.setBlockAndUpdate(pos, original);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (this.level() instanceof ServerLevel sl && !this.waterBlocks.isEmpty()) {
			for (BlockPos pos : new ArrayList<>(this.waterBlocks.keySet())) {
				restoreBlock(sl, pos);
			}
		}
		super.remove(reason);
	}

	// ------------------------------------------------------------------ Modi

	/** Phase 1 und 2: beobachten, einfrieren wenn man hinsieht, naehern wenn man wegsieht. */
	private void observe(ServerLevel sl, ServerPlayer target, int phase, double dist, boolean peaceful) {
		getLookControl().setLookAt(target, 40.0F, 40.0F);
		boolean seen = isLookedAt(target, 0.975);
		this.gazeTicks = seen ? this.gazeTicks + 1 : Math.max(0, this.gazeTicks - 1);

		double comfort = phase == 1 ? 16.0 : 9.0;
		if (!seen && dist > comfort) {
			getNavigation().moveTo(target, phase == 1 ? 0.5 : 0.7);
		} else {
			getNavigation().stop();
		}

		if (phase == 1) {
			// Zu nah oder zu lange angestarrt: einfach weg.
			if (dist < 12.0 || this.gazeTicks > 50) {
				vanish(sl, false);
			}
			return;
		}

		// Phase 2: unberechenbar.
		if (this.gazeTicks > 80) {
			if (this.random.nextBoolean()) {
				teleportBehind(sl, target);
			} else {
				vanish(sl, true);
			}
			this.gazeTicks = 0;
			return;
		}
		if (this.modeTicks % 20 == 0 && this.modeTicks > 60) {
			float roll = this.random.nextFloat();
			if (roll < 0.14F) {
				teleportBehind(sl, target);
			} else if (roll < 0.22F && !peaceful) {
				startCountdown(Mode.DASH);
			}
		}
	}

	/** Phase 2: kurzer Angriff, hoechstens zwei Treffer, dann verschwindet es wieder. */
	private void dash(ServerLevel sl, ServerPlayer target, double dist) {
		getLookControl().setLookAt(target, 60.0F, 60.0F);
		if (this.modeTicks % 4 == 0) {
			getNavigation().moveTo(target, 1.0);
		}
		if (tryHit(sl, target, 25)) {
			this.hits++;
		}
		if (this.hits >= 2 || this.modeTicks > 140 || dist > 40.0) {
			vanish(sl, true);
		}
	}

	/** Phase 2, 3, 4: 5 Sekunden Countdown in der Bildschirmmitte, dann beginnt der Angriff bzw. die Jagd. */
	private void startCountdown(Mode next) {
		this.mode = Mode.WINDUP;
		this.afterCountdown = next;
		this.modeTicks = 0;
		getNavigation().stop();
	}

	private void windup(ServerLevel sl, ServerPlayer target, double dist) {
		getNavigation().stop();
		getLookControl().setLookAt(target, 60.0F, 60.0F);
		int phase = getPhase();

		if (this.modeTicks == 1) {
			target.connection.send(new ClientboundSetTitlesAnimationPacket(0, 24, 4));
			Fx.play(sl, ModSounds.GROWL, getX(), getY(), getZ(), 2.0F, phase == 4 ? 0.6F : 0.85F);
		}
		if (this.modeTicks % 20 == 1 && this.modeTicks <= COUNTDOWN_TICKS) {
			int seconds = 5 - (this.modeTicks - 1) / 20;
			target.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("\u00a77\u00a7oit is coming...")));
			target.connection.send(new ClientboundSetTitleTextPacket(Component.literal("\u00a74\u00a7l" + seconds)));
			Fx.play(sl, ModSounds.STEP, target.getX(), target.getY(), target.getZ(), 1.5F, 0.5F);
		}
		if (this.modeTicks > COUNTDOWN_TICKS) {
			target.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 8));
			target.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
			target.connection.send(new ClientboundSetTitleTextPacket(Component.literal("\u00a74\u00a7lRUN!")));
			this.mode = this.afterCountdown;
			this.modeTicks = 0;
			this.hits = 0;
			this.lostSightTicks = 0;
			if (this.mode == Mode.HUNT) {
				this.entityData.set(DATA_HUNTING, true);
				Fx.play(sl, ModSounds.SCREAM, getX(), getY(), getZ(), phase == 4 ? 3.0F : 2.0F, phase == 4 ? 0.7F : 1.0F);
			} else {
				Fx.play(sl, ModSounds.GROWL, getX(), getY(), getZ(), 1.2F, 1.3F);
			}
		}
	}

	private void hunt(ServerLevel sl, ServerPlayer target, int phase, double dist, boolean peaceful) {
		getLookControl().setLookAt(target, 60.0F, 60.0F);
		if (this.modeTicks % 4 == 0) {
			getNavigation().moveTo(target, 1.0);
		}
		if (getNavigation().isDone() && this.modeTicks % 20 == 0) {
			getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
		}

		this.lostSightTicks = hasLineOfSight(target) ? 0 : this.lostSightTicks + 1;

		// Herzschlag/Anspannung direkt beim Spieler
		if (this.modeTicks % 120 == 1) {
			float closeness = (float) Math.max(0.4, 1.4 - dist / 40.0);
			Fx.play(sl, ModSounds.CHASE, target.getX(), target.getY(), target.getZ(), closeness, 1.0F);
		}

		boolean hit = tryHit(sl, target, phase == 3 ? 30 : 24);
		if (hit) {
			this.hits++;
		}

		int maxHunt = phase == 3 ? 900 : 1200;
		boolean lost = dist > 70.0 || this.lostSightTicks > 240;
		boolean timeUp = this.modeTicks > maxHunt;
		boolean caught = phase == 4 && (this.hits >= 2 || (timeUp && dist < 7.0));

		if (caught && canDisconnect(target, peaceful)) {
			this.mode = Mode.GRAB;
			this.modeTicks = 0;
			getNavigation().stop();
			EncounterDirector.beginDisconnect(target);
			return;
		}
		if (timeUp || lost || (caught)) {
			// Der Spieler ist entkommen (oder die Jagd ist vorbei).
			Fx.play(sl, ModSounds.SCREAM, getX(), getY(), getZ(), 2.0F, 0.6F);
			vanish(sl, true);
		}
	}

	private void grab(ServerPlayer target) {
		getNavigation().stop();
		getLookControl().setLookAt(target, 60.0F, 60.0F);
		if (this.modeTicks > 140) {
			discard();
		}
	}

	private boolean canDisconnect(ServerPlayer target, boolean peaceful) {
		UnkownConfig cfg = UnkownConfig.get();
		if (!cfg.disconnectEnabled || peaceful) {
			return false;
		}
		if (cfg.creativePlayersImmune && target.isCreative()) {
			return false;
		}
		return this.random.nextDouble() < cfg.disconnectChance;
	}

	// ------------------------------------------------------------------ Hilfsfunktionen

	private boolean tryHit(ServerLevel sl, ServerPlayer target, int cooldownTicks) {
		if (this.attackCooldown > 0 || sl.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		double reach = getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 1.1;
		if (distanceToSqr(target) > reach * reach || Math.abs(target.getY() - getY()) > getBbHeight()) {
			return false;
		}
		float damage = (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * UnkownConfig.get().damageMultiplier);
		this.attackCooldown = cooldownTicks;
		return target.hurtServer(sl, damageSources().mobAttack(this), damage);
	}

	private boolean isLookedAt(Player player, double threshold) {
		Vec3 view = player.getViewVector(1.0F).normalize();
		Vec3 to = new Vec3(getX() - player.getX(), getY() + getBbHeight() * 0.6 - player.getEyeY(), getZ() - player.getZ());
		double len = to.length();
		if (len < 1.0E-4) {
			return true;
		}
		return view.dot(to.scale(1.0 / len)) > threshold && player.hasLineOfSight(this);
	}

	private void teleportBehind(ServerLevel sl, ServerPlayer target) {
		BlockPos spot = SpawnFinder.behind(sl, target, 4.5, 8.0, getPhase());
		if (spot == null) {
			return;
		}
		Fx.play(sl, ModSounds.TELEPORT, getX(), getY(), getZ(), 1.0F, 1.0F);
		Fx.particles(sl, ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() / 2, getZ(), 15, 0.4);
		getNavigation().stop();
		setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		Fx.play(sl, ModSounds.TELEPORT, getX(), getY(), getZ(), 1.4F, 0.8F);
		this.gazeTicks = 0;
	}

	private void ambientSounds(ServerLevel sl, int phase, ServerPlayer target) {
		if (--this.soundTimer > 0 || this.mode == Mode.GRAB) {
			return;
		}
		float pitch = 0.8F + this.random.nextFloat() * 0.3F;
		switch (phase) {
			case 1 -> Fx.play(sl, this.random.nextBoolean() ? ModSounds.WHISPER : ModSounds.AMBIENT, getX(), getY(), getZ(), 0.9F, pitch);
			case 2 -> Fx.play(sl, this.random.nextInt(3) == 0 ? ModSounds.GROWL : ModSounds.WHISPER, getX(), getY(), getZ(), 1.1F, pitch);
			case 3 -> Fx.play(sl, this.mode == Mode.HUNT ? ModSounds.GROWL : ModSounds.WHISPER, getX(), getY(), getZ(), 1.6F, pitch);
			default -> Fx.play(sl, this.random.nextInt(3) == 0 ? ModSounds.DOOM : ModSounds.GROWL, getX(), getY(), getZ(), 2.0F, pitch * 0.8F);
		}
		this.soundTimer = (phase >= 3 ? 80 : 160) + this.random.nextInt(phase >= 3 ? 120 : 220);
		int maxLines = phase == 1 ? 1 : 2;
		if (UnkownConfig.get().chatMessages && this.chatLines < maxLines && this.random.nextFloat() < 0.3F) {
			this.chatLines++;
			String[] lines = Phases.chatLines(phase);
			String text = lines[this.random.nextInt(lines.length)];
			target.sendSystemMessage(Component.literal("<" + Phases.name(phase) + "> " + text));
		}
	}

	/** Verschwindet mit Rauch und Geraeusch. */
	public void vanish(ServerLevel sl, boolean loud) {
		if (this.mode == Mode.GRAB) {
			return;
		}
		Fx.play(sl, ModSounds.VANISH, getX(), getY(), getZ(), loud ? 1.5F : 0.8F, 0.9F + this.random.nextFloat() * 0.2F);
		Fx.particles(sl, ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() / 2, getZ(), 30, 0.5 * getScale());
		discard();
	}

	// ------------------------------------------------------------------ Schaden & Sounds

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		if (getPhase() <= 2) {
			// Wer es verletzt, verscheucht es nur.
			vanish(level, true);
			return true;
		}
		if (getHealth() - amount <= getMaxHealth() * 0.25F) {
			vanish(level, true);
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		// Phase 1 und 2 bewegen sich lautlos - Phase 3 und 4 sind zu hoeren.
		if (getPhase() >= 3) {
			playSound(ModSounds.STEP, 0.7F + 0.1F * getPhase(), 0.8F + this.random.nextFloat() * 0.3F);
		}
	}
}
