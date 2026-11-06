#!/usr/bin/env python3
"""Generate the static resources for the elemental crystal forms.

For each flavor's MMDLib material (src/main/resources/mmdlib/material/<flavor>.json) this writes
the blockstates, block and item models, loot tables, storage recipes, mining tag entries and
English names of its four forms, plus world generation for the flavors that occur naturally:

    <flavor>_crystal          item     harvested crystal
    <flavor>_crystal_block    block    storage block, 9 crystals
    <flavor>_budding_crystal  block    grows clusters; drops nothing, like budding amethyst
    <flavor>_crystal_cluster  block    growth stages 0-3; stage 3 drops crystals

Natural flavors (Light, Dark) also get a geode: vanilla's amethyst geode with the flavor's crystal
block, budding crystal and cluster stages, added to overworld biomes by a NeoForge biome modifier.

Textures come from generate_crystal_textures.py. Existing lang and pickaxe tag entries are kept.

Usage:
    python3 scripts/generate_crystal_resources.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / 'src/main/resources'
ASSETS = RESOURCES / 'assets/technomagy'
DATA = RESOURCES / 'data/technomagy'
NS = 'technomagy'
FLAVORS = ['fire', 'earth', 'air', 'water', 'light', 'dark']
NATURAL = ['light', 'dark']
STAGES = 4
# Vanilla places an amethyst geode in 1 of 24 chunks. Each natural flavor gets 1 in 46, so a crystal
# geode of either flavor turns up in about 1 of 23 chunks: a little more often than amethyst.
GEODE_RARITY = 46

# blockstate rotations that point a model built facing up toward each direction
FACING = {
    'up': {}, 'down': {'x': 180},
    'north': {'x': 90}, 'south': {'x': 90, 'y': 180},
    'east': {'x': 90, 'y': 90}, 'west': {'x': 90, 'y': 270},
}

SILK_TOUCH = {
    'condition': 'minecraft:match_tool',
    'predicate': {'predicates': {'minecraft:enchantments': [
        {'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}},
}


def write(path, value):
    """Writes value as indented JSON, creating parent folders."""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def cube(flavor, form):
    """Blockstate, block model (cube_all) and item model for a plain full-block form."""
    name = f'{flavor}_{form}'
    write(ASSETS / f'blockstates/{name}.json', {'variants': {'': {'model': f'{NS}:block/{name}'}}})
    write(ASSETS / f'models/block/{name}.json',
          {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{name}'}})
    write(ASSETS / f'models/item/{name}.json', {'parent': f'{NS}:block/{name}'})


def cluster(flavor):
    """Blockstate (every facing and stage), per-stage cross models and the item model of a flavor's cluster."""
    name = f'{flavor}_crystal_cluster'
    variants = {}
    for stage in range(STAGES):
        write(ASSETS / f'models/block/{name}_stage{stage}.json', {
            'parent': 'minecraft:block/cross',
            'render_type': 'minecraft:cutout',
            'textures': {'cross': f'{NS}:block/{name}_stage{stage}'},
        })
        for facing, rotation in FACING.items():
            variants[f'facing={facing},stage={stage}'] = {'model': f'{NS}:block/{name}_stage{stage}', **rotation}
    write(ASSETS / f'blockstates/{name}.json', {'variants': variants})
    write(ASSETS / f'models/item/{name}.json', {
        'parent': 'minecraft:item/generated',
        'textures': {'layer0': f'{NS}:block/{name}_stage{STAGES - 1}'},
        'display': {'head': {'translation': [0, 14, -5]}},
    })


def loot(flavor):
    """Loot tables for a flavor's storage block, budding crystal and cluster."""
    crystal, block = f'{NS}:{flavor}_crystal', f'{NS}:{flavor}_crystal_block'
    cluster_id = f'{NS}:{flavor}_crystal_cluster'
    write(DATA / f'loot_table/blocks/{flavor}_crystal_block.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'bonus_rolls': 0,
                   'entries': [{'type': 'minecraft:item', 'name': block}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'{NS}:blocks/{flavor}_crystal_block',
    })
    write(DATA / f'loot_table/blocks/{flavor}_budding_crystal.json', {
        'type': 'minecraft:block',
        'random_sequence': f'{NS}:blocks/{flavor}_budding_crystal',
    })
    # Silk touch keeps the cluster at its stage; otherwise only a full cluster drops crystals,
    # four with a pickaxe (plus fortune), two with anything else.
    write(DATA / f'loot_table/blocks/{flavor}_crystal_cluster.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{
            'type': 'minecraft:alternatives',
            'children': [
                {'type': 'minecraft:item', 'name': cluster_id, 'conditions': [SILK_TOUCH],
                 'functions': [{'function': 'minecraft:copy_state', 'block': cluster_id, 'properties': ['stage']}]},
                {'type': 'minecraft:alternatives',
                 'conditions': [{'condition': 'minecraft:block_state_property', 'block': cluster_id,
                                 'properties': {'stage': str(STAGES - 1)}}],
                 'children': [
                     {'type': 'minecraft:item', 'name': crystal,
                      'conditions': [{'condition': 'minecraft:match_tool',
                                      'predicate': {'items': '#minecraft:cluster_max_harvestables'}}],
                      'functions': [{'function': 'minecraft:set_count', 'count': 4, 'add': False},
                                    {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune',
                                     'formula': 'minecraft:ore_drops'}]},
                     {'type': 'minecraft:item', 'name': crystal,
                      'functions': [{'function': 'minecraft:set_count', 'count': 2, 'add': False},
                                    {'function': 'minecraft:explosion_decay'}]},
                 ]},
            ],
        }]}],
        'random_sequence': f'{NS}:blocks/{flavor}_crystal_cluster',
    })


def state(name, **properties):
    """A block state in the JSON form worldgen configs use; property values written as lower-case strings."""
    value = {'Name': name}
    if properties:
        value['Properties'] = {key: str(prop).lower() for key, prop in properties.items()}
    return value


def provider(name):
    """A simple_state_provider for a block's default state."""
    return {'type': 'minecraft:simple_state_provider', 'state': state(name)}


def geode(flavor):
    """Vanilla's amethyst geode, with this flavor's blocks in place of amethyst."""
    name = f'{flavor}_crystal_geode'
    cluster_id = f'{NS}:{flavor}_crystal_cluster'
    write(DATA / f'worldgen/configured_feature/{name}.json', {
        'type': 'minecraft:geode',
        'config': {
            'blocks': {
                'filling_provider': provider('minecraft:air'),
                'inner_layer_provider': provider(f'{NS}:{flavor}_crystal_block'),
                'alternate_inner_layer_provider': provider(f'{NS}:{flavor}_budding_crystal'),
                'middle_layer_provider': provider('minecraft:calcite'),
                'outer_layer_provider': provider('minecraft:smooth_basalt'),
                'inner_placements': [state(cluster_id, facing='up', stage=stage, waterlogged=False)
                                     for stage in range(STAGES)],
                'cannot_replace': '#minecraft:features_cannot_replace',
                'invalid_blocks': '#minecraft:geode_invalid_blocks',
            },
            'layers': {'filling': 1.7, 'inner_layer': 2.2, 'middle_layer': 3.2, 'outer_layer': 4.2},
            'crack': {'generate_crack_chance': 0.95, 'base_crack_size': 2.0, 'crack_point_offset': 2},
            'use_potential_placements_chance': 0.35,
            'use_alternate_layer0_chance': 0.083,
            'placements_require_layer0_alternate': True,
            'outer_wall_distance': {'type': 'minecraft:uniform', 'min_inclusive': 4, 'max_inclusive': 6},
            'distribution_points': {'type': 'minecraft:uniform', 'min_inclusive': 3, 'max_inclusive': 4},
            'point_offset': {'type': 'minecraft:uniform', 'min_inclusive': 1, 'max_inclusive': 2},
            'min_gen_offset': -16,
            'max_gen_offset': 16,
            'noise_multiplier': 0.05,
            'invalid_blocks_threshold': 1,
        },
    })
    write(DATA / f'worldgen/placed_feature/{name}.json', {
        'feature': f'{NS}:{name}',
        'placement': [
            {'type': 'minecraft:rarity_filter', 'chance': GEODE_RARITY},
            {'type': 'minecraft:in_square'},
            {'type': 'minecraft:height_range', 'height': {
                'type': 'minecraft:uniform',
                'min_inclusive': {'above_bottom': 6},
                'max_inclusive': {'absolute': 30},
            }},
            {'type': 'minecraft:biome'},
        ],
    })


def recipes(flavor):
    """Storage recipes: 9 crystals into a block, and a block back into 9 crystals."""
    crystal, block = f'{NS}:{flavor}_crystal', f'{NS}:{flavor}_crystal_block'
    write(DATA / f'recipe/{flavor}_crystal_block.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'building',
        'pattern': ['###', '###', '###'], 'key': {'#': {'item': crystal}},
        'result': {'id': block, 'count': 1},
    })
    write(DATA / f'recipe/{flavor}_crystal_from_block.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': block}],
        'result': {'id': crystal, 'count': 9},
    })


def names(flavor):
    """English names of a flavor's four forms, keyed by translation key."""
    title = flavor.capitalize()
    return {
        f'item.{NS}.{flavor}_crystal': f'{title} Crystal',
        f'block.{NS}.{flavor}_crystal_block': f'Block of {title} Crystal',
        f'block.{NS}.{flavor}_budding_crystal': f'Budding {title} Crystal',
        f'block.{NS}.{flavor}_crystal_cluster': f'{title} Crystal Cluster',
    }


def main():
    """Writes every flavor's resources, the geodes of the natural flavors, and the shared lang and tag files."""
    lang_path = ASSETS / 'lang/en_us.json'
    lang = json.loads(lang_path.read_text()) if lang_path.exists() else {}
    lang[f'itemGroup.{NS}.materials'] = 'Technomagy Materials'
    mineable = []
    for flavor in FLAVORS:
        cube(flavor, 'crystal_block')
        cube(flavor, 'budding_crystal')
        cluster(flavor)
        write(ASSETS / f'models/item/{flavor}_crystal.json',
              {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{flavor}_crystal'}})
        loot(flavor)
        recipes(flavor)
        lang.update(names(flavor))
        mineable += [f'{NS}:{flavor}_{form}' for form in ('crystal_block', 'budding_crystal', 'crystal_cluster')]
    for flavor in NATURAL:
        geode(flavor)
    write(DATA / 'neoforge/biome_modifier/crystal_geodes.json', {
        'type': 'neoforge:add_features',
        'biomes': '#minecraft:is_overworld',
        'features': [f'{NS}:{flavor}_crystal_geode' for flavor in NATURAL],
        'step': 'local_modifications',
    })
    write(lang_path, lang)
    # the tag is shared with other blocks; keep their entries
    tag_path = RESOURCES / 'data/minecraft/tags/block/mineable/pickaxe.json'
    values = json.loads(tag_path.read_text())['values'] if tag_path.exists() else []
    write(tag_path, {'replace': False, 'values': values + [v for v in mineable if v not in values]})


if __name__ == '__main__':
    main()
