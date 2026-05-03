const BuiltInRegistries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');

ServerEvents.tags('block', event => {
  const blacklist = [
    // unbreakble
    'minecraft:bedrock',
    'minecraft:end_portal_frame',
    'minecraft:barrier',
    'minecraft:command_block',
    'minecraft:chain_command_block',
    'minecraft:repeating_command_block',
    'minecraft:structure_block',
    'minecraft:jigsaw',

    // hard blocks
    'minecraft:obsidian',
    'minecraft:crying_obsidian',
    'minecraft:reinforced_deepslate',
    'minecraft:ancient_debris',
    'minecraft:respawn_anchor',
    'minecraft:enchanting_table',
    'minecraft:ender_chest',

    // Netherite
    'minecraft:netherite_block'
  ];

  BuiltInRegistries.BLOCK.keySet().forEach(id => {
    const blockId = String(id);

    // block any ore that finish with "_ore"
    if (blockId.endsWith('_ore')) return;

    // Bblock blacklist blocks
    if (blacklist.includes(blockId)) return;

    // add all the rest as cuttable
    event.add('ce:dismantle_cuttable', blockId);
  });
});