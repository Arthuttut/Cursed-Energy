function isDismantleCuttable(block, tagId) {
    if (!block || block.id == 'minecraft:air') return false
    return block.hasTag(tagId)
}

function getCutDirection(player) {
    let yaw = player.yaw % 360
    if (yaw < 0) yaw += 360

    // North/South = cut along the X axis
    if ((yaw >= 315 || yaw < 45) || (yaw >= 135 && yaw < 225)) {
        return 'x'
    }

    // East/West = cut along the Z axis
    return 'z'
}

function playDismantleSound(entity, sound) {
    entity.server.runCommandSilent(
        `execute at ${entity.username} run playsound ${sound} master @a[distance=..16] ~ ~ ~ 1 1.4`
    )
}

function dismantleCut(entity, entry) {
    if (!entity.isPlayer()) return

    let level = entity.level

    let distance = Number(entry.getPropertyByName('distance'))
    let radius = Number(entry.getPropertyByName('radius'))
    let height = Number(entry.getPropertyByName('height'))
    let tagId = String(entry.getPropertyByName('cuttable_tag'))
    let sound = String(entry.getPropertyByName('sound'))

    if (isNaN(distance)) distance = 8
    if (isNaN(radius)) radius = 3
    if (isNaN(height)) height = 0

    // Raycast to find the exact block the player is looking at
    let ray = entity.rayTrace(distance, false)

    if (!ray || !ray.block) {
        playDismantleSound(entity, sound)
        return
    }

    let hitBlock = ray.block

    let x = hitBlock.x
    let y = hitBlock.y
    let z = hitBlock.z

    let direction = getCutDirection(entity)

    // 50% chance for a straight vertical cut, 50% chance for a horizontal cut
    let verticalCut = Math.random() < 0.5

    if (verticalCut) {
        // Straight vertical cut
        // radius 3 = 7 blocks tall
        for (let dy = -radius; dy <= radius; dy++) {
            let block = level.getBlock(x, y + dy, z)

            if (isDismantleCuttable(block, tagId)) {
                block.set('minecraft:air')
            }
        }
    } else {
        // Horizontal cut
        // radius 3 = 7 blocks wide
        // height 0 = 1 block tall
        // height 1 = 2 blocks tall
        for (let h = 0; h <= height; h++) {
            if (direction == 'x') {
                for (let dx = -radius; dx <= radius; dx++) {
                    let block = level.getBlock(x + dx, y + h, z)

                    if (isDismantleCuttable(block, tagId)) {
                        block.set('minecraft:air')
                    }
                }
            } else {
                for (let dz = -radius; dz <= radius; dz++) {
                    let block = level.getBlock(x, y + h, z + dz)

                    if (isDismantleCuttable(block, tagId)) {
                        block.set('minecraft:air')
                    }
                }
            }
        }
    }

    playDismantleSound(entity, sound)
}

StartupEvents.registry('palladium:abilities', event => {
    event.create('ce:dismantle')
        .icon(palladium.createItemIcon('minecraft:iron_sword'))

        .addProperty(
            'distance',
            'integer',
            8,
            'Maximum raycast distance.'
        )

        .addProperty(
            'radius',
            'integer',
            3,
            'Cut radius. 3 = 7 blocks.'
        )

        .addProperty(
            'height',
            'integer',
            0,
            'Extra height for the horizontal cut. 0 = 1 block, 1 = 2 blocks.'
        )

        .addProperty(
            'cuttable_tag',
            'string',
            'ce:dismantle_cuttable',
            'Block tag used to decide which blocks can be cut.'
        )

        .addProperty(
            'sound',
            'string',
            'minecraft:entity.player.attack.sweep',
            'Sound played when the cut is used.'
        )

        .firstTick((entity, entry, holder, enabled) => {
            if (!enabled) return
            dismantleCut(entity, entry)
        })
})