package com.github.ashvalde.photonfy;

import static java.security.AccessController.getContext;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.github.ASHVALDE.DataPackage;
import com.github.ASHVALDE.Photonfy;
import com.github.ASHVALDE.PhotonfyCodes;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_BT_PERM = 123;
    Photonfy photonfy;
    private static final UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "MainActivity";

    private WebView webView;
    private BluetoothAdapter bluetoothAdapter;
    BluetoothSocket socket;


    @Override
    @SuppressLint({"SetJavaScriptEnabled"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // Fixed portrait orientation
        // Redirige System.out a Logcat para facilitar el debug
        System.setOut(new PrintStream(new LogOutputStream("MyLibrary", Log.DEBUG), true));

        initializeWebView();
        initializeBluetooth();
    }

    /**
     * Configura el WebView, habilita JavaScript y añade la interfaz para comunicación con JS.
     */
    private void initializeWebView() {
        webView = findViewById(R.id.web_view);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(this, "Android");
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                evaluateJavascript("loadDevices()");
            }

            // Crucial: Override shouldInterceptRequest to handle asset loading correctly
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (url.startsWith("file:///android_asset/")) { // Check if it's an asset URL
                    try {
                        String path = url.substring("file:///android_asset/".length()); // Extract relative path
                        return new android.webkit.WebResourceResponse(
                                "text/html", // Set the correct MIME type
                                "UTF-8", // Set the correct encoding
                                getAssets().open(path)); // Open the asset with the correct relative path
                    } catch (IOException e) {
                        return super.shouldInterceptRequest(view, url); // Handle error (e.g., return a 404 response)
                    }
                }
                return super.shouldInterceptRequest(view, url); // Let the default handler handle other URLs
            }
        });


        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Se notifica al JS que cargue los dispositivos emparejados
                evaluateJavascript("loadDevices()");
            }
        });
        if(socket==null || !socket.isConnected()){
            webView.loadUrl("file:///android_asset/home.html");
        }else{
            webView.loadUrl("file:///android_asset/PrincipalMenu/index.html");
        }
    }

    /**
     * Inicializa el adaptador Bluetooth y verifica los permisos necesarios.
     */
    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBluetoothPermissions();
    }

    /**
     * Verifica si el permiso de Bluetooth está concedido; en caso contrario lo solicita.
     */
    private void checkBluetoothPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setMessage("Habilita los permisos de Bluetooth para continuar")
                    .setPositiveButton("OK", (dialog, which) ->
                            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_PERM))
                    .setCancelable(false)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_BT_PERM) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setMessage("Permisos de Bluetooth requeridos")
                        .setPositiveButton("Salir", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Devuelve un String JSON con los dispositivos Bluetooth emparejados.
     * Este método es accesible desde JavaScript.
     *
     * @return JSON de dispositivos emparejados.
     */
    @JavascriptInterface
    @SuppressLint("MissingPermission")
    public String getPairedDevices() {
        JSONArray devicesArray = new JSONArray();

        if (bluetoothAdapter == null || !hasBluetoothPermission()) {
            return devicesArray.toString();
        }

        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            try {
                JSONObject deviceJson = new JSONObject();
                deviceJson.put("name", device.getName());
                deviceJson.put("address", device.getAddress());
                devicesArray.put(deviceJson);
            } catch (Exception e) {
                Log.e(TAG, "Error al procesar la información del dispositivo", e);
            }
        }
        return devicesArray.toString();
    }
    @JavascriptInterface
    public void loadMainMenu(){

        runOnUiThread(()->{
            webView.loadUrl("file:///android_asset/PrincipalMenu/index.html");

        });
    }
    @JavascriptInterface
    public void getFieldsFromSpectrometer(String command) throws JSONException, IOException {
        JSONObject jsonResponse = new JSONObject();
        switch (command) {
            case "UPDATE_INTEGRATION_TIME":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_INTEGRATION_TIME) {

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid(" + jsonResponse.toString() + ")");
                            photonfy.removeListener(this);
                        }

                    }
                });
                photonfy.sender.GET_INTEGRATION_TIME();

                break;
            case "UPDATE_GAIN":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_GAIN){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_GAIN();
                break;
            case "UPDATE_DEFAULT_TRANSFER_FUNCTION":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_DEFAULT_TRANSFER_FUNCTION){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_DEFAULT_TRANSFER_FUNCTION();
                break;
            case "UPDATE_WAVELENGTH_CALIBRATION":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_WAVELENGTH_CALIBRATION){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_WAVELENGTH_CALIBRATION();
                break;
            case "UPDATE_DEFAULT_WAVELENGTH_CALIBRATION":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_DEFAULT_WAVELENGTH_CALIBRATION){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_DEFAULT_WAVELENGTH_CALIBRATION();
                break;

            case "UPDATE_BACKGROUND_CALIBRATIONS":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_BACKGROUND_CALIBRATIONS){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_BACKGROUND_CALIBRATIONS();
                break;
            case "UPDATE_DEFAULT_BACKGROUND_CALIBRATIONS":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_DEFAULT_BACKGROUND_CALIBRATIONS){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_DEFAULT_BACKGROUND_CALIBRATIONS();
                break;

            case "UPDATE_BACKGROUND_COEFFICIENTS":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_BACKGROUND_COEFFICIENTS){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_BACKGROUND_COEFFICIENTS();
                break;

            case "UPDATE_DEFAULT_BACKGROUND_COEFFICIENTS":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_DEFAULT_BACKGROUND_COEFFICIENTS){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_DEFAULT_BACKGROUND_COEFFICIENTS();
                break;
            case "UPDATE_LUX_CALIBRATION":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_LUX_CALIBRATION){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_LUX_CALIBRATION();
                break;
            case "UPDATE_DEFAULT_LUX_CALIBRATION":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_DEFAULT_LUX_CALIBRATION){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_DEFAULT_LUX_CALIBRATION();
                break;
            case "UPDATE_VIDEO_SAMPLE_RATE":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_VIDEO_SAMPLE_RATE){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_VIDEO_SAMPLE_RATE();
                break;
            case "UPDATE_TIME":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_TIME){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_TIME();
                break;
            case "SET_BLUETOOTH_NAME":
                JSONArray jsonArray = new JSONArray();
                jsonArray.put("");
                jsonResponse.put("Values",jsonArray);
                evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                break;

            case "UPDATE_TRANSFER_FUNCTION":
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(PhotonfyCodes.fromInt(dataPackage.token) == PhotonfyCodes.GET_TRANSFER_FUNCTION){

                            try {
                                JSONArray jsonArray = new JSONArray();
                                for (String value : dataPackage.getResponse(true).split(",")) {
                                    jsonArray.put(value.trim()); // Trim whitespace for cleaner data
                                }
                                jsonResponse.put("Values", jsonArray);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");
                            photonfy.removeListener(this);
                        }
                    }
                });
                photonfy.sender.GET_TRANSFER_FUNCTION();

                break;
            default:
                jsonResponse.put("Values","[]").toString();
                evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")");

                break;
        }
    }
    @JavascriptInterface
    public void sendCommand(String command, String args) throws JSONException, IOException {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<String>>() {
        }.getType();
        List<String> stringList = gson.fromJson(args, listType);

        // No Log.d here as per user request, but you can keep them for debugging if needed
        // Log.d("comando", "Command received: " + command);
        // Log.d("comando", "Arguments received: " + args);
        // Log.d("comando", "List of Strings: " + stringList);


        try {
            switch (command) {
                case "UPDATE_INTEGRATION_TIME":
                    if (stringList.size() != 1) {
                        showToastError("Error in UPDATE_INTEGRATION_TIME: Expected 1 argument, got " + stringList.size());
                        return;
                    }
                    short integrationTime = parseShortSafely(stringList.get(0), "integrationTime");
                    if (integrationTime < 5 || integrationTime > 7000) {
                        showToastError("Error in UPDATE_INTEGRATION_TIME: Integration Time out of range [5-7000 ms], got: " + integrationTime);
                        return;
                    }
                    photonfy.sender.SET_INTEGRATION_TIME(integrationTime);
                    break;

                case "UPDATE_GAIN":
                    if (stringList.size() != 1) {
                        showToastError("Error in UPDATE_GAIN: Expected 1 argument, got " + stringList.size());
                        return;
                    }
                    short gainShort = parseShortSafely(stringList.get(0), "gain");
                    byte gainByte = (byte) gainShort; // Cast to byte as per protocol
                    if (gainShort < 0 || gainShort > 1) {
                        showToastError("Error in UPDATE_GAIN: Gain value out of range [0-1], got: " + gainShort);
                        return;
                    }
                    photonfy.sender.SET_GAIN(gainByte);
                    break;

                case "UPDATE_TRANSFER_FUNCTION":
                    if (stringList.size() != 81) {
                        showToastError("Error in UPDATE_TRANSFER_FUNCTION: Expected 81 arguments, got " + stringList.size());
                        return;
                    }
                    List<Float> LIST_TRANSFER_FUNCTION = parseFloatListSafely(stringList, "transferFunction");
                    photonfy.sender.SET_TRANSFER_FUNCTION(LIST_TRANSFER_FUNCTION);
                    break;

                case "UPDATE_DEFAULT_TRANSFER_FUNCTION":
                    if (stringList.size() != 81) {
                        showToastError("Error in UPDATE_DEFAULT_TRANSFER_FUNCTION: Expected 81 arguments, got " + stringList.size());
                        return;
                    }
                    List<Float> LIST_DEFAULT_TRANSFER_FUNCTION = parseFloatListSafely(stringList, "defaultTransferFunction");
                    photonfy.sender.SET_DEFAULT_TRANSFER_FUNCTION(LIST_DEFAULT_TRANSFER_FUNCTION);
                    break;

                case "UPDATE_WAVELENGTH_CALIBRATION":
                    if (stringList.size() != 6) {
                        showToastError("Error in UPDATE_WAVELENGTH_CALIBRATION: Expected 6 arguments, got " + stringList.size());
                        return;
                    }
                    List<Float> LIST_WAVELENGTH_CALIBRATION = parseFloatListSafely(stringList, "wavelengthCalibration");
                    photonfy.sender.SET_WAVELENGTH_CALIBRATION(LIST_WAVELENGTH_CALIBRATION);
                    break;

                case "UPDATE_DEFAULT_WAVELENGTH_CALIBRATION":
                    if (stringList.size() != 6) {
                        showToastError("Error in UPDATE_DEFAULT_WAVELENGTH_CALIBRATION: Expected 6 arguments, got " + stringList.size());
                        return;
                    }
                    List<Float> LIST_DEFAULT_WAVELENGTH_CALIBRATION = parseFloatListSafely(stringList, "defaultWavelengthCalibration");
                    photonfy.sender.SET_DEFAULT_WAVELENGTH_CALIBRATION(LIST_DEFAULT_WAVELENGTH_CALIBRATION);
                    break;

                case "UPDATE_BACKGROUND_CALIBRATIONS":
                    if (stringList.size() != 3) {
                        showToastError("Error in UPDATE_BACKGROUND_CALIBRATIONS: Expected 3 arguments, got " + stringList.size());
                        return;
                    }
                    short count = parseShortSafely(stringList.get(0), "backgroundCount");
                    short integration = parseShortSafely(stringList.get(1), "backgroundIntegration");
                    float Temperature = parseFloatSafely(stringList.get(2), "backgroundTemperature");
                    photonfy.sender.SET_BACKGROUND(count, integration, Temperature);
                    break;

                case "UPDATE_DEFAULT_BACKGROUND_CALIBRATIONS":
                    if (stringList.size() != 3) {
                        showToastError("Error in UPDATE_DEFAULT_BACKGROUND_CALIBRATIONS: Expected 3 arguments, got " + stringList.size());
                        return;
                    }
                    short count_DEFAULT = parseShortSafely(stringList.get(0), "defaultBackgroundCount");
                    short integration_DEFAULT = parseShortSafely(stringList.get(1), "defaultBackgroundIntegration");
                    float Temperature_DEFAULT = parseFloatSafely(stringList.get(2), "defaultBackgroundTemperature");
                    photonfy.sender.SET_DEFAULT_BACKGROUND(count_DEFAULT, integration_DEFAULT, Temperature_DEFAULT);
                    break;

                case "UPDATE_BACKGROUND_COEFFICIENTS":
                    if (stringList.size() != 14 * 3) { // 14 integration times * 3 coefficients
                        showToastError("Error in UPDATE_BACKGROUND_COEFFICIENTS: Expected 42 arguments, got " + stringList.size());
                        return;
                    }
                    List<Float> LIST_BACKGROUND_COEFFICIENTS = parseFloatListSafely(stringList, "backgroundCoefficients");
                    photonfy.sender.SET_BACKGROUND_COEFFICIENTS(LIST_BACKGROUND_COEFFICIENTS);
                    break;

                case "UPDATE_DEFAULT_BACKGROUND_COEFFICIENTS":
                    if (stringList.size() != 14 * 3) { // 14 integration times * 3 coefficients
                        showToastError("Error in UPDATE_DEFAULT_BACKGROUND_COEFFICIENTS: Expected 42 arguments, got " + stringList.size());
                        return;
                    }
                    List<Float> LIST_DEFAULT_BACKGROUND_COEFFICIENTS = parseFloatListSafely(stringList, "defaultBackgroundCoefficients");
                    photonfy.sender.SET_DEFAULT_BACKGROUND_COEFFICIENTS(LIST_DEFAULT_BACKGROUND_COEFFICIENTS);
                    break;

                case "UPDATE_LUX_CALIBRATION":
                    if (stringList.size() != 1) {
                        showToastError("Error in UPDATE_LUX_CALIBRATION: Expected 1 argument, got " + stringList.size());
                        return;
                    }
                    short luxCalibration = parseShortSafely(stringList.get(0), "luxCalibration");
                    photonfy.sender.SET_LUX_CALIBRATION(luxCalibration);
                    break;

                case "UPDATE_DEFAULT_LUX_CALIBRATION":
                    if (stringList.size() != 1) {
                        showToastError("Error in UPDATE_DEFAULT_LUX_CALIBRATION: Expected 1 argument, got " + stringList.size());
                        return;
                    }
                    short luxCalibration_D = parseShortSafely(stringList.get(0), "defaultLuxCalibration");
                    photonfy.sender.SET_DEFAULT_LUX_CALIBRATION(luxCalibration_D);
                    break;

                case "UPDATE_VIDEO_SAMPLE_RATE":
                    if (stringList.size() != 1) {
                        showToastError("Error in UPDATE_VIDEO_SAMPLE_RATE: Expected 1 argument, got " + stringList.size());
                        return;
                    }
                    short V_VIDEO_SAMPLE_RATE = parseShortSafely(stringList.get(0), "videoSampleRate");
                    photonfy.sender.SET_VIDEO_SAMPLE_RATE(V_VIDEO_SAMPLE_RATE);
                    break;

                case "UPDATE_TIME":
                    if (stringList.size() != 6) {
                        showToastError("Error in UPDATE_TIME: Expected 6 arguments (Hours, Minutes, Seconds, Dia, Mes, Anio), got " + stringList.size());
                        return;
                    }
                    // Basic validation for time format, more robust validation can be added
                    if (stringList.get(0).length() > 2 || stringList.get(1).length() > 2 || stringList.get(2).length() > 2 ||
                            stringList.get(3).length() > 2 || stringList.get(4).length() > 2 || stringList.get(5).length() != 4) { // Year assumed 4 digits for simplicity
                        showToastError("Error in UPDATE_TIME: Invalid time/date format. Please use HH, MM, SS, DD, MM, YYYY format.");
                        return;
                    }

                    String Horas = stringList.get(0);
                    String Minutos = stringList.get(1);
                    String Segundos = stringList.get(2);
                    String Dia = stringList.get(3);
                    String Mes = stringList.get(4);
                    String Anio = stringList.get(5);
                    String Time = Horas + ":" + Minutos + ":" + Segundos;
                    String Date = Dia + "/" + Mes + "/" + Anio;
                    photonfy.sender.SET_TIME(Date, Time);
                    break;
                case "GET_DEVICE_STATE":
                    photonfy.sender.GET_DEVICE_STATE();
                    break;
                case "GET_DEVICE_INFO":
                    photonfy.sender.GET_DEVICE_INFO();
                    break;

                case "GET_RAW_SPECTRUM":
                    photonfy.sender.GET_RAW_SPECTRUM();
                    break;

                case "CALIBRATE_BACKGROUND":
                    photonfy.sender.CALIBRATE_BACKGROUND();
                    break;
                case "CALIBRATE_DEFAULT_BACKGROUND":
                    photonfy.sender.CALIBRATE_DEFAULT_BACKGROUND();
                    break;
                case "RELOAD_DEFAULT_WAVELENGHT_CALIBRATION":
                    photonfy.sender.RELOAD_DEFAULT_WAVELENGTH_CALIBRATION();
                    break;
                case "RELOAD_DEFAULT_BACKGROUND_CALIBRATION":
                    photonfy.sender.RELOAD_DEFAULT_BACKGROUND_CALIBRATIONS();
                    break;
                case "RELOAD_DEFAULT_TRANSFER_FUNCTION":
                    photonfy.sender.RELOAD_DEFAULT_TRANSFER_FUNCTION();
                    break;
                case "RELOAD_DEFAULT_LUX_CALIBRATION":
                    photonfy.sender.RELOAD_DEFAULT_LUX_CALIBRATION();
                    break;
                case "RELOAD_DEFAULT_BACKGROUND_COEFFICIENTS":
                    photonfy.sender.RELOAD_DEFAULT_BACKGROUND_COEFFICIENTS();
                    break;
                case "GET_FLICKERING":
                    photonfy.sender.GET_FLICKERING();
                    break;
                case "END_INITIALIZATION":
                    photonfy.sender.END_INITIALIZATION();
                    break;
                case "SET_SOFT_FACTORY_RESET":
                    photonfy.sender.SET_SOFT_FACTORY_RESET();
                    break;
                case "SET_HARD_FACTORY_RESET":
                    photonfy.sender.SET_HARD_FACTORY_RESET();
                    break;

                case "SET_BLUETOOTH_NAME":
                    if (stringList.size() != 1) {
                        showToastError("Error in SET_BLUETOOTH_NAME: Expected 1 argument, got " + stringList.size());
                        return;
                    }
                    String newName = stringList.get(0);
                    if (newName.length() > 24) {
                        showToastError("Error in SET_BLUETOOTH_NAME: Bluetooth name exceeds maximum length of 24 characters.");
                        return;
                    }
                    photonfy.sender.SET_BLUETOOTH_NAME(newName);
                    break;


                default:
                    showToastError("Unknown command: " + command);
                    return;
            }
        } catch (IllegalArgumentException e) {
            // Exceptions from parsing or range validation are caught here and shown as Toast
            showToastError(e.getMessage());
        } catch (Exception e) {
            // Generic exception handler for unexpected errors
            showToastError("Unexpected error processing command: " + command + ". See logs for details.");
            Log.e("sendCommand", "Exception in command: " + command, e); // Keep logging for unexpected errors
        }
    }


    private short parseShortSafely(String value, String parameterName) {
        try {
            return Short.parseShort(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format for " + parameterName + ": " + value);
        }
    }

    private float parseFloatSafely(String value, String parameterName) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format for " + parameterName + ": " + value);
        }
    }

    private List<Float> parseFloatListSafely(List<String> stringList, String parameterName) {
        List<Float> floatList = new ArrayList<>();
        for (int i = 0; i < stringList.size(); i++) {
            try {
                floatList.add(Float.parseFloat(stringList.get(i)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number format at index " + i + " for " + parameterName + ": " + stringList.get(i));
            }
        }
        return floatList;
    }


    private void showToastError(String message) {
        // Make sure Toast is shown on the UI thread
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show();
        });
    }
    /**
     * Inicia la conexión al dispositivo Bluetooth especificado por su MAC.
     * Este método es accesible desde JavaScript.
     *
     * @param mac La dirección MAC del dispositivo Bluetooth.
     *
     */
    @JavascriptInterface
    public void generateConnection(String mac) {
        if (!hasBluetoothPermission() || bluetoothAdapter == null) {
            Log.e(TAG, "Permiso de Bluetooth no concedido o adaptador nulo");
            return;
        }

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mac);
            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                showToast("El dispositivo no está emparejado");
                return;
            }

            new Thread(() -> connectToDevice(device)).start();

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "MAC address inválida: " + mac, e);
            showToast("MAC address inválida");
        }
    }

    /**
     * Se conecta al dispositivo Bluetooth, inicializa Photonfy y notifica el estado al WebView.
     *
     * @param device El dispositivo Bluetooth a conectar.
     */
    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        try {
            socket = device.createRfcommSocketToServiceRecord(SERIAL_UUID);
            socket.connect();

            // Notifica al WebView que la conexión fue exitosa
            evaluateJavascript("onSocketConnected()");

            photonfy = new Photonfy(socket.getInputStream(), socket.getOutputStream());
            photonfy.addListener(data -> runOnUiThread(() -> {
                switch (PhotonfyCodes.fromInt(data.token)){
                    case GET_RAW_SPECTRUM:
                        Intent myIntent = new Intent(this, PhotoViewer.class);
                        myIntent.putExtra("data", data.getResponse()); //Optional parameters
                        myIntent.putExtra("nonPhoto",false); //Optional parameters
                        this.startActivity(myIntent);
                        break;
                    case GET_DEVICE_STATE:
                    case GET_DEVICE_INFO:
                    case GET_FLICKERING:
                        Intent myIntent2 = new Intent(this, PhotoViewer.class);
                        myIntent2.putExtra("nonPhoto",true); //Optional parameters

                        myIntent2.putExtra("data", data.getResponse()); //Optional parameters
                        this.startActivity(myIntent2);
                        break;
                    case RELOAD_DEFAULT_BACKGROUND_CALIBRATIONS:
                    case RELOAD_DEFAULT_BACKGROUND_COEFFICIENTS:
                    case RELOAD_DEFAULT_LUX_CALIBRATION:
                    case RELOAD_DEFAULT_WAVELENGTH_CALIBRATION:
                    case RELOAD_DEFAULT_TRANSFER_FUNCTION:
                    case CALIBRATE_DEFAULT_BACKGROUND:
                    case CALIBRATE_BACKGROUND:
                    case SET_SOFT_FACTORY_RESET:
                    case SET_HARD_FACTORY_RESET:
                    case END_INITIALIZATION:
                        showToast(data.getResponse());
                        break;
                    default:
                        try {
                            String Code = PhotonfyCodes.valueOf(data.getResponse()).name();
                            showToast(Code);
                        }catch(Exception e){
                            break;
                        }

                        break;
                }
            }));

        } catch (IOException e) {
            Log.e(TAG, "Error al conectar con el dispositivo", e);
            runOnUiThread(() -> {
                String errorMsg = "Fallo en la conexión: " + e.getMessage();
                showToast(errorMsg);
                evaluateJavascript("onSocketError('" + errorMsg + "')");
            });
            // Intenta cerrar el socket en caso de error
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException closeEx) {
                    Log.e(TAG, "Error al cerrar el socket", closeEx);
                }
            }
        }
    }

    /**
     * Verifica si el permiso de BLUETOOTH_CONNECT está concedido.
     *
     * @return true si el permiso está concedido, false en caso contrario.
     */
    private boolean hasBluetoothPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Muestra un mensaje Toast en el hilo principal.
     *
     * @param message El mensaje a mostrar.
     */
    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    /**
     * Ejecuta un script JavaScript en el WebView en el hilo principal.
     *
     * @param script El código JavaScript a ejecutar.
     */
    private void evaluateJavascript(String script) {
        runOnUiThread(() -> webView.evaluateJavascript(script, null));
    }


    public String getUpdateValues(String command) throws IOException {
        switch (command) {
            case "UPDATE_INTEGRATION_TIME":
                JSONObject jsonResponse = new JSONObject();
                photonfy.addListener(new Photonfy.DataListener() {
                    @Override
                    public void onPackageReceived(DataPackage dataPackage) throws IOException {
                        if(dataPackage.name.name().equals("UPDATE_INTEGRATION_TIME")){
                            try {
                                jsonResponse.put("value", dataPackage.getResponse());
                                webView.evaluateJavascript("getValuesFromAndroid("+jsonResponse.toString()+")",null);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            photonfy.removeListener(this);
                        }

                    }
                });
                photonfy.sender.GET_INTEGRATION_TIME();
            default:
                return "";
        }
    }

}
