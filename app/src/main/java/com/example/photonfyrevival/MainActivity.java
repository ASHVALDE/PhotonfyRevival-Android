package com.example.photonfyrevival;

import static com.example.photonfyrevival.Constants.Commands;
import static com.google.android.material.internal.ContextUtils.getActivity;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionService;

import com.example.photonfyrevival.Constants;
import com.google.android.material.textfield.TextInputLayout;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity {

    Set<BluetoothDevice> dispositivos=null;
    public static int lastMessageID = 0;
    public OutputStream Salida = null;
    dataSender sender;
    final UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //UUID for serial connection

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
                            sender.sendCommand(Commands.get(sp.getSelectedItem().toString()));
                            break;
                        case "GET_RAW_SPECTRUM":
                        case "GET_TRANSFER_FUNCTION":
                        case "GET_DEFAULT_WAVELENGTH_CALIBRATION":
                        case "GET_BACKGROUND_COEFFICIENTS":
                        case "GET_DEFAULT_BACKGROUND_COEFFICIENTS":
                        case "RELOAD_DEFAULT_BACKGROUND_COEFFICIENTS":


                        case "SET_DEFAULT_TRANSFER_FUNCTION":
                        case "SET_INTEGRATION_TIME":
                        case "SET_TIME":
                        case "SET_GAIN":
                        case "SET_WAVELENGTH_CALIBRATION":
                        case "SET_LUX_CALIBRATION":
                        case "SET_BLUETOOTH_NAME":
                        case "SET_DEFAULT_LUX_CALIBRATION":
                        case "SET_VIDEO_SAMPLE_RATE":
                        case "SET_BACKGROUND":
                        case "SET_DEFAULT_BACKGROUND":

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






        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}