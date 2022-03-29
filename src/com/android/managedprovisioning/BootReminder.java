/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.managedprovisioning;

import android.app.AppGlobals;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.CrossProfileAppsPregrantControllerProvider;
import com.android.managedprovisioning.common.EncryptionControllerProvider;
import com.android.managedprovisioning.common.ManagedProfileChecker;
import com.android.managedprovisioning.common.UserProvisioningStateHelperProvider;
import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;
import com.android.managedprovisioning.manageduser.ManagedUserRemovalListener;
import com.android.managedprovisioning.manageduser.ManagedUserRemovalUtils;

/**
 * Boot listener for triggering reminders at boot time.
 */
public class BootReminder extends BroadcastReceiver {
    private static final String PROFILE_OWNER_PACKAGE = "com.example.android.basicmanagedprofile";
    private static final String PROFILE_OWNER_CLASS = PROFILE_OWNER_PACKAGE
            + ".BasicDeviceAdminReceiver";

    private final ManagedProfileChecker mManagedProfileChecker;
    private final UserProvisioningStateHelperProvider mUserProvisioningStateHelperProvider;
    private final EncryptionControllerProvider mEncryptionControllerProvider;
    private final CrossProfileAppsPregrantControllerProvider
            mCrossProfileAppsPregrantControllerProvider;
    private final ManagedUserRemovalUtils mManagedUserRemovalUtils;

    public BootReminder() {
        this(
                ManagedProfileChecker.DEFAULT,
                UserProvisioningStateHelperProvider.DEFAULT,
                EncryptionControllerProvider.DEFAULT,
                CrossProfileAppsPregrantControllerProvider.DEFAULT,
                new ManagedUserRemovalUtils());
    }

    @VisibleForTesting
    public BootReminder(
            ManagedProfileChecker managedProfileChecker,
            UserProvisioningStateHelperProvider userProvisioningStateHelperProvider,
            EncryptionControllerProvider encryptionControllerProvider,
            CrossProfileAppsPregrantControllerProvider crossProfileAppsPregrantControllerProvider,
            ManagedUserRemovalUtils managedUserRemovalUtils) {
        mManagedProfileChecker = managedProfileChecker;
        mUserProvisioningStateHelperProvider = userProvisioningStateHelperProvider;
        mEncryptionControllerProvider = encryptionControllerProvider;
        mCrossProfileAppsPregrantControllerProvider = crossProfileAppsPregrantControllerProvider;
        mManagedUserRemovalUtils = managedUserRemovalUtils;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        UserManager um = context.getSystemService(UserManager.class);
        UserHandle userHandle = Process.myUserHandle();
        if (um.isManagedProfile(userHandle.getIdentifier())
                && dpm.getProfileOwnerAsUser(userHandle) == null) {
            try {
                Settings.Secure.putIntForUser(context.getContentResolver(),
                        Settings.Secure.USER_SETUP_COMPLETE, 0,
                        userHandle.getIdentifier());
                dpm.forceUpdateUserSetupComplete(userHandle.getIdentifier());
                AppGlobals.getPackageManager().getPackageInstaller()
                        .installExistingPackage(PROFILE_OWNER_PACKAGE,
                                PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
                                PackageManager.INSTALL_REASON_UNKNOWN, null,
                                userHandle.getIdentifier(), null);
                dpm.setActiveAdmin(new ComponentName(PROFILE_OWNER_PACKAGE,
                                PROFILE_OWNER_CLASS), true,
                        userHandle.getIdentifier());
                dpm.setProfileOwner(new ComponentName(PROFILE_OWNER_PACKAGE,
                                PROFILE_OWNER_CLASS), "CalyxOS",
                        userHandle.getIdentifier());
                dpm.setUserProvisioningState(DevicePolicyManager.STATE_USER_SETUP_FINALIZED,
                        userHandle.getIdentifier());
                Settings.Secure.putIntForUser(context.getContentResolver(),
                        Settings.Secure.USER_SETUP_COMPLETE, 1,
                        userHandle.getIdentifier());
                dpm.forceUpdateUserSetupComplete(userHandle.getIdentifier());
            } catch (RemoteException e) {
                Slog.e(BootReminder.class.getSimpleName(),
                        "Failed to install profile owner in unmanaged profiles", e);
            }
        }
        mCrossProfileAppsPregrantControllerProvider
                .createCrossProfileAppsPregrantController(context)
                        .checkCrossProfileAppsPermissions();

        // For encryption flows during setup wizard, this acts as a backup to
        // PostEncryptionActivity in case the PackageManager has not yet written the package state
        // to disk when the reboot is triggered.
        mEncryptionControllerProvider.createEncryptionController(context).resumeProvisioning();

        resetPrimaryUserProvisioningStateIfNecessary(context);
    }

    /**
     * Resets the primary user provisioning state if a work profile was removed, but the state
     * hasn't been updated by {@link ManagedUserRemovalListener}.
     *
     * <p>This can happen if the device gets rebooted after removing the work profile, but before
     * {@link ManagedUserRemovalListener} receives the {@link Intent#ACTION_USER_REMOVED}
     * broadcast.
     */
    private void resetPrimaryUserProvisioningStateIfNecessary(Context context) {
        if (mManagedProfileChecker.hasManagedProfile(context)) {
            return;
        }
        UserProvisioningStateHelper userProvisioningStateHelper =
                mUserProvisioningStateHelperProvider.createUserProvisioningStateHelper(context);
        mManagedUserRemovalUtils
                .resetPrimaryUserProvisioningStateIfNecessary(context, userProvisioningStateHelper);
    }
}

