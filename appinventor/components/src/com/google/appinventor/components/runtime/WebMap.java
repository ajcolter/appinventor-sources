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
    // This string is pasted from a html file (located in root/tmp). IntelliJ does the escaping automatically.
    // TODO (user) when copying a new string, make sure to change the initialization of the
    // mapContainer object by adding the lngLat parameter instead of the hardcoded values. Also change
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
        "       * Main function for this script. Initializes the map and all the related functionality.\n" +
        "       *\n" +
        "       */\n" +
        "      var mapContainer = function(centerLat, centerLng, showCenter, initialZoom) {\n" +
        "      \n" +
        "        var map;\n" +
        "        var centerMarker;\n" +
        "        //var markerFunctions;\n" +
        "        var allMarkers = {};\n" +
        "        var zoom = initialZoom || 6;\n" +
        "        var showingCenter = showCenter || true;\n" +
        "        var centerCoords = new google.maps.LatLng(centerLat, centerLng)\n" +
        "        function initialize() {\n" +
        "          var mapOptions = {\n" +
        "            zoom: zoom,\n" +
        "            center: centerCoords,\n" +
        "            disableDoubleClickZoom: true\n" +
        "          };\n" +
        "          map = new google.maps.Map(document.getElementById('map-canvas'), mapOptions);\n" +
        "          //centerMarker = setCenter(centerCoords);\n" +
        "          //showCenter(showingCenter);\n" +
        "\n" +
        "          //Initialize marker functions object, exposes the marker functions.\n" +
        "          //markerFunctions = markerObject(thisMap);\n" +
        "          \n" +
        "        };\n" +
        "\n" +
        "        var setCenter = function(location){\n" +
        "          if (centerMarker) \n" +
        "            centerMarker.setMap(null); // Delete any existing center first.\n" +
        "\n" +
        "          centerMarker = createMarker(location, 'Map Center');\n" +
        "          centerMarker.setIcon({path: google.maps.SymbolPath.CIRCLE,\n" +
        "                scale: 6})\n" +
        "          //TODO: include a pan to center?\n" +
        "          panToMarker(centerMarker);\n" +
        "          return centerMarker;\n" +
        "        };\n" +
        "\n" +
        "        // Special case of showing a marker only for the center. Might be possible to abstract in\n" +
        "        // markerFunctions, but leaving it here for now (jos).\n" +
        "        var showCenter = function(show){\n" +
        "          if (show) {\n" +
        "            if (centerMarker)\n" +
        "              centerMarker.setMap(map);\n" +
        "          } else {\n" +
        "            centerMarker.setMap(null);\n" +
        "          }\n" +
        "\n" +
        "          showingCenter = show;\n" +
        "        };\n" +
        "\n" +
        "        var getCenter = function() { \n" +
        "          return androidObject.sendCenterMarkerToAndroid(centerMarker.createJsonMarker()); \n" +
        "        };\n" +
        "\n" +
        "        var getGoogleMapObject = function() { return map; };\n" +
        "\n" +
        "        //not sure why we need this getter; what is the use case for needing these functions?\n" +
        "        //var getMarkerFunctions = function() { return markerFunctions; };\n" +
        "\n" +
        "        var getAllMarkers = function() { return allMarkers; };\n" +
        "\n" +
        "        var getMarker = function(location) {\n" +
        "          return allMarkers[location.toString()];\n" +
        "        };\n" +
        "\n" +
        "        var getMap = function() {\n" +
        "          return map;\n" +
        "        }\n" +
        "\n" +
        "\n" +
        "        /**\n" +
        "         * Add a marker, with additional information, to the map.\n" +
        "         * @param location {google.maps.LatLng} object specifying the position in the map\n" +
        "         * @param infoWindowContent content to be displayed in this marker infoWindow\n" +
        "         * @param title a title for the marker (shown on hover in browsers)\n" +
        "         */\n" +
        "        var createMarker = function(location, title) {//, infoWindowContent){ \n" +
        "          var marker;\n" +
        "          var loc;\n" +
        "          var lat;\n" +
        "          var lng;\n" +
        "          var existingMarker;\n" +
        "          if (location instanceof google.maps.LatLng) {\n" +
        "            loc = location;\n" +
        "          } else if (typeof location === \"string\") {\n" +
        "            loc = locationFromTextCoords(location);\n" +
        "          } else if (location.length == 2) {\n" +
        "            loc = locationFromLatLngCoords(location[0], location[1]);\n" +
        "          } else {\n" +
        "            //try geolocating from address\n" +
        "            //else throw error\n" +
        "            console.log(\"No valid location.\");\n" +
        "            return null;\n" +
        "          }\n" +
        "          lat = loc.lat();\n" +
        "          lng = loc.lng();\n" +
        "\n" +
        "          existingMarker = getMarker(loc);\n" +
        "          if (existingMarker) { //If it exists, there's no need to create it\n" +
        "            marker = existingMarker;\n" +
        "\n" +
        "            // We override values even if they are not different - it's easier.\n" +
        "            // marker.title = title;\n" +
        "            // if (infoWindowContent && marker.info){\n" +
        "            //   marker.info.setContent(infoWindowContent);\n" +
        "            // }\n" +
        "          } else {\n" +
        "            marker = new markerContainer(map, lat, lng, title, null, null, true, true);\n" +
        "            addMarker(marker);\n" +
        "\n" +
        "            // if (infoWindowContent) {\n" +
        "            //   markerFunctions.createInfoWindow(marker.getPosition().toString(), infoWindowContent);\n" +
        "            // }\n" +
        "          }\n" +
        "        // } else if () {\n" +
        "\n" +
        "        //   // if {\n" +
        "\n" +
        "        //   // } else {\n" +
        "        //   //   console.log('Calling Error handler on Android side');\n" +
        "        //   //   return null;\n" +
        "        //   // }\n" +
        "        // }\n" +
        "\n" +
        "          return marker;\n" +
        "\n" +
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
        "         //TODO: Do we really want to have a json list? what about a yaillist? or the app inventor list structure?\n" +
        "        var createMarkersFromList = function(listOfMarkers) {\n" +
        "          try {\n" +
        "            var allParsedMarkers = JSON.parse(listOfMarkers);\n" +
        "          } catch(parseError) {\n" +
        "            console.log('List of Markers is not valid JSON. Notifying Android side.');\n" +
        "            return;\n" +
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
        "          allParsedMarkers.forEach(function(markerObject) {\n" +
        "            // Try to decode each marker and even if some fail, still add the others.\n" +
        "            var markerData = decodeMarker(markerObject);\n" +
        "            if (markerData)\n" +
        "              createMarker(markerData[0], markerData[1], markerData[2]);\n" +
        "          });\n" +
        "        };\n" +
        "\n" +
        "\n" +
        "        //TODO Verify the marker isn't already in the dict? If it is already in the dict it will be overwritten.\n" +
        "        var addMarker = function(marker) {\n" +
        "          var loc = marker.getPosition().toString();\n" +
        "          allMarkers[loc] = marker;\n" +
        "        };\n" +
        "\n" +
        "        var addMarkersFromList = function(markerList) {\n" +
        "          //TODO link this in to create marker if not already a marker object?\n" +
        "          for (i = 0; i < markerList.length; i++) {\n" +
        "            if (!getMarker(markerList[i].prototype.getPosition().toString())) {\n" +
        "              addMarker(markerList[i]);\n" +
        "            }\n" +
        "          }\n" +
        "          showMarkers(true);\n" +
        "        };\n" +
        "        \n" +
        "        var locationFromLatLngCoords = function(lat, lng){\n" +
        "          var errorParsing = false;\n" +
        "          if (isNaN(lat) || isNaN(lng)) errorParsing = true;\n" +
        "          //DO WE NEED TO DO THIS? the LatLng will clamp or wrap the coordinates. \n" +
        "          // if (lat < -90 || lat > 90) errorParsing = true;\n" +
        "          // if (lng < -180 || lng > 180) errorParsing = true;\n" +
        "\n" +
        "          if (errorParsing) {\n" +
        "            return null;\n" +
        "          } else {\n" +
        "            return new google.maps.LatLng(lat, lng);\n" +
        "          }\n" +
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
        "          var locationSplit = locationText.split(',');\n" +
        "          if (locationSplit.length === 2){\n" +
        "            lat = parseFloat(locationSplit[0]);\n" +
        "            lng = parseFloat(locationSplit[1]);\n" +
        "            return locationFromLatLngCoords(lat, lng);\n" +
        "          } else {\n" +
        "            return null;\n" +
        "          }\n" +
        "        };\n" +
        "\n" +
        "        var showMarkers = function (show){\n" +
        "          if (show) {\n" +
        "            for (var key in allMarkers){\n" +
        "              getMarker(key).setMap(map);\n" +
        "            }\n" +
        "          } else {\n" +
        "            for (var key in allMarkers){\n" +
        "              getMarker(key).setMap(null);\n" +
        "            }\n" +
        "          }\n" +
        "        };\n" +
        "\n" +
        "        //Geolocation service\n" +
        "        var getGeolocationFromAddress = function (address){\n" +
        "          var geocoder = new google.maps.Geocoder();\n" +
        "          geocoder.geocode({address: address}, geolocationResults)\n" +
        "        };\n" +
        "\n" +
        "        function geolocationResults(results, status){\n" +
        "          if (status === 'OK') {\n" +
        "            var firstLocationFound = results[0].geometry.location;\n" +
        "            if (firstLocationFound){\n" +
        "              console.log(\"First loc:\", firstLocationFound);\n" +
        "              var marker = createMarker(firstLocationFound);\n" +
        "              var markerJson = createJsonMarkerFromId(marker.prototype.getPosition().toString());\n" +
        "              // return firstLocationFound;\n" +
        "            } else {\n" +
        "              console.log('No location found!');\n" +
        "            }\n" +
        "          } else if (status === \"ZERO_RESULTS\"){\n" +
        "            console.log('No results found for that particular address.');\n" +
        "          } else {\n" +
        "            console.log('No results found. Status of Geolocation call: ' + status);\n" +
        "          }\n" +
        "        };\n" +
        "\n" +
        "        var deleteAllMarkers = function() {\n" +
        "          showMarkers(false);\n" +
        "          allMarkers = {};\n" +
        "        };\n" +
        "\n" +
        "        var deleteMarker = function(markerId) {\n" +
        "          if (allMarkers[markerId]) {\n" +
        "            allMarkers[markerId].setMap(null);\n" +
        "            delete allMarkers[markerId];\n" +
        "          }\n" +
        "\n" +
        "        };\n" +
        "\n" +
        "        var setZoom = function(zoom) {\n" +
        "          if (zoom >= 0 && zoom <= 19){\n" +
        "            map.setZoom(zoom);\n" +
        "          } else { // Exception handling is also done on Android side\n" +
        "            console.log('Zoom value ' + zoom + ' is not in the valid range 0-19');\n" +
        "          }\n" +
        "        };\n" +
        "\n" +
        "        var getZoom = function() { return map.zoom; };\n" +
        "\n" +
        "        var panToMarker = function(markerId) {\n" +
        "          var markerToPanTo = getMarker(markerId);\n" +
        "          if (markerToPanTo)\n" +
        "            map.panTo(markerToPanTo.getPosition());\n" +
        "        };\n" +
        "\n" +
        "        //API for the thisMap object: main entry object for functionality\n" +
        "        return {\n" +
        "          initialize: initialize,\n" +
        "          showCenter: showCenter,\n" +
        "          setCenter: setCenter,\n" +
        "          getCenter: getCenter,\n" +
        "          getGoogleMapObject: getGoogleMapObject, // For Debugging (not used from the component).\n" +
        "          //getMarkerFunctions: getMarkerFunctions,\n" +
        "          getAllMarkers: getAllMarkers,\n" +
        "          getMarker: getMarker,\n" +
        "          createMarker: createMarker,\n" +
        "          createMarkersFromList: createMarkersFromList,\n" +
        "          addMarkersFromList: addMarkersFromList,\n" +
        "          locationFromLatLngCoords: locationFromLatLngCoords,\n" +
        "          locationFromTextCoords: locationFromTextCoords,\n" +
        "          getGeolocationFromAddress: getGeolocationFromAddress,\n" +
        "          showMarkers: showMarkers,\n" +
        "          deleteMarker: deleteMarker,\n" +
        "          deleteAllMarkers: deleteAllMarkers,\n" +
        "          setZoom: setZoom,\n" +
        "          getZoom: getZoom,\n" +
        "          panToMarker: panToMarker,\n" +
        "          getMap: getMap\n" +
        "        }\n" +
        "      //TODO (jos) Magic numbers: the center of the map will come from Android\n" +
        "      } (42.3598, -71.0921, true, 2); //Auto initialize the thisMap object\n" +
        "\n" +
        "\n" +
        "      /**\n" +
        "       * This function returns an object with certain methods exposed as its API. The functionality\n" +
        "       * of this object is related to management of markers in the map.\n" +
        "       * @returns an Object with methods related to Marker management.\n" +
        "       * @param map the map to associate the markers with. thisMap object from above.\n" +
        "       */\n" +
        "      function markerContainer(map, lat, lng, title, icon, clickable, visible) {\n" +
        "\n" +
        "        var marker;\n" +
        "        var id;\n" +
        "\n" +
        "        function initialize() {\n" +
        "          var markerOptions = {\n" +
        "              map: map,\n" +
        "              position: {lat: lat, lng: lng},\n" +
        "              title: title,\n" +
        "              icon: icon,\n" +
        "              clickable: clickable,\n" +
        "              visible: visible\n" +
        "            };\n" +
        "\n" +
        "          marker = new google.maps.Marker(markerOptions);\n" +
        "          id = marker.getPosition().toString();\n" +
        "          return marker;\n" +
        "        }\n" +
        "\n" +
        "      //this exists in the map container\n" +
        "      // markerContainer.deleteMarker = function() {\n" +
        "      //   this.marker.setMap(null);\n" +
        "      //   this.id = null;\n" +
        "\n" +
        "      //   delete this.marker; //does this work??\n" +
        "      // };\n" +
        "\n" +
        "      //TODO should equality be if their locations/IDs are the same, instead of the same object?\n" +
        "      var equals = function(otherMarker) {\n" +
        "        return marker === otherMarker;\n" +
        "      };\n" +
        "\n" +
        "      var getId = function() {\n" +
        "        return id;\n" +
        "      };\n" +
        "\n" +
        "      var getIcon = function() {\n" +
        "        return marker.icon;\n" +
        "      };\n" +
        "\n" +
        "      var getMap = function() {\n" +
        "        return marker.map;\n" +
        "      };\n" +
        "\n" +
        "      var getTitle = function() {\n" +
        "        return marker.title;\n" +
        "      };\n" +
        "\n" +
        "      var getClickable = function() {\n" +
        "        return marker.clickable;\n" +
        "      };\n" +
        "\n" +
        "      var getPosition = function() {\n" +
        "        return marker.position;\n" +
        "      };\n" +
        "\n" +
        "      var getLatitude = function() {\n" +
        "        return marker.position.lat();\n" +
        "      };\n" +
        "\n" +
        "      var getLongitude = function() {\n" +
        "        return marker.position.lng();\n" +
        "      }\n" +
        "\n" +
        "      var getVisible = function() {\n" +
        "        return marker.visible;\n" +
        "      };\n" +
        "\n" +
        "      var getMarkerObject = function() {\n" +
        "        return marker;\n" +
        "      }\n" +
        "\n" +
        "      var setIcon = function(icon) {\n" +
        "        marker.setIcon(icon);\n" +
        "      };\n" +
        "\n" +
        "      var setMap = function(map) {\n" +
        "        marker.setMap(map);\n" +
        "      };\n" +
        "\n" +
        "      var setTitle = function(title) {\n" +
        "        marker.setTitle(title);\n" +
        "      };\n" +
        "\n" +
        "      var setClickable = function(clickable) {\n" +
        "        marker.setClickable(clickable);\n" +
        "      }\n" +
        "\n" +
        "      var setPosition = function(lat, lng) {\n" +
        "        marker.setPosition({lat: lat, lng: lng});\n" +
        "      };\n" +
        "\n" +
        "      var setVisible = function(visible) {\n" +
        "        marker.setVisible(visible);\n" +
        "      };\n" +
        "\n" +
        "      var createJsonMarker = function(){\n" +
        "        var markerObject = {\n" +
        "          lat: marker.getPosition().lat(),\n" +
        "          lng: marker.getPosition().lng(),\n" +
        "          title: marker.title || '',\n" +
        "          info: (marker.info && marker.info.content) ?\n" +
        "              marker.info.content : ''\n" +
        "        }\n" +
        "        var markerJson = JSON.stringify(markerObject);\n" +
        "\n" +
        "        return markerJson;\n" +
        "      };\n" +
        "\n" +
        "      //TODO Does this work? I don't understand the callback...\n" +
        "      // var addListenersForMarkers = function (add) {\n" +
        "      //   if (add){\n" +
        "      //     google.maps.event.addListener(marker.map, 'click', function(event) {\n" +
        "      //       var markerJson = createJsonMarker();\n" +
        "      //     });\n" +
        "      //   } \n" +
        "      //   else\n" +
        "      //     google.maps.event.clearListeners(map,'click');\n" +
        "      // };\n" +
        "\n" +
        "      // InfoWindow functions\n" +
        "      var createInfoWindow = function(content) {\n" +
        "        var infoWindow = new google.maps.InfoWindow({\n" +
        "          content: content\n" +
        "        });\n" +
        "        marker.info = infoWindow;\n" +
        "      };\n" +
        "\n" +
        "      var openInfoWindow = function(){\n" +
        "        if (marker && marker.info)\n" +
        "          marker.info.open(marker.map, marker);\n" +
        "      };\n" +
        "\n" +
        "      var closeInfoWindow = function(){\n" +
        "        if (marker && marker.info)\n" +
        "          marker.info.close();\n" +
        "      };\n" +
        "\n" +
        "      return {\n" +
        "        // deleteMarker: deleteMarker,\n" +
        "        initialize: initialize,\n" +
        "        equals: equals,\n" +
        "        getId: getId,\n" +
        "        getIcon: getIcon,\n" +
        "        getMap: getMap,\n" +
        "        getTitle: getTitle,\n" +
        "        getClickable: getClickable,\n" +
        "        getPosition: getPosition,\n" +
        "        getLatitude: getLatitude,\n" +
        "        getLongitude: getLongitude,\n" +
        "        getVisible: getVisible,\n" +
        "        getMarkerObject: getMarkerObject,\n" +
        "        setIcon: setIcon,\n" +
        "        setMap: setMap,\n" +
        "        setTitle: setTitle,\n" +
        "        setClickable: setClickable,\n" +
        "        setPosition: setPosition,\n" +
        "        setVisible: setVisible,\n" +
        "        createJsonMarker: createJsonMarker,\n" +
        "        createInfoWindow: createInfoWindow,\n" +
        "        openInfoWindow: openInfoWindow,\n" +
        "        closeInfoWindow: closeInfoWindow\n" +
        "      }\n" +
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
        "        ERROR_ILLEGAL_COORDS_FORMAT: 3102,\n" +
        "        ERROR_PARSING_MARKERS_LIST: 3103,\n" +
        "        ERROR_INVALID_MARKER: 3104,\n" +
        "        ERROR_NO_GEOLOCATION_RESULTS: 3106,\n" +
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
        "        sendCenterMarkerToAndroid: function(jsonMarker) {\n" +
        "          if (typeof AppInventorMap !== 'undefined')\n" +
        "            AppInventorMap.handleMarker(jsonMarker);\n" +
        "\n" +
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
        "        }\n" +
        "\n" +
        "      };\n" +
        "\n" +
        "      google.maps.event.addDomListener(window, 'load', mapContainer.initialize);\n" +
        "\n" +
        "    </script>\n" +
        "  </head>\n" +
        "  <body>\n" +
        "    <div id=\"map-canvas\"></div>\n" +
        "  </body>\n" +
        "</html>\n";

//    webview.loadUrl("file:///sdcard/AppInventor/assets/map.html");
    webview.loadDataWithBaseURL(null, map, "text/html", "utf-8", null);

  }

  /**
   * Specifies whether users will be able to add markers by clicking on the map.
   *
   * @param allowUserMarkers  {@code true} markers allowed, {@code false} markers not allowed
   */
  @SimpleFunction(description = "Specifies whether users will be able to add markers by clicking " +
      "on the map. True will make the map listen for clicks. False will unbind the listener.")
  //TODO (ajcolter) is there a better name that could be used?
  //TODO (ajcolter) make a map property instead?
  public void AllowUserMarkers(boolean allowUserMarkers) {
    webview.loadUrl("javascript:mapContainer.getMarkerFunctions().addListenersForMarkers(" +
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
    webview.loadUrl("javascript:mapContainer.showCenter(" + show + ")");
  }

  @SimpleFunction(description = "Re-set the center of the map and pan to it. The coordinates are " +
      "in the format (lat, lng), for instance a text block containing '25, 25'.")
  public void SetCenter(String coords) {
    webview.loadUrl("javascript:mapContainer.setCenter('" + coords + "')");
  }

  public void GetCenter() {
    //TODO (aj) write handler to capture this value
    webview.loadUrl("javascript:mapContainer.getCenter()");
//    return new YailList();
  }

  public String GetGoogleMapObject() {
    //TODO (aj) write handler to capture this value
    webview.loadUrl("javascript:mapContainer.getGoogleMapObject()");
    return "";
  }

  @SimpleFunction(description = "Sets the Zoom to the specified level by the zoom parameter.")
  public void SetZoom(int zoom) {
    if (zoom >= 0 && zoom < 20)
      webview.loadUrl("javascript:mapContainer.setZoom(" + zoom + ")");
    else
      form.dispatchErrorOccurredEvent(form, "AddMarker", ErrorMessages.ERROR_INVALID_ZOOM_LEVEL);
  }

  @SimpleFunction(description = "Gets the Zoom level by the zoom parameter.")
  public int GetZoom() {
    webview.loadUrl("javascript:mapContainer.getZoom()");
    //TODO (ajcolter) how do I get the return value here??
    return 0;
  }

  @SimpleFunction(description = "Shows all the markers currently available on the map, " +
      "even those that might have been hidden by developer or user action.")
  public void ShowMarkers(boolean show) {
    webview.loadUrl("javascript:mapContainer.getMarkerFunctions().showMarkers(" + show + ")");
  }

//  @SimpleFunction(description = "Deletes all markers currently on the map. This is a full delete." +
//      " To simply hide markers from view please use the HideMarkers block.")
//  public void DeleteMarkersFromMap() {
//    webview.loadUrl("javascript:mapContainer.getMarkerFunctions().deleteMarkers()");
//  }

  @SimpleFunction(description = "Places a marker in a location by providing its address instead " +
      "of coordinates. An example could be '32 Vassar St. Cambridge MA'")
  public void GeoLocate(String address) {
    //TODO (aj) why doesn't this return the geolocation results? how is a map/marker going to use it? is it useful?
    webview.loadUrl("javascript:mapContainer.getGeolocationFromAddress('" + address + "')");
  }

  @SimpleFunction(description = "Adds a marker to the map. To create a Marker use the Marker " +
      "block, also available in WebMap.")
  public void CreateMarkerObjectOnMap(String location, String title) {
    //TODO (aj) create more of these blocks to take locations that are also google.maps.latlng instances, etc? or is
    // string just simpler?
    webview.loadUrl("javascript:mapContainer.createMarker("+location+ "," + title + ")");
  }
//  public void AddMarkerToMap(YailList marker) {
//    String [] markerData = marker.toStringArray();
//    String javaScriptCommand = "javascript:mapContainer.getMarkerFunctions().addAIMarker(mapContainer" +
//        ".getMarkerFunctions().locationFromLatLngCoords(" + markerData[0] + ", " + markerData[1] +
//        "),'" + markerData[2] + "', '" + markerData[3] + "')";
//
//    webview.loadUrl(javaScriptCommand);
//  }
  @SimpleFunction(description = "Creates a marker object; adds it to the map if map is not null.")
  public void CreateMarkerObject(String map, long latitude, long longitude, String title, String icon, boolean
      clickable, boolean visible) {
    webview.loadUrl("javascript:markerContainer(" +map+ "," + latitude + "," + longitude + "," + title + "," +
        icon + "," + clickable + "," +visible+")");
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

  @SimpleFunction(description = "Shows a particular marker on the map by its id.")
  public void ShowMarker(YailList marker, boolean show) {
    String markerId = idForMarker(marker);
    webview.loadUrl("javascript:thisMap.getMarkerFunctions().showMarker('" + markerId+ "', " +
        show + ")");
  }

//TODO (aj) fix this -- make a new one based on removing the marker object from the map?
//  @SimpleFunction(description = "Shows a particular marker on the map by its id.")
//  public void ShowMarker(YailList marker, boolean show) {
//    String markerId = idForMarker(marker);
//    webview.loadUrl("javascript:mapContainer.getMarkerFunctions().showMarker('" + markerId+ "', " +
//        show + ")");
//  }


  private String idForMarker(YailList marker) {
    //TODO (aj) still using yaillists for markers? probably want to make the marker class right?
    //String markerId = webview.loadUrl("javascript:"+marker+".prototype.getId();");//"(" + marker.getString(0) + ",
    // " + marker.getString(1) +
    // ")";
    //return markerId;
    return "Location";
  }

  @SimpleFunction(description = "Pan the map towards a particular marker on the map by its id.")
  public void PanToMarker(YailList marker) {
    String markerId = idForMarker(marker);
    webview.loadUrl("javascript:mapContainer.panToMarker('" + markerId+ "')");
  }

//  @SimpleFunction(description = "A new marker with all properties unchanged except for the title of" +
//      " the particular marker passed as a parameter. This title could be used as" +
//      " a more human readable id to show and select markers in a ListView or ListPicker.")
//  public YailList SetMarkerTitle(YailList marker, String title) {
//    String markerId = idForMarker(marker);
//    //create a new Marker object from the previous one. We cannot simply set position 3 to the
//    // new title because YailList does not implement the set method.
//    String markerValues [] = marker.toStringArray();
//    markerValues[2] = title;
//    YailList newMarker = YailList.makeList(markerValues);
//    String javaScriptCommand = "javascript:mapContainer.getMarkerFunctions().setMarkerTitle('" +
//        markerId + "', '" + title + "')";
//    Log.d(LOG_TAG, "JS command for SetMarkerTitle is: " + javaScriptCommand);
//    Log.d(LOG_TAG, "markerId for SetMarkerTitle is: " + markerId);
//    webview.loadUrl(javaScriptCommand);
//
//    return newMarker;
//  }

//  @SimpleFunction(description = "Get the title of a particular marker.")
//  public String GetMarkerTitle(YailList marker) {
//    return marker.getString(2);
//  }

//  @SimpleFunction(description = "Get the InfoWindow content of a particular marker.")
//  public String GetMarkerInfoWindowContent(YailList marker) {
//    return marker.getString(3);
//  }

  //TODO (aj) update these functions based on how we use the marker object
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
  public void GetAllMarkersFromMap() { //may make more sense to name this GetListOfMarkers
    webview.loadUrl("javascript:mapContainer.getAllMarkers()");
  }

  @SimpleFunction(description = "Visualizes a list of lists of markers in the map. This block " +
      "could be used in combination with RequestListOfMarkersFromMap to manage different lists of " +
      "markers within the same map. Note that the format must be a list of lists, " +
      "and those lists should contain 4 elements (id, coordinates, title, content).")
  public void AddMarkersFromList(YailList list) {
    //TODO (ajcolter) make sure it is a List of Lists and convert it to JSON
    String markersJSON = createStringifiedJSONFromYailList(list);
    webview.loadUrl("javascript:mapContainer.addMarkersFromList('" + markersJSON + "')");
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

//  @SimpleFunction(description = "Creates an InfoWindow for a particular marker that can be " +
//      "displayed on particular events (in combination with the open and close infoWindow blocks.)")
//  public YailList CreateInfoWindow(YailList marker, String content) {
//    String markerId = idForMarker(marker);
//    //create a new Marker object from the previous one. We cannot simply set position 3 to the
//    // new title because YailList does not implement the set method.
//    String markerValues [] = marker.toStringArray();
//    markerValues[3] = content;
//    YailList newMarker = YailList.makeList(markerValues);
//    webview.loadUrl("javascript:mapContainer.getMarkerFunctions().createInfoWindow('" +
//        markerId + "', '" + content + "')");
//
//    return newMarker;
//  }

//  @SimpleFunction(description = "Open an InfoWindow for a particular marker.")
//  public void OpenInfoWindow(YailList marker) {
//    String markerId = idForMarker(marker);
//    webview.loadUrl("javascript:mapContainer.getMarkerFunctions().openInfoWindow('" + markerId + "')");
//  }
//
//  @SimpleFunction(description = "Close an InfoWindow for a particular marker.")
//  public void CloseInfoWindow(YailList marker) {
//    String markerId = idForMarker(marker);
//    webview.loadUrl("javascript:mapContainer.getMarkerFunctions().closeInfoWindow('" + markerId+ "')");
//  }

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

//  /**
//   * Event triggered by a marker being clicked on the map.
//   * @param marker a stringified representation of the marked being clicked
//   */
//  @SimpleEvent(description = "Event triggered by a marker being clicked on the map.")
//  public void MarkerClicked(final String marker) {
//      YailList markerYail = createMarkerFromStringifiedJson(marker);
//      EventDispatcher.dispatchEvent(this, "MarkerClicked", markerYail);
//  }

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

//  /**
//   * Event triggered by a marker being doubled clicked on the map. NOTE that a MarkerClicked event
//   * will always be triggered at the same time that a marker is being double clicked.
//   * @param marker a stringified representation of the marked being double clicked
//   */
//  @SimpleEvent(description = "Event triggered by a marker being doubled clicked on the map. NOTE " +
//      "that a MarkerClicked event will always be triggered at the same time that a marker is " +
//      "being double clicked.")
//  //TODO (ajcolter) make a work around for this. It's silly to have a clicked and a double clicked event triggered at the same time.
//  public void MarkerDoubleClicked(final String marker) {
//    YailList markerYail = createMarkerFromStringifiedJson(marker);
//    EventDispatcher.dispatchEvent(this, "MarkerDoubleClicked", markerYail);
//  }

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
    //TODO (aj) can this happen on the JS side? Can it pull the gps info from the phone?
    EventDispatcher.dispatchEvent(this, "MapIsReady");
  }

//  @SimpleEvent(description = "A user has added a marker by clicking on the map. This event will " +
//      "trigger only if users are allowed to add markers by using the block AllowUserMarkers. " +
//      "The marker is returned so that actions can be applied to that particular marker.")
//  public void UserMarkerAdded(final String marker) {
//    YailList markerYail = createMarkerFromStringifiedJson(marker);
//    EventDispatcher.dispatchEvent(this, "UserMarkerAdded", markerYail);
//  }

//  @SimpleEvent(description = "A marker has been added to the map by using the Geolocate block. " +
//      "The marker is returned so that actions can be applied to that particular marker.")
//  public void GeolocationMarkerAdded(final String marker, final String formattedAddress) {
//    YailList markerYail = createMarkerFromStringifiedJson(marker);
//    EventDispatcher.dispatchEvent(this, "GeolocationMarkerAdded", markerYail, formattedAddress);
//  }

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
    public YailList getCenterMarker(final String jsonMarker) {
      Log.d(LOG_TAG, "Center marker: " + jsonMarker);
      return createMarkerFromStringifiedJson(jsonMarker);
    }

//    @JavascriptInterface
//    public void handleMarker(final String jsonMarker) {
//      Log.d(LOG_TAG, "Marker clicked: " + jsonMarker);
//      webViewForm.runOnUiThread(new Runnable() {
//        @Override
//        public void run() {
//          //MarkerClicked(jsonMarker);
//        }
//      });
//    }
//
//    @JavascriptInterface
//    public void handleDoubleMarker(final String jsonMarker) {
//      Log.d(LOG_TAG, "Marker DOUBLED clicked: " + jsonMarker);
//      webViewForm.runOnUiThread(new Runnable() {
//        @Override
//        public void run() {
//          //MarkerDoubleClicked(jsonMarker);
//        }
//      });
//    }

//    /**
//     * Receives a JSON object with marker data from the map and converts each marker to a List
//     * that will be added to a YailList of markers. The result is a list of lists with marker data.
//     * @param markersList the stringified JSON object coming from the map.
//     */
//    @JavascriptInterface
//    public void storeMarkers(final String markersList) {
//      try {
//        JSONArray markersJSON = new JSONArray(markersList);
//        List<YailList> markerValues = new ArrayList<YailList>();
//
//        for(int i = 0; i < markersJSON.length(); i++){
//          List<String> aMarker = new ArrayList<String>();
//          aMarker.add(markersJSON.getJSONObject(i).getString("lat"));
//          aMarker.add(markersJSON.getJSONObject(i).getString("lng"));
//          aMarker.add(markersJSON.getJSONObject(i).getString("title"));
//          aMarker.add(markersJSON.getJSONObject(i).getString("content"));
//          markerValues.add(YailList.makeList(aMarker));
//        }
//
//        final YailList markersYailList = YailList.makeList(markerValues);
//        webViewForm.runOnUiThread(new Runnable() {
//          @Override
//          public void run() {
//            MarkersFromMapReceived(markersYailList);
//          }
//        });
//      } catch (JSONException e) {
//        webViewForm.dispatchErrorOccurredEvent(form, "storeMarkers",
//            ErrorMessages.ERROR_PARSING_MARKERS_LIST);
//      }
//    }

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

//    @JavascriptInterface
//    public void userMarkerAdded(final String markerJson) {
//      webViewForm.runOnUiThread(new Runnable() {
//        @Override
//        public void run() {
//          UserMarkerAdded(markerJson);
//        }
//      });
//    }

//    @JavascriptInterface
//    public void geolocationMarkerAdded(final String markerJson, final String formattedAddress) {
//      webViewForm.runOnUiThread(new Runnable() {
//        @Override
//        public void run() {
//          GeolocationMarkerAdded(markerJson, formattedAddress);
//        }
//      });
//    }

  }
}
