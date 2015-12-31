package com.galvarez.ttw.model.components;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.artemis.Component;
import com.artemis.Entity;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.galvarez.ttw.model.DiplomaticSystem.Action;
import com.galvarez.ttw.model.DiplomaticSystem.State;

public final class Diplomacy extends Component {

  public final Map<Entity, State> relations = new HashMap<>();

  public final ObjectIntMap<Entity> lastChange = new ObjectIntMap<>();

  public final Map<Entity, Action> proposals = new HashMap<>();

  public final EnumSet<State> knownStates = EnumSet.of(State.NONE);

  public Diplomacy() {
  }

  public State getRelationWith(Entity other) {
    if (other == null)
      return State.NONE;
    State state = relations.get(other);
    return state != null ? state : State.NONE;
  }

  public boolean hasRelationWith(Entity other) {
    if (other == null)
      return false;
    else
      return relations.containsKey(other) || proposals.containsKey(other) || lastChange.containsKey(other);
  }

  public Action getProposalTo(Entity other) {
    Action action = proposals.get(other);
    return action != null ? action : Action.NO_CHANGE;
  }

  /** Get all empires whose state is the one passed as a parameter. */
  public List<Entity> getEmpires(State state) {
    List<Entity> others = new ArrayList<Entity>();
    for (Entry<Entity, State> e : relations.entrySet()) {
      if (e.getValue() == state)
        others.add(e.getKey());
    }
    return others;
  }

}
