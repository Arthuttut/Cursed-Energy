package net.hekopdcre.cursedenergy.entity.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import net.hekopdcre.cursedenergy.CursedEnergy;
import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.resources.Identifier;

public class RikaModel extends DefaultedEntityGeoModel<RikaEntity> {
    public RikaModel() {
        super(Identifier.fromNamespaceAndPath(CursedEnergy.MOD_ID, "rika"));
    }
}