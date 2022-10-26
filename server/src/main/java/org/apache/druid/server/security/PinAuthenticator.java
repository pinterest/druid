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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import org.apache.druid.java.util.common.logger.Logger;
import org.apache.hadoop.hdfs.security.token.delegation.DelegationTokenIdentifier;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Pinterest Authenticator Class
 */
public class PinAuthenticator
{
  private static final long PASTIS_CACHE_SIZE = 300L;
  private static final long HDT_CACHE_SIZE = 100L;
  private static final int CACHE_TIME_TO_LIVE_MIN = 10;
  private static final Logger log = new Logger(PinAuthenticator.class);
  private final String pastisFileName;
  private final Map<String, String> clusterUrlMap;
  private final LoadingCache<String, Boolean> pastisCache;
  private final LoadingCache<HdtKey, Boolean> hdtCache;

  @Inject
  public PinAuthenticator(BrokerPinAuthorizationConfig pinAuthorizationConfig)
  {
    this.pastisFileName = pinAuthorizationConfig.getPastisFileName();
    this.clusterUrlMap = pinAuthorizationConfig.getClusterUrlMap();
    this.pastisCache = generatePastisCache();
    this.hdtCache = generateHdtCache();
  }

  /**
   * Authenticate a request by pastis and hadoop delegation token. A request is allowed if one of following is true:
   *  1. no pastis file configured.
   *  2. request is not from external envoy-mesh.
   *  3. request is via external envoy mesh and no pastis rule configured for the host.
   *  3. request is via external envoy mesh and from spiffe allowed in pastis.
   *  4. request is via external envoy mesh and has a valid delegation token with owner allowed in pastis.
   * @param req a client request
   * @return true if allowed, otherwise false.
   */
  public boolean authenticate(HttpServletRequest req)
  {
    // use decider to ramp up traffic. TODO (yyang): remove after prod traffic is ramped up in all stages.
    if (!decidedToRampUp(req)) {
      return true;
    }

    // Only an external envoy mesh request with configured pastis needs further authentication
    if (pastisFileName.isEmpty() ||
        req.getHeader("x-forwarded-client-cert") == null ||
        (req.getHeader("x-envoy-internal") != null &&
         req.getHeader("x-envoy-internal").compareToIgnoreCase("true") == 0)) {
      return true;
    }

    String senderSpiffe = getSenderSpiffeFromReq(req);
    String payload = buildPayload("spiffe", senderSpiffe);
    boolean allowed = authurizeByPastis(payload);
    if (!allowed) {
      allowed = authenticateReqByHdt(req);
    }

    log.debug("request allowed=%b pastisCache.size=%d hdtCache.size=%d", allowed, pastisCache.size(),
              hdtCache.size());
    return allowed;
  }

  private boolean decidedToRampUp(HttpServletRequest req)
  {
    String decided = req.getHeader("use-pin-authentication");
    return (decided != null && "true".equalsIgnoreCase(decided));
  }

  private String getSenderSpiffeFromReq(HttpServletRequest req)
  {
    // example of a header:
    // header: x-forwarded-client-cert
    // value: By=spiffe://pin220.com/teletraan/druid-ads-reporting-staging/query;Hash=xxx;URI=spiffe://pin220.com/teletraan/pepsi/ads_reporting_service_postsubmit
    // spiffe is either followed by "URI="
    String prefix = "URI=";
    String val = req.getHeader("x-forwarded-client-cert");
    int startIdx = val.indexOf(prefix + "spiffe:") + prefix.length();
    String spiffe = val.substring(startIdx);
    if (spiffe.contains(";")) {
      spiffe = spiffe.substring(0, spiffe.indexOf(';'));
    }
    return spiffe;
  }

  @VisibleForTesting
  boolean authurizeByPastis(String payload)
  {
    Boolean allow = Boolean.FALSE;
    try {
      allow = pastisCache.get(payload);
    }
    catch (ExecutionException e) {
      log.error("Pastis cache query with payload %s, got error: %s", payload, e.getMessage());
    }
    return allow;
  }

  @VisibleForTesting
  String buildPayload(String type, String value)
  {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode pastisPayload = mapper.createObjectNode();
    ObjectNode input = pastisPayload.with("input");
    ObjectNode principal = input.putObject("principal");
    principal.put(type, value);
    return pastisPayload.toString();
  }

  private boolean authenticateReqByHdt(HttpServletRequest req)
  {
    // header: delegation-token
    // value: <token_string>
    String token = req.getHeader("delegation-token");
    if (token == null || token.isEmpty()) {
      log.error("Fail authentication with missing delegation-token in req! headers in req:");
      logHeaders(req);
      return false;
    }

    // header: principal-name
    // value: <user_or_service_account>@<cluster_name>
    String principal = req.getHeader("principal-name");
    if (principal == null || principal.isEmpty()) {
      log.error("Fail authentication with missing principal-name in req!  headers in req:");
      logHeaders(req);
      return false;
    }
    log.info("Authenticate request by Hadoop Delegation Token with principal=%s", principal);
    Boolean allow = Boolean.FALSE;
    try {
      allow = hdtCache.get(new HdtKey(principal, token));
    }
    catch (ExecutionException e) {
      log.error("Hadoop Delegation Token Authentication cache query with principal=%s, got error: %s",
                principal, e.getMessage());
    }
    return allow;
  }

  private LoadingCache<String, Boolean> generatePastisCache()
  {
    return CacheBuilder.newBuilder()
                       .maximumSize(PASTIS_CACHE_SIZE)
                       .expireAfterWrite(CACHE_TIME_TO_LIVE_MIN, TimeUnit.MINUTES)
                       .build(new CacheLoader<String, Boolean>() {
                         @Override
                         public Boolean load(String payload)
                         {
                           return postToPastis(payload);
                         }
                       });
  }

  private Boolean postToPastis(String payload)
  {
    HttpPost post = new HttpPost("http://localhost:18181/" + pastisFileName);
    post.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json");
    post.setEntity(new StringEntity(payload, ContentType.DEFAULT_TEXT));

    log.info("Send to pastis for authorization, post=%s, payload=%s", post, payload);

    Boolean authorized = false;
    try (CloseableHttpClient httpClient = HttpClients.createDefault();
         CloseableHttpResponse response = httpClient.execute(post)) {
      if (response.getStatusLine().getStatusCode() == 200) {
        String result = EntityUtils.toString(response.getEntity());
        JSONObject json = (JSONObject) new JSONParser().parse(result);
        authorized = (Boolean) json.get("result");
      } else {
        log.info("Pastis replied with failure status: %d", response.getStatusLine().getStatusCode());
      }
    }
    catch (Exception e) {
      log.error("Caught exception when posting spiffe to pastis: " + e);
    }

    return authorized;
  }

  private LoadingCache<HdtKey, Boolean> generateHdtCache()
  {
    return CacheBuilder.newBuilder()
                       .maximumSize(HDT_CACHE_SIZE)
                       .expireAfterWrite(CACHE_TIME_TO_LIVE_MIN, TimeUnit.MINUTES)
                       .build(new CacheLoader<HdtKey, Boolean>() {
                         @Override
                         public Boolean load(HdtKey hdtKey)
                         {
                           return authenticateHadoopDelegationToken(hdtKey.principal, hdtKey.token);
                         }
                       });
  }

  @VisibleForTesting
  boolean authenticateHadoopDelegationToken(String principal, String token)
  {
    String owner;
    Token<? extends TokenIdentifier> userToken = new Token();
    DelegationTokenIdentifier tokenIdentifier = new DelegationTokenIdentifier();

    log.info("Going to authenticate hadoop delegation token with principal=%s", principal);
    try {
      userToken.decodeFromUrlString(token);
      if (!userToken.getKind().toString().equals("HDFS_DELEGATION_TOKEN")) {
        log.error("Wrong token type!");
        throw new RuntimeException("Wrong token type " + userToken.getKind());
      }
      ByteArrayInputStream buf = new ByteArrayInputStream(userToken.getIdentifier());
      try (DataInputStream in = new DataInputStream(buf)) {
        tokenIdentifier.readFields(in);
        owner = tokenIdentifier.getOwner().toString();
      }
    }
    catch (IOException e) {
      throw new RuntimeException("Unable to decode provided token, error: " + e.getMessage());
    }

    String payload = buildPayload("owner", owner);
    if (!authurizeByPastis(payload)) {
      log.error("Failed to authorize Hadoop Delegation Token owner %s", owner);
      return false;
    }
    return authenticateTokenIdentifier(principal, token);
  }

  private String getGetwayUrlForPincipal(String principal)
  {
    String cluster = principal.substring(principal.indexOf('@') + 1);
    return clusterUrlMap.get(cluster);
  }

  boolean authenticateTokenIdentifier(String principal, String DTstring)
  {
    String gatewayUrl = getGetwayUrlForPincipal(principal);
    log.warn("authenticate Hadoop Delegation Token with gatewayUrl=%s", gatewayUrl);

    HttpGet get = new HttpGet(gatewayUrl + "/webhdfs/v1/user?delegation=" + DTstring + "&op=LISTSTATUS");

    get.setHeader("x-apache-knox-prod-instance", "true");

    boolean authorized = false;
    try (CloseableHttpClient httpClient = HttpClients.createDefault();
         CloseableHttpResponse response = httpClient.execute(get)) {
      authorized = (response.getStatusLine().getStatusCode() == 200
                    || response.getStatusLine().getStatusCode() == 204);
    }
    catch (Exception e) {
      log.error("Failed to authenticate against " + gatewayUrl + ": " + e);
    }
    return authorized;
  }

  private void logHeaders(HttpServletRequest req)
  {
    Enumeration<String> headerNames = req.getHeaderNames();
    if (headerNames == null) {
      log.info("No header in query request!");
      return;
    }
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      log.info("("
               + Thread.currentThread().getId()
               + ") Header Name - "
               + headerName
               + ", Value - "
               + req.getHeader(headerName));
    }

    Enumeration<String> params = req.getParameterNames();
    while (params.hasMoreElements()) {
      String paramName = params.nextElement();
      log.info("("
               + Thread.currentThread().getId()
               + ") Parameter Name - "
               + paramName
               + ", Value - "
               + req.getParameter(paramName));
    }
  }

  private class HdtKey
  {
    public final String principal;
    public final String token;

    public HdtKey(String principal, String token)
    {
      this.principal = principal;
      this.token = token;
    }

    @Override
    public boolean equals(Object o)
    {
      return ((o instanceof HdtKey) &&
              principal.equals(((HdtKey) o).principal) &&
              token.equals(((HdtKey) o).token));
    }

    @Override
    public int hashCode()
    {
      return (principal + token).hashCode();
    }
  }
}
