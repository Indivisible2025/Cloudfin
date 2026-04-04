package com.cloudfin.core;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toast.makeText(this, "Cloudfin Core 已在后台运行", Toast.LENGTH_SHORT).show();
        finish();
    }
}
