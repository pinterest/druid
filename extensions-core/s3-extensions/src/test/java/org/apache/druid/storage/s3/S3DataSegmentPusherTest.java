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

package org.apache.druid.storage.s3;

import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.CanonicalGrantee;
import com.amazonaws.services.s3.model.Grant;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.Permission;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import org.apache.druid.java.util.common.Intervals;
import org.apache.druid.timeline.DataSegment;
import org.apache.druid.timeline.partition.NoneShardSpec;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 */
@RunWith(Parameterized.class)
public class S3DataSegmentPusherTest
{
  private final boolean testPushSupplimentalIndexes;

  public S3DataSegmentPusherTest(boolean testPushSupplimentalIndexes)
  {
    this.testPushSupplimentalIndexes = testPushSupplimentalIndexes;
  }

  @Parameterized.Parameters
  public static Collection testPushSupplimentalIndexes()
  {
    return Arrays.asList(true, false);
  }

  private static class ValueContainer<T>
  {
    private T value;

    public T getValue()
    {
      return value;
    }

    public void setValue(T value)
    {
      this.value = value;
    }
  }

  @Rule
  public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule
  public final TemporaryFolder tempSupplimentalIndexFolder = new TemporaryFolder();

  @Test
  public void testPush() throws Exception
  {
    testPushInternal(false, "key/foo/2015-01-01T00:00:00\\.000Z_2016-01-01T00:00:00\\.000Z/0/0/index\\.zip");
  }

  @Test
  public void testPushUseUniquePath() throws Exception
  {
    testPushInternal(true, "key/foo/2015-01-01T00:00:00\\.000Z_2016-01-01T00:00:00\\.000Z/0/0/[A-Za-z0-9-]{36}/index\\.zip");
  }

  private void testPushInternal(boolean useUniquePath, String matcher) throws
                                                                                                            Exception
  {
    // When there are multiple files to push, relax to only check total mock method calls because of potential multi
    // threaded execution
    ServerSideEncryptingAmazonS3 s3Client = testPushSupplimentalIndexes ?
                                            EasyMock.createMock(ServerSideEncryptingAmazonS3.class) :
                                            EasyMock.createStrictMock(ServerSideEncryptingAmazonS3.class);
    // Push 1 index file or/and 2 supplimental index files
    int expectedNumMockCalls = testPushSupplimentalIndexes ? 3 : 1;
    final AccessControlList acl = new AccessControlList();
    acl.setOwner(new Owner("ownerId", "owner"));
    acl.grantAllPermissions(new Grant(new CanonicalGrantee(acl.getOwner().getId()), Permission.FullControl));
    EasyMock.expect(s3Client.getBucketAcl(EasyMock.eq("bucket"))).andReturn(acl).times(expectedNumMockCalls);

    EasyMock.expect(s3Client.putObject(EasyMock.anyObject()))
            .andReturn(new PutObjectResult())
            .times(expectedNumMockCalls);

    EasyMock.replay(s3Client);

    S3DataSegmentPusherConfig config = new S3DataSegmentPusherConfig();
    config.setBucket("bucket");
    config.setBaseKey("key");

    S3DataSegmentPusher pusher = new S3DataSegmentPusher(s3Client, config);

    // Create a mock segment on disk
    File tmp = tempFolder.newFile("version.bin");

    final byte[] data = new byte[]{0x0, 0x0, 0x0, 0x1};
    Files.write(data, tmp);
    final long size = data.length;

    // Create mock segment supplimental indexes
    File supplimentalIndexFilesDir = null;
    if (testPushSupplimentalIndexes) {
      supplimentalIndexFilesDir = tempSupplimentalIndexFolder.newFolder(
          S3LoadSpec.SEGMENT_SUPPLIMENTAL_INDEX_KEY_PREFIX);
      List<String> supplimentalIndexes = ImmutableList.of("supplimental_index_1", "supplimental_index_2");
      for (String s : supplimentalIndexes) {
        // Create supplimental index directory
        File supplimentalIndexDir = new File(supplimentalIndexFilesDir, s);
        supplimentalIndexDir.mkdir();
        // Create dummy data in the directory
        File supplimentalIndexData = new File(supplimentalIndexDir, "dummy.bin");
        Files.write(data, supplimentalIndexData);
      }
    }

    DataSegment segmentToPush = new DataSegment(
        "foo",
        Intervals.of("2015/2016"),
        "0",
        new HashMap<>(),
        new ArrayList<>(),
        new ArrayList<>(),
        NoneShardSpec.instance(),
        0,
        size
    );

    DataSegment segment = pusher.push(tempFolder.getRoot(), supplimentalIndexFilesDir, segmentToPush, useUniquePath);

    Assert.assertEquals(segmentToPush.getSize(), segment.getSize());
    Assert.assertEquals(1, (int) segment.getBinaryVersion());
    Assert.assertEquals("bucket", segment.getLoadSpec().get("bucket"));
    Assert.assertTrue(
        segment.getLoadSpec().get("key").toString(),
        Pattern.compile(matcher).matcher(segment.getLoadSpec().get("key").toString()).matches()
    );
    Assert.assertEquals("s3_zip", segment.getLoadSpec().get("type"));
    List<String> expectedAvailableSupplimentalIndexes =
        testPushSupplimentalIndexes ? ImmutableList.of("supplimental_index_1", "supplimental_index_2") :
        ImmutableList.of();
    Assert.assertEquals(expectedAvailableSupplimentalIndexes, segment.getAvailableSupplimentalIndexes());

    EasyMock.verify(s3Client);
  }
}
