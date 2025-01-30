package com.example.photonfyrevival;

import static com.example.photonfyrevival.Constants.Commands;
import static com.google.android.material.internal.ContextUtils.getActivity;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Debug;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.CompletionService;
import android.text.TextUtils;

import com.example.photonfyrevival.Constants;
import com.google.android.material.textfield.TextInputLayout;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity {

    Set<BluetoothDevice> dispositivos=null;
    public static int lastMessageID = 0;
    public OutputStream Salida = null;
    dataSender sender;
    final UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //UUID for serial connection
    final static String DATE_FORMAT = "dd-MM-yyyy";
    public boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");  // Supports decimals and negative numbers
    }
    public boolean isDateValid(String date)
    {
        try {
            DateFormat df = new SimpleDateFormat(DATE_FORMAT);
            df.setLenient(false);
            df.parse(date);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
    public boolean hasManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11 and above
            return Environment.isExternalStorageManager();
        }
        // For lower versions, it always returns true (as MANAGE_EXTERNAL_STORAGE doesn't apply)
        return true;
    }
    private ActivityResultLauncher<Intent> storagePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d("sapo", String.valueOf(result.getResultCode()));

                    if (hasManageExternalStoragePermission()) {
                        fillBluetoothDevicesComboBox();
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setMessage("No se puede usar la aplicacion sin habilitar los permisos de almacenamiento;\nPorfavor habilita los permisos en la configuracion del sistema.")
                                .setPositiveButton("Listo!",new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        finish();
                                        System.exit(0);
                                    }});

                        builder.create().show();
                    }

            });
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 123)        {
            if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_DENIED){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("No se puede usar la aplicacion sin habilitar los permisos de Bluetooth;\nPorfavor habilita los permisos en la configuracion del sistema.")
                        .setPositiveButton("Listo!",new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                finish();
                                System.exit(0);
                            }});

                builder.create().show();

            }else{
                // Cuando ya pidio los permisos de bluetooth pide los permisos de almacenamiento
                if(!hasManageExternalStoragePermission()){
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage("Porfavor habilita los permisos de almacenamiento, la aplicacion no puede funcionar sin ellos;")
                            .setPositiveButton("Listo!",new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                    intent.setData(Uri.parse("package:" + getPackageName()));
                                    storagePermissionLauncher.launch(intent);
                                }});
                    builder.create().show();
                }
            }

        }
    }
    public void fillBluetoothDevicesComboBox(){
        BluetoothAdapter btAdapter =  android.bluetooth.BluetoothAdapter.getDefaultAdapter();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        dispositivos = btAdapter.getBondedDevices();
        ArrayAdapter<String> lista = new ArrayAdapter<String>(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item);
        for(BluetoothDevice dispositivo:dispositivos){
            lista.add(dispositivo.getName());
        }
        Spinner sp = findViewById(R.id.Lista_Dispositivos);
        sp.setAdapter(lista);
        Button btn =  findViewById(R.id.ConectarBoton);
        Button getStatus = findViewById(R.id.button);

        getStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Spinner sp = findViewById(R.id.spinner);
                TextView text = findViewById(R.id.CajaOutput);
                try {
                    switch (sp.getSelectedItem().toString()){
                        case "GET_DEVICE_STATE":
                        case "GET_INTEGRATION_TIME":
                        case "GET_GAIN":
                        case "STOP_STREAMING":
                        case "GET_DEFAULT_TRANSFER_FUNCTION":
                        case "RELOAD_DEFAULT_TRANSFER_FUNCTION":
                        case "GET_WAVELENGTH_CALIBRATION":
                        case "RELOAD_DEFAULT_WAVELENGTH_CALIBRATION":
                        case "CALIBRATE_BACKGROUND":
                        case "GET_BACKGROUND_CALIBRATIONS":
                        case "CALIBRATE_DEFAULT_BACKGROUND":
                        case "GET_DEFAULT_BACKGROUND_CALIBRATIONS":
                        case "RELOAD_DEFAULT_BACKGROUND_CALIBRATIONS":
                        case "GET_LUX_CALIBRATION":
                        case "GET_DEFAULT_LUX_CALIBRATION":
                        case "RELOAD_DEFAULT_LUX_CALIBRATION":
                        case "GET_VIDEO_SAMPLE_RATE":
                        case "GET_TIME":
                        case "GET_DEVICE_INFO":
                        case "SET_SOFT_FACTORY_RESET":
                        case "SET_HARD_FACTORY_RESET":
                        case "GET_FLICKERING":
                        case "GET_TRANSFER_FUNCTION":
                        case "GET_DEFAULT_WAVELENGTH_CALIBRATION":
                        case "GET_BACKGROUND_COEFFICIENTS":
                        case "GET_DEFAULT_BACKGROUND_COEFFICIENTS":
                        case "RELOAD_DEFAULT_BACKGROUND_COEFFICIENTS":
                            sender.sendCommand(Commands.get(sp.getSelectedItem().toString()));
                            break;
                        case "SET_INTEGRATION_TIME":
                            LinearLayout linearLayout = findViewById(R.id.linearLayout);
                            EditText campo = (EditText)linearLayout.getChildAt(1);
                            String value = campo.getText().toString();

                            if(!TextUtils.isDigitsOnly(value)){
                                Toast.makeText(getBaseContext(),"La entrada debe ser un numero",Toast.LENGTH_LONG).show();
                                return;
                            }
                            final int INTEGRATION_TIME = Integer.parseInt(value);
                            if(INTEGRATION_TIME <0 || INTEGRATION_TIME > 6000){
                                Toast.makeText(getBaseContext(),"La entrada debe ser un numero entre 0 y 6000",Toast.LENGTH_LONG).show();
                                return;
                            }
                            sender.SET_INTEGRATION_TIME(Short.parseShort(value));
                            break;
                        case "SET_TIME":
                            LinearLayout linearLayout2 = findViewById(R.id.linearLayout);
                            EditText campo2 = (EditText)linearLayout2.getChildAt(1);
                            EditText campo3 = (EditText)linearLayout2.getChildAt(3);
                            String Fecha = campo2.getText().toString();
                            String Hora = campo3.getText().toString();

                            if(Objects.equals(Fecha, "") || Objects.equals(Hora, "")){
                                Toast.makeText(getBaseContext(),"Introduzca una fecha y hora",Toast.LENGTH_LONG).show();
                                return;
                            }
                            if(isDateValid(Fecha)){
                                Toast.makeText(getBaseContext(),"Introduzca una fecha valida",Toast.LENGTH_LONG).show();
                                return;
                            }
                            if (!Hora.matches("^([0-9]|0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$")){
                                Toast.makeText(getBaseContext(),"Introduzca una hora valida",Toast.LENGTH_LONG).show();
                                return;
                            }
                            sender.SET_TIME(Fecha, Hora);
                            break;
                        case "SET_BLUETOOTH_NAME":
                            LinearLayout linearLayout3 = findViewById(R.id.linearLayout);
                            EditText campo_BT = (EditText)linearLayout3.getChildAt(1);
                            String nombre_BT = campo_BT.getText().toString();
                            if(nombre_BT.length()>23){
                                Toast.makeText(getBaseContext(),"El nombre del dispositivo no puede tener mas de 23 caracteres",Toast.LENGTH_LONG).show();
                                return;
                            }
                            sender.SET_BLUETOOTH_NAME(nombre_BT);
                            break;
                        case "SET_GAIN":
                            LinearLayout linearLayout4 = findViewById(R.id.linearLayout);
                            EditText campo_Gain = (EditText)linearLayout4.getChildAt(1);
                            String Valor_Gain = campo_Gain.getText().toString();
                            if(!TextUtils.isDigitsOnly(Valor_Gain)){
                                Toast.makeText(getBaseContext(),"La entrada debe ser un numero",Toast.LENGTH_LONG).show();
                                return;
                            }
                            final int GAIN_1 = Integer.parseInt(Valor_Gain);
                            if(!(GAIN_1==0 || GAIN_1 ==1)){
                                Toast.makeText(getBaseContext(),"La entrada debe ser 0 o 1",Toast.LENGTH_LONG).show();
                                return;
                            }
                            try {
                                sender.SET_GAIN((byte) GAIN_1);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                            break;
                        case "SET_DEFAULT_LUX_CALIBRATION":
                        case "SET_LUX_CALIBRATION":
                            LinearLayout linearLayout5 = findViewById(R.id.linearLayout);
                            EditText campo_Lux = (EditText)linearLayout5.getChildAt(1);
                            String Valor_Lux = campo_Lux.getText().toString();
                            if(Objects.equals(Valor_Lux, "")){
                                Toast.makeText(getBaseContext(),"Introduzca un valor",Toast.LENGTH_LONG).show();
                                return;
                            }
                            if(!isNumeric(Valor_Lux)){
                                Toast.makeText(getBaseContext(),"La entrada debe ser un numero",Toast.LENGTH_LONG).show();
                                return;
                            }
                            sender.SET_LUX_CALIBRATION(Float.parseFloat(Valor_Lux));
                            break;
                        case "SET_VIDEO_SAMPLE_RATE":
                            LinearLayout linearLayout6 = findViewById(R.id.linearLayout);
                            EditText campo_FrameRate = (EditText)linearLayout6.getChildAt(1);
                            String Valor_FrameRate = campo_FrameRate.getText().toString();

                            if(Objects.equals(Valor_FrameRate, "")){
                                Toast.makeText(getBaseContext(),"Introduzca un valor",Toast.LENGTH_LONG);
                                return;
                            }
                            if(isNumeric(Valor_FrameRate)){
                                Toast.makeText(getBaseContext(),"La entrada debe ser un numero",Toast.LENGTH_LONG).show();
                                return;
                            }
                            if(Float.parseFloat(Valor_FrameRate)<=0){
                                Toast.makeText(getBaseContext(),"La entrada debe ser un numero mayor a 0",Toast.LENGTH_LONG).show();
                                return;
                            }
                            sender.SET_VIDEO_SAMPLE_RATE(Float.parseFloat(Valor_FrameRate));
                            break;

                        case "SET_WAVELENGTH_CALIBRATION":
                            LinearLayout linearLayout7 = findViewById(R.id.linearLayout);
                            EditText campo_WAVELENGTH_CALIBRATION_1 = (EditText)linearLayout7.getChildAt(1);
                            EditText campo_WAVELENGTH_CALIBRATION_2 = (EditText)linearLayout7.getChildAt(3);
                            EditText campo_WAVELENGTH_CALIBRATION_3 = (EditText)linearLayout7.getChildAt(5);
                            EditText campo_WAVELENGTH_CALIBRATION_4 = (EditText)linearLayout7.getChildAt(7);
                            EditText campo_WAVELENGTH_CALIBRATION_5 = (EditText)linearLayout7.getChildAt(9);
                            EditText campo_WAVELENGTH_CALIBRATION_6 = (EditText)linearLayout7.getChildAt(11);
                            EditText campo_WAVELENGTH_CALIBRATION_7 = (EditText)linearLayout7.getChildAt(13);
                            String Valor_WAVELENGTH_CALIBRATION_1 = campo_WAVELENGTH_CALIBRATION_1.getText().toString();
                            String Valor_WAVELENGTH_CALIBRATION_2 = campo_WAVELENGTH_CALIBRATION_2.getText().toString();
                            String Valor_WAVELENGTH_CALIBRATION_3 = campo_WAVELENGTH_CALIBRATION_3.getText().toString();
                            String Valor_WAVELENGTH_CALIBRATION_4 = campo_WAVELENGTH_CALIBRATION_4.getText().toString();
                            String Valor_WAVELENGTH_CALIBRATION_5 = campo_WAVELENGTH_CALIBRATION_5.getText().toString();
                            String Valor_WAVELENGTH_CALIBRATION_6 = campo_WAVELENGTH_CALIBRATION_6.getText().toString();
                            String[] Valores = {Valor_WAVELENGTH_CALIBRATION_1,Valor_WAVELENGTH_CALIBRATION_2,Valor_WAVELENGTH_CALIBRATION_3,Valor_WAVELENGTH_CALIBRATION_4,Valor_WAVELENGTH_CALIBRATION_5,Valor_WAVELENGTH_CALIBRATION_6};
                            Stack<Float> Coef = new Stack<>();
                            // Checks:
                            for (int i = 0; i < Valores.length; i++) {
                                if(Objects.equals(Valores[i], "")){
                                    Toast.makeText(getBaseContext(),"Introduzca los valores",Toast.LENGTH_LONG).show();
                                    return;
                                }
                                if(! isNumeric(Valores[i])){
                                    Toast.makeText(getBaseContext(),"La entrada debe ser un numero",Toast.LENGTH_LONG).show();
                                    return;
                                }
                                float num = Float.parseFloat(Valores[i]);
                                if(num<-380 || num>380){
                                    Toast.makeText(getBaseContext(),"La entrada debe estar entre -380 y 380",Toast.LENGTH_LONG).show();
                                    return;
                                }
                                Coef.push(num);
                            }
                            try {
                                sender.SET_WAVELENGTH_CALIBRATION(Coef);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                            break;

                        case "SET_BACKGROUND":
                        case "SET_DEFAULT_BACKGROUND":
                            LinearLayout linearLayout8 = findViewById(R.id.linearLayout);
                            EditText campo_SET_BACKGROUND_1 = (EditText)linearLayout8.getChildAt(1);
                            EditText campo_SET_BACKGROUND_2 = (EditText)linearLayout8.getChildAt(3);
                            EditText campo_SET_BACKGROUND_3 = (EditText)linearLayout8.getChildAt(5);

                            String Valor_SET_BACKGROUND_1 = campo_SET_BACKGROUND_1.getText().toString();
                            String Valor_SET_BACKGROUND_2 = campo_SET_BACKGROUND_2.getText().toString();
                            String Valor_SET_BACKGROUND_3 = campo_SET_BACKGROUND_3.getText().toString();
                            if(Objects.equals(Valor_SET_BACKGROUND_1, "") || Objects.equals(Valor_SET_BACKGROUND_2, "") || Objects.equals(Valor_SET_BACKGROUND_3, "")){
                                Toast.makeText(getBaseContext(),"Introduzca los valores",Toast.LENGTH_LONG).show();
                                return;
                            }
                            if(! isNumeric(Valor_SET_BACKGROUND_1) || ! isNumeric(Valor_SET_BACKGROUND_2) || !isNumeric(Valor_SET_BACKGROUND_3)){
                                Toast.makeText(getBaseContext(),"La entrada debe ser un numero",Toast.LENGTH_LONG).show();
                                return;
                            }
                            short Count = Short.parseShort(Valor_SET_BACKGROUND_1);
                            Short Integration = Short.parseShort(Valor_SET_BACKGROUND_2);
                            Float Temp1 = Float.parseFloat(Valor_SET_BACKGROUND_3);
                            sender.SET_BACKGROUND(Count,Integration,Temp1);
                            break;
                        case "GET_RAW_SPECTRUM": // No envia los paquetes de vuelta
                        case "SET_DEFAULT_TRANSFER_FUNCTION": // x Implementar
                        default:
                            text.setText("Funcion no implementada :/");
                            break;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        );
        btn.setEnabled(true);
        btn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Spinner sp = findViewById(R.id.Lista_Dispositivos);
                String SelectedItem = sp.getSelectedItem().toString();
                String MAC = "";
                for(BluetoothDevice dispositivo:dispositivos){
                    if (ActivityCompat.checkSelfPermission(view.getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    if(Objects.equals(dispositivo.getName(), SelectedItem)){
                        MAC = dispositivo.getAddress().toString();
                    }

                }
                BluetoothDevice device = btAdapter.getRemoteDevice(MAC); // deviceAddress is the Arduino HC-05 MAC address

                BluetoothSocket socket = null; // MY_UUID is a unique UUID for the SPP service
                try {
                    findViewById(R.id.button).setEnabled(true);
                    socket = device.createRfcommSocketToServiceRecord(SERIAL_UUID);
                    socket.connect(); // Establish the connection
                    Toast.makeText(view.getContext(),"Dispositivo Conectado",Toast.LENGTH_LONG).show();
                    TextView txt = findViewById(R.id.CajaOutput);
                    dataReceiver receiver = new dataReceiver(socket, getBaseContext(),txt,view);
                    sender = new dataSender(socket,getBaseContext());

                    receiver.start();
                    Salida = socket.getOutputStream();

                } catch (IOException e) {
                    Toast.makeText(view.getContext(),"No se realizo la conexion: "+e.getMessage(),Toast.LENGTH_LONG).show();

                }


            }
        });;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Constants costants = new Constants();
        costants.initConst();

        // Para que la aplicacion pueda funcionar primero pedimos los permisos de bluetooth
        if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!= PackageManager.PERMISSION_GRANTED){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Porfavor habilita los permisos de Bluetooth, la aplicacion no puede funcionar sin ellos;")
                    .setPositiveButton("Listo!",new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},123);
                        }});
            builder.create().show();
        }else {
            // Si si tiene blutu entonces pedimos los permisos de almacenamiento
            if(!hasManageExternalStoragePermission()){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Porfavor habilita los permisos de almacenamiento, la aplicacion no puede funcionar sin ellos;")
                        .setPositiveButton("Listo!",new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                storagePermissionLauncher.launch(intent);
                            }});
                builder.create().show();
            }else {
                // Tiene los dos fockin permisos
                fillBluetoothDevicesComboBox();
            }
        }


        // Codigo para crear las comboBoxes

        Spinner sp = findViewById(R.id.spinner);
        Map<String, List<String>> jsonData = new HashMap<>();
        jsonData.put("SET_INTEGRATION_TIME", Arrays.asList("Tiempo de integracion (0-6000ms):"));
        jsonData.put("SET_TIME", Arrays.asList("Fecha (DD/MM/AAAA):","Hora (HH:MM:SS con dos digitos):"));
        jsonData.put("SET_BLUETOOTH_NAME", Arrays.asList("Nombre del dispositivo:"));
        jsonData.put("SET_GAIN", Arrays.asList("1 para activar, 0 para desactivar:"));
        jsonData.put("SET_LUX_CALIBRATION", Arrays.asList("Coeficiente:"));
        jsonData.put("SET_DEFAULT_LUX_CALIBRATION", Arrays.asList("Coeficiente:"));
        jsonData.put("SET_VIDEO_SAMPLE_RATE", Arrays.asList("SampleRate:"));
        jsonData.put("SET_WAVELENGTH_CALIBRATION", Arrays.asList("Coeficiente 1:","Coeficiente 2:","Coeficiente 3:","Coeficiente 4:","Coeficiente 5:","Coeficiente 6:"));
        jsonData.put("SET_BACKGROUND", Arrays.asList("Counts:","Tiempo de integracion:","Temperatura"));
        jsonData.put("SET_DEFAULT_BACKGROUND", Arrays.asList("Counts:","Tiempo de integracion:","Temperatura"));



        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            LinearLayout linearLayout = findViewById(R.id.linearLayout);

            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                linearLayout.removeAllViews();
                linearLayout.setOrientation(LinearLayout.VERTICAL); // Ensure it's vertical

                boolean isDarkMode = (getResources().getConfiguration().uiMode &
                        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

                switch (parent.getItemAtPosition(position).toString()){
                    case "SET_INTEGRATION_TIME":
                    case "SET_TIME":
                    case "SET_BLUETOOTH_NAME":
                    case "SET_GAIN":
                    case "SET_LUX_CALIBRATION":
                    case "SET_DEFAULT_LUX_CALIBRATION":
                    case "SET_VIDEO_SAMPLE_RATE":
                    case "SET_WAVELENGTH_CALIBRATION":
                    case "SET_BACKGROUND":
                        case "SET_DEFAULT_BACKGROUND":
                        for (int i = 0; i < jsonData.get(parent.getItemAtPosition(position).toString()).size(); i++) {
                            TextView newTextView = new TextView(getBaseContext());
                            newTextView.setText(jsonData.get(parent.getItemAtPosition(position).toString()).get(i));
                            newTextView.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT));
                            EditText campo = new EditText(getBaseContext());
                            campo.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT));
                            if (isDarkMode) {
                                campo.setTextColor(Color.WHITE); // Light text for dark mode
                            } else {
                                campo.setTextColor(Color.BLACK); // Dark text for light mode
                            }
                            linearLayout.addView(newTextView);
                            linearLayout.addView(campo);
                        }

                        break;

                    default:
                        break;
                }
            }
            public void onNothingSelected(AdapterView<?> parent)
            {
                //Literalmente nunca pasa pero toca implementarlo XD
            }
        });




        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}