package com.galvarez.ttw.model;

import static java.util.Arrays.asList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.galvarez.ttw.rendering.SpriteRenderSystem;
import com.galvarez.ttw.utils.Assets.Icon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.VoidEntitySystem;
import com.galvarez.ttw.model.components.AIControlled;
import com.galvarez.ttw.model.components.ArmyCommand;
import com.galvarez.ttw.model.components.Destination;
import com.galvarez.ttw.model.components.Diplomacy;
import com.galvarez.ttw.model.components.Discoveries;
import com.galvarez.ttw.model.components.InfluenceSource;
import com.galvarez.ttw.model.data.Discovery;
import com.galvarez.ttw.model.map.Terrain;
import com.galvarez.ttw.rendering.NotificationsSystem;
import com.galvarez.ttw.rendering.components.Description;
import com.galvarez.ttw.rendering.components.Name;
import com.galvarez.ttw.screens.overworld.OverworldScreen;

/**
 * Contains 'special discoveries', having special effects not covered by
 * {@link EffectsSystem}.
 *
 * @author Guillaume Alvarez
 */
@Wire
public final class SpecialDiscoveriesSystem extends VoidEntitySystem {

  private static final Logger log = LoggerFactory.getLogger(SpecialDiscoveriesSystem.class);

  private static final String VILLAGE = "Village";

  private static final String CITY = "City";

  private static final String RAFT = "Raft";

  private static final String BOAT = "Boat";

  private ComponentMapper<Name> names;

  private ComponentMapper<Description> descriptions;

  private ComponentMapper<InfluenceSource> sources;

  private ComponentMapper<Destination> destinations;

  private ComponentMapper<ArmyCommand> commanders;

  private ComponentMapper<Diplomacy> relations;

  private ComponentMapper<AIControlled> ai;

  private NotificationsSystem notifications;

  private SpriteRenderSystem sprites;

  private interface Special {
    void apply(Entity empire, Discovery d, Discoveries discoveries);
  }

  private final Map<String, Special> effects = new HashMap<>();

  private final OverworldScreen screen;

  public SpecialDiscoveriesSystem(OverworldScreen screen) {
    this.screen = screen;
    effects.put(VILLAGE, new VillageDiscovery());
    effects.put(CITY, new CityDiscovery());
    effects.put(RAFT, new RaftDiscovery());
    effects.put(BOAT, new BoatDiscovery());
  }

  private class VillageDiscovery implements Special {
    @Override
    public void apply(Entity empire, Discovery d, Discoveries discoveries) {
      if (!discoveries.done.contains(CITY)) {
        String name = names.get(empire).name;
        String desc = "Village of " + name;
        setDescription(empire, name, desc);
        sprites.setSprite(empire, "village");
        cannotMove(empire);
      }
    }
  }

  private class CityDiscovery implements Special {
    @Override
    public void apply(Entity empire, Discovery d, Discoveries discoveries) {
      String name = names.get(empire).name;
      String desc = "City of " + name;
      setDescription(empire, name, desc);
      sprites.setSprite(empire, "city");
      cannotMove(empire);
    }
  }

  private class RaftDiscovery implements Special {
    @Override
    public void apply(Entity empire, Discovery d, Discoveries discoveries) {
      for (Entity army : sources.get(empire).secondarySources)
        destinations.get(army).forbiddenTiles.add(Terrain.SHALLOW_WATER);
      commanders.get(empire).forbiddenTiles.add(Terrain.SHALLOW_WATER);
    }
  }

  private class BoatDiscovery implements Special {
    @Override
    public void apply(Entity empire, Discovery d, Discoveries discoveries) {
      List<Terrain> list = asList(Terrain.DEEP_WATER, Terrain.SHALLOW_WATER);
      for (Entity army : sources.get(empire).secondarySources)
        destinations.get(army).forbiddenTiles.addAll(list);
      commanders.get(empire).forbiddenTiles.addAll(list);
    }
  }

  @Override
  protected void processSystem() {
    // nothing to do, called by other systems to activate special effects
  }

  private void setDescription(Entity empire, String name, String desc) {
    descriptions.get(empire).desc = desc;
    log.info("{} is now known as '{}'", name, desc);
    // notify only player empire, avoid spamming
    if (!ai.has(empire) || relations.get(empire).hasRelationWith(screen.player))
      notifications.addNotification(() -> screen.select(empire, false), null, Icon.BUILDINGS,
          "%s is now known as '%s'", name, desc);
  }

  private void cannotMove(Entity capital) {
    capital.edit().remove(Destination.class);
  }

  public void checkDiscoveries(Map<String, Discovery> discoveries) {
    for (String d : effects.keySet())
      if (!discoveries.containsKey(d))
        log.warn("Cannot find discovery '{}' that has a special effect.", d);
  }

  public void apply(Entity empire, Discovery d, Discoveries discoveries) {
    Special effect = effects.get(d.name);
    if (effect != null)
      effect.apply(empire, d, discoveries);
  }
}
