package com.example.photonfyrevival;

import static android.app.PendingIntent.getActivity;
import static androidx.core.content.ContextCompat.startActivity;
import static com.google.common.reflect.Reflection.getPackageName;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Locale;
import java.util.Stack;



public class dataReceiver extends Thread{
    static Stack<Short> dataPhotos = new Stack<Short>();
    static Stack<Float> dataTransferFunction = new Stack<Float>();
    static Stack<Integer> dataFlickering = new Stack<Integer>();
    public Context context;


    static BluetoothSocket connection = null;
    TextView outputBox;
    public View view;
    public dataReceiver(BluetoothSocket connection, Context context, TextView txt, View view) throws IOException {
        this.connection = connection;
        this.context =context;
        this.outputBox = txt;
        this.view = view;
    }


    public void run(){
        InputStream inputStream = null;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Recibimos una lectura
        boolean leyendo = true;
        var i = new Stack<Integer>();
        var bytes = new Stack<Byte>();
        while(true){
            while(leyendo) {

                while (true) {
                    try {
                        if (inputStream.available() == 0) break;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }


                    final int data;
                    try {
                        data = inputStream.read();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    bytes.add((byte) data);
                    i.add(data);
                    try {
                        if (inputStream.available() == 0) {

                            leyendo = false;
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            dataPackage x = null;
            try {
                x = new dataPackage().fromBytes(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if( x.function== 225){
                if(x.sequence==0){
                    try {
                        dataPhotos = x.unpackPayload();
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }
                if(x.sequence!=0) {
                    try {
                        dataPhotos.addAll(x.unpackPayload());
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }

                if(dataPhotos.size()>255){

                    String folderName = "/Photonfy/";
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

                    File directory = new File(Environment.getExternalStorageDirectory() + folderName + timestamp);
                    if (!directory.exists()) {
                        directory.mkdirs(); // Create directory if it doesn't exist
                    }

                    File file = new File(directory, "data.txt");

                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        for (int j = 0; j < dataPhotos.size(); j++) {
                            fos.write( dataPhotos.get(j).toString().getBytes());
                            if (j < dataPhotos.size() - 1){
                                fos.write(',');
                            }
                        }
                        fos.flush();
                        view.post(new Runnable() {
                            public void run() {
                                outputBox.setText("Creado archivo .CSV con datos del espectrometro en: "+file.getAbsolutePath());
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


            }else{
                try {
                    x.unpackPayload();
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }

            if(x.UnpackedResponse!= null){
                // Aqui tambien deberia mandar la foto

                dataPackage finalX = x;
                view.post(new Runnable() {
                    public void run() {
                        outputBox.setText(finalX.UnpackedResponse);
                    }
                });


            }

            bytes = new Stack<Byte>();
            i = new Stack<Integer>();
            leyendo = true;
    }
}
}
