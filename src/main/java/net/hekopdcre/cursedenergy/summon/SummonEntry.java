package net.hekopdcre.cursedenergy.summon;

import net.minecraft.resources.Identifier;
import java.util.UUID;

public record SummonEntry(UUID entityUUID, Identifier abilityId) {
}