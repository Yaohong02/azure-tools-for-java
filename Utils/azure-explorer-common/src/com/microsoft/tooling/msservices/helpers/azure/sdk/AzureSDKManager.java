/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.tooling.msservices.helpers.azure.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.microsoft.azure.management.compute.*;
import com.microsoft.azure.management.compute.implementation.ComputeManager;
import com.microsoft.azure.management.network.Network;
import com.microsoft.azure.management.network.PublicIPAddress;
import com.microsoft.azure.management.network.implementation.NetworkManager;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.fluentcore.model.Creatable;
import com.microsoft.azure.management.resources.implementation.ResourceManager;
import com.microsoft.azure.management.storage.implementation.StorageManager;
import com.microsoft.azure.toolkit.lib.applicationinsights.ApplicationInsight;
import com.microsoft.azure.toolkit.lib.applicationinsights.AzureApplicationInsights;
import com.microsoft.azure.toolkit.lib.applicationinsights.task.GetOrCreateApplicationInsightsTask;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.logging.Log;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.storage.StorageAccount;
import com.microsoft.azuretools.authmanage.IdeAzureAccount;
import com.microsoft.azuretools.azurecommons.helpers.NotNull;
import com.microsoft.azuretools.azurecommons.helpers.Nullable;
import com.microsoft.tooling.msservices.model.vm.VirtualNetwork;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AzureSDKManager {

    private static final String INSIGHTS_REGION_LIST_URL = "https://management.azure.com/providers/microsoft.insights?api-version=2015-05-01";

    public static VirtualMachine createVirtualMachine(String subscriptionId, @NotNull String name,
                                                      @NotNull String resourceGroup, boolean withNewResourceGroup,
                                                      @NotNull String size, @NotNull String region,
                                                      final VirtualMachineImage vmImage, Object knownImage,
                                                      boolean isKnownImage, final StorageAccount storageAccount,
                                                      final Network network, VirtualNetwork newNetwork,
                                                      boolean withNewNetwork, @NotNull String subnet,
                                                      @Nullable PublicIPAddress pip, boolean withNewPip,
                                                      @Nullable AvailabilitySet availabilitySet,
                                                      boolean withNewAvailabilitySet, @NotNull final String username,
                                                      @Nullable final String password, @Nullable String publicKey)
            throws Exception {
        final boolean isWindows;
        if (isKnownImage) {
            isWindows = knownImage instanceof KnownWindowsVirtualMachineImage;
        } else {
            isWindows = vmImage.osDiskImage().operatingSystem() == OperatingSystemTypes.WINDOWS;
        }
        // ------ Resource Group ------
        final ComputeManager.Configurable vc = ComputeManager.configure();
        final ComputeManager va = IdeAzureAccount.getInstance().authenticateForTrack1(subscriptionId, vc, (t, c) -> c.authenticate(t, subscriptionId));
        final VirtualMachine.DefinitionStages.WithGroup withGroup = va.virtualMachines().define(name).withRegion(region);
        Creatable<ResourceGroup> newResourceGroup = null;
        final VirtualMachine.DefinitionStages.WithNetwork withNetwork;
        if (withNewResourceGroup) {
            final ResourceManager.Configurable rc = ResourceManager.configure();
            final ResourceManager ra = IdeAzureAccount.getInstance().authenticateForTrack1(subscriptionId, rc, (t, c) -> c.authenticate(t).withSubscription(subscriptionId));
            newResourceGroup = ra.resourceGroups().define(resourceGroup).withRegion(region);
            withNetwork = withGroup.withNewResourceGroup(newResourceGroup);
        } else {
            withNetwork = withGroup.withExistingResourceGroup(resourceGroup);
        }
        // ------ Virtual Network -----
        final VirtualMachine.DefinitionStages.WithPublicIPAddress withPublicIpAddress;
        if (withNewNetwork) {
            final NetworkManager.Configurable nc = NetworkManager.configure();
            final NetworkManager na = IdeAzureAccount.getInstance().authenticateForTrack1(subscriptionId, nc, (t, c) -> c.authenticate(t, subscriptionId));
            final Network.DefinitionStages.WithGroup networkWithGroup = na.networks().define(newNetwork.name).withRegion(region);
            final Creatable<Network> newVirtualNetwork;
            if (withNewResourceGroup) {
                newVirtualNetwork = networkWithGroup.withNewResourceGroup(newResourceGroup)
                    .withAddressSpace(newNetwork.addressSpace)
                    .withSubnet(newNetwork.subnet.name, newNetwork.subnet.addressSpace);
            } else {
                newVirtualNetwork = networkWithGroup.withExistingResourceGroup(resourceGroup)
                    .withAddressSpace(newNetwork.addressSpace)
                    .withSubnet(newNetwork.subnet.name, newNetwork.subnet.addressSpace);
            }
            withPublicIpAddress = withNetwork.withNewPrimaryNetwork(newVirtualNetwork).withPrimaryPrivateIPAddressDynamic();
            //withPublicIpAddress = withNetwork.withNewPrimaryNetwork("10.0.0.0/28").
            //.withPrimaryPrivateIpAddressDynamic();
        } else {
            withPublicIpAddress = withNetwork.withExistingPrimaryNetwork(network)
                    .withSubnet(subnet)
                    .withPrimaryPrivateIPAddressDynamic();
        }
        // ------ Public IP Address------
        final VirtualMachine.DefinitionStages.WithOS withOS;
        if (pip == null) {
            if (withNewPip) {
                withOS = withPublicIpAddress.withNewPrimaryPublicIPAddress(name + "pip");
            } else {
                withOS = withPublicIpAddress.withoutPrimaryPublicIPAddress();
            }
        } else {
            withOS = withPublicIpAddress.withExistingPrimaryPublicIPAddress(pip);
        }
        // ------ OS and credentials -----
        VirtualMachine.DefinitionStages.WithCreate withCreate;
        if (isWindows) {
            final VirtualMachine.DefinitionStages.WithWindowsAdminUsernameManagedOrUnmanaged withWindowsAdminUsername;
            if (isKnownImage) {
                withWindowsAdminUsername = withOS.withPopularWindowsImage((KnownWindowsVirtualMachineImage) knownImage);
            } else {
                withWindowsAdminUsername = withOS.withSpecificWindowsImageVersion(vmImage.imageReference());
            }
            withCreate = withWindowsAdminUsername.withAdminUsername(username).withAdminPassword(password).withUnmanagedDisks();
        } else {
            final VirtualMachine.DefinitionStages.WithLinuxRootPasswordOrPublicKeyManagedOrUnmanaged withLinuxRootPasswordOrPublicKey;
            if (isKnownImage) {
                withLinuxRootPasswordOrPublicKey = withOS.withPopularLinuxImage((KnownLinuxVirtualMachineImage) knownImage).withRootUsername(username);
            } else {
                withLinuxRootPasswordOrPublicKey = withOS.withSpecificLinuxImageVersion(vmImage.imageReference()).withRootUsername(username);
            }
            VirtualMachine.DefinitionStages.WithLinuxCreateManagedOrUnmanaged withLinuxCreate;
            // we assume either password or public key is not empty
            if (password != null && !password.isEmpty()) {
                withLinuxCreate = withLinuxRootPasswordOrPublicKey.withRootPassword(password);
                if (publicKey != null) {
                    withLinuxCreate = withLinuxCreate.withSsh(publicKey);
                }
            } else {
                withLinuxCreate = withLinuxRootPasswordOrPublicKey.withSsh(publicKey);
            }
            withCreate = withLinuxCreate.withUnmanagedDisks();
        }
        withCreate = withCreate.withSize(size);
        // ---- Storage Account --------
        final StorageManager.Configurable sc = StorageManager.configure();
        final StorageManager sa = IdeAzureAccount.getInstance().authenticateForTrack1(subscriptionId, sc, (t, c) -> c.authenticate(t, subscriptionId));
        final com.microsoft.azure.management.storage.StorageAccount existedStorageAccount = sa.storageAccounts().getById(storageAccount.id());
        withCreate = withCreate.withExistingStorageAccount(existedStorageAccount);
        if (withNewAvailabilitySet) {
            withCreate = withCreate.withNewAvailabilitySet(name + "as");
        } else if (availabilitySet != null) {
            withCreate = withCreate.withExistingAvailabilitySet(availabilitySet);
        }
        return withCreate.create();
    }

    public static List<ApplicationInsight> getInsightsResources(@NotNull String subscriptionId) {
        return com.microsoft.azure.toolkit.lib.Azure.az(AzureApplicationInsights.class).applicationInsights(subscriptionId).list();
    }

    public static List<ApplicationInsight> getInsightsResources(@NotNull Subscription subscription) {
        return getInsightsResources(subscription.getId());
    }

    // SDK will return existing application insights component when you create new one with existing name
    // Use this method in case SDK service update their behavior
    public static ApplicationInsight getOrCreateApplicationInsights(@NotNull String subscriptionId,
                                                                    @NotNull String resourceGroupName,
                                                                    @NotNull String resourceName,
                                                                    @NotNull String location) throws IOException {
        return new GetOrCreateApplicationInsightsTask(subscriptionId, resourceGroupName, Region.fromName(location), resourceName).execute();
    }

    public static ApplicationInsight createInsightsResource(@NotNull String subscriptionId,
                                                                      @NotNull String resourceGroupName,
                                                                      @NotNull String resourceName,
                                                                      @NotNull String location) throws IOException {
        return getOrCreateApplicationInsights(subscriptionId, resourceGroupName, resourceName, location);
    }

    public static ApplicationInsight createInsightsResource(@NotNull Subscription subscription,
                                                                      @NotNull String resourceGroupName,
                                                                      boolean isNewGroup,
                                                                      @NotNull String resourceName,
                                                                      @NotNull String location) throws IOException {
        return createInsightsResource(subscription.getId(), resourceGroupName, resourceName, location);
    }

    public static List<String> getLocationsForInsights(String subscriptionId) throws IOException {
        final HttpGet request = new HttpGet(INSIGHTS_REGION_LIST_URL);
        final Subscription subscription = com.microsoft.azure.toolkit.lib.Azure.az(AzureAccount.class).account().getSubscription(subscriptionId);
        final String accessToken = IdeAzureAccount.getInstance().getAccessTokenForTrack1(subscription.getTenantId());
        request.setHeader("Authorization", String.format("Bearer %s", accessToken));
        final CloseableHttpResponse response = HttpClients.createDefault().execute(request);
        final InputStream responseStream = response.getEntity().getContent();
        try (final InputStreamReader isr = new InputStreamReader(responseStream)) {
            final Gson gson = new Gson();
            final JsonObject jsonContent = (gson).fromJson(isr, JsonObject.class);
            final JsonArray jsonResourceTypes = jsonContent.getAsJsonArray("resourceTypes");
            for (int i = 0; i < jsonResourceTypes.size(); ++i) {
                final Object obj = jsonResourceTypes.get(i);
                if (obj instanceof JsonObject) {
                    final JsonObject jsonResourceType = (JsonObject) obj;
                    final String resourceType = jsonResourceType.get("resourceType").getAsString();
                    if (resourceType.equalsIgnoreCase("components")) {
                        final JsonArray jsonLocations = jsonResourceType.getAsJsonArray("locations");
                        return gson.fromJson(jsonLocations, new ArrayList().getClass());
                    }
                }
            }
        } catch (final IOException | JsonParseException e) {
            Log.error(e);
        }
        return Collections.emptyList();
    }

    // TODO: AI SDK doesn't provide a method to list regions which are available regions to create AI,
    // we are requiring the SDK to provide that API, before SDK side fix, we will use our own impl
    public static List<String> getLocationsForInsights(Subscription subscription) throws IOException {
        return getLocationsForInsights(subscription.getId());
    }
}
