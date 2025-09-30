package com.trtc.uikit.livekit.example.service;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;

import com.tencent.feedback.eup.CrashReport;
import com.tencent.feedback.eup.CrashStrategyBean;
import com.tencent.qcloud.tuicore.util.TUIBuild;
import com.trtc.uikit.livekit.example.BuildConfig;

public class BuglyService {
    private static final String TAG = "BuglyService";

    //appID fetch: https://bugly.woa.com/v2/setting/product?productId=83d861f1a0&pid=2
    private static final String BUGLY_APP_ID = "0b073ca48e";

    private static final Object  sLock   = new Object();
    private static       boolean sIsInit = false;

    private static final String BUGLY_TAG_DEBUG   = "Debug";
    private static final String BUGLY_TAG_RELEASE = "Release";

    private static String sAppVersion = "";

    private BuglyService() {
    }

    public static void init(Context context, String sdkVersion, String userId) {
        if (context == null) {
            Log.e(TAG, "init context is null");
            return;
        }
        if (sIsInit) {
            return;
        }
        synchronized (sLock) {
            if (sIsInit) {
                return;
            }
            sIsInit = true;
            Log.d(TAG, "start bugly service ");
            final Context appContext = context.getApplicationContext();
            String appVersion = getAppVersion(appContext);
            String productVersion = TextUtils.isEmpty(appVersion) ? sdkVersion : sdkVersion + "_" + appVersion;
            CrashReport.setProductVersion(appContext, productVersion);
            CrashReport.setUserId(appContext, userId);
            CrashReport.setAppChannel(appContext, BuildConfig.DEBUG ? BUGLY_TAG_DEBUG : BUGLY_TAG_RELEASE);
            CrashReport.setDeviceModel(appContext, TUIBuild.getModel());

            CrashStrategyBean crashStrategyBean = new CrashStrategyBean();
            crashStrategyBean.setEnableNativeCrashMonitor(true);
            crashStrategyBean.setEnableANRCrashMonitor(true);
            crashStrategyBean.setEnableCatchAnrTrace(true);
            crashStrategyBean.setEnableRecordAnrMainStack(true);

            CrashReport.initCrashReport(appContext, BUGLY_APP_ID, BuildConfig.DEBUG, crashStrategyBean);
        }
    }

    private static String getAppVersion(Context context) {
        if (TextUtils.isEmpty(sAppVersion)) {
            PackageManager packageManager = context.getPackageManager();
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
                sAppVersion = packageInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                sAppVersion = "";
            }
        }
        return sAppVersion;
    }
}
