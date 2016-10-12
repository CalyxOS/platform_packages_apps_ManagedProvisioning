/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.analytics;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.List;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-tests for {@link AnalyticsUtils}.
 */
@SmallTest
public class AnalyticsUtilsTest extends AndroidTestCase {
    private static final String INVALID_PACKAGE_NAME = "invalid-package-name";
    private static final String VALID_PACKAGE_NAME = "valid-package-name";
    private static final String VALID_INSTALLER_PACKAGE = "valid-installer-package";
    private static final String INVALID_PROVISIONING_EXTRA = "invalid-provisioning-extra";

    @Mock private Context mockContext;
    @Mock private PackageManager mockPackageManager;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
    }

    public void testGetInstallerPackageName_invalidPackage() {
        // WHEN getting installer package name for an invalid package.
        when(mockPackageManager.getInstallerPackageName(INVALID_PACKAGE_NAME))
                .thenThrow(new IllegalArgumentException());
        // THEN null should be returned and exception should be digested.
        assertNull(AnalyticsUtils.getInstallerPackageName(mockContext, INVALID_PACKAGE_NAME));
    }

    public void testGetInstallerPackageName_validPackage() {
        // WHEN getting installer package name for a valid package.
        when(mockPackageManager.getInstallerPackageName(VALID_PACKAGE_NAME))
                .thenReturn(VALID_INSTALLER_PACKAGE);
        // THEN valid installer package name should be returned.
        assertEquals(VALID_INSTALLER_PACKAGE,
                AnalyticsUtils.getInstallerPackageName(mockContext, VALID_PACKAGE_NAME));
    }

    public void testGetAllProvisioningExtras_NullIntent() {
        // WHEN getting provisioning extras using null Intent.
        List<String> provisioningExtras = AnalyticsUtils.getAllProvisioningExtras(null);
        // THEN an empty list of valid provisioning extras should be returned.
        assertEquals(0, provisioningExtras.size());
    }

    public void testGetAllProvisioningExtras_NullExtras() {
        // GIVEN intent has null extras
        Intent intent = new Intent();
        // WHEN getting provisioning extras with null extras
        List<String> provisioningExtras = AnalyticsUtils.getAllProvisioningExtras(intent);
        // THEN an empty list of valid provisioning extras should be returned.
        assertEquals(0, provisioningExtras.size());
    }

    public void testGetAllProvisioningExtras() {
        // GIVEN intent with both valid and invalid provisioning extras
        Intent intent = new Intent();
        intent.putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, "");
        intent.putExtra(INVALID_PROVISIONING_EXTRA, "");
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, "");
        // WHEN getting provisioning extras using the intent
        List<String> provisioningExtras = AnalyticsUtils.getAllProvisioningExtras(intent);
        // THEN an empty list of valid provisioning extras should be returned.
        assertEquals(2, provisioningExtras.size());
        provisioningExtras.contains(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME);
        provisioningExtras.contains(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE);
    }
}
