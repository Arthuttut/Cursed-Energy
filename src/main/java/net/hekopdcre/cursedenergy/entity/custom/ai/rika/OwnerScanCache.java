package net.hekopdcre.cursedenergy.entity.custom.ai.rika;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * OwnerScanCache — cache de scan de entidades compartilhado por owner.
 *
 * PROBLEMA RESOLVIDO:
 * Com N Rikas servindo o mesmo owner, cada uma rodava getEntitiesOfClass
 * de forma independente no mesmo tick, varrendo a mesma região com o mesmo
 * predicado N vezes. Resultado: N spatial queries idênticas por tick.
 *
 * SOLUÇÃO:
 * Cache estático indexado por ownerUUID. O primeiro tick que precisar de
 * dados chama getEntitiesOfClass e armazena. Todas as Rikas subsequentes
 * no mesmo tick recebem a lista cacheada sem custo adicional.
 *
 * CICLO DE VIDA:
 * - Cada ScanResult guarda o tickCount em que foi populado.
 * - TTL de 4 ticks (configurável). Expirado → próximo caller recalcula.
 * - Nenhum tick de limpeza global necessário: o próprio caller verifica TTL
 * e substitui a entrada, mantendo o mapa sempre pequeno (1 entrada / owner
 * ativo).
 * - Em servidores com dezenas de owners distintos, o mapa cresce
 * proporcionalmente
 * mas cada entrada expira naturalmente quando não há Rika chamando para aquele
 * owner.
 *
 * THREAD SAFETY:
 * O tick de entidades no NeoForge 1.21 roda na thread principal do servidor.
 * Este cache não é thread-safe por design — não é necessário.
 * Se Rikas forem tickadas em threads separadas (improvável no NeoForge padrão),
 * substitua HashMap por ConcurrentHashMap e envolva updates em synchronized.
 *
 * IMPACTO ESTIMADO:
 * 3 Rikas mesmo owner: de 3 scans/tick para 1 scan/tick.
 * 5 Rikas mesmo owner: de 5 scans/tick para 1 scan/tick.
 * Ganho linear com número de Rikas compartilhando owner.
 */
public final class OwnerScanCache {

    /**
     * TTL em ticks. 4 ticks ≈ 200ms a 20TPS — mesma janela usada no cache de
     * projéteis.
     */
    public static final int TTL = 4;

    // -----------------------------------------------------------------------
    // DATA STRUCTURES
    // -----------------------------------------------------------------------

    public static final class ScanResult {
        /** Mobs hostis dentro de raio 48 do owner (Monster, vivo, não é a Rika). */
        public final List<Mob> hostileMobs;
        /** Projéteis perigosos indo em direção ao owner dentro de raio 32. */
        public final List<Projectile> dangerousProjectiles;
        /** Tick em que este resultado foi gerado. */
        public final int generatedAt;

        public ScanResult(List<Mob> hostileMobs, List<Projectile> dangerousProjectiles, int generatedAt) {
            this.hostileMobs = hostileMobs;
            this.dangerousProjectiles = dangerousProjectiles;
            this.generatedAt = generatedAt;
        }

        public boolean isExpired(int currentTick) {
            return currentTick - generatedAt >= TTL;
        }
    }

    // Mapa estático: ownerUUID → ScanResult mais recente.
    // Sem WeakReference: o mapa é pequeno (1 entrada por owner ativo) e limpo
    // implicitamente quando o owner não tem Rika chamando por ele.
    private static final Map<java.util.UUID, ScanResult> CACHE = new HashMap<>();

    // Construtor privado — classe puramente estática.
    private OwnerScanCache() {
    }

    // -----------------------------------------------------------------------
    // PUBLIC API
    // -----------------------------------------------------------------------

    /**
     * Retorna o ScanResult para o owner dado, populando o cache se necessário.
     *
     * @param owner       A entidade dona (pode ser Player ou qualquer
     *                    LivingEntity).
     * @param currentTick this.tickCount da Rika que está solicitando — usado para
     *                    TTL.
     * @param rika        A própria Rika (excluída dos resultados).
     * @return ScanResult nunca nulo; lista interna pode ser vazia.
     */
    public static ScanResult get(LivingEntity owner, int currentTick, LivingEntity rika) {
        java.util.UUID ownerUUID = owner.getUUID();
        ScanResult cached = CACHE.get(ownerUUID);

        if (cached != null && !cached.isExpired(currentTick)) {
            return cached;
        }

        // Cache miss ou expirado: realizar scan e armazenar.
        ScanResult fresh = scan(owner, currentTick, rika);
        CACHE.put(ownerUUID, fresh);
        return fresh;
    }

    /**
     * Invalida manualmente o cache de um owner.
     * Útil quando o owner morre ou muda de dimensão para evitar dados obsoletos.
     */
    public static void invalidate(java.util.UUID ownerUUID) {
        CACHE.remove(ownerUUID);
    }

    /**
     * Limpa entradas antigas para owners que não estão mais sendo servidos.
     * Deve ser chamado ocasionalmente (ex: a cada 400 ticks por qualquer Rika).
     * Em servidores com poucos owners simultâneos, pode ser omitido.
     */
    public static void purgeStale(int currentTick) {
        CACHE.entrySet().removeIf(e -> e.getValue().isExpired(currentTick));
    }

    // -----------------------------------------------------------------------
    // INTERNAL
    // -----------------------------------------------------------------------

    private static ScanResult scan(LivingEntity owner, int currentTick, LivingEntity rika) {
        // Raio maior (48) cobre tanto detecção geral quanto ameaças distantes.
        AABB mobBB = owner.getBoundingBox().inflate(48);
        List<Mob> mobs = owner.level().getEntitiesOfClass(
                Mob.class, mobBB,
                e -> e != rika && e.isAlive() && e instanceof Monster);

        // Projéteis: raio 32 (movem rápido, não precisam de raio maior).
        AABB projBB = owner.getBoundingBox().inflate(32);
        List<Projectile> projectiles = owner.level().getEntitiesOfClass(
                Projectile.class, projBB,
                p -> DangerousProjectiles.isDangerous(p, owner));

        return new ScanResult(mobs, projectiles, currentTick);
    }
}