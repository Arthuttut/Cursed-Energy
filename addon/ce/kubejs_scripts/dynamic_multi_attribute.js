let UUID = Java.loadClass('java.util.UUID');

function getPlayerName(entity) {
    if (entity.username) {
        return String(entity.username);
    }

    if (entity.name) {
        return String(entity.name);
    }

    return "";
}

function getScore(entity, objectiveName) {
    let server = entity.server;
    if (!server) return 0;

    let scoreboard = server.getScoreboard();
    if (!scoreboard) return 0;

    let objective = scoreboard.getObjective(objectiveName);
    if (objective == null) return 0;

    let playerName = getPlayerName(entity);
    if (playerName === "") return 0;

    let score = scoreboard.getOrCreatePlayerScore(playerName, objective);
    if (!score) return 0;

    return score.getScore();
}

function removeAttributes(entity, attributes, uuids) {
    for (let i = 0; i < attributes.length; i++) {
        if (i >= uuids.length) break;

        let attrId = attributes[i];
        let uuidStr = uuids[i];

        try {
            let uuidObj = UUID.fromString(uuidStr);
            entity.removeAttribute(attrId, uuidObj);
        } catch (error) {
            console.warn('[Dynamic Multi Attribute] Invalid UUID: ' + uuidStr);
        }
    }
}

StartupEvents.registry('palladium:abilities', (event) => {
    event.create('ce:dynamic_multi_attribute')
        .icon(palladium.createItemIcon('minecraft:golden_apple'))

        .addProperty(
            'property',
            'string',
            'sukuna_fingers',
            'Nome da scoreboard usada como nível. Ex: sukuna_fingers'
        )

        .addProperty(
            'intervals',
            'string_array',
            ["0", "1", "2", "3", "4", "5"],
            'Valores permitidos da scoreboard.'
        )

        .addProperty(
            'attributes',
            'string_array',
            [],
            'Lista de atributos. Ex: ["minecraft:generic.max_health", "minecraft:generic.attack_damage"].'
        )

        .addProperty(
            'amounts',
            'string_array',
            [],
            'Valores base. Ex: ["2", "1.5"]. O valor será multiplicado pelo nível.'
        )

        .addProperty(
            'operations',
            'string_array',
            [],
            'Operações. Use: add, remove, multiply_base ou multiply_total.'
        )

        .addProperty(
            'uuids',
            'string_array',
            [],
            'UUIDs dos modificadores.'
        )

        .tick((entity, entry, holder, enabled) => {
            if (!enabled || !entity.isPlayer()) return;

            let scoreboardName = entry.getPropertyByName('property');

            let intervalsRaw = entry.getPropertyByName('intervals');
            let propAttributes = entry.getPropertyByName('attributes');
            let propAmounts = entry.getPropertyByName('amounts');
            let propOperations = entry.getPropertyByName('operations');
            let propUuids = entry.getPropertyByName('uuids');

            let intervals = intervalsRaw ? Array.from(intervalsRaw).map(n => Number(n)) : [];

            let attributes = propAttributes ? Array.from(propAttributes) : [];
            let amounts = propAmounts ? Array.from(propAmounts) : [];
            let operations = propOperations ? Array.from(propOperations) : [];
            let uuids = propUuids ? Array.from(propUuids) : [];

            if (
                attributes.length !== amounts.length ||
                attributes.length !== operations.length ||
                attributes.length !== uuids.length
            ) {
                console.warn('[Dynamic Multi Attribute] attributes, amounts, operations and uuids must have the same length.');
                holder.setEnabled(false);
                return;
            }

            let level = getScore(entity, scoreboardName);

            if (!intervals.includes(level)) {
                removeAttributes(entity, attributes, uuids);
                return;
            }

            for (let i = 0; i < attributes.length; i++) {
                let attrId = attributes[i];
                let amountBase = parseFloat(amounts[i]);
                let operationRaw = String(operations[i]).toLowerCase();
                let uuidStr = uuids[i];

                if (isNaN(amountBase)) {
                    console.warn('[Dynamic Multi Attribute] Invalid amount: ' + amounts[i]);
                    continue;
                }

                if (!entity.getAttribute(attrId)) {
                    console.warn('[Dynamic Multi Attribute] Invalid attribute: ' + attrId);
                    continue;
                }

                let uuidObj;

                try {
                    uuidObj = UUID.fromString(uuidStr);
                } catch (error) {
                    console.warn('[Dynamic Multi Attribute] Invalid UUID: ' + uuidStr);
                    continue;
                }

                let finalAmount = amountBase * level;
                let operation = 'ADDITION';

                if (operationRaw === 'add') {
                    operation = 'ADDITION';
                } else if (operationRaw === 'remove') {
                    operation = 'ADDITION';
                    finalAmount = finalAmount * -1;
                } else if (operationRaw === 'multiply_base') {
                    operation = 'MULTIPLY_BASE';
                } else if (operationRaw === 'multiply_total') {
                    operation = 'MULTIPLY_TOTAL';
                } else {
                    console.warn('[Dynamic Multi Attribute] Invalid operation: ' + operationRaw);
                    continue;
                }

                entity.modifyAttribute(
                    attrId,
                    uuidObj,
                    finalAmount,
                    operation
                );
            }
        })

        .lastTick((entity, entry, holder) => {
            if (!entity.isPlayer()) return;

            let propAttributes = entry.getPropertyByName('attributes');
            let propUuids = entry.getPropertyByName('uuids');

            let attributes = propAttributes ? Array.from(propAttributes) : [];
            let uuids = propUuids ? Array.from(propUuids) : [];

            removeAttributes(entity, attributes, uuids);
        });
});