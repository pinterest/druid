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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class BloomFilterMetadataTest
{
  private final ObjectMapper mapper = TestHelper.makeJsonMapper();

  @Test
  public void testSerdeRoundTrip() throws Exception
  {
    BloomFilterMetadata b = new BloomFilterMetadata(0x1, ImmutableList.of("dim1", "dim2"));

    testSerdeHelper(
        b,
        "{\"version\":1,\"dimensions\":[\"dim1\",\"dim2\"]}"
    );
  }

  void testSerdeHelper(BloomFilterMetadata b, String expectedSerializedString) throws IOException
  {
    // Verify serialized format
    Assert.assertEquals(expectedSerializedString, mapper.writeValueAsString(b));
    // Verify round trip
    final BloomFilterMetadata deserialized = mapper.readValue(
        mapper.writeValueAsBytes(b),
        BloomFilterMetadata.class
    );
    Assert.assertEquals(b.getVersion(), deserialized.getVersion());
    Assert.assertEquals(b.getDimensions(), deserialized.getDimensions());
  }
}
