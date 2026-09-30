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

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.maven.api.Session;
import org.apache.maven.api.SessionData;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.Parent;
import org.apache.maven.api.services.ModelSource;
import org.apache.maven.api.services.Sources;
import org.apache.maven.api.services.model.ModelResolver;
import org.apache.maven.api.services.model.ModelResolverException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

/**
 * A model resolver to assist building of dependency POMs. This resolver gives priority to those repositories that have
 * been initially specified and repositories discovered in dependency POMs are recessively merged into the search chain.
 * <p>
 * It replaces the standalone session's own resolver (priority) and resolves through the Ant project's
 * {@link RepositorySystem}. The Ant-side state reaches it through the session's data, see {@link #CONTEXT}.
 * </p>
 */
@Named
@Singleton
@Priority(1000)
public class AntModelResolver implements ModelResolver {

    static final SessionData.Key<Context> CONTEXT = SessionData.key(Context.class);

    /**
     * The Ant-side state of one model build.
     */
    static final class Context {

        private final RepositorySystemSession session;

        private final String context;

        private final RepositorySystem repoSys;

        private final RemoteRepositoryManager remoteRepositoryManager;

        private List<RemoteRepository> repositories;

        private final Set<String> repositoryIds = new HashSet<>();

        Context(
                RepositorySystemSession session,
                String context,
                RepositorySystem repoSys,
                RemoteRepositoryManager remoteRepositoryManager,
                List<RemoteRepository> repositories) {
            this.session = session;
            this.context = context;
            this.repoSys = repoSys;
            this.remoteRepositoryManager = remoteRepositoryManager;
            this.repositories = repositories;
            for (RemoteRepository repository : repositories) {
                repositoryIds.add(repository.getId());
            }
        }

        /**
         * Merges the repositories the model builder knows about, which include those declared by the POMs read so far.
         * The v4 API exposes only id, URL and protocol of a repository, not its policies, so a discovered repository
         * gets the default policies for both releases and snapshots.
         */
        synchronized void addRepositories(List<org.apache.maven.api.RemoteRepository> discovered) {
            List<RemoteRepository> added = new ArrayList<>();
            for (org.apache.maven.api.RemoteRepository repository : discovered) {
                if (repositoryIds.add(repository.getId())) {
                    added.add(convert(repository));
                }
            }
            if (!added.isEmpty()) {
                repositories = remoteRepositoryManager.aggregateRepositories(session, repositories, added, true);
            }
        }

        synchronized Artifact resolve(String groupId, String artifactId, String version)
                throws ArtifactResolutionException {
            Artifact pomArtifact = new DefaultArtifact(groupId, artifactId, "", "pom", version);
            ArtifactRequest request = new ArtifactRequest(pomArtifact, repositories, context);
            return repoSys.resolveArtifact(session, request).getArtifact();
        }
    }

    static RemoteRepository convert(org.apache.maven.api.RemoteRepository repository) {
        RemoteRepository.Builder builder =
                new RemoteRepository.Builder(repository.getId(), repository.getProtocol(), repository.getUrl());
        builder.setSnapshotPolicy(new RepositoryPolicy());
        builder.setReleasePolicy(new RepositoryPolicy());
        return builder.build();
    }

    private static Context context(Session session) {
        Context context = session.getData().get(CONTEXT);
        if (context == null) {
            throw new IllegalStateException("No Ant repository context in the session");
        }
        return context;
    }

    @Override
    public ModelResolverResult resolveModel(ModelResolverRequest request) throws ModelResolverException {
        Context context = context(request.getSession());
        context.addRepositories(request.repositories());
        try {
            Artifact pomArtifact = context.resolve(request.groupId(), request.artifactId(), request.version());
            File pomFile = pomArtifact.getPath().toFile();
            return new ModelResolverResult(request, Sources.resolvedSource(pomFile.toPath(), null), request.version());
        } catch (ArtifactResolutionException e) {
            throw new ModelResolverException(
                    "Failed to resolve POM for " + request.groupId() + ":" + request.artifactId() + ":"
                            + request.version() + " due to " + e.getMessage(),
                    request.groupId(),
                    request.artifactId(),
                    request.version(),
                    e);
        }
    }

    @Override
    public ModelSource resolveModel(
            Session session,
            List<org.apache.maven.api.RemoteRepository> repositories,
            Parent parent,
            AtomicReference<Parent> modified)
            throws ModelResolverException {
        return resolveModel(new ModelResolverRequest(
                        session,
                        null,
                        repositories,
                        parent.getGroupId(),
                        parent.getArtifactId(),
                        parent.getVersion(),
                        null))
                .source();
    }

    @Override
    public ModelSource resolveModel(
            Session session,
            List<org.apache.maven.api.RemoteRepository> repositories,
            Dependency dependency,
            AtomicReference<Dependency> modified)
            throws ModelResolverException {
        return resolveModel(new ModelResolverRequest(
                        session,
                        null,
                        repositories,
                        dependency.getGroupId(),
                        dependency.getArtifactId(),
                        dependency.getVersion(),
                        null))
                .source();
    }
}
