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

package org.ehcache.internal.tier;

import org.ehcache.exceptions.CacheAccessException;
import org.ehcache.expiry.Expirations;
import org.ehcache.function.Function;
import org.ehcache.spi.cache.Store;
import org.ehcache.spi.cache.tiering.CachingTier;
import org.ehcache.spi.test.After;
import org.ehcache.spi.test.Before;
import org.ehcache.spi.test.LegalSPITesterException;
import org.ehcache.spi.test.SPITest;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test the {@link CachingTier#clear()} contract of the
 * {@link CachingTier CachingTier} interface.
 * <p/>
 *
 * @author Aurelien Broszniowski
 */
public class CachingTierClear<K, V> extends CachingTierTester<K, V> {

  private CachingTier tier;

  public CachingTierClear(final CachingTierFactory<K, V> factory) {
    super(factory);
  }

  @Before
  public void setUp() {
  }

  @After
  public void tearDown() {
    if (tier != null) {
      factory.disposeOf(tier);
      tier = null;
    }
  }

  @SPITest
  @SuppressWarnings("unchecked")
  public void removeMapping() throws LegalSPITesterException {
    long nbMappings = 10;

    tier = factory.newCachingTier();

    V originalValue= factory.createValue(1);
    V newValue= factory.createValue(2);

    final Store.ValueHolder<V> originalValueHolder = mock(Store.ValueHolder.class);
    when(originalValueHolder.value()).thenReturn(originalValue);

    try {
      List<K> keys = new ArrayList<K>();
      for (int i = 0; i < nbMappings; i++) {
        K key = factory.createKey(i);

        tier.getOrComputeIfAbsent(key, new Function<K, Store.ValueHolder<V>>() {
          @Override
          public Store.ValueHolder<V> apply(final K k) {
            return originalValueHolder;
          }
        });
        keys.add(key);
      }

      tier.clear();

      final Store.ValueHolder<V> newValueHolder = mock(Store.ValueHolder.class);
      when(newValueHolder.value()).thenReturn(newValue);

      for (K key : keys) {
        tier.invalidate(key);
        Store.ValueHolder<V> newReturnedValueHolder = tier.getOrComputeIfAbsent(key, new Function() {
          @Override
          public Object apply(final Object o) {
            return newValueHolder;
          }
        });

        assertThat(newReturnedValueHolder.value(), is(equalTo(newValueHolder.value())));
      }
    } catch (CacheAccessException e) {
      throw new LegalSPITesterException("Warning, an exception is thrown due to the SPI test");
    }
  }
}
