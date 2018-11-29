/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.signedconfig;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Slog;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

class SignedConfigApplicator {

    private static final String TAG = "SignedConfig";

    private static final Set<String> ALLOWED_KEYS = Collections.unmodifiableSet(new ArraySet<>(
            Arrays.asList(
                    Settings.Global.HIDDEN_API_POLICY,
                    Settings.Global.HIDDEN_API_BLACKLIST_EXEMPTIONS
            )));

    private final Context mContext;
    private final String mSourcePackage;

    SignedConfigApplicator(Context context, String sourcePackage) {
        mContext = context;
        mSourcePackage = sourcePackage;
    }

    private boolean checkSignature(String data, String signature) {
        Slog.w(TAG, "SIGNATURE CHECK NOT IMPLEMENTED YET!");
        return false;
    }

    private int getCurrentConfigVersion() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.SIGNED_CONFIG_VERSION, 0);
    }

    private void updateCurrentConfig(int version, Map<String, String> values) {
        for (Map.Entry<String, String> e: values.entrySet()) {
            Settings.Global.putString(
                    mContext.getContentResolver(),
                    e.getKey(),
                    e.getValue());
        }
        Settings.Global.putInt(
                mContext.getContentResolver(), Settings.Global.SIGNED_CONFIG_VERSION, version);
    }


    void applyConfig(String configStr, String signature) {
        if (!checkSignature(configStr, signature)) {
            Slog.e(TAG, "Signature check on signed configuration in package " + mSourcePackage
                    + " failed; ignoring");
            return;
        }
        SignedConfig config;
        try {
            config = SignedConfig.parse(configStr, ALLOWED_KEYS);
        } catch (InvalidConfigException e) {
            Slog.e(TAG, "Failed to parse config from package " + mSourcePackage, e);
            return;
        }
        int currentVersion = getCurrentConfigVersion();
        if (currentVersion >= config.version) {
            Slog.i(TAG, "Config from package " + mSourcePackage + " is older than existing: "
                    + config.version + "<=" + currentVersion);
            return;
        }
        // We have new config!
        Slog.i(TAG, "Got new signed config from package " + mSourcePackage + ": version "
                + config.version + " replacing existing version " + currentVersion);
        SignedConfig.PerSdkConfig matchedConfig =
                config.getMatchingConfig(Build.VERSION.SDK_INT);
        if (matchedConfig == null) {
            Slog.i(TAG, "Config is not applicable to current SDK version; ignoring");
            return;
        }

        Slog.i(TAG, "Updating signed config to version " + config.version);
        updateCurrentConfig(config.version, matchedConfig.values);
    }
}
