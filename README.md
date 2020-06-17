# Sample Code for Azure Data Lake Storage Gen2 Shared Access Signature Update Preview

Azure Data Lake Storage Gen2 (ADLS Gen2) is previewing updates to Shared Access Signatures
 (SAS) that include:
 
 1) Directory scoped SAS tokens for granting constrained access to a directory
 and the files within.
 
 2) Authorization features providing an enhanced authorization model for multi-user
 cluster workloads like Hadoop and Spark. SAS tokens may be constrained to a specific
  filesystem operation and user, providing a less vulnerable access token that is 
  safer for the purpose of distributing across a multi-user cluster. One use case
  for these features is the integration of the Hadoop ABFS driver with Apache Ranger.
  
This project includes [code samples](./src/test/java/com/azure/samples) that show you how to use the new SAS features.

## Getting started
The ADSL Gen2 REST API supports the new features when using an authentication version
of 2020-02-10 or higher.  These SAS features are supported by older REST versions, so
you do not need to update the REST version of your client, only the authentication version
must be 2020-02-10 or higher.  The authentication version is specified by the sv query
parameter of the SAS token.

An [ADLS Gen2 SDK](https://azure.microsoft.com/en-us/blog/filesystem-sdks-for-azure-data-lake-storage-gen2-now-generally-available/) supporting the new features will not be available until
later this year, but the sample code in this project will help you get started without
requiring you to work with the low-level REST APIs.

The sample code consist of two classes that generate SAS tokens: DelegationSasGenerator
and ServiceSasGenerator.  Both classes include methods to generate SAS with container,
directory, or blob scope (note that in this context container is equivalent to filesystem
and blob is equivalent to file).  Furthermore, the DelegationSasGenerator class includes
methods to generate SAS constrained to a specific filesystem operation and user.

There are two test classes that provide examples of how to generate SAS tokens with
the aforementioned SAS generators.  These test classes are TestServiceSas and TestDelegationSas.
The test classes use the [ADLS Gen2 SDK for Java](https://docs.microsoft.com/en-us/azure/storage/blobs/data-lake-storage-directory-file-acl-java) to invoke filesystem operations using
the SAS tokens created by the two SAS generators included in this project.

Before you can run the tests, you must register an app, assign it to roles so that
it has the permissions to generate user delegation keys and fully control
filesystems within a test storage account, and update the settings.json file with
the test account name and credentials:

    1) Register an app:
      a) Login to https://portal.azure.com, select your AAD directory and search for app registrations.
      b) Click "New registration".
      c) Provide a display name, such as "sas-sample-app".
      d) Set the account type to "Accounts in this organizational directory only ({YOUR_Tenant} only - Single tenant)".
      e) For Redirect URI select Web and enter "http://localhost".
      f) Click Register.

    2)  Create necessary role assignments:
      a) Login to https://portal.azure.com and find the Storage account with hierarchical namespace enabled
         that you plan to run the tests against.
      b) Select "Access Control (IAM)".
      c) Select Role Assignments
      d) Click Add and select "Add role assignments"
      e) For Role and enter "Storage Blob Data Owner".
      f) Under Select enter the name of the app you registered in step 1 and select it.
      g) Click Save.
      h) Repeat above steps to create a second role assignment for the app but this time for
         the "Storage Blob Delegator" role.

    3) Generate a new client secret for the application:
      a) Login to https://portal.azure.com and find the app registered in step 1.
      b) Select "Certificates and secrets".
      c) Click "New client secret".
      d) Enter a description (eg. Secret1)
      e) Set expiration period.  Expires in 1 year is good.
      f) Click Add
      g) Copy the secret and expiration to a safe location.

    4) Set the following configuration values in you local copy of src/test/resources/settings.json:
        {
          "TestServiceSASSettings": {
            "accountName": "{YOUR_ACCOUNT_NAME}",
            "accountKey": "{YOUR_ACCOUNT_KEY}"
          },
          "TestDelegationSASSettings": {
            "accountName": "{YOUR_ACCOUNT_NAME}",
            "RegisteredApplication": {
              "appName": "{YOUR_APP_NAME}",
              "appId": "{YOUR_APP_ID}",
              "appSecret": "{YOUR_APP_SECRET}",
              "appServicePrincipalOID": "{YOUR_APP_SERVICE_PRINCIPAL_OID}",
              "appServicePrincipalTID": "{YOUR_APP_SERVICE_PRINCIPAL_TID}"
            }
          }
        }

The easiest way to run and debug the test cases is with [IntelliJ](https://www.jetbrains.com/idea/).  Simply clone the
sample code repository and then load the pom.xml file in IntelliJ.

Alternatively you can build and run the tests using maven:
1) cd to directory containing pom.xml
2) build the project: mvn clean compile
3) run the tests: mvn test

## Details on the ADLS Gen2 SAS Update
The SAS updates are described below, but for context on how this relates to existing
functionality of SAS please refer to [Shared 
Access Signatures](https://docs.microsoft.com/en-us/rest/api/storageservices/delegate-access-with-shared-access-signature).

1) Service and User Delegation SAS will support directory scope (sr=d) when 
the authentication version (sv) is 2020-02-10 or higher and namespace is enabled. 
 The semantics for directory scope (sr=d) are similar to container scope (sr=c),
except access is restricted to a directory and the files within.  When sr=d is specified, 
the sdd query parameter is also required.  The sdd query parameter indicates the depth 
of the directory specified in the canonicalizedResource field of the string-to-sign (see below).
 The depth of the directory is the number of directories beneath the root folder. 
 For example, the directory https://{account}.blob.core.windows.net/{container}/d1/d2 has 
 a depth of 2 and the root directory https://{account}.blob.core.windows.net/{container}/ 
 has a depth of 0.  The value of sdd must be a non-negative integer.
 
2) Service and User Delegation SAS have support for new permissions (sp) when the 
authentication version (sv) is 2020-02-10 or higher:

    1) Move (m)  Move a file or directory to a new location.  If the path is a directory, 
the directory and its contents can be moved to a new location.
  
    2) Execute (e)  Get the system properties and, when namespace is enabled, get the POSIX 
ACL of a blob.  This permission allows the caller to read system properties, but not 
user defined metadata.  Furthermore, if the namespace is enabled and the caller is the 
owner of a blob, this permission grants the ability to set the owning group, POSIX permissions,
   and POSIX ACL of the blob.
   
    3) Ownership (o)  When namespace is enabled, allows the caller to set owner, owning group,
 or act as the owner when renaming or deleting a blob (file or directory) within a folder
  that has the sticky bit set.
  
    4) Permissions (p)  When namespace is enabled, allows the caller to set permissions and POSIX
ACLs on blobs (files and directories).

3) User Delegation SAS supports an optional user OID carried in either the saoid or
 suoid parameter when the authentication version (sv) is 2020-02-10 or higher:
 
    1) The user delegating access (skoid) must have 
    Microsoft.Storage/storageAccounts/blobServices/containers/blobs/runAsSuperUser/action or
    Microsoft.Storage/storageAccounts/blobServices/containers/blobs/manageOwnership/action 
    RBAC permission when using a SAS with an optional user OID.

    2) When saoid is specified, the identity is assumed to be an authorized user acting 
    on behalf of the user delegating access (skoid).
  
    3) When suoid is specified, the ADLS Gen2 service will perform a POSIX ACL authorization
    check for this identity and operation before granting access, but otherwise treats
    it the same as saoid. The namespace must be enabled when suoid is used, or the 
    request will fail with an authorization error.

    4) The optional user OID will be included in the diagnostic logs for auditing purposes.
    
    5) The optional user OID also restricts operations related to file or folder ownership:
    
        1) If the operation creates a file or folder, the owner of the file or folder will 
        be set to the value specified by the optional user OID.  If an optional user OID 
        is not specified, then the owner of the file or folder will be set to the value 
        specified by the skoid parameter.

        2) If the sticky bit is set on the parent folder and the operation is delete 
        or rename, then the owner of the parent folder or the owner of the resource 
        must match the value specified by the optional user OID.

        3) If the operation is SetAccessControl and x-ms-owner is being set, the value 
        must match the value specified by the optional user OID.  

        4) If the operation is SetAccessControl and x-ms-owner is being set, the value 
        of x-ms-owner must match the value specified by the optional user OID. 

        5) If the operation is SetAccessControl and x-ms-group is being set, then 
        the value specified by the optional user OID must be a member of the group 
        specified by x-ms-group.
 
4) User Delegation SAS supports an optional correlation ID carried in the scid parameter
when the authentication version (sv) is 2020-02-10 or higher.  This is a GUID value 
that will be logged in the storage diagnostic logs and can be used to correlate SAS 
generation with storage resource access. 

5)	For User Delegation SAS, the string-to-sign for authentication version 2020-02-10 
or higher has the following format:

            StringToSign = signedPermissions + "\n" +  
                           signedStart + "\n" +  
                           signedExpiry + "\n" +  
                           canonicalizedResource + "\n" +  
                           signedKeyObjectId + "\n" +
                           signedKeyTenantId + "\n" +
                           signedKeyStart + "\n" +
                           signedKeyExpiry  + "\n" +
                           signedKeyService + "\n" +
                           signedKeyVersion + "\n" +
                           signedAuthorizedUserObjectId + "\n" +  
                           signedUnauthorizedUserObjectId + "\n" +  
                           signedCorrelationId + "\n" +  
                           signedIP + "\n" +  
                           signedProtocol + "\n" +  
                           signedVersion + "\n" +  
                           signedResource + "\n" +
                           signedSnapshotTime + "\n" +
                           rscc + "\n" +
                           rscd + "\n" +  
                           rsce + "\n" +  
                           rscl + "\n" +  
                           rsct

6) For Service SAS, the string-to-sign format for authentication version 2020-02-10 is unchanged,
so please refer to [authentication version 2018-11-09 format](https://docs.microsoft.com/en-us/rest/api/storageservices/create-service-sas#version-2018-11-09-and-later).