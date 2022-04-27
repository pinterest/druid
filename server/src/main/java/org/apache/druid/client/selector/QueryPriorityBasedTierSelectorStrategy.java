/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.client.selector;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import org.apache.druid.timeline.DataSegment;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class QueryPriorityBasedTierSelectorStrategy extends HighestPriorityTierSelectorStrategy
{

  private final Map<Integer, Integer> lookup;

  @JsonCreator
  public QueryPriorityBasedTierSelectorStrategy(
      @JacksonInject ServerSelectorStrategy serverSelectorStrategy,
      @JacksonInject QueryPriorityBasedTierSelectorStrategyConfig config
  )
  {
    super(serverSelectorStrategy);
    lookup = new HashMap<>(config.getQueryPriorityToTierPriorityMap());
  }


  @Nullable
  @Override
  public QueryableDruidServer pick(
      int queryPriority,
      Int2ObjectRBTreeMap<Set<QueryableDruidServer>> prioritizedServers,
      DataSegment segment
  )
  {
    if (lookup.containsKey(queryPriority)) {
      int tierPriority = lookup.get(queryPriority);
      if (prioritizedServers.containsKey(tierPriority)) {
        return Iterables.getOnlyElement(pick(prioritizedServers.get(tierPriority), segment, 1), null);
      }
    }
    // Default to the parent which is the highest priority tier selector
    return pick(prioritizedServers, segment);
  }
}
