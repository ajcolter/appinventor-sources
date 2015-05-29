// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2014 MIT, All rights reserved
// Released under the MIT License https://raw.github.com/mit-cml/app-inventor/master/mitlicense.txt

package com.google.appinventor.components.runtime;

import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.ElementsUtil;
import com.google.appinventor.components.runtime.util.YailList;
import org.json.JSONArray;
import org.json.JSONException;
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.util.ErrorMessages;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a specialised web viewer to accommodate the google maps Javascript API (v.3). A number
 * of functions and event from the API have been added as blocks. The map (JS) and the Android
 * component talk back and forth through and interface. There are a number of functions defined
 * on the JavaScript side that can be executed from Java through webview.loadUrl(function) (see
 * AllowUserMarkers method), and there are a number of JavaScript functions that can call Java
 * methods through the interface defined as <em>AppInventorMap<em/>.
 *
 * IMPORTANT: To make changes to this component please follow the instructions as specified in the
 * loadMapLongLat method.
 * //TODO (jos) add link to the html file... is that in the repo?
 */
@DesignerComponent(version = YaVersion.WEBMAP_COMPONENT_VERSION,
    category = ComponentCategory.USERINTERFACE,
    description = "A component encapsulating functionality from the Google Maps JavaScript API " +
        "v3. An API key is recommended and can be obtained through the Google APIs console at " +
        "https://console.developers.google.com <br>" +
        "AI developers can specify certain attributes of the map, such as the initial location, " +
        "or the center of the map. Functions are available to perform actions such as pan the " +
        "map or adding markers to the map with different information. A number of events are also" +
        " provided to handle clicks on markers, or to allow end users to insert their own " +
        "markers. Markers can be managed in Lists of Lists, that can be persisted using an " +
        "additional component such as TinyDB.")
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.INTERNET")
public class WebMap extends AndroidViewComponent {

  public static final String LOG_TAG = "WEBMAP";
  public static final String INITIAL_LOCATION = "43.473847, -8.169154"; // Perlío, Spain.
  private final WebView webview;
  private String googleMapsKey = "";
  private String initialLatLng = INITIAL_LOCATION;
  private Form form;

  // allows passing strings to javascript
  WebViewInterface wvInterface;

  /**
   * Creates a new WebMap component.
   *
   * @param container  container the component will be placed in
   */
  public WebMap(ComponentContainer container) {
    super(container);

    this.form = container.$form();
    webview = new WebView(container.$context());

    webview.getSettings().setJavaScriptEnabled(true);
    webview.setFocusable(true);
    // adds a way to send strings to the javascript
    wvInterface = new WebViewInterface(form);
    webview.addJavascriptInterface(wvInterface, "AppInventorMap");
    //We had some issues with rendering of maps on certain devices; using caching seems to solve it
    webview.setDrawingCacheEnabled(false);
    webview.setDrawingCacheEnabled(true);

    // Support for console APIs -- only available in API level 8+ (here only for debugging).
    //TODO (jos) will this crash in lower level phones?
    webview.setWebChromeClient(new WebChromeClient() {
      public boolean onConsoleMessage(ConsoleMessage cm) {
        Log.d(LOG_TAG, cm.message() + " -- From line "
            + cm.lineNumber() + " of "
            + cm.sourceId() );
        return true;
      }
    });

    container.$add(this);

    webview.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
          case MotionEvent.ACTION_UP:
            if (!v.hasFocus()) {
              v.requestFocus();
            }
            break;
        }
        return false;
      }
    });

    InitialLocationLatLng("");

    // set the initial default properties.  Height and Width
    // will be fill-parent, which will be the default for the web viewer.
    Width(LENGTH_FILL_PARENT);
    Height(LENGTH_FILL_PARENT);


  }

  @SimpleProperty(description = "Google Maps API key. This key is not mandatory, " +
      "but the app might stop functioning at any time if it's not provided. Note that Google " +
      "imposes a limit of 25,000 calls a day for the free maps API. Keys can be obtained at: " +
      "https://console.developers.google.com, and more information about quotas can be accessed " +
      "at: https://developers.google.com/maps/documentation/javascript/usage")
  public String GoogleMapsKey() {
    return googleMapsKey;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "")
  @SimpleProperty
  public void GoogleMapsKey(String googleMapsKey) {
    this.googleMapsKey = googleMapsKey;
  }

  @SimpleProperty(description= "Initial location for the map. It will constitute the initial " +
      "center of the map. This location can be changed with the SetCenter block. The format must " +
      "be (lat, lgn), for instance a text block containing '25, 25'. The valid range for " +
      "Latitude is [-90, 90] and Longitude is [-180, 180]. An empty initial location will center " +
      "the map in Perlío, A Coruña, Spain.")
  public String InitialLocationLatLng() {
    return initialLatLng;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
      defaultValue = INITIAL_LOCATION)
  @SimpleProperty
  public void InitialLocationLatLng(String latLng) {
    initialLatLng = decodeLatLng(latLng);
    // clear the history, since changing the center of the map is a kind of reset
    webview.clearHistory();

    loadMapLongLat(initialLatLng);
  }

  /**
   * Parsing latitude and longitude from a string. Lat range [-90, 90]. Lng range [-180, 180].
   * @param latLng a string in the format "long, long" where long is a number (int or long)
   * @return the input string if it's in the correct format or INTIAL_LOCATION otherwise.
   */
  private String decodeLatLng(String latLng) {
    Log.d(LOG_TAG, "DecodeLatLng called: " + latLng);
    if (latLng.equals("")){
      Log.d(LOG_TAG, "No initial Location set; defaulting to Perlío, Spain.");
      return INITIAL_LOCATION;
    }
    else{
      boolean errorParsing = false;
      String [] locationSplit = latLng.split(",");
      Log.d(LOG_TAG, "locationSplit.length = " + locationSplit.length);
      if (locationSplit.length == 2){
        try {
          float lat = Float.parseFloat(locationSplit[0]);
          float lng = Float.parseFloat(locationSplit[1]);
          if (lat < -90 || lat > 90) errorParsing = true;
          if (lng < -180 || lng > 180) errorParsing = true;
        } catch (Exception e) { // Any exception here will have the same result
          errorParsing = true;
        }
      }
      else {
        errorParsing = true;
      }

      if(errorParsing){
        // We need a Handler to allow the UI to display before showing the Toast.
        Handler handler = new Handler();
        handler.postDelayed(new Runnable(){
          @Override
          public void run() {
            Log.d(LOG_TAG, "In the Handler Thread dispatching the parsing error;");
            form.dispatchErrorOccurredEvent(form, "InitialLocationLongLat",
                ErrorMessages.ERROR_ILLEGAL_INITIAL_CORDS_FORMAT);
          }
        }, 500);
        return INITIAL_LOCATION;
      }
    }

    return latLng;

  }

  /**
   * Loading the map into the WebView. We could have added the map in the assets folder,
   * but some users unpack apps and mess with the contents of that folder (some might decide to
   * delete a file they did not place there).
   * An added benefit of pasting the html file as a string here is that we can modify certain
   * values such as the API key and initial center.
   * IMPORTANT: to make changes to this component please follow the instructions in this method.
   * @param latLng initial center of the map (lat, lng).
   */
  private void loadMapLongLat(String latLng) {
    String mapKey = "";
    if (!GoogleMapsKey().equals(""))
      mapKey = "&key=" + GoogleMapsKey();

    //NOTE (IMPORTANT) : Do not make changes to this string directly.
    // This string is pasted from a html file. IntelliJ does the escaping automatically.
    // TODO (user) when copying a new string, make sure to change the initialization of the
    // thisMap object by adding the lngLat parameter instead of the hardcoded values. Also change
    // the key when we get one. Coming up with a better way to do this would be good.
    String map = "<!DOCTYPE html>\n" +
        "<html>\n" +
        "  <head>\n" +
        "    <title>App Inventor - Map Component</title>\n" +
        "    <meta name=\"viewport\" content=\"initial-scale=1.0, user-scalable=no\">\n" +
        "    <meta charset=\"utf-8\">\n" +
        "    <style>\n" +
        "      html, body, #map-canvas {\n" +
        "        height: 100%;\n" +
        "        margin: 0px;\n" +
        "        padding: 0px\n" +
        "      }\n" +
        "    </style>\n" +
        "    <script src=\"https://maps.googleapis.com/maps/api/js?v=3.exp\"></script>\n" +
        "    <script>\n" +
        "      /**\n" +
        "       * This map script is an abstraction of a number of functions from the Google maps\n" +
        "       * JavaScript API to be used within a customized WebView as an App Inventor Component.\n" +
        "       *\n" +
        "       * It contains two main objects: thisMap and mapMarkers.\n" +
        "       * thisMap initializes the map and makes use of mapMarkers, which simply encapsulates all\n" +
        "       * functions related to markers placed in the map. It is possible to create other utility\n" +
        "       * objects with other functionality in the SDK such as drawing, layers, or services.\n" +
        "       */\n" +
        "\n" +
        "      /**\n" +
        "       * This function returns an object with certain methods exposed as its API. The functionality\n" +
        "       * of this object is related to management of markers in the map.\n" +
        "       * @returns an Object with methods related to Marker management.\n" +
        "       * @param mapComponent the map to associate the markers with.\n" +
        "       */\n" +
        "      var mapMarkers = function(mapComponent) {\n" +
        "\n" +
        "        if (!mapComponent) throw new Error('No map available');\n" +
        "\n" +
        "        function AIMarker(marker){\n" +
        "          if (marker instanceof google.maps.Marker){\n" +
        "            this.marker = marker;\n" +
        "            this.id = marker.getPosition().toString(); //Use position as unique id\n" +
        "          } else {\n" +
        "            console.log('Calling Error handler on Android side - Invalid Marker');\n" +
        "            androidObject.dispatchErrorToAndroid(androidObject.ERROR_INVALID_MARKER);\n" +
        "          }\n" +
        "        }\n" +
        "\n" +
        "        AIMarker.prototype.equals = function(otherMarker){\n" +
        "          if (otherMarker == null) return false;\n" +
        "          if (otherMarker instanceof AIMarker){\n" +
        "            //Equality is based on position - two markers on the same position are the same marker\n" +
        "            return this.marker.getPosition().toString() ===\n" +
        "                otherMarker.marker.getPosition().toString();\n" +
        "          }\n" +
        "          return false;\n" +
        "\n" +
        "        }\n" +
        "\n" +
        "        var aiMarkers = {};\n" +
        "\n" +
        "        /**\n" +
        "         * Add a marker, with additional information, to the map.\n" +
        "         * @param location {google.maps.LatLng} object specifying the position in the map\n" +
        "         * @param infoWindowContent content to be displayed in this marker infoWindow\n" +
        "         * @param title a title for the marker (shown on hover in browsers)\n" +
        "         */\n" +
        "        var addAIMarker = function(location, title, infoWindowContent){\n" +
        "          var newAiMarker, marker;\n" +
        "          if (location instanceof google.maps.LatLng){\n" +
        "            newAiMarker = aiMarkers[location.toString()];\n" +
        "            if (newAiMarker) { //If it exists, there's no need to create it\n" +
        "              marker = newAiMarker.marker;\n" +
        "              // We override values even if they are not different - it's easier.\n" +
        "              marker.title = title;\n" +
        "              if (infoWindowContent && marker.info){\n" +
        "                marker.info.setContent(infoWindowContent);\n" +
        "              }\n" +
        "            } else {\n" +
        "              marker = new google.maps.Marker({\n" +
        "                position: location,\n" +
        "                title: title || '',\n" +
        "                map: mapComponent\n" +
        "              });\n" +
        "\n" +
        "              newAiMarker = new AIMarker(marker);\n" +
        "              var theId = newAiMarker.id;\n" +
        "              google.maps.event.addListener(marker, 'click', markerClicked(theId));\n" +
        "              google.maps.event.addListener(marker, 'dblclick', markerClicked(theId, true));\n" +
        "              aiMarkers[marker.getPosition().toString()] = newAiMarker;\n" +
        "\n" +
        "              if (infoWindowContent) {\n" +
        "                createInfoWindow(theId, infoWindowContent);\n" +
        "              }\n" +
        "            }\n" +
        "          } else {\n" +
        "            console.log('Calling Error handler on Android side');\n" +
        "            androidObject.dispatchErrorToAndroid(androidObject.ERROR_ILLEGAL_COORDS_FORMAT);\n" +
        "          }\n" +
        "\n" +
        "          return newAiMarker;\n" +
        "        };\n" +
        "\n" +
        "        // Closure needed to associate each markerId with its click handler function\n" +
        "        function markerClicked(markerId, doubleClick) {\n" +
        "          return function(){\n" +
        "            handleMarkerById(markerId, doubleClick);\n" +
        "          }\n" +
        "        }\n" +
        "\n" +
        "        function handleMarkerById(markerId, doubleClick) {\n" +
        "          var markerJson = createJsonMarkerFromId(markerId);\n" +
        "          if (doubleClick)\n" +
        "            androidObject.sendDoubleMarkerToAndroid(markerJson);\n" +
        "          else\n" +
        "            androidObject.sendMarkerToAndroid(markerJson);\n" +
        "        }\n" +
        "\n" +
        "        function createJsonMarkerFromId(markerId){\n" +
        "          var currentAiMarker = aiMarkers[markerId];\n" +
        "          var markerObject = {\n" +
        "            lat: currentAiMarker.marker.getPosition().lat(),\n" +
        "            lng: currentAiMarker.marker.getPosition().lng(),\n" +
        "            title: currentAiMarker.marker.title || '',\n" +
        "            info: (currentAiMarker.marker.info && currentAiMarker.marker.info.content) ?\n" +
        "                currentAiMarker.marker.info.content : ''\n" +
        "          }\n" +
        "          var markerJson = JSON.stringify(markerObject);\n" +
        "\n" +
        "          return markerJson;\n" +
        "        }\n" +
        "\n" +
        "        var panToMarker = function(markerId) {\n" +
        "          var markerToPanTo = aiMarkers[markerId].marker;\n" +
        "          if (markerToPanTo)\n" +
        "            mapComponent.panTo(markerToPanTo.getPosition());\n" +
        "        };\n" +
        "\n" +
        "        /**\n" +
        "         * Decodes the JSON input and generates and displays markers for each of the objects.\n" +
        "         * Sample data:\n" +
        "         * \"[\n" +
        "         *     {\"lat\":48.856614,\"lng\":2.3522219000000177,\"title\":\"\",\"content\":\"\"},\n" +
        "         *     {\"lat\":48,\"lng\":3,\"title\":\"near paris\",\"content\":\"near Paris content\"}\n" +
        "         *  ]\"\n" +
        "         * @param listOfMarkers a JSON representation of the markers to be displayed\n" +
        "         */\n" +
        "        var addMarkersFromList = function(listOfMarkers) {\n" +
        "          var allMarkers = [];\n" +
        "          try {\n" +
        "            allMarkers = JSON.parse(listOfMarkers);\n" +
        "          } catch(parseError) {\n" +
        "            console.log('List of Markers is not valid JSON. Notifying Android side.');\n" +
        "            androidObject.dispatchErrorToAndroid(androidObject.ERROR_PARSING_MARKERS_LIST)\n" +
        "          }\n" +
        "\n" +
        "          function decodeMarker(markerObject){\n" +
        "            var markerData = [];\n" +
        "            var lat, lng;\n" +
        "\n" +
        "            if (markerObject.lat)\n" +
        "              lat = markerObject.lat;\n" +
        "\n" +
        "            if (markerObject.lng)\n" +
        "              lng = markerObject.lng;\n" +
        "\n" +
        "            if (lat && lng)\n" +
        "              markerData[0] = locationFromLatLngCoords(lat, lng);\n" +
        "\n" +
        "            if (markerObject.title)\n" +
        "              markerData[1] = markerObject.title;\n" +
        "\n" +
        "            if (markerObject.content)\n" +
        "              markerData[2] = markerObject.content;\n" +
        "\n" +
        "            // Location has to be available(other fields are optional).\n" +
        "            if (markerData[0] instanceof google.maps.LatLng)\n" +
        "              return markerData;\n" +
        "            else //TODO (jos) trigger user feedback or not?\n" +
        "              return null;\n" +
        "          }\n" +
        "\n" +
        "          allMarkers.forEach(function(markerObject) {\n" +
        "            // Try to decode each marker and even if some fail, still add the others.\n" +
        "            var markerData = decodeMarker(markerObject);\n" +
        "            if (markerData)\n" +
        "              addAIMarker(markerData[0], markerData[1], markerData[2]);\n" +
        "          });\n" +
        "        };\n" +
        "\n" +
        "        var locationFromLatLngCoords = function(lat, lng){\n" +
        "          var errorParsing = false;\n" +
        "          //DO WE NEED TO DO THIS? the LatLng will wrap the coordinates. \n" +
        "          if (isNaN(lat) || isNaN(lng)) errorParsing = true;\n" +
        "          if (lat < -90 || lat > 90) errorParsing = true;\n" +
        "          if (lng < -180 || lng > 180) errorParsing = true;\n" +
        "\n" +
        "          if (errorParsing) {\n" +
        "            androidObject.dispatchErrorToAndroid(androidObject.ERROR_ILLEGAL_COORDS_FORMAT);\n" +
        "            return null;\n" +
        "          } else {\n" +
        "            return new google.maps.LatLng(lat, lng);\n" +
        "          }\n" +
        "\n" +
        "        };\n" +
        "\n" +
        "        /**\n" +
        "         * Generates a LatLng map object from coordinates passed in as a string. Valid ranges are:\n" +
        "         * Lat [-90, 90], and Lng [-180, 180].\n" +
        "         * @param locationText a string in the format 'float, float'\n" +
        "         * @returns {google.maps.LatLng} a LatLng object or null if the location is not in the\n" +
        "         * right format.\n" +
        "         */\n" +
        "        var locationFromTextCoords = function(locationText) {\n" +
        "          var lat, lng;\n" +
        "\n" +
        "          var locationSplit = locationText.split(',');\n" +
        "          if (locationSplit.length === 2){\n" +
        "            lat = parseFloat(locationSplit[0]);\n" +
        "            lng = parseFloat(locationSplit[1]);\n" +
        "            return locationFromLatLngCoords(lat, lng);\n" +
        "          } else {\n" +
        "            androidObject.dispatchErrorToAndroid(androidObject.ERROR_ILLEGAL_COORDS_FORMAT);\n" +
        "            return null;\n" +
        "          }\n" +
        "        };\n" +
        "\n" +
        "        var addListenersForMarkers = function (add) {\n" +
        "          if (add){\n" +
        "            google.maps.event.addListener(mapComponent, 'click', function(event) {\n" +
        "              var aiMarker = addAIMarker(event.latLng);\n" +
        "              var markerJson = createJsonMarkerFromId(aiMarker.id);\n" +
        "              androidObject.sendUserMarkerAddedToAndroid(markerJson);\n" +
        "            });\n" +
        "          } else\n" +
        "            google.maps.event.clearListeners(mapComponent,'click');\n" +
        "        };\n" +
        "\n" +
        "        var showMarker = function(markerId, show) {\n" +
        "          var markerToShow = aiMarkers[markerId].marker;\n" +
        "          if (show) {\n" +
        "            if (markerToShow){\n" +
        "              markerToShow.setMap(mapComponent);\n" +
        "            }\n" +
        "          } else {\n" +
        "            if (markerToShow) {\n" +
        "              markerToShow.setMap(null);\n" +
        "            }\n" +
        "          }\n" +
        "        };\n" +
        "\n" +
        "        var showMarkers = function (show){\n" +
        "          if (show) {\n" +
        "            for (var key in aiMarkers){\n" +
        "              aiMarkers[key].marker.setMap(mapComponent);\n" +
        "            }\n" +
        "          } else {\n" +
        "            for (var key in aiMarkers){\n" +
        "              aiMarkers[key].marker.setMap(null);\n" +
        "            }\n" +
        "          }\n" +
        "        };\n" +
        "\n" +
        "        var deleteMarkers = function (){\n" +
        "          showMarkers(false);\n" +
        "          aiMarkers = {};\n" +
        "        };\n" +
        "\n" +
        "        var storeMarkers = function () {\n" +
        "          var allMarkers = [];\n" +
        "          for (var key in aiMarkers){\n" +
        "            var marker = aiMarkers[key].marker;\n" +
        "\n" +
        "            var jsMarker = {};\n" +
        "            jsMarker.lat = marker.getPosition().lat();\n" +
        "            jsMarker.lng = marker.getPosition().lng();\n" +
        "            jsMarker.title = marker.title || '';\n" +
        "            if (marker.info && marker.info.content)\n" +
        "              jsMarker.content = marker.info.content;\n" +
        "            else\n" +
        "              jsMarker.content = '';\n" +
        "\n" +
        "            allMarkers.push(jsMarker);\n" +
        "          }\n" +
        "\n" +
        "          androidObject.sendListOfMarkersToAndroid(JSON.stringify(allMarkers));\n" +
        "\n" +
        "          return allMarkers;\n" +
        "        };\n" +
        "\n" +
        "        //Geolocation service\n" +
        "        var geolocate = function (address){\n" +
        "          var geocoder = new google.maps.Geocoder();\n" +
        "          geocoder.geocode({address: address}, geolocationResults)\n" +
        "        };\n" +
        "\n" +
        "        function geolocationResults(results, status){\n" +
        "          var notifyAndroid = false;\n" +
        "          if (status === 'OK') {\n" +
        "            var firstLocationFound = results[0].geometry.location;\n" +
        "            if (firstLocationFound){\n" +
        "              console.log(firstLocationFound);\n" +
        "              var marker = addAIMarker(firstLocationFound);\n" +
        "              var markerJson = createJsonMarkerFromId(marker.id);\n" +
        "              androidObject.sendGeolocationMarkerAddedToAndroid(markerJson,\n" +
        "                  results[0].formatted_address);\n" +
        "            } else {\n" +
        "              console.log('No location found!');\n" +
        "              notifyAndroid = true;\n" +
        "            }\n" +
        "          } else if (status === \"ZERO_RESULTS\"){\n" +
        "            console.log('No results found for that particular address.');\n" +
        "             notifyAndroid = true;\n" +
        "          } else {\n" +
        "            console.log('No results found. Status of Geolocation call: ' + status);\n" +
        "             notifyAndroid = true;\n" +
        "          }\n" +
        "\n" +
        "          if (notifyAndroid) {\n" +
        "            console.log('Notifying the Android side: No Results from Geolocation.')\n" +
        "            androidObject.dispatchErrorToAndroid(androidObject.ERROR_NO_GEOLOCATION_RESULTS);\n" +
        "          }\n" +
        "        };\n" +
        "\n" +
        "        // InfoWindow functions\n" +
        "        var createInfoWindow = function(markerId, content) {\n" +
        "          var infoWindow = new google.maps.InfoWindow({\n" +
        "            content: content\n" +
        "          });\n" +
        "          var marker = aiMarkers[markerId].marker;\n" +
        "          if (marker)\n" +
        "            marker.info = infoWindow;\n" +
        "        };\n" +
        "\n" +
        "        var openInfoWindow = function(markerId){\n" +
        "          var marker = aiMarkers[markerId].marker;\n" +
        "          if (marker && marker.info)\n" +
        "            marker.info.open(mapComponent, marker);\n" +
        "        };\n" +
        "\n" +
        "        var closeInfoWindow = function(markerId){\n" +
        "          var marker = aiMarkers[markerId].marker;\n" +
        "          if (marker && marker.info)\n" +
        "            marker.info.close();\n" +
        "        };\n" +
        "\n" +
        "        var setMarkerTitle = function(markerId, title) {\n" +
        "          var marker = aiMarkers[markerId].marker;\n" +
        "          if (marker)\n" +
        "            marker.setTitle(title);\n" +
        "        };\n" +
        "\n" +
        "        //var aiPolygons = {};\n" +
        "        /**\n" +
        "         * TODO ajcolter \n" +
        "         * Add a polygon to the map.\n" +
        "         * @param location {google.maps.LatLng} object specifying the position in the map\n" +
        "         * @param infoWindowContent content to be displayed in this marker infoWindow\n" +
        "         * @param title a title for the marker (shown on hover in browsers)\n" +
        "         */\n" +
        "        var addAIPolygon = function(pathList, strkColor, strkOpacity, strkWeight, fllColor, fllOpacity){\n" +
        "          var newAiPolygon, polygon;\n" +
        "              polygon = new google.maps.Polygon({\n" +
        "                paths: pathList,\n" +
        "                strokeColor: strkColor,\n" +
        "                strokeOpacity: strkOpacity,\n" +
        "                strokeWeight: strkWeight,\n" +
        "                fillColor: fllColor,\n" +
        "                fillOpacity: fllOpacity\n" +
        "              });\n" +
        "\n" +
        "              // newAiPolygon = new AIMarker(marker);\n" +
        "              // var theId = newAiMarker.id;\n" +
        "              // google.maps.event.addListener(marker, 'click', markerClicked(theId));\n" +
        "              // google.maps.event.addListener(marker, 'dblclick', markerClicked(theId, true));\n" +
        "              // aiMarkers[marker.getPosition().toString()] = newAiMarker;\n" +
        "              polygon.setMap(mapComponent);\n" +
        "\n" +
        "              var polygonJson = createJsonPolygon(polygon);\n" +
        "              androidObject.sendPolygonAddedToAndroid(polygonJson, results[0].formatted_address);\n" +
        "\n" +
        "          // } else {\n" +
        "          //   console.log('Calling Error handler on Android side');\n" +
        "          //   androidObject.dispatchErrorToAndroid(androidObject.ERROR_ILLEGAL_COORDS_FORMAT);\n" +
        "          // }\n" +
        "          return polygon;\n" +
        "          // return addAIPolygon;\n" +
        "        };\n" +
        "\n" +
        "        function createJsonPolygon(polygon){\n" +
        "          var currentPolygon = polygon;\n" +
        "          var polygonObject = {\n" +
        "            paths: currentPolygon.getPaths(),\n" +
        "            strokeColor: currentPolygon.strokeColor,\n" +
        "            strokeOpacity: currentPolygon.strokeOpacity,\n" +
        "            strokeWeight: currentPolygon.strokeWeight,\n" +
        "            fillColor: currentPolygon.fillColor,\n" +
        "            fillOpacity: currentPolygon.fillOpacity\n" +
        "          }\n" +
        "          var polygonJson = JSON.stringify(polygonObject);\n" +
        "\n" +
        "          return polygonJson;\n" +
        "        };\n" +
        "\n" +
        "        //API for the mapMarkers object\n" +
        "        //MUST ADD FUNCTION HANDLES HERE\n" +
        "        return {\n" +
        "          addMarkersFromList: addMarkersFromList,\n" +
        "          addListenersForMarkers: addListenersForMarkers,\n" +
        "          showMarker: showMarker,\n" +
        "          showMarkers: showMarkers,\n" +
        "          deleteMarkers: deleteMarkers,\n" +
        "          storeMarkers: storeMarkers,\n" +
        "          geolocate: geolocate,\n" +
        "          panToMarker: panToMarker,\n" +
        "          createInfoWindow: createInfoWindow,\n" +
        "          openInfoWindow: openInfoWindow,\n" +
        "          closeInfoWindow: closeInfoWindow,\n" +
        "          locationFromTextCoords: locationFromTextCoords,\n" +
        "          locationFromLatLngCoords: locationFromLatLngCoords,\n" +
        "          setMarkerTitle: setMarkerTitle,\n" +
        "          addAIMarker: addAIMarker,\n" +
        "          aiMarkers: aiMarkers,\n" +
        "          addAIPolygon: addAIPolygon\n" +
        "        };\n" +
        "\n" +
        "      };\n" +
        "\n" +
        "\n" +
        "      /**\n" +
        "       * Main function for this script. Initializes the map and all the related functionality.\n" +
        "       *\n" +
        "       */\n" +
        "      var thisMap = function(centerLat, centerLng, showCenter, initialZoom){\n" +
        "      \n" +
        "        var map;\n" +
        "        var centerMarker;\n" +
        "        var markerFunctions;\n" +
        "        var zoom = initialZoom || 6;\n" +
        "        var showingCenter = showCenter || true;\n" +
        "        function initialize() {\n" +
        "          var mapOptions = {\n" +
        "            zoom: zoom,\n" +
        "            center: new google.maps.LatLng(centerLat, centerLng),\n" +
        "            disableDoubleClickZoom: true\n" +
        "          };\n" +
        "          mapComponent = new google.maps.Map(document.getElementById('map-canvas'), mapOptions);\n" +
        "\n" +
        "          // Listening for the first 'idle' event to tell Android that the map is ready\n" +
        "          google.maps.event.addListenerOnce(mapComponent, 'idle', function(){\n" +
        "            console.log('Triggering mapIsReady on Android side.');\n" +
        "            androidObject.mapIsReadyToAndroid();\n" +
        "          });\n" +
        "\n" +
        "          centerMarker = createCenter();\n" +
        "          showCenter(showingCenter);\n" +
        "\n" +
        "          //Initialize marker functions object\n" +
        "          markerFunctions = mapMarkers(mapComponent);\n" +
        "          \n" +
        "        }\n" +
        "\n" +
        "        var createCenter = function(){\n" +
        "          if (centerMarker) centerMarker.setMap(null); // Delete any existing center first.\n" +
        "          var newCenter;\n" +
        "          if (mapComponent && mapComponent.getCenter()){\n" +
        "              newCenter = new google.maps.Marker({\n" +
        "                position: mapComponent.getCenter(),\n" +
        "                map: mapComponent,\n" +
        "                title: 'Map Center',\n" +
        "                icon: {\n" +
        "                  path: google.maps.SymbolPath.CIRCLE,\n" +
        "                  scale: 6\n" +
        "                }\n" +
        "              });\n" +
        "          }\n" +
        "          return newCenter;\n" +
        "        };\n" +
        "\n" +
        "        // Special case of showing a marker only for the center. Might be possible to abstract in\n" +
        "        // markerFunctions, but leaving it here for now (jos).\n" +
        "        var showCenter = function(show){\n" +
        "          if (show){\n" +
        "            if (centerMarker)\n" +
        "              centerMarker.setMap(mapComponent);\n" +
        "            else\n" +
        "              centerMarker = createCenter();\n" +
        "          } else {\n" +
        "            centerMarker.setMap(null);\n" +
        "          }\n" +
        "\n" +
        "          showingCenter = show;\n" +
        "        };\n" +
        "\n" +
        "        var setCenter = function(location){\n" +
        "          if (location instanceof google.maps.LatLng){\n" +
        "            mapComponent.setCenter(location);\n" +
        "          } else {\n" +
        "            mapComponent.setCenter(markerFunctions.locationFromTextCoords(location));\n" +
        "          }\n" +
        "\n" +
        "          centerMarker = createCenter();\n" +
        "          showCenter(showingCenter);\n" +
        "        };\n" +
        "\n" +
        "        var getMap = function() { return mapComponent; };\n" +
        "        var getMarkerFunctions = function() { return markerFunctions; };\n" +
        "\n" +
        "        var setZoom = function(zoom) {\n" +
        "          if (zoom >= 0 && zoom <= 19){\n" +
        "            mapComponent.setZoom(zoom);\n" +
        "          } else { // Exception handling is also done on Android side\n" +
        "            console.log('Zoom value ' + zoom + ' is not in the valid range 0-19');\n" +
        "          }\n" +
        "        };\n" +
        "\n" +
        "        var getZoom = function() { return mapComponent.zoom; };\n" +
        "\n" +
        "        //API for the thisMap object: main entry object for functionality\n" +
        "        return {\n" +
        "          initialize: initialize,\n" +
        "          showCenter: showCenter,\n" +
        "          setCenter: setCenter,\n" +
        "          getMap: getMap, // For Debugging (not used from the component).\n" +
        "          getMarkerFunctions: getMarkerFunctions,\n" +
        "          setZoom: setZoom,\n" +
        "          getZoom: getZoom\n" +
        "        }\n" +
        "      //TODO (jos) Magic numbers: the center of the map will come from Android\n" +
        "      }(43.473847, -8.169154, true, 6); //Auto initialize the thisMap object\n" +
        "\n" +
        "      /**\n" +
        "       * An object to hold functions that communicate directly to Android through the JS interface.\n" +
        "       * @type {{ERROR_ILLEGAL_COORDS_FORMAT: number, ERROR_PARSING_MARKERS_LIST: number,\n" +
        "       * ERROR_NO_GEOLOCATION_RESULTS: number, dispatchErrorToAndroid: dispatchErrorToAndroid,\n" +
        "       * sendMarkerToAndroid: sendMarkerToAndroid,\n" +
        "       * sendDoubleMarkerToAndroid: sendDoubleMarkerToAndroid,\n" +
        "       * sendListOfMarkersToAndroid: sendListOfMarkersToAndroid,\n" +
        "       * mapIsReadyToAndroid: mapIsReadyToAndroid,\n" +
        "       * sendUserMarkerAddedToAndroid: sendUserMarkerAddedToAndroid,\n" +
        "       * sendGeolocationMarkerAddedToAndroid: sendGeolocationMarkerAddedToAndroid}}\n" +
        "       */\n" +
        "      var androidObject = {\n" +
        "\n" +
        "        // CONSTANTS FOR ERRORS, As defined on the Android side.\n" +
        "        ERROR_ILLEGAL_COORDS_FORMAT: 2802,\n" +
        "        ERROR_PARSING_MARKERS_LIST: 2803,\n" +
        "        ERROR_INVALID_MARKER: 2804,\n" +
        "        ERROR_NO_GEOLOCATION_RESULTS: 2806,\n" +
        "\n" +
        "        /**\n" +
        "         * Function to dispatch errors to Android through the AppInventorMap interface. If this\n" +
        "         * file is loaded on a browser, AppInventorMap will be undefined and we skip the\n" +
        "         * dispatching.\n" +
        "         * TODO (jos) think about Error handling in JS if I even want to use this file standalone.\n" +
        "         * @param errorNumber number for the message to display as user feedback on the Android\n" +
        "         * side. The messages are defined in ErrorMessages.java\n" +
        "         */\n" +
        "        dispatchErrorToAndroid: function(errorNumber) {\n" +
        "          if (typeof AppInventorMap !== 'undefined')\n" +
        "            AppInventorMap.dispatchError(errorNumber);\n" +
        "        },\n" +
        "\n" +
        "        /**\n" +
        "         * Function to call to the Android side after a user has clicked on a marker\n" +
        "         */\n" +
        "        sendMarkerToAndroid: function(jsonMarker) {\n" +
        "          if (typeof AppInventorMap !== 'undefined')\n" +
        "            AppInventorMap.handleMarker(jsonMarker);\n" +
        "        },\n" +
        "\n" +
        "        sendDoubleMarkerToAndroid: function(jsonMarker) {\n" +
        "          if (typeof AppInventorMap !== 'undefined')\n" +
        "            AppInventorMap.handleDoubleMarker(jsonMarker);\n" +
        "        },\n" +
        "\n" +
        "        /**\n" +
        "         * Function to export all markers to the Android side for storage\n" +
        "         */\n" +
        "        sendListOfMarkersToAndroid: function(markers) {\n" +
        "          if (typeof AppInventorMap !== 'undefined')\n" +
        "            AppInventorMap.storeMarkers(markers);\n" +
        "        },\n" +
        "\n" +
        "        /**\n" +
        "         * Notify Component that the Map is ready.\n" +
        "         */\n" +
        "        mapIsReadyToAndroid: function() {\n" +
        "          if (typeof AppInventorMap !== 'undefined')\n" +
        "            AppInventorMap.mapIsReady();\n" +
        "        },\n" +
        "\n" +
        "        sendUserMarkerAddedToAndroid: function(markerJson) {\n" +
        "          if (typeof AppInventorMap !== 'undefined')\n" +
        "            AppInventorMap.userMarkerAdded(markerJson);\n" +
        "        },\n" +
        "\n" +
        "        sendGeolocationMarkerAddedToAndroid: function(markerJson, formattedAddress) {\n" +
        "          if (typeof AppInventorMap !== 'undefined')\n" +
        "            AppInventorMap.geolocationMarkerAdded(markerJson, formattedAddress);\n" +
        "        },\n" +
        "\n" +
        "        sendPolygonAddedToAndroid: function(polygonJson) {\n" +
        "          if (typeof AppInventorMap !== 'undefined')\n" +
        "            AppInventorMap.polygonAdded(polygonJson);\n" +
        "        }\n" +
        "\n" +
        "      };\n" +
        "\n" +
        "      google.maps.event.addDomListener(window, 'load', thisMap.initialize);\n" +
        "\n" +
        "    </script>\n" +
        "  </head>\n" +
        "  <body>\n" +
        "    <div id=\"map-canvas\"></div>\n" +
        "  </body>\n" +
        "</html>\n";

    webview.loadUrl("file:///sdcard/AppInventor/assets/map.html");
//    webview.loadDataWithBaseURL(null, map, "text/html", "utf-8", null);

  }

  /**
   * Specifies whether users will be able to add markers by clicking on the map.
   *
   * @param allowUserMarkers  {@code true} markers allowed, {@code false} markers not allowed
   */
  @SimpleFunction(description = "Specifies whether users will be able to add markers by clicking " +
      "on the map. True will make the map listen for clicks. False will unbind the listener.")
  //TODO (ajcolter) is there a better name that could be used?
  public void AllowUserMarkers(boolean allowUserMarkers) {
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().addListenersForMarkers(" +
        allowUserMarkers + ")");
  }

  /**
   * The center of the map is marked with a circle. The show parameter specifies if the circle is
   * painted on the map (true) or not (false).
   * @param show true paints the icon in the center of the map. false hides the special marker
   *             icon.
   */
  @SimpleFunction(description = "The center of the map is marked with a circle. The show parameter " +
      "specifies if the circle is painted on the map (true) or not (false).")
  public void ShowCenter(boolean show) {
    webview.loadUrl("javascript:thisMap.showCenter(" + show + ")");
  }

  @SimpleFunction(description = "Re-set the center of the map and pan to it. The coordinates are " +
      "in the format (lat, lng), for instance a text block containing '25, 25'.")
  public void SetCenter(String coords) {
    webview.loadUrl("javascript:thisMap.setCenter('" + coords + "')");
  }

  @SimpleFunction(description = "Sets the Zoom to the specified level by the zoom parameter.")
  public void SetZoom(int zoom) {
    if (zoom >= 0 && zoom < 20)
      webview.loadUrl("javascript:thisMap.setZoom(" + zoom + ")");
    else
      form.dispatchErrorOccurredEvent(form, "AddMarker", ErrorMessages.ERROR_INVALID_ZOOM_LEVEL);
  }

  @SimpleFunction(description = "Shows all the markers currently available on the map, " +
      "even those that might have been hidden by developer or user action.")
  public void ShowMarkers(boolean show) {
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().showMarkers(" + show + ")");
  }

  @SimpleFunction(description = "Deletes all markers currently on the map. This is a full delete." +
      " To simply hide markers from view please use the HideMarkers block.")
  public void DeleteMarkersFromMap() {
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().deleteMarkers()");
  }

  @SimpleFunction(description = "Places a marker in a location by providing its address instead " +
      "of coordinates. An example could be '32 Vassar St. Cambridge MA'")
  public void GeoLocate(String address) {
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().geolocate('" + address + "')");
  }

  @SimpleFunction(description = "Adds a marker to the map. To create a Marker use the Marker " +
      "block, also available in WebMap.")
  public void AddMarkerToMap(YailList marker) {
    String [] markerData = marker.toStringArray();
    String javaScriptCommand = "javascript:thisMap.getMarkerFunctions().addAIMarker(thisMap" +
        ".getMarkerFunctions().locationFromLatLngCoords(" + markerData[0] + ", " + markerData[1] +
        "),'" + markerData[2] + "', '" + markerData[3] + "')";

    webview.loadUrl(javaScriptCommand);
  }

  @SimpleFunction(description = "Creates and returns a Marker but does not directly add it to the" +
      " map. To add it to the map you can use the AddMarkerToMap block. You can also store this " +
      "marker in a List and use the block AddMarkersFromList. The range for latitude is [-90, 90]," +
      " and the range for longitude is [-180, 180]. The id field is used to manage markers and " +
      "actions over markers. If you don't need to manage markers you can use the value -1 and the" +
      " map will handle ids automatically. The field title can be used as a more human readable " +
      "id if you wanted to show all markers in a ListView or a ListPicker. The field content is " +
      "used to add a paragraph of text to an InfoWindow that could be displayed when the user " +
      "clicks on a marker.")
  public YailList Marker(long latitude, long longitude, String title, String infoWindowContent) {

    if ((-90.0 <= latitude && latitude <= 90.0) && (-180.0 <= longitude && longitude <= 180.0)){
      ArrayList<String> values = new ArrayList<String>();
      values.add(latitude + "");
      values.add(longitude + "");
      values.add(title + "");
      values.add(infoWindowContent + "");
      return YailList.makeList(values);
    }
    else {
      form.dispatchErrorOccurredEvent(form, "Marker", ErrorMessages.ERROR_ILLEGAL_COORDS_FORMAT);
    }

    return YailList.makeList(new ArrayList()); // Return an empty list if we cannot create a marker
  }

  @SimpleFunction(description = "Creates and returns a Polygon. The paths object expects a set of latitude-longitude " +
      "pairs. The range for latitude is [-90, 90], and the range for longitude is [-180, 180]. The stroke color is " +
      "the desired for the stroke between vertices. The stroke opacity is the opacity of the line between vertices. " +
      "The stroke weight is the thickness of the line between vertices. The fill color is the color that will be used" +
      " to fill the polygon. The fill opacity is the opacity of the fill in the polygon.")
  public YailList Polygon(YailList paths, int strokeColor, long strokeOpacity, long strokeWeight, int fillColor,
                          long fillOpacity) {

    ArrayList<String> values = new ArrayList<String>();
//    ArrayList<String> pathsCopy = new ArrayList<String>();
    //comma separate all of the values
//    for (int i = 0; i< paths.length(); i++) {
//      pathsCopy.add(paths.getString(i) + ",");
//    }
//    Log.i("aubrey", pathsCopy.toString());
//    values.add(YailList.makeList(pathsCopy) + "");
    values.add(paths + "");
    values.add(strokeColor + "");
    values.add(strokeOpacity + "");
    values.add(strokeWeight + "");
    values.add(strokeWeight + "");
    values.add(fillColor + "");
    values.add(fillOpacity + "");
    Log.i("aubrey values", values.toString());
    return YailList.makeList(values);
  }

  @SimpleFunction(description = "Adds a polygon to the map.")
  public void AddPolygonToMap(YailList polygon) {
    Log.i("aubrey polygon", polygon.toString());

    String[] polygonData = polygon.toStringArray();
//    Log.i("aubrey polygonData", polygonData.toString());
//    Log.i("aubrey get0 get0)", polygon.getString(0).get(0));
//    for (String poly : polygonData) {
//      Log.i("aubrey polyData", poly);
//    }

    String pathval = new String();
//    String pathsString = polygon.getObject(0).toString();

//    String[] pathsArray = pathsString.split("\\.");
    YailList pathlist = ElementsUtil.elementsFromString(polygonData[0]);
//    YailList pathlist2 = ElementsUtil.elementsFromString(polygon.get(0).toString());
//    Log.i("aubrey polygon get0", polygon.get(0).toString());
//    Log.i("aubrey getobject0", polygon.getObject(0).toString());
//    pathlist.getObject()
//    String[] s = pathlist.toStringArray();

    for (int i = 1; i <= pathlist.size(); i++) {
//      Log.i("aubreyGET "+i, pathlist2.get(i).toString());
      Log.i("aubrey pathlist " + i, pathlist.get(i).toString());
//      pathlist.getObject(i+1).toString().split("\\s+");
//      pathval += "thisMap.getMarkerFunctions().locationFromLatLngCoords" + pathlist.getString(i) + ", " +
//          pathlist.getString(i+1) + ",";
    }
//    Log.i("aubrey pathval", pathval);
//    Log.i("aubrey patharray", pathsArray.toString());
//    Log.i("aubrey pathval", pathval);
//    String javaScriptCommand = "javascript:thisMap.getMarkerFunctions().addAIPolygon([" +
//        "thisMap.getMarkerFunctions().locationFromLatLngCoords(25.774252, -80.190262)," +
//        "thisMap.getMarkerFunctions().locationFromLatLngCoords(18.466465, -66.118292)," +
//        "thisMap.getMarkerFunctions().locationFromLatLngCoords(32.321384, -64.75737)," + "'], '" +
//        polygonData[1] + "', '" + polygonData[2] + "', '" + polygonData[3] + "', '" + polygonData[4] + "', '" +
//        polygonData[5] + "', '" + polygonData[6] + "')";

//    webview.loadUrl(javaScriptCommand);
  }

  @SimpleFunction(description = "Shows a particular marker on the map by its id.")
  public void ShowMarker(YailList marker, boolean show) {
    String markerId = idForMarker(marker);
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().showMarker('" + markerId+ "', " +
        show + ")");
  }

  private String idForMarker(YailList marker) {
    String markerId = "(" + marker.getString(0) + ", " + marker.getString(1) + ")";
    return markerId;
  }

  @SimpleFunction(description = "Pan the map towards a particular marker on the map by its id.")
  public void PanToMarker(YailList marker) {
    String markerId = idForMarker(marker);
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().panToMarker('" + markerId+ "')");
  }

  @SimpleFunction(description = "A new marker with all properties unchanged except for the title of" +
      " the particular marker passed as a parameter. This title could be used as" +
      " a more human readable id to show and select markers in a ListView or ListPicker.")
  public YailList SetMarkerTitle(YailList marker, String title) {
    String markerId = idForMarker(marker);
    //create a new Marker object from the previous one. We cannot simply set position 3 to the
    // new title because YailList does not implement the set method.
    String markerValues [] = marker.toStringArray();
    markerValues[2] = title;
    YailList newMarker = YailList.makeList(markerValues);
    String javaScriptCommand = "javascript:thisMap.getMarkerFunctions().setMarkerTitle('" +
        markerId + "', '" + title + "')";
    Log.d(LOG_TAG, "JS command for SetMarkerTitle is: " + javaScriptCommand);
    Log.d(LOG_TAG, "markerId for SetMarkerTitle is: " + markerId);
    webview.loadUrl(javaScriptCommand);

    return newMarker;
  }

  @SimpleFunction(description = "Get the title of a particular marker.")
  public String GetMarkerTitle(YailList marker) {
    return marker.getString(2);
  }

  @SimpleFunction(description = "Get the InfoWindow content of a particular marker.")
  public String GetMarkerInfoWindowContent(YailList marker) {
    return marker.getString(3);
  }

  @SimpleFunction(description = "Get the Latitude of a particular marker.")
  public double GetMarkerLatitude(YailList marker) {
    return Double.valueOf(marker.getString(0)).doubleValue();
  }

  @SimpleFunction(description = "Get the Longitude of a particular marker.")
  public double GetMarkerLongitude(YailList marker) {
    return Double.valueOf(marker.getString(1)).doubleValue();
  }

  @SimpleFunction(description = "This function requests a list of lists that contains all of the " +
      "markers currently existing on the map. These markers can be hidden or visible, and " +
      "could have been added by the AI developer or directly by the end user. This list of lists " +
      "can be persisted with an additional component, such as TinyDB. This function triggers the " +
      "event MarkersFromMapReceived when the list is received. Several lists of markers could be " +
      "stored in the Screen and sent to the map with the block AddMarkersFromList.")
  public void RequestListOfMarkersFromMap() { //may make more sense to name this GetListOfMarkers
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().storeMarkers()");
  }

  @SimpleFunction(description = "Visualizes a list of lists of markers in the map. This block " +
      "could be used in combination with RequestListOfMarkersFromMap to manage different lists of " +
      "markers within the same map. Note that the format must be a list of lists, " +
      "and those lists should contain 4 elements (id, coordinates, title, content).")
  public void AddMarkersFromList(YailList list) {
    //TODO (ajcolter) make sure it is a List of Lists and convert it to JSON
    String markersJSON = createStringifiedJSONFromYailList(list);
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().addMarkersFromList('" + markersJSON +
        "')");
  }

  /**
   * From a YailList of YailLists containing data for markers, serialize into a JSON object to
   * send to the map.
   * @param markers list of lists with marker information
   * @return
   */
  private String createStringifiedJSONFromYailList(YailList markers) {

    // this is something like ( (47 3 title_text content_text) )
    StringBuilder json = new StringBuilder();
    json.append("[");
    for (int i = 1; i < markers.length(); i++){

      String lat = ((YailList) markers.get(i)).get(1).toString();
      String lng = ((YailList) markers.get(i)).get(2).toString();
      String title = ((YailList) markers.get(i)).get(3).toString();
      String content = ((YailList) markers.get(i)).get(4).toString();
      json.append("{\"lat\":" + lat + ",\"lng\":" + lng + "," +
          "\"title\":\"" + title + "\",\"content\":\"" + content + "\"},");
    }

    json.setLength(json.length() - 1); // Delete last comma from the object
    json.append("]");

    return json.toString();
  }

  @SimpleFunction(description = "Creates an InfoWindow for a particular marker that can be " +
      "displayed on particular events (in combination with the open and close infoWindow blocks.)")
  public YailList CreateInfoWindow(YailList marker, String content) {
    String markerId = idForMarker(marker);
    //create a new Marker object from the previous one. We cannot simply set position 3 to the
    // new title because YailList does not implement the set method.
    String markerValues [] = marker.toStringArray();
    markerValues[3] = content;
    YailList newMarker = YailList.makeList(markerValues);
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().createInfoWindow('" +
        markerId + "', '" + content + "')");

    return newMarker;
  }

  @SimpleFunction(description = "Open an InfoWindow for a particular marker.")
  public void OpenInfoWindow(YailList marker) {
    String markerId = idForMarker(marker);
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().openInfoWindow('" + markerId + "')");
  }

  @SimpleFunction(description = "Close an InfoWindow for a particular marker.")
  public void CloseInfoWindow(YailList marker) {
    String markerId = idForMarker(marker);
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().closeInfoWindow('" + markerId+ "')");
  }

  @Override
  public View getView() {
    return webview;
  }

  // Components don't normally override Width and Height, but we do it here so that
  // the automatic width and height will be fill parent.
  @Override
  @SimpleProperty()
  public void Width(int width) {
    if (width == LENGTH_PREFERRED) {
      width = LENGTH_FILL_PARENT;
    }
    super.Width(width);
  }

  @Override
  @SimpleProperty()
  public void Height(int height) {
    if (height == LENGTH_PREFERRED) {
      height = LENGTH_FILL_PARENT;
    }
    super.Height(height);
  }

  /**
   * Event triggered by a marker being clicked on the map.
   * @param marker a stringified representation of the marked being clicked
   */
  @SimpleEvent(description = "Event triggered by a marker being clicked on the map.")
  public void MarkerClicked(final String marker) {
      YailList markerYail = createMarkerFromStringifiedJson(marker);
      EventDispatcher.dispatchEvent(this, "MarkerClicked", markerYail);
  }

  private YailList createMarkerFromStringifiedJson(String jsonMarker){
    YailList marker = YailList.makeList(new ArrayList());
    try {
      JSONObject object = new JSONObject(jsonMarker);

      ArrayList<String> values = new ArrayList<String>();
      values.add(object.getString("lat"));
      values.add(object.getString("lng"));
      values.add(object.getString("title"));
      values.add(object.getString("info"));
      marker = YailList.makeList(values);
    } catch (JSONException e) {
      //TODO (ajcolter) do something about this! Create an ErrorMessage for this
      e.printStackTrace();
      Log.d(LOG_TAG, "Problem parsing the JSON that came from the JavaScript Click handler");
    }
    return marker;
  }

  private YailList createPolygonFromStringifiedJson(String jsonPolygon){
    YailList polygon = YailList.makeList(new ArrayList());
    try {
      JSONObject object = new JSONObject(jsonPolygon);

      ArrayList<String> values = new ArrayList<String>();
      values.add(object.getString("paths"));
      values.add(object.getString("strokeColor"));
      values.add(object.getString("strokeOpacity"));
      values.add(object.getString("strokeWeight"));
      values.add(object.getString("fillColor"));
      values.add(object.getString("fillOpacity"));
      polygon = YailList.makeList(values);
    } catch (JSONException e) {
      //TODO (ajcolter) do something about this! Create an ErrorMessage for this
      e.printStackTrace();
      Log.d(LOG_TAG, "Problem parsing the JSON that came from the JavaScript Click handler");
    }
    return polygon;
  }

  /**
   * Event triggered by a marker being doubled clicked on the map. NOTE that a MarkerClicked event
   * will always be triggered at the same time that a marker is being double clicked.
   * @param marker a stringified representation of the marked being double clicked
   */
  @SimpleEvent(description = "Event triggered by a marker being doubled clicked on the map. NOTE " +
      "that a MarkerClicked event will always be triggered at the same time that a marker is " +
      "being double clicked.")
  //TODO (ajcolter) make a work around for this. It's silly to have a clicked and a double clicked event triggered at the same time.
  public void MarkerDoubleClicked(final String marker) {
    YailList markerYail = createMarkerFromStringifiedJson(marker);
    EventDispatcher.dispatchEvent(this, "MarkerDoubleClicked", markerYail);
  }

  @SimpleEvent(description = "Event triggered after a request made by the " +
      "RequestListOfMarkersFromMap block. It returns a List of Lists with the information of all " +
      "the markers currently available (hidden or visible) on the map.")
  public void MarkersFromMapReceived(final YailList markersList) {
    EventDispatcher.dispatchEvent(this, "MarkersFromMapReceived", markersList);
  }

  @SimpleEvent(description = "Event triggered after the map has finished loading and is ready to " +
      "receive instructions. Anything done before the map is fully loaded might not have any " +
      "effect.")
  public void MapIsReady() {
    SetCenter(initialLatLng);   // This should really be done when the map is loaded
    EventDispatcher.dispatchEvent(this, "MapIsReady");
  }

  @SimpleEvent(description = "A user has added a marker by clicking on the map. This event will " +
      "trigger only if users are allowed to add markers by using the block AllowUserMarkers. " +
      "The marker is returned so that actions can be applied to that particular marker.")
  public void UserMarkerAdded(final String marker) {
    YailList markerYail = createMarkerFromStringifiedJson(marker);
    EventDispatcher.dispatchEvent(this, "UserMarkerAdded", markerYail);
  }

  @SimpleEvent(description = "A marker has been added to the map by using the Geolocate block. " +
      "The marker is returned so that actions can be applied to that particular marker.")
  public void GeolocationMarkerAdded(final String marker, final String formattedAddress) {
    YailList markerYail = createMarkerFromStringifiedJson(marker);
    EventDispatcher.dispatchEvent(this, "GeolocationMarkerAdded", markerYail, formattedAddress);
  }

  @SimpleEvent(description = "A polygon has been added to the map by using the Polygon block. " +
      "The polygon is returned so that actions can be applied to that particular polygon.")
  public void PolygonAdded(final String polygon) {
    YailList polygonYail = createPolygonFromStringifiedJson(polygon);
    EventDispatcher.dispatchEvent(this, "PolygonAdded", polygonYail);
  }

  /**
   * Allows the setting of properties to be monitored from the javascript
   * in the WebView
   */
  public class WebViewInterface {
    Form webViewForm;

    /** Instantiate the interface and set the context */
    WebViewInterface(Form webViewForm) {
      this.webViewForm = webViewForm;
    }

    /**
     * Method to be invoked from JavaScript in the WebView
     * @param errorNumber the error number to dispatch
     */
    @JavascriptInterface
    public void dispatchError(int errorNumber) {
      Log.d(LOG_TAG, "Error triggered on map with errorNumber: " + errorNumber);
      webViewForm.dispatchErrorOccurredEvent(webViewForm, "dispatchError", errorNumber);
    }

    @JavascriptInterface
    public void handleMarker(final String jsonMarker) {
      Log.d(LOG_TAG, "Marker clicked: " + jsonMarker);
      webViewForm.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          MarkerClicked(jsonMarker);
        }
      });
    }

    @JavascriptInterface
    public void handleDoubleMarker(final String jsonMarker) {
      Log.d(LOG_TAG, "Marker DOUBLED clicked: " + jsonMarker);
      webViewForm.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          MarkerDoubleClicked(jsonMarker);
        }
      });
    }

    /**
     * Receives a JSON object with marker data from the map and converts each marker to a List
     * that will be added to a YailList of markers. The result is a list of lists with marker data.
     * @param markersList the stringified JSON object coming from the map.
     */
    @JavascriptInterface
    public void storeMarkers(final String markersList) {
      try {
        JSONArray markersJSON = new JSONArray(markersList);
        List<YailList> markerValues = new ArrayList<YailList>();

        for(int i = 0; i < markersJSON.length(); i++){
          List<String> aMarker = new ArrayList<String>();
          aMarker.add(markersJSON.getJSONObject(i).getString("lat"));
          aMarker.add(markersJSON.getJSONObject(i).getString("lng"));
          aMarker.add(markersJSON.getJSONObject(i).getString("title"));
          aMarker.add(markersJSON.getJSONObject(i).getString("content"));
          markerValues.add(YailList.makeList(aMarker));
        }

        final YailList markersYailList = YailList.makeList(markerValues);
        webViewForm.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            MarkersFromMapReceived(markersYailList);
          }
        });
      } catch (JSONException e) {
        webViewForm.dispatchErrorOccurredEvent(form, "storeMarkers",
            ErrorMessages.ERROR_PARSING_MARKERS_LIST);
      }
    }

    @JavascriptInterface
    public void mapIsReady() {
      Log.d(LOG_TAG, "MAP IS READY IS BEING CALLED!");
      webViewForm.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          MapIsReady();
        }
      });
    }

    @JavascriptInterface
    public void userMarkerAdded(final String markerJson) {
      webViewForm.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          UserMarkerAdded(markerJson);
        }
      });
    }

    @JavascriptInterface
    public void geolocationMarkerAdded(final String markerJson, final String formattedAddress) {
      webViewForm.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          GeolocationMarkerAdded(markerJson, formattedAddress);
        }
      });
    }

  }
}
