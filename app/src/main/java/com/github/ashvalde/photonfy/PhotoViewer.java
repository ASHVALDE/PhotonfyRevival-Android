package com.github.ashvalde.photonfy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PhotoViewer extends AppCompatActivity {

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        Intent intent = getIntent();
        String[] data = intent.getStringExtra("data").split("\n");

        String preData = intent.getStringExtra("data");
        setContentView(R.layout.activity_photo_viewer);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            WebView webView = findViewById(R.id.web_view);
            webView.getSettings().setJavaScriptEnabled(true);
            if(intent.getBooleanExtra("nonPhoto",true)){
                webView.loadUrl("file:///android_asset/dataPrint/index.html");
                webView.setWebViewClient(new android.webkit.WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        Log.d("dataPrint",preData);
                        String preData2 = preData.replace("\n", "\\n"); // Escapa los saltos de línea
                        webView.evaluateJavascript("createGraph('" + preData2 + "')", null);
                    }
                });
            }else{
                webView.loadUrl("file:///android_asset/PhotoViewer/index.html");
                webView.setWebViewClient(new android.webkit.WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    webView.evaluateJavascript("createGraph([" + data[1] + "])", null);
                }
                });
            }

            return insets;
        });


    }
}