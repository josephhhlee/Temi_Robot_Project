package com.cyberpunk.temiiotproject;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

import java.util.concurrent.TimeUnit;

public class Browser extends AppCompatActivity {

    boolean touchDetected;
    Thread timeoutListener = null;
    static boolean checkout, exit, timeout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        Robot.getInstance().speak(TtsRequest.create("Bringing up Food Menu.", false));
        WebView webview = findViewById(R.id.upperview);
        // WebView webview =  new WebView(this);
        webview.setWebViewClient(new myWebClient());
        //Enable JavaScript support
        // webview.getSettings().setBuiltInZoomControls(true);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.loadUrl("https://www.mcdelivery.com.sg/sg/browse/menu.html");
//        webview.loadUrl("198.162.0.107");
        checkout = false;
        exit = false;
        timeout = false;
        timeoutListener = new Thread(this::timeoutListener);
        timeoutListener.start();
        // setContentView(webview);
    }

    public class myWebClient extends WebViewClient
    {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }
    }

    public void timeoutListener() {
        for (int i = 0; i < 100; i++) {
            if (touchDetected) {
                i = -1;
                touchDetected = false;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        timeout();
    }

    public void onUserInteraction() {
        touchDetected = true;
    }

    static public boolean getCheckoutStatus() {return checkout;}
    static public boolean getExitStatus() {return exit;}
    static public boolean getTimeoutStatus() {return timeout;}

    public void checkOut(View view){
        checkout = true;
        exit = false;
        timeout = false;
        Toast.makeText(Browser.this, "Checked Out", Toast.LENGTH_SHORT).show();
        finish();
    }

    public void exit(View view){
        exit = true;
        checkout = false;
        timeout = false;
        finish();
    }

    public void timeout(){
        timeout = true;
        checkout = false;
        exit = false;
        finish();
    }

}