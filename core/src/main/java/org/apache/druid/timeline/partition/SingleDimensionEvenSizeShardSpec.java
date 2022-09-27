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

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import org.apache.druid.java.util.common.ISE;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Unlike SingleDimensionShardSpec, start and end are both inclusive in SingleDimensionEvenSizeShardSpec as one
 * dimension value can span multiple shard specs
 */
public class SingleDimensionEvenSizeShardSpec extends SingleDimensionShardSpec
{
  @JsonIgnore
  private int partitionSize;
  @JsonIgnore
  private final int startCount;
  @JsonIgnore
  private final int endCount;
  @JsonIgnore
  private int partitions;

  @JsonCreator
  public SingleDimensionEvenSizeShardSpec(
          @JsonProperty("dimension") String dimension,
          @JsonProperty("start") String start,
          @JsonProperty("end") String end,
          @JsonProperty("partitionNum") int partitionNum,
          @JsonProperty("partitions") int partitions,
          @JsonProperty("partitionSize") int partitionSize,
          @JsonProperty("startCount") int startCount,
          @JsonProperty("endCount") int endCount,
          @JsonProperty("numCorePartitions") @Nullable Integer numCorePartitions, // nullable for backward compatibility
          @JacksonInject ObjectMapper jsonMapper
  )
  {
    super(dimension, start, end, partitionNum, numCorePartitions);
    Preconditions.checkArgument(dimension != null && !dimension.isEmpty(), "dimension");
    Preconditions.checkArgument(partitionNum >= 0, "partitionNum");
    Preconditions.checkArgument(startCount >= 0, "startCount");
    Preconditions.checkArgument(endCount >= 0, "endCount");
    this.partitions = partitions;
    this.partitionSize = partitionSize;
    this.startCount = startCount;
    this.endCount = endCount;
  }

  @JsonProperty("partitions")
  public int getPartitions()
  {
    return partitions;
  }

  public void setPartitions(int partitions)
  {
    this.partitions = partitions;
  }

  @JsonProperty("partitionSize")
  public int getPartitionSize()
  {
    return partitionSize;
  }

  public void setPartitionSize(int partitionSize)
  {
    this.partitionSize = partitionSize;
  }

  @JsonProperty("startCount")
  public int getStartCount()
  {
    return startCount;
  }

  @JsonProperty("endCount")
  public int getEndCount()
  {
    return endCount;
  }

  @Override
  public ShardSpecLookup getLookup(final List<? extends ShardSpec> shardSpecs)
  {
    throw new ISE("Only needed for ingestion");
  }

  @Override
  public List<String> getDomainDimensions()
  {
    return ImmutableList.of(getDimension());
  }

  @Override
  public boolean possibleInDomain(Map<String, RangeSet<String>> domain)
  {

    RangeSet<String> rangeSet = domain.get(getDimension());
    if (rangeSet == null) {
      return true;
    }
    return !rangeSet.subRangeSet(Range.closed(getStart(), getEnd())).isEmpty();
  }

  @Override
  public <T> PartitionChunk<T> createChunk(T obj)
  {
    return NumberedPartitionChunk.make(getPartitionNum(), getPartitions(), obj);
  }

  @Override
  public String toString()
  {
    return "SingleDimensionEvenSizeShardSpec{" +
           "dimension='" + getDimension() + '\'' +
           ", start='" + getStart() + '\'' +
           ", end='" + getEnd() + '\'' +
           ", partitionNum=" + getPartitionNum() +
           ", partitions=" + getNumCorePartitions() +
           ", partitionSize=" + getPartitionSize() +
           ", startCount=" + getStartCount() +
           ", endCount=" + getEndCount() +
           '}';
  }
}
