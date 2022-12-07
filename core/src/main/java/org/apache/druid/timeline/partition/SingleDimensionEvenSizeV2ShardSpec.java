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

package org.apache.druid.timeline.partition;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import org.apache.druid.java.util.common.ISE;

import javax.annotation.Nullable;
import java.util.*;

public class SingleDimensionEvenSizeV2ShardSpec extends SingleDimensionEvenSizeShardSpec
{
  @JsonIgnore
  private final Map<String, Integer> largePartitionDimensionValues;
  @JsonIgnore
  private final Set<String> groupKeyDimensions;

  @JsonCreator
  public SingleDimensionEvenSizeV2ShardSpec(
          @JsonProperty("dimension") String dimension,
          @JsonProperty("start") String start,
          @JsonProperty("end") String end,
          @JsonProperty("partitionNum") int partitionNum,
          @JsonProperty("partitions") int partitions,
          @JsonProperty("partitionSize") int partitionSize,
          @JsonProperty("largePartitionDimensionValues") Map<String, Integer> largePartitionDimensionValues,
          @JsonProperty("groupKeyDimensions") Set<String> groupKeyDimensions,
          @JsonProperty("numCorePartitions") @Nullable Integer numCorePartitions, // nullable for backward compatibility
          @JacksonInject ObjectMapper jsonMapper
  )
  {
    super(dimension, start, end, partitionNum, partitions, partitionSize, 0, 0, numCorePartitions, jsonMapper);
    this.largePartitionDimensionValues = largePartitionDimensionValues == null ? new HashMap<>() :
                                         largePartitionDimensionValues;
    this.groupKeyDimensions = groupKeyDimensions == null ? Collections.emptySet() : groupKeyDimensions;
  }

  @JsonProperty("largePartitionDimensionValues")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public Map<String, Integer> getLargePartitionDimensionValues()
  {
    return largePartitionDimensionValues;
  }

  @JsonProperty("groupKeyDimensions")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public Set<String> getGroupKeyDimensions()
  {
    return groupKeyDimensions;
  }

  // Not used, skip in serde
  @Override
  @JsonProperty("startCount")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public int getStartCount()
  {
    return 0;
  }

  // Not used, skip in serde
  @Override
  @JsonProperty("endCount")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public int getEndCount()
  {
    return 0;
  }

  @Override
  public ShardSpecLookup getLookup(final List<? extends ShardSpec> shardSpecs)
  {
    throw new ISE("Only needed for ingestion");
  }

  @Override
  public boolean possibleInDomain(Map<String, RangeSet<String>> domain)
  {

    RangeSet<String> rangeSet = domain.get(getDimension());
    if (rangeSet == null) {
      return true;
    }

    return largePartitionDimensionValues.keySet()
                                        .stream()
                                        .anyMatch(k -> rangeSet.contains(k)) ||
           (getStart() != null &&
            getEnd() != null &&
            !rangeSet.subRangeSet(Range.closed(getStart(), getEnd())).isEmpty());
  }

  @Override
  public <T> PartitionChunk<T> createChunk(T obj)
  {
    return NumberedPartitionChunk.make(getPartitionNum(), getPartitions(), obj);
  }

  @Override
  public String toString()
  {
    return "SingleDimensionEvenSizeV2ShardSpec{" +
           "dimension='" + getDimension() + '\'' +
           ", start='" + getStart() + '\'' +
           ", end='" + getEnd() + '\'' +
           ", partitionNum=" + getPartitionNum() +
           ", partitions=" + getNumCorePartitions() +
           ", partitionSize=" + getPartitionSize() +
           ", largePartitionDimensionValues=" + getLargePartitionDimensionValues() +
           ", groupKeyDimensions=" + getGroupKeyDimensions() +
           '}';
  }
}
