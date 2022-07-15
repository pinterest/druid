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

package org.apache.druid.segment;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BloomFilterObjectStrategyTest
{
  @Test
  public void testRoundTrip()
  {
    String[] values = {"a", "b", "c", "d", "e"};
    BloomFilter<CharSequence> bloomFilter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8),
                                                               values.length);
    for (String v : values) {
      bloomFilter.put(v);
    }

    byte[] serialized = BloomFilterObjectStrategy.STRATEGY
        .toBytes(bloomFilter);

    // Simulate read-only byte buffer as from Mmaped files
    ByteBuffer serializedByteBuffer = ByteBuffer.wrap(serialized).asReadOnlyBuffer();

    BloomFilter<CharSequence> deserialized = BloomFilterObjectStrategy.STRATEGY
        .fromByteBuffer(serializedByteBuffer, serialized.length);

    Assert.assertEquals(bloomFilter, deserialized);
  }
}
