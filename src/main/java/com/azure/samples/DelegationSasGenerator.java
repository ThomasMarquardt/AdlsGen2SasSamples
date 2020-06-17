/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.azure.samples;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.UserDelegationKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delegation SAS generator.
 */
public class DelegationSasGenerator extends SasGenerator {
  private static final Logger LOG = LoggerFactory.getLogger(DelegationSasGenerator.class);
  public static final String GEN2_APPEND_OPERATION = "append";
  public static final String GEN2_CHECK_ACCESS_OPERATION = "check-access";
  public static final String GEN2_CREATE_DIRECTORY_OPERATION = "create-directory";
  public static final String GEN2_CREATE_FILE_OPERATION = "create-file";
  public static final String GEN2_DELETE_OPERATION = "delete";
  public static final String GEN2_DELETE_RECURSIVE_OPERATION = "delete-recursive";
  public static final String GEN2_FLUSH_OPERATION = "flush";
  public static final String GEN2_GET_ACL_OPERATION = "get-acl";
  public static final String GEN2_GET_STATUS_OPERATION = "get-status";
  public static final String GEN2_GET_PROPERTIES_OPERATION = "get-properties";
  public static final String GEN2_LIST_OPERATION = "list";
  public static final String GEN2_LIST_RECURSIVE_OPERATION = "list-recursive";
  public static final String GEN2_READ_OPERATION = "read";
  public static final String GEN2_RENAME_SOURCE_OPERATION = "rename-source";
  public static final String GEN2_RENAME_DESTINATION_OPERATION = "rename-destination";
  public static final String GEN2_SET_ACL_OPERATION = "set-acl";
  public static final String GEN2_SET_OWNER_OPERATION = "set-owner";
  public static final String GEN2_SET_PERMISSION_OPERATION = "set-permission";
  public static final String GEN2_SET_PROPERTIES_OPERATION = "set-properties";

  private static final Duration KEY_RENEWAL_PERIOD_IN_SECONDS = Duration.ofDays(1);
  private static final Duration KEY_START_TIME_BACKOFF_IN_SECONDS = Duration.ofMinutes(5);
  private static final Duration KEY_EXPIRY_PERIOD_IN_SECONDS = KEY_RENEWAL_PERIOD_IN_SECONDS.multipliedBy(2);

  private final ClientSecretCredential tokenProvider;
  private final String account;
  private final String endpoint;
  private final String skoid;
  private final String sktid;
  private String skt;
  private String ske;
  private OffsetDateTime keyExpiry = OffsetDateTime.MIN;
  private OffsetDateTime keyRenewal = OffsetDateTime.MIN;
  private final String sks = "b";
  private String skv;

  /**
   * Creates a delegation SAS generator that uses the OAuth 2.0 client credentials flow
   * to acquire an OAuth token and then uses that token to acquire a user delegation key
   * with which to sign SAS tokens.  The key is acquired with an expiration of
   * two days, and is refreshed every.
   * @param accountName the Storage Account name
   * @param appId - the application ID of a registered application that is assigned
   *              the Storage Blob Delegator role and the Storage Blob Data Owner
   *              role at the scope of the account.  This application is used to
   *              generateSasForOperation user delegation keys and sign SAS tokens.
   * @param appSecret - a client secret for the registered application.
   * @param servicePrincipalOID - the service principal object ID for the application.
   * @param servicePrincipalTID - the service principal tenant ID for the application.
   */
  public DelegationSasGenerator(
      String accountName,
      String appId,
      String appSecret,
      String servicePrincipalOID,
      String servicePrincipalTID) {
    account = accountName;
    endpoint = "https://" + account + ".blob.core.windows.net";
    skoid = servicePrincipalOID;
    sktid = servicePrincipalTID;
    tokenProvider = new ClientSecretCredentialBuilder()
        .tenantId(servicePrincipalTID)
        .clientId(appId)
        .clientSecret(appSecret)
        .build();
    refreshKey();
  }

  /**
   * Generate User Delegation SAS for the specified ADLS operation with minimal
   * permissions.
   * @param container the container that will be accessed with the SAS token.
   * @param path the path that will be accessed with the SAS token. The path should not
   *             begin with / and the root path is the empty string.
   * @param operation the ADLS Gen2 REST API operation.
   * @return a SAS token with the default expiry and minimal permissions for performing
   * the operation on the specified resource.
   */
  public String generateSasForOperation(
      String container,
      String path,
      String operation) {
    if (isNullOrEmpty(container)
        || path == null
        || (path.length() != 0 && path.charAt(0) == '/')  // should not begin with '/' (root path is empty string)
        || isNullOrEmpty(operation)) {
      throw new IllegalArgumentException();
    }
    return generateForOperationInternal(container, path, operation,
        null, null, null);
  }

  /**
   * Generate User Delegation SAS for the specified ADLS operation and user
   * with minimal permissions.
   * @param container the container that will be accessed with the SAS token.
   * @param path the path that will be accessed with the SAS token.  The path should not
   *             begin with / and the root path is the empty string.
   * @param operation the ADLS Gen2 REST API operation.
   * @param correlationID a GUID value that will be logged in the storage diagnostic
   *                      logs and can be used to correlate SAS generation with storage
   *                      resource access.  This is the optional scid query parameter value
   *                      of the SAS token.
   * @param userOID the AAD object ID of the user or service principal to whom the
   *                        SAS token is given.  This OID will be logged in the storage diagnostic
   *                        logs.  This is the optional saoid or suoid query parameter value of the SAS token.
   * @param performPosixAuthorizationCheckForUser true if a POSIX authorization check should be performed for
   *                                              the user specified by userOID; otherwise false.
   * @return a SAS token with the default expiry and minimal permissions for performing
   * the operation on the specified resource by the specified user with optional POSIX
   * authorization check for the user.
   */
  public String generateSasForOperation(
      String container,
      String path,
      String operation,
      UUID correlationID,
      UUID userOID,
      boolean performPosixAuthorizationCheckForUser) {
    if (isNullOrEmpty(container)
        || path == null
        || (path.length() != 0 && path.charAt(0) == '/')  // should not begin with '/' (root path is empty string)
        || isNullOrEmpty(operation)) {
      throw new IllegalArgumentException();
    }
    UUID saoid = performPosixAuthorizationCheckForUser ? null : userOID;
    UUID suoid = performPosixAuthorizationCheckForUser ? userOID : null;
    return generateForOperationInternal(container, path, operation,
        correlationID, saoid, suoid);
  }

  /**
   * Generate a User Delegation SAS with path (or blob) scope, the specified
   * expiry, and permissions.
   * @param container the container that will be accessed with the SAS token.
   * @param path the path that will be accessed with the SAS token.  The path should not
   *             begin with / and the root path is the empty string.
   * @param expiryTime the expiration time of the SAS token.  This must be less than the
   *                   user delegation key expiry, and will be truncated if necessary.
   * @param sp the permissions granted by the SAS token.  And combination of the
   *           characters "racwdxltfmeop" is valid, but they must appear in this order.
   * @return the SAS token
   */
  public String generateSasForPath(
      String container,
      String path,
      OffsetDateTime expiryTime,
      String sp) {
    if (isNullOrEmpty(container)
        || path == null
        || (path.length() != 0 && path.charAt(0) == '/')  // should not begin with '/' (root path is empty string)
        || !isValidSignedPermission(sp)) {
      throw new IllegalArgumentException();
    }
    final String sr = "b";
    return generateInternal(container, path, expiryTime, sp, sr, null, null, null, null);
  }

  /**
   * Generate a User Delegation SAS with directory scope, the specified
   * expiry, and permissions.
   * @param container the container that will be accessed with the SAS token.
   * @param directory the directory that will be accessed with the SAS token.  The directory should not
   *             begin with / and the root path is the empty string.
   * @param expiryTime the expiration time of the SAS token.  This must be less than the
   *                   user delegation key expiry, and will be truncated if necessary.
   * @param sp the permissions granted by the SAS token.  And combination of the
   *           characters "racwdxltfmeop" is valid, but they must appear in this order.
   * @return the SAS token
   */
  public String generateSasForDirectory(
      String container,
      String directory,
      OffsetDateTime expiryTime,
      String sp) {
    if (isNullOrEmpty(container)
        || directory == null
        || (directory.length() != 0 && directory.charAt(0) == '/')  // should not begin with '/' (root path is empty string)
        || !isValidSignedPermission(sp)) {
      throw new IllegalArgumentException();
    }
    final String sr = "d";
    final String sdd = Integer.toString(getDirectoryDepth(directory));
    return generateInternal(container, directory, expiryTime, sp, sr, sdd, null, null, null);
  }

  /**
   * Generate a User Delegation SAS with container scope, the specified
   * expiry, and permissions.
   * @param container the container that will be accessed with the SAS token.
   * @param expiryTime the expiration time of the SAS token.  This must be less than the
   *                   user delegation key expiry, and will be truncated if necessary.
   * @param sp the permissions granted by the SAS token.  And combination of the
   *           characters "racwdxltfmeop" is valid, but they must appear in this order.
   * @return the SAS token
   */
  public String generateSasForContainer(
      String container,
      OffsetDateTime expiryTime,
      String sp) {
    if (isNullOrEmpty(container)
        || !isValidSignedPermission(sp)) {
      throw new IllegalArgumentException();
    }
    final String sr = "c";
    return generateInternal(container, null, expiryTime, sp, sr, null, null, null, null);
  }

  private String generateForOperationInternal(
      String container,
      String path,
      String operation,
      UUID scid,
      UUID saoid,
      UUID suoid) {
    String sp;
    String sr = "b";
    String sdd = null;
    switch (operation) {
      case GEN2_CHECK_ACCESS_OPERATION:
        sp = "e";
        break;
      case GEN2_APPEND_OPERATION:
      case GEN2_FLUSH_OPERATION:
      case GEN2_CREATE_FILE_OPERATION:
      case GEN2_CREATE_DIRECTORY_OPERATION:
        sp = "w";
        break;
      case GEN2_DELETE_OPERATION:
        sp = "d";
        break;
      case GEN2_DELETE_RECURSIVE_OPERATION:
        sp = "d";
        sr = "d";
        sdd = Integer.toString(getDirectoryDepth(path));
        break;
      case GEN2_GET_ACL_OPERATION:
      case GEN2_GET_STATUS_OPERATION:
        sp = "e";
        break;
      case GEN2_LIST_OPERATION:
        sp = "l";
        break;
      case GEN2_LIST_RECURSIVE_OPERATION:
        sp = "l";
        sr = "d";
        sdd = Integer.toString(getDirectoryDepth(path));
        break;
      case GEN2_READ_OPERATION:
      case GEN2_GET_PROPERTIES_OPERATION:
        sp = "r";
        break;
      case GEN2_RENAME_DESTINATION_OPERATION:
      case GEN2_RENAME_SOURCE_OPERATION:
        sp = "m";
        break;
      case GEN2_SET_ACL_OPERATION:
        sp = "p";
        break;
      case GEN2_SET_OWNER_OPERATION:
        sp = "o";
        break;
      case GEN2_SET_PERMISSION_OPERATION:
        sp = "p";
        break;
      case GEN2_SET_PROPERTIES_OPERATION:
        sp = "w";
        break;
      default:
        throw new IllegalArgumentException(operation);
    }
    final OffsetDateTime se = OffsetDateTime.now().plus(KEY_RENEWAL_PERIOD_IN_SECONDS);
    return generateInternal(container, path, se, sp, sr, sdd, scid, saoid, suoid);
  }

  private String generateInternal(
      String container,
      String path,
      OffsetDateTime expiryTime,
      String sp,
      String sr,
      String sdd,
      UUID scid,
      UUID saoid,
      UUID suoid) {

    refreshKey();

    final String st = ISO_8601_FORMATTER.format(OffsetDateTime.now().minus(KEY_START_TIME_BACKOFF_IN_SECONDS));
    final String se = keyExpiry.isBefore(expiryTime) ? ISO_8601_FORMATTER.format(keyExpiry) : ISO_8601_FORMATTER.format(keyRenewal);
    final String sv = AuthenticationVersion.Feb20.toString();

    String signature = computeSignatureForSAS(container, path,
        sp, st, se, sv, sr,
        scid, saoid, suoid);

    StringBuilder sb = new StringBuilder(512);
    sb.append("skoid=");
    sb.append(urlEncode(skoid));
    sb.append("&sktid=");
    sb.append(urlEncode(sktid));
    sb.append("&skt=");
    sb.append(urlEncode(skt));
    sb.append("&ske=");
    sb.append(urlEncode(ske));
    sb.append("&sks=");
    sb.append(urlEncode(sks));
    sb.append("&skv=");
    sb.append(urlEncode(skv));
    if (saoid != null) {
      sb.append("&saoid=");
      sb.append(urlEncode(saoid.toString()));
    }
    if (suoid != null) {
      sb.append("&suoid=");
      sb.append(urlEncode(suoid.toString()));
    }
    if (scid != null) {
      sb.append("&scid=");
      sb.append(urlEncode(scid.toString()));

    }
    sb.append("&sp=");
    sb.append(urlEncode(sp));
    sb.append("&st=");
    sb.append(urlEncode(st));
    sb.append("&se=");
    sb.append(urlEncode(se));
    sb.append("&sv=");
    sb.append(urlEncode(sv));
    sb.append("&sr=");
    sb.append(urlEncode(sr));
    if (sdd != null) {
      sb.append("&sdd=");
      sb.append(urlEncode(sdd));
    }
    sb.append("&sig=");
    sb.append(urlEncode(signature));

    return sb.toString();
  }

  private String computeSignatureForSAS(
      String containerName, String path,
      String sp, String st, String se, String sv, String sr,
      UUID scid, UUID saoid, UUID suoid) {
    StringBuilder sb = new StringBuilder();
    sb.append(sp);
    sb.append("\n");
    sb.append(st);
    sb.append("\n");
    sb.append(se);
    sb.append("\n");
    // canonicalized resource
    sb.append("/blob/");
    sb.append(account);
    sb.append("/");
    sb.append(containerName);
    if (path != null && !sr.equals("c")) {
      sb.append("/");
      sb.append(path);
    }
    sb.append("\n");
    sb.append(skoid);
    sb.append("\n");
    sb.append(sktid);
    sb.append("\n");
    sb.append(skt);
    sb.append("\n");
    sb.append(ske);
    sb.append("\n");
    sb.append(sks);
    sb.append("\n");
    sb.append(skv);
    sb.append("\n");
    if (saoid != null) {
      sb.append(saoid.toString());
    }
    sb.append("\n");
    if (suoid != null) {
      sb.append(suoid.toString());
    }
    sb.append("\n");
    if (scid != null) {
      sb.append(scid.toString());
    }
    sb.append("\n");

    sb.append("\n"); // sip
    sb.append("\n"); // spr
    sb.append(sv);
    sb.append("\n");
    sb.append(sr);
    sb.append("\n");
    sb.append("\n"); // - For optional : rscc - ResponseCacheControl
    sb.append("\n"); // - For optional : rscd - ResponseContentDisposition
    sb.append("\n"); // - For optional : rsce - ResponseContentEncoding
    sb.append("\n"); // - For optional : rscl - ResponseContentLanguage
    sb.append("\n"); // - For optional : rsct - ResponseContentType

    String stringToSign = sb.toString();
    LOG.debug("string-to-sign=[{}]", stringToSign.replace('\n', '.'));
    return computeHmac256(stringToSign);
  }

  private void refreshKey() {
    if (OffsetDateTime.now().isBefore(keyRenewal)) {
      return;
    }
    synchronized (this) {
      OffsetDateTime now = OffsetDateTime.now();
      if (now.isBefore(keyRenewal)) {
        return;
      }
      OffsetDateTime renewal = now.plus(KEY_RENEWAL_PERIOD_IN_SECONDS);
      OffsetDateTime start = OffsetDateTime.now().minus(KEY_START_TIME_BACKOFF_IN_SECONDS);
      OffsetDateTime expiry = OffsetDateTime.now().plus(KEY_EXPIRY_PERIOD_IN_SECONDS);
      BlobServiceClient client = new BlobServiceClientBuilder()
          .endpoint(endpoint)
          .credential(this.tokenProvider)
          .buildClient();
      UserDelegationKey udk = client.getUserDelegationKey(start, expiry);
      initializeMac(Base64.getDecoder().decode(udk.getValue()));
      skt = ISO_8601_FORMATTER.format(udk.getSignedStart());
      ske = ISO_8601_FORMATTER.format(udk.getSignedExpiry());
      skv = udk.getSignedVersion();
      keyExpiry = udk.getSignedExpiry();
      keyRenewal = renewal;
    }
  }
}