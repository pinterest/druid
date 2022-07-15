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

import com.google.common.collect.BoundType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.apache.druid.data.input.InputRow;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class BloomFilterShardSpecTest
{
  // Create a dummy shard spec class that extends BloomFilterShardSpec
  class TestBloomFilterShardSpec extends BloomFilterShardSpec
  {
    @Override
    public <T> PartitionChunk<T> createChunk(T obj)
    {
      return null;
    }

    //@Override
    public boolean isInChunk(long timestamp, InputRow inputRow)
    {
      return false;
    }

    @Override
    public int getPartitionNum()
    {
      return 0;
    }

    @Override
    public int getNumCorePartitions()
    {
      return 0;
    }

    @Override
    public ShardSpecLookup getLookup(List<? extends ShardSpec> shardSpecs)
    {
      return null;
    }

    @Override
    public List<String> getDomainDimensions()
    {
      return null;
    }

    @Override
    public boolean isCompatible(Class<? extends ShardSpec> other)
    {
      return false;
    }
  }

  @Test
  public void testSimple() throws Exception
  {
    BloomFilterShardSpec s = new TestBloomFilterShardSpec();

    // Construct bloom filter
    int cardinality = 5;
    int dim2StartValue = 10;
    BloomFilter<CharSequence> dim1BloomFilter = BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8),
        cardinality
    );
    BloomFilter<CharSequence> dim2BloomFilter = BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8),
        cardinality
    );
    for (int i = 0; i < cardinality; i++) {
      dim1BloomFilter.put("" + i);
      dim2BloomFilter.put("" + (dim2StartValue + i));
    }

    // Construct test domain
    RangeSet<String> dim1RangeSet = TreeRangeSet.create();
    RangeSet<String> dim2RangeSet = TreeRangeSet.create();
    RangeSet<String> dim3RangeSet = TreeRangeSet.create();
    Map<String, RangeSet<String>> domain = ImmutableMap.of(
        "dim1", dim1RangeSet,
        "dim2", dim2RangeSet,
        "dim3", dim3RangeSet
    );

    // Test empty bloom filter
    Assert.assertTrue(s.possibleInBloomFilter(domain));

    // Put dimensions dim1 and dim2 into bloom filter
    Map<String, BloomFilter<CharSequence>> bloomFilters = ImmutableMap.of("dim1", dim1BloomFilter, "dim2", dim2BloomFilter);
    s.setBloomFilters(bloomFilters);
    Assert.assertEquals(bloomFilters, s.getBloomFilters());

    // Test empty domain
    Assert.assertTrue(s.possibleInBloomFilter(ImmutableMap.of()));

    // Test single dimension: value exists in bloom filter
    dim1RangeSet.add(Range.singleton("1"));
    Assert.assertTrue(s.possibleInBloomFilter(domain));

    // Test single dimension: value not exists in bloom filter
    dim1RangeSet.clear();
    dim2RangeSet.clear();
    dim3RangeSet.clear();
    dim1RangeSet.add(Range.singleton("7"));
    Assert.assertFalse(s.possibleInBloomFilter(domain));

    // Test single dimension: dimension doesn't exist in bloom filter
    dim1RangeSet.clear();
    dim2RangeSet.clear();
    dim3RangeSet.clear();
    dim3RangeSet.add(Range.singleton("1"));
    Assert.assertTrue(s.possibleInBloomFilter(domain));

    // Test single dimension: value is a list of point values all exist in bloom filter
    dim1RangeSet.clear();
    dim2RangeSet.clear();
    dim3RangeSet.clear();
    dim1RangeSet.add(Range.singleton("1"));
    dim1RangeSet.add(Range.singleton("2"));
    dim1RangeSet.add(Range.singleton("3"));
    Assert.assertTrue(s.possibleInBloomFilter(domain));

    // Test single dimension: value is a list of point values only one of them exists in bloom filter
    dim1RangeSet.clear();
    dim2RangeSet.clear();
    dim3RangeSet.clear();
    dim1RangeSet.add(Range.singleton("1"));
    dim1RangeSet.add(Range.singleton("7"));
    dim1RangeSet.add(Range.singleton("8"));
    Assert.assertTrue(s.possibleInBloomFilter(domain));

    // Test single dimension: value is a range
    dim1RangeSet.clear();
    dim2RangeSet.clear();
    dim3RangeSet.clear();
    dim1RangeSet.add(Range.range("1", BoundType.CLOSED, "14", BoundType.CLOSED));
    Assert.assertTrue(s.possibleInBloomFilter(domain));

    // Test two dimensions: both values exist in bloom filter
    dim1RangeSet.clear();
    dim2RangeSet.clear();
    dim3RangeSet.clear();
    dim1RangeSet.add(Range.singleton("1"));
    dim2RangeSet.add(Range.singleton("11"));
    Assert.assertTrue(s.possibleInBloomFilter(domain));

    // Test two dimensions: one value exists and the other value doesn't exist in bloom filter
    dim1RangeSet.clear();
    dim2RangeSet.clear();
    dim3RangeSet.clear();
    dim1RangeSet.add(Range.singleton("1"));
    dim2RangeSet.add(Range.singleton("7"));
    Assert.assertFalse(s.possibleInBloomFilter(domain));

    // Test two dimensions: one dimension and its value exists but the other dimension doesn't exist in bloom filter
    dim1RangeSet.clear();
    dim2RangeSet.clear();
    dim3RangeSet.clear();
    dim1RangeSet.add(Range.singleton("1"));
    dim3RangeSet.add(Range.singleton("2"));
    Assert.assertTrue(s.possibleInBloomFilter(domain));
  }
}
