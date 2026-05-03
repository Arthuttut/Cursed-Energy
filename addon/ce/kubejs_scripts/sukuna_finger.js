StartupEvents.registry('item', event => {
    event.create('ce:sukuna_finger')
        .displayName('Sukuna Finger')
        .rarity('epic')
        .fireResistant()
        .maxStackSize(20)
        .food(food => food
            .hunger(2)
            .saturation(0.1)
            .alwaysEdible()
        )
})