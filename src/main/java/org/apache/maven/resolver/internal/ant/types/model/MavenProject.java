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
package org.apache.maven.resolver.internal.ant.types.model;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.api.model.Developer;
import org.apache.maven.api.model.Exclusion;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Repository;
import org.apache.maven.api.model.Scm;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.services.xml.XmlWriterException;
import org.apache.maven.resolver.internal.ant.MavenApi;
import org.apache.maven.resolver.internal.ant.types.Dependency;
import org.apache.tools.ant.types.DataType;

/**
 * Represents a Maven project model.
 * This class encapsulates the Maven POM model and provides methods to manipulate it,
 * such as adding licenses, dependencies, and setting project properties.
 */
public class MavenProject extends DataType {
    private final Model.Builder model;

    private final List<org.apache.maven.api.model.License> licenses = new ArrayList<>();

    private final List<org.apache.maven.api.model.Dependency> dependencies = new ArrayList<>();

    private final List<org.apache.maven.api.model.Dependency> managedDependencies = new ArrayList<>();

    private final List<Repository> repositories = new ArrayList<>();

    private final List<Developer> developers = new ArrayList<>();

    private Scm scm;

    /**
     * Initialize a new Maven project instance with the modelVersion set to "4.0.0" and packaging to "jar".
     */
    public MavenProject() {
        model = Model.newBuilder().modelVersion("4.0.0").packaging("jar");
    }

    /**
     * Adds a license to the Maven project model.
     *
     * @param license the License object to add, must not be null
     */
    public void addLicense(License license) {
        licenses.add(org.apache.maven.api.model.License.newBuilder()
                .name(license.getName())
                .url(license.getUrl())
                .comments(license.getComments())
                .distribution(license.getDistribution())
                .build());
    }

    /**
     * Set the groupId for the Maven project model.
     *
     * @param groupId the groupId to set, must not be null or empty
     */
    public void setGroupId(String groupId) {
        model.groupId(groupId);
    }

    /**
     * Set the artifactId for the Maven project model.
     *
     * @param artifactId the artifactId to set, must not be null or empty
     */
    public void setArtifactId(String artifactId) {
        model.artifactId(artifactId);
    }

    /**
     * Set the version for the Maven project model.
     *
     * @param version the version to set, must not be null or empty
     */
    public void setVersion(String version) {
        model.version(version);
    }

    /**
     * Set the name for the Maven project model.
     *
     * @param name the name to set, can be null or empty
     */
    public void setName(String name) {
        model.name(name);
    }

    /**
     * Set the description for the Maven project model.
     *
     * @param description the description to set, can be null or empty
     */
    public void setDescription(String description) {
        model.description(description);
    }

    /**
     * Add a dependency to the dependencies section of the Maven project model.
     * @param dependency the Dependency object to add, must not be null
     */
    public void addDependency(Dependency dependency) {
        dependencies.add(convert(dependency));
    }

    private org.apache.maven.api.model.Dependency convert(Dependency dep) {
        org.apache.maven.api.model.Dependency.Builder dependency = org.apache.maven.api.model.Dependency.newBuilder()
                .groupId(dep.getGroupId())
                .artifactId(dep.getArtifactId());
        if (dep.getVersion() != null) {
            dependency.version(dep.getVersion());
        }
        if (dep.getType() != null) {
            dependency.type(dep.getType());
        }
        if (dep.getClassifier() != null) {
            dependency.classifier(dep.getClassifier());
        }
        if (dep.getScope() != null && !dep.getScope().trim().isEmpty()) {
            dependency.scope(dep.getScope());
        }

        if (dep.getExclusions() != null && !dep.getExclusions().isEmpty()) {
            List<Exclusion> exclusions = new ArrayList<>();
            dep.getExclusions()
                    .forEach(exclusion -> exclusions.add(Exclusion.newBuilder()
                            .groupId(exclusion.getGroupId())
                            .artifactId(exclusion.getArtifactId())
                            .build()));
            dependency.exclusions(exclusions);
        }
        return dependency.build();
    }

    /**
     * Adds a dependency to the dependency management section of the Maven project model.
     *
     * @param dep the Dependency object to add, must not be null
     */
    public void addToDependencyManagement(Dependency dep) {
        managedDependencies.add(convert(dep));
    }

    /**
     * Adds a repository to the Maven project model.
     *
     * @param repository the repository to add, must not be null
     */
    public void addRepository(Repository repository) {
        repositories.add(repository);
    }

    /**
     * Adds a developer to the Maven project model.
     *
     * @param developer the developer to add, must not be null
     */
    public void addDeveloper(Developer developer) {
        developers.add(developer);
    }

    /**
     * Sets the SCM section of the Maven project model.
     *
     * @param scm the SCM section, must not be null
     */
    public void setScm(Scm scm) {
        this.scm = scm;
    }

    /**
     * Gets the Maven project model as configured so far. The v4 model is immutable, so this is a snapshot: later
     * changes to this project are not reflected in a model returned earlier.
     *
     * @return the Model object representing the Maven project, never null
     */
    public Model getModel() {
        Model.Builder builder = Model.newBuilder(model.build(), true);
        builder.licenses(licenses).dependencies(dependencies).repositories(repositories);
        builder.developers(developers);
        if (!managedDependencies.isEmpty()) {
            builder.dependencyManagement(org.apache.maven.api.model.DependencyManagement.newBuilder()
                    .dependencies(managedDependencies)
                    .build());
        }
        if (scm != null) {
            builder.scm(scm);
        }
        return builder.build();
    }

    /**
     * Writes the Maven project model to the specified Writer in POM format.
     *
     * @param pomStream the OutputStream to write the POM to, must not be null
     * @throws IOException if an I/O error occurs while writing
     */
    public void toPom(OutputStream pomStream) throws IOException {
        try {
            MavenApi.shared().getService(ModelXmlFactory.class).write(getModel(), pomStream);
        } catch (XmlWriterException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
