
package com.example.deepak14035.strokebeat;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Orientation implements SensorEventListener {

  public interface Listener {
    void onOrientationChanged(String sensor, float readings);
  }

  private static final int SENSOR_DELAY_MICROS = 50 * 1000; // 50ms

  private final WindowManager mWindowManager;

  private final SensorManager mSensorManager;

  @Nullable
  private final Sensor mRotationSensor, mTempSensor;

  private int mLastAccuracy;
  private Listener mListener;
  String gyrofilename="gyrodata.txt",accelerofilename="accelerodata.txt";
  Context cont;
  public Orientation(Activity activity, int sensorID, int sensorID2) {
    mWindowManager = activity.getWindow().getWindowManager();
    mSensorManager = (SensorManager) activity.getSystemService(Activity.SENSOR_SERVICE);
    cont=activity.getApplicationContext();
    // Can be null if the sensor hardware is not available
    mRotationSensor = mSensorManager.getDefaultSensor(sensorID);
    mTempSensor = mSensorManager.getDefaultSensor(sensorID2);

  }

  public void startListening(Listener listener) {
    if (mListener == listener) {
      return;
    }
    mListener = listener;
    if (mRotationSensor == null && mTempSensor==null) {
      //LogUtil.w("Rotation vector sensor not available; will not provide orientation data.");
      return;
    }
    if(mTempSensor==null){
      Log.d("error","TEMPERATURE  vector sensor not available; will not provide orientation data.");
    }
    mSensorManager.registerListener(this, mRotationSensor, SENSOR_DELAY_MICROS);
    mSensorManager.registerListener(this, mTempSensor, SENSOR_DELAY_MICROS);

  }

  public void stopListening() {
    mSensorManager.unregisterListener(this);
    mListener = null;
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    if (mLastAccuracy != accuracy) {
      mLastAccuracy = accuracy;
    }
  }

/*
  public void writeFile(Context context,String sFileName, String text) {
    if(MainActivity.startsaving) {
      try {
        File root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WARDI");
        Log.d("file", "writing" + root.getName());
        if (!root.exists()) {
          root.mkdir();
        }
        File file = new File(root, sFileName);

        FileOutputStream f = new FileOutputStream(file, true);
        PrintWriter pw = new PrintWriter(f);
        pw.append(text + "\t");
        pw.flush();
        pw.close();
        f.close();

      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

*/
  @Override
  public void onSensorChanged(SensorEvent event) {
    if (mListener == null) {
      return;
    }
    if (mLastAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
      return;
    }float[] val=event.values;

    if (event.sensor == mRotationSensor) {
      //updateOrientation(event.values);
      mListener.onOrientationChanged("accel",val[0]+val[1]+val[2]);
      Log.d("accelero", val[0] + "," + val[1] + "," + val[2]);
      //writeFile(cont,accelerofilename,val[0]+","+val[1]+","+val[2]);
    }
    else if(event.sensor==mTempSensor){
      mListener.onOrientationChanged("temp",val[0]);
      Log.d("temp",val[0]+",");
      //writeFile(cont,gyrofilename,val[0]+","+val[1]+","+val[2]);
    }
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void updateOrientation(float[] rotationVector) {
    float[] rotationMatrix = new float[9];
    SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);

    final int worldAxisForDeviceAxisX;
    final int worldAxisForDeviceAxisY;

    // Remap the axes as if the device screen was the instrument panel,
    // and adjust the rotation matrix for the device orientation.
    switch (mWindowManager.getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_0:
      default:
        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
        worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
        break;
      case Surface.ROTATION_90:
        worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
        break;
      case Surface.ROTATION_180:
        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
        break;
      case Surface.ROTATION_270:
        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
        break;
    }

    float[] adjustedRotationMatrix = new float[9];
    SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
        worldAxisForDeviceAxisY, adjustedRotationMatrix);

    // Transform rotation matrix into azimuth/pitch/roll
    float[] orientation = new float[3];
    SensorManager.getOrientation(adjustedRotationMatrix, orientation);

    // Convert radians to degrees
    float pitch = orientation[1] * -57;
    float roll = orientation[2] * -57;

    mListener.onOrientationChanged("accel",pitch+roll+0f);
  }
}
