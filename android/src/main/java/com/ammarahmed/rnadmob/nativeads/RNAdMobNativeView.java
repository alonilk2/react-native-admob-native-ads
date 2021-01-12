package com.ammarahmed.rnadmob.nativeads;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.CatalystInstance;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.formats.MediaView;
import com.google.android.gms.ads.formats.NativeAdOptions;
import com.google.android.gms.ads.formats.UnifiedNativeAd;
import com.google.android.gms.ads.formats.UnifiedNativeAdView;

import java.util.UUID;

public class RNAdMobNativeView extends LinearLayout {

    private final Runnable measureAndLayout = new Runnable() {
        @Override
        public void run() {
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        }
    };
    public int adRefreshInterval = 60000;
    public boolean usingPreloadedAd = false;

    ReactContext mContext;
    UnifiedNativeAdView nativeAdView;
    UnifiedNativeAd unifiedNativeAd;
    MediaView mediaView;
    String adType = "simple";
    boolean preloadingAds = true;

    protected @Nullable
    String messagingModuleName;


    AdListener adListener = new AdListener() {
        @Override
        public void onAdFailedToLoad(int i) {
            super.onAdFailedToLoad(i);
            String errorMessage = "Unknown error";
            switch (i) {
                case AdRequest.ERROR_CODE_INTERNAL_ERROR:
                    errorMessage = "Internal error, an invalid response was received from the ad server.";
                    break;
                case AdRequest.ERROR_CODE_INVALID_REQUEST:
                    errorMessage = "Invalid ad request, possibly an incorrect ad unit ID was given.";
                    break;
                case AdRequest.ERROR_CODE_NETWORK_ERROR:
                    errorMessage = "The ad request was unsuccessful due to network connectivity.";
                    break;
                case AdRequest.ERROR_CODE_NO_FILL:
                    errorMessage = "The ad request was successful, but no ad was returned due to lack of ad inventory.";
                    break;
            }
            WritableMap event = Arguments.createMap();
            WritableMap error = Arguments.createMap();
            error.putString("message", errorMessage);
            event.putMap("error", error);
            sendEvent(RNAdMobNativeViewManager.EVENT_AD_FAILED_TO_LOAD, event);

            if (handler != null) {
                runnable = new Runnable() {
                    @Override
                    public void run() {
                        reloadAd();
                    }
                };
                handler.postDelayed(runnable, adRefreshInterval);
            }
        }

        @Override
        public void onAdFailedToLoad(LoadAdError loadAdError) {
            super.onAdFailedToLoad(loadAdError);

            WritableMap event = Arguments.createMap();
            WritableMap error = Arguments.createMap();
            if (loadAdError.getMessage() != null) {
                error.putString("message", loadAdError.getMessage());
            } else {
                error.putString("message", "unknown error occurred.");
            }
            event.putMap("error", error);
            sendEvent(RNAdMobNativeViewManager.EVENT_AD_FAILED_TO_LOAD, event);

        }

        @Override
        public void onAdClosed() {
            super.onAdClosed();
            sendEvent(RNAdMobNativeViewManager.EVENT_AD_CLOSED, null);
        }

        @Override
        public void onAdOpened() {
            super.onAdOpened();
            sendEvent(RNAdMobNativeViewManager.EVENT_AD_OPENED, null);
        }

        @Override
        public void onAdClicked() {
            super.onAdClicked();
            sendEvent(RNAdMobNativeViewManager.EVENT_AD_CLICKED, null);

        }

        @Override
        public void onAdLoaded() {
            super.onAdLoaded();
            sendEvent(RNAdMobNativeViewManager.EVENT_AD_LOADED, null);
        }

        @Override
        public void onAdImpression() {
            super.onAdImpression();
            sendEvent(RNAdMobNativeViewManager.EVENT_AD_IMPRESSION, null);
        }

        @Override
        public void onAdLeftApplication() {
            super.onAdLeftApplication();
            sendEvent(RNAdMobNativeViewManager.EVENT_AD_LEFT_APPLICATION, null);
        }
    };

    private Handler handler;
    UnifiedNativeAd.OnUnifiedNativeAdLoadedListener onUnifiedNativeAdLoadedListener = new UnifiedNativeAd.OnUnifiedNativeAdLoadedListener() {
        @Override
        public void onUnifiedNativeAdLoaded(UnifiedNativeAd nativeAd) {

            if (usingPreloadedAd) return;
            if (unifiedNativeAd != null) {
                unifiedNativeAd.destroy();
            }
            if (nativeAd != null) {

                unifiedNativeAd = nativeAd;
                nativeAdView.setNativeAd(unifiedNativeAd);
                if (mediaView != null) {
                    nativeAdView.setMediaView(mediaView);
                    mediaView.requestLayout();
                }

            }

            setNativeAdToJS(nativeAd);
        }
    };


    public RNAdMobNativeView(ReactContext context) {
        super(context);
        mContext = context;
        createView(context);
        handler = new Handler();
        mCatalystInstance = mContext.getCatalystInstance();
        setId(UUID.randomUUID().hashCode() + this.getId());
    }

    public void createView(Context context) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View viewRoot = layoutInflater.inflate(R.layout.rn_ad_unified_native_ad, this, true);
        nativeAdView = (UnifiedNativeAdView) viewRoot.findViewById(R.id.native_ad_view);

    }

    public void addMediaView(int id) {

        try {
            RNMediaView adMediaView = (RNMediaView) nativeAdView.findViewById(id);
            if (adMediaView != null) {
                nativeAdView.setMediaView(adMediaView);
                adMediaView.requestLayout();
            }
        } catch (Exception e) {

        }
    }

    private Runnable runnable;

    private void setNativeAdToJS(final UnifiedNativeAd nativeAd) {

        try {
            WritableMap args = Arguments.createMap();
            args.putString("headline", nativeAd.getHeadline());
            args.putString("tagline", nativeAd.getBody());
            args.putString("advertiser", nativeAd.getAdvertiser());
            args.putString("callToAction", nativeAd.getCallToAction());
            args.putBoolean("video", nativeAd.getMediaContent().hasVideoContent());
            args.putString("price", nativeAd.getPrice());
            if (nativeAd.getStore() != null) {
                args.putString("store", nativeAd.getStore());
            }
            if (nativeAd.getStarRating() != null) {
                args.putInt("rating", nativeAd.getStarRating().intValue());
            }

            float aspectRatio = 1.0f;


            if (nativeAd.getResponseInfo().getMediationAdapterClassName().equals("com.google.ads.mediation.admob.AdMobAdapter")) {
                if (nativeAd.getMediaContent() != null) {
                    aspectRatio = nativeAd.getMediaContent().getAspectRatio();

                    if (aspectRatio > 0) {
                        args.putString("aspectRatio", String.valueOf(aspectRatio));
                    } else {
                        args.putString("aspectRatio", String.valueOf(1.0f));
                    }

                }
            } else {
                args.putString("aspectRatio", String.valueOf(1.0f));
            }


            WritableArray images = new WritableNativeArray();

            if (nativeAd.getImages() != null && nativeAd.getImages().size() > 0) {

                for (int i = 0; i < nativeAd.getImages().size(); i++) {
                    WritableMap map = Arguments.createMap();
                    if (nativeAd.getImages().get(i) != null) {
                        map.putString("url", nativeAd.getImages().get(i).getUri().toString());
                        map.putInt("width", nativeAd.getImages().get(i).getWidth());
                        map.putInt("height", nativeAd.getImages().get(i).getHeight());
                        images.pushMap(map);
                    }
                }
            }

            if (images != null) {
                args.putArray("images", images);
            } else {
                args.putArray("images", null);
            }

            if (nativeAd.getIcon() != null) {
                args.putString("icon", nativeAd.getIcon().getUri().toString());

            } else {
                if (nativeAd.getResponseInfo() != null && nativeAd.getResponseInfo().getMediationAdapterClassName() != null) {
                    if (nativeAd.getResponseInfo().getMediationAdapterClassName().equals("com.google.ads.mediation.admob.AdMobAdapter")) {
                        args.putString("icon", "noicon");
                    } else {
                        args.putString("icon", "empty");
                    }
                } else {
                    args.putString("icon", "noicon");
                }

            }

            sendDirectMessage(args);

        } catch (Exception ignored) {
        }
        if (handler != null) {
            runnable = new Runnable() {
                @Override
                public void run() {

                }
            };
            handler.postDelayed(runnable, adRefreshInterval);
        }
    }

    private void reloadAd() {

        if (adType == "video") {
            if (preloadingAds) {
                loadPreloadedVideoAd();
            } else {
                loadAd(RNAdMobGlobals.preloader.videoAdUnitID);
            }
        } else {
            if (preloadingAds) {
                loadPreloadedAd();
            } else {
                loadAd(RNAdMobGlobals.preloader.adUnitID);
            }

        }
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }


    public void setMessagingModuleName(String moduleName) {
        messagingModuleName = moduleName;
    }

    CatalystInstance mCatalystInstance;

    protected void sendDirectMessage(WritableMap data) {

        WritableNativeMap event = new WritableNativeMap();
        event.putMap("nativeEvent", data);
        WritableNativeArray params = new WritableNativeArray();
        params.pushMap(event);

        if (mCatalystInstance != null) {
            mCatalystInstance.callFunction(messagingModuleName, "onUnifiedNativeAdLoaded", params);
        }

    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    public void sendEvent(String name, @Nullable WritableMap event) {

        ReactContext reactContext = (ReactContext) mContext;
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                name,
                event);
    }


    /**
     * Load a preloaded Ad if present, or request for a new ad from server if the preloader is empty.
     */

    public void loadPreloadedAd() {


        UnifiedNativeAd nativeAd = RNAdMobGlobals.preloader.getNativeAd();
        if (nativeAd != null) {
            unifiedNativeAd = nativeAd;
            nativeAdView.setNativeAd(unifiedNativeAd);
            setNativeAdToJS(unifiedNativeAd);
            usingPreloadedAd = true;
        } else {
            loadAd(RNAdMobGlobals.preloader.adUnitID);
        }
    }

    /**
     * Load a preloaded Video Ad if present, or request for a new video ad from server if the preloader is empty.
     */

    public void loadPreloadedVideoAd() {

        UnifiedNativeAd nativeAd = RNAdMobGlobals.preloader.getNativeVideoAds();
        if (nativeAd != null) {
            unifiedNativeAd = nativeAd;
            nativeAdView.setNativeAd(unifiedNativeAd);
            setNativeAdToJS(unifiedNativeAd);
            usingPreloadedAd = true;
        } else {
            loadAd(RNAdMobGlobals.preloader.videoAdUnitID);
        }
    }


    /**
     * Request a new Ad from server. All the configuration for the Ad is taken from the RNAdMobPreloader class.
     */

    public void loadAd(String adId) {
        try {
            AdLoader.Builder builder = new AdLoader.Builder(mContext, adId);
            builder.forUnifiedNativeAd(onUnifiedNativeAdLoadedListener);

            VideoOptions videoOptions = new VideoOptions.Builder()
                    .setStartMuted(RNAdMobGlobals.preloader.videosStartMuted)
                    .build();
            NativeAdOptions adOptions = new NativeAdOptions.Builder()
                    .setVideoOptions(videoOptions)
                    .setAdChoicesPlacement(RNAdMobGlobals.preloader.adChoicesPlacement)
                    .setMediaAspectRatio(RNAdMobGlobals.preloader.mediaAspectRatio)
                    .build();
            builder.withNativeAdOptions(adOptions);
            AdLoader adLoader = builder.withAdListener(adListener)
                    .build();
            AdRequest adRequest;

            if (RNAdMobGlobals.preloader.requestNonPersonalizedAdsOnly) {
                Bundle extras = new Bundle();
                extras.putString("npa", "1");
                adRequest = new AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter.class, extras).build();
            } else {
                adRequest = new AdRequest.Builder().build();
            }

            adLoader.loadAd(adRequest);

        } catch (Exception e) {
        }
    }


    public void addNewView(View child, int index) {
        try {
            nativeAdView.addView(child, index);
            requestLayout();
            nativeAdView.requestLayout();
        } catch (Exception e) {

        }

    }

    @Override
    public void addView(View child) {
        super.addView(child);
        requestLayout();
    }

    public void setAdType(String type) {
        adType = type;
    }

    public void setPreloadingMode(boolean preloading) {
        preloadingAds = preloading;
    }


    public void setAdRefreshInterval(int interval) {
        adRefreshInterval = interval;
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        post(measureAndLayout);
    }

    public void removeHandler() {
        if (handler != null) {
            handler.removeCallbacks(runnable);
            handler = null;
        }
    }
}