/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehcache.clustered.management;

import org.ehcache.Cache;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.terracotta.management.model.call.ContextualReturn;
import org.terracotta.management.model.capabilities.Capability;
import org.terracotta.management.model.capabilities.descriptors.Descriptor;
import org.terracotta.management.model.capabilities.descriptors.Settings;
import org.terracotta.management.model.capabilities.descriptors.StatisticDescriptor;
import org.terracotta.management.model.cluster.Cluster;
import org.terracotta.management.model.context.ContextContainer;
import org.terracotta.management.model.message.Message;
import org.terracotta.management.model.stats.ContextualStatistics;
import org.terracotta.management.model.stats.Sample;
import org.terracotta.management.model.stats.StatisticType;
import org.terracotta.management.model.stats.history.CounterHistory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.ehcache.clustered.client.config.builders.ClusteredResourcePoolBuilder.clusteredDedicated;
import static org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder;
import static org.ehcache.config.builders.ResourcePoolsBuilder.newResourcePoolsBuilder;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertThat;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClusteringManagementServiceTest extends AbstractClusteringManagementTest {

  private static final Collection<StatisticDescriptor> ONHEAP_DESCRIPTORS = new ArrayList<>();
  private static final Collection<StatisticDescriptor> OFFHEAP_DESCRIPTORS = new ArrayList<>();
  private static final Collection<StatisticDescriptor> DISK_DESCRIPTORS =  new ArrayList<>();
  private static final Collection<StatisticDescriptor> CLUSTERED_DESCRIPTORS =  new ArrayList<>();
  private static final Collection<StatisticDescriptor> CACHE_DESCRIPTORS = new ArrayList<>();
  private static final Collection<StatisticDescriptor> POOL_DESCRIPTORS = new ArrayList<>();
  private static final Collection<StatisticDescriptor> SERVER_STORE_DESCRIPTORS = new ArrayList<>();

  @Test
  @Ignore("This is not a test, but something useful to show a json print of a cluster topology with all management metadata inside")
  public void test_A_topology() throws Exception {
    Cluster cluster = consumer.readTopology();
    String json = mapper.writeValueAsString(cluster.toMap());
    System.out.println(json);
  }

  @Test
  public void test_A_client_tags_exposed() throws Exception {
    String[] tags = consumer.readTopology().getClient(clientIdentifier).get().getTags().toArray(new String[0]);
    assertThat(tags, equalTo(new String[]{"server-node-1", "webapp-1"}));
  }

  @Test
  public void test_B_client_contextContainer_exposed() throws Exception {
    ContextContainer contextContainer = consumer.readTopology().getClient(clientIdentifier).get().getManagementRegistry().get().getContextContainer();
    assertThat(contextContainer.getValue(), equalTo("my-super-cache-manager"));
    Collection<ContextContainer> subContexts = contextContainer.getSubContexts();
    TreeSet<String> cacheNames = subContexts.stream().map(ContextContainer::getValue).collect(Collectors.toCollection(TreeSet::new));
    assertThat(subContexts, hasSize(3));
    assertThat(cacheNames, hasSize(3));
    assertThat(cacheNames, equalTo(new TreeSet<>(Arrays.asList("dedicated-cache-1", "shared-cache-2", "shared-cache-3"))));
  }

  @Test
  public void test_C_client_capabilities_exposed() throws Exception {
    Capability[] capabilities = consumer.readTopology().getClient(clientIdentifier).get().getManagementRegistry().get().getCapabilities().toArray(new Capability[0]);
    assertThat(capabilities.length, equalTo(5));
    assertThat(capabilities[0].getName(), equalTo("ActionsCapability"));
    assertThat(capabilities[1].getName(), equalTo("StatisticsCapability"));
    assertThat(capabilities[2].getName(), equalTo("StatisticCollectorCapability"));
    assertThat(capabilities[3].getName(), equalTo("SettingsCapability"));
    assertThat(capabilities[4].getName(), equalTo("ManagementAgentService"));
    assertThat(capabilities[0].getDescriptors(), hasSize(4));

    Collection<Descriptor> descriptors = capabilities[1].getDescriptors();
    Collection<Descriptor> allDescriptors = new ArrayList<>();
    allDescriptors.addAll(CACHE_DESCRIPTORS);
    allDescriptors.addAll(ONHEAP_DESCRIPTORS);
    allDescriptors.addAll(OFFHEAP_DESCRIPTORS);
    allDescriptors.addAll(CLUSTERED_DESCRIPTORS);

    assertThat(descriptors, containsInAnyOrder(allDescriptors.toArray()));
    assertThat(descriptors, hasSize(allDescriptors.size()));
  }

  @Test
  public void test_D_server_capabilities_exposed() throws Exception {
    Capability[] capabilities = consumer.readTopology().getSingleStripe().getActiveServerEntity(serverEntityIdentifier).get().getManagementRegistry().get().getCapabilities().toArray(new Capability[0]);

    assertThat(capabilities.length, equalTo(7));

    assertThat(capabilities[0].getName(), equalTo("ClientStateSettings"));
    assertThat(capabilities[1].getName(), equalTo("OffHeapResourceSettings"));
    assertThat(capabilities[2].getName(), equalTo("ServerStoreSettings"));
    assertThat(capabilities[3].getName(), equalTo("PoolSettings"));
    assertThat(capabilities[4].getName(), equalTo("ServerStoreStatistics"));
    assertThat(capabilities[5].getName(), equalTo("PoolStatistics"));
    assertThat(capabilities[6].getName(), equalTo("StatisticCollector"));

    assertThat(capabilities[1].getDescriptors(), hasSize(3)); // time + 2 resources
    assertThat(capabilities[2].getDescriptors(), hasSize(4)); // time descriptor + 3 dedicated store

    // stats

    assertThat(capabilities[4].getDescriptors(), containsInAnyOrder(SERVER_STORE_DESCRIPTORS.toArray()));
    assertThat(capabilities[4].getDescriptors(), hasSize(SERVER_STORE_DESCRIPTORS.size()));
    assertThat(capabilities[5].getDescriptors(), containsInAnyOrder(POOL_DESCRIPTORS.toArray()));
    assertThat(capabilities[5].getDescriptors(), hasSize(POOL_DESCRIPTORS.size()));

    // ClientStateSettings

    assertThat(capabilities[0].getDescriptors(), hasSize(1));
    Settings settings = (Settings) capabilities[0].getDescriptors().iterator().next();
    assertThat(settings.get("attachedStores"), equalTo(new String[]{"dedicated-cache-1", "shared-cache-2", "shared-cache-3"}));

    // EhcacheStateServiceSettings

    List<Descriptor> descriptors = new ArrayList<>(capabilities[3].getDescriptors());
    assertThat(descriptors, hasSize(4));

    settings = (Settings) descriptors.get(0);
    assertThat(settings.get("alias"), equalTo("resource-pool-b"));
    assertThat(settings.get("type"), equalTo("PoolBinding"));
    assertThat(settings.get("serverResource"), equalTo("primary-server-resource"));
    assertThat(settings.get("size"), equalTo(16 * 1024 * 1024L));
    assertThat(settings.get("allocationType"), equalTo("shared"));

    settings = (Settings) descriptors.get(1);
    assertThat(settings.get("alias"), equalTo("resource-pool-a"));
    assertThat(settings.get("type"), equalTo("PoolBinding"));
    assertThat(settings.get("serverResource"), equalTo("secondary-server-resource"));
    assertThat(settings.get("size"), equalTo(28 * 1024 * 1024L));
    assertThat(settings.get("allocationType"), equalTo("shared"));

    settings = (Settings) descriptors.get(2);
    assertThat(settings.get("alias"), equalTo("dedicated-cache-1"));
    assertThat(settings.get("type"), equalTo("PoolBinding"));
    assertThat(settings.get("serverResource"), equalTo("primary-server-resource"));
    assertThat(settings.get("size"), equalTo(4 * 1024 * 1024L));
    assertThat(settings.get("allocationType"), equalTo("dedicated"));

    settings = (Settings) descriptors.get(3);
    assertThat(settings.get("type"), equalTo("PoolSettingsManagementProvider"));
    assertThat(settings.get("defaultServerResource"), equalTo("primary-server-resource"));
  }

  @Test
  public void test_E_notifs_on_add_cache() throws Exception {
    cacheManager.createCache("cache-2", newCacheConfigurationBuilder(
      String.class, String.class,
      newResourcePoolsBuilder()
        .heap(10, EntryUnit.ENTRIES)
        .offheap(1, MemoryUnit.MB)
        .with(clusteredDedicated("primary-server-resource", 2, MemoryUnit.MB)))
      .build());

    ContextContainer contextContainer = consumer.readTopology().getClient(clientIdentifier).get().getManagementRegistry().get().getContextContainer();
    assertThat(contextContainer.getSubContexts(), hasSize(4));

    TreeSet<String> cNames = contextContainer.getSubContexts().stream().map(ContextContainer::getValue).collect(Collectors.toCollection(TreeSet::new));
    assertThat(cNames, equalTo(new TreeSet<>(Arrays.asList("cache-2", "dedicated-cache-1", "shared-cache-2", "shared-cache-3"))));

    List<Message> messages = consumer.drainMessageBuffer();
    assertThat(notificationTypes(messages),  equalTo(Arrays.asList(
      "ENTITY_REGISTRY_UPDATED", "EHCACHE_SERVER_STORE_CREATED", "ENTITY_REGISTRY_UPDATED",
      "CLIENT_REGISTRY_UPDATED", "CACHE_ADDED")));
    assertThat(consumer.readMessageBuffer(), is(nullValue()));
  }

  @Test
  public void test_F_notifs_on_remove_cache() throws Exception {
    cacheManager.removeCache("cache-2");

    List<Message> messages = consumer.drainMessageBuffer();
    assertThat(notificationTypes(messages),  equalTo(Arrays.asList("CLIENT_REGISTRY_UPDATED", "CACHE_REMOVED", "ENTITY_REGISTRY_UPDATED")));
    assertThat(consumer.readMessageBuffer(), is(nullValue()));
  }

  @Test
  public void test_G_stats_collection() throws Exception {

    ContextualReturn<?> contextualReturn = sendManagementCallToCollectStats("Cache:HitCount");
    assertThat(contextualReturn.hasExecuted(), is(true));

    Cache<String, String> cache1 = cacheManager.getCache("dedicated-cache-1", String.class, String.class);
    cache1.put("key1", "val");
    cache1.put("key2", "val");

    cache1.get("key1");
    cache1.get("key2");

    List<ContextualStatistics> allStats = new ArrayList<>();
    long val = 0;

    // it could be several seconds before the sampled stats could become available
    // let's try until we find the correct value : 2
    do {

      // get the stats (we are getting the primitive counter, not the sample history)
      List<ContextualStatistics> stats = waitForNextStats();
      allStats.addAll(stats);

      for (ContextualStatistics stat : stats) {
        if (stat.getContext().get("cacheName").equals("dedicated-cache-1")) {
          Sample<Long>[] samples = stat.getStatistic(CounterHistory.class, "Cache:HitCount").getValue();
          if(samples.length > 0) {
            val = samples[samples.length - 1].getValue();
          }
        }
      }
    } while(val != 2);

    // do some other operations
    cache1.get("key1");
    cache1.get("key2");

    do {

      List<ContextualStatistics> stats = waitForNextStats();
      allStats.addAll(stats);

      for (ContextualStatistics stat : stats) {
        if (stat.getContext().get("cacheName").equals("dedicated-cache-1")) {
          Sample<Long>[] samples = stat.getStatistic(CounterHistory.class, "Cache:HitCount").getValue();
          if(samples.length > 0) {
            val = samples[samples.length - 1].getValue();
          }
        }
      }

    } while(val != 4);

    // wait until we have some stats coming from the server entity
    while (!allStats.stream().filter(statistics -> statistics.getContext().contains("consumerId")).findFirst().isPresent()) {
      allStats.addAll(waitForNextStats());
    }
    List<ContextualStatistics> serverStats = allStats.stream().filter(statistics -> statistics.getContext().contains("consumerId")).collect(Collectors.toList());

    // server-side stats

    assertThat(
      serverStats.stream()
        .map(ContextualStatistics::getCapability)
        .collect(Collectors.toSet()),
      equalTo(new HashSet<>(Arrays.asList("PoolStatistics", "ServerStoreStatistics"))));

    // ensure we collect stats from all registered objects (pools and stores)

    assertThat(
      serverStats.stream()
        .filter(statistics -> statistics.getCapability().equals("PoolStatistics"))
        .map(statistics -> statistics.getContext().get("alias"))
        .collect(Collectors.toSet()),
      equalTo(new HashSet<>(Arrays.asList("resource-pool-b", "resource-pool-a", "dedicated-cache-1", "cache-2"))));

    assertThat(
      serverStats.stream()
        .filter(statistics -> statistics.getCapability().equals("ServerStoreStatistics"))
        .map(statistics -> statistics.getContext().get("alias"))
        .collect(Collectors.toSet()),
      equalTo(new HashSet<>(Arrays.asList("shared-cache-3", "shared-cache-2", "dedicated-cache-1", "cache-2"))));

    // ensure we collect all the stat names

    assertThat(
      serverStats.stream()
        .filter(statistics -> statistics.getCapability().equals("PoolStatistics"))
        .flatMap(statistics -> statistics.getStatistics().keySet().stream())
        .collect(Collectors.toSet()),
      equalTo(POOL_DESCRIPTORS.stream().map(StatisticDescriptor::getName).collect(Collectors.toSet())));

    assertThat(
      serverStats.stream()
        .filter(statistics -> statistics.getCapability().equals("ServerStoreStatistics"))
        .flatMap(statistics -> statistics.getStatistics().keySet().stream())
        .collect(Collectors.toSet()),
      equalTo(SERVER_STORE_DESCRIPTORS.stream().map(StatisticDescriptor::getName).collect(Collectors.toSet())));
  }

  @BeforeClass
  public static void initDescriptors() throws ClassNotFoundException {
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:MissLatencyMinimum" , StatisticType.DURATION_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:EvictionLatencyMinimum" , StatisticType.DURATION_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:EvictionCount" , StatisticType.COUNTER_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:MissCount" , StatisticType.COUNTER_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:MissLatencyMaximum" , StatisticType.DURATION_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:EvictionRate" , StatisticType.RATE_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:HitRatioRatio" , StatisticType.RATIO_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:MissRatioRatio" , StatisticType.RATIO_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:MappingCount" , StatisticType.COUNTER_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:HitLatencyAverage" , StatisticType.AVERAGE_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:HitLatencyMinimum" , StatisticType.DURATION_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:OccupiedByteSize", StatisticType.SIZE_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:MissRate" , StatisticType.RATE_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:EvictionLatencyAverage" , StatisticType.AVERAGE_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:HitCount" , StatisticType.COUNTER_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:HitRate" , StatisticType.RATE_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:MissLatencyAverage" , StatisticType.AVERAGE_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:EvictionLatencyMaximum" , StatisticType.DURATION_HISTORY));
    ONHEAP_DESCRIPTORS.add(new StatisticDescriptor("OnHeap:HitLatencyMaximum" , StatisticType.DURATION_HISTORY));

    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:MissCount", StatisticType.COUNTER_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:EvictionRate", StatisticType.RATE_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:MissRate", StatisticType.RATE_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:HitLatencyMinimum", StatisticType.DURATION_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:HitLatencyAverage", StatisticType.AVERAGE_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:OccupiedByteSize", StatisticType.SIZE_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:EvictionLatencyAverage", StatisticType.AVERAGE_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:HitLatencyMaximum", StatisticType.DURATION_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:AllocatedByteSize", StatisticType.SIZE_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:EvictionLatencyMaximum", StatisticType.DURATION_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:MappingCount", StatisticType.COUNTER_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:HitRate", StatisticType.RATE_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:HitRatioRatio", StatisticType.RATIO_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:MissRatioRatio", StatisticType.RATIO_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:EvictionLatencyMinimum", StatisticType.DURATION_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:MissLatencyMinimum", StatisticType.DURATION_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:EvictionCount", StatisticType.COUNTER_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:MissLatencyMaximum", StatisticType.DURATION_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:MaxMappingCount", StatisticType.COUNTER_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:HitCount", StatisticType.COUNTER_HISTORY));
    OFFHEAP_DESCRIPTORS.add(new StatisticDescriptor("OffHeap:MissLatencyAverage", StatisticType.AVERAGE_HISTORY));

    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:MissLatencyMaximum", StatisticType.DURATION_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:MissLatencyAverage", StatisticType.AVERAGE_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:HitLatencyMinimum", StatisticType.DURATION_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:MaxMappingCount", StatisticType.COUNTER_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:HitRate", StatisticType.RATE_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:OccupiedByteSize", StatisticType.SIZE_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:HitLatencyAverage", StatisticType.AVERAGE_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:EvictionLatencyAverage", StatisticType.AVERAGE_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:EvictionLatencyMinimum", StatisticType.DURATION_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:EvictionRate", StatisticType.RATE_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:EvictionLatencyMaximum", StatisticType.DURATION_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:AllocatedByteSize", StatisticType.SIZE_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:HitLatencyMaximum", StatisticType.DURATION_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:HitCount", StatisticType.COUNTER_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:MissLatencyMinimum", StatisticType.DURATION_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:EvictionCount", StatisticType.COUNTER_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:HitRatioRatio", StatisticType.RATIO_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:MissRatioRatio", StatisticType.RATIO_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:MissCount", StatisticType.COUNTER_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:MappingCount", StatisticType.COUNTER_HISTORY));
    DISK_DESCRIPTORS.add(new StatisticDescriptor("Disk:MissRate", StatisticType.RATE_HISTORY));

    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:MissCount", StatisticType.COUNTER_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:EvictionLatencyMinimum", StatisticType.DURATION_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:HitCount", StatisticType.COUNTER_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:MissLatencyMaximum", StatisticType.DURATION_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:MaxMappingCount", StatisticType.COUNTER_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:HitRate", StatisticType.RATE_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:EvictionLatencyAverage", StatisticType.AVERAGE_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:EvictionCount", StatisticType.COUNTER_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:HitLatencyAverage", StatisticType.AVERAGE_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:HitLatencyMaximum", StatisticType.DURATION_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:MissRate", StatisticType.RATE_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:OccupiedByteSize", StatisticType.SIZE_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:MissLatencyMinimum", StatisticType.DURATION_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:EvictionLatencyMaximum", StatisticType.DURATION_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:MissLatencyAverage", StatisticType.AVERAGE_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:HitRatioRatio", StatisticType.RATIO_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:MissRatioRatio", StatisticType.RATIO_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:AllocatedByteSize", StatisticType.SIZE_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:MappingCount", StatisticType.COUNTER_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:EvictionRate", StatisticType.RATE_HISTORY));
    CLUSTERED_DESCRIPTORS.add(new StatisticDescriptor("Clustered:HitLatencyMinimum", StatisticType.DURATION_HISTORY));

    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:MissLatencyMaximum", StatisticType.DURATION_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:HitRate", StatisticType.RATE_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:HitLatencyMinimum", StatisticType.DURATION_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:HitCount", StatisticType.COUNTER_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:HitRatioRatio", StatisticType.RATIO_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:MissLatencyMinimum", StatisticType.DURATION_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:ClearLatencyAverage", StatisticType.AVERAGE_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:HitLatencyMaximum", StatisticType.DURATION_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:ClearRate", StatisticType.RATE_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:MissLatencyAverage", StatisticType.AVERAGE_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:HitLatencyAverage", StatisticType.AVERAGE_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:ClearLatencyMaximum", StatisticType.DURATION_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:MissRate", StatisticType.RATE_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:ClearCount", StatisticType.COUNTER_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:ClearLatencyMinimum", StatisticType.DURATION_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:MissCount", StatisticType.COUNTER_HISTORY));
    CACHE_DESCRIPTORS.add(new StatisticDescriptor("Cache:MissRatioRatio", StatisticType.RATIO_HISTORY));

    POOL_DESCRIPTORS.add(new StatisticDescriptor("Pool:AllocatedSize", StatisticType.SIZE_HISTORY));

    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:AllocatedMemory", StatisticType.SIZE_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:DataAllocatedMemory", StatisticType.SIZE_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:OccupiedMemory", StatisticType.SIZE_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:DataOccupiedMemory", StatisticType.SIZE_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:Entries", StatisticType.COUNTER_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:UsedSlotCount", StatisticType.COUNTER_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:DataVitalMemory", StatisticType.SIZE_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:VitalMemory", StatisticType.SIZE_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:ReprobeLength", StatisticType.SIZE_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:RemovedSlotCount", StatisticType.COUNTER_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:DataSize", StatisticType.SIZE_HISTORY));
    SERVER_STORE_DESCRIPTORS.add(new StatisticDescriptor("Store:TableCapacity", StatisticType.SIZE_HISTORY));
  }

}
