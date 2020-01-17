/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.util.rule;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestStabilityRule implements TestRule {
    private static final String TAG = "TestStabilityRule";
    private static final Pattern LAUNCHER_BUILD =
            Pattern.compile("^("
                    + "(?<local>(BuildFromAndroidStudio|"
                    + "([0-9]+|[A-Z])-eng\\.[a-z]+\\.[0-9]+\\.[0-9]+))|"
                    + "(?<presubmit>([0-9]+|[A-Z])-P[0-9]+)|"
                    + "(?<postsubmit>([0-9]+|[A-Z])-[0-9]+)|"
                    + "(?<platform>[0-9]+|[A-Z])"
                    + ")$");
    private static final Pattern PLATFORM_BUILD =
            Pattern.compile("^("
                    + "(?<commandLine>eng\\.[a-z]+\\.[0-9]+\\.[0-9]+)|"
                    + "(?<presubmit>P[0-9]+)|"
                    + "(?<postsubmit>[0-9]+)"
                    + ")$");

    public static final int LOCAL = 0x1;
    public static final int UNBUNDLED_PRESUBMIT = 0x2;
    public static final int UNBUNDLED_POSTSUBMIT = 0x4;
    public static final int PLATFORM_PRESUBMIT = 0x8;
    public static final int PLATFORM_POSTSUBMIT = 0x10;

    public static final int RUN_FLAFOR = getRunFlavor();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Stability {
        int flavors();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        final Stability stability = description.getAnnotation(Stability.class);
        if (stability != null) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    if ((stability.flavors() & RUN_FLAFOR) != 0) {
                        Log.d(TAG, "Running " + description.getDisplayName());
                        base.evaluate();
                    } else {
                        Log.d(TAG, "Skipping " + description.getDisplayName());
                    }
                }
            };
        } else {
            return base;
        }
    }

    private static int getRunFlavor() {
        final String flavorOverride = InstrumentationRegistry.getArguments().getString("flavor");

        if (flavorOverride != null) {
            Log.d(TAG, "Flavor override: " + flavorOverride);
            try {
                return (int) TestStabilityRule.class.getField(flavorOverride).get(null);
            } catch (NoSuchFieldException e) {
                throw new AssertionError("Unrecognized run flavor override: " + flavorOverride);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        final String launcherVersion;
        try {
            launcherVersion = getInstrumentation().
                    getContext().
                    getPackageManager().
                    getPackageInfo(
                            UiDevice.getInstance(getInstrumentation()).
                                    getLauncherPackageName(),
                            0).
                    versionName;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        final String platformVersion = Build.VERSION.INCREMENTAL;

        Log.d(TAG, "Launcher: " + launcherVersion + ", platform: " + platformVersion);

        final Matcher launcherBuildMatcher = LAUNCHER_BUILD.matcher(launcherVersion);
        if (!launcherBuildMatcher.find()) {
            throw new AssertionError("Launcher build match not found");
        }

        final Matcher platformBuildMatcher = PLATFORM_BUILD.matcher(platformVersion);
        if (!platformBuildMatcher.find()) {
            throw new AssertionError("Platform build match not found");
        }

        final int runFlavor;

        if (launcherBuildMatcher.group("local") != null && (
                platformBuildMatcher.group("commandLine") != null ||
                        platformBuildMatcher.group("postsubmit") != null)) {
            Log.d(TAG, "LOCAL RUN");
            runFlavor = LOCAL;
        } else if (launcherBuildMatcher.group("presubmit") != null
                && platformBuildMatcher.group("postsubmit") != null) {
            Log.d(TAG, "UNBUNDLED PRESUBMIT");
            runFlavor = UNBUNDLED_PRESUBMIT;
        } else if (launcherBuildMatcher.group("postsubmit") != null
                && platformBuildMatcher.group("postsubmit") != null) {
            Log.d(TAG, "UNBUNDLED POSTSUBMIT");
            runFlavor = UNBUNDLED_POSTSUBMIT;
        } else if (launcherBuildMatcher.group("platform") != null
                && platformBuildMatcher.group("presubmit") != null) {
            Log.d(TAG, "PLATFORM PRESUBMIT");
            runFlavor = PLATFORM_PRESUBMIT;
        } else if (launcherBuildMatcher.group("platform") != null
                && platformBuildMatcher.group("postsubmit") != null) {
            Log.d(TAG, "PLATFORM POSTSUBMIT");
            runFlavor = PLATFORM_POSTSUBMIT;
        } else {
            throw new AssertionError("Unrecognized run flavor");
        }

        return runFlavor;
    }
}