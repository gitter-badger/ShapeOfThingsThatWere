package com.galvarez.ttw.model;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySystem;
import com.artemis.annotations.Wire;
import com.artemis.utils.ImmutableBag;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.galvarez.ttw.EntityFactory;
import com.galvarez.ttw.model.DiplomaticSystem.Action;
import com.galvarez.ttw.model.DiplomaticSystem.State;
import com.galvarez.ttw.model.components.Army;
import com.galvarez.ttw.model.components.ArmyCommand;
import com.galvarez.ttw.model.components.Diplomacy;
import com.galvarez.ttw.model.components.InfluenceSource;
import com.galvarez.ttw.model.components.InfluenceSource.Modifiers;
import com.galvarez.ttw.model.data.Empire;
import com.galvarez.ttw.model.map.GameMap;
import com.galvarez.ttw.model.map.Influence;
import com.galvarez.ttw.model.map.MapPosition;
import com.galvarez.ttw.model.map.MapTools.Border;
import com.galvarez.ttw.model.map.Terrain;
import com.galvarez.ttw.rendering.components.Description;

/**
 * This classes computes the influence from the different sources (i.e. cities)
 * and update it every turn.
 * <p>
 * The game is centered on the influence idea. It starts from a source with a
 * certain power and flow on the neighboring map tiles. The throughput depends
 * on the source power, distance to the source and terrain cost/difficulty. When
 * multiple sources influence the same tile, it belongs to the source with the
 * highest influence score. For ease of understanding, influence is expressed as
 * percentages.
 * <p>
 * Ideally the influence progression should be:
 * <ul>
 * <li>at a constant pace and continuous from a turn to the next (for instance
 * one tile per turn)
 * <li>with decreasing values from the civilized center to the wild or disputed
 * border
 * <li>on a small scale so that close influence sources can fight for tiles
 * <p>
 * The model that works the best seems to be a linear interpolation of the
 * target influence. The target influence is computed as a waterfall algorithm
 * from the source.
 * 
 * @author Guillaume Alvarez
 */
@Wire
public final class InfluenceSystem extends EntitySystem {

  private static final Logger log = LoggerFactory.getLogger(InfluenceSystem.class);

  public static final int INITIAL_POWER = 100;

  private ComponentMapper<Empire> empires;

  private ComponentMapper<InfluenceSource> sources;

  private ComponentMapper<Diplomacy> relations;

  private ComponentMapper<ArmyCommand> commands;

  private ComponentMapper<MapPosition> positions;

  private ComponentMapper<Army> armies;

  private DiplomaticSystem diplomaticSystem;

  private final GameMap map;

  private Modifiers modifiers;

  @SuppressWarnings("unchecked")
  public InfluenceSystem(GameMap gameMap) {
    super(Aspect.getAspectForAll(InfluenceSource.class));
    this.map = gameMap;
  }

  @Override
  protected boolean checkProcessing() {
    return true;
  }

  @Override
  protected void inserted(Entity e) {
    InfluenceSource source = sources.get(e);
    source.power = INITIAL_POWER;
    if (source.power > 0) {
      MapPosition pos = positions.get(e);

      // first influence own tile
      Influence tile = map.getInfluenceAt(pos);
      tile.setInfluence(e, tile.getMaxInfluence());
      source.influencedTiles.add(pos);
    }
  }

  @Override
  protected void processEntities(ImmutableBag<Entity> empires) {
    // must apply each step to all sources to have a consistent behavior

    // first apply delta from previous turn and display it
    for (int x = 0; x < map.height; x++) {
      for (int y = 0; y < map.width; y++) {
        updateTileInfluence(x, y);
      }
    }

    // and update source power
    for (Entity city : empires) {
      updateInfluencedTiles(city);
      accumulatePower(city);
    }

    // then compute the delta for every entity and tile
    for (Entity empire : empires) {
      IntIntMap armyInfluenceOn = new IntIntMap();
      Diplomacy diplo = relations.get(empire);
      int armyPower = commands.get(empire).militaryPower;
      for (Entity enemy : diplo.getEmpires(State.WAR))
        armyInfluenceOn.put(enemy.getId(), armyPower - commands.get(enemy).militaryPower);

      InfluenceSource source = sources.get(empire);
      checkInfluencedByOther(source, empire);
      addDistanceDelta(source, empire, armyInfluenceOn);
    }
  }

  /**
   * When a city tile main influence belongs to an other empire, we add a
   * tribute diplomatic relation.
   */
  private void checkInfluencedByOther(InfluenceSource source, Entity empire) {
    Influence tile = map.getInfluenceAt(positions.get(empire));
    if (!tile.isMainInfluencer(empire) && tile.hasMainInfluence()) {
      Entity influencer = tile.getMainInfluenceSource(world);
      if (empire != influencer) {
        log.info("{} conquered by {}, will now be tributary to its conqueror.",
            empire.getComponent(Description.class).desc, influencer.getComponent(Description.class));
        source.power--;
        sources.get(influencer).power++;
        if (source.power <= 0) {
          // TODO source was destroyed, not sure what to do
        } else {
          Diplomacy loser = relations.get(empire);
          diplomaticSystem.changeRelation(empire, loser, influencer, relations.get(influencer), Action.SURRENDER);
          loser.proposals.remove(influencer);

          // keep influence on own tile
          tile.setInfluence(empire, tile.getMaxInfluence() + 1);
          source.influencedTiles.add(tile.position);
        }
      }
    }
  }

  private void updateTileInfluence(int x, int y) {
    Influence tile = map.getInfluenceAt(x, y);
    for (IntIntMap.Entry e : tile.getDelta()) {
      if (e.value != 0) {
        Entity empire = world.getEntity(e.key);
        EntityFactory.createFadingTileLabel(world, e.value > 0 ? "+" + e.value : Integer.toString(e.value),
            empires.get(empire).color, x, y);
      }
    }
    tile.applyDelta();
    // do not forget to update the main source
    Entity main = tile.getMainInfluenceSource(world);
    if (main != null)
      sources.get(main).influencedTiles.add(map.getPositionAt(x, y));
  }

  /**
   * Spread source influence around the source. For each tile already influenced
   * or next to one we compute the minimal distance to the source. Then from the
   * distance we compute the target influence level. Then apply the delta.
   */
  private void addDistanceDelta(InfluenceSource source, Entity e, IntIntMap armyInfluenceOn) {
    ObjectIntMap<MapPosition> targets = new ObjectIntMap<>();
    for (Entry<MapPosition, Integer> entry : getTargetInfluence(e, positions.get(e), source.power).entrySet()) {
      Influence tile = map.getInfluenceAt(entry.getKey());
      int target = canInfluence(e, entry.getKey()) ? entry.getValue().intValue()
      // start losing influence when no neighboring tile
          : 0;
      // do not forget the military from war
      if (tile.hasMainInfluence())
        target += armyInfluenceOn.get(tile.getMainInfluenceSource(world).getId(), 0);
      targets.put(entry.getKey(), target);
    }

    for (Entity s : source.secondarySources) {
      for (Entry<MapPosition, Integer> entry : getTargetInfluence(e, positions.get(s), armies.get(s).currentPower)
          .entrySet()) {
        MapPosition pos = entry.getKey();
        if (canInfluence(e, pos))
          targets.getAndIncrement(pos, 0, entry.getValue().intValue());
      }
    }

    for (ObjectIntMap.Entry<MapPosition> entry : targets) {
      Influence tile = map.getInfluenceAt(entry.key);
      int current = tile.getInfluence(e);
      int target = entry.value;
      if (target > current)
        tile.addInfluenceDelta(e, max(1, (target - current) / 10));
      else if (target < current)
        tile.addInfluenceDelta(e, -max(1, (current - target) / 10));
      // do nothing if same obviously
    }
  }

  private Map<MapPosition, Integer> getTargetInfluence(Entity source, MapPosition startPos, int startingPower) {
    Map<Terrain, Integer> costs = terrainCosts(source);
    Queue<MapPosition> frontier = new ArrayDeque<>(32);
    frontier.add(startPos);
    Map<MapPosition, Integer> targets = new HashMap<>();
    targets.put(startPos, startingPower);

    while (!frontier.isEmpty()) {
      MapPosition current = frontier.poll();
      int currentPower = targets.get(current);

      for (MapPosition next : map.getNeighbors(current)) {
        Influence inf = map.getInfluenceAt(next);
        if (!inf.terrain.moveBlock()) {
          int newTarget = currentPower - costs.get(inf.terrain);
          Integer oldTarget = targets.get(next);
          if (oldTarget == null || newTarget > oldTarget.intValue()) {
            targets.put(next, newTarget);
            if (inf.hasInfluence(source) && newTarget > 0)
              // only one tile from already influenced tiles
              frontier.offer(next);
          }
        }
      }
    }
    return targets;
  }

  private Map<Terrain, Integer> terrainCosts(Entity source) {
    modifiers = sources.get(source).modifiers;
    Map<Terrain, Integer> res = new EnumMap<>(Terrain.class);
    for (Terrain t : Terrain.values())
      res.put(t, max(1, t.moveCost() - modifiers.terrainBonus.get(t)));
    return res;
  }

  /**
   * Can only influence a tile if it belongs to the source or one of its
   * neighbor does. Cannot influence if we have a treaty.
   */
  private boolean canInfluence(Entity source, MapPosition pos) {
    Influence influence = map.getInfluenceAt(pos);
    if (influence.isMainInfluencer(source))
      return true;

    // cannot influence on tiles from empires we have a treaty with
    if (influence.hasMainInfluence()) {
      Diplomacy treaties = relations.get(source);
      if (treaties.getRelationWith(influence.getMainInfluenceSource(world)) == State.TREATY)
        return false;
    }

    // need a neighbor we already have influence on
    for (Border b : Border.values()) {
      Influence tile = map.getInfluenceAt(b.getNeighbor(pos));
      if (tile != null && tile.isMainInfluencer(source))
        return true;
    }
    return false;
  }

  /**
   * Remove from {@link InfluenceSource#influencedTiles} all the tiles the
   * entity is no longer the main influencer on.
   */
  private void updateInfluencedTiles(Entity e) {
    InfluenceSource source = sources.get(e);

    Predicate<MapPosition> isNotMain = p -> !map.getInfluenceAt(p).isMainInfluencer(e);
    source.influencedTiles.removeIf(isNotMain);
  }

  /**
   * Update the source power. Each turn we add the number of influenced tiles to
   * the advancement. When it reaches the threshold then power is increased by 1
   * and advancement is reset.
   * <p>
   * Power is lost when cities revolt.
   */
  private void accumulatePower(Entity empire) {
    InfluenceSource source = sources.get(empire);

    int increase = source.growth + source.influencedTiles.size();
    if (increase > 0) {
      Diplomacy diplomacy = relations.get(empire);
      List<Entity> tributes = diplomacy.getEmpires(State.TRIBUTE);
      int remains = increase;
      for (Entity other : tributes) {
        int tribute = min(remains, increase / tributes.size());
        sources.get(other).powerAdvancement += tribute;
        remains -= tribute;
      }
      increase = remains;
    }

    source.powerAdvancement += increase;
    if (source.powerAdvancement < 0) {
      source.power--;
      source.powerAdvancement = getRequiredPowerAdvancement(source) + source.powerAdvancement;
    } else {
      int required = getRequiredPowerAdvancement(source);
      if (source.powerAdvancement >= required) {
        source.powerAdvancement -= required;
        source.power++;
      }
    }
  }

  public int getRequiredPowerAdvancement(InfluenceSource source) {
    // TODO the base power should depend on the empire
    return source.power + 1;
  }

}
