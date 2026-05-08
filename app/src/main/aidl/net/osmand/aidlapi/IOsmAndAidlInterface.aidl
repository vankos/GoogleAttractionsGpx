package net.osmand.aidlapi;

import net.osmand.aidlapi.gpx.ASelectedGpxFile;
import net.osmand.aidlapi.gpx.AGpxFile;
import net.osmand.aidlapi.gpx.ImportGpxParams;

interface IOsmAndAidlInterface {
    // 1
    boolean addMapMarker(in Bundle params);
    // 2
    boolean removeMapMarker(in Bundle params);
    // 3
    boolean updateMapMarker(in Bundle params);
    // 4
    boolean addMapWidget(in Bundle params);
    // 5
    boolean removeMapWidget(in Bundle params);
    // 6
    boolean updateMapWidget(in Bundle params);
    // 7
    boolean addMapPoint(in Bundle params);
    // 8
    boolean removeMapPoint(in Bundle params);
    // 9
    boolean updateMapPoint(in Bundle params);
    // 10
    boolean addMapLayer(in Bundle params);
    // 11
    boolean removeMapLayer(in Bundle params);
    // 12
    boolean updateMapLayer(in Bundle params);
    // 13
    boolean importGpx(in ImportGpxParams params);
    // 14
    boolean showGpx(in Bundle params);
    // 15
    boolean hideGpx(in Bundle params);
    // 16
    boolean getActiveGpx(out List<ASelectedGpxFile> files);
    // 17
    boolean setMapLocation(in Bundle params);
    // 18
    boolean calculateRoute(in Bundle params);
    // 19
    boolean refreshMap();
    // 20
    boolean addFavoriteGroup(in Bundle params);
    // 21
    boolean removeFavoriteGroup(in Bundle params);
    // 22
    boolean updateFavoriteGroup(in Bundle params);
    // 23
    boolean addFavorite(in Bundle params);
    // 24
    boolean removeFavorite(in Bundle params);
    // 25
    boolean updateFavorite(in Bundle params);
    // 26
    boolean startGpxRecording(in Bundle params);
    // 27
    boolean stopGpxRecording(in Bundle params);
    // 28
    boolean takePhotoNote(in Bundle params);
    // 29
    boolean startVideoRecording(in Bundle params);
    // 30
    boolean startAudioRecording(in Bundle params);
    // 31
    boolean stopRecording(in Bundle params);
    // 32
    boolean navigate(in Bundle params);
    // 33
    boolean navigateGpx(inout Bundle params);
    // 34
    boolean removeGpx(in Bundle params);
    // 35
    boolean showMapPoint(in Bundle params);
    // 36
    boolean setNavDrawerItems(in Bundle params);
    // 37
    boolean pauseNavigation(in Bundle params);
    // 38
    boolean resumeNavigation(in Bundle params);
    // 39
    boolean stopNavigation(in Bundle params);
    // 40
    boolean muteNavigation(in Bundle params);
    // 41
    boolean unmuteNavigation(in Bundle params);
    // 42
    boolean search(in Bundle params, IBinder callback);
    // 43
    boolean navigateSearch(in Bundle params);
    // 44
    long registerForUpdates(in long updateTimeMS, IBinder callback);
    // 45
    boolean unregisterFromUpdates(in long callbackId);
    // 46
    boolean setNavDrawerLogo(in String imageUri);
    // 47
    boolean setEnabledIds(in List<String> ids);
    // 48
    boolean setDisabledIds(in List<String> ids);
    // 49
    boolean setEnabledPatterns(in List<String> patterns);
    // 50
    boolean setDisabledPatterns(in List<String> patterns);
    // 51
    boolean regWidgetVisibility(in Bundle params);
    // 52
    boolean regWidgetAvailability(in Bundle params);
    // 53
    boolean customizeOsmandSettings(in Bundle params);
    // 54
    boolean getImportedGpx(out List<AGpxFile> files);
    // 55
    boolean getSqliteDbFiles(out List<Bundle> files);
    // 56
    boolean getActiveSqliteDbFiles(out List<Bundle> files);
    // 57
    boolean showSqliteDbFile(String fileName);
    // 58
    boolean hideSqliteDbFile(String fileName);
    // 59
    boolean setNavDrawerLogoWithParams(in Bundle params);
    // 60
    boolean setNavDrawerFooterWithParams(in Bundle params);
    // 61
    boolean restoreOsmand();
    // 62
    boolean changePluginState(in Bundle params);
    // 63
    boolean registerForOsmandInitListener(IBinder callback);
    // 64
    boolean getBitmapForGpx(in Bundle file, IBinder callback);
    // 65
    int copyFile(in Bundle filePart);
    // 66
    long registerForNavigationUpdates(in Bundle params, IBinder callback);
    // 67
    long addContextMenuButtons(in Bundle params, IBinder callback);
    // 68
    boolean removeContextMenuButtons(in Bundle params);
    // 69
    boolean updateContextMenuButtons(in Bundle params);
    // 70
    boolean areOsmandSettingsCustomized(in Bundle params);
    // 71
    boolean setCustomization(in Bundle params);
    // 72
    long registerForVoiceRouterMessages(in Bundle params, IBinder callback);
    // 73
    boolean removeAllActiveMapMarkers(in Bundle params);
    // 74
    boolean importProfile(in Bundle params);
    // 75
    boolean executeQuickAction(in Bundle params);
    // 76
    boolean getQuickActionsInfo(out List<Bundle> quickActions);
    // 77
    boolean setLockState(in Bundle params);
    // 78
    long registerForKeyEvents(in Bundle params, IBinder callback);
    // 79
    Bundle getAppInfo();
    // 80
    boolean setMapMargins(in Bundle params);
    // 81
    boolean exportProfile(in Bundle params);
    // 82
    boolean isFragmentOpen();
    // 83
    boolean isMenuOpen();
    // 84
    int getPluginVersion(in Bundle params);
    // 85
    boolean selectProfile(in Bundle params);
    // 86
    boolean getProfiles(out List<Bundle> profiles);
    // 87
    boolean getBlockedRoads(out List<Bundle> blockedRoads);
    // 88
    boolean addRoadBlock(in Bundle params);
    // 89
    boolean removeRoadBlock(in Bundle params);
    // 90
    boolean setLocation(in Bundle params);
    // 91
    boolean exitApp(in Bundle params);
    // 92
    boolean getText(inout Bundle params);
    // 93
    boolean reloadIndexes();
    // 94
    boolean setPreference(in Bundle params);
    // 95
    boolean getPreference(inout Bundle params);
    // 96
    long registerForLogcatMessages(in Bundle params, IBinder callback);
    // 97
    boolean setZoomLimits(in Bundle params);
}
