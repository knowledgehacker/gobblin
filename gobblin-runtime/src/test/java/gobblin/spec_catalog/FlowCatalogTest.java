/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gobblin.spec_catalog;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;

import gobblin.runtime.api.Spec;
import gobblin.runtime.api.SpecExecutorInstanceProducer;
import gobblin.runtime.api.FlowSpec;
import gobblin.runtime.app.ServiceBasedAppLauncher;
import gobblin.runtime.spec_catalog.FlowCatalog;
import gobblin.runtime.spec_executorInstance.InMemorySpecExecutorInstanceProducer;
import gobblin.util.ConfigUtils;
import gobblin.util.PathUtils;


public class FlowCatalogTest {
  private static final Logger logger = LoggerFactory.getLogger(FlowCatalog.class);
  private static Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private static final String SPEC_STORE_PARENT_DIR = "/tmp";
  private static final String SPEC_STORE_DIR = "/tmp/flowTestSpecStore";
  private static final String SPEC_DESCRIPTION = "Test Flow Spec";
  private static final String SPEC_VERSION = "1";

  private ServiceBasedAppLauncher serviceLauncher;
  private FlowCatalog flowCatalog;
  private FlowSpec flowSpec;

  @BeforeClass
  public void setup() throws Exception {
    File specStoreDir = new File(SPEC_STORE_DIR);
    if (specStoreDir.exists()) {
      FileUtils.deleteDirectory(specStoreDir);
    }

    Properties properties = new Properties();
    properties.put("specStore.fs.dir", SPEC_STORE_DIR);

    this.serviceLauncher = new ServiceBasedAppLauncher(properties, "FlowCatalogTest");

    this.flowCatalog = new FlowCatalog(ConfigUtils.propertiesToConfig(properties),
        Optional.of(logger));
    this.serviceLauncher.addService(flowCatalog);

    // Start Catalog
    this.serviceLauncher.start();

    // Create Spec to play with
    this.flowSpec = initFlowSpec();
  }

  private FlowSpec initFlowSpec() {
    Properties properties = new Properties();
    properties.put("specStore.fs.dir", SPEC_STORE_DIR);
    properties.put("specExecInstance.capabilities", "source:destination");
    Config config = ConfigUtils.propertiesToConfig(properties);

    SpecExecutorInstanceProducer specExecutorInstanceProducer = new InMemorySpecExecutorInstanceProducer(config);

    FlowSpec.Builder flowSpecBuilder = null;
    try {
      flowSpecBuilder = FlowSpec.builder(computeFlowSpecURI())
          .withConfig(config)
          .withDescription(SPEC_DESCRIPTION)
          .withVersion(SPEC_VERSION)
          .withTemplate(new URI("templateURI"));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    return flowSpecBuilder.build();
  }

  @AfterClass
  public void cleanUp() throws Exception {
    // Shutdown Catalog
    this.serviceLauncher.stop();

    File specStoreDir = new File(SPEC_STORE_DIR);
    if (specStoreDir.exists()) {
      FileUtils.deleteDirectory(specStoreDir);
    }
  }

  @Test
  public void createFlowSpec() {
    // List Current Specs
    Collection<Spec> specs = flowCatalog.getSpecs();
    logger.info("[Before Create] Number of specs: " + specs.size());
    int i=0;
    for (Spec spec : specs) {
      FlowSpec flowSpec = (FlowSpec) spec;
      logger.info("[Before Create] Spec " + i++ + ": " + gson.toJson(flowSpec));
    }
    Assert.assertTrue(specs.size() == 0, "Spec store should be empty before addition");

    // Create and add Spec
    this.flowCatalog.put(flowSpec);

    // List Specs after adding
    specs = flowCatalog.getSpecs();
    logger.info("[After Create] Number of specs: " + specs.size());
    i = 0;
    for (Spec spec : specs) {
      flowSpec = (FlowSpec) spec;
      logger.info("[After Create] Spec " + i++ + ": " + gson.toJson(flowSpec));
    }
    Assert.assertTrue(specs.size() == 1, "Spec store should contain 1 Spec after addition");
  }

  @Test (dependsOnMethods = "createFlowSpec")
  public void deleteFlowSpec() {
    // List Current Specs
    Collection<Spec> specs = flowCatalog.getSpecs();
    logger.info("[Before Delete] Number of specs: " + specs.size());
    int i=0;
    for (Spec spec : specs) {
      FlowSpec flowSpec = (FlowSpec) spec;
      logger.info("[Before Delete] Spec " + i++ + ": " + gson.toJson(flowSpec));
    }
    Assert.assertTrue(specs.size() == 1, "Spec store should initially have 1 Spec before deletion");

    this.flowCatalog.remove(flowSpec.getUri());

    // List Specs after adding
    specs = flowCatalog.getSpecs();
    logger.info("[After Create] Number of specs: " + specs.size());
    i = 0;
    for (Spec spec : specs) {
      flowSpec = (FlowSpec) spec;
      logger.info("[After Create] Spec " + i++ + ": " + gson.toJson(flowSpec));
    }
    Assert.assertTrue(specs.size() == 0, "Spec store should be empty after deletion");
  }

  public URI computeFlowSpecURI() {
    // Make sure this is relative
    URI uri = PathUtils.relativizePath(new Path(SPEC_STORE_DIR), new Path(SPEC_STORE_PARENT_DIR)).toUri();
    return uri;
  }
}
