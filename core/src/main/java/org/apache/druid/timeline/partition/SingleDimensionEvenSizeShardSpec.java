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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import org.apache.druid.data.input.InputRow;
import org.apache.druid.java.util.common.IAE;
import org.apache.druid.java.util.common.ISE;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Unlike SingleDimensionShardSpec, start and end are both inclusive in SingleDimensionEvenSizeShardSpec as one
 * dimension value can span multiple shard specs
 */
public class SingleDimensionEvenSizeShardSpec extends SingleDimensionShardSpec
{
  @JsonIgnore
  private final int partitionSize;
  @JsonIgnore
  private final int startCount;
  @JsonIgnore
  private final int endCount;
  @JsonIgnore
  private final int partitions;
  @Nullable
  private final int numCorePartitions;

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
          @JsonProperty("numCorePartitions") @Nullable Integer numCorePartitions // nullable for backward compatibility
  )
  {
    super(dimension, start, end, partitionNum, numCorePartitions);
    Preconditions.checkArgument(dimension != null && !dimension.isEmpty(), "dimension");
    Preconditions.checkArgument(start != null && !start.isEmpty(), "start");
    Preconditions.checkArgument(end != null && !end.isEmpty(), "end");
    Preconditions.checkArgument(partitionNum >= 0, "partitionNum");
    Preconditions.checkArgument(partitions > 0, "partitions");
    Preconditions.checkArgument(partitionSize > 0, "partitionSize");
    Preconditions.checkArgument(startCount > 0, "startCount");
    Preconditions.checkArgument(endCount > 0, "endCount");
    this.partitions = partitions;
    this.partitionSize = partitionSize;
    this.startCount = startCount;
    this.endCount = endCount;
    this.numCorePartitions = numCorePartitions;
  }

  @JsonProperty("partitions")
  public int getPartitions()
  {
    return partitions;
  }

  @JsonProperty("partitionSize")
  public int getPartitionSize()
  {
    return partitionSize;
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
    return (long timestamp, InputRow row) -> {
      // 1) Get dimension value
      final List<String> values = row.getDimension(getDimension());
      if (values == null || values.size() != 1) {
        throw new IAE("Value must not be null or of multi value type");
      }
      String value = values.get(0);

      // 2) Get all the shards with dimension value in [shard.start, shard.end] interval
      List<SingleDimensionEvenSizeShardSpec> candidateShardSpecs = shardSpecs
          .stream()
          .map(s -> (SingleDimensionEvenSizeShardSpec) s)
          .filter(s -> value.compareTo(s.getStart()) >= 0 && value.compareTo(s.getEnd()) <= 0)
          .collect(Collectors.toList());

      if (candidateShardSpecs.size() == 1) {
        return candidateShardSpecs.get(0);
      } else if (candidateShardSpecs.isEmpty()) {
        throw new ISE(
            "The number of candidate shard specs is 0. row[%s] doesn't fit in any shard[%s]",
            row,
            shardSpecs
        );
      } else {
        // 3) The value is in multiple shards, pick a shard according to how many rows the value in each shard
        // Calculate weights
        IntStream countStream = candidateShardSpecs.stream()
                                                   .mapToInt(s -> {
                                                     if (value.equals(s.getStart()) && value.equals(s.getEnd())) {
                                                       return s.getPartitionSize();
                                                     } else if (value.equals(s.getStart())) {
                                                       return s.getStartCount();
                                                     } else if (value.equals(s.getEnd())) {
                                                       return s.getEndCount();
                                                     } else {
                                                       throw new ISE("Either start or end should equal to value");
                                                     }
                                                   });

        double[] weights = new double[candidateShardSpecs.size()];
        int curWeight = 0;
        int[] rowsInShard = countStream.toArray();
        int totalWeight = IntStream.of(rowsInShard).sum();
        for (int i = 0; i < rowsInShard.length - 1; i++) {
          curWeight += rowsInShard[i];
          weights[i] = curWeight / (double) totalWeight;
        }
        weights[rowsInShard.length - 1] = 1;

        double r = ThreadLocalRandom.current().nextDouble();
        int index = Arrays.binarySearch(weights, r);
        if (index < 0) {
          index = -index - 1;
          if (index == candidateShardSpecs.size()) {
            throw new ISE("The value is in multiple shards but row[%s] doesn't fit in any shard[%s]", row, shardSpecs);
          }
        }
        return candidateShardSpecs.get(index);
      }
    };
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
    return NumberedPartitionChunk.make(getPartitionNum(), partitions, obj);
  }

  @Override
  public String toString()
  {
    return "SingleDimensionEvenSizeShardSpec{" +
           "dimension='" + getDimension() + '\'' +
           ", start='" + getStart() + '\'' +
           ", end='" + getEnd() + '\'' +
           ", partitionNum=" + getPartitionNum() +
           ", partitions=" + getPartitions() +
           ", partitionSize=" + getPartitionSize() +
           ", startCount=" + getStartCount() +
           ", endCount=" + getEndCount() +
           '}';
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }

    if (!(o instanceof SingleDimensionEvenSizeShardSpec)) {
      return false;
    }

    final SingleDimensionEvenSizeShardSpec that = (SingleDimensionEvenSizeShardSpec) o;
    if (!getDimension().equals(that.getDimension())) {
      return false;
    } else if (!getStart().equals(that.getStart())) {
      return false;
    } else if (!getEnd().equals(that.getEnd())) {
      return false;
    } else if (getPartitionNum() != that.getPartitionNum()) {
      return false;
    } else if (partitions != that.getPartitions()) {
      return false;
    } else if (partitionSize != that.getPartitionSize()) {
      return false;
    } else if (startCount != that.getStartCount()) {
      return false;
    }
    return endCount == that.getEndCount();
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(getStart(), getEnd(), getPartitionNum(), partitions, partitionSize, startCount, endCount);
  }
}
