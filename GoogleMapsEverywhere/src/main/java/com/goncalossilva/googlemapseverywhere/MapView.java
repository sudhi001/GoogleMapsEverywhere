package com.goncalossilva.googlemapseverywhere;

import com.goncalossilva.googlemapseverywhere.model.CameraPosition;
import com.goncalossilva.googlemapseverywhere.model.JavaScriptBridge;
import com.goncalossilva.googlemapseverywhere.model.LatLng;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.AttributeSet;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MapView extends FrameLayout {
    private static final GoogleMapOptions DEFAULT_MAP_OPTIONS =
            new GoogleMapOptions()
                    .camera(new CameraPosition.Builder().target(new LatLng(0f, 0f)).zoom(2)
                            .build());

    private GoogleMapView mGoogleMapView;

    public MapView(Context context) {
        super(context);
        addInnerView(context, DEFAULT_MAP_OPTIONS);
    }

    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        addInnerView(context, DEFAULT_MAP_OPTIONS);
    }

    public MapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        addInnerView(context, DEFAULT_MAP_OPTIONS);
    }

    public MapView(Context context, GoogleMapOptions options) {
        super(context);
        addInnerView(context, options);
    }

    private void addInnerView(Context context, GoogleMapOptions options) {
        mGoogleMapView = new GoogleMapView(context, options);
        addView(mGoogleMapView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public GoogleMap getMap() {
        return mGoogleMapView.getMap();
    }

    private static class GoogleMapView extends WebView {

        private static final String MAP_ID = "map-canvas";

        private GoogleMap mGoogleMap;

        public GoogleMapView(Context context, GoogleMapOptions options) {
            super(context);

            WebSettings settings = getSettings();

            // Enable JavaScript.
            settings.setJavaScriptEnabled(true);

            // Automatically grant location permissions.
            settings.setGeolocationEnabled(true);
            setWebChromeClient(new WebChromeClient() {
                @Override
                public void onGeolocationPermissionsShowPrompt(String origin,
                        GeolocationPermissions.Callback callback) {
                    callback.invoke(origin, true, false);
                }
            });

            // Extract API_KEY from meta-data.
            String apiKey;
            try {
                ApplicationInfo applicationInfo =
                        context.getPackageManager().getApplicationInfo(
                                context.getPackageName(),
                                PackageManager.GET_META_DATA);
                apiKey = applicationInfo.metaData.getString("com.google.android.maps.v3.API_KEY");
            } catch (NullPointerException | PackageManager.NameNotFoundException e) {
                throw new IllegalStateException(
                        "com.google.android.maps.v3.API_KEY must be set in AndroidManifest.xml");
            }

            // Bind JavaScript interface.
            JavaScriptInterface javaScriptInterface = new JavaScriptInterface(apiKey);
            addJavascriptInterface(javaScriptInterface, "JavaScriptInterface");

            // Load the HTML.
            loadUrl("file:///android_asset/base_map.html");

            // Create GoogleMap object.
            mGoogleMap = new GoogleMap(context.getApplicationContext(), javaScriptInterface, MAP_ID, options);
        }

        public GoogleMap getMap() {
            return mGoogleMap;
        }

        private class JavaScriptInterface implements JavaScriptBridge {

            private String mApiKey;

            private boolean mInitialized = false;

            private List<String> mPendingScripts = new ArrayList<>();

            private GoogleMap.OnMapLoadedCallback mOnMapLoadedCallback;

            private GoogleMap.OnMapClickListener mOnMapClickListener;

            private GoogleMap.OnMapLongClickListener mOnMapLongClickListener;

            public JavaScriptInterface(String apiKey) {
                mApiKey = apiKey;
            }

            @JavascriptInterface
            public String getApiKey() {
                return mApiKey;
            }

            @JavascriptInterface
            public String getMapId() {
                return MAP_ID;
            }

            @JavascriptInterface
            public synchronized void setInitialized() {
                if (mPendingScripts.size() == 0) {
                    mInitialized = true;
                } else {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            mInitialized = true;

                            Iterator<String> it = mPendingScripts.iterator();
                            while (it.hasNext()) {
                                evaluateJavascript(it.next());
                                it.remove();
                            }
                        }
                    });
                }
            }

            @Override
            public synchronized void evaluateJavascript(String script) {
                if (mInitialized) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        GoogleMapView.this.evaluateJavascript(script, null);
                    } else {
                        loadUrl("javascript:" + script);
                    }
                } else {
                    mPendingScripts.add(script);
                }
            }

            @JavascriptInterface
            public void onMapLoaded() {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (mOnMapLoadedCallback != null) {
                            mOnMapLoadedCallback.onMapLoaded();
                        }
                    }
                });
            }

            @JavascriptInterface
            public void onMapClick(final double latitude, final double longitude) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (mOnMapClickListener != null) {
                            mOnMapClickListener.onMapClick(new LatLng(latitude, longitude));
                        }
                    }
                });
            }

            @JavascriptInterface
            public void onMapLongClick(final double latitude, final double longitude) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (mOnMapLongClickListener != null) {
                            mOnMapLongClickListener.onMapLongClick(new LatLng(latitude, longitude));
                        }
                    }
                });
            }

            @Override
            public void setOnMapLoadedCallback(GoogleMap.OnMapLoadedCallback callback) {
                mOnMapLoadedCallback = callback;
            }

            @Override
            public void setOnMapClickListener(GoogleMap.OnMapClickListener listener) {
                mOnMapClickListener = listener;
            }

            @Override
            public void setOnMapLongClickListener(GoogleMap.OnMapLongClickListener listener) {
                mOnMapLongClickListener = listener;
            }
        }
    }
}
