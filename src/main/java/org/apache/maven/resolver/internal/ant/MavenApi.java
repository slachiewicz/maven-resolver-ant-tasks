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
package org.apache.maven.resolver.internal.ant;

import org.apache.maven.api.Session;
import org.apache.maven.impl.standalone.ApiRunner;

/**
 * Access to the Maven 4 API outside of a Maven runtime. The API's {@link Session} is normally provided by Maven; in
 * Ant there is no Maven, so the only way to obtain one is {@link ApiRunner}, which is not part of the public API
 * ({@code maven-impl}).
 */
public final class MavenApi {

    private static Session shared;

    private MavenApi() {}

    /**
     * A session for the stateless services (settings building, XML writing). Created on first use and shared, because
     * bootstrapping the standalone injector is expensive. Never use it for model building: a session caches the
     * results of its requests.
     */
    public static synchronized Session shared() {
        if (shared == null) {
            shared = ApiRunner.createSession();
        }
        return shared;
    }

    /**
     * A new session for one model build, whose parent and import POMs are resolved by {@link AntModelResolver} rather
     * than by the standalone session's own repository system (which has no transporter and knows nothing about the
     * Ant project's mirrors, proxies and credentials).
     */
    static Session newModelSession(AntModelResolver.Context context) {
        Session session = ApiRunner.createSession(injector -> injector.bindImplicit(AntModelResolver.class));
        session.getData().set(AntModelResolver.CONTEXT, context);
        return session;
    }
}
