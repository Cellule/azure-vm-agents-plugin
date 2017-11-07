package com.microsoft.azure.vmagent.util;

import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.Provider;
import com.microsoft.azure.management.resources.ProviderResourceType;
import hudson.util.TimeUnit2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LocationCache {
    private static Map<String, Set<String>> regions = new HashMap<>();
    private static final long EXPIRE_TIME_IN_MILLIS = TimeUnit2.HOURS.toMillis(24); //re-get locations every 24 hours
    private static Map<String, Long> achieveTimeInMillis = new HashMap<>();

    public static Set<String> getLocation(Azure azureClient, String serviceManagementURL) throws Exception {
        if (regions.containsKey(serviceManagementURL)
                && !regions.get(serviceManagementURL).isEmpty()
                && System.currentTimeMillis() < achieveTimeInMillis.get(serviceManagementURL) + EXPIRE_TIME_IN_MILLIS) {
            return regions.get(serviceManagementURL);
        } else {
            synchronized (LocationCache.class) {
                if (regions.containsKey(serviceManagementURL)
                        && !regions.get(serviceManagementURL).isEmpty()
                        && System.currentTimeMillis()
                            < achieveTimeInMillis.get(serviceManagementURL) + EXPIRE_TIME_IN_MILLIS) {
                    return regions.get(serviceManagementURL);
                } else {
                    Set<String> locations = new HashSet<>();
                    PagedList<Provider> providers = azureClient.providers().list();
                    for (Provider provider : providers) {
                        List<ProviderResourceType> resourceTypes = provider.resourceTypes();
                        for (ProviderResourceType resourceType : resourceTypes) {
                            if (!resourceType.resourceType().equalsIgnoreCase("virtualMachines")) {
                                continue;
                            }

                            for (String location : resourceType.locations()) {
                                if (!locations.contains(location)) {
                                    try {
                                        if (!azureClient.virtualMachines().sizes().listByRegion(location).isEmpty()) {
                                            locations.add(location);
                                        }
                                    } catch (Exception e) {
                                        // some of the provider regions might not be valid for other API calls.
                                        // The SDK call will throw an exception instead of returning an empty list
                                    }
                                }
                            }

                        }
                    }
                    achieveTimeInMillis.put(serviceManagementURL, System.currentTimeMillis());
                    regions.put(serviceManagementURL, locations);
                    return locations;
                }
            }
        }
    }

    private LocationCache() {

    }
}
