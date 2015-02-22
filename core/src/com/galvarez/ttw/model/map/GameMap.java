package com.galvarez.ttw.model.map;

import java.util.Collection;

import com.artemis.Entity;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.galvarez.ttw.model.data.Empire;
import com.galvarez.ttw.utils.MyMath;

public final class GameMap {

  public final Terrain[][] map;

  private final Entity[][] entityByCoord;

  private final MapPosition[][] posByCoord;

  private final Influence[][] influenceByCoord;

  public final int width, height;

  /** Represents the whole map as a single image. */
  public final Texture texture;

  public final Empire[] empires;

  public GameMap(Terrain[][] map, Collection<Empire> empires) {
    this.map = map;
    this.empires = empires.toArray(new Empire[0]);
    width = map.length;
    height = map[0].length;

    entityByCoord = new Entity[width][height];
    influenceByCoord = new Influence[width][height];
    posByCoord = new MapPosition[width][height];

    Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        MapPosition pos = posByCoord[x][y] = new MapPosition(x, y);
        influenceByCoord[x][y] = new Influence(pos, map[x][y]);
        pixmap.setColor(map[x][y].getColor());
        pixmap.drawPixel(x, y);
      }
    }

    texture = new Texture(pixmap);
    pixmap.dispose();
  }

  public Entity getEntityAt(int x, int y) {
    if (x < 0 || x >= entityByCoord.length || y < 0 || y >= entityByCoord[0].length)
      return null;
    return entityByCoord[x][y];
  }

  public Entity getEntityAt(MapPosition pos) {
    return getEntityAt(pos.x, pos.y);
  }

  public boolean hasEntity(MapPosition pos) {
    return getEntityAt(pos) != null;
  }

  public Terrain getTerrainAt(MapPosition pos) {
    return getTerrainAt(pos.x, pos.y);
  }

  public Terrain getTerrainAt(int x, int y) {
    if (x < 0 || x > map.length - 1 || y < 0 || y > map[0].length - 1)
      return null;
    return map[x][y];
  }

  public Influence getInfluenceAt(MapPosition pos) {
    return getInfluenceAt(pos.x, pos.y);
  }

  public Influence getInfluenceAt(int x, int y) {
    if (x < 0 || x > influenceByCoord.length - 1 || y < 0 || y > influenceByCoord[0].length - 1)
      return null;
    return influenceByCoord[x][y];
  }

  public boolean isOnMap(MapPosition p) {
    return isOnMap(p.x, p.y);
  }

  public boolean isOnMapBorder(MapPosition p) {
    return p.x == 0 || p.x == posByCoord.length - 1 || p.y == 0 || p.y == posByCoord[0].length - 1;
  }

  private boolean isOnMap(int x, int y) {
    return x >= 0 && x < posByCoord.length && y >= 0 && y < posByCoord[0].length;
  }

  public MapPosition getPositionAt(int x, int y) {
    if (x < 0 || x > posByCoord.length - 1 || y < 0 || y > posByCoord[0].length - 1)
      return null;
    return posByCoord[x][y];
  }

  public void setEntity(Entity e, int x, int y) {
    entityByCoord[x][y] = e;
  }

  public void setEntity(Entity e, MapPosition p) {
    setEntity(e, p.x, p.y);
  }

  public void moveEntity(Entity e, MapPosition from, MapPosition to) {
    entityByCoord[from.x][from.y] = null;
    entityByCoord[to.x][to.y] = e;
  }

  public Array<MapPosition> getNeighbors(int x, int y, int n) {
    Array<MapPosition> coordinates = new Array<MapPosition>();
    int min;
    int myrow;
    for (int row = y - n; row < y + n + 1; row++) {
      min = MyMath.min(2 * (row - y + n), n, -2 * (row - y - n) + 1);
      for (int col = x - min; col < x + min + 1; col++) {
        if ((col < 0) || (col >= width))
          continue;
        if (x == col && y == row)
          continue;
        else if (x % 2 == 0)
          myrow = 2 * y - row;
        else
          myrow = row;
        if ((myrow < 0) || (myrow >= height))
          continue;
        coordinates.add(new MapPosition(col, myrow));
      }
    }
    return coordinates;
  }

  public Array<MapPosition> getNeighbors(MapPosition pos) {
    return getNeighbors(pos.x, pos.y, 1);
  }

}
