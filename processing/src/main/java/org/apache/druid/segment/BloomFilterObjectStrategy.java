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
import org.apache.commons.lang.NotImplementedException;
import org.apache.druid.io.ByteBufferInputStream;
import org.apache.druid.segment.data.ObjectStrategy;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

/**
 * Contains serde logic of individual bloom filter object
 */
public class BloomFilterObjectStrategy implements ObjectStrategy<BloomFilter>
{
  public static final BloomFilterObjectStrategy STRATEGY = new BloomFilterObjectStrategy();

  @Override
  public Class<BloomFilter> getClazz()
  {
    return BloomFilter.class;
  }

  @Override
  public int compare(final BloomFilter o1, final BloomFilter o2)
  {
    throw new NotImplementedException();
  }

  @Override
  public BloomFilter fromByteBuffer(final ByteBuffer buffer, final int numBytes)
  {
    ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();
    readOnlyBuffer.limit(buffer.position() + numBytes);
    try (ByteBufferInputStream byteArrayInputStream = new ByteBufferInputStream(readOnlyBuffer);
         ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
      return (BloomFilter) objectInputStream.readObject();
    }
    catch (ClassNotFoundException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public byte[] toBytes(@Nullable final BloomFilter bloomFilter)
  {
    if (bloomFilter == null) {
      return null;
    } else {
      final byte[] bytes;
      try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
           ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
        objectOutputStream.writeObject(bloomFilter);
        bytes = byteArrayOutputStream.toByteArray();
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      return bytes;
    }
  }
}
