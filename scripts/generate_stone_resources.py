#!/usr/bin/env python3
"""Generate the static resources for the flavored stones.

Each flavor has a stone material (src/main/resources/mmdlib/material/<flavor>_stone.json) using
MMDLib's built-in block, slab and wall forms:

    <flavor>_stone_block  block  full block
    <flavor>_stone_slab   block  vanilla SlabBlock; its double state shows the full block
    <flavor>_stone_wall   block  vanilla WallBlock

For each this writes blockstates, block and item models, loot tables, crafting recipes (the block
from cobblestone and the flavor's crystals; slabs and walls from the block), stonecutting
recipes, the pickaxe/slab/wall tags (MMDLib only declares tags; walls need #minecraft:walls to
connect), and English names. Textures come from generate_stone_textures.py. Existing lang and
tag entries are kept.

Usage:
    python3 scripts/generate_stone_resources.py
"""
import json

from generate_crystal_resources import ASSETS, DATA, NS, RESOURCES, write

FLAVORS = ['fire', 'earth', 'air', 'water', 'light', 'dark']
STONE_YIELD = 5  # stone blocks from 5 cobblestone + 4 crystals

# the post sits up when no side is tall; sides are low or tall, as vanilla walls
WALL_SIDES = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def self_drop(name):
    """A loot table dropping the block itself when it survives the explosion."""
    return {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'bonus_rolls': 0,
                   'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'{NS}:blocks/{name}',
    }


def block(flavor):
    """Blockstate, models and loot table of a flavor's full stone block."""
    name, texture = f'{flavor}_stone_block', f'{NS}:block/{flavor}_stone'
    write(ASSETS / f'blockstates/{name}.json', {'variants': {'': {'model': f'{NS}:block/{name}'}}})
    write(ASSETS / f'models/block/{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': texture}})
    write(ASSETS / f'models/item/{name}.json', {'parent': f'{NS}:block/{name}'})
    write(DATA / f'loot_table/blocks/{name}.json', self_drop(name))


def slab(flavor):
    """Blockstate, bottom/top models, item model and loot table (2 when double) of a flavor's slab."""
    name, texture = f'{flavor}_stone_slab', f'{NS}:block/{flavor}_stone'
    sides = {'bottom': texture, 'top': texture, 'side': texture}
    write(ASSETS / f'blockstates/{name}.json', {'variants': {
        'type=bottom': {'model': f'{NS}:block/{name}'},
        'type=top': {'model': f'{NS}:block/{name}_top'},
        'type=double': {'model': f'{NS}:block/{flavor}_stone_block'},
    }})
    write(ASSETS / f'models/block/{name}.json', {'parent': 'minecraft:block/slab', 'textures': sides})
    write(ASSETS / f'models/block/{name}_top.json', {'parent': 'minecraft:block/slab_top', 'textures': sides})
    write(ASSETS / f'models/item/{name}.json', {'parent': f'{NS}:block/{name}'})
    write(DATA / f'loot_table/blocks/{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{
            'type': 'minecraft:item', 'name': f'{NS}:{name}',
            'functions': [
                {'function': 'minecraft:set_count', 'count': 2, 'add': False,
                 'conditions': [{'condition': 'minecraft:block_state_property', 'block': f'{NS}:{name}',
                                 'properties': {'type': 'double'}}]},
                {'function': 'minecraft:explosion_decay'},
            ],
        }]}],
        'random_sequence': f'{NS}:blocks/{name}',
    })


def wall(flavor):
    """Multipart blockstate, post/side/inventory models, item model and loot table of a flavor's wall."""
    name, texture = f'{flavor}_stone_wall', f'{NS}:block/{flavor}_stone'
    parts = [{'when': {'up': 'true'}, 'apply': {'model': f'{NS}:block/{name}_post'}}]
    for height, model in (('low', f'{name}_side'), ('tall', f'{name}_side_tall')):
        for side, y in WALL_SIDES.items():
            apply = {'model': f'{NS}:block/{model}', 'uvlock': True}
            if y:
                apply['y'] = y
            parts.append({'when': {side: height}, 'apply': apply})
    write(ASSETS / f'blockstates/{name}.json', {'multipart': parts})
    for suffix, parent in (('post', 'template_wall_post'), ('side', 'template_wall_side'),
                           ('side_tall', 'template_wall_side_tall'), ('inventory', 'wall_inventory')):
        write(ASSETS / f'models/block/{name}_{suffix}.json',
              {'parent': f'minecraft:block/{parent}', 'textures': {'wall': texture}})
    write(ASSETS / f'models/item/{name}.json', {'parent': f'{NS}:block/{name}_inventory'})
    write(DATA / f'loot_table/blocks/{name}.json', self_drop(name))


def recipes(flavor):
    """The stone block from cobblestone and crystals, and slab, wall and stonecutter recipes from it."""
    stone, slab_id, wall_id = (f'{NS}:{flavor}_stone_{form}' for form in ('block', 'slab', 'wall'))
    write(DATA / f'recipe/{flavor}_stone_block.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'building',
        'pattern': ['scs', 'csc', 'scs'],
        'key': {'s': {'tag': 'c:cobblestones'}, 'c': {'item': f'{NS}:{flavor}_crystal'}},
        'result': {'id': stone, 'count': STONE_YIELD},
    })
    write(DATA / f'recipe/{flavor}_stone_slab.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'building',
        'pattern': ['###'], 'key': {'#': {'item': stone}},
        'result': {'id': slab_id, 'count': 6},
    })
    write(DATA / f'recipe/{flavor}_stone_wall.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc',
        'pattern': ['###', '###'], 'key': {'#': {'item': stone}},
        'result': {'id': wall_id, 'count': 6},
    })
    for result, count in ((slab_id, 2), (wall_id, 1)):
        write(DATA / f'recipe/{result.split(":")[1]}_from_stonecutting.json', {
            'type': 'minecraft:stonecutting',
            'ingredient': {'item': stone},
            'result': {'id': result, 'count': count},
        })


def names(flavor):
    """English names of a flavor's stone, slab and wall, keyed by translation key."""
    title = f'{flavor.capitalize()} Stone'
    return {
        f'block.{NS}.{flavor}_stone_block': title,
        f'block.{NS}.{flavor}_stone_slab': f'{title} Slab',
        f'block.{NS}.{flavor}_stone_wall': f'{title} Wall',
    }


def add_to_tag(path, values):
    """Adds values to a tag file shared with other blocks, keeping their entries."""
    existing = json.loads(path.read_text())['values'] if path.exists() else []
    write(path, {'replace': False, 'values': existing + [v for v in values if v not in existing]})


def main():
    """Writes every flavor's stone resources and the shared lang and tag files."""
    lang_path = ASSETS / 'lang/en_us.json'
    lang = json.loads(lang_path.read_text()) if lang_path.exists() else {}
    tags = {'pickaxe': [], 'slabs': [], 'walls': []}
    for flavor in FLAVORS:
        block(flavor)
        slab(flavor)
        wall(flavor)
        recipes(flavor)
        lang.update(names(flavor))
        tags['pickaxe'] += [f'{NS}:{flavor}_stone_{form}' for form in ('block', 'slab', 'wall')]
        tags['slabs'].append(f'{NS}:{flavor}_stone_slab')
        tags['walls'].append(f'{NS}:{flavor}_stone_wall')
    write(lang_path, lang)
    tag_root = RESOURCES / 'data/minecraft/tags'
    add_to_tag(tag_root / 'block/mineable/pickaxe.json', tags['pickaxe'])
    for kind in ('slabs', 'walls'):
        add_to_tag(tag_root / f'block/{kind}.json', tags[kind])
        add_to_tag(tag_root / f'item/{kind}.json', tags[kind])


if __name__ == '__main__':
    main()
