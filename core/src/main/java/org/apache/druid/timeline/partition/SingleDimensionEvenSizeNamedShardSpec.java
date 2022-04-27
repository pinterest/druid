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

import javax.annotation.Nullable;
import java.util.Objects;

public class SingleDimensionEvenSizeNamedShardSpec extends SingleDimensionEvenSizeShardSpec
{
  @JsonIgnore
  private final String partitionName;

  @JsonCreator
  public SingleDimensionEvenSizeNamedShardSpec(
          @JsonProperty("dimension") String dimension,
          @JsonProperty("start") String start,
          @JsonProperty("end") String end,
          @JsonProperty("partitionNum") int partitionNum,
          @JsonProperty("partitions") int partitions,
          @JsonProperty("partitionSize") int partitionSize,
          @JsonProperty("startCount") int startCount,
          @JsonProperty("endCount") int endCount,
          @JsonProperty("partitionName") String partitionName,
          @JsonProperty("numCorePartitions") @Nullable Integer numCorePartitions // nullable for backward compatibility
  )
  {
    super(dimension, start, end, partitionNum, partitions, partitionSize, startCount, endCount, numCorePartitions);
    Preconditions.checkArgument(partitionName != null && !partitionName.isEmpty(), "partitionName");
    this.partitionName = partitionName;
  }

  @JsonProperty("partitionName")
  public String getPartitionName()
  {
    return this.partitionName;
  }

  @Override
  public <T> PartitionChunk<T> createChunk(T obj)
  {
    return NamedNumberedPartitionChunk.make(getPartitionNum(), getPartitions(), partitionName, obj);
  }

  @Override
  public Object getIdentifier()
  {
    return this.partitionName + "_" + this.getPartitionNum();
  }

  @Override
  public String toString()
  {
    return "SingleDimensionEvenSizeNamedShardSpec{" +
           "dimension='" + getDimension() + '\'' +
           ", start='" + getStart() + '\'' +
           ", end='" + getEnd() + '\'' +
           ", partitionNum=" + getPartitionNum() +
           ", partitions=" + getPartitions() +
           ", partitionSize=" + getPartitionSize() +
           ", startCount=" + getStartCount() +
           ", endCount=" + getEndCount() +
           ", partitionName=" + partitionName +
           '}';
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }

    if (!(o instanceof SingleDimensionEvenSizeNamedShardSpec)) {
      return false;
    }

    final SingleDimensionEvenSizeNamedShardSpec that = (SingleDimensionEvenSizeNamedShardSpec) o;
    if (!getDimension().equals(that.getDimension())) {
      return false;
    } else if (!getStart().equals(that.getStart())) {
      return false;
    } else if (!getEnd().equals(that.getEnd())) {
      return false;
    } else if (getPartitionNum() != that.getPartitionNum()) {
      return false;
    } else if (getPartitions() != that.getPartitions()) {
      return false;
    } else if (getPartitionSize() != that.getPartitionSize()) {
      return false;
    } else if (getStartCount() != that.getStartCount()) {
      return false;
    } else if (getEndCount() != that.getEndCount()) {
      return false;
    }
    return partitionName.equals(that.getPartitionName());
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(
        getStart(),
        getEnd(),
        getPartitionNum(),
        getPartitions(),
        getPartitionSize(),
        getStartCount(),
        getEndCount(),
        partitionName
    );
  }
}
