package com.p4f.objecttracking;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class MainActivity extends AppCompatActivity implements
        FragmentManager.OnBackStackChangedListener,
        SensorEventListener {

    private static final String TAG = "MainActivity";

    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private TextView angleTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        onBackStackChanged();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        angleTextView = findViewById(R.id.angleTextView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] quat = new float[4];
            SensorManager.getQuaternionFromVector(quat, event.values);

            float w = quat[0];
            float x = quat[1];
            float y = quat[2];
            float z = quat[3];

            // Hiển thị quaternion trực tiếp (normalized)
            String qText = String.format(
                    "Quaternion:\nW: %.4f\nX: %.4f\nY: %.4f\nZ: %.4f",
                    w, x, y, z
            );
            angleTextView.setText(qText);
            Log.d(TAG, qText);

            // Nếu cần: truyền quaternion đến hệ thống xử lý khác (OpenGL, Unity, v.v.)
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onBackStackChanged() {
        boolean canGoBack = getSupportFragmentManager().getBackStackEntryCount() > 0;
        getSupportActionBar().setDisplayHomeAsUpEnabled(canGoBack);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_camera) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            Fragment cameraFragment = fragmentManager.findFragmentByTag("camera");

            if (cameraFragment == null || !cameraFragment.isVisible()) {
                fragmentManager.beginTransaction()
                        .replace(R.id.fragment, new CameraFragment(), "camera")
                        .addToBackStack(null)
                        .commit();
            } else {
                fragmentManager.popBackStack();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
