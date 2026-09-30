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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.model.Model;
import org.apache.maven.resolver.internal.ant.types.RemoteRepositories;
import org.apache.maven.resolver.internal.ant.types.RemoteRepository;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Model building goes through the Maven 4 API, whose standalone session has no transporter and knows nothing about the
 * Ant project. The parent POM here exists only in a file repository handed to the task, so it can be found solely if
 * the model builder uses {@link AntModelResolver} and the Ant repository system.
 */
class AntModelResolverTest {
    @TempDir
    Path tmp;

    private Project project;

    private Task task;

    private File pom;

    private RemoteRepositories repositories;

    @BeforeEach
    void setUp() throws Exception {
        Path remote = Files.createDirectory(tmp.resolve("remote"));
        Path parentDir = remote.resolve("test/ant/parent-pom/1");
        Files.createDirectories(parentDir);
        write(
                parentDir.resolve("parent-pom-1.pom"),
                "<project><modelVersion>4.0.0</modelVersion>"
                        + "<groupId>test.ant</groupId><artifactId>parent-pom</artifactId><version>1</version>"
                        + "<packaging>pom</packaging>"
                        + "<properties><inherited>from-parent</inherited></properties>"
                        + "<dependencyManagement><dependencies><dependency>"
                        + "<groupId>g</groupId><artifactId>a</artifactId><version>2</version>"
                        + "</dependency></dependencies></dependencyManagement></project>");

        pom = tmp.resolve("pom.xml").toFile();
        write(
                pom.toPath(),
                "<project><modelVersion>4.0.0</modelVersion>"
                        + "<parent><groupId>test.ant</groupId><artifactId>parent-pom</artifactId><version>1</version>"
                        + "</parent><artifactId>child</artifactId>"
                        + "<dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId></dependency>"
                        + "</dependencies></project>");

        project = new Project();
        project.setProperty("user.home", System.getProperty("user.home"));
        project.setProperty(
                "maven.repo.local",
                Files.createDirectory(tmp.resolve("local")).toFile().getAbsolutePath());
        task = new Task() {};
        task.setProject(project);

        RemoteRepository repository = new RemoteRepository();
        repository.setProject(project);
        repository.setId("test-remote");
        repository.setUrl(remote.toUri().toString());
        repositories = new RemoteRepositories();
        repositories.setProject(project);
        repositories.addRemoterepo(repository);
    }

    private static void write(Path path, String content) throws Exception {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testParentIsResolvedFromTheRepositoriesOfTheTask() {
        Model model = AntRepoSys.getInstance(project).loadModel(task, pom, true, repositories);

        assertEquals("test.ant", model.getGroupId());
        assertEquals("1", model.getVersion());
        assertEquals("from-parent", model.getProperties().get("inherited"));
        assertEquals("2", model.getDependencies().get(0).getVersion());
    }
}
