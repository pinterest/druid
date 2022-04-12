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

package org.apache.druid.timeline;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import org.apache.druid.java.util.common.logger.Logger;
import org.apache.druid.timeline.partition.PartitionChunk;
import org.apache.druid.utils.CollectionUtils;
import org.joda.time.Interval;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

public class NamespacedVersionedIntervalTimeline<VersionType, ObjectType extends Overshadowable<ObjectType>>
    implements TimelineLookup<VersionType, ObjectType>
{
  private static final Logger LOG = new Logger(NamespacedVersionedIntervalTimeline.class);

  final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

  final Map<String, VersionedIntervalTimeline<VersionType, ObjectType>> timelines = new HashMap<>();

  private final Comparator<? super VersionType> versionComparator;

  public static String getNamespace(Object identifier)
  {
    if (identifier == null) {
      return null;
    }

    String identifierStr = identifier.toString();
    int index = identifierStr.lastIndexOf('_');
    if (index <= 0) {
      return null;
    }
    return identifierStr.substring(0, index);
  }

  public Set<String> getNamespaces()
  {
    return timelines.keySet();
  }

  public static NamespacedVersionedIntervalTimeline<String, DataSegment> forSegments(Iterable<DataSegment> segments)
  {
    return forSegments(segments.iterator());
  }

  public static NamespacedVersionedIntervalTimeline<String, DataSegment> forSegments(Iterator<DataSegment> segments)
  {
    NamespacedVersionedIntervalTimeline<String, DataSegment> timeline = new NamespacedVersionedIntervalTimeline<>(
        Ordering.natural());
    addSegments(timeline, segments);
    return timeline;
  }

  public static void addSegments(
      NamespacedVersionedIntervalTimeline<String, DataSegment> timeline,
      Iterator<DataSegment> segments
  )
  {
    // TODO - may want to create addAll method to limit number of times we need to aquire write lock
    while (segments.hasNext()) {
      DataSegment segment = segments.next();
      timeline.add(
          getNamespace(segment.getShardSpec().getIdentifier()),
          segment.getInterval(),
          segment.getVersion(),
          segment.getShardSpec().createChunk(segment)
      );
    }
  }

  @VisibleForTesting
  public Map<Interval, TreeMap<VersionType, VersionedIntervalTimeline<VersionType, ObjectType>.TimelineEntry>> getAllTimelineEntries()
  {
    // This function should merge maps for tests after real
    // NamespacedVersionedIntervalTimeline tests are implemented
    if (timelines.isEmpty()) {
      return new HashMap<>();
    }

    return timelines.get(null).getAllTimelineEntries();
  }

  /**
   * Returns a lazy collection with all objects (including overshadowed, see {@link #findFullyOvershadowed}) in this
   * NamespacedVersionedIntervalTimeline to be used for iteration or {@link Collection#stream()} transformation.
   * The order of objects in this collection is unspecified.
   * <p>
   * Note: iteration over the returned collection may not be as trivially cheap as, for example, iteration over an
   * ArrayList. Try (to some reasonable extent) to organize the code so that it iterates the returned collection only
   * once rather than several times.
   */
  public Collection<ObjectType> iterateAllObjects()
  {
    List<ObjectType> allObjects = new ArrayList<>();
    for (VersionedIntervalTimeline<VersionType, ObjectType> versionedIntervalTimeline : timelines.values()) {
      allObjects.addAll(versionedIntervalTimeline.iterateAllObjects());
    }
    return CollectionUtils.createLazyCollectionFromStream(
        () -> allObjects.stream(),
        allObjects.size()
    );
  }

  public Map<String, VersionedIntervalTimeline<VersionType, ObjectType>> getTimelines()
  {
    return timelines;
  }

  public NamespacedVersionedIntervalTimeline()
  {
    this((Comparator<? super VersionType>) Ordering.natural());
  }

  public NamespacedVersionedIntervalTimeline(
      Comparator<? super VersionType> versionComparator
  )
  {
    this.versionComparator = versionComparator;
  }

  public void add(String namespace, final Interval interval, VersionType version, PartitionChunk<ObjectType> object)
  {
    try {
      lock.writeLock().lock();

      VersionedIntervalTimeline<VersionType, ObjectType> timeline = timelines.computeIfAbsent(
          namespace,
          func -> new VersionedIntervalTimeline<>(versionComparator)
      );

      timeline.add(interval, version, object);
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  public PartitionChunk<ObjectType> remove(
      String namespace,
      Interval interval,
      VersionType version,
      PartitionChunk<ObjectType> chunk
  )
  {
    try {
      lock.writeLock().lock();

      VersionedIntervalTimeline<VersionType, ObjectType> timeline = timelines.get(namespace);
      if (timeline == null) {
        return null;
      }

      return timeline.remove(interval, version, chunk);
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  public List<TimelineObjectHolder<VersionType, ObjectType>> lookup(String namespace, Interval interval)
  {
    if (namespace == null) {
      return lookup(interval);
    }

    try {
      lock.readLock().lock();
      VersionedIntervalTimeline<VersionType, ObjectType> timeline = timelines.get(namespace);
      if (timeline != null) {
        return timeline.lookup(interval);
      } else {
        return ImmutableList.of();
      }
    }
    finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public List<TimelineObjectHolder<VersionType, ObjectType>> lookup(Interval interval)
  {
    try {
      lock.readLock().lock();

      List<TimelineObjectHolder<VersionType, ObjectType>> entries = new ArrayList<>();

      for (VersionedIntervalTimeline<VersionType, ObjectType> timeline : timelines.values()) {
        List<TimelineObjectHolder<VersionType, ObjectType>> entry = timeline.lookup(interval);
        entries.addAll(entry);
      }

      return entries;
    }
    finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public List<TimelineObjectHolder<VersionType, ObjectType>> lookupWithIncompletePartitions(Interval interval)
  {
    try {
      lock.readLock().lock();

      List<TimelineObjectHolder<VersionType, ObjectType>> entries = new ArrayList<>();

      for (VersionedIntervalTimeline<VersionType, ObjectType> timeline : timelines.values()) {
        List<TimelineObjectHolder<VersionType, ObjectType>> entry = timeline.lookupWithIncompletePartitions(interval);
        entries.addAll(entry);
      }

      return entries;
    }
    finally {
      lock.readLock().unlock();
    }
  }

  /**
   * This method should be deduplicated with DataSourcesSnapshot.determineOvershadowedSegments(): see
   * https://github.com/apache/incubator-druid/issues/8070.
   */
  public Set<TimelineObjectHolder<VersionType, ObjectType>> findFullyOvershadowed()
  {
    try {
      lock.readLock().lock();

      Set<TimelineObjectHolder<VersionType, ObjectType>> entries = new HashSet<>();

      for (VersionedIntervalTimeline<VersionType, ObjectType> timeline : timelines.values()) {
        Set<TimelineObjectHolder<VersionType, ObjectType>> entry = timeline.findFullyOvershadowed();
        entries.addAll(entry);
      }

      return entries;
    }
    finally {
      lock.readLock().unlock();
    }
  }

  public boolean isOvershadowed(String namespace, Interval interval, VersionType version, ObjectType objectType)
  {
    try {
      lock.readLock().lock();
      VersionedIntervalTimeline<VersionType, ObjectType> timeline = timelines.get(namespace);
      if (timeline == null) {
        return false;
      }
      if (timeline.isOvershadowed(interval, version, objectType)) {
        return true;
      }
      return namespace != null && isOvershadowedByParent(namespace, interval, version, objectType);
    }
    finally {
      lock.readLock().unlock();
    }
  }

  @Override
  @Nullable
  public PartitionChunk<ObjectType> findChunk(Interval interval, VersionType version, int partitionNum)
  {
    throw new UnsupportedOperationException("Non-namespaced findChunk not supported in a namespaced timeline");
  }

  @Nullable
  public PartitionChunk<ObjectType> findChunk(@Nullable String namespace, Interval interval, VersionType version, int partitionNum)
  {
    try {
      lock.readLock().lock();

      VersionedIntervalTimeline<VersionType, ObjectType> timeline = timelines.get(namespace);
      if (timeline == null) {
        return null;
      }

      return timeline.findChunk(interval, version, partitionNum);
    }
    finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns num objects (including overshadowed, see {@link #findFullyOvershadowed}) in this
   * NamespacedVersionedIntervalTimeline to be used for iteration or {@link Collection#stream()} transformation.
   * The order of objects in this collection is unspecified.
   * <p>
   * Note: iteration over the returned collection may not be as trivially cheap as, for example, iteration over an
   * ArrayList. Try (to some reasonable extent) to organize the code so that it iterates the returned collection only
   * once rather than several times.
   */
  public int getNumObjects()
  {
    List<ObjectType> allObjects = new ArrayList<>();
    for (VersionedIntervalTimeline<VersionType, ObjectType> versionedIntervalTimeline : timelines.values()) {
      allObjects.addAll(versionedIntervalTimeline.iterateAllObjects());
    }
    return allObjects.size();
  }

  private boolean isOvershadowedByParent(
            String namespace,
            Interval interval,
            VersionType version,
            ObjectType objectType)
  {
    OverShadowConfig overShadowConfig = OverShadowConfigProvider.get();
    if (overShadowConfig == null) {
      return false;
    }
    Map<Pattern, String> namespaceChildParentMap = overShadowConfig.getNamespaceChildParentMap();
    for (Map.Entry<Pattern, String> e : namespaceChildParentMap.entrySet()) {
      if (e.getKey().matcher(namespace).matches()) {
        String parentNamespace = e.getValue();
        if (!parentNamespace.equals(namespace) && timelines.containsKey(parentNamespace) && timelines.get(
                parentNamespace).isOvershadowed(interval, version, objectType)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Computes a set with all objects falling within the specified interval which are at least partially "visible" in
   * this interval (that is, are not fully overshadowed within this interval).
   *
   * Note that this method returns a set of {@link ObjectType}. Duplicate objects in different time chunks will be
   * removed in the result.
   */
  public Set<ObjectType> findNonOvershadowedObjectsInInterval(Interval interval, Partitions completeness)
  {
    final List<TimelineObjectHolder<VersionType, ObjectType>> holders;

    lock.readLock().lock();
    try {
      holders = lookup(interval); //the lookup method uses Partitions.ONLY_COMPLETE
    }
    finally {
      lock.readLock().unlock();
    }

    return FluentIterable
            .from(holders)
            .transformAndConcat(TimelineObjectHolder::getObject)
            .transform(PartitionChunk::getObject)
            .toSet();
  }
}
