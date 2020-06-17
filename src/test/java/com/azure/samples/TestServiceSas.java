/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.azure.samples;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import com.azure.storage.file.datalake.DataLakeFileSystemClientBuilder;
import com.azure.storage.file.datalake.DataLakePathClientBuilder;
import com.azure.storage.file.datalake.models.DataLakeStorageException;
import com.azure.storage.file.datalake.models.ListPathsOptions;
import com.azure.storage.file.datalake.models.PathAccessControl;
import com.azure.storage.file.datalake.models.PathItem;
import com.azure.storage.file.datalake.models.PathPermissions;
import com.azure.storage.file.datalake.models.RolePermissions;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestServiceSas {
  private static final Logger LOG = LoggerFactory.getLogger(TestServiceSas.class);
  private static ServiceSasGenerator generator;
  private static String container;
  private static String blobEndpoint;
  private static String dfsEndpoint;
  private static String connectionString;

  @org.junit.BeforeClass
  public static void setup() throws Exception {
    TestConfigurationSettings config = new TestConfigurationSettings();
    String account = config.getTestServiceSASAccountName();
    String key = config.getTestServiceSASAccountKey();
    generator = new ServiceSasGenerator(account, key);
    blobEndpoint = "https://" + account + ".blob.core.windows.net";
    dfsEndpoint = "https://" + account + ".dfs.core.windows.net";
    connectionString = "DefaultEndpointsProtocol=https;AccountName="
        + account
        + ";AccountKey="
        + key
        + ";EndpointSuffix=core.windows.net";

    BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
        .endpoint(blobEndpoint)
        .connectionString(connectionString)
        .buildClient();

    container = "testservicesas-" + UUID.randomUUID().toString();
    blobServiceClient.createBlobContainer(container);
  }


  @org.junit.AfterClass
  public static void cleanup() throws Exception {
    BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
        .endpoint(blobEndpoint)
        .connectionString(connectionString)
        .buildClient();

    blobServiceClient.deleteBlobContainer(container);
  }

  @org.junit.Test
  public void generateSasForPath() throws Exception {
    // generate a SAS for the root path
    String rootFolder = "";
    OffsetDateTime expiry = OffsetDateTime.now().plus(1, ChronoUnit.HOURS);
    String permissions = "eop"; // execute,manageOwnership,modifyPermissions
    String sasToken = generator.generateSasForPath(container, rootFolder, expiry, permissions);
    LOG.info(sasToken);

    // get owner, owning group, permissions and ACL
    DataLakeFileClient fileClient = new DataLakePathClientBuilder()
        .endpoint(dfsEndpoint)
        .sasToken(sasToken)
        .fileSystemName(container)
        .pathName(rootFolder)
        .buildFileClient();

    String ownerNew = UUID.randomUUID().toString();
    String groupNew = UUID.randomUUID().toString();
    PathPermissions permissionsNew = new PathPermissions().setOther(RolePermissions.parseSymbolic("--x", false));
    fileClient.setPermissions(permissionsNew, groupNew, ownerNew);

    PathAccessControl descriptor = fileClient.getAccessControl();
    assertEquals(ownerNew, descriptor.getOwner());
    assertEquals(groupNew, descriptor.getGroup());
    assertEquals(permissionsNew.toString(), descriptor.getPermissions().toString());

    // attempt to list root folder contents
    DataLakeFileSystemClient filesystemClient = new DataLakeFileSystemClientBuilder()
        .endpoint(dfsEndpoint)
        .sasToken(sasToken)
        .fileSystemName(container)
        .buildClient();

    ListPathsOptions options = new ListPathsOptions()
        .setPath(rootFolder)
        .setRecursive(false);
    try {
      filesystemClient.listPaths(options, Duration.ofSeconds(30));
    } catch (DataLakeStorageException ex) {
      assertEquals(403, ex.getStatusCode());
      assertEquals("AuthorizationPermissionMismatch", ex.getErrorCode());
      assertEquals("This request is not authorized to perform this operation using this permission.", ex.getServiceMessage());
    }
  }

  @org.junit.Test
  public void generateSasForDirectory() throws Exception {
    // generate a SAS with directory scope
    String directory = "directory-" + UUID.randomUUID().toString();
    OffsetDateTime expiry = OffsetDateTime.now().plus(1, ChronoUnit.HOURS);
    String permissions = "wl"; // write,list
    String sasToken = generator.generateSasForDirectory(container, directory, expiry, permissions);
    LOG.info(sasToken);

    // create 2 files
    String path1 = directory + "/path1-" + UUID.randomUUID().toString();
    String path2 = directory + "/path2-" + UUID.randomUUID().toString();
    DataLakeFileClient fileClient = new DataLakePathClientBuilder()
        .endpoint(dfsEndpoint)
        .sasToken(sasToken)
        .fileSystemName(container)
        .pathName(path1)
        .buildFileClient();
    String inputMsg = "hello, world!";
    try (ByteArrayInputStream input = new ByteArrayInputStream(inputMsg.getBytes("UTF-8"))) {
      int length = input.available();
      fileClient.create();
      fileClient.append(input, 0, length);
      fileClient.flush(length);
    }
    fileClient = new DataLakePathClientBuilder()
        .endpoint(dfsEndpoint)
        .sasToken(sasToken)
        .fileSystemName(container)
        .pathName(path2)
        .buildFileClient();
    try (ByteArrayInputStream input = new ByteArrayInputStream(inputMsg.getBytes("UTF-8"))) {
      int length = input.available();
      fileClient.create();
      fileClient.append(input, 0, length);
      fileClient.flush(length);
    }

    // attempt to create 3rd file outside directory
    String path3 = "path3-" + UUID.randomUUID().toString();
    fileClient = new DataLakePathClientBuilder()
        .endpoint(dfsEndpoint)
        .sasToken(sasToken)
        .fileSystemName(container)
        .pathName(path3)
        .buildFileClient();
    try {
      fileClient.create();
    } catch(DataLakeStorageException ex) {
      assertEquals(403, ex.getStatusCode());
      assertEquals("AuthenticationFailed", ex.getErrorCode());
    }

    //list directory contents
    DataLakeFileSystemClient filesystemClient = new DataLakeFileSystemClientBuilder()
        .endpoint(dfsEndpoint)
        .sasToken(sasToken)
        .fileSystemName(container)
        .buildClient();

    ListPathsOptions options = new ListPathsOptions()
        .setPath(directory)
        .setRecursive(false);
    int count = 0;
    for (PathItem item: filesystemClient.listPaths(options, Duration.ofSeconds(30))) {
      count++;
      String path = (count == 1) ? path1 : path2;
      assertEquals(path, item.getName());
    }
    assertEquals(2, count);
  }

  @org.junit.Test
  public void generateSasForContainer() throws Exception {
    // generate a SAS with container scope
    OffsetDateTime expiry = OffsetDateTime.now().plus(1, ChronoUnit.HOURS);
    String permissions = "rwdlmeop"; //read,write,delete,list,move,execute,manageownership,modifypermissions
    String sasToken = generator.generateSasForContainer(container, expiry, permissions);
    LOG.info(sasToken);

    // use SAS to create a blob
    String path = "/path-" + UUID.randomUUID().toString();
    BlobClient blobClient = new BlobClientBuilder()
        .endpoint(blobEndpoint)
        .containerName(container)
        .blobName(path.substring(1))
        .sasToken(sasToken)
        .buildClient();
    String inputMsg = "hello, world!";
    try (ByteArrayInputStream input = new ByteArrayInputStream(inputMsg.getBytes("UTF-8"))) {
      blobClient.upload(input, input.available());
    }

    // read blob as a file and validate message
    DataLakeFileClient fileClient = new DataLakePathClientBuilder()
        .endpoint(dfsEndpoint)
        .sasToken(sasToken)
        .fileSystemName(container)
        .pathName(path.substring(1))
        .buildFileClient();
    try (ByteArrayOutputStream output = new ByteArrayOutputStream(256)) {
      fileClient.read(output);
      String outputMsg = new String(output.toByteArray(), StandardCharsets.UTF_8);
      assertEquals(inputMsg, outputMsg);
    }
  }
}