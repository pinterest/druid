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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.apache.druid.java.util.common.ISE;

import javax.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NamedNumberedShardSpecFactory implements PartialShardSpec
{
  @JsonProperty
  private final String partitionName;

  private static final Map<String, NamedNumberedShardSpecFactory> INSTANCES = new ConcurrentHashMap<>();

  @JsonCreator
  public NamedNumberedShardSpecFactory(
      @JsonProperty("partitionName") String partitionName
  )
  {
    if (partitionName == null) {
      throw new ISE("partition Name is null");
    }
    this.partitionName = partitionName;
  }

  @JsonProperty
  public String getPartitionName()
  {
    return partitionName;
  }

  public static NamedNumberedShardSpecFactory instance(String partitionName)
  {
    Preconditions.checkNotNull(partitionName, "partitionName is required");
    return INSTANCES.computeIfAbsent(partitionName, NamedNumberedShardSpecFactory::new);
  }
 // TODO: Once all working we have to remove create method from code

  //@Override
  public ShardSpec create(ObjectMapper objectMapper, @Nullable ShardSpec specOfPreviousMaxPartitionId)
  {
    if (specOfPreviousMaxPartitionId == null) {
      return new NamedNumberedShardSpec(0, 0, partitionName);
    } else {
      final NumberedShardSpec prevSpec = (NumberedShardSpec) specOfPreviousMaxPartitionId;
      //return new NamedNumberedShardSpec(prevSpec.getPartitionNum() + 1, prevSpec.getPartitions(), partitionName);
      return null;
    }
  }

  //@Override
  public ShardSpec create(ObjectMapper objectMapper, int partitionId)
  {
    return new NamedNumberedShardSpec(partitionId, 0, partitionName);
  }

  @Override
  public ShardSpec complete(ObjectMapper objectMapper, int partitionId, int numCorePartitions) {

    return new NamedNumberedShardSpec(partitionId , numCorePartitions, partitionName);
  }

  @Override
  public Class<? extends ShardSpec> getShardSpecClass()
  {
    return NamedNumberedShardSpec.class;
  }
}
