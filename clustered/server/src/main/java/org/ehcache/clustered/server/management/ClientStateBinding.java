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
package org.ehcache.clustered.server.management;

import org.ehcache.clustered.server.ClientState;
import org.terracotta.entity.ClientDescriptor;
import org.terracotta.management.service.registry.provider.ClientBinding;

final class ClientStateBinding extends ClientBinding {

  ClientStateBinding(ClientDescriptor clientDescriptor, ClientState clientState) {
    super(clientDescriptor, clientState);
  }

  @Override
  public ClientState getValue() {
    return (ClientState) super.getValue();
  }

}
