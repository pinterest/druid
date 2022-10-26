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

package org.apache.druid.server.security;

import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class HadoopDTTest
{
  private PinAuthenticator pinAuth;

  @Before
  public void setUp() throws Exception
  {
    pinAuth = new PinAuthenticator(new BrokerPinAuthorizationConfig(
        "druid_ads_reporting_staging_query",
        ImmutableMap.of("monarch-fgac-prod-002-20220311",
                        "http://localhost:19193/gateway/monarch-fgac-prod-002-webhdfs")));
  }

  @Test
  public void testAnthenticateHadoopDT()
  {
    String principal = "bwang@monarch-fgac-prod-002-20220311";
    String token = "foo-token";
    boolean allow = pinAuth.authenticateTokenIdentifier(principal, token);
    Assert.assertFalse(allow);
  }

  @Test
  public void testAuthorizeSpiffe()
  {
    String payload1 = pinAuth.buildPayload("spiffe", "spiffe://pin220.com/teletraan/foo");
    boolean allow1 = pinAuth.authurizeByPastis(payload1);
    Assert.assertFalse(allow1);

    String payload2 = pinAuth.buildPayload("spiffe", "spiffe://pin220.com/teletraan/ingress-pinadmin/foo");
    boolean allow2 = pinAuth.authurizeByPastis(payload2);
    Assert.assertTrue(allow2);
  }

  @Test
  public void testAuthorizeOwner()
  {
    String payload1 = pinAuth.buildPayload("owner", "bwang");
    boolean allow1 = pinAuth.authurizeByPastis(payload1);
    Assert.assertTrue(allow1);

    String payload2 = pinAuth.buildPayload("owner", "svc-fgac-ads-reporting-hourly-compaction");
    boolean allow2 = pinAuth.authurizeByPastis(payload2);
    Assert.assertTrue(allow2);

    String payload3 = pinAuth.buildPayload("owner", "foo");
    boolean allow3 = pinAuth.authurizeByPastis(payload3);
    Assert.assertFalse(allow3);
  }

  @Ignore
  @Test
  // fail the test and check test log for cache entries
  public void testCache()
  {
    for (int i = 0; i < 100; i++) {
      String payload = pinAuth.buildPayload("spiffe", "spiffe://pin220.com/teletraan/ingress-pinadmin/foo");
      boolean allow = pinAuth.authurizeByPastis(payload);
      Assert.assertTrue(allow);
      String payload1 = pinAuth.buildPayload("owner", "bwang");
      boolean allow1 = pinAuth.authurizeByPastis(payload1);
      Assert.assertTrue(allow1);
      String payload2 = pinAuth.buildPayload("owner", "svc-fgac-ads-reporting-hourly-compaction");
      boolean allow2 = pinAuth.authurizeByPastis(payload2);
      Assert.assertTrue(allow2);
    }
    Assert.assertTrue(false);
  }
}
