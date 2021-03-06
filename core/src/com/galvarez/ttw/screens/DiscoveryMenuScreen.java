package com.galvarez.ttw.screens;

import static java.lang.Math.max;
import static java.lang.String.format;

import com.artemis.Entity;
import com.artemis.World;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.galvarez.ttw.ThingsThatWereGame;
import com.galvarez.ttw.model.DiscoverySystem;
import com.galvarez.ttw.model.components.Discoveries;
import com.galvarez.ttw.model.components.InfluenceSource;
import com.galvarez.ttw.model.components.Research;
import com.galvarez.ttw.model.data.Discovery;
import com.galvarez.ttw.model.map.Terrain;
import com.galvarez.ttw.rendering.ui.FramedMenu;
import com.galvarez.ttw.screens.overworld.OverworldScreen;

/**
 * This screen appears when user tries to pause or escape from the main game
 * screen.
 * 
 * @author Guillaume Alvarez
 */
public final class DiscoveryMenuScreen extends AbstractPausedScreen<OverworldScreen> {

  private final FramedMenu topMenu, lastDiscovery, terrains, discoveryChoices;

  private final Discoveries empire;

  private final InfluenceSource source;

  private final DiscoverySystem discoverySystem;

  public DiscoveryMenuScreen(ThingsThatWereGame game, World world, SpriteBatch batch, OverworldScreen gameScreen,
      Entity empire, DiscoverySystem discoverySystem) {
    super(game, world, batch, gameScreen);
    this.empire = empire.getComponent(Discoveries.class);
    this.source = empire.getComponent(InfluenceSource.class);
    this.discoverySystem = discoverySystem;

    topMenu = new FramedMenu(skin, 800, 600);
    lastDiscovery = new FramedMenu(skin, 800, 600);
    terrains = new FramedMenu(skin, 800, 600);
    discoveryChoices = new FramedMenu(skin, 800, 600);
  }

  @Override
  protected void initMenu() {
    topMenu.clear();
    topMenu.addButton("Resume game", this::resumeGame);
    topMenu.addToStage(stage, 30, stage.getHeight() - 30, false);

    lastDiscovery.clear();
    if (empire.last == null) {
      lastDiscovery.addLabel("- No last discovery -");
    } else {
      lastDiscovery.addLabel("- Last discovery (effect doubled): " + empire.last.target.name + " -");
      lastDiscovery.addLabel("Discovered from " + previousString(empire.last));
      lastDiscovery.addLabel("Effects:");
      for (String effect : discoverySystem.effectsStrings(empire.last.target))
        lastDiscovery.addLabel(" - " + effect);
    }
    lastDiscovery.addToStage(stage, 30, topMenu.getY() - 30, false);

    terrains.clear();
    terrains.addLabel("- Terrains influence costs -");
    for (Terrain t : Terrain.values()) {
      Integer mod = source.modifiers.terrainBonus.get(t);
      int cost = max(1, mod != null ? t.moveCost() - mod : t.moveCost());
      terrains.addLabel(format("%s: %d (%s)", t.getDesc(), cost, mod != null && mod != 0 ? -mod : "no modifier"));
    }
    terrains.addToStage(stage, 30, lastDiscovery.getY() - 30, false);

    discoveryChoices.clear();
    discoveryChoices.addButton("Next discovery?", gameScreen::askDiscovery);
    discoveryChoices.addLabel(" ");
    discoveryChoices.addToStage(stage, 30, terrains.getY() - 30, false);
  }

  private static String previousString(Research next) {
    if (next.previous.isEmpty())
      return "our environment";

    StringBuilder sb = new StringBuilder();
    for (Discovery previous : next.previous)
      sb.append(previous.name).append(", ");
    sb.setLength(sb.length() - 2);
    return sb.toString();
  }

}
