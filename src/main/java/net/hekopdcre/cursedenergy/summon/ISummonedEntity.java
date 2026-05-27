package net.hekopdcre.cursedenergy.summon;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public interface ISummonedEntity {
    void setSummonOwner(UUID ownerUUID);

    @Nullable
    UUID getSummonOwner();

    void setSummonAbilityId(Identifier abilityId);

    @Nullable
    Identifier getSummonAbilityId();

    default boolean hasSummonOwner() {
        return getSummonOwner() != null;
    }
}