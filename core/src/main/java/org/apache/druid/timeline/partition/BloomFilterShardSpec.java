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
import com.google.common.hash.BloomFilter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class BloomFilterShardSpec implements ShardSpec
{
  // Deliberately not declared as final because we will asyncly load the bloom
  // filters during bootstrap to change the assignment of this variable. In other words, before bootstrap is complete,
  // this variable is null and after bootstrap is complete, it can be non null.
  // Map key: dimension name, value: bloom filter for the dimension
  private Map<String, BloomFilter<CharSequence>> bloomFilters;

  @Override
  public boolean possibleInDomain(Map<String, RangeSet<String>> domain)
  {
    return possibleInBloomFilter(domain);
  }

  public Map<String, BloomFilter<CharSequence>> getBloomFilters()
  {
    return bloomFilters;
  }

  public void setBloomFilters(Map<String, BloomFilter<CharSequence>> bloomFilters)
  {
    this.bloomFilters = bloomFilters;
  }

  /**
   *
   * @param domain
   * @return False if domain is absolutely not possible in bloom filter; true otherwise (can be false positive)
   */
  protected boolean possibleInBloomFilter(Map<String, RangeSet<String>> domain)
  {
    if (bloomFilters == null || bloomFilters.isEmpty()) {
      return true;
    }

    Map<String, Set<String>> domainPointSet = getDomainPointSet(domain, bloomFilters.keySet());
    for (Map.Entry<String, Set<String>> e : domainPointSet.entrySet()) {
      if (bloomFilters.containsKey(e.getKey()) && !e.getValue().isEmpty()) {
        // For each dimension, at least one value in the domain point set must potentially exist in the bloom filter in
        // order to return true
        if (e.getValue()
             .stream()
             .noneMatch(v -> v == null || bloomFilters.get(e.getKey()).mightContain(v))) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * For each domain, convert a range set to a point set if all values are single values otherwise bypass
   *
   * @param domain The overall domain
   * @param filteredDomain The domain to filter
   * @return A map of domain to its point set or empty map
   */
  private Map<String, Set<String>> getDomainPointSet(Map<String, RangeSet<String>> domain, Set<String> filteredDomain)
  {
    if (domain == null || domain.isEmpty() || filteredDomain == null || filteredDomain.isEmpty()) {
      return ImmutableMap.of();
    }

    Map<String, Set<String>> domainPointSet = new HashMap<>();
    for (Map.Entry<String, RangeSet<String>> e : domain.entrySet()) {
      if (!filteredDomain.contains(e.getKey()) || e.getValue() == null || e.getValue().isEmpty()) {
        continue;
      }

      for (Range<String> v : e.getValue().asRanges()) {
        // If there are range values, simply bypass this domain
        if (v.isEmpty() || !v.hasLowerBound() || !v.hasUpperBound() ||
            v.lowerBoundType() != BoundType.CLOSED || v.upperBoundType() != BoundType.CLOSED ||
            !v.lowerEndpoint().equals(v.upperEndpoint())) {
          domainPointSet.remove(e.getKey());
          continue;
        }

        domainPointSet.computeIfAbsent(e.getKey(), k -> new HashSet<>())
                      .add(v.lowerEndpoint());
      }
    }

    return domainPointSet;
  }
}
