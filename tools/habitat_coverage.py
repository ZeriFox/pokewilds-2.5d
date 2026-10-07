#!/usr/bin/env python3
"""Audit every playable species against actual route pools from the delivered JAR.

This is coverage evidence, not proof that every generated encounter is valid.
The helper invokes Route.allowedPokemon (including expansion and duplicate weights)
with the real runtime; it does not reimplement route membership in Python.
"""
from collections import Counter, defaultdict
import argparse
import json
import os
import re
import subprocess
import zipfile
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256

HELPER = r'''
package com.pkmngen.game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.utils.*;
import com.pkmngen.leaks.LeakTracer;
public final class HabitatCoverage {
  static Throwable error;
  public static void main(String[] args) {
    Game.leakTracer=LeakTracer.NoOp.INSTANCE;
    Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
    config.setInitialVisible(false);config.setWindowedMode(320,288);
    new Lwjgl3Application(new Sample(),config);
    if(error!=null){error.printStackTrace();System.exit(1);}
    System.out.println("HABITAT COVERAGE COMPLETE");System.exit(0);
  }
  static class Sample extends Game {
    Sample(){super(new String[0],2);}
    @Override public void create(){try{
      super.create();actionStack.clear();
      if(ExpansionDex.habitatId("treecko")!=2)throw new IllegalStateException("Habitat-aware JAR required");
      JsonValue result=new JsonValue(JsonValue.ValueType.object);
      for(JsonValue route:new JsonReader().parse(Gdx.files.local("route-names.json"))){
        JsonValue pool=new JsonValue(JsonValue.ValueType.array);
        for(String species:new Route(route.asString(),10).allowedPokemon())pool.addChild(new JsonValue(species));
        result.addChild(route.asString(),pool);
      }
      Gdx.files.local("route-pools.json").writeString(result.prettyPrint(JsonWriter.OutputType.json,100),false);
    }catch(Throwable e){error=e;}Gdx.app.exit();}
    @Override public void render(){}
  }
}
'''

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write-docs', action='store_true', help='Explicitly update tracked docs/HABITAT-COVERAGE.md after reviewing the audit')
    args = parser.parse_args()
    dest = ROOT / 'build/habitat-coverage'
    dest.mkdir(parents=True, exist_ok=True)
    classes = dest / 'classes'
    classes.mkdir(exist_ok=True)
    java, javac = find_jdk()
    route_source = (ROOT / 'src/com/pkmngen/game/Route.java').read_text(encoding='utf-8')
    method = route_source.split('public ArrayList<String> allowedPokemon()', 1)[1].split('public ArrayList<String> goodRodPokemon()', 1)[0]
    routes = sorted(set(re.findall(r'this\.name\.equals\("([^"]*)"\)', method)))
    if len(routes) < 25:
        raise RuntimeError('Route catalog extraction is incomplete')
    (dest / 'route-names.json').write_text(json.dumps(routes), encoding='utf-8')
    source = dest / 'HabitatCoverage.java'
    source.write_text(HELPER, encoding='utf-8')
    cp = os.pathsep.join(map(str, (classes, JAR)))
    log_path = dest / 'coverage.log'
    with log_path.open('w', encoding='utf-8') as log:
        log.write('JAR SHA256 ' + sha256(JAR) + '\n'); log.flush()
        for command in ([javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', str(JAR), '-d', str(classes), str(source)],
                        [java, '-Xmx2g', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off', '-cp', cp, 'com.pkmngen.game.HabitatCoverage']):
            subprocess.run(command, cwd=dest, stdout=log, stderr=subprocess.STDOUT, timeout=120, creationflags=CREATE_FLAGS, check=True)
    if 'HABITAT COVERAGE COMPLETE' not in log_path.read_text(encoding='utf-8'):
        raise RuntimeError('Native audit did not finish')
    pools = json.loads((dest / 'route-pools.json').read_text(encoding='utf-8'))
    with zipfile.ZipFile(JAR) as jar:
        original = {m[1] for path in jar.namelist() if (m := re.fullmatch(r'pokemon/(?:credited/)?pokemon/([^/]+)/front\.png', path))}
        expansion = json.loads(jar.read('visual/dex/expansion-dex.json'))['species']
        profiles = json.loads(jar.read('visual/biomes/profiles.json'))
    catalog = original | expansion.keys()
    rules = {species: rule for rule in profiles['speciesRules'] for species in rule['species']}
    memberships = defaultdict(dict)
    for route, entries in pools.items():
        for species, count in Counter(entries).items():
            memberships[species][route] = count
    rows = []
    habitat_names = {1: 'cave', 2: 'forest', 3: 'grassland', 4: 'mountain', 5: 'rare', 6: 'rough-terrain', 7: 'sea', 8: 'urban', 9: 'waters-edge'}
    for species in sorted(catalog):
        entry = expansion.get(species, {})
        rule = rules.get(species)
        route_map = memberships.get(species, {})
        protected = bool(entry.get('legendary') or entry.get('mythical'))
        if protected and route_map:
            raise RuntimeError('Protected expansion species entered an ordinary route: ' + species)
        habitat = entry.get('habitatId', 0)
        coverage = 'explicit gate' if rule else 'canonical habitat' if habitat else 'route default / unknown metadata' if route_map else 'unknown / no ordinary route'
        status = 'ordinary route' if route_map else 'no ordinary route'
        if protected:
            status = 'legendary/mythical excluded'
        elif entry and not entry.get('spawnable'):
            status = 'expansion spawn disabled'
        elif entry.get('evolvesFrom'):
            status += '; evolved species'
        gate = {key: value for key, value in (rule or {}).items() if key not in ('species', 'weight')}
        if habitat:
            gate['canonicalHabitat'] = habitat_names.get(habitat, 'unknown-id-' + str(habitat))
        rows.append({'species': species, 'source': 'expansion' if entry else 'upstream', 'status': status,
                     'coverage': coverage, 'habitatId': habitat,
                     'routes': route_map, 'weightPerRouteEntry': rule.get('weight', 20) if rule else 20,
                     'gate': gate, 'legendary': protected})
    payload = {'jarSha256': sha256(JAR), 'catalogSize': len(catalog), 'routeCount': len(pools),
               'explicitRuleSpecies': len(catalog & rules.keys()), 'rows': rows}
    (dest / 'coverage.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
    summary = Counter(row['status'].split(';')[0] for row in rows)
    coverage_summary = Counter(row['coverage'] for row in rows)
    lines = ['# Habitat coverage audit', '',
             'Generated by `python tools/habitat_coverage.py`. The route membership below was read by invoking the actual delivered JAR.', '',
             f'- JAR SHA-256: `{payload["jarSha256"]}`',
             f'- Playable sprite/data catalog: {len(catalog)} species/forms ({len(original)} upstream sprite records, {len(expansion)} expansion data records; {len(original & expansion.keys())} overlap).',
             f'- Explicit route branches queried: {len(pools)}. Duplicate entries are preserved as ×N.',
             f'- Explicit surface/time habitat rules: {payload["explicitRuleSpecies"]}.',
             '- Categories: ' + '; '.join(f'{key}: {value}' for key, value in sorted(summary.items())) + '.', '',
             '- Coverage: ' + '; '.join(f'{key}: {value}' for key, value in sorted(coverage_summary.items())) + '.', '',
             '## Limits and findings', '',
             '- This records the full playable catalog and exact route pools. It does not claim complete ecological coverage or a procedural generation test.',
             '- Known expansion habitatId metadata now selects compatible ordinary routes and gates actual surface/water proximity. Definitions: [pinned PokeAPI habitat CSV](https://raw.githubusercontent.com/PokeAPI/pokeapi/bc92d3b6029ef1abe9e7ad424c400b338f3c11fe/data/v2/csv/pokemon_habitats.csv). These broad categories do not contain species-specific temperature/accessibility conditions.',
             '- Species with no explicit rule and no canonical metadata retain documented authored/type-derived route defaults. They are classified unknown rather than assigned invented ecological data; this remains a coverage limit.',
             '- WildSpawnRules.choose preserves route duplicates and never introduces a species absent from the candidate pool. A filtered-empty cell produces no ordinary encounter.',
             '- Initial/new-area ordinary spawns (GenIsland1), walking encounters (PlayerStanding), and ordinary rod-restricted fishing use the habitat gate. Stored tree encounters and authored dungeon/event pools retain their explicit identities and are not ecological random-spawn tests.',
             '- Explicit authored exceptions include the unique oasis Milotic, desert trapinch pits/sand fishing, night ghost encounters, cacturne/gengar events, and special legendary/puzzle creatures. The audit does not erase or relocate saved/owned creatures.',
             '- No ordinary route does not mean unavailable: evolutions, breeding, events and stored encounters remain possible. Event eligibility is not inferred from a missing route.',
             '- Species-specific weights are per eligible ordinary surface-route occurrence, not whole-world encounter probabilities. Fishing preserves the original uniform sampling of rod-filtered entries including duplicates; it does not apply surface rarity weights. Levels, evolution and route encounter-rate filters still apply.', '',
             '## Full catalog', '', '| Species/form | Data | Coverage | Availability | Route pool occurrences | Weight | Additional gate |',
             '|---|---|---|---|---|---:|---|']
    for row in rows:
        locations = ', '.join(f'{route or "(empty)"}' + (f' ×{count}' if count > 1 else '') for route, count in row['routes'].items()) or '—'
        gate = json.dumps(row['gate'], sort_keys=True, separators=(',', ':')) if row['gate'] else '—'
        lines.append(f'| {row["species"]} | {row["source"]} | {row["coverage"]} | {row["status"]} | {locations} | {row["weightPerRouteEntry"]} | {gate} |')
    report = '\n'.join(lines) + '\n'
    report_path = dest / 'HABITAT-COVERAGE.md'
    report_path.write_text(report, encoding='utf-8')
    if args.write_docs:
        (ROOT / 'docs/HABITAT-COVERAGE.md').write_text(report, encoding='utf-8')
    print(f'Habitat coverage: {len(rows)} species, {len(pools)} routes, {payload["explicitRuleSpecies"]} explicit gates. Report: {report_path}')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
